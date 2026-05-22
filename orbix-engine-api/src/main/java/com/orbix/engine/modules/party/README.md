# Party module

## 1. Purpose

The `party` module is the **shared identity base** for every legal-or-natural person the ERP transacts with: customers, suppliers, employees, and field sales agents. It implements a discriminator-style aggregate where a single `party` row carries the common fields (name, code, tax IDs, addresses, contacts, status) and one-or-more role tables (`customer`, `supplier`, `employee`, `sales_agent`) hold role-specific extension columns and reference the party by its primary key. **A single legal entity can hold multiple roles simultaneously** — the same `party_id` may appear in both `customer` and `supplier`, which lets the system surface relationships ("this supplier is also a customer — net them off?") and eliminates the legacy ERP's `Customer.name` / `Supplier.name` / `Employee.name` duplication.

The module is consumed by virtually every transactional module in the system: `sales` and `pos` resolve customers, `procurement` resolves suppliers, `wms` resolves sales agents and route customers, `hr` and `platform/security` resolve employees, and `debt` posts ageing against parties regardless of role. Because the module owns the canonical person/organisation record, it is one of the few modules whose entities are *referenced* by foreign key from many other modules rather than the other way around.

Within the modular monolith (ARCHITECTURE.md §2.1), `party` sits alongside `catalog` as a foundational master-data module — created before any transactional module can post — and follows the hexagonal layout (§2.2) because its aggregates carry real invariants (role membership, default-flag uniqueness, PII handling) that warrant a domain layer.

## 2. Scope

**In scope.**
- The `party` aggregate root and its child collections `party_address` and `party_contact`.
- The four role projections: `customer`, `supplier`, `employee`, `sales_agent`.
- The `biometric_enrolment` table (fingerprint / face templates tied to an `app_user`, which in turn is typically linked to an `employee`).
- CRUD and lifecycle (activate, deactivate, archive) for all of the above.
- PII tagging and encrypted-at-rest handling for biometric templates.
- Emission of party lifecycle events to the outbox.

**Out of scope.**
- `app_user`, roles, permissions, supervisor PIN, JWT issuance — owned by `platform/security`.
- `company`, `branch`, `organisation` — owned by `platform/company`. Party references these by ID; it does not manage them.
- Shifts, payroll, leave — owned by `hr` (`shift` table). `hr` reads `employee` from this module.
- Credit-limit *enforcement* and debt ageing — owned by `debt`. Party owns the `customer.credit_limit_amount` field only.
- Price lists — owned by `catalog`. Party owns the `customer.price_list_id` FK only.
- Commission *calculation* and agent settlement — owned by `wms`. Party owns the `sales_agent.commission_rate` field only.
- Customer-statement rendering, invoice rendering — owned by `sales` and `reporting`.

## 3. Domain model

Attribute lists, types, and nullability are defined in DATA-MODEL.md §2.x; this table is the index.

| Table | Purpose | Key relationships | Reference |
|---|---|---|---|
| `party` | Aggregate root; common fields (code, name, legal_name, category, TIN, VRN, phone, email, addresses, country_code, status) for every person/organisation. | `company_id` → `company.id`. `code` UNIQUE per company. | §2.1 |
| `party_address` | Multiple labelled addresses (`DELIVERY`, `BILLING`, `OFFICE`) per party, optionally GPS-tagged by WMS field visits. | `party_id` → `party.id`. One `is_default` per label per party. | §2.2 |
| `party_contact` | Named contact people at a business party ("Joseph in accounts"). | `party_id` → `party.id`. One `is_primary` per party. | §2.3 |
| `customer` | Customer-role extension: credit limit, credit terms, price list override, default sales agent, default branch, walk-in flag, tax-exempt flag. | `party_id` PK+FK → `party.id`. `default_sales_agent_id` → `sales_agent.party_id`. `price_list_id` → catalog. | §2.4 |
| `supplier` | Supplier-role extension: payment terms, default currency, bank details, lead-time days. | `party_id` PK+FK → `party.id`. | §2.5 |
| `employee` | HR-tracked person: employee code, job title, home branch, hire/termination dates, optional link to `app_user`. | `party_id` PK+FK → `party.id`. `app_user_id` → security. `branch_id` → company. | §2.6 |
| `sales_agent` | Field-sales person: agent code, route, commission rate, branch the van loads from. May or may not be an employee. | `party_id` PK+FK → `party.id`. `app_user_id` → security. | §2.7 |
| `biometric_enrolment` | Encrypted fingerprint/face template for POS quick login. Raw images are never stored. | `app_user_id` → security. `enrolled_by` → security. | §2.8 |

## 4. Key business flows

- **Create customer (US-SALES-001).** Salesperson submits name, category, optional TIN, phone, addresses, credit limit, price list, default agent. Service inserts `party` (allocating `code` from the `CUSTOMER` number sequence) and `customer` in one transaction, emits `PartyCreated.v1` and `CustomerCreated.v1`. The synthetic walk-in customer (`is_walk_in = true`) is created per branch by the first-run wizard.
- **Create supplier (US-PROC-001).** Merchandiser submits name, category, optional TIN, VRN, bank details, payment terms, lead time. Service inserts `party` and `supplier`. If a `party` with matching TIN already exists (already a customer), the UI offers to add the supplier role to the existing party rather than creating a duplicate. Emits `PartyCreated.v1` (if new) and `SupplierCreated.v1`.
- **Create employee (US-HR-001, US-HR-002).** Admin submits name, employee code, home branch, hire date, optional `app_user_id`. Inserts `party` and `employee`. If linked to an `app_user`, that user becomes attributable for audited actions.
- **Enrol biometric (US-IAM-011, US-HR-004).** Supervisor authenticates first; service captures the raw template from the client, encrypts with AES-GCM using the per-deployment key, persists only `template_ciphertext`. The raw template never touches disk and is zeroed in memory after encryption. Emits `BiometricEnrolled.v1`.
- **Deactivate party (US-HR-005 for employees; generic for customer/supplier).** Sets `party.status = 'INACTIVE'`. For employees, also sets `employee.termination_date`, deactivates the linked `app_user`, and revokes refresh tokens (delegated to `platform/security`). Emits `PartyDeactivated.v1`. Transactional history is preserved untouched.

## 5. Module interactions

**Depends on.**
- `platform/company` — `company_id`, `branch_id` FK targets.
- `platform/security` — `app_user_id` FK target; supervisor authorisation flow for biometric enrolment.
- `platform/sequence` — allocates `party.code` from `CUSTOMER`, `SUPPLIER`, `EMPLOYEE`, `AGENT` sequences per company.
- `platform/events` — transactional outbox (`domain_event` table, ARCHITECTURE.md §2.10).
- `platform/audit` — every write emits an `AuditEvent`.

**Publishes events** (versioned, payload via outbox; JSON serialised as `TEXT`).
- `PartyCreated.v1` — `{ partyId, companyId, code, name, category, status, occurredAt }`.
- `PartyUpdated.v1` — `{ partyId, companyId, changedFields[], occurredAt }`.
- `PartyDeactivated.v1` — `{ partyId, companyId, reason, occurredAt }`.
- `CustomerCreated.v1` — `{ partyId, companyId, creditLimitAmount, creditTermsDays, priceListId, defaultBranchId, isWalkIn, occurredAt }`.
- `CustomerUpdated.v1` — `{ partyId, changedFields[], occurredAt }`.
- `SupplierCreated.v1` — `{ partyId, companyId, paymentTermsDays, defaultCurrencyCode, leadTimeDays, occurredAt }`.
- `EmployeeCreated.v1` — `{ partyId, companyId, employeeCode, branchId, appUserId, hireDate, occurredAt }`.
- `EmployeeTerminated.v1` — `{ partyId, employeeCode, terminationDate, appUserId, occurredAt }`.
- `SalesAgentCreated.v1` — `{ partyId, companyId, agentCode, routeId, branchId, commissionRate, occurredAt }`.
- `BiometricEnrolled.v1` — `{ enrolmentId, appUserId, method, enrolledBy, occurredAt }`. Payload never carries the template.
- `BiometricRevoked.v1` — `{ enrolmentId, appUserId, revokedAt }`.

**Consumes events.**
- `CompanyCreated.v1` (from `platform/company`) — first-run wizard hook to provision the per-branch walk-in customer and the `CUSTOMER`/`SUPPLIER`/`EMPLOYEE`/`AGENT` number sequences.
- `AppUserDeactivated.v1` (from `platform/security`) — defensive: if a linked `app_user` is deactivated outside the HR flow, mark the corresponding `employee.termination_date` if unset.

## 6. API surface

Base path `/api/v1`. All endpoints honour `Authorization` (JWT) and `X-Branch-Id` per ARCHITECTURE.md §2.5–§2.6.

| Path | Verbs | Notes |
|---|---|---|
| `/api/v1/parties` | GET (list, paged), POST | Generic party CRUD. POST creates a bare party with no role. |
| `/api/v1/parties/{id}` | GET, PATCH, DELETE | DELETE is logical (`status = 'ARCHIVED'`). |
| `/api/v1/parties/{id}/addresses` | GET, POST | Add `party_address`. |
| `/api/v1/parties/{id}/addresses/{addrId}` | PATCH, DELETE | |
| `/api/v1/parties/{id}/contacts` | GET, POST | Add `party_contact`. |
| `/api/v1/parties/{id}/contacts/{contactId}` | PATCH, DELETE | |
| `/api/v1/customers` | GET, POST | POST may create the underlying `party` in the same call, or attach the customer role to an existing `partyId`. |
| `/api/v1/customers/{partyId}` | GET, PATCH | |
| `/api/v1/customers/{partyId}/deactivate` | POST | |
| `/api/v1/suppliers` | GET, POST | Same dual mode as `/customers`. |
| `/api/v1/suppliers/{partyId}` | GET, PATCH | |
| `/api/v1/employees` | GET, POST | |
| `/api/v1/employees/{partyId}` | GET, PATCH | |
| `/api/v1/employees/{partyId}/terminate` | POST | Body: `{ terminationDate, reason }`. |
| `/api/v1/sales-agents` | GET, POST | |
| `/api/v1/sales-agents/{partyId}` | GET, PATCH | |
| `/api/v1/biometrics` | POST | Enrol; multipart with encrypted template. Requires supervisor auth token. |
| `/api/v1/biometrics/{id}/revoke` | POST | Sets `revoked_at`. |

Lists support `?q=` (Meilisearch-backed for `name`, `code`, `tin`), `?role=customer|supplier|employee|agent`, `?status=`, plus standard `page`, `size`, `sort`.

## 7. Persistence

Flyway scripts live in `db/migration/common/` (ARCHITECTURE.md §2.3). One migration per table to keep history reviewable.

| Migration | Tables | Notes |
|---|---|---|
| `V20__party_party.sql` | `party` | UNIQUE `(company_id, code)`. Index `(company_id, status, name)`. |
| `V21__party_party_address.sql` | `party_address` | Index `(party_id, label)`. |
| `V22__party_party_contact.sql` | `party_contact` | Index `(party_id, is_primary)`. |
| `V23__party_customer.sql` | `customer` | PK = `party_id`. Index `(default_sales_agent_id)`. |
| `V24__party_supplier.sql` | `supplier` | PK = `party_id`. |
| `V25__party_employee.sql` | `employee` | PK = `party_id`. UNIQUE `(company_id, employee_code)` enforced via composite uniqueness on a denormalised `company_id` carried from `party` (or via trigger-free app-layer check, since composite uniqueness across tables is non-portable). |
| `V26__party_sales_agent.sql` | `sales_agent` | PK = `party_id`. UNIQUE `(company_id, agent_code)` — same approach as employee. |
| `V27__party_biometric_enrolment.sql` | `biometric_enrolment` | Index `(app_user_id, revoked_at)` for active-template lookup. |

**MySQL/Postgres notes.** All columns use the DB-agnostic types mandated by ARCHITECTURE.md §2.3 (`VARCHAR`, `TEXT`, `DECIMAL(18,4)`, `BIGINT`, `BOOLEAN`, `TIMESTAMP`). `template_ciphertext` is `TEXT` (Base64) rather than `BLOB`/`BYTEA` to keep dialect parity. Booleans map cleanly via Hibernate to `TINYINT(1)` on MySQL and `BOOLEAN` on Postgres. No `JSONB`, no `FULLTEXT` — name/TIN/code search is delegated to Meilisearch (§2.11). IDs are `BIGINT` from the Hibernate sequence-table generator, never `IDENTITY`.

## 8. User stories

**P1.**
- US-PROC-001 — Create a supplier
- US-SALES-001 — Create a customer
- US-SALES-002 — Edit a customer
- US-HR-001 — Create an employee
- US-HR-002 — Link an employee to an app user
- US-COMP-001 — Set up the organisation and first company (provisions walk-in customer)
- US-IAM-009 — Assign a role to a user, scoped per company / branch (consumes `employee.app_user_id`)
- US-WMS-008 / US-WMS-010 — Sales agent operations (consumes `sales_agent`)

**P2.**
- US-IAM-011 — Enrol a biometric
- US-IAM-012 — Log in with a fingerprint (read-side of `biometric_enrolment`)
- US-HR-004 — Enrol a biometric for an employee (alias of US-IAM-011)
- US-HR-005 — Terminate an employee

**P3.**
- Multi-role unification UX (offer "add supplier role to existing customer" when TIN matches) — derives from DATA-MODEL.md §16.1.

## 9. Open questions

From PRD §13 and DATA-MODEL.md §16, only items that touch this module:

1. **PRD §13 Q5 / DATA-MODEL §16.1 — Should `customer` and `supplier` allow the same `party_id`?** Architecturally yes, provisionally answered yes in Architecture §4.2, but UX behaviour (auto-suggest vs force-merge) and reporting impact ("net them off") need owner sign-off before MVP cut. This is the defining question for the whole shared-base pattern.
2. **DATA-MODEL §16.3 — Walk-in customer at POS.** Recommendation: one synthetic `customer` per branch (`is_walk_in = true`, code `WALK-IN-BR-<code>-NN`) so every POS sale links to a real party. Cleaner than NULL FKs but needs confirmation.
3. **DATA-MODEL §16.8 — Sales agent commission storage.** Currently a single `commission_rate` on `sales_agent`. If commissions are tier-based or item-based, this module will need to own a new `commission_rule` table — confirm scope.

## 10. Implementation notes

**Hexagonal layout** (ARCHITECTURE.md §2.2), since the module has real invariants:
```
com.orbix.engine.modules.party
├── api/        # REST controllers, request/response DTOs, bean-validation annotations
├── app/        # PartyService, CustomerService, SupplierService, EmployeeService,
│               # SalesAgentService, BiometricService — @Transactional boundary
├── domain/     # Party, Customer, Supplier, Employee, SalesAgent, BiometricEnrolment
│               # aggregates; domain events; PartyStatus enum; PartyCategory enum
└── infra/      # Spring Data JPA repositories; Meilisearch indexer adapter;
                # AES-GCM template-encryption adapter
```

**Invariants.**
- A `party.status = 'ACTIVE'` invariant must hold whenever any role row exists referencing it. Deactivating a party fans out to deactivating the role rows in the same transaction.
- A `party` may hold zero, one, or many roles. The role row's existence — not a discriminator column — is the source of truth for whether a party plays that role. This is intentional: it lets the same `party_id` be both a customer and a supplier without a coupling column.
- `customer.is_walk_in = true` is permitted only on the synthetic walk-in party per branch; enforced in `CustomerService.create`.
- `party_address` allows at most one `is_default = true` per `(party_id, label)`. Enforced in the service (composite partial uniques are not portable).
- `party_contact` allows at most one `is_primary = true` per `party_id`. Same enforcement strategy.
- `biometric_enrolment` allows at most one *active* (`revoked_at IS NULL`) row per `(app_user_id, method)`. Enforced in `BiometricService.enrol` (re-enrolment revokes the previous template in the same transaction).

**Multi-tenant scoping** (ARCHITECTURE.md §2.6).
- `party` is **company-scoped**: every read filters by `company_id` from `RequestContext` via the base repository predicate.
- `employee` and `sales_agent` are **branch-aware**: they carry `branch_id` (home / van-loading branch), but reads are not branch-filtered by default — a salesperson at branch A may still create a quotation for a customer whose default branch is B. Branch scoping applies only to transactions, not to master data lookups.
- Cross-company party access is forbidden. The same legal entity transacting with two companies in the organisation gets two `party` rows (one per company) — confirmed under DATA-MODEL §16.1 follow-up.

**Idempotency.** Create endpoints accept an optional `clientOpId` (UUID v7) header per ARCHITECTURE.md §2.9. Re-submitting the same `clientOpId` returns the previously created party rather than a duplicate. Used by POS and WMS when a customer is created offline and synced.

**PII tagging.** `party.name`, `party.legal_name`, `party.phone`, `party.email`, `party.physical_address`, `party.postal_address`, `party_contact.name`, `party_contact.phone`, `party_contact.email` are tagged with the `@PII` marker (DATA-MODEL.md §2.1 etc). The audit aspect (ARCHITECTURE.md §2.7) redacts `@PII` fields from `audit_log.payload_json` and the outbox does the same for event payloads sent to external subscribers. `biometric_enrolment.template_ciphertext` is never logged or emitted — it is read only by the biometric matcher.

**Concurrency.** Every aggregate (`party`, `customer`, `supplier`, `employee`, `sales_agent`) carries `@Version` for optimistic locking. A conflicting edit returns RFC 7807 Problem Details with `type=/errors/conflict` and HTTP 409, per ARCHITECTURE.md §2.3 and §2.8.
