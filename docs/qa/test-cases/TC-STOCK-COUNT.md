# TC-STOCK-COUNT — Physical Stock Count Lifecycle

**Module:** stock  
**Stories:** US-STOCK-004, US-STOCK-005, US-STOCK-006  
**API base:** `http://localhost:8081/api/v1`  
**Permission required:** `STOCK.COUNT` (all read + write); `STOCK.COUNT_APPROVE` (above-threshold post authoriser)  
**Lifecycle:** DRAFT -> IN_PROGRESS -> CLOSED -> POSTED  
**Threshold default:** 50,000 TZS (`STOCK_ADJUSTMENT_THRESHOLD`, `SettingKey.STOCK_ADJUSTMENT_THRESHOLD`)

> Interference note: prefix every count `number` with `SC-<RUNNER_ID>-<ULID_SHORT>` to avoid duplicate-number conflicts across concurrent runners.

---

### TC-STOCK-COUNT-001 — Full happy path: create, start, record, close, post (zero authoriser needed)

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-001 |
| **Title** | Complete stock-count lifecycle posts correct ADJUSTMENT moves and updates on-hand |
| **Area** | stock-count |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-004, US-STOCK-005, US-STOCK-006 |
| **Preconditions** | 1. Admin authenticated. 2. Resolve COKE500 itemId: `GET /api/v1/items?q=COKE500` -> `items[0].id` (call it `ITEM_ID`). 3. Note `qty_on_hand` for that item + branchId=1: `GET /api/v1/stock?itemId=<ITEM_ID>&branchId=1` -> `qtyOnHand` (call it `SOH_BEFORE`). 4. Variance value must stay under 50,000 TZS (use small qty diff). |
| **Steps** | 1. `POST /api/v1/stock-counts` body: `{"number":"SC-QA-001-A","branchId":1,"countDate":"2026-05-30","type":"SPOT","itemIds":[<ITEM_ID>]}` -> expect 201; capture `uid` (call it `SC_UID`) and `lines[0].id` (call it `LINE_ID`); assert `lines[0].systemQty == SOH_BEFORE`. 2. `POST /api/v1/stock-counts/uid/<SC_UID>/start` -> expect 200; assert `status=="IN_PROGRESS"`. 3. `PUT /api/v1/stock-counts/uid/<SC_UID>/counts` body: `{"counts":[{"lineId":<LINE_ID>,"countedQty":<SOH_BEFORE - 2>,"note":"QA short count"}]}` -> expect 200. 4. `POST /api/v1/stock-counts/uid/<SC_UID>/close` -> expect 200; assert `status=="CLOSED"`; assert `lines[0].varianceQty == -2`. 5. `POST /api/v1/stock-counts/uid/<SC_UID>/post` body: `{}` -> expect 200; assert `status=="POSTED"` and `postedAt != null`. 6. `GET /api/v1/stock?itemId=<ITEM_ID>&branchId=1` -> assert `qtyOnHand == SOH_BEFORE - 2`. |
| **Expected Result** | Count reaches POSTED; exactly one ADJUSTMENT stock_move created for ITEM_ID with qty=-2; `item_branch_balance.qty_on_hand` decremented by 2; `postedAt` timestamp populated. |
| **Automatable?** | yes — integration test (Testcontainers) |
| **Result/Status** | |
| **Notes/IssueRef** | P0: posting loop is the primary risk — wrong variance math corrupts whole-branch on-hand |

---

### TC-STOCK-COUNT-002 — systemQty frozen at count creation, not at start

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-002 |
| **Title** | line.systemQty reflects on-hand at the moment of POST /stock-counts, ignoring moves that arrive after |
| **Area** | stock-count |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-004 |
| **Preconditions** | 1. Admin authenticated. 2. Resolve BREAD itemId (call it `ITEM_ID`). 3. Know `SOH` = current on-hand. |
| **Steps** | 1. `POST /api/v1/stock-counts` body: `{"number":"SC-QA-002-A","branchId":1,"countDate":"2026-05-30","type":"SPOT","itemIds":[<ITEM_ID>]}` -> capture `lines[0].systemQty` (call it `FROZEN_QTY`). 2. Post a manual adjustment of +5 units for the same item/branch via `POST /api/v1/adjustments`. 3. `POST /api/v1/stock-counts/uid/<SC_UID>/start` -> inspect returned `lines[0].systemQty`. |
| **Expected Result** | `lines[0].systemQty` remains `FROZEN_QTY` (the on-hand at step 1); the +5 adjustment is NOT reflected in systemQty even though on-hand has changed. variance will be computed as `countedQty - FROZEN_QTY` not vs. current live balance. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Critical for audit integrity — systemQty is a snapshot, not a live lookup |

---

### TC-STOCK-COUNT-003 — Zero-variance lines produce no ADJUSTMENT move

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-003 |
| **Title** | Items counted equal to system qty generate no ADJUSTMENT move on post |
| **Area** | stock-count |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-006 |
| **Preconditions** | Admin authenticated. Two items: COKE500 (will have variance) and BREAD (will be counted exactly). |
| **Steps** | 1. Create count with both items. 2. Start count. 3. Record: COKE500 countedQty=systemQty-1 (variance -1); BREAD countedQty=systemQty (variance 0). 4. Close count. 5. Note count of existing `stock_move` rows for BREAD via `GET /api/v1/stock-report/card?itemId=<BREAD_ID>&branchId=1`. 6. Post count. 7. Check `stock_move` count for BREAD again. |
| **Expected Result** | BREAD `stock_move` count is unchanged (no new ADJUSTMENT row). COKE500 gets one ADJUSTMENT move with qty=-1. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Guards against generating phantom adjustments on every zero-variance line |

---

### TC-STOCK-COUNT-004 — Uncounted lines default to zero variance (not null)

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-004 |
| **Title** | Line left unrecorded (null countedQty) resolves to variance=0 on close, not error |
| **Area** | stock-count |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-005 |
| **Preconditions** | Admin authenticated. One item added to count. |
| **Steps** | 1. Create count with SALT500G. 2. Start count. 3. Skip `PUT /counts` (do not record any countedQty). 4. `POST /uid/<SC_UID>/close`. 5. `POST /uid/<SC_UID>/post`. |
| **Expected Result** | Close succeeds; `lines[0].varianceQty == 0`; post succeeds with no ADJUSTMENT move created; on-hand unchanged. (`computeVariance()` treats null countedQty as equal to systemQty.) |
| **Automatable?** | yes — unit test + integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Mirrors `StockCountLine.computeVariance()`: `counted = countedQty != null ? countedQty : systemQty` |

---

### TC-STOCK-COUNT-005 — Cannot post twice (idempotency guard)

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-005 |
| **Title** | Second POST /post on an already-POSTED count returns error, no duplicate moves |
| **Area** | stock-count |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-006 |
| **Preconditions** | A stock count has been taken through to POSTED status (can reuse result of TC-STOCK-COUNT-001). Note on-hand value after first post (`SOH_AFTER`). |
| **Steps** | 1. Replay `POST /api/v1/stock-counts/uid/<SC_UID>/post` on the already-POSTED count. 2. `GET /api/v1/stock?itemId=<ITEM_ID>&branchId=1` — check on-hand. |
| **Expected Result** | Step 1: HTTP 4xx (400 or 409); response body includes message indicating count is POSTED, expected CLOSED. No new stock_move rows created. On-hand at step 2 == `SOH_AFTER` (not further adjusted). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | P0: duplicate post doubles on-hand error; `StockCount.post()` calls `requireStatus(CLOSED)` -> `IllegalStateException` |

---

### TC-STOCK-COUNT-006 — Cannot record counts after close

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-006 |
| **Title** | PUT /counts on a CLOSED count is rejected |
| **Area** | stock-count |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-005 |
| **Preconditions** | Admin authenticated. A count is in CLOSED status. |
| **Steps** | 1. Close a count (create -> start -> close, skip post). 2. `PUT /api/v1/stock-counts/uid/<SC_UID>/counts` body: `{"counts":[{"lineId":<LINE_ID>,"countedQty":99,"note":"late entry"}]}`. |
| **Expected Result** | HTTP 4xx; message indicates count is not IN_PROGRESS; the line's countedQty is unchanged. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `recordCounts()` guards: `if (count.getStatus() != StockCountStatus.IN_PROGRESS)` |

---

### TC-STOCK-COUNT-007 — Cannot close a DRAFT count (must start first)

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-007 |
| **Title** | POST /close on a DRAFT count is rejected; lifecycle step cannot be skipped |
| **Area** | stock-count |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-004 |
| **Preconditions** | Admin authenticated. A count is in DRAFT status (just created, not started). |
| **Steps** | 1. `POST /api/v1/stock-counts` to create count -> capture `SC_UID`. 2. `POST /api/v1/stock-counts/uid/<SC_UID>/close` immediately (skip start). |
| **Expected Result** | HTTP 4xx; response message indicates count is DRAFT, expected IN_PROGRESS. Status remains DRAFT. |
| **Automatable?** | yes — unit test + integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `StockCount.close()` calls `requireStatus(IN_PROGRESS)` -> `IllegalStateException` |

---

### TC-STOCK-COUNT-008 — Above-threshold post requires separate-user authoriser

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-008 |
| **Title** | POST /post with variance value > 50,000 TZS rejected when no authorisedByUserId supplied |
| **Area** | stock-count |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-006 |
| **Preconditions** | 1. Admin authenticated. 2. Item with avg_cost high enough that `variance_qty * avg_cost > 50000`. E.g. COKE500 avg_cost=600 TZS -> variance of 100 units = 60,000 TZS. 3. Create count, start, record 100-unit short, close. |
| **Steps** | 1. `POST /api/v1/stock-counts/uid/<SC_UID>/post` body: `{}` (no authoriser). |
| **Expected Result** | HTTP 422 (or 400); response message contains `STOCK.COUNT_APPROVE`; count status remains CLOSED; no stock_move rows created. |
| **Automatable?** | yes — unit test (`postCount_aboveThreshold_withoutAuthoriser_isRejected` in StockCountServiceImplTest) + integration test |
| **Result/Status** | |
| **Notes/IssueRef** | P0 dual-control gate; mirrors `STOCK.ADJUST_APPROVE` pattern |

---

### TC-STOCK-COUNT-009 — Authoriser cannot be the same user as the poster

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-009 |
| **Title** | POST /post with above-threshold variance and authorisedByUserId == actorId is rejected (self-approval) |
| **Area** | stock-count |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-006 |
| **Preconditions** | Count in CLOSED status with variance value > 50,000 TZS (same setup as TC-STOCK-COUNT-008). Admin's own userId known (retrieve from `GET /api/v1/me` or JWT decode). |
| **Steps** | 1. `POST /api/v1/stock-counts/uid/<SC_UID>/post` body: `{"authorisedByUserId":<OWN_USER_ID>}`. |
| **Expected Result** | HTTP 422 (or 400); message contains "cannot authorise your own"; count remains CLOSED; no stock_move rows. |
| **Automatable?** | yes — unit test (`postCount_aboveThreshold_withSelfAuthoriser_isRejected`) + integration test |
| **Result/Status** | |
| **Notes/IssueRef** | P0 segregation-of-duties rule |

---

### TC-STOCK-COUNT-010 — Authoriser must hold STOCK.COUNT_APPROVE

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-010 |
| **Title** | POST /post naming a user without STOCK.COUNT_APPROVE as authoriser is rejected with 403 |
| **Area** | stock-count |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-006 |
| **Preconditions** | 1. Count in CLOSED status with above-threshold variance. 2. A second user (`cashier`) who does NOT hold `STOCK.COUNT_APPROVE` — resolve cashier's userId via `GET /api/v1/users?q=cashier`. |
| **Steps** | 1. `POST /api/v1/stock-counts/uid/<SC_UID>/post` body: `{"authorisedByUserId":<CASHIER_USER_ID>}` (admin as poster). |
| **Expected Result** | HTTP 403; message references `STOCK.COUNT_APPROVE`; count remains CLOSED. |
| **Automatable?** | yes — unit test (`postCount_aboveThreshold_authoriserMissingPermission_403`) + integration test |
| **Result/Status** | |
| **Notes/IssueRef** | P0; `validateAuthoriser()` throws `AccessDeniedException` -> 403 |

---

### TC-STOCK-COUNT-011 — Above-threshold post succeeds with valid separate-user authoriser

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-011 |
| **Title** | POST /post above threshold with a valid STOCK.COUNT_APPROVE authoriser transitions to POSTED |
| **Area** | stock-count |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-006 |
| **Preconditions** | 1. Count in CLOSED status with variance value > 50,000 TZS. 2. Admin has `STOCK.COUNT_APPROVE` (rootadmin has full permissions). 3. A second admin user (`approver`) exists with `STOCK.COUNT_APPROVE` and a different userId. If none, create one via `POST /api/v1/users`. |
| **Steps** | 1. Authenticate as a non-approver user (or use rootadmin as poster against a count created by a second actor -- skip if only one user available; use unit test coverage instead). 2. `POST /api/v1/stock-counts/uid/<SC_UID>/post` body: `{"authorisedByUserId":<APPROVER_USER_ID>}`. 3. `GET /api/v1/stock-counts/uid/<SC_UID>` — check status + postedAt. 4. `GET /api/v1/stock?itemId=<ITEM_ID>&branchId=1` — verify on-hand adjusted. |
| **Expected Result** | HTTP 200; status=POSTED; postedAt != null; on-hand reflects variance; one ADJUSTMENT move per non-zero variance line. |
| **Automatable?** | yes — unit test (`postCount_aboveThreshold_withApprovedAuthoriser_succeeds`) + integration test (needs two seeded users) |
| **Result/Status** | |
| **Notes/IssueRef** | concurrencyRisk=possible (needs second user, may conflict with other runners seeding users) |

---

### TC-STOCK-COUNT-012 — Duplicate count number for same branch is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-012 |
| **Title** | POST /stock-counts with number that already exists on the branch returns validation error |
| **Area** | stock-count |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-004 |
| **Preconditions** | Admin authenticated. A count with number `SC-DUP-QA` already exists for branchId=1. |
| **Steps** | 1. `POST /api/v1/stock-counts` body: `{"number":"SC-DUP-QA","branchId":1,"countDate":"2026-05-30","type":"SPOT","itemIds":[<ANY_ITEM_ID>]}`. 2. Repeat identical POST. |
| **Expected Result** | First POST: 201. Second POST: HTTP 400 or 409; message contains "already exists". No second stock_count row created. |
| **Automatable?** | yes — unit test (`createCount_rejectsDuplicateNumber`) + integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `uk_stock_count_branch_number` unique constraint is the DB-level backstop |

---

### TC-STOCK-COUNT-013 — CREATE with empty itemIds list is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-013 |
| **Title** | POST /stock-counts with empty itemIds fails validation before persisting |
| **Area** | stock-count |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-STOCK-004 |
| **Preconditions** | Admin authenticated. |
| **Steps** | 1. `POST /api/v1/stock-counts` body: `{"number":"SC-NEG-EMPTY","branchId":1,"countDate":"2026-05-30","type":"SPOT","itemIds":[]}`. |
| **Expected Result** | HTTP 400; `errors` array contains a message about `itemIds` must not be empty (`@NotEmpty` on `CreateStockCountRequestDto.itemIds`). No row persisted. |
| **Automatable?** | yes — unit test / e2e |
| **Result/Status** | |
| **Notes/IssueRef** | Bean validation guard; `@NotEmpty List<Long> itemIds` |

---

### TC-STOCK-COUNT-014 — GET /stock-counts without STOCK.COUNT permission returns 403

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-014 |
| **Title** | All stock-count endpoints require STOCK.COUNT authority; unauthenticated / low-privilege callers are rejected |
| **Area** | stock-count |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-004 |
| **Preconditions** | POS cashier authenticated (user `cashier` / `Cashier#2026`). Cashier holds POS_CASHIER role which does NOT include `STOCK.COUNT`. |
| **Steps** | 1. Authenticate as cashier: `POST /api/v1/auth/login` body: `{"username":"cashier","password":"Cashier#2026"}` -> capture cashier token. 2. `GET /api/v1/stock-counts` with `Authorization: Bearer <cashier_token>`. 3. `POST /api/v1/stock-counts` body: any valid payload. |
| **Expected Result** | Both requests return HTTP 403. No data leaked. |
| **Automatable?** | yes — integration test (security filter) |
| **Result/Status** | |
| **Notes/IssueRef** | Controller-level `@PreAuthorize("hasAuthority('STOCK.COUNT')")` |

---

### TC-STOCK-COUNT-015 — Cross-company count UID lookup returns 404

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-015 |
| **Title** | GET /stock-counts/uid/<uid> for a count belonging to a different company returns 404, not 403 (no info leak) |
| **Area** | stock-count |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-004 |
| **Preconditions** | Requires two companies in the same container (difficult in shared QA). Mark BLOCKED(needs-exclusive-state) if a second company cannot be created without affecting other runners. Otherwise: create company B, create a count in company B, authenticate as company A admin. |
| **Steps** | 1. Obtain UID of a stock count owned by company B. 2. Authenticate as company A user (admin). 3. `GET /api/v1/stock-counts/uid/<COMPANY_B_UID>`. |
| **Expected Result** | HTTP 404; response message "Stock count not found: <uid>". The response does NOT reveal that the count exists in another tenant. |
| **Automatable?** | yes — unit test (`getCount_fromAnotherCompany_throwsNotFound`) |
| **Result/Status** | BLOCKED(needs-exclusive-state) on shared container — run as unit test |
| **Notes/IssueRef** | `requireCountByUid()` checks `count.getCompanyId() == context.companyId()` before returning |

---

### TC-STOCK-COUNT-016 — List counts filtered by branchId

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-016 |
| **Title** | GET /stock-counts?branchId=1 returns only counts for branch 1 |
| **Area** | stock-count |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | US-STOCK-004 |
| **Preconditions** | Admin authenticated. At least one count exists for branchId=1. |
| **Steps** | 1. `GET /api/v1/stock-counts?branchId=1`. 2. `GET /api/v1/stock-counts` (no filter). |
| **Expected Result** | Step 1: all returned counts have `branchId==1`. Step 2: returns counts across all accessible branches. Each DTO has `id`, `uid`, `number`, `status`, `countDate`, `type`, `lines[]`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-STOCK-COUNT-017 — Positive variance (shortage correction / overage) posts correct IN move

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-017 |
| **Title** | Counted qty > system qty generates a positive ADJUSTMENT stock_move (stock-in) |
| **Area** | stock-count |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-006 |
| **Preconditions** | Admin authenticated. Item SUGAR1KG with known on-hand `SOH`. Ensure variance value stays under 50,000 TZS threshold. |
| **Steps** | 1. Create SPOT count for SUGAR1KG. 2. Start. 3. Record `countedQty = SOH + 3` (overage). 4. Close. 5. Assert `varianceQty == +3`. 6. Post (no authoriser needed if under threshold). 7. `GET /api/v1/stock?itemId=<SUGAR_ID>&branchId=1`. |
| **Expected Result** | `qtyOnHand == SOH + 3`; ADJUSTMENT move has `qty=+3` (IN direction); `postedAt != null`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Overage path is distinct from the more common shortage; guards against sign-flip bug |

---

### TC-STOCK-COUNT-018 — Invalid UID format returns 400, not 500

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-COUNT-018 |
| **Title** | Requests with malformed UID in path are rejected at the validation layer (not propagated to DB) |
| **Area** | stock-count |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-STOCK-004 |
| **Preconditions** | Admin authenticated. |
| **Steps** | 1. `GET /api/v1/stock-counts/uid/not-a-ulid`. 2. `POST /api/v1/stock-counts/uid/not-a-ulid/start`. 3. `POST /api/v1/stock-counts/uid/not-a-ulid/close`. 4. `POST /api/v1/stock-counts/uid/not-a-ulid/post`. |
| **Expected Result** | Each request returns HTTP 400; `errors` describes `@ValidUlid` constraint violation. No stack trace or DB error message in the response body. |
| **Automatable?** | yes — e2e / integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `@ValidUlid` on `@PathVariable String uid` in `StockCountController` |
