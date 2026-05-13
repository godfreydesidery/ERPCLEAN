# Admin — test plan

Module-level tests for org / company / branch / section / currency / fx_rate / till_currency masters and the first-run wizard.

## First-run wizard

### TC-ADMIN-001 — Bootstrap empty deployment [P1]
**Stories:** US-COMP-001
**Steps:** POST /api/v1/setup/first-run with org / company / branch / admin payload.
**Expected:** Creates organisation + 1 company + 1 branch + 1 section ("Main Floor", type RETAIL_FLOOR) + admin user + per-branch walk-in customer + default RETAIL price list + functional currency in `currency`. All in one transaction. Idempotent on company code.

### TC-ADMIN-002 — Re-run wizard on existing deployment is no-op [P1]
**Steps:** First-run already succeeded. POST /api/v1/setup/first-run again with same payload.
**Expected:** 200, no new rows; idempotency key is `company.code`.

### TC-ADMIN-003 — Re-run with different payload returns conflict [P1]
**Steps:** Wizard already created `ORBIX-UG`. POST with different organisation name + same company code.
**Expected:** 409.

## Branch CRUD

### TC-ADMIN-004 — Create a branch [P1]
**Stories:** US-COMP-004
**Steps:** POST /api/v1/branches `{ code: "BR-2", name: "Mbarara", timezone: "Africa/Kampala", ... }`.
**Expected:** 201; auto-creates default RETAIL_FLOOR section; provisions per-branch number sequences (LPO-BR2-..., INV-BR2-..., RCT-BR2-..., TILL-BR2-..., ORD-BR2-..., GC-BR2-...); auto-creates walk-in customer per `party` module.

### TC-ADMIN-005 — Duplicate branch code per company [P1]
**Steps:** POST with `code = BR-1` (already exists in this company).
**Expected:** 409 `UNIQUE_CONSTRAINT_VIOLATION (company_id, code)`.

### TC-ADMIN-006 — Deactivate branch with no open data [P2]
**Steps:** Deactivate a branch that has no active till_session and no open business_day.
**Expected:** 200; status = INACTIVE; existing transactional history preserved.

### TC-ADMIN-007 — Deactivate branch with open till_session [P1]
**Steps:** Deactivate while a till session is OPEN.
**Expected:** 422 `BRANCH_HAS_OPEN_TILL`.

## Section CRUD

### TC-ADMIN-008 — Create a section [P1]
**Stories:** US-ADMIN-001
**Steps:** POST /api/v1/branches/{id}/sections `{ code: "BAKERY", name: "Bakery", type: "BAKERY", manager_user_id: ... }`.
**Expected:** 201; `SectionCreated.v1` emitted; UNIQUE (branch_id, code).

### TC-ADMIN-009 — Assign manager to section [P1]
**Stories:** US-ADMIN-002
**Steps:** PATCH /api/v1/sections/{id} `{ manager_user_id: ... }`.
**Expected:** 200; audit log records the change; manager receives notification (out-of-band).

### TC-ADMIN-010 — Deactivate section with no tills / BOMs [P2]
**Stories:** US-ADMIN-005
**Steps:** Deactivate a section that has no till and no BOM.
**Expected:** 200; status = INACTIVE.

### TC-ADMIN-011 — Deactivate last RETAIL_FLOOR section blocked [P1]
**Steps:** Attempt to deactivate the only RETAIL_FLOOR section in a branch.
**Expected:** 422 `BRANCH_REQUIRES_RETAIL_FLOOR`.

### TC-ADMIN-012 — Deactivate section with referenced tills [P1]
**Steps:** Section has 2 active tills.
**Expected:** 422 `SECTION_HAS_ACTIVE_TILLS`.

## Currency + FX

### TC-ADMIN-013 — Enable a currency [P1]
**Stories:** US-ADMIN-003
**Steps:** POST /api/v1/currencies `{ code: "USD", name: "US Dollar", symbol: "$", minor_unit_digits: 2 }`.
**Expected:** 201; `CurrencyEnabled.v1`. Cannot re-enable an already-active currency (409).

### TC-ADMIN-014 — Quote an FX rate [P1]
**Stories:** US-ADMIN-004
**Steps:** POST /api/v1/fx-rates `{ from_currency: "UGX", to_currency: "USD", rate: 0.000263, effective_at: now }`.
**Expected:** 201; `FxRateQuoted.v1`. Rate > 0; same-currency rate (UGX→UGX) rejected with 422.

### TC-ADMIN-015 — FX rate lookup at sale time [P1]
**Type:** Functional / Integration
**Steps:**
1. Quote rate at 10:00 (rate A).
2. Quote rate at 14:00 (rate B).
3. Look up the rate for a sale at 12:00.

**Expected:** Returns rate A (most recent ≤ sale time).

### TC-ADMIN-016 — Configure accepted currencies per till [P2]
**Stories:** US-ADMIN-006
**Steps:** PUT /api/v1/tills/{tillId}/currencies `[ "USD", "EUR" ]`.
**Expected:** 200; `till_currency` rows replaced; functional currency implicit (not in table).

### TC-ADMIN-017 — Remove a currency from till [P2]
**Steps:** DELETE /api/v1/tills/{tillId}/currencies/USD.
**Expected:** 200; future USD tender on this till rejected.

## Active branch switching

### TC-ADMIN-018 — Switch active branch in session [P1]
**Stories:** US-COMP-005
**Steps:** User with access to branches A and B. PUT /api/v1/session/active-branch `{ branch_id: B }`.
**Expected:** 200; subsequent requests without `X-Branch-Id` header use B as default.

### TC-ADMIN-019 — Switch to branch user has no access to [P1]
**Steps:** PUT /api/v1/session/active-branch `{ branch_id: C }` where user has no role on C.
**Expected:** 403.

## Negative / edge

### TC-ADMIN-020 — Invalid timezone on branch create [P2]
**Steps:** POST branch with `timezone: "Not/Real"`.
**Expected:** 422.

### TC-ADMIN-021 — Section type not in enum [P1]
**Steps:** POST section with `type: "AUTOMOTIVE"`.
**Expected:** 422 with allowed values list.

### TC-ADMIN-022 — Concurrent branch create with same code [P2]
**Steps:** Two concurrent POSTs with same `(company_id, code)`.
**Expected:** Exactly one succeeds; the other 409.

### TC-ADMIN-023 — Audit log captures every admin write [P1]
**Steps:** Create org / company / branch / section. Inspect `audit_log`.
**Expected:** 4 rows with the correct `actor_id`, `action`, `entity_type`, `entity_id`, `prev_hash` / `row_hash` set.
