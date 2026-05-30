# TC-STOCK-TRANSFER — Inter-Branch Stock Transfers

**Module:** stock  
**Stories:** US-STOCK-007, US-STOCK-008  
**Permission gate:** `STOCK.TRANSFER` (class-level `@PreAuthorize`)  
**API base:** `http://localhost:8081/api/v1`  
**Lifecycle:** DRAFT → ISSUED → RECEIVED → CLOSED

> Interference discipline: every case that creates a transfer uses a unique number
> prefixed `XFER-QA-<entropy>` (e.g. `XFER-QA-A1B2`). Do NOT close the shared
> business day or archive the shared branch. Cases that require a second branch
> must create one via `POST /api/v1/branches` with a unique code and clean it up
> (or accept its persistence). `concurrencyRisk=possible` where noted.

---

### TC-STOCK-TRANSFER-001 — Full happy-path lifecycle: create → issue → receive → close

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-001 |
| **Title** | Complete DRAFT→ISSUED→RECEIVED→CLOSED lifecycle succeeds and stock moves are balanced |
| **Area** | stock-transfer |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-007, US-STOCK-008 |
| **Preconditions** | 1. Admin token with `STOCK.TRANSFER` in scope. 2. Two distinct branches exist: branch A (fromBranchId) and branch B (toBranchId) — use branch HQ id=1 as source; create a second branch if none exists. 3. Item COKE500 has qty_on_hand ≥ 5 at branch A (verify via `GET /api/v1/stock?itemId=<id>&branchId=1`). |
| **Steps** | 1. Resolve COKE500 itemId: `GET /api/v1/items?q=COKE500` → note `data[0].id` as `ITEM_ID`. 2. Note branch A balance before: `GET /api/v1/stock?itemId=ITEM_ID&branchId=1` → `balanceA_before`. 3. Create transfer: `POST /api/v1/stock-transfers` body `{"number":"XFER-QA-001A","fromBranchId":1,"toBranchId":2,"lines":[{"itemId":ITEM_ID,"issuedQty":3}]}` → note `data.uid` as `TUID`, `data.lines[0].id` as `LINE_ID`, assert `data.status=="DRAFT"`. 4. Issue: `POST /api/v1/stock-transfers/uid/TUID/issue` → assert `data.status=="ISSUED"`, `data.issuedAt` is non-null, `data.lines[0].costAmount` is non-null. 5. Check source balance: `GET /api/v1/stock?itemId=ITEM_ID&branchId=1` → `balanceA_after_issue`. 6. Receive (exact quantity): `PUT /api/v1/stock-transfers/uid/TUID/receive` body `{"lines":[{"lineId":LINE_ID,"receivedQty":3}]}` → assert `data.status=="RECEIVED"`, `data.receivedAt` is non-null. 7. Check dest balance: `GET /api/v1/stock?itemId=ITEM_ID&branchId=2` → `balanceB_after_receive`. 8. Close: `POST /api/v1/stock-transfers/uid/TUID/close` → assert `data.status=="CLOSED"`. |
| **Expected Result** | Step 3: HTTP 201; `status=DRAFT`. Step 4: HTTP 200; `status=ISSUED`; `lines[0].costAmount > 0`. Step 5: `qty_on_hand = balanceA_before - 3` (TRANSFER_OUT posted). Step 6: HTTP 200; `status=RECEIVED`. Step 7: `qty_on_hand = balanceB_before + 3` (TRANSFER_IN posted; same cost used for both moves). Step 8: HTTP 200; `status=CLOSED`. Net: source decremented by 3, destination incremented by 3 — system is balanced. |
| **Automatable?** | yes — integration test (Testcontainers) |
| **Result/Status** | |
| **Notes/IssueRef** | concurrencyRisk=possible (shared branch 1 stock balance). costAmount frozen at issue time from ItemBranchBalance.avgCost. |

---

### TC-STOCK-TRANSFER-002 — TRANSFER_OUT decrements source, TRANSFER_IN increments destination with matching cost

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-002 |
| **Title** | stock_move ledger shows paired TRANSFER_OUT (negative) and TRANSFER_IN (positive) with identical cost per unit |
| **Area** | stock-transfer |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-007, US-STOCK-008 |
| **Preconditions** | TC-STOCK-TRANSFER-001 completed (or a fresh transfer issued and received in this session). Transfer UID and LINE_ID known. |
| **Steps** | 1. After receive step in TC-STOCK-TRANSFER-001 (or equivalent fresh transfer), call `GET /api/v1/stock/moves?itemId=ITEM_ID&branchId=1` to retrieve source moves. 2. Filter for `moveType=TRANSFER_OUT` and the relevant `stockTransferId`. 3. Call `GET /api/v1/stock/moves?itemId=ITEM_ID&branchId=2` for destination moves. 4. Filter for `moveType=TRANSFER_IN` and the same `stockTransferId`. 5. Compare `qty` magnitudes and `costAmount` per unit on both records. |
| **Expected Result** | Source: exactly one TRANSFER_OUT move with `qty = -3`, `costAmount = issuedQty * avgCostAtIssue`. Destination: exactly one TRANSFER_IN move with `qty = +3`, `costAmount` equal to that frozen at issue (not re-evaluated at receive time). Sum: `TRANSFER_OUT.qty + TRANSFER_IN.qty = 0` — no stock created or destroyed. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Key invariant: cost is frozen at issueTransfer; receiveTransfer reuses line.costAmount. Any re-evaluation at receive would be a data-integrity bug. |

---

### TC-STOCK-TRANSFER-003 — Receive with variance (short-receive)

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-003 |
| **Title** | Receiving fewer units than issued posts TRANSFER_IN for received qty only; variance is visible on line |
| **Area** | stock-transfer |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-008 |
| **Preconditions** | A transfer exists in ISSUED status with a line of issuedQty=5. `LINE_ID` and `TUID` known. Item has balances at both branches. |
| **Steps** | 1. Create and issue a transfer with `issuedQty=5` for COKE500 using unique number `XFER-QA-003A`. 2. Note source balance after issue. 3. Receive with short quantity: `PUT /api/v1/stock-transfers/uid/TUID/receive` body `{"lines":[{"lineId":LINE_ID,"receivedQty":3}]}`. 4. Read the transfer back: `GET /api/v1/stock-transfers/uid/TUID`. 5. Check destination balance: `GET /api/v1/stock?itemId=ITEM_ID&branchId=<toBranchId>`. |
| **Expected Result** | HTTP 200; `status=RECEIVED`; `lines[0].issuedQty=5`; `lines[0].receivedQty=3`. Destination balance increased by 3, not 5 (variance of 2 is NOT automatically credited). Exactly one TRANSFER_IN move with qty=3 exists. Source balance remains decremented by the full issued qty of 5 (TRANSFER_OUT was posted at issue). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-STOCK-008 AC: "Variance (received ≠ issued) requires a reason and is reported." The current implementation records receivedQty but does not enforce a reason field or a separate variance document — track as known gap if AC not met. |

---

### TC-STOCK-TRANSFER-004 — Receive with zero quantity on a line posts no stock move

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-004 |
| **Title** | Line received with receivedQty=0 is recorded but no TRANSFER_IN move is posted |
| **Area** | stock-transfer |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-008 |
| **Preconditions** | A transfer in ISSUED status with one line (issuedQty=2). TUID and LINE_ID known. |
| **Steps** | 1. Create and issue transfer `XFER-QA-004A` with issuedQty=2. 2. Receive with zero: `PUT /api/v1/stock-transfers/uid/TUID/receive` body `{"lines":[{"lineId":LINE_ID,"receivedQty":0}]}`. 3. Read transfer: `GET /api/v1/stock-transfers/uid/TUID`. 4. Read destination stock moves for this item. |
| **Expected Result** | HTTP 200; `status=RECEIVED`; `lines[0].receivedQty=0`. No TRANSFER_IN stock_move exists for this transfer at the destination branch. Destination qty_on_hand unchanged. Source still decremented by 2 from the TRANSFER_OUT at issue. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `@PositiveOrZero` on ReceiveTransferRequestDto.ReceiveLine.receivedQty allows zero; service guards with `if (entry.receivedQty().signum() > 0)` before posting the move. |

---

### TC-STOCK-TRANSFER-005 — Cannot receive a DRAFT transfer (not yet issued)

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-005 |
| **Title** | PUT /receive on a DRAFT transfer is rejected with 4xx and no stock move posted |
| **Area** | stock-transfer |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-008 |
| **Preconditions** | A transfer exists in DRAFT status. TUID and LINE_ID known. |
| **Steps** | 1. Create transfer `XFER-QA-005A` (do NOT call /issue). 2. `PUT /api/v1/stock-transfers/uid/TUID/receive` body `{"lines":[{"lineId":LINE_ID,"receivedQty":2}]}`. |
| **Expected Result** | HTTP 4xx (400 or 422); response body contains `"ISSUED"` in the error message or `responseCode` indicating wrong state. No TRANSFER_IN move posted. Transfer status remains DRAFT. |
| **Automatable?** | yes — unit test (StockTransferServiceImplTest) + integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Service check: `if (transfer.getStatus() != StockTransferStatus.ISSUED)` throws IllegalArgumentException. Covered by existing unit test `receiveTransfer_rejectsTransferNotIssued`. |

---

### TC-STOCK-TRANSFER-006 — Cannot receive a RECEIVED transfer a second time (double-receive)

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-006 |
| **Title** | PUT /receive on an already RECEIVED transfer is rejected; no duplicate TRANSFER_IN move is posted |
| **Area** | stock-transfer |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-008 |
| **Preconditions** | A transfer has been issued and received (status=RECEIVED). TUID and LINE_ID known. |
| **Steps** | 1. Complete issue + receive cycle for transfer `XFER-QA-006A` with issuedQty=4. 2. Attempt a second receive: `PUT /api/v1/stock-transfers/uid/TUID/receive` body `{"lines":[{"lineId":LINE_ID,"receivedQty":4}]}`. 3. Read destination balance. |
| **Expected Result** | HTTP 4xx; error message contains state context (`RECEIVED`, expected `ISSUED`). No second TRANSFER_IN move posted. Destination balance is not double-incremented. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `StockTransfer.receive()` calls `requireStatus(ISSUED)` which throws IllegalStateException when status=RECEIVED. |

---

### TC-STOCK-TRANSFER-007 — Cannot issue an already ISSUED transfer

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-007 |
| **Title** | POST /issue on a non-DRAFT transfer is rejected; no duplicate TRANSFER_OUT move |
| **Area** | stock-transfer |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-007 |
| **Preconditions** | Transfer `XFER-QA-007A` has been issued (status=ISSUED). TUID known. |
| **Steps** | 1. Create and issue transfer `XFER-QA-007A`. Confirm status=ISSUED. 2. `POST /api/v1/stock-transfers/uid/TUID/issue` again. 3. Read source balance. |
| **Expected Result** | HTTP 4xx; error message contains `DRAFT` (expected state). No second TRANSFER_OUT move posted. Source balance decremented only once (by the first issue). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `StockTransfer.issue()` calls `requireStatus(DRAFT)`. |

---

### TC-STOCK-TRANSFER-008 — Cannot create a transfer with source equal to destination

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-008 |
| **Title** | POST with fromBranchId == toBranchId is rejected at creation |
| **Area** | stock-transfer |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-007 |
| **Preconditions** | Admin token with STOCK.TRANSFER. Any valid branchId (e.g. 1). |
| **Steps** | 1. `POST /api/v1/stock-transfers` body `{"number":"XFER-QA-008A","fromBranchId":1,"toBranchId":1,"lines":[{"itemId":ITEM_ID,"issuedQty":1}]}`. |
| **Expected Result** | HTTP 4xx; response message contains `"must differ"` or equivalent. No transfer record persisted. |
| **Automatable?** | yes — unit test (existing: `createTransfer_rejectsSameSourceAndDestination`) |
| **Result/Status** | |
| **Notes/IssueRef** | Service throws IllegalArgumentException "Transfer source and destination must differ". |

---

### TC-STOCK-TRANSFER-009 — Duplicate transfer number within the same company is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-009 |
| **Title** | POST with a number that already exists for the same company returns 4xx |
| **Area** | stock-transfer |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-007 |
| **Preconditions** | Transfer number `XFER-QA-009A` already exists (create it first). |
| **Steps** | 1. Create transfer `XFER-QA-009A` successfully. 2. `POST /api/v1/stock-transfers` with the same number `XFER-QA-009A`, different itemId. |
| **Expected Result** | HTTP 4xx; message contains `"already exists"`. No second transfer record persisted. |
| **Automatable?** | yes — unit test (existing: `createTransfer_rejectsDuplicateNumber`) + integration test |
| **Result/Status** | |
| **Notes/IssueRef** | DB constraint `uk_stock_transfer_company_number` would catch any bypass of the service-layer check. |

---

### TC-STOCK-TRANSFER-010 — Unauthenticated request is rejected (no token)

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-010 |
| **Title** | All stock-transfer endpoints return 401 without a valid Bearer token |
| **Area** | stock-transfer |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-007, US-STOCK-008 |
| **Preconditions** | No auth header. |
| **Steps** | 1. `GET /api/v1/stock-transfers` — no Authorization header. 2. `POST /api/v1/stock-transfers` — no Authorization header. 3. `POST /api/v1/stock-transfers/uid/01JFAKE00000000000000000A/issue` — no Authorization header. |
| **Expected Result** | All three return HTTP 401. No data disclosed. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-STOCK-TRANSFER-011 — Permission gate: user without STOCK.TRANSFER is denied

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-011 |
| **Title** | A user with a valid token but lacking STOCK.TRANSFER permission is refused on all endpoints |
| **Area** | stock-transfer |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-007, US-STOCK-008 |
| **Preconditions** | Create a test user (`XFER-SEC-USR`) with a role that has no `STOCK.TRANSFER` permission. Obtain their JWT via `POST /api/v1/auth/login`. |
| **Steps** | 1. `GET /api/v1/stock-transfers` with restricted-user token. 2. `POST /api/v1/stock-transfers` with restricted-user token. |
| **Expected Result** | HTTP 403 on both. No transfer data returned. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Class-level `@PreAuthorize("hasAuthority('STOCK.TRANSFER')")` on StockTransferController. |

---

### TC-STOCK-TRANSFER-012 — Tenant isolation: transfer from another company is not visible

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-012 |
| **Title** | A transfer created under company A cannot be read or actioned by a user of company B |
| **Area** | stock-transfer |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-007 |
| **Preconditions** | Two companies exist in the QA container (or use a second tenant if seeded). User-A (company A) and User-B (company B) both have STOCK.TRANSFER. A transfer TUID_A belongs to company A. |
| **Steps** | 1. User-A creates and issues transfer `XFER-QA-012A`. Note `TUID_A`. 2. User-B calls `GET /api/v1/stock-transfers/uid/TUID_A`. 3. User-B calls `POST /api/v1/stock-transfers/uid/TUID_A/issue`. |
| **Expected Result** | Steps 2 and 3: HTTP 404 (the service masks cross-tenant UIDs as "not found" per `requireTransferByUid` company check). Transfer is not accessible or actionable by company B. |
| **Automatable?** | yes — integration test |
| **Result/Status** | BLOCKED(needs-exclusive-state) if only one company exists in the QA container |
| **Notes/IssueRef** | `requireTransferByUid` throws NoSuchElementException when `transfer.getCompanyId() != context.companyId()`. 404 is the expected response (not 403) — deliberate information hiding. |

---

### TC-STOCK-TRANSFER-013 — Branch-scoped user can only access transfers touching their branch

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-013 |
| **Title** | A branch-scoped user sees only transfers where fromBranchId or toBranchId matches their branch |
| **Area** | stock-transfer |
| **Dimension** | SEC |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-007, US-STOCK-008 |
| **Preconditions** | User X has a role grant scoped to branch 1 only and has STOCK.TRANSFER. A transfer exists between branch 2 and branch 3 (neither is branch 1). A transfer exists between branch 1 and branch 2. |
| **Steps** | 1. Create transfer `XFER-QA-013A` from branch 2 to branch 3 (as admin). Note `TUID_ALIEN`. 2. Create transfer `XFER-QA-013B` from branch 1 to branch 2. Note `TUID_VISIBLE`. 3. Authenticate as User X (branch 1 scoped). 4. `GET /api/v1/stock-transfers` as User X. 5. `GET /api/v1/stock-transfers/uid/TUID_ALIEN` as User X. |
| **Expected Result** | Step 4: list does NOT include `TUID_ALIEN`; DOES include `TUID_VISIBLE`. Step 5: HTTP 403 or 404 for `TUID_ALIEN`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `listTransfers` uses `findInvolvingBranch` for non-company-wide callers. `requireTransferByUid` calls `requireAccessToEither` which throws 403 if caller has no grant on either side. |

---

### TC-STOCK-TRANSFER-014 — Create request validation: missing required fields returns 400

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-014 |
| **Title** | POST /stock-transfers with missing or invalid fields returns HTTP 400 |
| **Area** | stock-transfer |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-007 |
| **Preconditions** | Admin token with STOCK.TRANSFER. |
| **Steps** | 1. `POST /api/v1/stock-transfers` body `{}` (empty). 2. `POST /api/v1/stock-transfers` body `{"number":"XFER-QA-014A","fromBranchId":1,"toBranchId":2,"lines":[]}` (empty lines). 3. `POST /api/v1/stock-transfers` body `{"number":"XFER-QA-014B","fromBranchId":1,"toBranchId":2,"lines":[{"itemId":null,"issuedQty":5}]}` (null itemId). 4. `POST /api/v1/stock-transfers` body `{"number":"XFER-QA-014C","fromBranchId":1,"toBranchId":2,"lines":[{"itemId":99,"issuedQty":-1}]}` (negative qty). |
| **Expected Result** | All four: HTTP 400. `errors[]` in `ApiResponse` identifies each constraint: empty body → missing fields; empty lines → `@NotEmpty`; null itemId → `@NotNull`; negative qty → `@Positive`. No transfer persisted for any case. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Bean validation via `@Valid` on CreateStockTransferRequestDto. |

---

### TC-STOCK-TRANSFER-015 — Receive request with line ID not belonging to the transfer is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-015 |
| **Title** | PUT /receive with a lineId from a different transfer returns 4xx; no stock move posted |
| **Area** | stock-transfer |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-008 |
| **Preconditions** | Transfer A (TUID_A) in ISSUED status, LINE_ID_A belongs to it. Transfer B (TUID_B) in ISSUED status, LINE_ID_B belongs to it. |
| **Steps** | 1. Create and issue transfers `XFER-QA-015A` and `XFER-QA-015B`. 2. `PUT /api/v1/stock-transfers/uid/TUID_A/receive` body `{"lines":[{"lineId":LINE_ID_B,"receivedQty":1}]}` (line from B sent to receive of A). |
| **Expected Result** | HTTP 4xx; error message contains `"Line not on this transfer"` or equivalent. Transfer A status remains ISSUED. No TRANSFER_IN posted. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Service throws NoSuchElementException "Line not on this transfer: {lineId}". |

---

### TC-STOCK-TRANSFER-016 — GET /stock-transfers/uid/:uid with non-existent or malformed UID

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-016 |
| **Title** | GET with an unknown UID returns 404; GET with a non-ULID path segment returns 400 |
| **Area** | stock-transfer |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-STOCK-007 |
| **Preconditions** | Admin token with STOCK.TRANSFER. |
| **Steps** | 1. `GET /api/v1/stock-transfers/uid/01JFAKEUID000000000000000` (valid ULID format but no matching record). 2. `GET /api/v1/stock-transfers/uid/not-a-ulid`. |
| **Expected Result** | Step 1: HTTP 404. Step 2: HTTP 400 (constraint violation from `@ValidUlid`). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `@ValidUlid` on path var rejects malformed ULIDs at the controller layer. |

---

### TC-STOCK-TRANSFER-017 — Outbox event published on issue and receive

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-017 |
| **Title** | issueTransfer publishes StockTransferIssued.v1 event; receiveTransfer publishes StockTransferReceived.v1 event to the outbox |
| **Area** | stock-transfer |
| **Dimension** | INT |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-007, US-STOCK-008 |
| **Preconditions** | Admin access to `domain_event` table (via phpMyAdmin :8090 or MariaDB CLI inside the container). Transfer TUID known. |
| **Steps** | 1. Create and issue transfer `XFER-QA-017A`. Note the transfer's numeric id. 2. Query: `SELECT event_type, aggregate_id, dispatched_at FROM domain_event WHERE aggregate_id = '<transfer_id>' ORDER BY id;`. 3. Receive the transfer. 4. Query the same table again. |
| **Expected Result** | After issue: one row with `event_type='StockTransferIssued.v1'`, `aggregate_id=<transfer_id>`. After receive: a second row with `event_type='StockTransferReceived.v1'`. Both rows have `dispatched_at` set (or null pending dispatch — the outbox poller runs async; allow up to 30 s). Payload JSON contains `uid`, `fromBranchId`, `toBranchId`, `lines`. |
| **Automatable?** | partial — outbox write is integration-testable; dispatch timing requires a wait or spy |
| **Result/Status** | |
| **Notes/IssueRef** | EventPublisher writes to `domain_event` in the same transaction. Dispatch is async (scheduled poller). Test the write synchronously; do not gate on dispatch timing. |

---

### TC-STOCK-TRANSFER-018 — List endpoint scoping for company-wide vs. branch-scoped caller

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-TRANSFER-018 |
| **Title** | GET /stock-transfers returns all company transfers for company-wide caller and only branch-relevant transfers for branch-scoped caller |
| **Area** | stock-transfer |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-007 |
| **Preconditions** | At least two transfers exist: one touching branch 1 (TUID_1) and one between branches 2 and 3 (TUID_23). Admin (company-wide) token and branch-1-scoped token both with STOCK.TRANSFER. |
| **Steps** | 1. `GET /api/v1/stock-transfers` as admin (company-wide). Note count and presence of TUID_23. 2. `GET /api/v1/stock-transfers` as branch-1-scoped user. Note count. |
| **Expected Result** | Admin response: includes TUID_23 and TUID_1. Branch-scoped response: includes TUID_1, does NOT include TUID_23. HTTP 200 for both. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Service uses `branchScope.isCompanyWide()` to choose between `findByCompanyIdOrderByIdDesc` and `findInvolvingBranch`. |
