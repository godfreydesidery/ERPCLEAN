# TC-NFR — Non-Functional Requirements: Security, Performance, UX, Data Integrity, Reliability

**Dimensions covered:** SEC, PERF, UX, DATA, RELI  
**Applies to:** All modules (system-wide)

---

## Security

### TC-NFR-SEC-001 — BCrypt cost >= 12

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-SEC-001 |
| **Title** | Password encoder uses BCrypt with cost factor >= 12 |
| **Area** | auth |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-001 |
| **Preconditions** | QA container running. |
| **Steps** | 1. Login as rootadmin. 2. Inspect `app_user.password_hash` from DB (via phpMyAdmin at :8090 or `docker exec` MariaDB). 3. Extract cost factor from hash prefix. |
| **Expected Result** | Hash starts with `$2a$12$` or higher cost (e.g. `$2a$14$`). Never `$2a$10$`. |
| **Automatable?** | yes — unit test (inspect BCryptPasswordEncoder bean strength) |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-NFR-SEC-002 — JWT TTL within spec; alg matches profile

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-SEC-002 |
| **Title** | Access token exp-iat <= 900s; local profile uses HS256; prod profile RS256 |
| **Area** | auth |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-001 |
| **Preconditions** | QA container running (qa profile — ephemeral HS256 key). |
| **Steps** | 1. Login. 2. Base64-decode JWT header and payload. 3. Compute `exp - iat`. |
| **Expected Result** | `exp - iat = 900` (±2s). `alg = HS256` in qa profile. `iss = orbix-engine` or equivalent. No sensitive data (password hash, PII) in payload. |
| **Automatable?** | yes — unit test (`JwtServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-NFR-SEC-003 — Tampered JWT signature rejected

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-SEC-003 |
| **Title** | JWT with altered payload character rejected with 401 |
| **Area** | auth |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-001 |
| **Preconditions** | Valid JWT obtained. |
| **Steps** | 1. Split JWT into header.payload.signature. 2. Base64-decode payload. Change `sub` to a different user. Re-encode. Reconstruct token with original signature. 3. Use tampered token on any protected endpoint. |
| **Expected Result** | HTTP 401. No request processed. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-NFR-SEC-004 — CORS rejects disallowed origins

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-SEC-004 |
| **Title** | CORS preflight from evil origin returns no ACAO header or 403 |
| **Area** | auth |
| **Dimension** | SEC |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-001 |
| **Preconditions** | QA container running. |
| **Steps** | 1. `curl -s -X OPTIONS http://localhost:8081/api/v1/items -H "Origin: https://evil.example.com" -H "Access-Control-Request-Method: GET" -v`. |
| **Expected Result** | No `Access-Control-Allow-Origin: https://evil.example.com` in response. Only configured origins allowed (localhost, production domain). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | PRD §2.1 G5: "Tighter security baseline" |

---

### TC-NFR-SEC-005 — SQL injection attempt returns 400, not 500

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-SEC-005 |
| **Title** | SQL injection string in query param does not cause 500 or expose stack trace |
| **Area** | api (all) |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | (platform security) |
| **Preconditions** | QA container running. |
| **Steps** | 1. `GET /api/v1/items?q=' OR '1'='1` (URL-encoded). 2. `GET /api/v1/customers?name='; DROP TABLE customer; --`. |
| **Expected Result** | HTTP 200 with empty results, or HTTP 400 validation error. No 500. No stack trace in response body. JPQL/CriteriaBuilder used — parameterized query prevents injection. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | CLAUDE.md: "JPQL or CriteriaBuilder only. Native queries are banned unless wrapped." |

---

### TC-NFR-SEC-006 — Sensitive data absent from error responses

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-SEC-006 |
| **Title** | Error responses do not include password_hash, stack traces, or internal paths |
| **Area** | api (all) |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | (platform security) |
| **Preconditions** | QA container running. |
| **Steps** | 1. Login with wrong password. Inspect full response body. 2. POST /items with invalid payload. Inspect 400 response. 3. Trigger a 500 (e.g. invalid UUID format in path). |
| **Expected Result** | No `password_hash`, no Java class names/stack traces, no file paths, no DB error messages visible in any error response body. |
| **Automatable?** | partial — Playwright checking response body; unit test on GlobalExceptionHandler |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-NFR-SEC-007 — Multi-tenant: branch-A user cannot read branch-B POS sales

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-SEC-007 |
| **Title** | User scoped to branch A cannot retrieve pos_sale rows from branch B |
| **Area** | pos / iam |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-009 |
| **Preconditions** | 1. Branch A (id=1), Branch B (id=2). 2. POS sale exists for branch B (uid=<sale_B_uid>). 3. Cashier has role scoped to branch A only. |
| **Steps** | 1. Login as cashier (branch A only). 2. `GET /api/v1/pos-sales/uid/<sale_B_uid>` with `X-Branch-Id: 1`. |
| **Expected Result** | HTTP 403 or 404 (not a 200 returning branch-B data). Branch B sale not accessible from a branch-A token. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Critical: data isolation — Blocker if violated |

---

### TC-NFR-SEC-008 — Audit log append-only; no DELETE on audit_log

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-SEC-008 |
| **Title** | There is no API endpoint or service method that deletes audit_log rows |
| **Area** | iam |
| **Dimension** | SEC |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-014 |
| **Preconditions** | Codebase scan. |
| **Steps** | 1. Search codebase for `DELETE FROM audit_log` or `auditLogRepository.delete`. 2. Verify no API controller exposes a DELETE /audit endpoint. |
| **Expected Result** | Zero results — audit_log is append-only. No TRUNCATE, DELETE, or bulk-delete path exists. |
| **Automatable?** | yes — ArchUnit or grep-based static check |
| **Result/Status** | |
| **Notes/IssueRef** | US-IAM-014 AC: "verify the audit log has not been tampered with" |

---

### TC-NFR-SEC-009 — PAN never stored in pos_payment

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-SEC-009 |
| **Title** | Card payment reference field in pos_payment does not store PAN or full card number |
| **Area** | pos |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | CARD payment posted with reference "4111-1111-1111-1111". |
| **Steps** | 1. Post POS sale with CARD payment reference containing a 16-digit PAN. 2. Query `pos_payment.reference` from DB. |
| **Expected Result** | If the reference is stored as-is, this is acceptable (client should not send PANs). Verify no server-side processing trims or logs PANs. Ideally reference is a terminal receipt number, not a PAN. If a PAN IS stored, file Critical security issue. |
| **Automatable?** | partial — policy check + DB inspection |
| **Result/Status** | |
| **Notes/IssueRef** | US-POS-009 AC: "PAN is never stored" |

---

## Performance

### TC-NFR-PERF-001 — Login endpoint responds within 2 seconds

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-PERF-001 |
| **Title** | POST /auth/login responds within 2 seconds including BCrypt verification |
| **Area** | auth |
| **Dimension** | PERF |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-001 |
| **Preconditions** | QA container at idle. |
| **Steps** | 1. Time 10 consecutive logins: `time curl -s -X POST http://localhost:8081/api/v1/auth/login -H "Content-Type: application/json" -d '{"username":"rootadmin","password":"SKp315goPN8Nb0yJtMCCD7cm"}'`. |
| **Expected Result** | p95 response time < 2000ms. BCrypt cost 12 on modern hardware takes ~200-400ms; allow for network overhead. |
| **Automatable?** | partial — shell timing loop |
| **Result/Status** | |
| **Notes/IssueRef** | US-IAM-012 AC: "POS fingerprint login in under 5 seconds" — password login should be faster |

---

### TC-NFR-PERF-002 — Item list endpoint responds within 500ms for 1000 items

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-PERF-002 |
| **Title** | GET /items with 1000 items responds within 500ms p95 |
| **Area** | catalog |
| **Dimension** | PERF |
| **Priority** | P1 |
| **Linked US-*** | US-CAT-005 |
| **Preconditions** | 1000 items seeded. Index exists on `item.company_id + item.status`. |
| **Steps** | 1. Seed 1000 items (or use existing dataset). 2. Time 20 calls to `GET /api/v1/items?pageSize=20`. |
| **Expected Result** | p95 < 500ms; p50 < 200ms. No full table scan (check EXPLAIN if needed). |
| **Automatable?** | partial — curl timing loop; requires 1000-item dataset |
| **Result/Status** | |
| **Notes/IssueRef** | US-CAT-005 AC: "Search returns within 100ms for 50k-item catalog" (Meilisearch target) — server list endpoint has different budget |

---

### TC-NFR-PERF-003 — POS sync push of 50 ops < 5 seconds

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-PERF-003 |
| **Title** | Batch push of 50 POS_SALE ops completes within 5 seconds wall time |
| **Area** | pos/sync |
| **Dimension** | PERF |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | 50 items in stock. Open till session. |
| **Steps** | 1. Build 50 valid POS_SALE ops. 2. Time `POST /api/v1/sync/push`. |
| **Expected Result** | Total request duration < 5000ms; all 50 accepted. Individual op transaction time < 100ms avg. |
| **Automatable?** | partial — integration test with timing assertion |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-NFR-PERF-004 — Report endpoint paginates; no OOM on large dataset

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-PERF-004 |
| **Title** | Sales report with 5000+ rows does not load all into memory; pagination enforced |
| **Area** | reports |
| **Dimension** | PERF |
| **Priority** | P2 |
| **Linked US-*** | US-RPT-008 |
| **Preconditions** | 5000+ invoice rows. |
| **Steps** | 1. Call report endpoint without pageSize — check if server enforces a max page size. 2. Monitor container memory during report call: `docker stats orbix`. |
| **Expected Result** | Server enforces max page size (e.g. 500); does not attempt to load 5000 rows into a single List<> in memory; memory stays below baseline + 200MB. |
| **Automatable?** | partial — manual monitoring; container stats |
| **Result/Status** | |
| **Notes/IssueRef** | US-RPT-008 AC: "Exports run as background jobs for results > 5000 rows" |

---

### TC-NFR-PERF-005 — Concurrent till sessions do not corrupt balances (optimistic lock)

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-PERF-005 |
| **Title** | Two concurrent POS sales for the same item reduce stock correctly (no phantom read) |
| **Area** | pos / stock |
| **Dimension** | PERF |
| **Priority** | P1 |
| **Linked US-*** | US-POS-009 US-STOCK-010 |
| **Preconditions** | Item COKE500 qty_on_hand=5. Two concurrent sessions open. |
| **Steps** | 1. From two threads simultaneously, post POS sale of 3x COKE500 each (total 6 > stock). 2. Check qty_on_hand after both requests complete. 3. Check number of successful sales. |
| **Expected Result** | At most one sale succeeds for 3 units; the other gets 422 insufficient stock (or one gets stock-blocked after the first commits). Final qty_on_hand = 5 - 3 = 2 or 5 (if both blocked). No value < 0 without STOCK.OVERSELL. No corrupted intermediate state. |
| **Automatable?** | partial — concurrent integration test; timing-sensitive |
| **Result/Status** | |
| **Notes/IssueRef** | Optimistic locking on item_branch_balance expected; version column or pessimistic SELECT FOR UPDATE |

---

## UX / Accessibility

### TC-NFR-UX-001 — Login page passes axe-core WCAG AA

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-UX-001 |
| **Title** | Login page at / has zero axe-core WCAG AA violations |
| **Area** | web / auth |
| **Dimension** | UX |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-001 |
| **Preconditions** | Web app running at http://localhost:8081/. Playwright + axe-core configured. |
| **Steps** | 1. Navigate to `http://localhost:8081/` (login page). 2. Run `checkA11y()` via Playwright axe integration. |
| **Expected Result** | 0 axe violations at WCAG AA level. |
| **Automatable?** | yes — Playwright + axe-core (CI gate) |
| **Result/Status** | |
| **Notes/IssueRef** | WCAG AA is a CI gate per CLAUDE.md |

---

### TC-NFR-UX-002 — Dashboard page passes axe-core WCAG AA

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-UX-002 |
| **Title** | Dashboard page after login has zero axe-core WCAG AA violations |
| **Area** | web / dashboard |
| **Dimension** | UX |
| **Priority** | P0 |
| **Linked US-*** | US-RPT-002 |
| **Preconditions** | Logged in as rootadmin. |
| **Steps** | 1. Login and navigate to dashboard. 2. Run `checkA11y()`. |
| **Expected Result** | 0 axe violations. |
| **Automatable?** | yes — Playwright + axe |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-NFR-UX-003 — All form pages pass axe-core WCAG AA

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-UX-003 |
| **Title** | Every create/edit form page has zero axe-core violations |
| **Area** | web (all features) |
| **Dimension** | UX |
| **Priority** | P0 |
| **Linked US-*** | (all US-* with Web surface) |
| **Preconditions** | Web app running. Playwright e2e suite configured. |
| **Steps** | 1. Run `npm run e2e` Playwright suite which exercises each feature page. 2. `checkA11y()` called on every page in the suite. |
| **Expected Result** | 0 axe violations across: login, dashboard, catalog/items, catalog/price-lists, party/customers, party/suppliers, sales/invoices, procurement/lpo, procurement/grn, stock, day, cash, reports, admin, debt. |
| **Automatable?** | yes — Playwright + axe (CI gate) |
| **Result/Status** | |
| **Notes/IssueRef** | axe gate blocks CI per CLAUDE.md |

---

### TC-NFR-UX-004 — No raw id/uid inputs in any form

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-UX-004 |
| **Title** | All reference fields across all forms are name pickers, not raw numeric id inputs |
| **Area** | web (all features) |
| **Dimension** | UX |
| **Priority** | P0 |
| **Linked US-*** | (all US-* with Web surface) |
| **Preconditions** | Web app running. Post-PR-54 (pickers merged). |
| **Steps** | 1. Navigate to each major create form (items, customers, suppliers, LPO, GRN, invoice, POS setup). 2. For each reference field, check that it is a picker component not an input[type=number]. 3. Playwright: assert no `input[type=number]` exists for fields like customerId, itemId, supplierId, branchId, vatGroupId, uomId, itemGroupId, priceListId. |
| **Expected Result** | Zero forms have raw numeric id inputs visible to the user. Every reference field uses a typeahead, dropdown, or modal picker that shows the entity name. |
| **Automatable?** | yes — Playwright selector assertion |
| **Result/Status** | |
| **Notes/IssueRef** | CLAUDE.md feedback-no-raw-id-entries; PR #54 reference |

---

### TC-NFR-UX-005 — Keyboard navigation works for critical flows

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-UX-005 |
| **Title** | Login, item search, and invoice creation are operable by keyboard alone |
| **Area** | web |
| **Dimension** | UX |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-001 US-CAT-005 US-SALES-005 |
| **Preconditions** | Web app running. |
| **Steps** | 1. Navigate to login page. Tab to username, type, Tab to password, type, Enter to submit. 2. Navigate to catalog. Tab to search box. Type "coke". Verify results appear. Arrow-key select item. 3. Navigate to New Invoice. Tab through form fields. |
| **Expected Result** | All interactive elements reachable by Tab; forms submittable by Enter; no keyboard trap; focus indicators visible. |
| **Automatable?** | partial — Playwright keyboard events; some requires visual inspection |
| **Result/Status** | |
| **Notes/IssueRef** | WCAG 2.1 SC 2.1.1 (Keyboard) |

---

### TC-NFR-UX-006 — Loading, empty, and error states are handled gracefully

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-UX-006 |
| **Title** | List pages show loading spinner, then either data or "no results" message |
| **Area** | web |
| **Dimension** | UX |
| **Priority** | P1 |
| **Linked US-*** | (all feature list pages) |
| **Preconditions** | Web app running. |
| **Steps** | 1. Navigate to a list page (e.g. Customers). Network throttle to slow. Observe loading state. 2. Apply a filter that returns no results. 3. Observe API error scenario (simulate by stopping container briefly). |
| **Expected Result** | Step 1: Loading spinner visible while data loads; no blank white flash. Step 2: "No results" message, not an empty table with no explanation. Step 3: User-friendly error message; no raw HTTP status code shown to user. |
| **Automatable?** | partial — Playwright with network interception |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## Data Integrity

### TC-NFR-DATA-001 — DB-agnostic: migrations run on both MariaDB and PostgreSQL

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-DATA-001 |
| **Title** | All common Flyway migrations apply cleanly to MariaDB 11 and PostgreSQL 15 |
| **Area** | platform |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | (ARCHITECTURE.md §2.3) |
| **Preconditions** | Testcontainers available (or separate Docker instances). |
| **Steps** | 1. Start MariaDB container; run Flyway migrate + validate. 2. Start PostgreSQL container; run Flyway migrate + validate (includes postgres-specific scripts in V*_1 files). 3. Boot Spring context against each; run `HealthSmokeTest`. |
| **Expected Result** | Both: `ddl-auto=validate` passes; no Flyway checksum errors; no schema drift. |
| **Automatable?** | yes — Testcontainers integration test (BLOCKED: HealthSmokeTest needs DB infra — tracked debt) |
| **Result/Status** | |
| **Notes/IssueRef** | HealthSmokeTest is the vehicle for this. DB infra debt from 2026-05-24. |

---

### TC-NFR-DATA-002 — UID is a valid Crockford ULID; no collision in batch

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-DATA-002 |
| **Title** | 1000 consecutively generated UIDs are unique 26-char Crockford Base32 strings |
| **Area** | platform |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | (CLAUDE.md uid convention) |
| **Preconditions** | UidGenerator accessible. |
| **Steps** | 1. Generate 1000 UIDs in a tight loop. 2. Check for duplicates. 3. Verify each is 26 chars and matches Crockford Base32 charset. |
| **Expected Result** | 0 collisions; all 1000 unique; charset `[0-9A-HJKMNP-TV-Z]` (Crockford). |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | CLAUDE.md: "Crockford ULID at @PrePersist" |

---

### TC-NFR-DATA-003 — Money arithmetic uses BigDecimal; no IEEE 754 float anywhere in financial path

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-DATA-003 |
| **Title** | No double/float used for money calculations in PosSaleServiceImpl, CashLedgerService, debt paths |
| **Area** | platform |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-009 US-SALES-005 US-DEBT-001 |
| **Preconditions** | Codebase access. |
| **Steps** | 1. Grep for `double` or `float` in `orbix-engine-api/src/main/java` under `modules/pos`, `modules/cash`, `modules/sales`, `modules/debt`. 2. For any found, verify they are not in financial computation paths. |
| **Expected Result** | Zero `double` or `float` variables used for money amounts. All money fields are `BigDecimal`. |
| **Automatable?** | yes — ArchUnit rule or grep-based static analysis |
| **Result/Status** | |
| **Notes/IssueRef** | Tanzania TZS; MONEY_SCALE=4 in PosSaleServiceImpl |

---

### TC-NFR-DATA-004 — company_id and branch_id on every transactional table

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-DATA-004 |
| **Title** | Tables pos_sale, sales_invoice, grn, debt_entry all have company_id + branch_id columns |
| **Area** | platform |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-COMP-004 |
| **Preconditions** | DB schema accessible. |
| **Steps** | 1. Run `SHOW COLUMNS FROM pos_sale` (and other transactional tables). Check for company_id and branch_id. |
| **Expected Result** | All major transactional tables have company_id NOT NULL and branch_id NOT NULL. RequestContext filter injects these from JWT on every write. |
| **Automatable?** | yes — Flyway migration inspection + ArchUnit entity check |
| **Result/Status** | |
| **Notes/IssueRef** | CLAUDE.md: "Every transactional table carries company_id + branch_id" |

---

### TC-NFR-DATA-005 — Idempotency key uniqueness constraint on transactional outbox

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-DATA-005 |
| **Title** | Duplicate domain event with same aggregate+type+correlation is rejected by DB constraint |
| **Area** | platform |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 |
| **Preconditions** | Domain event infrastructure running. |
| **Steps** | 1. Publish two identical domain events (same opType, same aggregate_id, same idempotency key). 2. Check domain_event table. |
| **Expected Result** | Second insert is either deduplicated at app level or rejected by DB unique constraint; only one event row. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Transactional outbox: `domain_event` table (V78 schema) |

---

## Reliability

### TC-NFR-RELI-001 — Boot-safety: fresh-volume container boots clean (see TC-ADMIN-001)

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-RELI-001 |
| **Title** | Fresh-volume QA container boots, Flyway migrates, ddl-auto=validate passes, health UP |
| **Area** | platform |
| **Dimension** | RELI |
| **Priority** | P0 |
| **Linked US-*** | US-PLAT-001 |
| **Preconditions** | Docker image `orbix:qa` built from current HEAD. |
| **Steps** | Same as TC-ADMIN-001. |
| **Expected Result** | Same as TC-ADMIN-001. |
| **Automatable?** | partial — shell script; Testcontainers for CI (BLOCKED: HealthSmokeTest debt) |
| **Result/Status** | |
| **Notes/IssueRef** | Duplicate reference to TC-ADMIN-001 by design — boot-safety is both Admin and RELI dimension |

---

### TC-NFR-RELI-002 — App restart does not lose in-flight outbox events

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-RELI-002 |
| **Title** | Outbox events pending dispatch survive an application restart |
| **Area** | platform |
| **Dimension** | RELI |
| **Priority** | P1 |
| **Linked US-*** | US-POS-018 US-POS-011 |
| **Preconditions** | Post a POS sale → outbox event written but dispatcher not yet polled. |
| **Steps** | 1. Post a POS sale (with regime=TZ_VFD for fiscal event) — confirm event in `domain_event` table with status PENDING. 2. `docker restart orbix`. 3. Wait for dispatcher to poll. 4. Check event dispatched. |
| **Expected Result** | After restart, dispatcher picks up the pending event and dispatches it; event status moves to DISPATCHED (or equivalent); sale is eventually fiscalized. |
| **Automatable?** | partial — requires container restart; integration test with delay |
| **Result/Status** | |
| **Notes/IssueRef** | CLAUDE.md: "Spring ApplicationEventPublisher directly is not the pattern here because it loses events on crash. Use outbox." |

---

### TC-NFR-RELI-003 — POS sells offline for 30 minutes; all ops sync on reconnect

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-RELI-003 |
| **Title** | POS Flutter app queues 20 sales offline; all 20 sync correctly on reconnect |
| **Area** | pos (Flutter + API) |
| **Dimension** | RELI |
| **Priority** | P0 |
| **Linked US-*** | US-POS-017 US-POS-018 |
| **Preconditions** | POS app running on Windows. QA container accessible. Till session OPEN. |
| **Steps** | 1. Disconnect POS from network. 2. Complete 20 cash sales in POS (items from local SQLite cache). 3. Verify offline banner shows queue depth=20. 4. Reconnect. 5. Wait for sync. 6. Verify all 20 sales appear in server `/api/v1/pos-sales` for this session. 7. Verify stock decremented correctly. |
| **Expected Result** | All 20 sales: ACCEPTED verdict; stock on server decremented by sum of line quantities; no duplicates; queue depth returns to 0; sync timestamp updated. |
| **Automatable?** | partial — Flutter integration test simulating offline; requires manual network control |
| **Result/Status** | |
| **Notes/IssueRef** | US-POS-017 AC: "All sales, payments, pickups, petty cash, and close-till functions work without network" |

---

### TC-NFR-RELI-004 — Invalid enum in API request returns 400, not 500

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-RELI-004 |
| **Title** | Submitting an invalid enum value in a request body returns 400, not 500 |
| **Area** | api (all) |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | (platform — known bug class per project conventions) |
| **Preconditions** | QA container running. |
| **Steps** | 1. `POST /api/v1/items` body with `"type": "NOT_A_REAL_TYPE"`. 2. `POST /api/v1/pos-sales` body with `payments[0].method: "INVALID_METHOD"`. |
| **Expected Result** | HTTP 400; structured validation error listing the invalid field and acceptable values. NOT HTTP 500. (Note: CLAUDE.md convention flags this as a known issue class — "invalid enum currently returns 500 not 400". If 500 is returned, file as TC-NFR-RELI-004-BUG severity Major.) |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Known bug class per CLAUDE.md instructions: "note: invalid enum currently returns 500 not 400 — include a case" |

---

### TC-NFR-RELI-005 — ArchUnit ModuleBoundaryTest passes with 0 violations

| Field | Value |
|-------|-------|
| **ID** | TC-NFR-RELI-005 |
| **Title** | ArchUnit ModuleBoundaryTest runs green: controllers not touching repositories; correct layer order |
| **Area** | platform |
| **Dimension** | RELI |
| **Priority** | P0 |
| **Linked US-*** | (ARCHITECTURE.md — modular monolith) |
| **Preconditions** | `mvn test` environment available. |
| **Steps** | 1. `mvn test -Dtest=ModuleBoundaryTest` from `orbix-engine-api/`. |
| **Expected Result** | 0 violations. Rules: controllers in `com.orbix.engine.api..` may not import repositories; modules talk only via `..domain.dto..` / `..domain.enums..` + common/auth/iam infrastructure; layer order: controller → service → repository → domain. |
| **Automatable?** | yes — ArchUnit (already in test suite) |
| **Result/Status** | |
| **Notes/IssueRef** | CLAUDE.md: "A change that breaks this is a design bug; you do not relax the rule." |
