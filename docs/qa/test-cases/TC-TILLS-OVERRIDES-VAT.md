# TC-TILLS-OVERRIDES-VAT — Till Master Data, Business-Day Overrides, VAT Return

**Module:** pos / day / reports
**Stories:** US-POS-001, US-ADMIN-006, US-DAY-003, US-DAY-004, US-RPT-001, US-POS-021 (F5.1, F5.6, F2.1, F8.8 / US-NFR-COMP-001)
**API base:** `http://localhost:8081/api/v1`
**Auth:** rootadmin / SKp315goPN8Nb0yJtMCCD7cm (POS.MANAGE_TILL + DAY.OVERRIDE + DAY.READ)
**Interference note:** All tills created in these cases use the prefix `TOV-` to avoid cross-runner collisions.

---

## Till Master Data

### TC-TILLS-OVERRIDES-VAT-001 — Create till; verify 201 and full DTO wire shape

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-001 |
| **Title** | POST /tills creates till; returns 201 with uid, code uppercased, status=ACTIVE |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-001 |
| **Preconditions** | 1. Logged in as rootadmin (holds `POS.MANAGE_TILL`). 2. Price list RETAIL id=1 exists. 3. Branch HQ id=1 exists. |
| **Steps** | 1. `POST /api/v1/tills` body: `{"branchId":1,"code":"tov-t01","name":"TOV Till 01","defaultPriceListId":1,"installId":null}`. 2. Capture `data.uid` from the 201 response. 3. `GET /api/v1/tills/uid/<uid>`. |
| **Expected Result** | Step 1: HTTP 201; `Location` header = `/api/v1/tills/uid/<uid>`; body contains `data.uid` (26-char ULID), `data.code = "TOV-T01"` (uppercased), `data.status = "ACTIVE"`, `data.branchId = 1`, `data.companyId` non-null, `data.defaultPriceListId = "1"` (stringified Long). Step 3: same shape confirmed on read. |
| **Automatable?** | yes — integration test (`TillServiceImplTest#createTill_persistsAndReturnsDto`) |
| **Result/Status** | |
| **Notes/IssueRef** | `id` and `defaultPriceListId` serialise as JSON strings due to `IdLongAsStringSerializerModifier`. |

---

### TC-TILLS-OVERRIDES-VAT-002 — Duplicate till code within a branch is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-002 |
| **Title** | POST /tills with duplicate code on the same branch returns 400 |
| **Area** | pos |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-001 |
| **Preconditions** | Till `TOV-T01` exists on branch 1 (TC-TILLS-OVERRIDES-VAT-001 or equivalent). |
| **Steps** | 1. `POST /api/v1/tills` body: `{"branchId":1,"code":"tov-t01","name":"TOV Till 01 Copy","defaultPriceListId":1}`. |
| **Expected Result** | HTTP 400; `errors` or `message` references duplicate code ("Till code already exists for this branch"). No new till row created. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Service checks `tills.existsByBranchIdAndCode` before persisting. |

---

### TC-TILLS-OVERRIDES-VAT-003 — Create till rejects missing required fields

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-003 |
| **Title** | POST /tills with blank code or null branchId returns 400 validation error |
| **Area** | pos |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-001 |
| **Preconditions** | Logged in as rootadmin. |
| **Steps** | 1. `POST /api/v1/tills` body: `{"branchId":1,"code":"","name":"X","defaultPriceListId":1}` (blank code). 2. `POST /api/v1/tills` body: `{"code":"TOV-T99","name":"X","defaultPriceListId":1}` (null branchId). |
| **Expected Result** | Both return HTTP 400; response body contains field-level validation errors referencing the failing constraint (`@NotBlank`, `@NotNull`). |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | `CreateTillRequestDto` uses `@NotNull branchId`, `@NotBlank code`, `@NotBlank name`, `@NotNull defaultPriceListId`. |

---

### TC-TILLS-OVERRIDES-VAT-004 — PATCH /tills/uid/{uid} updates name and price list

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-004 |
| **Title** | PATCH /tills/uid/{uid} persists updated name and defaultPriceListId |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-001 |
| **Preconditions** | Till `TOV-T01` exists (uid from TC-TILLS-OVERRIDES-VAT-001). |
| **Steps** | 1. `PATCH /api/v1/tills/uid/<uid>` body: `{"name":"TOV Till 01 Updated","defaultPriceListId":1,"installId":"dev-box-01"}`. 2. `GET /api/v1/tills/uid/<uid>`. |
| **Expected Result** | Step 1: HTTP 200; `data.name = "TOV Till 01 Updated"`, `data.installId = "dev-box-01"`. Step 2: confirms persistence; `data.status` unchanged (`ACTIVE`). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Code is immutable after creation (not in `UpdateTillRequestDto`). |

---

### TC-TILLS-OVERRIDES-VAT-005 — Deactivate till with no open session

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-005 |
| **Title** | POST /tills/uid/{uid}/deactivate sets status=INACTIVE when no OPEN session |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-001 |
| **Preconditions** | 1. Till `TOV-T02` created fresh (no session ever opened). 2. rootadmin logged in. |
| **Steps** | 1. Create till: `POST /api/v1/tills` body `{"branchId":1,"code":"tov-t02","name":"TOV Till 02","defaultPriceListId":1}`. Capture uid. 2. `POST /api/v1/tills/uid/<uid>/deactivate`. 3. `GET /api/v1/tills/uid/<uid>`. |
| **Expected Result** | Step 2: HTTP 200; `data.status = "INACTIVE"`. Event `TillDeactivated.v1` written to `domain_event` outbox. Step 3: status confirmed `INACTIVE`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-TILLS-OVERRIDES-VAT-006 — Deactivate till blocked when OPEN session exists

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-006 |
| **Title** | POST /tills/uid/{uid}/deactivate returns 400 when till has an OPEN session |
| **Area** | pos |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-POS-001 US-POS-002 |
| **Preconditions** | 1. Till exists with at least one OPEN till session (session status = OPEN). 2. Business day is OPEN. |
| **Steps** | 1. Open a session: `POST /api/v1/till-sessions/open` body `{"tillId":<id>,"openingFloatAmount":10000}`. 2. `POST /api/v1/tills/uid/<till_uid>/deactivate`. |
| **Expected Result** | Step 2: HTTP 400; error references the blocking open session ("Cannot deactivate a till with an OPEN session"). Till status remains `ACTIVE`. Session remains open. |
| **Automatable?** | yes — unit test (`TillServiceImplTest#deactivate_blocked_byOpenSession`) |
| **Result/Status** | |
| **Notes/IssueRef** | Guard is in `TillServiceImpl.deactivate` via `tillSessions.findFirstByTillIdAndStatus(OPEN)`. P0 because a deactivated-with-open-session till would leave cash unreconciled. |

---

### TC-TILLS-OVERRIDES-VAT-007 — Activate an INACTIVE till

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-007 |
| **Title** | POST /tills/uid/{uid}/activate restores INACTIVE till to ACTIVE |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-001 |
| **Preconditions** | Till `TOV-T02` is INACTIVE (TC-TILLS-OVERRIDES-VAT-005 passed). |
| **Steps** | 1. `POST /api/v1/tills/uid/<uid>/activate`. 2. `GET /api/v1/tills/uid/<uid>`. |
| **Expected Result** | Step 1: HTTP 200; `data.status = "ACTIVE"`. Event `TillActivated.v1` emitted to outbox. Step 2: status confirmed `ACTIVE`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-TILLS-OVERRIDES-VAT-008 — List tills filtered by branchId

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-008 |
| **Title** | GET /tills?branchId=1 returns only tills belonging to branch 1 |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-001 |
| **Preconditions** | At least 2 tills on branch 1 exist; branch 2 exists with at least 1 till (or none). |
| **Steps** | 1. `GET /api/v1/tills?branchId=1`. 2. Verify each row's `branchId`. |
| **Expected Result** | HTTP 200; all returned items have `data[*].branchId = 1`; branch-2 tills do not appear. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-TILLS-OVERRIDES-VAT-009 — GET /tills without POS.MANAGE_TILL permission returns 403

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-009 |
| **Title** | Till endpoints require POS.MANAGE_TILL; cashier role without it receives 403 |
| **Area** | pos |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-POS-001 US-IAM-009 |
| **Preconditions** | 1. Cashier user exists with role POS_CASHIER (seeded). 2. POS_CASHIER role does NOT hold `POS.MANAGE_TILL`. |
| **Steps** | 1. Authenticate: `POST /api/v1/auth/login` body `{"username":"cashier","password":"Cashier#2026"}`. Capture token. 2. `GET /api/v1/tills` using cashier token. 3. `POST /api/v1/tills` using cashier token with valid body. |
| **Expected Result** | Steps 2 and 3: HTTP 403. No till data returned or created. |
| **Automatable?** | yes — integration test (Spring Security context wired) |
| **Result/Status** | |
| **Notes/IssueRef** | Controller class-level `@PreAuthorize("hasAuthority('POS.MANAGE_TILL')")`. |

---

### TC-TILLS-OVERRIDES-VAT-010 — Till with invalid uid returns 404

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-010 |
| **Title** | GET /tills/uid/{uid} with unknown uid returns 404; malformed uid returns 400 |
| **Area** | pos |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-001 |
| **Preconditions** | rootadmin logged in. |
| **Steps** | 1. `GET /api/v1/tills/uid/01JAAAAAAAAAAAAAAAAAAAAAAA` (valid ULID format, non-existent till). 2. `GET /api/v1/tills/uid/NOT-A-ULID` (malformed). |
| **Expected Result** | Step 1: HTTP 404 (NoSuchElementException). Step 2: HTTP 400 (`@ValidUlid` constraint violation). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## Till Currencies (FX Tender Configuration)

### TC-TILLS-OVERRIDES-VAT-011 — Add and list FX currency on a till

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-011 |
| **Title** | POST /tills/{tillId}/currencies/{code} adds currency; GET lists it |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-ADMIN-006 US-POS-021 |
| **Preconditions** | 1. rootadmin holds `POS.TILL_CURRENCY_MANAGE`. 2. Till TOV-T01 exists (id captured from TC-TILLS-OVERRIDES-VAT-001). |
| **Steps** | 1. `POST /api/v1/tills/<tillId>/currencies/USD`. 2. `GET /api/v1/tills/<tillId>/currencies`. |
| **Expected Result** | Step 1: HTTP 201; `Location` header `/api/v1/tills/<tillId>/currencies/USD`; body `data.currencyCode = "USD"`, `data.tillId = <tillId>`, `data.createdAt` populated. Step 2: HTTP 200; list contains `{"currencyCode":"USD"}`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-ADMIN-006: "cashiers can't tender an unsupported currency". |

---

### TC-TILLS-OVERRIDES-VAT-012 — Remove FX currency from a till

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-012 |
| **Title** | DELETE /tills/{tillId}/currencies/{code} removes it; subsequent list does not contain it |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-ADMIN-006 |
| **Preconditions** | Till TOV-T01 has USD currency (TC-TILLS-OVERRIDES-VAT-011 passed). |
| **Steps** | 1. `DELETE /api/v1/tills/<tillId>/currencies/USD`. 2. `GET /api/v1/tills/<tillId>/currencies`. |
| **Expected Result** | Step 1: HTTP 204 (no body). Step 2: list does not contain USD entry. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-TILLS-OVERRIDES-VAT-013 — Till currency endpoint requires POS.TILL_CURRENCY_MANAGE

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-013 |
| **Title** | POST/DELETE /tills/{id}/currencies returns 403 when caller lacks POS.TILL_CURRENCY_MANAGE |
| **Area** | pos |
| **Dimension** | SEC |
| **Priority** | P1 |
| **Linked US-*** | US-ADMIN-006 US-IAM-009 |
| **Preconditions** | Cashier token (no `POS.TILL_CURRENCY_MANAGE`). Till id known. |
| **Steps** | 1. `POST /api/v1/tills/<tillId>/currencies/EUR` using cashier token. |
| **Expected Result** | HTTP 403; no currency row created. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## Business-Day Overrides

### TC-TILLS-OVERRIDES-VAT-014 — Post override on a closed business day (happy path)

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-014 |
| **Title** | POST /business-days/uid/{uid}/overrides creates override with entityType, entityId, reason |
| **Area** | day |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-DAY-003 |
| **Preconditions** | 1. rootadmin holds `DAY.OVERRIDE`. 2. At least one CLOSED business day exists for branch 1 — capture its uid via `GET /api/v1/business-days?branchId=1`. NOTE: do NOT use the shared OPEN day; use a previously-closed one or BLOCKED(needs-exclusive-state) if none exists. |
| **Steps** | 1. `POST /api/v1/business-days/uid/<closed_day_uid>/overrides` body: `{"entityType":"SALES_INVOICE","entityId":1,"reason":"TOV test back-date correction — automated QA"}`. 2. Capture `data.uid` of the returned override. 3. `GET /api/v1/business-days/overrides?branchId=1`. |
| **Expected Result** | Step 1: HTTP 201; `Location: /api/v1/business-days/overrides/uid/<override_uid>`; body `data.uid` (ULID), `data.entityType = "SALES_INVOICE"`, `data.entityId = 1`, `data.reason` = supplied text, `data.authorisedBy` = rootadmin userId, `data.archivedAt = null`. Domain event `BusinessDayOverridden.v1` written to `domain_event` table in same transaction. Step 3: list contains the new override for the branch. |
| **Automatable?** | yes — integration test (`BusinessDayServiceImplTest#postOverride_writesAuditAndEvent`) |
| **Result/Status** | |
| **Notes/IssueRef** | concurrencyRisk=possible — this touches shared state (business day row). Run on a CLOSED day only; do not use the shared OPEN day. |

---

### TC-TILLS-OVERRIDES-VAT-015 — Override request with missing required fields is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-015 |
| **Title** | POST /business-days/uid/{uid}/overrides with blank reason or null entityId returns 400 |
| **Area** | day |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-DAY-003 |
| **Preconditions** | A closed business day uid known. rootadmin logged in. |
| **Steps** | 1. `POST /api/v1/business-days/uid/<closed_uid>/overrides` body: `{"entityType":"SALES_INVOICE","entityId":1,"reason":""}` (blank reason). 2. `POST /api/v1/business-days/uid/<closed_uid>/overrides` body: `{"entityType":"TOV_TYPE","reason":"valid reason"}` (null entityId). 3. `POST /api/v1/business-days/uid/<closed_uid>/overrides` body: `{"entityType":"XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX","entityId":1,"reason":"r"}` (entityType > 40 chars). |
| **Expected Result** | All three return HTTP 400 with field-level validation errors. No override row created. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | `PostBusinessDayOverrideRequestDto`: `@NotBlank @Size(max=40) entityType`, `@NotNull entityId`, `@NotBlank @Size(max=4000) reason`. |

---

### TC-TILLS-OVERRIDES-VAT-016 — Archive (void) an override before the back-dated post lands

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-016 |
| **Title** | POST /business-days/overrides/uid/{overrideUid}/archive stamps archivedAt and archivedBy |
| **Area** | day |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-DAY-003 |
| **Preconditions** | An active (non-archived) override exists (from TC-TILLS-OVERRIDES-VAT-014); uid known. |
| **Steps** | 1. `POST /api/v1/business-days/overrides/uid/<override_uid>/archive`. 2. `GET /api/v1/business-days/overrides?branchId=1` and inspect the archived override. |
| **Expected Result** | Step 1: HTTP 200; `data.archivedAt` is a non-null Instant, `data.archivedBy` = rootadmin userId. Step 2: the override appears in the list with `archivedAt` populated (auditors can see the full history). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | "void before the back-dated post has landed" per service contract. |

---

### TC-TILLS-OVERRIDES-VAT-017 — Re-archiving an already-archived override is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-017 |
| **Title** | POST /overrides/uid/{uid}/archive on an already-archived override returns 4xx |
| **Area** | day |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-DAY-003 |
| **Preconditions** | Override already archived (TC-TILLS-OVERRIDES-VAT-016 passed). |
| **Steps** | 1. `POST /api/v1/business-days/overrides/uid/<override_uid>/archive` (second call). |
| **Expected Result** | HTTP 400 or 409; error states override is already archived; `archivedAt` unchanged from first call. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Service contract: "a second archive attempt throws — callers should treat the first response as the source of truth". |

---

### TC-TILLS-OVERRIDES-VAT-018 — Override on non-existent business day uid returns 404

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-018 |
| **Title** | POST /business-days/uid/{uid}/overrides with unknown uid returns 404 |
| **Area** | day |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-DAY-003 |
| **Preconditions** | rootadmin logged in. |
| **Steps** | 1. `POST /api/v1/business-days/uid/01JBBBBBBBBBBBBBBBBBBBBBBB/overrides` body: `{"entityType":"SALES_INVOICE","entityId":1,"reason":"test"}` (non-existent uid). |
| **Expected Result** | HTTP 404; error references unknown business day uid. No override created. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-TILLS-OVERRIDES-VAT-019 — Override endpoint requires DAY.OVERRIDE permission

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-019 |
| **Title** | POST /business-days/uid/{uid}/overrides returns 403 when caller lacks DAY.OVERRIDE |
| **Area** | day |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-DAY-003 US-IAM-009 |
| **Preconditions** | 1. Cashier token (holds only `POS.SALE_POST`, `POS.SESSION_OPEN`). 2. Closed business day uid known. |
| **Steps** | 1. `POST /api/v1/business-days/uid/<closed_uid>/overrides` using cashier token, body: `{"entityType":"SALES_INVOICE","entityId":1,"reason":"test"}`. 2. `POST /api/v1/business-days/overrides/uid/<some_uid>/archive` using cashier token. |
| **Expected Result** | Both return HTTP 403. No override created or archived. |
| **Automatable?** | yes — integration test (Spring Security wired) |
| **Result/Status** | |
| **Notes/IssueRef** | P0: back-dating into a closed day is an audit-sensitive action; cashiers must never hold DAY.OVERRIDE. |

---

### TC-TILLS-OVERRIDES-VAT-020 — Override endpoint tenant-isolates by company

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-020 |
| **Title** | Admin from company B cannot post or read overrides on company A's business days |
| **Area** | day |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-DAY-003 US-IAM-009 |
| **Preconditions** | Two companies exist with separate admin users. Company A has a closed business day uid known to the test. Company B's admin holds DAY.OVERRIDE for their own company. |
| **Steps** | 1. Authenticate as company B admin. 2. `POST /api/v1/business-days/uid/<company_A_day_uid>/overrides` body: `{"entityType":"SALES_INVOICE","entityId":1,"reason":"cross-tenant attempt"}`. 3. `GET /api/v1/business-days/overrides?branchId=<company_A_branch_id>`. |
| **Expected Result** | Step 2: HTTP 403 or 404 (no data leak). Step 3: HTTP 403 or 200 with empty list (no company A overrides returned). Company A's override data is never accessible to company B. |
| **Automatable?** | partial — integration test for service-layer isolation; full tenant setup requires seeding two companies |
| **Result/Status** | BLOCKED(needs-exclusive-state) |
| **Notes/IssueRef** | Requires a second company seeded in the container. Flag as BLOCKED on shared QA container; run on a dedicated wipe. |

---

## VAT Return Report

### TC-TILLS-OVERRIDES-VAT-021 — VAT return for explicit date range returns correct structure

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-021 |
| **Title** | GET /reports/vat-return returns all active VAT groups with zero or non-zero rows |
| **Area** | reports |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-RPT-001 (F8.8 / US-NFR-COMP-001) |
| **Preconditions** | 1. At least 2 active VAT groups seeded (STD18 at 18%, EXEMPT at 0%). 2. At least 1 posted POS sale or sales invoice in the period. 3. rootadmin logged in. |
| **Steps** | 1. `GET /api/v1/reports/vat-return?branchId=1&from=2026-05-01&to=2026-05-31`. |
| **Expected Result** | HTTP 200; `data.from = "2026-05-01"`, `data.to = "2026-05-31"`, `data.branchId = 1`. `data.rows` contains at least 2 entries (one per active VAT group, including zero-activity groups). Each row has `vatGroupId`, `code`, `name`, `rate`, `outputNet`, `outputVat`, `inputNet`, `inputVat`, `netVatPayable`. Grand totals: `totalOutputNet = sum(rows[*].outputNet)`, `totalOutputVat = sum(rows[*].outputVat)`, `totalInputNet = sum(rows[*].inputNet)`, `totalInputVat = sum(rows[*].inputVat)`, `netPayable = totalOutputVat - totalInputVat`. |
| **Automatable?** | yes — integration test (`VatReturnReportServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | Zero-activity VAT groups MUST appear (auditors expect every active rate). Seeded groups: STD18, EXEMPT. |

---

### TC-TILLS-OVERRIDES-VAT-022 — VAT return defaults to previous calendar month when bounds omitted

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-022 |
| **Title** | GET /reports/vat-return with no from/to defaults to previous full calendar month |
| **Area** | reports |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-RPT-001 (F8.8) |
| **Preconditions** | rootadmin logged in. Current date is 2026-05-30. |
| **Steps** | 1. `GET /api/v1/reports/vat-return` (no query params). |
| **Expected Result** | HTTP 200; `data.from = "2026-04-01"`, `data.to = "2026-04-30"` (previous calendar month). `data.branchId` is null or the company-wide scope. Response structure identical to TC-TILLS-OVERRIDES-VAT-021. |
| **Automatable?** | yes — unit test (mock clock fixed to 2026-05-30) |
| **Result/Status** | |
| **Notes/IssueRef** | Default logic: `today.minusMonths(1).firstDayOfMonth()` / `lastDayOfMonth()`. |

---

### TC-TILLS-OVERRIDES-VAT-023 — VAT return grand totals cross-check (money correctness)

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-023 |
| **Title** | VAT return grand totals equal sum of rows; netPayable = totalOutputVat - totalInputVat |
| **Area** | reports |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-RPT-001 (F8.8) |
| **Preconditions** | At least 1 posted POS SALE and 1 posted GRN in the period (so both output and input VAT rows have non-zero values). |
| **Steps** | 1. Post a POS sale for item COKE500 (STD18 group) for 1180 TZS inclusive. 2. Post a GRN receiving COKE500 at cost 500 TZS (STD18 group). 3. `GET /api/v1/reports/vat-return?branchId=1&from=<today>&to=<today>`. 4. Sum `rows[*].outputNet` and compare to `totalOutputNet`. Sum `rows[*].outputVat` and compare to `totalOutputVat`. Compute `totalOutputVat - totalInputVat` and compare to `netPayable`. 5. For the STD18 row: verify `outputNet + outputVat ~= gross_sales_amount` (tax-inclusive); verify `inputVat ~= inputNet * 0.18` (rounded half-up to 4 decimal places). |
| **Expected Result** | All cross-checks pass within 0.0001 TZS rounding tolerance. `netPayable = totalOutputVat - totalInputVat` (positive when output exceeds input). Money precision: 4 decimal places throughout. |
| **Automatable?** | yes — integration test with seeded sale + GRN |
| **Result/Status** | |
| **Notes/IssueRef** | P0: money miscalculation in the VAT return is a Critical/Blocker defect for fiscal compliance. MONEY_SCALE=4, RoundingMode.HALF_UP per `VatReturnReportServiceImpl`. |

---

### TC-TILLS-OVERRIDES-VAT-024 — POS refund subtracts from output VAT (not adds)

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-024 |
| **Title** | A posted REFUND POS sale reduces outputVat in the VAT return for its VAT group |
| **Area** | reports |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-RPT-001 (F8.8) US-POS-019 |
| **Preconditions** | 1. Period contains: (a) 1 POSTED SALE of COKE500 (STD18) for 1180 TZS inclusive. (b) 1 POSTED REFUND of COKE500 (STD18) for 590 TZS inclusive. Net expected output VAT for the STD18 group = ((1180/1.18)*0.18) - ((590/1.18)*0.18) = 180 - 90 = 90 TZS. |
| **Steps** | 1. `GET /api/v1/reports/vat-return?branchId=1&from=<today>&to=<today>`. 2. Locate the STD18 row. 3. Assert `rows[STD18].outputVat` equals the expected 90 TZS net (within rounding tolerance). |
| **Expected Result** | `outputVat` for STD18 = 90 (SALE 180 minus REFUND 90). `netVatPayable` for the row = 90 - inputVat (which may be zero). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | P0: if refunds are incorrectly added (not subtracted) the VAT return overstates the tax liability — a Critical compliance error. Confirmed in `VatReturnReportServiceImpl` lines 74-81 which subtract refund amounts. |

---

### TC-TILLS-OVERRIDES-VAT-025 — Voided and draft sales invoices excluded from VAT return

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-025 |
| **Title** | VOIDED / DRAFT / CANCELLED sales invoices do not contribute to VAT return output |
| **Area** | reports |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-RPT-001 (F8.8) US-SALES-007 |
| **Preconditions** | Period contains: (a) 1 POSTED sales invoice for 1180 TZS (STD18). (b) 1 VOIDED sales invoice for 5000 TZS (STD18) — voided before posting or after posting+void. |
| **Steps** | 1. `GET /api/v1/reports/vat-return?branchId=1&from=<today>&to=<today>`. 2. Verify STD18 `outputNet` reflects only the POSTED invoice. |
| **Expected Result** | `outputNet` for STD18 does NOT include the 5000 TZS voided invoice. Only POSTED status invoices contribute. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | VatReturnDto doc: "POSTED sales invoices (excluding VOIDED + DRAFT + CANCELLED)". P0 because including voided invoices overstates the tax filing. |

---

### TC-TILLS-OVERRIDES-VAT-026 — VAT return rows ordered by VAT group code ascending

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-026 |
| **Title** | GET /reports/vat-return returns rows in ascending VAT group code order |
| **Area** | reports |
| **Dimension** | DATA |
| **Priority** | P2 |
| **Linked US-*** | US-RPT-001 (F8.8) |
| **Preconditions** | At least 2 active VAT groups exist (e.g. EXEMPT code and STD18 code). |
| **Steps** | 1. `GET /api/v1/reports/vat-return?branchId=1&from=2026-05-01&to=2026-05-31`. 2. Extract `rows[*].code` list. |
| **Expected Result** | `rows[*].code` is ordered lexicographically ascending (e.g. `["EXEMPT","STD18"]`). |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Ordering is by `vatGroup.code` asc then `id` asc per `VatReturnReportServiceImpl`. |

---

### TC-TILLS-OVERRIDES-VAT-027 — VAT return when branchId omitted spans whole company

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-027 |
| **Title** | GET /reports/vat-return without branchId returns company-wide rollup |
| **Area** | reports |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-RPT-001 (F8.8) |
| **Preconditions** | At least 2 branches with posted sales in the period. rootadmin has company-wide scope. |
| **Steps** | 1. `GET /api/v1/reports/vat-return?from=2026-05-01&to=2026-05-31` (no branchId). 2. `GET /api/v1/reports/vat-return?branchId=1&from=2026-05-01&to=2026-05-31`. 3. Compare totals. |
| **Expected Result** | Step 1 `totalOutputNet >= step 2 totalOutputNet` (company-wide includes branch 1 plus any other branches). `data.branchId` in step 1 response = null (or company-scope indicator). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `BranchScope.requireReadable(null)` returns null, which the impl uses to span the whole company. |

---

### TC-TILLS-OVERRIDES-VAT-028 — VAT return unauthenticated request returns 401

| Field | Value |
|-------|-------|
| **ID** | TC-TILLS-OVERRIDES-VAT-028 |
| **Title** | GET /reports/vat-return without Authorization header returns 401 |
| **Area** | reports |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-RPT-001 US-IAM-009 |
| **Preconditions** | None. |
| **Steps** | 1. `GET http://localhost:8081/api/v1/reports/vat-return?from=2026-05-01&to=2026-05-31` — no Authorization header. |
| **Expected Result** | HTTP 401; no VAT data in response body. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | P0: fiscal return data must never be publicly accessible. |
