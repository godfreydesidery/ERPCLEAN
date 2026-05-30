# TC-SUPPLIER-DEBT — Supplier AP Debt: Ageing, Dunning, Statement

**Module:** debt (AP surface — `SupplierDebtController`)
**Stories:** US-DEBT-002, US-DEBT-003, US-DEBT-006
**API base:** `http://localhost:8081/api/v1`
**Permission required:** `DEBT.READ` (id 130) — class-level gate on all three endpoints
**Interference note:** cases create suppliers and invoices with unique codes prefixed `SDBT` + entropy. Do NOT close/archive the shared business day or seeded catalog. Cases are independent and safe to run concurrently except TC-SUPPLIER-DEBT-010 (cross-tenant probe — read-only, safe).

---

## Setup helper (run once per session before any case that needs AP data)

```bash
# Authenticate
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"rootadmin","password":"SKp315goPN8Nb0yJtMCCD7cm"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"

# Create a test supplier (unique code per run — substitute $ENTROPY with a short random string)
ENTROPY=$(python3 -c "import random,string; print(''.join(random.choices(string.ascii_uppercase+string.digits,k=6)))")
SUPP_RESP=$(curl -s -X POST http://localhost:8081/api/v1/suppliers \
  -H "Content-Type: application/json" -H "$AUTH" \
  -d "{\"party\":{\"name\":\"SDBT-Supplier-$ENTROPY\",\"email\":\"sdbt$ENTROPY@test.local\"},
       \"paymentTermsDays\":30,\"creditLimitAmount\":1000000,\"defaultCurrencyCode\":\"TZS\"}")
SUPP_UID=$(echo $SUPP_RESP | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['party']['uid'])")
SUPP_ID=$(echo $SUPP_RESP  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['party']['id'])")
```

Supplier invoices require `PROCUREMENT.MANAGE_INVOICE`. `rootadmin` holds all permissions. The invoice must be POSTed then posted (`POST /uid/{uid}/post`) before it appears in `findAllOpenForAging` (POSTED or PARTIALLY_PAID status with outstanding > 0).

---

### TC-SUPPLIER-DEBT-001 — AP ageing report returns correct bucket totals

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-001 |
| **Title** | GET /debt/supplier-aging groups open AP invoices into correct 5-bucket row |
| **Area** | debt / supplier-AP |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-DEBT-002, US-DEBT-003 |
| **Preconditions** | 1. Authenticated as `rootadmin`. 2. Test supplier created (see setup helper, SUPP_UID + SUPP_ID set). 3. Five supplier invoices POSTed (status POSTED) against that supplier, each with a different due date relative to today: (a) due in +10 days = CURRENT; (b) due -15 days = D_1_30; (c) due -45 days = D_31_60; (d) due -75 days = D_61_90; (e) due -120 days = D_90_PLUS. Each invoice: branchId=1, currencyCode=TZS, subtotalAmount=100000, taxAmount=0, one allocation line (grnId may be omitted if service accepts direct invoice — use a real GRN or a DRAFT→post flow). |
| **Steps** | 1. `GET /api/v1/debt/supplier-aging?branchId=1&asOf=<today>` with `Authorization: Bearer $TOKEN`. 2. Find the row in `data.rows` where `supplierName` matches the test supplier. 3. Inspect bucket fields on that row and on `data.totals`. |
| **Expected Result** | HTTP 200; `data.rows` contains the test supplier row with: `current` includes TZS 100000 from invoice (a); `d1_30` includes TZS 100000 from (b); `d31_60` includes TZS 100000 from (c); `d61_90` includes TZS 100000 from (d); `d90_plus` includes TZS 100000 from (e); `totalOutstanding` = 500000; `oldestDaysOverdue` = 120 (invoice e). `data.totals.totalOutstanding` includes the test supplier's 500000. `data.asOf` = today's date. `data.currencyCode` = "TZS". `supplierId` serialised as JSON string (not integer). |
| **Automatable?** | yes — integration test (Testcontainers + real MariaDB) |
| **Result/Status** | |
| **Notes/IssueRef** | Bucket boundary logic: days overdue ≤ 30 → D_1_30; ≤ 60 → D_31_60; ≤ 90 → D_61_90; > 90 → D_90_PLUS. An invoice with dueDate = today is NOT overdue (current). Logic in `SupplierAgingAccumulator.add()`: `!due.isBefore(today)` → CURRENT. |

---

### TC-SUPPLIER-DEBT-002 — Boundary: invoice due exactly today lands in CURRENT

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-002 |
| **Title** | Invoice with dueDate = asOf date is classified as CURRENT, not D_1_30 |
| **Area** | debt / supplier-AP |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-DEBT-003 |
| **Preconditions** | 1. Test supplier with one POSTED invoice; dueDate = today's date; totalAmount = 50000, paidAmount = 0. |
| **Steps** | 1. `GET /api/v1/debt/supplier-aging?asOf=<today>` (no branchId filter — company-wide). 2. Locate the supplier row. |
| **Expected Result** | `data.rows[n].current` = 50000; `d1_30` = 0; `oldestDaysOverdue` is null (not overdue). `data.totals.current` includes the 50000. |
| **Automatable?** | yes — unit test (`SupplierDebtReadModelServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | Boundary condition: `isBefore(today)` is false when due == today, so the invoice is CURRENT. This is consistent with the AR side. |

---

### TC-SUPPLIER-DEBT-003 — Fully-paid invoice is excluded from ageing

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-003 |
| **Title** | Supplier invoice with paidAmount = totalAmount does not appear in AP ageing row |
| **Area** | debt / supplier-AP |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-DEBT-002 |
| **Preconditions** | 1. Test supplier A has one POSTED invoice: totalAmount=20000, paidAmount=20000 (fully paid), dueDate=-10 days. 2. Test supplier B has one POSTED invoice: totalAmount=20000, paidAmount=0, dueDate=-10 days (open). |
| **Steps** | 1. `GET /api/v1/debt/supplier-aging?asOf=<today>`. 2. Check supplier A row. 3. Check supplier B row. |
| **Expected Result** | Supplier A does NOT appear in `data.rows` (totalOutstanding = 0, supplier excluded). Supplier B appears with `d1_30` = 20000. `data.totals.supplierCount` counts only suppliers with outstanding > 0. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | `SupplierAgingAccumulator.add()`: `outstanding.signum() <= 0 → return` (skip). Outer loop: `if (supplierOutstanding.signum() <= 0) continue`. Fully-paid invoices are still loaded by `findAllOpenForAging` if status is POSTED or PARTIALLY_PAID and `totalAmount > paidAmount` — PAID status is excluded at the JPQL query level. Verify with a PAID-status invoice: it should not even be loaded. |

---

### TC-SUPPLIER-DEBT-004 — Ageing rows sorted by oldest-overdue descending

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-004 |
| **Title** | GET /debt/supplier-aging rows ordered oldest-overdue desc (worst arrears first) |
| **Area** | debt / supplier-AP |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-DEBT-003 |
| **Preconditions** | 1. Three distinct test suppliers: (X) one overdue invoice 100 days; (Y) one overdue invoice 10 days; (Z) one invoice due in future (CURRENT only). |
| **Steps** | 1. `GET /api/v1/debt/supplier-aging?asOf=<today>`. 2. Extract `data.rows` order. |
| **Expected Result** | Supplier X appears first (`oldestDaysOverdue`=100), supplier Y second (`oldestDaysOverdue`=10), supplier Z last (null `oldestDaysOverdue` — CURRENT only, sorted nulls-last). |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Sort: `Comparator.nullsLast(Comparator.reverseOrder())` on `oldestDaysOverdue`. |

---

### TC-SUPPLIER-DEBT-005 — AP dunning queue default returns all suppliers with overdue debt

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-005 |
| **Title** | GET /debt/supplier-dunning without bucket filter returns all overdue suppliers paged |
| **Area** | debt / supplier-AP |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-DEBT-002, US-DEBT-003 |
| **Preconditions** | 1. At least two test suppliers with overdue invoices in different buckets: supplier A has one invoice 100 days overdue (D_90_PLUS); supplier B has one invoice 15 days overdue (D_1_30). 2. No bucket filter applied. |
| **Steps** | 1. `GET /api/v1/debt/supplier-dunning?page=0&size=25` with admin auth. 2. Verify response shape. |
| **Expected Result** | HTTP 200; `data.content` is an array; each row has fields: `supplierId` (string), `supplierUid`, `supplierName`, `totalOutstanding`, `oldestDaysOverdue`, `oldestDueDate`, `worstBucket`, `overdueInvoiceCount`. Supplier A row: `worstBucket`="D_90_PLUS", `oldestDaysOverdue`=100, appears before supplier B. `data.totalElements` >= 2. `data.page`=0, `data.size`=25. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Response shape is `PageDto<SupplierDunningQueueRowDto>` with fields `content`, `page`, `size`, `totalElements`, `totalPages` (exact shape per `PageDto.of`). |

---

### TC-SUPPLIER-DEBT-006 — AP dunning queue filtered by bucket returns only matching suppliers

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-006 |
| **Title** | GET /debt/supplier-dunning?bucket=D_90_PLUS returns only suppliers whose worst bucket is D_90_PLUS |
| **Area** | debt / supplier-AP |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-DEBT-003 |
| **Preconditions** | 1. Supplier A: one invoice 100 days overdue (worstBucket = D_90_PLUS). 2. Supplier B: one invoice 15 days overdue (worstBucket = D_1_30). |
| **Steps** | 1. `GET /api/v1/debt/supplier-dunning?bucket=D_90_PLUS&page=0&size=25`. 2. Inspect `data.content`. |
| **Expected Result** | HTTP 200; supplier A present in `data.content` with `worstBucket`="D_90_PLUS"; supplier B absent (worstBucket does not match filter). Note: the filter is on `worstBucket` (highest-severity bucket with a balance), not on whether the supplier has any amount in D_90_PLUS — a supplier with amounts in both D_1_30 and D_90_PLUS still qualifies because worstBucket = D_90_PLUS. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Valid `bucket` values: `CURRENT`, `D_1_30`, `D_31_60`, `D_61_90`, `D_90_PLUS`. |

---

### TC-SUPPLIER-DEBT-007 — Invalid bucket enum rejected at API boundary

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-007 |
| **Title** | GET /debt/supplier-dunning?bucket=GARBAGE returns 400, not 500 |
| **Area** | debt / supplier-AP |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-DEBT-003 |
| **Preconditions** | Authenticated as rootadmin. |
| **Steps** | 1. `GET /api/v1/debt/supplier-dunning?bucket=GARBAGE` with admin auth. |
| **Expected Result** | HTTP 400; response body is a standard `ApiResponse` envelope with `status`="error" and a descriptive error message referencing the unknown enum value. No stack trace in production response. `statusCode` is not 500. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Spring MVC converts `@RequestParam AgingBucket bucket` via enum name; unknown value → `MethodArgumentTypeMismatchException` → 400. |

---

### TC-SUPPLIER-DEBT-008 — Supplier statement drill-down returns open invoices and aging row

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-008 |
| **Title** | GET /debt/supplier/uid/{uid} returns supplier statement with open invoices, aging row, and recent payments |
| **Area** | debt / supplier-AP |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-DEBT-006 |
| **Preconditions** | 1. Test supplier (SUPP_UID set). 2. Two POSTED supplier invoices for that supplier: INV-A (totalAmount=80000, paidAmount=10000, dueDate=-10 days), INV-B (totalAmount=120000, paidAmount=0, dueDate=+20 days). 3. One POSTED supplier payment of 10000 in the last 30 days allocated against INV-A. |
| **Steps** | 1. `GET /api/v1/debt/supplier/uid/$SUPP_UID` with admin auth. |
| **Expected Result** | HTTP 200. Response envelope: `data.supplierUid`=SUPP_UID; `data.supplierName` matches; `data.currencyCode`="TZS"; `data.totalOutstanding`=190000 (70000 outstanding on INV-A + 120000 on INV-B); `data.openInvoiceCount`=2; `data.overdueInvoiceCount`=1 (only INV-A is overdue); `data.openInvoices` has 2 rows sorted dueDate asc (INV-A first — older due); INV-A row: `outstanding`=70000, `daysOverdue`=10, `status`="POSTED" or "PARTIALLY_PAID"; INV-B row: `daysOverdue`=null (not overdue); `data.recentPayments` has 1 row with `totalAmount`=10000; `data.agingRow.d1_30`=70000 (INV-A outstanding in D_1_30 bucket); `data.agingRow.current`=120000 (INV-B outstanding is CURRENT). `data.supplierId` serialised as JSON string. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Open invoices capped at 100, sorted dueDate asc. Recent payments: last 30 days, capped at 50, newest-first. Outstanding = totalAmount − paidAmount computed in-memory. |

---

### TC-SUPPLIER-DEBT-009 — Supplier statement for unknown uid returns 404 / not-found

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-009 |
| **Title** | GET /debt/supplier/uid/{uid} with non-existent uid returns 404 |
| **Area** | debt / supplier-AP |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-DEBT-006 |
| **Preconditions** | Authenticated as rootadmin. |
| **Steps** | 1. `GET /api/v1/debt/supplier/uid/01AAAAAAAAAAAAAAAAAAAAAAAAA` (a well-formed ULID that does not exist in the DB). |
| **Expected Result** | HTTP 404; `data` is null or absent; `errors` contains a message referencing the uid. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `requireSupplierByUid` throws `NoSuchElementException` when party not found; global exception handler must map this to 404. Verify the exception-handler mapping — `NoSuchElementException` → 404 is the project convention (confirm in `GlobalExceptionHandler`). |

---

### TC-SUPPLIER-DEBT-010 — Supplier statement rejects cross-tenant uid (tenant isolation)

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-010 |
| **Title** | GET /debt/supplier/uid/{uid} with a uid belonging to another company's party returns 404 (not 200 with leaked data) |
| **Area** | debt / supplier-AP |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-DEBT-006 |
| **Preconditions** | BLOCKED(needs-exclusive-state): requires a second company tenant with a supplier party. In a shared container, this case must be run on a dedicated instance. Mark BLOCKED if only one company is provisioned. If two companies are available: 1. Obtain a valid `DEBT.READ`-holding token for company A. 2. Know a valid supplier uid belonging to company B. |
| **Steps** | 1. Authenticate as company A user. 2. `GET /api/v1/debt/supplier/uid/{companyB_supplierUid}`. |
| **Expected Result** | HTTP 404; response contains error message "Supplier not found"; no company B data leaked. The service checks `party.companyId == context.companyId()` before proceeding — a mismatch results in `NoSuchElementException`. |
| **Automatable?** | yes — integration test (two-tenant scenario via Testcontainers; requires second company bootstrap) |
| **Result/Status** | BLOCKED(needs-exclusive-state) |
| **Notes/IssueRef** | Isolation guard in `SupplierDebtReadModelServiceImpl.requireSupplierByUid()` lines 291-295: checks `party.getCompanyId() == context.companyId()` and throws `NoSuchElementException` with the same message as not-found, preventing tenant enumeration. |

---

### TC-SUPPLIER-DEBT-011 — Unauthenticated request returns 401

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-011 |
| **Title** | GET /debt/supplier-aging without Authorization header returns 401 |
| **Area** | debt / supplier-AP |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-DEBT-002 |
| **Preconditions** | None — no token needed. |
| **Steps** | 1. `curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/api/v1/debt/supplier-aging`. 2. Repeat for `/debt/supplier-dunning`. 3. Repeat for `/debt/supplier/uid/01AAAAAAAAAAAAAAAAAAAAAAAAA`. |
| **Expected Result** | HTTP 401 for all three endpoints. Response body is `ApiResponse` with `status`="error". No data leaked in response. |
| **Automatable?** | yes — e2e / integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Class-level `@PreAuthorize("hasAuthority('DEBT.READ')")` on `SupplierDebtController` — the Spring Security filter chain should intercept before method invocation and return 401 (no JWT) or 403 (valid JWT, missing permission). |

---

### TC-SUPPLIER-DEBT-012 — Insufficient permission (no DEBT.READ) returns 403

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-012 |
| **Title** | User without DEBT.READ permission is denied access to all three AP debt endpoints with 403 |
| **Area** | debt / supplier-AP |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-DEBT-002 |
| **Preconditions** | 1. A user account that holds a token but does NOT have the `DEBT.READ` permission (id=130). The seeded POS cashier (`cashier` / `Cashier#2026`) holds no DEBT permissions and is suitable. |
| **Steps** | 1. `POST /api/v1/auth/login` body `{"username":"cashier","password":"Cashier#2026"}` → capture `accessToken`. 2. `GET /api/v1/debt/supplier-aging` with `Authorization: Bearer <cashierToken>`. 3. `GET /api/v1/debt/supplier-dunning` with cashier token. 4. `GET /api/v1/debt/supplier/uid/01AAAAAAAAAAAAAAAAAAAAAAAAA` with cashier token. |
| **Expected Result** | HTTP 403 for steps 2, 3, 4. Response body is `ApiResponse` with `status`="error". No aging/dunning data returned. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Permission id 130 seeded in V70 Flyway migration. If cashier role is granted DEBT.READ in future, pick a different low-privilege user or create a dedicated test role. |

---

### TC-SUPPLIER-DEBT-013 — Ageing asOf parameter shifts bucket classification

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-013 |
| **Title** | GET /debt/supplier-aging?asOf=<past-date> shifts overdue classification relative to that date |
| **Area** | debt / supplier-AP |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-DEBT-003 |
| **Preconditions** | 1. Test supplier with one POSTED invoice: dueDate = 2026-04-15, totalAmount = 75000, paidAmount = 0. |
| **Steps** | 1. `GET /api/v1/debt/supplier-aging?asOf=2026-04-20` (5 days after due). 2. Note bucket values. 3. `GET /api/v1/debt/supplier-aging?asOf=2026-05-20` (35 days after due). 4. Note bucket values. |
| **Expected Result** | Step 2 (asOf=2026-04-20): invoice is 5 days overdue → `d1_30`=75000, `current`=0, `d31_60`=0. `data.asOf`="2026-04-20". Step 4 (asOf=2026-05-20): invoice is 35 days overdue → `d31_60`=75000, `d1_30`=0. `data.asOf`="2026-05-20". The bucket boundary shifts with the `asOf` anchor, not with today's date. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `aging()` passes `asOf` to the accumulator via `today = asOf != null ? asOf : LocalDate.now()`. When `asOf` is null, it defaults to `LocalDate.now()` — verified by TC-SUPPLIER-DEBT-001. |

---

### TC-SUPPLIER-DEBT-014 — Statement uid path segment validated as ULID

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-014 |
| **Title** | GET /debt/supplier/uid/{uid} with a malformed uid (not a ULID) returns 400 |
| **Area** | debt / supplier-AP |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-DEBT-006 |
| **Preconditions** | Authenticated as rootadmin. |
| **Steps** | 1. `GET /api/v1/debt/supplier/uid/not-a-ulid` with admin auth. 2. `GET /api/v1/debt/supplier/uid/123` with admin auth. 3. `GET /api/v1/debt/supplier/uid/` with admin auth (empty segment). |
| **Expected Result** | Step 1 and 2: HTTP 400; `errors` contains a message referencing ULID validation failure. Step 3: HTTP 404 or 405 (no route match — path segment absent). No 500 in any case. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `@ValidUlid` on `@PathVariable String uid` in `SupplierDebtController.supplierStatement()`. Validation is via `com.orbix.engine.modules.common.validation.ValidUlid`. |

---

### TC-SUPPLIER-DEBT-015 — Dunning queue pagination works correctly

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-015 |
| **Title** | GET /debt/supplier-dunning?page=1&size=2 returns second page of results |
| **Area** | debt / supplier-AP |
| **Dimension** | DATA |
| **Priority** | P2 |
| **Linked US-*** | US-DEBT-003 |
| **Preconditions** | 1. At least 3 test suppliers each with one overdue invoice (so `totalElements` >= 3). |
| **Steps** | 1. `GET /api/v1/debt/supplier-dunning?page=0&size=2` — capture `data.content` (2 rows) and `data.totalElements`. 2. `GET /api/v1/debt/supplier-dunning?page=1&size=2` — capture `data.content` (should be 1 row if totalElements=3). 3. Verify no row appears on both pages. 4. `GET /api/v1/debt/supplier-dunning?page=99&size=25` — beyond total pages. |
| **Expected Result** | Step 1: `data.page`=0, `data.size`=2, `data.content` has 2 entries. Step 2: `data.page`=1, `data.content` has 1 entry; it is a different supplier from page 0. Step 4: `data.content` is empty; `data.totalElements` same as step 1. No 500 on out-of-range page. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Dunning pagination is in-memory (Java subList). `PageRequest.of(page, size)` converts to `pageable.getOffset()` and `getPageSize()`. |

---

### TC-SUPPLIER-DEBT-016 — Company-wide ageing (no branchId) aggregates all branches

| Field | Value |
|-------|-------|
| **ID** | TC-SUPPLIER-DEBT-016 |
| **Title** | GET /debt/supplier-aging without branchId returns company-wide totals across all branches |
| **Area** | debt / supplier-AP |
| **Dimension** | INT |
| **Priority** | P1 |
| **Linked US-*** | US-DEBT-003 |
| **Preconditions** | 1. Test supplier S has two POSTED invoices: one in branch 1 for 60000, one in branch 2 for 40000 (requires branch 2 to exist). If only branch 1 is provisioned: at minimum two invoices in branch 1 and compare branch-scoped vs company-wide totals. |
| **Steps** | 1. `GET /api/v1/debt/supplier-aging?branchId=1&asOf=<today>` — note supplier S totalOutstanding. 2. `GET /api/v1/debt/supplier-aging` (no branchId) — note supplier S totalOutstanding. |
| **Expected Result** | Without branchId: `data.branchId` is null in response; `data.rows` includes invoices from all branches; supplier S `totalOutstanding` = sum across all branches (100000 if both branches exist). With `branchId=1`: only branch 1's 60000 appears. With no branch filter: `totals.totalOutstanding` >= branch-specific total. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `branchScope.requireReadable(null)` returns null; `findAllOpenForAging(companyId, null)` executes JPQL with `(:branchId is null or s.branchId = :branchId)` — null param matches all rows. |

---

## Coverage summary

| Dimension | Cases |
|-----------|-------|
| FUNC | TC-001, TC-005, TC-006, TC-008 |
| NEG | TC-002, TC-007, TC-009, TC-014 |
| DATA | TC-003, TC-013, TC-015 |
| SEC | TC-010, TC-011, TC-012 |
| INT | TC-004, TC-016 |

**P0:** TC-001, TC-002, TC-003, TC-008, TC-010, TC-011, TC-012
**P1:** TC-004, TC-005, TC-006, TC-007, TC-009, TC-013, TC-016
**P2:** TC-014, TC-015

**Exit criteria for this area:**
- All P0 cases PASS on QA container.
- TC-010 UNBLOCKED and PASS before multi-tenant production rollout.
- No 5xx responses for any case in TC-007, TC-009, TC-011, TC-012, TC-014.
