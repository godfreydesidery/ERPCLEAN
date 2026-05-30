# TC-POS-IDEMPOTENCY — POS Sync Op Idempotency

**Module:** pos / sync  
**Stories:** US-POS-017, US-POS-018  
**API base:** `http://localhost:8081/api/v1`  
**Auth:** cashier / Cashier#2026, X-Branch-Id: 1, X-Orbix-Contract-Version: 1  
**Source grounding:** `SyncServiceImpl.java` (applyPosSale, applyTillSessionOpen, applyCashPickup,
applyPettyCash, applyTillSessionClose), `SyncController.java`, V78 uk_*_client_op constraints.

**Gap:** TC-POS-SYNC covers batch-level acceptance and pull mechanics.
This file covers **idempotency-specific** failure modes: duplicate op on retry, concurrent
concurrent replay, cross-op type idempotency, batch full-replay, TILL_SESSION_CLOSE via both push
and the dedicated endpoint, and manifest-mismatch on reconciliation. These are P0 — a
double-effect equals money loss or double stock drain.

**INTERFERENCE NOTE:** Each case generates its own ULID-prefixed clientOpId, deviceId, and
(where required) a freshly opened till session. No case closes or archives the shared seeded
business day, catalog, or customer.

---

## Auth Precondition (shared across all cases)

```bash
CASHIER_TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"cashier","password":"Cashier#2026"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
CASH_HDR="Authorization: Bearer $CASHIER_TOKEN"
BRANCH="X-Branch-Id: 1"
CONTRACT="X-Orbix-Contract-Version: 1"
```

Resolve seeded COKE500 item and UoM:
```bash
COKE_ID=$(curl -s "http://localhost:8081/api/v1/items?q=COKE500" \
  -H "$CASH_HDR" | grep -o '"id":"[0-9]*"' | head -1 | grep -o '[0-9]*')
UOM_ID=1   # EA — seeded by Flyway
SECTION_ID=1   # default section; resolve via GET /api/v1/sections if needed
CUSTOMER_ID=$(curl -s "http://localhost:8081/api/v1/customers?q=CUST0001" \
  -H "$CASH_HDR" | grep -o '"id":"[0-9]*"' | head -1 | grep -o '[0-9]*')
```

---

## Happy-Path Idempotency (FUNC / DATA)

### TC-POS-IDEMPOTENCY-001 — PETTY_CASH op replayed with same clientOpId returns DUPLICATE, no double-deduct

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-001 |
| **Title** | Replaying a PETTY_CASH op returns DUPLICATE and does not double-deduct the session cash |
| **Area** | pos/sync |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN for branch 1. 2. Open a fresh till session via TILL_SESSION_OPEN op; record the returned `serverEntityId` as `<SESSION_ID>`. 3. Cashier token obtained. |
| **Steps** | 1. Generate `SESS_OP_ID="IDMP-001-SESS-$(date +%s%N)"` and `PC_OP_ID="IDMP-001-PC-$(date +%s%N)"`. 2. Open session: `POST /api/v1/sync/push` body: `{"deviceId":"DEV-IDMP-001","clientContractVersion":1,"ops":[{"clientOpId":"<SESS_OP_ID>","opType":"TILL_SESSION_OPEN","seq":1,"occurredAt":"<ISO8601>","payload":{"tillId":1,"openingFloatAmount":50000}}]}`. Capture `data.results[0].serverEntityId` as `<SESSION_ID>`. 3. Push PETTY_CASH op (first time): `POST /api/v1/sync/push` body: `{"deviceId":"DEV-IDMP-001","clientContractVersion":1,"ops":[{"clientOpId":"<PC_OP_ID>","opType":"PETTY_CASH","seq":2,"occurredAt":"<ISO8601>","payload":{"tillSessionId":<SESSION_ID>,"amount":3000,"authorisedBy":1,"category":"TRANSPORT","paidTo":"Driver","description":"Fuel reimbursement"}}]}`. Record response: `first_verdict`. 4. Query `petty_cash` count for this session. 5. Push identical op again (same `<PC_OP_ID>`): second `POST /api/v1/sync/push` with exactly the same body. Record response: `second_verdict`. 6. Query `petty_cash` count for this session again. |
| **Expected Result** | Step 3: HTTP 200; `first_verdict = "ACCEPTED"`. Step 4: exactly 1 `petty_cash` row for this session. Step 5: HTTP 200; `second_verdict = "DUPLICATE"`; `data.results[0].serverEntityId` equals the same id returned in step 3. Step 6: still exactly 1 `petty_cash` row — no double-deduct. Session expected cash reduced by exactly 3000 TZS, not 6000. |
| **Automatable?** | yes — integration test (`SyncServiceImplTest#pettyCash_replay_returnsDuplicate_noDoubleDeduct`) |
| **Result/Status** | |
| **Notes/IssueRef** | P0 — double-deduct = money loss. Idempotency key: `uk_petty_cash_client_op` (V78). Verify via `pettyCashRepository.sumForSession`. |

---

### TC-POS-IDEMPOTENCY-002 — TILL_SESSION_CLOSE via push batch replayed returns DUPLICATE, no second close/variance

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-002 |
| **Title** | Replaying TILL_SESSION_CLOSE op via push batch returns DUPLICATE; session not closed twice; variance not doubled |
| **Area** | pos/sync |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. Fresh till session OPEN via TILL_SESSION_OPEN op; `SESS_OP_ID` recorded. 3. At least one POS_SALE op accepted in the session. 4. Cashier token. |
| **Steps** | 1. Generate `CLOSE_OP_ID="IDMP-002-CLOSE-$(date +%s%N)"`. 2. Push TILL_SESSION_CLOSE op: `POST /api/v1/sync/push` body: `{"deviceId":"DEV-IDMP-002","clientContractVersion":1,"ops":[{"clientOpId":"<CLOSE_OP_ID>","opType":"TILL_SESSION_CLOSE","seq":10,"occurredAt":"<ISO8601>","dependsOn":"<SESS_OP_ID>","payload":{"tillSessionClientOpId":"<SESS_OP_ID>","declaredCash":52000}}]}`. Record `first_verdict` and `first_variance` from the session. 3. Query `till_session` status and `variance_amount`. 4. Push identical close op again (same `<CLOSE_OP_ID>`, same body). Record `second_verdict`. 5. Query `till_session` again for status and `variance_amount`. |
| **Expected Result** | Step 2: `first_verdict = "ACCEPTED"`; session status → CLOSED in DB. Step 3: `till_session.status = CLOSED`, `variance_amount = 2000` (52000 declared − 50000 expected). Step 4: `second_verdict = "DUPLICATE"`; `data.results[0].serverEntityId` matches session id. Step 5: session still CLOSED, `variance_amount` unchanged (not doubled); no new cash ledger entry created. |
| **Automatable?** | yes — integration test (`SyncServiceImplTest#tillSessionClose_replay_returnsDuplicate_noDoubleVariance`) |
| **Result/Status** | |
| **Notes/IssueRef** | P0 — duplicate close could corrupt variance calculation or trigger a second Z-report. Guard: `applyTillSessionClose` pre-checks `session.getStatus() == CLOSED`. |

---

### TC-POS-IDEMPOTENCY-003 — POS_SALE + CASH_PICKUP + PETTY_CASH batch, then full re-push: all DUPLICATE

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-003 |
| **Title** | Full batch of POS_SALE + CASH_PICKUP + PETTY_CASH re-pushed returns all DUPLICATE; no additional rows created |
| **Area** | pos/sync |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. Fresh session opened via TILL_SESSION_OPEN op; `SESS_OP_ID` and `<SESSION_ID>` recorded. 3. COKE500 item id resolved as `<COKE_ID>`. 4. Customer id resolved as `<CUST_ID>`. 5. Cashier token. |
| **Steps** | 1. Generate op ids: `SALE_OP="IDMP-003-SALE-$(date +%s%N)"`, `PICKUP_OP="IDMP-003-PU-$(date +%s%N)"`, `PC_OP="IDMP-003-PC-$(date +%s%N)"`. 2. Push all three ops in one batch: `POST /api/v1/sync/push` with ops: (a) `POS_SALE` clientOpId=`<SALE_OP>`, payload: `{"number":"TILL-1-IDMP-003","clientOpId":"<SALE_OP>","tillSessionId":<SESSION_ID>,"sectionId":1,"customerId":<CUST_ID>,"saleAt":"<ISO8601>","lines":[{"itemId":<COKE_ID>,"uomId":1,"qty":2,"unitPrice":1200,"discountPct":0}],"payments":[{"method":"CASH","amount":2400}]}`; (b) `CASH_PICKUP` clientOpId=`<PICKUP_OP>`, payload: `{"tillSessionId":<SESSION_ID>,"amount":10000,"authorisedBy":1,"note":"Mid-day pickup"}`; (c) `PETTY_CASH` clientOpId=`<PC_OP>`, payload: `{"tillSessionId":<SESSION_ID>,"amount":1500,"authorisedBy":1,"category":"OFFICE","paidTo":"Supplier","description":"Stationery"}`. 3. Verify: all 3 verdicts = ACCEPTED. Record `pos_sale` count, `cash_pickup` count, `petty_cash` count, stock qty-on-hand for COKE500. 4. Re-push the identical batch (same body, same clientOpIds). 5. Re-query counts and stock. |
| **Expected Result** | Step 3: all verdicts ACCEPTED; 1 pos_sale, 1 cash_pickup, 1 petty_cash row; stock reduced by 2. Step 4: all verdicts DUPLICATE; `serverEntityId` matches original row ids. Step 5: counts unchanged (no additional rows); stock not double-drained. |
| **Automatable?** | yes — integration test (`SyncServiceImplTest#fullBatch_fullRepush_allDuplicate`) |
| **Result/Status** | |
| **Notes/IssueRef** | P0 — simulates loss-of-response retry: offline POS re-pushes its entire outbox after reconnect. |

---

### TC-POS-IDEMPOTENCY-004 — TILL_SESSION_CLOSE via dedicated endpoint is idempotent

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-004 |
| **Title** | POST /sync/till-session/close called twice with matching manifest returns CLOSED both times; no second variance |
| **Area** | pos/sync |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. Fresh session OPEN via TILL_SESSION_OPEN op; `SESS_OP_ID` recorded and session has at least one accepted POS_SALE op. 3. Sale clientOpId recorded as `<SALE_OP>`. 4. Cashier token. |
| **Steps** | 1. Build manifest: `clientOpIds = ["<SESS_OP_ID>", "<SALE_OP>"]`. 2. First close: `POST /api/v1/sync/till-session/close` headers `$CASH_HDR`, `$BRANCH`, `$CONTRACT`; body: `{"tillSessionClientOpId":"<SESS_OP_ID>","declaredCash":51200,"manifest":{"posSaleCount":1,"posSaleTotal":1200,"cashPickupCount":0,"cashPickupTotal":0,"pettyCashCount":0,"pettyCashTotal":0,"clientOpIds":["<SESS_OP_ID>","<SALE_OP>"]}}`. Record `status`, `variance`, `confirmedClientOpIds`. 3. Repeat identical request (second call). 4. Query `till_session.variance_amount` directly. |
| **Expected Result** | Step 2: HTTP 200; `data.status = "CLOSED"`, `data.variance` is a single consistent value, `data.confirmedClientOpIds` contains both op ids. Step 3: HTTP 200; `data.status = "CLOSED"`, same `variance`, same `confirmedClientOpIds`. Step 4: DB `variance_amount` unchanged (not doubled). |
| **Automatable?** | yes — integration test (`SyncServiceImplTest#closeTillSession_idempotent_noDoubleVariance`) |
| **Result/Status** | |
| **Notes/IssueRef** | Guard: `closeTillSession` checks `session.status == CLOSED/RECONCILED` before re-computing. Source: `SyncServiceImpl.closeTillSession` lines 617–619. |

---

### TC-POS-IDEMPOTENCY-005 — TILL_SESSION_OPEN op idempotency: no second open session on replay

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-005 |
| **Title** | Re-pushing TILL_SESSION_OPEN op returns DUPLICATE; only one till session row created |
| **Area** | pos/sync |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. Till 1 has no existing OPEN session (or use a uniquely identified till). 3. Cashier token. |
| **Steps** | 1. Generate `SESS_OP="IDMP-005-SESS-$(date +%s%N)"`. 2. First push: `POST /api/v1/sync/push` body: `{"deviceId":"DEV-IDMP-005","clientContractVersion":1,"ops":[{"clientOpId":"<SESS_OP>","opType":"TILL_SESSION_OPEN","seq":1,"occurredAt":"<ISO8601>","payload":{"tillId":1,"openingFloatAmount":30000}}]}`. Record `first_verdict` and `serverEntityId`. 3. Query `till_session` count for till_id=1 with status OPEN. 4. Second push: identical request. Record `second_verdict` and `serverEntityId`. 5. Query `till_session` count again. 6. Query cash ledger for float entry count. |
| **Expected Result** | Step 2: `first_verdict = "ACCEPTED"`; `serverEntityId` is a numeric string. Step 3: exactly 1 OPEN session. Step 4: `second_verdict = "DUPLICATE"`; `serverEntityId` is the same id as step 2. Step 5: still exactly 1 session row. Step 6: exactly 1 float (opening) cash_entry — opening float not credited twice. |
| **Automatable?** | yes — integration test (`SyncServiceImplTest#tillSessionOpen_replay_returnsDuplicate_noDuplicateSession`) |
| **Result/Status** | |
| **Notes/IssueRef** | Idempotency key: `sessions.findByCompanyIdAndClientOpId`. Source: `SyncServiceImpl.applyTillSessionOpen`. |

---

### TC-POS-IDEMPOTENCY-006 — CASH_PICKUP op replay: amount not applied twice

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-006 |
| **Title** | Replaying CASH_PICKUP op returns DUPLICATE; pickup amount reflected in session expected cash exactly once |
| **Area** | pos/sync |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. Fresh session OPEN; `<SESSION_ID>` and `<SESS_OP>` recorded. 3. Cashier token. |
| **Steps** | 1. Generate `PU_OP="IDMP-006-PU-$(date +%s%N)"`. 2. First push: `POST /api/v1/sync/push` body: `{"deviceId":"DEV-IDMP-006","clientContractVersion":1,"ops":[{"clientOpId":"<PU_OP>","opType":"CASH_PICKUP","seq":2,"occurredAt":"<ISO8601>","payload":{"tillSessionId":<SESSION_ID>,"amount":8000,"authorisedBy":1,"note":"Cash drop"}}]}`. Record verdict and `serverEntityId`. 3. Query `cash_pickup` sum for session. 4. Second push: identical body. Record verdict. 5. Query `cash_pickup` sum and count. |
| **Expected Result** | Step 2: `verdict = "ACCEPTED"`. Step 3: `cash_pickup` sum = 8000. Step 4: `verdict = "DUPLICATE"`; `serverEntityId` unchanged. Step 5: `cash_pickup` sum still 8000 (not 16000); count = 1. |
| **Automatable?** | yes — integration test (`SyncServiceImplTest#cashPickup_replay_returnsDuplicate_noDoubleAmount`) |
| **Result/Status** | |
| **Notes/IssueRef** | P0 — double-pickup inflates cash-out and corrupts Z-report. Idempotency key: `uk_cash_pickup_client_op` (V78). |

---

## Negative / Boundary (NEG)

### TC-POS-IDEMPOTENCY-007 — POS_SALE_VOID replayed returns DUPLICATE, no double stock reversal

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-007 |
| **Title** | Replaying POS_SALE_VOID op returns DUPLICATE; stock reinstated exactly once |
| **Area** | pos/sync |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. Session OPEN; `<SESSION_ID>` recorded. 3. A POS_SALE op accepted; `<SALE_OP>` recorded, COKE500 qty-on-hand noted as `<QTY_BEFORE_VOID>`. |
| **Steps** | 1. Generate `VOID_OP="IDMP-007-VOID-$(date +%s%N)"`. 2. First void push: `POST /api/v1/sync/push` body: `{"deviceId":"DEV-IDMP-007","clientContractVersion":1,"ops":[{"clientOpId":"<VOID_OP>","opType":"POS_SALE_VOID","seq":3,"occurredAt":"<ISO8601>","payload":{"originalClientOpId":"<SALE_OP>","reason":"Customer changed mind"}}]}`. Record `first_verdict`; note `pos_sale.status`. 3. Query COKE500 qty-on-hand. 4. Second void push: identical body. Record `second_verdict`. 5. Query qty-on-hand again. |
| **Expected Result** | Step 2: `first_verdict = "ACCEPTED"`; `pos_sale.status = VOIDED`. Step 3: qty-on-hand = `<QTY_BEFORE_VOID>` (stock reversed once). Step 4: `second_verdict = "DUPLICATE"` (guard: `applyPosSaleVoid` detects `sale.status == VOIDED`). Step 5: qty-on-hand unchanged from step 3 — not double-reversed. |
| **Automatable?** | yes — integration test (`SyncServiceImplTest#posSaleVoid_replay_returnsDuplicate_noDoubleStockReversal`) |
| **Result/Status** | |
| **Notes/IssueRef** | Source: `SyncServiceImpl.applyPosSaleVoid` lines 338–340. |

---

### TC-POS-IDEMPOTENCY-008 — TILL_SESSION_CLOSE via push with manifest missing an op returns RECONCILE_INCOMPLETE

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-008 |
| **Title** | POST /sync/till-session/close where manifest clientOpIds omit a server-confirmed op returns RECONCILE_INCOMPLETE |
| **Area** | pos/sync |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. Session OPEN; `<SESS_OP>` recorded. 3. Two POS_SALE ops accepted: `<SALE_OP_A>` and `<SALE_OP_B>`. |
| **Steps** | 1. Submit close with manifest listing only `<SESS_OP>` and `<SALE_OP_A>` (omitting `<SALE_OP_B>`): `POST /api/v1/sync/till-session/close` body: `{"tillSessionClientOpId":"<SESS_OP>","declaredCash":50000,"manifest":{"posSaleCount":1,"posSaleTotal":1200,"cashPickupCount":0,"cashPickupTotal":0,"pettyCashCount":0,"pettyCashTotal":0,"clientOpIds":["<SESS_OP>","<SALE_OP_A>"]}}`. 2. Inspect `status`, `unexpectedClientOpIds`. |
| **Expected Result** | HTTP 200; `data.status = "RECONCILE_INCOMPLETE"`; `data.unexpectedClientOpIds` contains `<SALE_OP_B>`; `data.missingClientOpIds` is empty; session remains OPEN (not closed). |
| **Automatable?** | yes — integration test (`SyncServiceImplTest#closeTillSession_missingOpInManifest_returnsReconcileIncomplete`) |
| **Result/Status** | |
| **Notes/IssueRef** | Guard: `closeTillSession` cross-checks `serverSet` vs `clientSet`; on mismatch returns RECONCILE_INCOMPLETE without closing. Source lines 629–643. |

---

### TC-POS-IDEMPOTENCY-009 — TILL_SESSION_CLOSE manifest lists op the server never received

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-009 |
| **Title** | POST /sync/till-session/close where manifest references a clientOpId the server has no record of returns RECONCILE_INCOMPLETE with missingClientOpIds |
| **Area** | pos/sync |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. Session OPEN; `<SESS_OP>` recorded. 3. Exactly one sale accepted: `<SALE_OP>`. |
| **Steps** | 1. Submit close manifest claiming an extra op `"GHOST-OP-9999"` was sent: `POST /api/v1/sync/till-session/close` body: `{"tillSessionClientOpId":"<SESS_OP>","declaredCash":50000,"manifest":{"posSaleCount":2,"posSaleTotal":2400,"cashPickupCount":0,"cashPickupTotal":0,"pettyCashCount":0,"pettyCashTotal":0,"clientOpIds":["<SESS_OP>","<SALE_OP>","GHOST-OP-9999"]}}`. 2. Inspect response. |
| **Expected Result** | HTTP 200; `data.status = "RECONCILE_INCOMPLETE"`; `data.missingClientOpIds` contains `"GHOST-OP-9999"`; `data.unexpectedClientOpIds` is empty; session remains OPEN. |
| **Automatable?** | yes — integration test (`SyncServiceImplTest#closeTillSession_ghostOpInManifest_returnsReconcileIncomplete_withMissingList`) |
| **Result/Status** | |
| **Notes/IssueRef** | Common scenario: op pushed but lost in transit; client must re-push the missing op then retry close. |

---

### TC-POS-IDEMPOTENCY-010 — PETTY_CASH op with invalid category rejected; idempotency key NOT committed

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-010 |
| **Title** | PETTY_CASH op with unknown category returns REJECTED; no petty_cash row created; clientOpId remains available for a corrected re-push |
| **Area** | pos/sync |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. Session OPEN; `<SESSION_ID>` recorded. 3. Cashier token. |
| **Steps** | 1. Generate `PC_OP="IDMP-010-PC-$(date +%s%N)"`. 2. Push with invalid category: `POST /api/v1/sync/push` body: `{"deviceId":"DEV-IDMP-010","clientContractVersion":1,"ops":[{"clientOpId":"<PC_OP>","opType":"PETTY_CASH","seq":2,"occurredAt":"<ISO8601>","payload":{"tillSessionId":<SESSION_ID>,"amount":500,"authorisedBy":1,"category":"INVALID_CATEGORY","paidTo":"Someone","description":"Test"}}]}`. Record verdict and errorCode. 3. Push same clientOpId with a valid category (`"TRANSPORT"`): `POST /api/v1/sync/push` body identical except `"category":"TRANSPORT"`. Record second verdict. |
| **Expected Result** | Step 2: `verdict = "REJECTED"`; `errorCode = "BUSINESS_RULE_VIOLATION"`; error message references unknown PettyCashCategory; no `petty_cash` row in DB for `<PC_OP>`. Step 3: `verdict = "ACCEPTED"` — REJECTED ops do NOT consume the idempotency key; the corrected re-push succeeds. |
| **Automatable?** | yes — integration test (`SyncServiceImplTest#pettyCash_invalidCategory_rejected_keyNotConsumed`) |
| **Result/Status** | |
| **Notes/IssueRef** | Valid categories: TRANSPORT, OFFICE, MAINTENANCE, OTHER. Critical UX: if REJECTED consumed the key, the cashier could never correct a bad op. |

---

### TC-POS-IDEMPOTENCY-011 — dependsOn: op before its session in same batch is DEFERRED

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-011 |
| **Title** | POS_SALE op with dependsOn pointing to a TILL_SESSION_OPEN op not yet in the batch or unsettled is DEFERRED |
| **Area** | pos/sync |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. Cashier token. |
| **Steps** | 1. Generate `SESS_OP="IDMP-011-SESS-$(date +%s%N)"` and `SALE_OP="IDMP-011-SALE-$(date +%s%N)"`. 2. Push a batch where `<SALE_OP>` is listed BEFORE `<SESS_OP>` (wrong order) with `dependsOn = "<SESS_OP>"`: `POST /api/v1/sync/push` body: `{"deviceId":"DEV-IDMP-011","clientContractVersion":1,"ops":[{"clientOpId":"<SALE_OP>","opType":"POS_SALE","seq":2,"occurredAt":"<ISO8601>","dependsOn":"<SESS_OP>","payload":{...}},{"clientOpId":"<SESS_OP>","opType":"TILL_SESSION_OPEN","seq":1,"occurredAt":"<ISO8601>","payload":{"tillId":1,"openingFloatAmount":20000}}]}`. Note: `<SESS_OP>` is second in array even though seq is lower. |
| **Expected Result** | HTTP 200; `<SALE_OP>` result `verdict = "DEFERRED"` (dependsOn not yet settled when it was processed); `<SESS_OP>` result `verdict = "ACCEPTED"` (processed after). `data.batchRejectedCount` does not include DEFERRED ops. No pos_sale row for `<SALE_OP>`. |
| **Automatable?** | yes — unit test (`SyncServiceImplTest#dependsOn_orderingEnforced_deferredWhenPredecessorLater`) |
| **Result/Status** | |
| **Notes/IssueRef** | Client must re-push `<SALE_OP>` next cycle; by that time `<SESS_OP>` is in `settled`. Source: `SyncServiceImpl.pushBatch` lines 140–144. |

---

### TC-POS-IDEMPOTENCY-012 — dependsOn: op referencing an external (prior-batch) settled op is accepted

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-012 |
| **Title** | POS_SALE with dependsOn referencing a clientOpId from a previous batch (already ACCEPTED on server) proceeds as ACCEPTED |
| **Area** | pos/sync |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. `<SESS_OP>` already ACCEPTED in a prior push; `<SESSION_ID>` and `<COKE_ID>` known. 3. Cashier token. |
| **Steps** | 1. Generate `SALE_OP="IDMP-012-SALE-$(date +%s%N)"`. 2. Push new batch with only the sale, referencing the prior session: `POST /api/v1/sync/push` body: `{"deviceId":"DEV-IDMP-012","clientContractVersion":1,"ops":[{"clientOpId":"<SALE_OP>","opType":"POS_SALE","seq":5,"occurredAt":"<ISO8601>","dependsOn":"<SESS_OP>","payload":{"number":"TILL-1-IDMP-012","clientOpId":"<SALE_OP>","tillSessionId":<SESSION_ID>,"sectionId":1,"customerId":<CUST_ID>,"saleAt":"<ISO8601>","lines":[{"itemId":<COKE_ID>,"uomId":1,"qty":1,"unitPrice":1200,"discountPct":0}],"payments":[{"method":"CASH","amount":1200}]}}]}`. |
| **Expected Result** | HTTP 200; `verdict = "ACCEPTED"`. Note: `dependsOn` is satisfied because `<SESS_OP>` is not in this batch's `settled` set — this test validates that the `dependsOn` guard only DEFERS when the dependency is absent from the current batch AND not found on server. Implementation note: the current `settled` set is per-batch, so a cross-batch dependsOn referencing a prior-batch op that is not in the current `settled` set would be DEFERRED. If so, document as a known behaviour and recommend dropping `dependsOn` for cross-batch references. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | This case probes whether `dependsOn` is evaluated only within the current batch (`settled` set) or also against the DB. Source lines 140–144: `settled` is local to the batch. Cross-batch dependsOn resolves as DEFERRED on first call; client should re-push without `dependsOn` for cross-batch ops. Document finding. |

---

## Security / Tenant Isolation (SEC)

### TC-POS-IDEMPOTENCY-013 — clientOpId from company A cannot be replayed as DUPLICATE by company B

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-013 |
| **Title** | Company B cashier pushing a clientOpId that already exists in company A gets ACCEPTED (not DUPLICATE); data is tenant-isolated |
| **Area** | pos/sync |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-009, US-POS-018 |
| **Preconditions** | 1. Two companies exist (company A = the default QA company; company B = a second company with its own cashier token). 2. `<SHARED_OP_ID>` is a clientOpId already ACCEPTED for company A. 3. Company B cashier has equivalent permissions and an open session. |
| **Steps** | 1. Confirm `<SHARED_OP_ID>` was accepted for company A: query `petty_cash` where `company_id = <A_ID>` and `client_op_id = <SHARED_OP_ID>`. 2. Company B cashier pushes a PETTY_CASH op reusing exactly `<SHARED_OP_ID>`: `POST /api/v1/sync/push` with company B token, body: `{"deviceId":"DEV-IDMP-013-B","clientContractVersion":1,"ops":[{"clientOpId":"<SHARED_OP_ID>","opType":"PETTY_CASH","seq":1,"occurredAt":"<ISO8601>","payload":{"tillSessionId":<B_SESSION_ID>,"amount":500,"authorisedBy":1,"category":"OTHER","paidTo":"X","description":"B petty"}}]}`. |
| **Expected Result** | HTTP 200; company B result `verdict = "ACCEPTED"` (not DUPLICATE — the idempotency check is per company_id). Company A's `petty_cash` row untouched. Company B gets its own new `petty_cash` row. No cross-tenant data visible. |
| **Automatable?** | yes — integration test (requires second company fixture) |
| **Result/Status** | concurrencyRisk=possible |
| **Notes/IssueRef** | P0 — DUPLICATE across tenants = data leak or denial of service. Idempotency query: `findByCompanyIdAndClientOpId` — compound key enforces isolation. Source: `applyPettyCash` line 292. |

---

### TC-POS-IDEMPOTENCY-014 — User without POS.SYNC cannot push any op, including idempotent replays

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-014 |
| **Title** | Push op (first push or replay) without POS.SYNC permission returns 403; no op processed |
| **Area** | pos/sync |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-009, US-POS-018 |
| **Preconditions** | 1. User `testuser-noperm` created with no permissions. 2. Token for `testuser-noperm` obtained. |
| **Steps** | 1. `POST /api/v1/sync/push` headers: `Authorization: Bearer <NOPERM_TOKEN>`, `X-Branch-Id: 1`, `X-Orbix-Contract-Version: 1`; body: `{"deviceId":"DEV-IDMP-014","clientContractVersion":1,"ops":[{"clientOpId":"IDMP-014-OP","opType":"PETTY_CASH","seq":1,"occurredAt":"<ISO8601>","payload":{"tillSessionId":1,"amount":100,"authorisedBy":1,"category":"OTHER","paidTo":"X","description":"Auth test"}}]}`. |
| **Expected Result** | HTTP 403; body contains error referencing insufficient authority; no `petty_cash` row created; no till session touched. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Gate: `@PreAuthorize("hasAuthority('POS.SYNC')")` on SyncController class-level. |

---

## Data Integrity (DATA)

### TC-POS-IDEMPOTENCY-015 — Concurrent concurrent replay of same POS_SALE clientOpId: exactly one ACCEPTED, one DUPLICATE

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-015 |
| **Title** | Two simultaneous push requests carrying the same POS_SALE clientOpId produce one ACCEPTED and one DUPLICATE; no duplicate pos_sale row |
| **Area** | pos/sync |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. Session OPEN; `<SESSION_ID>` and `<COKE_ID>` known. 3. Cashier token valid. |
| **Steps** | 1. Generate `CONCURRENT_OP="IDMP-015-CONC-$(date +%s%N)"`. 2. Fire two concurrent HTTP requests simultaneously (e.g. using `curl` in parallel with `&`): both `POST /api/v1/sync/push` with identical bodies (same `<CONCURRENT_OP>`, same sale payload). 3. Collect both responses. 4. Query `pos_sale` count for `<CONCURRENT_OP>`. |
| **Expected Result** | Exactly one response has `verdict = "ACCEPTED"`, the other `verdict = "DUPLICATE"`. Exactly 1 `pos_sale` row for `<CONCURRENT_OP>`. No `DataIntegrityViolationException` leaks in HTTP response body (caught and converted to DUPLICATE internally per `applyPosSale` lines 222–230). |
| **Automatable?** | partial — integration test with two concurrent threads; full determinism requires forcing a race (Thread.sleep in test or DB delay injection) |
| **Result/Status** | |
| **Notes/IssueRef** | P0 — race between concurrent POS terminals or network retry. Guard: `uk_pos_sale_client_op` + `DataIntegrityViolationException` catch-and-reload in `applyPosSale`. |

---

## Reliability (RELI)

### TC-POS-IDEMPOTENCY-016 — Contract version mismatch rejects push before any idempotency processing

| Field | Value |
|-------|-------|
| **ID** | TC-POS-IDEMPOTENCY-016 |
| **Title** | Push with X-Orbix-Contract-Version: 0 (below server minimum) returns 426 before any op is processed |
| **Area** | pos/sync |
| **Dimension** | RELI |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Server contract version = 1 (default). 2. Cashier token valid. |
| **Steps** | 1. `POST /api/v1/sync/push` headers: `$CASH_HDR`, `$BRANCH`, `X-Orbix-Contract-Version: 0`; body: `{"deviceId":"DEV-IDMP-016","clientContractVersion":0,"ops":[{"clientOpId":"IDMP-016-OP","opType":"PETTY_CASH","seq":1,"occurredAt":"<ISO8601>","payload":{"tillSessionId":1,"amount":100,"authorisedBy":1,"category":"OTHER","paidTo":"X","description":"Test"}}]}`. |
| **Expected Result** | HTTP 426 Upgrade Required; body contains `"CONTRACT_TOO_OLD"`; no op processed; no petty_cash row created. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard: `SyncController.validateContractVersion` lines 131–143. Ensures stale POS apps are blocked before touching data. |
