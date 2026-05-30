# TC-POS-SYNC — POS Offline Sync: Push & Pull

**Module:** pos / sync  
**Stories:** US-POS-017, US-POS-018, US-CAT-013  
**API base:** `http://localhost:8081/api/v1`  
**Auth:** cashier / Cashier#2026, X-Branch-Id: 1  
**Key source:** `SyncServiceImpl.java`, migrations V78 (sync_spine), docs/design/slice-sync-spine.md

---

## Push — Happy Path

### TC-POS-SYNC-001 — Push a single POS_SALE op; server accepts and posts

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-001 |
| **Title** | Push batch with one POS_SALE op; server accepts, posts sale, returns ACCEPTED |
| **Area** | pos/sync |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 1. Business day OPEN. 2. Till session OPEN (opened via TILL_SESSION_OPEN op or directly). 3. Item COKE500 in stock. 4. Cashier token + X-Branch-Id:1. |
| **Steps** | 1. Construct `clientOpId = "sync-test-001"`. 2. `POST /api/v1/sync/push` body: `{"ops":[{"clientOpId":"sync-test-001","opType":"POS_SALE","clientCreatedAt":"<ISO8601>","payload":{"tillSessionId":<id>,"clientOpId":"sync-test-001","lines":[{"itemId":<coke_id>,"qty":1,"unitPrice":1200,"discountPct":0}],"payments":[{"method":"CASH","amount":1200}],"clientCreatedAt":"<ISO8601>"}}]}`. 3. Check `pos_sale` table for the sale. 4. Check `stock_move` for debit. |
| **Expected Result** | HTTP 200; `data.results[0].verdict = "ACCEPTED"`, `data.results[0].clientOpId = "sync-test-001"`; `accepted = 1`, `rejected = 0`; `pos_sale` row exists with `client_op_id = "sync-test-001"`, status=CLOSED. |
| **Automatable?** | yes — integration test (`SyncServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-POS-SYNC-002 — Replay same op returns DUPLICATE, no double-post

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-002 |
| **Title** | Submitting the same clientOpId twice returns DUPLICATE on second call; sale not duplicated |
| **Area** | pos/sync |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | TC-POS-SYNC-001 completed successfully; `clientOpId = "sync-test-001"` already ACCEPTED. |
| **Steps** | 1. `POST /api/v1/sync/push` with identical op (same `clientOpId = "sync-test-001"`). 2. Query `pos_sale` for rows with `client_op_id = "sync-test-001"`. |
| **Expected Result** | HTTP 200; `data.results[0].verdict = "DUPLICATE"`; exactly ONE `pos_sale` row for that clientOpId (no duplication); stock not decremented again. |
| **Automatable?** | yes — integration test. Idempotency enforced by `uk_pos_sale_client_op` constraint (V78) + pre-check in `SyncServiceImpl.applyPosSale`. |
| **Result/Status** | |
| **Notes/IssueRef** | Critical: double-post = money and stock corruption |

---

### TC-POS-SYNC-003 — Push batch of 5 ops in FIFO order; all accepted

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-003 |
| **Title** | Push 5 queued offline ops in order; all accepted; results returned in order |
| **Area** | pos/sync |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | Business day OPEN. Till session OPEN. Stock sufficient for 5 sales of 1 unit. |
| **Steps** | 1. Build ops list with clientOpIds `sync-batch-001` through `sync-batch-005`, each a 1-unit COKE500 sale for 1200 TZS. 2. `POST /api/v1/sync/push` with all 5 ops. 3. Check `pos_sale` count. 4. Check `item_branch_balance.qty_on_hand` decrease. |
| **Expected Result** | HTTP 200; `data.accepted = 5`, `data.rejected = 0`; `data.results` array length=5, all verdicts ACCEPTED; 5 distinct `pos_sale` rows in DB; stock decreased by 5. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-POS-SYNC-004 — Push batch exceeds server maximum rejected

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-004 |
| **Title** | Batch with ops.size() > pushBatchMax (500) is rejected 400 |
| **Area** | pos/sync |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | `orbix.sync.push-batch-max = 500` (default). |
| **Steps** | 1. Construct a push body with 501 ops (can be stubs with opType="POS_SALE"). 2. `POST /api/v1/sync/push`. |
| **Expected Result** | HTTP 400; error message references batch size and server maximum. |
| **Automatable?** | yes — unit test (no real ops needed, just size check) |
| **Result/Status** | |
| **Notes/IssueRef** | `SyncServiceImpl.pushBatchMax` |

---

### TC-POS-SYNC-005 — Op with dependsOn unsettled returns DEFERRED

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-005 |
| **Title** | Op with dependsOn pointing to an unsettled clientOpId returns DEFERRED |
| **Area** | pos/sync |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | Business day OPEN. |
| **Steps** | 1. Push batch with op B that has `dependsOn: "op-A-not-in-batch"` and no op A in the same batch. |
| **Expected Result** | Op B verdict = "DEFERRED"; client is expected to re-push with op A present first. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | `SyncServiceImpl.pushBatch` DEFERRED logic |

---

### TC-POS-SYNC-006 — TILL_SESSION_OPEN op is idempotent

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-006 |
| **Title** | Pushing TILL_SESSION_OPEN op twice with same clientOpId returns DUPLICATE, no second session |
| **Area** | pos/sync |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | Business day OPEN. No existing OPEN session on the till. |
| **Steps** | 1. Push TILL_SESSION_OPEN op with `clientOpId = "sess-open-001"`. 2. Push same op again. 3. Query `till_session` count for this till. |
| **Expected Result** | First push: ACCEPTED, session OPEN. Second push: DUPLICATE, only one session row; `uk_*_client_op` constraint (V78) enforces this. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | V78 adds uk_till_session_client_op constraint |

---

### TC-POS-SYNC-007 — CASH_PICKUP op is idempotent

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-007 |
| **Title** | Duplicate CASH_PICKUP op returns DUPLICATE; pickup amount not applied twice |
| **Area** | pos/sync |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | Open session. |
| **Steps** | 1. Push CASH_PICKUP op `clientOpId = "pickup-001"`, amount=5000. 2. Push same op again. 3. Check cash_entry count and sum for this session. |
| **Expected Result** | One `cash_pickup` row; cash_entry shows single 5000 OUT entry, not 10000. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | V78 uk_cash_pickup_client_op constraint |

---

### TC-POS-SYNC-008 — Op for unknown opType returns REJECTED

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-008 |
| **Title** | Unknown opType in push batch returns REJECTED with UNKNOWN_OP_TYPE code |
| **Area** | pos/sync |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | Cashier authenticated. |
| **Steps** | 1. Push op with `opType: "INVALID_TYPE"`. |
| **Expected Result** | HTTP 200 (batch returns 200); result verdict = "REJECTED"; errorCode = "UNKNOWN_OP_TYPE"; no DB changes. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-POS-SYNC-009 — FIELD_SALE op returns REJECTED (WMS stub)

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-009 |
| **Title** | FIELD_SALE opType is rejected with FIELD_SALE_NOT_SUPPORTED code |
| **Area** | pos/sync |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-WMS-005 |
| **Preconditions** | Cashier authenticated. |
| **Steps** | 1. Push op with `opType: "FIELD_SALE"`. |
| **Expected Result** | verdict = "REJECTED"; errorCode = "FIELD_SALE_NOT_SUPPORTED"; message references WMS pilot scope. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Intentional stub per SyncServiceImpl comment "WMS van-stock FIELD_SALE: out of scope for POS pilot" |

---

### TC-POS-SYNC-010 — One bad op in batch does not roll back other ops

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-010 |
| **Title** | Batch with one valid + one invalid op: valid is ACCEPTED, invalid REJECTED; no rollback |
| **Area** | pos/sync |
| **Dimension** | RELI |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | Business day OPEN. Item COKE500 in stock. |
| **Steps** | 1. Push batch: op1 = valid POS_SALE (COKE500 1200 TZS), op2 = POS_SALE with invalid itemId=999999. |
| **Expected Result** | HTTP 200; op1.verdict = ACCEPTED; op2.verdict = REJECTED; pos_sale for op1 exists in DB; no pos_sale for op2. Each op runs in its own transaction per SyncServiceImpl design. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | "Push is intentionally NOT @Transactional at the batch level" — SyncServiceImpl comment |

---

## Pull — Catalog/Price/Balance/Customer

### TC-POS-SYNC-011 — Pull catalog dataset returns items with change_seq cursor

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-011 |
| **Title** | GET /sync/pull?datasets=catalog returns items, barcodes, vat_groups with nextCursor |
| **Area** | pos/sync |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-CAT-013 |
| **Preconditions** | Cashier authenticated. Seeded items exist. |
| **Steps** | 1. `GET /api/v1/sync/pull?datasets=catalog&cursor=` (first pull, empty cursor) header `X-Branch-Id: 1`, `X-Orbix-Contract-Version: 1`. 2. Note `data.nextCursor`. 3. Pull again with `cursor = <nextCursor>` without any catalog changes. |
| **Expected Result** | Step 1: HTTP 200; `data.catalog` contains all seeded items (COKE500 etc.); `data.nextCursor` is non-null; items have `code`, `name`, `vatRate`, `prices` per price list. Step 3: `data.catalog` is empty (no delta); cursor advanced. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `SyncServiceImpl.DS_CATALOG = "catalog"` |

---

### TC-POS-SYNC-012 — Pull price dataset returns price list items

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-012 |
| **Title** | GET /sync/pull?datasets=price returns price_list_items for branch's price lists |
| **Area** | pos/sync |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-CAT-013 |
| **Preconditions** | RETAIL price list seeded with COKE500 @ 1200 TZS. |
| **Steps** | 1. `GET /api/v1/sync/pull?datasets=price&cursor=` with cashier auth, X-Branch-Id:1. |
| **Expected Result** | HTTP 200; `data.price` contains COKE500 entry with `price=1200`, `priceListCode=RETAIL`, `taxInclusive=true`, `vatRate=0.18`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-POS-SYNC-013 — Pull balance dataset returns stock on hand per item

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-013 |
| **Title** | GET /sync/pull?datasets=balance returns item_branch_balance entries for branch |
| **Area** | pos/sync |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-017 |
| **Preconditions** | Stock balances exist for seeded items at branch 1. |
| **Steps** | 1. `GET /api/v1/sync/pull?datasets=balance&cursor=` with cashier auth. |
| **Expected Result** | HTTP 200; `data.balance` list contains entries for seeded items with `qtyOnHand` values; branch-scoped to branch 1 only. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Multi-tenant: must only return branch 1 balances |

---

### TC-POS-SYNC-014 — Pull customer dataset returns customer list

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-014 |
| **Title** | GET /sync/pull?datasets=customer returns customers for the company |
| **Area** | pos/sync |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-017 |
| **Preconditions** | At least one customer exists (seeded). |
| **Steps** | 1. `GET /api/v1/sync/pull?datasets=customer&cursor=` with cashier auth. |
| **Expected Result** | HTTP 200; `data.customer` list with customer uid, name, phone (where available); scoped to company, not cross-company. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `SyncServiceImpl.DS_CUSTOMER = "customer"` |

---

### TC-POS-SYNC-015 — Incremental pull after catalog update returns only delta

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-015 |
| **Title** | After updating one item's name, incremental pull returns only that item |
| **Area** | pos/sync |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-CAT-013 |
| **Preconditions** | First pull completed; `nextCursor` stored. |
| **Steps** | 1. Admin: `PUT /api/v1/items/uid/<coke_uid>` — change name to "Coca-Cola 500ml Revised". 2. Pull with the stored `cursor`. |
| **Expected Result** | Delta catalog contains only COKE500 (or the changed item); other items absent; `nextCursor` advanced. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | change_seq monotonicity required; update must stamp item's change_seq |

---

### TC-POS-SYNC-016 — change_seq is monotonically increasing

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-016 |
| **Title** | change_seq values across pull pages are strictly monotonically increasing |
| **Area** | pos/sync |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | Multiple items/sessions/sales exist with change_seq values. |
| **Steps** | 1. Pull catalog with page_size=3 (if configurable) or inspect raw change_seq values from multiple pulls. 2. Compare nextCursor values across pages. |
| **Expected Result** | Each successive pull returns items with change_seq > previous cursor; no item returned twice across pages; no item missed between pages. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Core invariant for correct offline sync |

---

### TC-POS-SYNC-017 — Multi-tenant isolation in sync pull

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-017 |
| **Title** | Sync pull with X-Branch-Id:1 does not return data belonging to branch 2 |
| **Area** | pos/sync |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-009 |
| **Preconditions** | Two branches exist (1 and 2). Item X exists only in branch 2's price list. |
| **Steps** | 1. Pull with `X-Branch-Id: 1`. 2. Inspect returned items for presence of branch-2-only item. |
| **Expected Result** | Branch-2-only item is absent from pull results. `balance` entries are branch-1 only. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Critical security case — multi-tenant isolation |

---

## Performance

### TC-POS-SYNC-018 — Sync pull for 100 items completes within 500ms

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-018 |
| **Title** | Catalog pull with 100 items responds within 500ms p95 |
| **Area** | pos/sync |
| **Dimension** | PERF |
| **Priority** | P1 |
| **Linked US-*** | US-POS-017 |
| **Preconditions** | 100 items seeded in catalog with prices. QA container running on local hardware. |
| **Steps** | 1. Time 10 consecutive calls to `GET /api/v1/sync/pull?datasets=catalog,price&cursor=`. 2. Record p50 and p95 response times using `curl -w "%{time_total}\n"`. |
| **Expected Result** | p95 response time < 500ms. p50 < 200ms. No errors. |
| **Automatable?** | partial — curl timing script; full load test requires k6 or JMeter |
| **Result/Status** | |
| **Notes/IssueRef** | Target derived from US-POS-004 "local index returns under 80 ms" (server-side pulls have wider budget) |

---

### TC-POS-SYNC-019 — Push batch of 50 ops completes within 5 seconds

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-019 |
| **Title** | Push batch of 50 sales ops completes within 5 seconds total |
| **Area** | pos/sync |
| **Dimension** | PERF |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 50 items in stock. Open session. |
| **Steps** | 1. Build 50 valid POS_SALE ops. 2. `POST /api/v1/sync/push` with all 50. 3. Time the total request duration. |
| **Expected Result** | Total push duration < 5 seconds; all 50 accepted; stock decremented correctly. |
| **Automatable?** | partial — integration test with timing assertion |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## Reliability

### TC-POS-SYNC-020 — Sync push requires POS.SYNC permission

| Field | Value |
|-------|-------|
| **ID** | TC-POS-SYNC-020 |
| **Title** | User without POS.SYNC permission cannot POST /sync/push |
| **Area** | pos/sync |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-009 |
| **Preconditions** | User `noperm` has no POS.SYNC permission (permission id 44 per seed script). |
| **Steps** | 1. Login as `noperm`. 2. `POST /api/v1/sync/push` with minimal valid body. |
| **Expected Result** | HTTP 403; no ops processed. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | POS.SYNC permission id=44 per seed-dev-data.local.sh |
