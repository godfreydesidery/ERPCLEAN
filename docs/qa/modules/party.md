# Party — test plan

Module-level tests for the shared party base + customer / supplier / employee / sales_agent / biometric extensions.

## Party base

### TC-PARTY-001 — Create generic party with no role [P2]
**Steps:** POST /api/v1/parties with name + code.
**Expected:** 201; `PartyCreated.v1`. No child role row yet.

### TC-PARTY-002 — Duplicate code per company [P1]
**Steps:** POST two parties with same code in one company.
**Expected:** Second 409. UNIQUE (company_id, code).

### TC-PARTY-003 — Add address + contact [P1]
**Steps:** POST /api/v1/parties/{id}/addresses; POST /api/v1/parties/{id}/contacts.
**Expected:** 201 each; at most one `is_default = true` per `(party_id, label)`; at most one `is_primary = true` per party.

## Customer

### TC-PARTY-004 — Create customer (with party) [P1]
**Stories:** US-SALES-001
**Steps:** POST /api/v1/customers `{ party: { name, code, ... }, credit_limit_amount: 5000000, price_list_id, default_sales_agent_id, default_branch_id }`.
**Expected:** 201; one `party` row + one `customer` row; `CustomerCreated.v1`.

### TC-PARTY-005 — Add customer role to existing party [P1]
**Steps:** Party A exists (already a supplier). POST customer with `partyId = A.id`.
**Expected:** 201; new `customer` row; party shared. Confirms shared-base pattern.

### TC-PARTY-006 — Edit customer [P1]
**Stories:** US-SALES-002
**Steps:** PATCH /api/v1/customers/{partyId} `{ credit_limit_amount: 10000000 }`.
**Expected:** 200; `CustomerUpdated.v1` with `changedFields: ["creditLimitAmount"]`.

### TC-PARTY-007 — Deactivate customer with open invoices blocked [P1]
**Steps:** Customer has unpaid invoice. POST deactivate.
**Expected:** 422 `CUSTOMER_HAS_OPEN_DEBT`.

### TC-PARTY-008 — Walk-in customer per branch (auto-created) [P1]
**Steps:** After branch create, list customers with `is_walk_in = true` for that branch.
**Expected:** Exactly one synthetic customer per branch; code `WALK-IN-BR-<code>-01`.

## Supplier

### TC-PARTY-009 — Create supplier [P1]
**Stories:** US-PROC-001
**Steps:** POST /api/v1/suppliers with party data + supplier-specific fields.
**Expected:** 201; `SupplierCreated.v1`.

### TC-PARTY-010 — Match by TIN at supplier create [P2]
**Steps:** Party exists with TIN `1234`. POST supplier with TIN `1234`.
**Expected:** UI offers "add supplier role to existing party"; if confirmed, returns existing partyId with new supplier role.

## Employee

### TC-PARTY-011 — Create employee linked to user [P1]
**Stories:** US-HR-001, US-HR-002
**Steps:** POST /api/v1/employees with party + employee fields, optionally `app_user_id`.
**Expected:** 201; `EmployeeCreated.v1`. UNIQUE (company_id, employee_code).

### TC-PARTY-012 — Terminate employee [P2]
**Stories:** US-HR-005
**Steps:** POST /api/v1/employees/{partyId}/terminate `{ terminationDate, reason }`.
**Expected:** `party.status = INACTIVE`; `employee.termination_date` set; if linked to `app_user`, user deactivated and refresh tokens revoked; `EmployeeTerminated.v1`.

### TC-PARTY-013 — Set staff_price_list_id [P1]
**Stories:** US-POS-023
**Steps:** PATCH employee with `staff_price_list_id`.
**Expected:** 200. POS uses this on badge scan.

## Sales agent

### TC-PARTY-014 — Create sales agent [P1]
**Steps:** POST /api/v1/sales-agents with agent_code, route, commission_rate.
**Expected:** 201; UNIQUE (company_id, agent_code).

## Biometric

### TC-PARTY-015 — Enrol biometric [P2]
**Stories:** US-IAM-011
**Steps:** Supervisor authenticates. POST /api/v1/biometrics with encrypted template.
**Expected:** Template stored as `template_ciphertext`; raw template never on disk; one active enrolment per `(app_user_id, method)`.

### TC-PARTY-016 — Re-enrol revokes previous template [P2]
**Steps:** Active fingerprint enrolment exists. Enrol again.
**Expected:** Previous template `revoked_at = now`; new one ACTIVE.

### TC-PARTY-017 — Revoke biometric [P2]
**Steps:** POST /api/v1/biometrics/{id}/revoke.
**Expected:** `revoked_at = now`; cannot be used for login.

## PII

### TC-PARTY-018 — PII fields scrubbed from audit log [P1]
**Steps:** Create customer with phone + email. Inspect `audit_log.after_json`.
**Expected:** Phone / email values redacted (e.g., `"****"`).

### TC-PARTY-019 — PII export for SAR [P1]
**Stories:** US-PLAT-007
**Steps:** GET /api/v1/parties/{id}/pii-export.
**Expected:** Bundles party + addresses + contacts + linked customer/supplier/employee rows; signed PDF or JSON.

### TC-PARTY-020 — PII anonymisation [P2]
**Stories:** US-PLAT-008
**Steps:** POST anonymise on a party with closed history.
**Expected:** Name → "(anonymised)"; phone / email nulled; FK references retained for audit / accounting.

## Multi-tenancy

### TC-PARTY-021 — Cross-company party access blocked [P1]
**Steps:** User on company 1 reads party belonging to company 2.
**Expected:** 404.

### TC-PARTY-022 — Same legal entity across companies = two party rows [P2]
**Steps:** Same TIN in two companies under one organisation.
**Expected:** Two `party` rows (one per company); no cross-company lookup.

## Edge

### TC-PARTY-023 — Idempotency on offline party create [P2]
**Steps:** WMS creates a customer offline with `client_op_id`. Sync replays.
**Expected:** Second call returns existing partyId; no duplicate.

### TC-PARTY-024 — Search by phone (partial match) [P2]
**Steps:** GET /api/v1/parties?q=07000.
**Expected:** Meilisearch returns matches. PII redacted in response if requesting user lacks permission.
