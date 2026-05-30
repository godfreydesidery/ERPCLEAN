# TC-CONSUMPTION-BATCH — Internal Consumption and Stock Batch / FEFO Management

**Module:** stock  
**Stories:** US-STOCK-005, US-STOCK-006, US-STOCK-007  
**API base:** `http://localhost:8081/api/v1`  
**Permissions required:** `STOCK.INTERNAL_CONSUMPTION`, `STOCK.BATCH`

---

### TC-CONSUMPTION-BATCH-001 — Happy-path internal consumption decrements on-hand

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-001 |
| **Title** | POST /internal-consumption creates INTERNAL_CONSUMPTION move and reduces qty_on_hand |
| **Area** | consumption-batch |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-005 |
| **Preconditions** | 1. Business day OPEN at branch 1. 2. Item COKE500 has qty_on_hand >= 5 at branch 1. 3. Token for `rootadmin` (holds both `STOCK.INTERNAL_CONSUMPTION` and `STOCK.INTERNAL_CONSUMPTION_APPROVE`). 4. A second user `authoriser` exists who holds `STOCK.INTERNAL_CONSUMPTION` and is NOT the caller. Resolve COKE500 item id via `GET /api/v1/items?q=COKE500`. Resolve a valid sectionId via `GET /api/v1/sections`. |
| **Steps** | 1. Record pre-consumption balance: `GET /api/v1/stock?itemId=<cokeId>&branchId=1` — note `qtyOnHand`. 2. `POST /api/v1/internal-consumption` with header `Authorization: Bearer <rootadmin-token>` and body: `{"itemId":<cokeId>,"branchId":1,"qty":3,"consumptionCategory":"CANTEEN","sectionId":<sectionId>,"authorisedByUserId":<authoriserId>,"reason":"CB-001 staff canteen draw","batchId":null}`. 3. Record response body. 4. `GET /api/v1/stock?itemId=<cokeId>&branchId=1` — note new `qtyOnHand`. 5. `GET /api/v1/stock-report/card?itemId=<cokeId>&branchId=1` — inspect last move row. |
| **Expected Result** | Step 2: HTTP 200; response is `ApiResponse<StockMoveDto>` with `data.moveType = "INTERNAL_CONSUMPTION"`, `data.qty` is negative (−3), `data.consumptionCategory = "CANTEEN"`, `data.authorisedByUserId = <authoriserId>`. Step 4: `qtyOnHand = pre_balance − 3`. Step 5: stock card contains one row with `moveType=INTERNAL_CONSUMPTION`, `qty=−3`, matching `reason`. No customer reference — `refType` is `"InternalConsumption"`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | P0 — silent stock reduction path; wrong qty here is undetected without this test. concurrencyRisk=possible. |

---

### TC-CONSUMPTION-BATCH-002 — Caller cannot self-authorise consumption

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-002 |
| **Title** | POST /internal-consumption with authorisedByUserId == caller id is rejected |
| **Area** | consumption-batch |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-005 |
| **Preconditions** | 1. Resolve `rootadmin` user id via `GET /api/v1/users/me` or `GET /api/v1/users?username=rootadmin`. 2. Business day OPEN at branch 1. |
| **Steps** | 1. `POST /api/v1/internal-consumption` body: `{"itemId":<cokeId>,"branchId":1,"qty":2,"consumptionCategory":"SAMPLES","sectionId":<sectionId>,"authorisedByUserId":<rootadmin-userId>,"reason":"CB-002 self-auth attempt"}` — where `authorisedByUserId` equals the id of the authenticated `rootadmin` caller. |
| **Expected Result** | HTTP 400 (or 422); `responseCode` indicates validation failure; message contains "cannot authorise your own". `qty_on_hand` unchanged — no `stock_move` row written. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard in `InternalConsumptionServiceImpl`: `Objects.equals(request.authorisedByUserId(), actorId)` → `IllegalArgumentException`. |

---

### TC-CONSUMPTION-BATCH-003 — Authoriser must hold STOCK.INTERNAL_CONSUMPTION permission

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-003 |
| **Title** | POST /internal-consumption with authoriser lacking permission is rejected with 403-equivalent |
| **Area** | consumption-batch |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-005 |
| **Preconditions** | 1. Create a test user `CB003-USER-<entropy>` with no stock permissions and note their id. 2. Caller is `rootadmin`. 3. Business day OPEN. |
| **Steps** | 1. `POST /api/v1/internal-consumption` body: `{"itemId":<cokeId>,"branchId":1,"qty":2,"consumptionCategory":"DISPLAY","sectionId":<sectionId>,"authorisedByUserId":<unprivilegedUserId>,"reason":"CB-003 unpriv authoriser"}`. |
| **Expected Result** | HTTP 403 (or 422); message references authoriser id and `STOCK.INTERNAL_CONSUMPTION`. No `stock_move` row created. `qty_on_hand` unchanged. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard in `InternalConsumptionServiceImpl.postInternalConsumption` — `PermissionResolverService.resolve` check. |

---

### TC-CONSUMPTION-BATCH-004 — Consumption request without permission token is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-004 |
| **Title** | POST /internal-consumption without STOCK.INTERNAL_CONSUMPTION returns 403 |
| **Area** | consumption-batch |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-005 |
| **Preconditions** | POS cashier token (`cashier` / `Cashier#2026`) — does not hold `STOCK.INTERNAL_CONSUMPTION`. |
| **Steps** | 1. Obtain cashier token: `POST /api/v1/auth/login` body `{"username":"cashier","password":"Cashier#2026"}`. 2. `POST /api/v1/internal-consumption` with cashier token body: `{"itemId":<cokeId>,"branchId":1,"qty":1,"consumptionCategory":"OTHER","sectionId":<sectionId>,"authorisedByUserId":<anyOtherId>,"reason":"CB-004 unauth attempt"}`. |
| **Expected Result** | HTTP 403; no stock move written. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `@PreAuthorize("hasAuthority('STOCK.INTERNAL_CONSUMPTION')")` on controller. |

---

### TC-CONSUMPTION-BATCH-005 — Consumption of zero or negative qty is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-005 |
| **Title** | POST /internal-consumption with qty <= 0 returns validation error |
| **Area** | consumption-batch |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-005 |
| **Preconditions** | `rootadmin` token. Business day OPEN. |
| **Steps** | 1. POST with `qty=0`. 2. POST with `qty=-5`. 3. POST with missing `reason` field (omit key). 4. POST with missing `consumptionCategory`. |
| **Expected Result** | Each attempt: HTTP 400; `errors[]` array non-empty; field name in error. No `stock_move` row created in any case. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | `@Positive` on `qty`, `@NotBlank` on `reason`, `@NotNull` on `consumptionCategory` in `PostInternalConsumptionRequestDto`. |

---

### TC-CONSUMPTION-BATCH-006 — Consumption blocked when business day is CLOSED

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-006 |
| **Title** | POST /internal-consumption while business day CLOSED returns error |
| **Area** | consumption-batch |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-005 |
| **Preconditions** | BLOCKED(needs-exclusive-state) — requires closing the shared business day, which interferes with other runners. Mark BLOCKED unless run in an isolated container. |
| **Steps** | 1. Close business day for branch 1. 2. `POST /api/v1/internal-consumption` with valid payload. 3. Reopen business day. |
| **Expected Result** | Step 2: HTTP 422; message references business day not open. `qty_on_hand` unchanged. |
| **Automatable?** | yes — integration test (isolated env only) |
| **Result/Status** | BLOCKED(needs-exclusive-state) |
| **Notes/IssueRef** | `DayGuard.requireOpenDay` called from `StockMoveServiceImpl.post`. |

---

### TC-CONSUMPTION-BATCH-007 — All six ConsumptionCategory values accepted

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-007 |
| **Title** | POST /internal-consumption accepts all ConsumptionCategory enum values |
| **Area** | consumption-batch |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | US-STOCK-005 |
| **Preconditions** | `rootadmin` token. Business day OPEN. Item with qty_on_hand >= 6. |
| **Steps** | For each category in `[CANTEEN, DISPLAY, SAMPLES, DONATION, MAINTENANCE, OTHER]`: POST `/api/v1/internal-consumption` body `{"itemId":<id>,"branchId":1,"qty":1,"consumptionCategory":"<CAT>","sectionId":<id>,"authorisedByUserId":<authoriserId>,"reason":"CB-007 <CAT>"}`. |
| **Expected Result** | Each POST: HTTP 200; `data.consumptionCategory = "<CAT>"` in response. Six `stock_move` rows written with correct category stamps. |
| **Automatable?** | yes — unit test (parameterised) |
| **Result/Status** | |
| **Notes/IssueRef** | Validates consumption-report grouping column will have all categories covered. |

---

### TC-CONSUMPTION-BATCH-008 — GET /stock-batches lists batches with FEFO ordering

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-008 |
| **Title** | GET /stock-batches returns paginated batch list ordered by expiry_at ASC then id ASC |
| **Area** | consumption-batch |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-006 |
| **Preconditions** | At least two ACTIVE batches exist for branch 1 with different `expiryAt` dates. (Create via GRN flow or direct batch creation if available.) |
| **Steps** | 1. `GET /api/v1/stock-batches?branchId=1&status=ACTIVE&page=0&size=20` with `rootadmin` token. 2. Inspect `data.content[*].expiryAt` ordering. |
| **Expected Result** | HTTP 200; `data.content` non-empty; `expiryAt` values are in ascending order (earlier expiry first, nulls last). `data.totalElements >= 2`. Each element contains `id`, `uid`, `itemId`, `branchId`, `status`, `qtyOnHand`, `qtyReceived`, `expiryAt`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | FEFO ordering correctness is critical — wrong order means wrong batch consumed first. |

---

### TC-CONSUMPTION-BATCH-009 — GET /stock-batches/expiring-soon respects daysAhead window

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-009 |
| **Title** | GET /stock-batches/expiring-soon returns only ACTIVE batches with expiryAt within daysAhead |
| **Area** | consumption-batch |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-006 |
| **Preconditions** | At least one ACTIVE batch with `expiryAt` within 10 days of today, and one ACTIVE batch with `expiryAt` > 30 days from today. |
| **Steps** | 1. `GET /api/v1/stock-batches/expiring-soon?branchId=1&daysAhead=10` with `rootadmin` token. 2. `GET /api/v1/stock-batches/expiring-soon?branchId=1&daysAhead=30`. |
| **Expected Result** | Step 1: response array contains only batches with `expiryAt <= today + 10 days`, all with `status=ACTIVE`. Step 2: same near-expiry batch appears, plus the one within 30 days; the far-future batch does NOT appear in either response. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `listExpiringSoon` uses exclusive cutoff `LocalDate.now().plusDays(daysAhead).plusDays(1)`. Batches with null `expiryAt` are filtered out. |

---

### TC-CONSUMPTION-BATCH-010 — GET /stock-batches/expiring-soon with negative daysAhead is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-010 |
| **Title** | GET /stock-batches/expiring-soon with daysAhead < 0 returns error |
| **Area** | consumption-batch |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-STOCK-006 |
| **Preconditions** | `rootadmin` token. |
| **Steps** | 1. `GET /api/v1/stock-batches/expiring-soon?daysAhead=-1` with `rootadmin` token. |
| **Expected Result** | HTTP 400 or 422; message contains "daysAhead" and ">=0". No batches returned. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard in `StockBatchServiceImpl.listExpiringSoon`: `if (daysAhead < 0) throw IllegalArgumentException`. |

---

### TC-CONSUMPTION-BATCH-011 — GET /stock-batches/uid/:uid returns single batch

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-011 |
| **Title** | GET /stock-batches/uid/{uid} returns correct StockBatchDto by ULID |
| **Area** | consumption-batch |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-006 |
| **Preconditions** | At least one batch exists. Obtain its `uid` from `GET /stock-batches`. |
| **Steps** | 1. `GET /api/v1/stock-batches?page=0&size=1` to get a batch `uid`. 2. `GET /api/v1/stock-batches/uid/<uid>` with `rootadmin` token. 3. `GET /api/v1/stock-batches/uid/00000000000000000000000000` (invalid-format ULID — all zeros but valid Crockford length). |
| **Expected Result** | Step 2: HTTP 200; all fields present (`id`, `uid`, `itemId`, `branchId`, `batchNo`, `status`, `qtyOnHand`, `qtyReceived`, `cost`, `expiryAt`). Ids serialised as JSON strings. Step 3: HTTP 404. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `@ValidUlid` on path variable — malformed ULID returns 400, not-found returns 404. |

---

### TC-CONSUMPTION-BATCH-012 — Batch recall writes EXPIRY_WRITE_OFF move and marks RECALLED

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-012 |
| **Title** | POST /stock-batches/uid/{uid}/recall creates compensating EXPIRY_WRITE_OFF move and flips batch to RECALLED |
| **Area** | consumption-batch |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-007 |
| **Preconditions** | 1. An ACTIVE batch exists with `qtyOnHand > 0`. Note its `uid`, `itemId`, `branchId`, `qtyOnHand`. 2. Record `item_branch_balance.qty_on_hand` before recall. 3. Business day OPEN. 4. `rootadmin` token with `STOCK.BATCH`. |
| **Steps** | 1. `POST /api/v1/stock-batches/uid/<uid>/recall` body: `{"reason":"CB-012 supplier quality recall"}`. 2. `GET /api/v1/stock-batches/uid/<uid>` — inspect status and `qtyOnHand`. 3. `GET /api/v1/stock?itemId=<itemId>&branchId=<branchId>` — inspect `qtyOnHand`. 4. `GET /api/v1/stock-report/card?itemId=<itemId>&branchId=<branchId>` — find recall move. |
| **Expected Result** | Step 1: HTTP 200; response `data.status = "RECALLED"`, `data.qtyOnHand = 0`. Step 2: same — status RECALLED, qtyOnHand 0. Step 3: `item_branch_balance.qty_on_hand` decreased by exactly the recalled quantity (the quantity that was `qtyOnHand` before recall). Step 4: a `stock_move` row with `moveType=EXPIRY_WRITE_OFF`, `qty = −<prior_qtyOnHand>`, `refType="StockBatch"`, `refId=<batchId>`, `notes="CB-012 supplier quality recall"`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | P0 — recall is the compensating move for contaminated/expired stock; silent failure = phantom on-hand. concurrencyRisk=possible. |

---

### TC-CONSUMPTION-BATCH-013 — Recall of EXHAUSTED or RECALLED batch is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-013 |
| **Title** | POST /stock-batches/uid/{uid}/recall on a non-ACTIVE batch returns error |
| **Area** | consumption-batch |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-007 |
| **Preconditions** | 1. An EXHAUSTED batch exists (qtyOnHand=0, status=EXHAUSTED). Note its `uid`. 2. A previously RECALLED batch exists. Note its `uid`. |
| **Steps** | 1. `POST /api/v1/stock-batches/uid/<exhausted-uid>/recall` body `{"reason":"CB-013 re-recall exhausted"}`. 2. `POST /api/v1/stock-batches/uid/<recalled-uid>/recall` body `{"reason":"CB-013 re-recall recalled"}`. |
| **Expected Result** | Both: HTTP 400 or 422; message references batch status (e.g., "Cannot write off a EXHAUSTED batch"). No additional `stock_move` rows written. `item_branch_balance` unchanged. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | `StockBatch.writeOffRemaining` throws `IllegalStateException` when `status != ACTIVE`. |

---

### TC-CONSUMPTION-BATCH-014 — Recall with missing reason is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-014 |
| **Title** | POST /stock-batches/uid/{uid}/recall with blank reason returns 400 |
| **Area** | consumption-batch |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-STOCK-007 |
| **Preconditions** | An ACTIVE batch `uid` is available. `rootadmin` token. |
| **Steps** | 1. `POST /api/v1/stock-batches/uid/<uid>/recall` body `{"reason":""}`. 2. `POST /api/v1/stock-batches/uid/<uid>/recall` body `{}` (missing reason). |
| **Expected Result** | Both: HTTP 400; `errors[]` references `reason`; `@NotBlank` violation. Batch status unchanged. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | `@NotBlank String reason` in `RecallStockBatchRequestDto`. |

---

### TC-CONSUMPTION-BATCH-015 — Batch endpoint isolated to caller's company (tenant isolation)

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-015 |
| **Title** | GET /stock-batches/uid/{uid} for a batch belonging to a different company returns 404 |
| **Area** | consumption-batch |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-006 |
| **Preconditions** | BLOCKED(needs-exclusive-state) — requires a second company tenant in the same container. Mark BLOCKED unless multi-tenant fixture is available. Alternatively, simulate by using a batch id known to belong to company 2 and a token scoped to company 1. |
| **Steps** | 1. Obtain a valid `uid` for a batch belonging to company 2. 2. Authenticate as a user of company 1. 3. `GET /api/v1/stock-batches/uid/<company-2-uid>`. 4. `POST /api/v1/stock-batches/uid/<company-2-uid>/recall` body `{"reason":"CB-015 cross-tenant attempt"}`. |
| **Expected Result** | Both step 3 and 4: HTTP 404 (not 403 — guard uses `NoSuchElementException` masked lookup). No data from company 2 is exposed. |
| **Automatable?** | yes — integration test (multi-tenant fixture required) |
| **Result/Status** | BLOCKED(needs-exclusive-state) |
| **Notes/IssueRef** | `StockBatchServiceImpl.requireBatchByUid` checks `batch.getCompanyId() == context.companyId()` and returns 404 if mismatched. |

---

### TC-CONSUMPTION-BATCH-016 — FEFO drain order: earliest-expiry batch consumed first

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-016 |
| **Title** | Internal consumption with batchId=null drains from the FEFO-earliest ACTIVE batch first |
| **Area** | consumption-batch |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-006, US-STOCK-005 |
| **Preconditions** | 1. Item CB016-ITEM-`<entropy>` has two ACTIVE batches at branch 1: Batch-A expiryAt=2026-06-01 qtyOnHand=5, Batch-B expiryAt=2026-12-31 qtyOnHand=5. 2. Business day OPEN. 3. `rootadmin` token. Note: use unique item to avoid cross-runner interference. concurrencyRisk=possible. |
| **Steps** | 1. Note Batch-A `id` and Batch-B `id` before consuming. 2. `POST /api/v1/internal-consumption` body: `{"itemId":<itemId>,"branchId":1,"qty":3,"consumptionCategory":"CANTEEN","sectionId":<sectionId>,"authorisedByUserId":<authoriserId>,"reason":"CB-016 FEFO test","batchId":null}`. 3. `GET /api/v1/stock-batches/uid/<batch-A-uid>` — check `qtyOnHand`. 4. `GET /api/v1/stock-batches/uid/<batch-B-uid>` — check `qtyOnHand`. |
| **Expected Result** | Step 3: Batch-A `qtyOnHand = 2` (drained by 3). Step 4: Batch-B `qtyOnHand = 5` (untouched). The `stock_move` returned in step 2 has `batchId = Batch-A.id`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | P0 — FEFO correctness is the core safety property. Wrong drain order causes expired stock to remain in rotation. `drainFefo` orders by `expiryAt ASC, id ASC`. |

---

### TC-CONSUMPTION-BATCH-017 — FEFO drain spanning multiple batches

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-017 |
| **Title** | Consumption qty exceeding first FEFO batch drains remainder from next batch |
| **Area** | consumption-batch |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-006, US-STOCK-005 |
| **Preconditions** | Item CB017-ITEM-`<entropy>` has two ACTIVE batches: Batch-X expiryAt=2026-06-10 qtyOnHand=3, Batch-Y expiryAt=2026-11-30 qtyOnHand=10. Business day OPEN. concurrencyRisk=possible. |
| **Steps** | 1. `POST /api/v1/internal-consumption` with `qty=7`, `batchId=null`, reason `"CB-017 cross-batch FEFO"`. 2. `GET /api/v1/stock-batches/uid/<batch-X-uid>`. 3. `GET /api/v1/stock-batches/uid/<batch-Y-uid>`. |
| **Expected Result** | Step 2: Batch-X `qtyOnHand=0`, `status=EXHAUSTED`. Step 3: Batch-Y `qtyOnHand=6` (7−3=4 drained from Y). Overall `item_branch_balance.qty_on_hand` reduced by 7. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `drainFefo` loops batches in FEFO order; batch exhausted mid-drain transitions to EXHAUSTED and emits `StockBatchExhausted.v1`. |

---

### TC-CONSUMPTION-BATCH-018 — FEFO drain fails when aggregate available < requested qty

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-018 |
| **Title** | Internal consumption rejected when item has insufficient aggregate on-hand across all batches |
| **Area** | consumption-batch |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-005 |
| **Preconditions** | Item CB018-ITEM-`<entropy>` has total ACTIVE batch qtyOnHand=2 at branch 1. Business day OPEN. concurrencyRisk=possible. |
| **Steps** | 1. `POST /api/v1/internal-consumption` with `qty=10`, `batchId=null`, reason `"CB-018 oversell attempt"`. 2. `GET /api/v1/stock?itemId=<id>&branchId=1` — verify balance unchanged. |
| **Expected Result** | Step 1: HTTP 400 or 422; message references "Insufficient active batches" and item id. Step 2: `qtyOnHand` unchanged at 2. No `stock_move` written (transaction rolled back). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | P0 — `drainFefo` throws `IllegalArgumentException("Insufficient active batches...")` when `remaining.signum() > 0` at end of loop. Must be transactionally atomic — no partial drain survives. |

---

### TC-CONSUMPTION-BATCH-019 — Recall of zero-qty ACTIVE batch writes no stock move

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-019 |
| **Title** | POST /stock-batches/uid/{uid}/recall on ACTIVE batch with qtyOnHand=0 marks RECALLED without writing a move |
| **Area** | consumption-batch |
| **Dimension** | DATA |
| **Priority** | P2 |
| **Linked US-*** | US-STOCK-007 |
| **Preconditions** | An ACTIVE batch with `qtyOnHand=0` exists (edge case: batch fully consumed via FEFO but not yet flipped to EXHAUSTED, or manually constructed). Note its `uid` and `itemId`. |
| **Steps** | 1. Record `stock_move` count for item before recall. 2. `POST /api/v1/stock-batches/uid/<uid>/recall` body `{"reason":"CB-019 zero-qty recall"}`. 3. `GET /api/v1/stock-batches/uid/<uid>`. 4. Verify `stock_move` count for item unchanged. |
| **Expected Result** | Step 2: HTTP 200; `data.status=RECALLED`, `data.qtyOnHand=0`. Step 3: confirms RECALLED. Step 4: no new `stock_move` row written (guard: `if (remaining.signum() > 0)` in `recall()`). `item_branch_balance` unchanged. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | `StockBatchServiceImpl.recall`: compensating move is only posted when `remaining.signum() > 0`. |

---

### TC-CONSUMPTION-BATCH-020 — STOCK.BATCH permission gates batch endpoints

| Field | Value |
|-------|-------|
| **ID** | TC-CONSUMPTION-BATCH-020 |
| **Title** | GET /stock-batches and POST recall return 403 without STOCK.BATCH permission |
| **Area** | consumption-batch |
| **Dimension** | SEC |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-006, US-STOCK-007 |
| **Preconditions** | POS cashier token (no `STOCK.BATCH`). A known batch `uid`. |
| **Steps** | 1. `GET /api/v1/stock-batches` with cashier token. 2. `GET /api/v1/stock-batches/expiring-soon` with cashier token. 3. `GET /api/v1/stock-batches/uid/<uid>` with cashier token. 4. `POST /api/v1/stock-batches/uid/<uid>/recall` with cashier token body `{"reason":"CB-020 auth test"}`. |
| **Expected Result** | All four requests: HTTP 403. No data returned. Batch status unchanged. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `@PreAuthorize("hasAuthority('STOCK.BATCH')")` on `StockBatchController`. |
