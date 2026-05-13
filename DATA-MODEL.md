# Orbix ERP — Data Model

| Field | Value |
|---|---|
| Document | Data Model v0.1 (draft) |
| Author | Godfrey (with Claude) |
| Date | 2026-05-13 |
| Status | Draft — pending review |
| Companion | [PRD.md](PRD.md), [ARCHITECTURE.md](ARCHITECTURE.md), [USER-STORIES.md](USER-STORIES.md) |

This document specifies the entities of the new system: rationale for each, attributes with datatypes, realistic examples, and relationships. It expands [ARCHITECTURE.md §4](ARCHITECTURE.md) into implementation-ready detail.

---

## Conventions

### Datatypes (DB-agnostic)

| Type | Meaning | MySQL 8 | PostgreSQL 15+ |
|---|---|---|---|
| `BIGINT` | 64-bit signed integer; all primary keys | `BIGINT` | `BIGINT` |
| `INT` | 32-bit integer; counters, version columns | `INT` | `INTEGER` |
| `VARCHAR(n)` | Variable-length text up to `n` characters | `VARCHAR(n)` | `VARCHAR(n)` |
| `TEXT` | Unbounded text (notes, JSON payloads, addresses) | `TEXT` | `TEXT` |
| `DECIMAL(18,4)` | Money and stock quantities — 4 decimal places, ~14-digit whole part | `DECIMAL(18,4)` | `NUMERIC(18,4)` |
| `DECIMAL(10,4)` | Rates and factors (VAT %, UoM factor, FX rate) | `DECIMAL(10,4)` | `NUMERIC(10,4)` |
| `DATE` | Calendar date, no time | `DATE` | `DATE` |
| `TIMESTAMP` | UTC timestamp, microsecond precision | `TIMESTAMP(6)` | `TIMESTAMP(6)` |
| `BOOLEAN` | True/false | `BOOLEAN` (TINYINT(1)) | `BOOLEAN` |
| `UUID` | Stored as `BINARY(16)` (MySQL) or native `UUID` (Postgres). Used for `client_op_id`, document tokens. | `BINARY(16)` | `UUID` |
| `ENUM` | Stored as `VARCHAR(32)`; allowed values enforced by the application. No native DB enums (DB-agnostic). | `VARCHAR(32)` | `VARCHAR(32)` |

### Common columns (every table unless noted)

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | PK; generated from `hibernate_sequence` table. |
| `created_at` | `TIMESTAMP` | NO | Set by app on insert, UTC. |
| `created_by` | `BIGINT` | NO | FK → `app_user.id`. `0` for system writes. |
| `updated_at` | `TIMESTAMP` | NO | Set by app on each update. |
| `updated_by` | `BIGINT` | NO | FK → `app_user.id`. |
| `version` | `INT` | NO | Hibernate `@Version` — optimistic lock. |

### Common columns on transactional / branch-scoped tables

| Column | Type | Null | Notes |
|---|---|---|---|
| `company_id` | `BIGINT` | NO | FK → `company.id`. |
| `branch_id` | `BIGINT` | NO | FK → `branch.id`. |

### Common columns on append-only ledgers

Postings tables (`stock_move`, `debt_entry`, `cash_entry`, `audit_log`, `domain_event`) do **not** carry `updated_at`/`updated_by`/`version` — they are immutable. They keep `created_at` (rename `at` in code) and `created_by` (rename `actor_id`).

### Status enums

A `status` column appears on most aggregates with values drawn from:

| Aggregate type | Allowed statuses |
|---|---|
| Master data (Item, Customer, Supplier, AppUser, Role, …) | `ACTIVE`, `INACTIVE`, `ARCHIVED` |
| Transactions (SalesInvoice, LPO, GRN, SalesReceipt, …) | `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `POSTED`, `CANCELLED`, `VOIDED` |
| Sessions (TillSession, ProductionBatch) | `OPEN`, `IN_PROGRESS`, `CLOSED`, `RECONCILED` |
| Sync ops, events | `PENDING`, `DISPATCHED`, `FAILED`, `DEAD_LETTERED` |

### Naming

- Table names: `snake_case`, singular (`item`, `sales_invoice`). Junction tables named after both sides (`role_privilege`).
- Column names: `snake_case`. Foreign keys: `<other_table>_id`.
- Booleans named for the true case: `is_active`, `is_default`, `has_been_synced`.
- Money column names end in `_amount`; quantity columns end in `_qty`.

---

# 1. Platform

Cross-cutting tables — identity, hierarchy, audit, infrastructure plumbing.

## 1.1 `organisation`

**Rationale.** The top of the multi-company tree. A single deployment hosts exactly one organisation (because deployments are single-tenant — see PRD §2.2 NG3). The row exists so that consolidated group-level reports and shared master data have a parent.

**Attributes.**

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | PK. |
| `name` | `VARCHAR(200)` | NO | Display name of the group. |
| `legal_name` | `VARCHAR(200)` | YES | If different from display name. |
| `currency_code` | `VARCHAR(3)` | NO | ISO 4217, e.g. `TZS`. Group-wide base currency. |
| `country_code` | `VARCHAR(2)` | NO | ISO 3166-1 alpha-2. |
| `status` | `VARCHAR(32)` | NO | Master status enum. |

**Example.**
```json
{ "id": 1, "name": "Orbit Beverages Group", "currency_code": "TZS", "country_code": "TZ", "status": "ACTIVE" }
```

**Relationships.** Parent of `company` (1..N).

## 1.2 `company`

**Rationale.** A legal entity within the group. Holds its own customers, suppliers, items, tax registration, and bank accounts. Consolidated reporting rolls up many companies into the organisation; per-company reporting respects the boundary.

**Attributes.**

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | PK. |
| `organisation_id` | `BIGINT` | NO | FK → `organisation.id`. |
| `code` | `VARCHAR(20)` | NO | Short code used in document numbers, e.g. `OBL`. Unique within the org. |
| `name` | `VARCHAR(200)` | NO | Display name. |
| `legal_name` | `VARCHAR(200)` | YES | |
| `tin` | `VARCHAR(40)` | YES | Taxpayer ID. |
| `vrn` | `VARCHAR(40)` | YES | VAT registration. |
| `physical_address` | `TEXT` | YES | |
| `postal_address` | `TEXT` | YES | |
| `phone` | `VARCHAR(40)` | YES | |
| `email` | `VARCHAR(120)` | YES | |
| `website` | `VARCHAR(200)` | YES | |
| `currency_code` | `VARCHAR(3)` | NO | Inherits org default but overridable. |
| `country_code` | `VARCHAR(2)` | NO | |
| `time_zone` | `VARCHAR(64)` | NO | IANA, e.g. `Africa/Dar_es_Salaam`. |
| `logo_object_key` | `VARCHAR(200)` | YES | Pointer into object store. |
| `default_invoice_note` | `TEXT` | YES | Footer text on invoices. |
| `default_quotation_note` | `TEXT` | YES | |
| `status` | `VARCHAR(32)` | NO | Master status enum. |

**Example.**
```json
{
  "id": 7, "organisation_id": 1, "code": "OBL", "name": "Orbit Beverages Ltd",
  "tin": "123-456-789", "vrn": "40-005678-A", "currency_code": "TZS",
  "country_code": "TZ", "time_zone": "Africa/Dar_es_Salaam", "status": "ACTIVE"
}
```

**Relationships.** Belongs to `organisation`. Parent of `branch`, scoping parent of all company-bound master data.

## 1.3 `branch`

**Rationale.** A physical location: shop, depot, plant, warehouse. The smallest unit at which stock balances exist and at which a business day is opened and closed. Every transaction belongs to a branch so reporting and segregation are trivial.

**Attributes.**

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | PK. |
| `company_id` | `BIGINT` | NO | FK → `company.id`. |
| `code` | `VARCHAR(20)` | NO | Short code, e.g. `BR-DSM-01`. Unique within company. |
| `name` | `VARCHAR(120)` | NO | |
| `type` | `VARCHAR(32)` | NO | `SHOP`, `WAREHOUSE`, `DEPOT`, `PLANT`, `HQ`. |
| `physical_address` | `TEXT` | YES | |
| `phone` | `VARCHAR(40)` | YES | |
| `time_zone` | `VARCHAR(64)` | NO | Inherits company, overridable. |
| `is_default` | `BOOLEAN` | NO | At most one default per company. |
| `status` | `VARCHAR(32)` | NO | |

**Example.**
```json
{ "id": 12, "company_id": 7, "code": "BR-DSM-01", "name": "Kariakoo Shop",
  "type": "SHOP", "time_zone": "Africa/Dar_es_Salaam", "is_default": true, "status": "ACTIVE" }
```

**Relationships.** Belongs to `company`. Referenced by every transactional table and by `till`, `business_day`, `item_branch_balance`.

## 1.4 `app_user`

**Rationale.** A person who logs into any client surface (Web ERP, POS, WMS). Distinct from `employee` — a user is an identity; an employee is HR-tracked. A user may or may not be an employee, and vice versa.

**Attributes.**

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | PK. |
| `username` | `VARCHAR(80)` | NO | UNIQUE. Lowercase ASCII. |
| `password_hash` | `VARCHAR(120)` | NO | Bcrypt, cost ≥ 12. |
| `display_name` | `VARCHAR(120)` | NO | Shown in UI. |
| `email` | `VARCHAR(120)` | YES | For password reset; verified flag separate. |
| `email_verified_at` | `TIMESTAMP` | YES | |
| `phone` | `VARCHAR(40)` | YES | |
| `default_company_id` | `BIGINT` | YES | First company the user lands in after login. |
| `default_branch_id` | `BIGINT` | YES | First branch after login. |
| `failed_login_count` | `INT` | NO | DEFAULT 0. |
| `locked_until` | `TIMESTAMP` | YES | Brute-force lockout. |
| `last_login_at` | `TIMESTAMP` | YES | |
| `status` | `VARCHAR(32)` | NO | Master status enum. |

**Example.**
```json
{ "id": 4501, "username": "g.kahindo", "display_name": "Godfrey Kahindo",
  "email": "g.kahindo@orbit.tz", "default_company_id": 7, "default_branch_id": 12, "status": "ACTIVE" }
```

**Relationships.** Many `user_role` rows. Author of every `audit_log`, `created_by`, `updated_by` reference.

## 1.5 `role`

**Rationale.** A named collection of privileges. Roles are reusable across users and companies (`Cashier`, `Storekeeper`, `Branch Manager`).

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `code` | `VARCHAR(40)` | NO | UNIQUE. `CASHIER`, `STOREKEEPER`. |
| `name` | `VARCHAR(120)` | NO | Display. |
| `description` | `TEXT` | YES | |
| `is_system` | `BOOLEAN` | NO | System roles cannot be deleted (audit anchor). |
| `status` | `VARCHAR(32)` | NO | |

**Example.** `{ "id": 3, "code": "CASHIER", "name": "Till Cashier", "is_system": true, "status": "ACTIVE" }`

## 1.6 `privilege`

**Rationale.** The atomic permission unit. Coarser than per-endpoint, finer than per-module. Backend `@PreAuthorize` checks reference privilege codes, not roles.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `code` | `VARCHAR(80)` | NO | UNIQUE, dot-separated: `SALES_INVOICE.POST`, `STOCK.ADJUST`. |
| `description` | `TEXT` | NO | What the privilege grants. |
| `module` | `VARCHAR(40)` | NO | For UI grouping: `sales`, `stock`, `procurement`. |

**Example.** `{ "id": 102, "code": "SALES_INVOICE.POST", "module": "sales", "description": "Post a sales invoice, decrementing stock and creating debt." }`

## 1.7 `role_privilege`

**Rationale.** Junction. A role grants many privileges; a privilege belongs to many roles.

| Column | Type | Null | Notes |
|---|---|---|---|
| `role_id` | `BIGINT` | NO | FK; part of composite PK. |
| `privilege_id` | `BIGINT` | NO | FK; part of composite PK. |

## 1.8 `user_role`

**Rationale.** A user holds a role *scoped to a company*. The same person can be `CASHIER` at company A, `BRANCH_MANAGER` at company B. Optionally further scoped to a single branch.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `user_id` | `BIGINT` | NO | FK. |
| `role_id` | `BIGINT` | NO | FK. |
| `company_id` | `BIGINT` | NO | FK; the scope. |
| `branch_id` | `BIGINT` | YES | NULL = applies to all branches in the company. |
| `granted_at` | `TIMESTAMP` | NO | |
| `granted_by` | `BIGINT` | NO | FK → `app_user.id`. |
| `revoked_at` | `TIMESTAMP` | YES | NULL = active. |

**Example.**
```json
{ "id": 9001, "user_id": 4501, "role_id": 3, "company_id": 7, "branch_id": 12,
  "granted_at": "2026-04-01T08:00:00Z", "granted_by": 1, "revoked_at": null }
```

## 1.9 `audit_log`

**Rationale.** Append-only record of who did what to which entity. Written by an aspect on every transactional service method so it cannot be forgotten. Hash-chained so tampering is detectable.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | PK. |
| `at` | `TIMESTAMP` | NO | Indexed. |
| `actor_id` | `BIGINT` | NO | FK → `app_user.id`. `0` for system. |
| `action` | `VARCHAR(40)` | NO | `CREATE`, `UPDATE`, `DELETE`, `POST`, `VOID`, `APPROVE`, `LOGIN`, `FLAG_CHANGE`. |
| `entity_type` | `VARCHAR(80)` | NO | `SalesInvoice`, `Item`, `FeatureFlag`. |
| `entity_id` | `VARCHAR(80)` | NO | Stringified PK. |
| `company_id` | `BIGINT` | YES | |
| `branch_id` | `BIGINT` | YES | |
| `before_json` | `TEXT` | YES | Pre-state, JSON, PII-scrubbed. |
| `after_json` | `TEXT` | YES | Post-state. |
| `meta_json` | `TEXT` | YES | Extras: client IP, request ID, supervisor authoriser. |
| `prev_hash` | `VARCHAR(64)` | NO | SHA-256 of previous row in the chain. |
| `row_hash` | `VARCHAR(64)` | NO | SHA-256 of this row's canonical content + prev_hash. |

**Example.**
```json
{ "id": 558203, "at": "2026-05-13T08:42:11Z", "actor_id": 4501,
  "action": "POST", "entity_type": "SalesInvoice", "entity_id": "31127",
  "company_id": 7, "branch_id": 12,
  "after_json": "{...summary...}", "prev_hash": "a8f...", "row_hash": "9c3..." }
```

**Indexes.** `(entity_type, entity_id, at)`, `(actor_id, at)`, `(at)` (for chain verification).

## 1.10 `number_sequence`

**Rationale.** Document numbers per branch per document type are issued by the server inside a transaction so they are gap-free and unique. Avoids race conditions and lets each branch print human-readable numbers (`LPO-DSM-000123`).

| Column | Type | Null | Notes |
|---|---|---|---|
| `company_id` | `BIGINT` | NO | Composite PK. |
| `branch_id` | `BIGINT` | NO | Composite PK. |
| `doc_type` | `VARCHAR(40)` | NO | `LPO`, `GRN`, `SI`, `SR`. Composite PK. |
| `prefix` | `VARCHAR(20)` | NO | Printed prefix, e.g. `LPO-DSM-`. |
| `current_value` | `BIGINT` | NO | Next number to issue. |
| `pad_width` | `INT` | NO | Zero-pad to this width. |

**Example.**
```json
{ "company_id": 7, "branch_id": 12, "doc_type": "LPO",
  "prefix": "LPO-DSM-", "current_value": 124, "pad_width": 6 }
```
→ next issued document is `LPO-DSM-000124`.

## 1.11 `domain_event`

**Rationale.** The transactional outbox. Every business write that other modules / external subscribers might care about writes a row here in the same DB transaction. A poller dispatches rows asynchronously. See [ARCHITECTURE.md §2.10](ARCHITECTURE.md).

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `type` | `VARCHAR(120)` | NO | Versioned: `SalesInvoicePosted.v1`. |
| `aggregate_type` | `VARCHAR(80)` | NO | `SalesInvoice`. |
| `aggregate_id` | `VARCHAR(80)` | NO | Stringified PK. |
| `payload_json` | `TEXT` | NO | Event payload, schema pinned per `type`. |
| `occurred_at` | `TIMESTAMP` | NO | Business time. |
| `company_id` | `BIGINT` | YES | |
| `branch_id` | `BIGINT` | YES | |
| `actor_id` | `BIGINT` | YES | Who triggered it. |
| `status` | `VARCHAR(32)` | NO | `PENDING`, `DISPATCHED`, `FAILED`, `DEAD_LETTERED`. |
| `attempt_count` | `INT` | NO | DEFAULT 0. |
| `last_attempt_at` | `TIMESTAMP` | YES | |
| `last_error` | `TEXT` | YES | |
| `dispatched_at` | `TIMESTAMP` | YES | |

**Example.**
```json
{ "id": 9912, "type": "SalesInvoicePosted.v1", "aggregate_type": "SalesInvoice",
  "aggregate_id": "31127",
  "payload_json": "{\"invoiceId\":31127,\"customerId\":540,\"total\":120000.0}",
  "occurred_at": "2026-05-13T08:42:11Z", "company_id": 7, "branch_id": 12,
  "actor_id": 4501, "status": "PENDING", "attempt_count": 0 }
```

## 1.12 `event_subscription` & `event_delivery`

**Rationale.** External webhook subscribers and their delivery history. Each subscriber gets at-least-once delivery with backoff.

`event_subscription` columns: `id`, `name`, `url`, `secret_hash` (for HMAC), `event_types` (JSON array or CSV of types), `is_active`, plus common columns.

`event_delivery` columns: `id`, `event_id` (FK), `subscription_id` (FK), `attempt`, `status`, `http_status`, `response_body`, `delivered_at`.

## 1.13 `feature_flag`

**Rationale.** Definitions of all flags in the system. The code registers each flag at startup against this table; rows are insert-or-update.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `code` | `VARCHAR(80)` | NO | UNIQUE. `pos.loyalty.enabled`. |
| `type` | `VARCHAR(32)` | NO | `RELEASE`, `OPERATIONAL`, `PERMISSION`, `EXPERIMENT`. |
| `description` | `TEXT` | NO | What this flag controls. |
| `default_value` | `BOOLEAN` | NO | When no override applies. |
| `expires_at` | `DATE` | YES | Release flags must set this; CI warns when overdue. |
| `status` | `VARCHAR(32)` | NO | |

## 1.14 `feature_flag_override`

**Rationale.** Per-scope overrides for a flag. Resolution order: user > role > branch > company > global > default.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `flag_code` | `VARCHAR(80)` | NO | FK → `feature_flag.code`. |
| `scope` | `VARCHAR(20)` | NO | `GLOBAL`, `COMPANY`, `BRANCH`, `ROLE`, `USER`. |
| `scope_id` | `BIGINT` | YES | Target id; NULL for GLOBAL. |
| `enabled` | `BOOLEAN` | NO | |
| `note` | `TEXT` | YES | "Pilot till 2026-06-30" — context for the next admin. |

**Example.** `{ "flag_code": "pos.loyalty.enabled", "scope": "BRANCH", "scope_id": 12, "enabled": true, "note": "Pilot at Kariakoo until 2026-06-30" }`

## 1.15 `client_op`

**Rationale.** Idempotency record for offline-originated operations (POS sales, WMS sheet submissions). Lets the server safely accept the same op twice without double-posting.

| Column | Type | Null | Notes |
|---|---|---|---|
| `client_op_id` | `UUID` | NO | PK; UUID v7 generated by client. |
| `client_type` | `VARCHAR(20)` | NO | `POS`, `WMS`. |
| `client_install_id` | `VARCHAR(80)` | NO | Stable per-device installation. |
| `op_type` | `VARCHAR(80)` | NO | `POS_SALE_POSTED`, `SALES_SHEET_SUBMITTED`. |
| `server_resource_type` | `VARCHAR(80)` | YES | What got created server-side (e.g. `PosSale`). |
| `server_resource_id` | `BIGINT` | YES | The ID assigned. |
| `status` | `VARCHAR(32)` | NO | `RECEIVED`, `APPLIED`, `REJECTED`. |
| `received_at` | `TIMESTAMP` | NO | |
| `applied_at` | `TIMESTAMP` | YES | |
| `error_json` | `TEXT` | YES | Why it was rejected, if so. |

**Example.**
```json
{ "client_op_id": "01HX7Q2K3J4MZ8R9A0V1B6N5DC", "client_type": "POS",
  "client_install_id": "TILL-3-MAC-AA11BB22", "op_type": "POS_SALE_POSTED",
  "server_resource_type": "PosSale", "server_resource_id": 778231,
  "status": "APPLIED", "received_at": "2026-05-13T08:42:09Z", "applied_at": "2026-05-13T08:42:09Z" }
```

## 1.16 `refresh_token`

**Rationale.** Refresh tokens are rotated on use; the hash is stored server-side so they can be revoked individually. Logout deletes a row; "log me out everywhere" deletes by user_id.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `user_id` | `BIGINT` | NO | |
| `token_hash` | `VARCHAR(120)` | NO | UNIQUE. SHA-256 of the token value. |
| `issued_at` | `TIMESTAMP` | NO | |
| `expires_at` | `TIMESTAMP` | NO | |
| `client_install_id` | `VARCHAR(80)` | YES | For per-device revocation. |
| `revoked_at` | `TIMESTAMP` | YES | NULL = active. |

---

# 2. Party (shared base for Customer / Supplier / Employee / SalesAgent)

A single `party` table holds the common fields. Role-specific tables (`customer`, `supplier`, …) hold extension fields and reference the party. A party can hold multiple roles — the same legal entity can be both a supplier and a customer.

## 2.1 `party`

**Rationale.** Eliminates the legacy duplication of `Customer.name` + `Supplier.name` + `Employee.name`. Centralises addresses, contacts, tax IDs in one place, and lets the system surface relationships ("this supplier is also a customer — net them off?").

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `company_id` | `BIGINT` | NO | Parties are company-scoped by default. |
| `code` | `VARCHAR(40)` | NO | Human-friendly. UNIQUE per company. |
| `name` | `VARCHAR(200)` | NO | `@PII`. |
| `legal_name` | `VARCHAR(200)` | YES | If different. `@PII`. |
| `category` | `VARCHAR(32)` | NO | `INDIVIDUAL`, `BUSINESS`, `GOVERNMENT`, `NGO`. |
| `tin` | `VARCHAR(40)` | YES | Tax ID. |
| `vrn` | `VARCHAR(40)` | YES | VAT registration. |
| `phone` | `VARCHAR(40)` | YES | `@PII`. |
| `email` | `VARCHAR(120)` | YES | `@PII`. |
| `physical_address` | `TEXT` | YES | `@PII`. |
| `postal_address` | `TEXT` | YES | `@PII`. |
| `country_code` | `VARCHAR(2)` | YES | |
| `notes` | `TEXT` | YES | |
| `status` | `VARCHAR(32)` | NO | |

**Example.**
```json
{ "id": 540, "company_id": 7, "code": "C-000540", "name": "Mama Sara Wholesale",
  "category": "BUSINESS", "tin": "999-111-222", "phone": "+255700111222",
  "physical_address": "Plot 42, Kariakoo, Dar es Salaam", "status": "ACTIVE" }
```

## 2.2 `party_address`

**Rationale.** Many parties have multiple addresses (delivery, billing, alternate sites). Pulled out so they're queryable and editable independently.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `party_id` | `BIGINT` | NO | |
| `label` | `VARCHAR(40)` | NO | `DELIVERY`, `BILLING`, `OFFICE`. |
| `line1` | `VARCHAR(200)` | NO | |
| `line2` | `VARCHAR(200)` | YES | |
| `city` | `VARCHAR(80)` | YES | |
| `region` | `VARCHAR(80)` | YES | |
| `country_code` | `VARCHAR(2)` | YES | |
| `gps_lat` | `DECIMAL(10,7)` | YES | Field-agent visits set this. |
| `gps_lng` | `DECIMAL(10,7)` | YES | |
| `is_default` | `BOOLEAN` | NO | One default per label per party. |

## 2.3 `party_contact`

**Rationale.** Names and phones of people at a business party (e.g. "Joseph in accounts" at a supplier).

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `party_id` | `BIGINT` | NO | |
| `name` | `VARCHAR(120)` | NO | `@PII`. |
| `role_label` | `VARCHAR(80)` | YES | "Accounts", "Owner". |
| `phone` | `VARCHAR(40)` | YES | `@PII`. |
| `email` | `VARCHAR(120)` | YES | `@PII`. |
| `is_primary` | `BOOLEAN` | NO | |

## 2.4 `customer`

**Rationale.** Customer-specific terms: credit limit, price tier, default sales agent, default branch.

| Column | Type | Null | Notes |
|---|---|---|---|
| `party_id` | `BIGINT` | NO | PK + FK → `party.id`. |
| `credit_limit_amount` | `DECIMAL(18,4)` | NO | DEFAULT 0. |
| `credit_terms_days` | `INT` | NO | DEFAULT 0. |
| `price_list_id` | `BIGINT` | YES | Override default price list. |
| `default_sales_agent_id` | `BIGINT` | YES | FK → `sales_agent.party_id`. |
| `default_branch_id` | `BIGINT` | YES | |
| `is_walk_in` | `BOOLEAN` | NO | True for the synthetic "walk-in customer" used at POS. |
| `tax_exempt` | `BOOLEAN` | NO | DEFAULT false. |

**Example.**
```json
{ "party_id": 540, "credit_limit_amount": 2000000, "credit_terms_days": 30,
  "price_list_id": 3, "default_sales_agent_id": 311, "default_branch_id": 12,
  "is_walk_in": false }
```

## 2.5 `supplier`

**Rationale.** Supplier-specific terms.

| Column | Type | Null | Notes |
|---|---|---|---|
| `party_id` | `BIGINT` | NO | PK + FK. |
| `payment_terms_days` | `INT` | NO | DEFAULT 0. |
| `credit_limit_amount` | `DECIMAL(18,4)` | NO | DEFAULT 0 (rare — we owe them). |
| `default_currency_code` | `VARCHAR(3)` | YES | |
| `bank_name` | `VARCHAR(120)` | YES | |
| `bank_account_no` | `VARCHAR(40)` | YES | |
| `lead_time_days` | `INT` | YES | For reorder calculations. |

## 2.6 `employee`

**Rationale.** HR-tracked person. Often (not always) linked to an `app_user` for system access.

| Column | Type | Null | Notes |
|---|---|---|---|
| `party_id` | `BIGINT` | NO | PK + FK. |
| `app_user_id` | `BIGINT` | YES | FK → `app_user.id`. Null = doesn't log in. |
| `employee_code` | `VARCHAR(40)` | NO | UNIQUE per company. |
| `job_title` | `VARCHAR(120)` | YES | |
| `branch_id` | `BIGINT` | NO | Home branch. |
| `hire_date` | `DATE` | YES | |
| `termination_date` | `DATE` | YES | |

## 2.7 `sales_agent`

**Rationale.** Field-sales person. May or may not be an employee — sometimes commissioned third parties. References a `party` so they have address/phone like everyone else.

| Column | Type | Null | Notes |
|---|---|---|---|
| `party_id` | `BIGINT` | NO | PK + FK. |
| `app_user_id` | `BIGINT` | YES | If they log into WMS, set here. |
| `agent_code` | `VARCHAR(40)` | NO | UNIQUE per company. |
| `route_code` | `VARCHAR(40)` | YES | Default route. |
| `commission_rate` | `DECIMAL(10,4)` | YES | Decimal fraction, e.g. 0.0250 = 2.5%. |
| `branch_id` | `BIGINT` | NO | Branch the agent loads van from. |

## 2.8 `biometric_enrolment`

**Rationale.** Fingerprint or face templates for POS quick login. Templates are stored encrypted; the raw image is never stored.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `app_user_id` | `BIGINT` | NO | |
| `method` | `VARCHAR(20)` | NO | `FINGERPRINT`, `FACE`. |
| `template_ciphertext` | `TEXT` | NO | Base64 of AES-GCM-encrypted template. |
| `enrolled_at` | `TIMESTAMP` | NO | |
| `enrolled_by` | `BIGINT` | NO | Supervisor app_user_id. |
| `revoked_at` | `TIMESTAMP` | YES | |

---

# 3. Catalog (items, groups, pricing, tax)

## 3.1 `item_group`

**Rationale.** Replaces the legacy four parallel tables (`Department`, `Class`, `SubClass`, `Category`, `SubCategory`, `LevelOne`–`LevelFour`) with **one self-referencing tree** that supports any depth. `level` is a denormalised hint for reporting.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `company_id` | `BIGINT` | NO | |
| `parent_id` | `BIGINT` | YES | NULL = root. |
| `level` | `INT` | NO | 1 = Department, 2 = Class, 3 = SubClass, 4 = Category, 5 = SubCategory. |
| `code` | `VARCHAR(40)` | NO | UNIQUE per company. |
| `name` | `VARCHAR(120)` | NO | |
| `status` | `VARCHAR(32)` | NO | |

**Example tree.**
```
1 — BEVERAGES (Department)
  └── 2 — Non-alcoholic (Class)
        └── 3 — Carbonated (SubClass)
              └── 4 — Cola (Category)
                    └── 5 — 500ml (SubCategory)
```

## 3.2 `item`

**Rationale.** **The single most important table**. Replaces the legacy `Product` + `Material` split. One `item` can be sold, consumed in production, or both — driven by `type`. Hierarchies, pricing, costing, tax, and stock all hang off this row.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `company_id` | `BIGINT` | NO | |
| `code` | `VARCHAR(40)` | NO | UNIQUE per company; user-facing SKU. |
| `name` | `VARCHAR(200)` | NO | |
| `short_name` | `VARCHAR(80)` | YES | Used on receipts when space is tight. |
| `type` | `VARCHAR(20)` | NO | `SELLABLE`, `CONSUMABLE`, `BOTH`, `SERVICE`. |
| `item_group_id` | `BIGINT` | NO | Leaf group (any level). |
| `uom_id` | `BIGINT` | NO | Stocking UoM. |
| `vat_group_id` | `BIGINT` | NO | Tax classification. |
| `is_tracked` | `BOOLEAN` | NO | False = no stock kept (services, fees). |
| `avg_cost` | `DECIMAL(18,4)` | NO | Moving average across all branches (read-mostly snapshot). |
| `last_cost` | `DECIMAL(18,4)` | NO | Cost on most recent GRN. |
| `standard_cost` | `DECIMAL(18,4)` | YES | For variance reporting. |
| `min_sell_price` | `DECIMAL(18,4)` | YES | Floor enforced on POS / invoice. |
| `default_supplier_id` | `BIGINT` | YES | FK → `supplier.party_id`. |
| `image_object_key` | `VARCHAR(200)` | YES | |
| `is_serialised` | `BOOLEAN` | NO | DEFAULT false; serialised items track per-unit. |
| `is_batch_tracked` | `BOOLEAN` | NO | DEFAULT false. |
| `shelf_life_days` | `INT` | YES | Drives expiry alerts. |
| `weight_kg` | `DECIMAL(18,4)` | YES | For logistics. |
| `status` | `VARCHAR(32)` | NO | |

**Example.**
```json
{ "id": 8801, "company_id": 7, "code": "BEV-CO-500-001", "name": "Cola 500ml Bottle",
  "short_name": "COLA 500", "type": "BOTH", "item_group_id": 5, "uom_id": 1,
  "vat_group_id": 2, "is_tracked": true, "avg_cost": 480.00, "last_cost": 500.00,
  "default_supplier_id": 712, "status": "ACTIVE" }
```

## 3.3 `item_barcode`

**Rationale.** An item commonly has more than one barcode (unit, six-pack, case). Pulled out so scanning at POS is a single indexed lookup.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `item_id` | `BIGINT` | NO | |
| `barcode` | `VARCHAR(40)` | NO | UNIQUE per company. |
| `pack_uom_id` | `BIGINT` | YES | The UoM this barcode represents. |
| `pack_qty` | `DECIMAL(18,4)` | NO | DEFAULT 1.0. |

**Example.** `{ "item_id": 8801, "barcode": "5449000054227", "pack_uom_id": 1, "pack_qty": 1.0 }` (single bottle), and another row `{ "barcode": "5449000054234", "pack_uom_id": 2, "pack_qty": 24.0 }` (case).

## 3.4 `item_supplier`

**Rationale.** Same item from many suppliers, each with their own code, last cost, and lead time. Drives "best supplier" suggestions on LPOs.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `item_id` | `BIGINT` | NO | |
| `supplier_id` | `BIGINT` | NO | FK → `supplier.party_id`. |
| `supplier_item_code` | `VARCHAR(40)` | YES | |
| `last_buy_price` | `DECIMAL(18,4)` | YES | |
| `last_buy_date` | `DATE` | YES | |
| `is_preferred` | `BOOLEAN` | NO | |

## 3.5 `uom`

**Rationale.** Unit of measure. Items declare their stocking UoM; barcodes and price lists can declare alternates.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `code` | `VARCHAR(20)` | NO | UNIQUE. `EA`, `KG`, `L`, `CASE_24`. |
| `name` | `VARCHAR(80)` | NO | |
| `dimension` | `VARCHAR(20)` | NO | `COUNT`, `WEIGHT`, `VOLUME`, `LENGTH`. |
| `is_base` | `BOOLEAN` | NO | Base unit for its dimension (`EA`, `KG`, `L`). |

## 3.6 `uom_conversion`

**Rationale.** Conversions are item-specific when they involve packs (a case of cola is 24 bottles; a case of beer is 12). For pure dimensional conversions (kg ↔ g), `item_id` is NULL.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `item_id` | `BIGINT` | YES | NULL = global dimensional conversion. |
| `from_uom_id` | `BIGINT` | NO | |
| `to_uom_id` | `BIGINT` | NO | |
| `factor` | `DECIMAL(18,8)` | NO | `qty_to = qty_from * factor`. |

**Example.** `{ "item_id": 8801, "from_uom_id": 2, "to_uom_id": 1, "factor": 24.0 }` — one case of cola = 24 bottles.

## 3.7 `vat_group`

**Rationale.** Tax classification. Rate may change over time; we keep a `valid_from` so historical invoices stay correct.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `company_id` | `BIGINT` | NO | |
| `code` | `VARCHAR(20)` | NO | `VAT_STD`, `VAT_ZERO`, `VAT_EXEMPT`. |
| `name` | `VARCHAR(80)` | NO | |
| `rate` | `DECIMAL(10,4)` | NO | Decimal fraction, e.g. 0.18 = 18%. |
| `valid_from` | `DATE` | NO | |
| `is_default` | `BOOLEAN` | NO | One default per company. |
| `status` | `VARCHAR(32)` | NO | |

**Example.** `{ "code": "VAT_STD", "name": "VAT 18%", "rate": 0.1800, "valid_from": "2020-01-01", "is_default": true }`

## 3.8 `price_list`

**Rationale.** Named price book — `RETAIL`, `WHOLESALE`, `AGENT`, `STAFF`. Items have prices in each list. A customer's `price_list_id` (or a price-tier on the till) selects which prices to use.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `company_id` | `BIGINT` | NO | |
| `code` | `VARCHAR(40)` | NO | UNIQUE per company. |
| `name` | `VARCHAR(120)` | NO | |
| `currency_code` | `VARCHAR(3)` | NO | |
| `valid_from` | `DATE` | NO | |
| `valid_to` | `DATE` | YES | |
| `is_default` | `BOOLEAN` | NO | |
| `tax_inclusive` | `BOOLEAN` | NO | Prices in this list already include tax? |
| `status` | `VARCHAR(32)` | NO | |

## 3.9 `price_list_item`

**Rationale.** A price for one item in one price list, optionally for a UoM other than the stocking UoM (e.g. case price as well as bottle price).

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `price_list_id` | `BIGINT` | NO | |
| `item_id` | `BIGINT` | NO | |
| `uom_id` | `BIGINT` | NO | |
| `price` | `DECIMAL(18,4)` | NO | |
| `valid_from` | `DATE` | NO | |
| `valid_to` | `DATE` | YES | NULL = current. |

**Example.** `{ "price_list_id": 1, "item_id": 8801, "uom_id": 1, "price": 1000.00, "valid_from": "2026-01-01" }` (retail cola, TZS 1000 per bottle).

## 3.10 `price_change_log`

**Rationale.** Auditable history of every price change. Reports answer "what was this item sold at on date X?".

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `price_list_item_id` | `BIGINT` | NO | |
| `old_price` | `DECIMAL(18,4)` | YES | NULL on first set. |
| `new_price` | `DECIMAL(18,4)` | NO | |
| `effective_from` | `DATE` | NO | |
| `changed_at` | `TIMESTAMP` | NO | |
| `changed_by` | `BIGINT` | NO | |
| `reason` | `TEXT` | YES | |

## 3.11 `promotion`

**Rationale.** Time-bounded special pricing — bundles, BOGO, scheduled discounts, customer-segment offers.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `company_id` | `BIGINT` | NO | |
| `code` | `VARCHAR(40)` | NO | UNIQUE per company. |
| `name` | `VARCHAR(120)` | NO | |
| `type` | `VARCHAR(32)` | NO | `PERCENT_OFF`, `AMOUNT_OFF`, `BOGO`, `BUNDLE`, `STEP_QTY`. |
| `params_json` | `TEXT` | NO | Type-specific parameters (e.g. `{"percent":0.10}` or `{"buy":2,"get":1}`). |
| `starts_at` | `TIMESTAMP` | NO | |
| `ends_at` | `TIMESTAMP` | NO | |
| `applies_to_price_list_id` | `BIGINT` | YES | Restrict by price list. |
| `priority` | `INT` | NO | Higher wins when multiple apply. |
| `is_stackable` | `BOOLEAN` | NO | |
| `status` | `VARCHAR(32)` | NO | |

## 3.12 `promotion_item`

**Rationale.** Which items are eligible for the promotion. Empty = all.

| Column | Type | Null | Notes |
|---|---|---|---|
| `promotion_id` | `BIGINT` | NO | Composite PK. |
| `item_id` | `BIGINT` | NO | Composite PK. |

---

# 4. Stock

The truth about stock lives in **postings** (`stock_move`). `item_branch_balance` is a maintained cache for fast reads.

## 4.1 `stock_move`

**Rationale.** Append-only ledger of every stock change. Source of truth. All balances are derived from sums over this table; the balance cache is rebuildable from here.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `at` | `TIMESTAMP` | NO | Business time. Indexed. |
| `item_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `company_id` | `BIGINT` | NO | |
| `qty` | `DECIMAL(18,4)` | NO | Signed. Positive = inbound, negative = outbound. |
| `cost_amount` | `DECIMAL(18,4)` | NO | Unit cost at the time of the move. |
| `direction` | `VARCHAR(20)` | NO | `IN`, `OUT`. Redundant with sign of `qty` but lets index lookups skip the abs(). |
| `move_type` | `VARCHAR(40)` | NO | `GRN`, `SALE`, `RETURN_IN`, `RETURN_OUT`, `DAMAGE`, `ADJUSTMENT`, `TRANSFER_OUT`, `TRANSFER_IN`, `PROD_CONSUME`, `PROD_OUTPUT`, `OPENING`. |
| `ref_type` | `VARCHAR(40)` | NO | The aggregate that caused this move, e.g. `SalesInvoiceLine`. |
| `ref_id` | `BIGINT` | NO | Its id. |
| `actor_id` | `BIGINT` | NO | |
| `notes` | `VARCHAR(200)` | YES | |

**Example.**
```json
{ "id": 880123, "at": "2026-05-13T08:42:11Z", "item_id": 8801, "branch_id": 12, "company_id": 7,
  "qty": -2.0, "cost_amount": 480.00, "direction": "OUT", "move_type": "SALE",
  "ref_type": "SalesInvoiceLine", "ref_id": 71228, "actor_id": 4501 }
```

**Indexes.** `(item_id, branch_id, at)`, `(branch_id, at)`, `(ref_type, ref_id)`.

## 4.2 `item_branch_balance`

**Rationale.** Maintained cache of current stock per `(item, branch)` so screens don't sum millions of moves. Rebuildable.

| Column | Type | Null | Notes |
|---|---|---|---|
| `item_id` | `BIGINT` | NO | Composite PK. |
| `branch_id` | `BIGINT` | NO | Composite PK. |
| `qty_on_hand` | `DECIMAL(18,4)` | NO | |
| `qty_reserved` | `DECIMAL(18,4)` | NO | Held by open quotations / packing lists. |
| `qty_in_transit` | `DECIMAL(18,4)` | NO | From transfers out, not yet received. |
| `avg_cost` | `DECIMAL(18,4)` | NO | Moving average for this branch. |
| `last_cost` | `DECIMAL(18,4)` | NO | |
| `reorder_min` | `DECIMAL(18,4)` | YES | |
| `reorder_max` | `DECIMAL(18,4)` | YES | |
| `bin_location` | `VARCHAR(40)` | YES | |
| `last_moved_at` | `TIMESTAMP` | YES | |

## 4.3 `stock_count`

**Rationale.** Physical count session. Spans many items; once closed, variances are posted as `ADJUSTMENT` moves.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | UNIQUE per branch. |
| `branch_id` | `BIGINT` | NO | |
| `company_id` | `BIGINT` | NO | |
| `count_date` | `DATE` | NO | |
| `type` | `VARCHAR(20)` | NO | `FULL`, `CYCLE`, `SPOT`. |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `IN_PROGRESS`, `CLOSED`, `POSTED`. |
| `started_by` | `BIGINT` | NO | |
| `closed_by` | `BIGINT` | YES | |
| `posted_at` | `TIMESTAMP` | YES | |

## 4.4 `stock_count_line`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `stock_count_id` | `BIGINT` | NO | |
| `item_id` | `BIGINT` | NO | |
| `system_qty` | `DECIMAL(18,4)` | NO | Frozen at count start. |
| `counted_qty` | `DECIMAL(18,4)` | YES | NULL = not yet counted. |
| `variance_qty` | `DECIMAL(18,4)` | YES | Computed on close. |
| `note` | `VARCHAR(200)` | YES | |

## 4.5 `stock_transfer`

**Rationale.** Inter-branch transfer. Two-phase: issuing branch posts `TRANSFER_OUT` immediately; receiving branch posts `TRANSFER_IN` on physical receipt. Difference between issued and received drives a variance investigation.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | UNIQUE per company. |
| `company_id` | `BIGINT` | NO | |
| `from_branch_id` | `BIGINT` | NO | |
| `to_branch_id` | `BIGINT` | NO | |
| `issued_at` | `TIMESTAMP` | YES | |
| `received_at` | `TIMESTAMP` | YES | |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `ISSUED`, `IN_TRANSIT`, `RECEIVED`, `CLOSED`. |

## 4.6 `stock_transfer_line`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `stock_transfer_id` | `BIGINT` | NO | |
| `item_id` | `BIGINT` | NO | |
| `issued_qty` | `DECIMAL(18,4)` | NO | |
| `received_qty` | `DECIMAL(18,4)` | YES | |
| `cost_amount` | `DECIMAL(18,4)` | NO | Frozen at issue. |

---

# 5. Procurement

Purchase quotation → LPO → GRN → Supplier Invoice → Payment, with vendor returns and credit notes alongside.

## 5.1 `purchase_quotation`

**Rationale.** Request-for-quote sent to one or more suppliers for the same items. Used when items are non-stocked or prices need refreshing.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `supplier_id` | `BIGINT` | NO | |
| `quote_date` | `DATE` | NO | |
| `valid_until` | `DATE` | YES | |
| `currency_code` | `VARCHAR(3)` | NO | |
| `subtotal_amount` | `DECIMAL(18,4)` | NO | |
| `tax_amount` | `DECIMAL(18,4)` | NO | |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `SENT`, `RECEIVED`, `CONVERTED`, `EXPIRED`, `CANCELLED`. |
| `notes` | `TEXT` | YES | |

## 5.2 `purchase_quotation_line`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `quotation_id` | `BIGINT` | NO | |
| `item_id` | `BIGINT` | NO | |
| `uom_id` | `BIGINT` | NO | |
| `qty` | `DECIMAL(18,4)` | NO | |
| `unit_price` | `DECIMAL(18,4)` | NO | |
| `vat_group_id` | `BIGINT` | NO | |
| `line_total` | `DECIMAL(18,4)` | NO | |
| `notes` | `VARCHAR(200)` | YES | |

## 5.3 `lpo_order`

**Rationale.** Local Purchase Order. Authoritative document sent to a supplier committing to buy. Posted state freezes prices and quantities; GRNs draw against it.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | UNIQUE per branch. |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `supplier_id` | `BIGINT` | NO | |
| `order_date` | `DATE` | NO | |
| `expected_delivery_date` | `DATE` | YES | |
| `currency_code` | `VARCHAR(3)` | NO | |
| `subtotal_amount` | `DECIMAL(18,4)` | NO | |
| `tax_amount` | `DECIMAL(18,4)` | NO | |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED`. |
| `approved_by` | `BIGINT` | YES | |
| `approved_at` | `TIMESTAMP` | YES | |
| `notes` | `TEXT` | YES | |

**Example.**
```json
{ "id": 31021, "number": "LPO-DSM-000124", "company_id": 7, "branch_id": 12,
  "supplier_id": 712, "order_date": "2026-05-10", "expected_delivery_date": "2026-05-13",
  "currency_code": "TZS", "subtotal_amount": 4800000, "tax_amount": 864000, "total_amount": 5664000,
  "status": "APPROVED", "approved_by": 4101, "approved_at": "2026-05-10T14:00:00Z" }
```

## 5.4 `lpo_order_line`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `lpo_order_id` | `BIGINT` | NO | |
| `line_no` | `INT` | NO | 1-based sequence. |
| `item_id` | `BIGINT` | NO | |
| `uom_id` | `BIGINT` | NO | |
| `ordered_qty` | `DECIMAL(18,4)` | NO | |
| `received_qty` | `DECIMAL(18,4)` | NO | Aggregated from GRNs; DEFAULT 0. |
| `unit_price` | `DECIMAL(18,4)` | NO | |
| `vat_group_id` | `BIGINT` | NO | |
| `discount_pct` | `DECIMAL(10,4)` | NO | DEFAULT 0. |
| `line_total` | `DECIMAL(18,4)` | NO | After discount, before tax. |

## 5.5 `grn`

**Rationale.** Goods Received Note. The receiving event. Posting it: increments stock, updates costs, opens a supplier payable.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `supplier_id` | `BIGINT` | NO | |
| `lpo_order_id` | `BIGINT` | YES | NULL = direct GRN (rare, requires privilege). |
| `received_date` | `DATE` | NO | |
| `supplier_delivery_note` | `VARCHAR(80)` | YES | |
| `subtotal_amount` | `DECIMAL(18,4)` | NO | |
| `tax_amount` | `DECIMAL(18,4)` | NO | |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `POSTED`, `CANCELLED`. |
| `posted_at` | `TIMESTAMP` | YES | |
| `posted_by` | `BIGINT` | YES | |

## 5.6 `grn_line`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `grn_id` | `BIGINT` | NO | |
| `lpo_order_line_id` | `BIGINT` | YES | |
| `item_id` | `BIGINT` | NO | |
| `uom_id` | `BIGINT` | NO | |
| `received_qty` | `DECIMAL(18,4)` | NO | |
| `unit_cost` | `DECIMAL(18,4)` | NO | Lands in `stock_move.cost_amount`. |
| `vat_group_id` | `BIGINT` | NO | |
| `line_total` | `DECIMAL(18,4)` | NO | |
| `batch_no` | `VARCHAR(40)` | YES | If `item.is_batch_tracked`. |
| `expiry_date` | `DATE` | YES | |

## 5.7 `supplier_invoice`

**Rationale.** The supplier's bill, matched to one or more GRNs. Drives the payable amount and due date; supports three-way match (LPO ↔ GRN ↔ Invoice).

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | Our internal number. |
| `supplier_invoice_no` | `VARCHAR(80)` | NO | Number on the supplier's bill. |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `supplier_id` | `BIGINT` | NO | |
| `invoice_date` | `DATE` | NO | |
| `due_date` | `DATE` | NO | Driven by supplier `payment_terms_days`. |
| `subtotal_amount` | `DECIMAL(18,4)` | NO | |
| `tax_amount` | `DECIMAL(18,4)` | NO | |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `paid_amount` | `DECIMAL(18,4)` | NO | DEFAULT 0. |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `POSTED`, `PARTIALLY_PAID`, `PAID`, `CANCELLED`. |

## 5.8 `supplier_invoice_grn`

**Rationale.** Junction: an invoice can cover several GRNs (consolidated billing); a GRN can in principle be split across invoices.

| Column | Type | Null | Notes |
|---|---|---|---|
| `supplier_invoice_id` | `BIGINT` | NO | PK. |
| `grn_id` | `BIGINT` | NO | PK. |
| `amount` | `DECIMAL(18,4)` | NO | Portion of the GRN covered. |

## 5.9 `vendor_return`

**Rationale.** Goods being returned to the supplier (damaged, wrong delivery, expired). On post: stock decremented, supplier credit-note expected.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `supplier_id` | `BIGINT` | NO | |
| `original_grn_id` | `BIGINT` | YES | |
| `return_date` | `DATE` | NO | |
| `reason` | `VARCHAR(120)` | NO | `DAMAGED`, `EXPIRED`, `WRONG_ITEM`, `OVERSUPPLY`, `OTHER`. |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `POSTED`, `CREDITED`, `CANCELLED`. |

## 5.10 `vendor_return_line`

Same shape as `grn_line` with `returned_qty` instead of `received_qty`.

## 5.11 `vendor_credit_note`

**Rationale.** Supplier's confirmation that they will reduce the payable. Allocated against open supplier invoices.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `vendor_return_id` | `BIGINT` | YES | |
| `supplier_id` | `BIGINT` | NO | |
| `cn_date` | `DATE` | NO | |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `allocated_amount` | `DECIMAL(18,4)` | NO | DEFAULT 0. |
| `status` | `VARCHAR(32)` | NO | |

---

# 6. Sales

Sales quotation → Sales Invoice → Receipt → Allocation; with customer returns / credit notes and packing lists.

## 6.1 `sales_quotation`

**Rationale.** A quote sent to a customer. May expire; converts to a sales invoice when accepted.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `customer_id` | `BIGINT` | NO | |
| `sales_agent_id` | `BIGINT` | YES | |
| `quote_date` | `DATE` | NO | |
| `valid_until` | `DATE` | YES | |
| `price_list_id` | `BIGINT` | NO | Frozen at issue. |
| `subtotal_amount` | `DECIMAL(18,4)` | NO | |
| `tax_amount` | `DECIMAL(18,4)` | NO | |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `SENT`, `ACCEPTED`, `EXPIRED`, `CONVERTED`, `CANCELLED`. |
| `converted_to_invoice_id` | `BIGINT` | YES | |
| `notes` | `TEXT` | YES | |

## 6.2 `sales_quotation_line`

Shape mirrors `sales_invoice_line` below.

## 6.3 `sales_invoice`

**Rationale.** The legal sales document. Posting: decrements stock, creates customer debt (if credit) or cash entry (if paid), emits `SalesInvoicePosted` event.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | UNIQUE per branch. |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `customer_id` | `BIGINT` | NO | |
| `sales_agent_id` | `BIGINT` | YES | |
| `invoice_date` | `DATE` | NO | The business date. |
| `due_date` | `DATE` | YES | For credit sales. |
| `payment_terms` | `VARCHAR(20)` | NO | `CASH`, `CREDIT`. |
| `currency_code` | `VARCHAR(3)` | NO | |
| `price_list_id` | `BIGINT` | NO | |
| `subtotal_amount` | `DECIMAL(18,4)` | NO | Before tax, after line discounts. |
| `discount_amount` | `DECIMAL(18,4)` | NO | Header-level discount. |
| `tax_amount` | `DECIMAL(18,4)` | NO | |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `paid_amount` | `DECIMAL(18,4)` | NO | Set by allocations; DEFAULT 0. |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `POSTED`, `PARTIALLY_PAID`, `PAID`, `CANCELLED`, `VOIDED`. |
| `posted_at` | `TIMESTAMP` | YES | |
| `posted_by` | `BIGINT` | YES | |
| `voided_at` | `TIMESTAMP` | YES | |
| `voided_by` | `BIGINT` | YES | |
| `void_reason` | `VARCHAR(200)` | YES | |
| `reference` | `VARCHAR(80)` | YES | Customer's PO number, if any. |
| `notes` | `TEXT` | YES | |

**Example.**
```json
{ "id": 71228, "number": "SI-DSM-000931", "company_id": 7, "branch_id": 12,
  "customer_id": 540, "sales_agent_id": 311, "invoice_date": "2026-05-13",
  "due_date": "2026-06-12", "payment_terms": "CREDIT", "currency_code": "TZS",
  "price_list_id": 2, "subtotal_amount": 100000, "discount_amount": 0,
  "tax_amount": 18000, "total_amount": 118000, "paid_amount": 0, "status": "POSTED",
  "posted_at": "2026-05-13T08:42:11Z", "posted_by": 4501 }
```

## 6.4 `sales_invoice_line`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `sales_invoice_id` | `BIGINT` | NO | |
| `line_no` | `INT` | NO | |
| `item_id` | `BIGINT` | NO | |
| `uom_id` | `BIGINT` | NO | |
| `qty` | `DECIMAL(18,4)` | NO | |
| `unit_price` | `DECIMAL(18,4)` | NO | After price-list resolution, before discount. |
| `discount_pct` | `DECIMAL(10,4)` | NO | DEFAULT 0. |
| `discount_amount` | `DECIMAL(18,4)` | NO | DEFAULT 0. |
| `vat_group_id` | `BIGINT` | NO | |
| `tax_amount` | `DECIMAL(18,4)` | NO | |
| `line_total` | `DECIMAL(18,4)` | NO | Including tax. |
| `cost_amount` | `DECIMAL(18,4)` | NO | Snapped cost at post — drives margin reporting. |
| `promotion_id` | `BIGINT` | YES | If a promotion adjusted the price. |

## 6.5 `sales_receipt`

**Rationale.** Money coming in from a customer. May be against one or many invoices (via `receipt_allocation`).

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `customer_id` | `BIGINT` | NO | |
| `receipt_date` | `DATE` | NO | |
| `method` | `VARCHAR(20)` | NO | `CASH`, `CARD`, `BANK_TRANSFER`, `MOBILE_MONEY`, `CHEQUE`, `STORE_CREDIT`. |
| `reference` | `VARCHAR(80)` | YES | Cheque no, transaction id, etc. |
| `currency_code` | `VARCHAR(3)` | NO | |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `allocated_amount` | `DECIMAL(18,4)` | NO | Sum across `receipt_allocation`. |
| `unallocated_amount` | `DECIMAL(18,4)` | NO | total − allocated. Goes to customer credit if positive. |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `POSTED`, `CANCELLED`. |
| `posted_at` | `TIMESTAMP` | YES | |
| `posted_by` | `BIGINT` | YES | |
| `notes` | `TEXT` | YES | |

## 6.6 `receipt_allocation`

**Rationale.** Links a receipt to one or many invoices it pays.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `sales_receipt_id` | `BIGINT` | NO | |
| `sales_invoice_id` | `BIGINT` | NO | |
| `amount` | `DECIMAL(18,4)` | NO | |
| `allocated_at` | `TIMESTAMP` | NO | |
| `allocated_by` | `BIGINT` | NO | |

**Example.** Receipt #4502 for TZS 200,000 paid two invoices: `{ receipt_id: 4502, invoice_id: 71228, amount: 118000 }` and `{ receipt_id: 4502, invoice_id: 71229, amount: 82000 }`.

## 6.7 `customer_return`

**Rationale.** Goods coming back from a customer.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `customer_id` | `BIGINT` | NO | |
| `original_invoice_id` | `BIGINT` | YES | |
| `return_date` | `DATE` | NO | |
| `reason` | `VARCHAR(120)` | NO | `DAMAGED`, `EXPIRED`, `WRONG_ITEM`, `BUYER_REMORSE`, `OTHER`. |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `POSTED`, `CREDITED`, `CANCELLED`. |
| `restock` | `BOOLEAN` | NO | If false → posts as `DAMAGE` move, not `RETURN_IN`. |

## 6.8 `customer_return_line`

Mirrors `sales_invoice_line` with `returned_qty`.

## 6.9 `customer_credit_note`

**Rationale.** Credits issued to customers — for returns, goodwill, or pricing corrections. Allocated against open invoices or held as customer credit.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `customer_return_id` | `BIGINT` | YES | |
| `customer_id` | `BIGINT` | NO | |
| `cn_date` | `DATE` | NO | |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `allocated_amount` | `DECIMAL(18,4)` | NO | DEFAULT 0. |
| `status` | `VARCHAR(32)` | NO | |

## 6.10 `packing_list`

**Rationale.** Physical delivery against a sales invoice — possibly in multiple trips. Each posting causes `IN_TRANSIT` then `OUT` movements.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `sales_invoice_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `dispatch_date` | `DATE` | NO | |
| `driver_name` | `VARCHAR(120)` | YES | |
| `vehicle_no` | `VARCHAR(40)` | YES | |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `DISPATCHED`, `DELIVERED`, `CANCELLED`. |

## 6.11 `packing_list_line`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `packing_list_id` | `BIGINT` | NO | |
| `sales_invoice_line_id` | `BIGINT` | NO | |
| `qty` | `DECIMAL(18,4)` | NO | |

---

# 7. POS (Desktop)

Till sessions and the sales they hold. POS sales are a sibling of `sales_invoice`, not a subset — they have simpler shape and offline-origin semantics. Both feed the same `stock_move`, `debt_entry`, and `cash_entry` postings.

## 7.1 `till`

**Rationale.** A physical till — a workstation + cash drawer + printer.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `code` | `VARCHAR(20)` | NO | `TILL-1`. UNIQUE per branch. |
| `name` | `VARCHAR(80)` | NO | |
| `install_id` | `VARCHAR(80)` | YES | Stable POS install token bound to this till. |
| `default_price_list_id` | `BIGINT` | NO | |
| `status` | `VARCHAR(32)` | NO | |

## 7.2 `till_session`

**Rationale.** A cashier's shift on a till. Bounds float, sales, pickups, petty cash, and the close. At most one OPEN session per till.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `till_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `business_date` | `DATE` | NO | Driven by `business_day`. |
| `opened_by` | `BIGINT` | NO | |
| `opened_at` | `TIMESTAMP` | NO | |
| `opening_float_amount` | `DECIMAL(18,4)` | NO | |
| `closed_by` | `BIGINT` | YES | |
| `closed_at` | `TIMESTAMP` | YES | |
| `expected_cash_amount` | `DECIMAL(18,4)` | YES | Computed at close. |
| `declared_cash_amount` | `DECIMAL(18,4)` | YES | What the cashier counted. |
| `variance_amount` | `DECIMAL(18,4)` | YES | declared − expected. |
| `status` | `VARCHAR(32)` | NO | `OPEN`, `CLOSED`, `RECONCILED`. |
| `z_report_object_key` | `VARCHAR(200)` | YES | PDF in object store. |

**Example.**
```json
{ "id": 5520, "till_id": 4, "branch_id": 12, "business_date": "2026-05-13",
  "opened_by": 4501, "opened_at": "2026-05-13T07:00:00Z", "opening_float_amount": 50000,
  "closed_at": "2026-05-13T20:30:00Z", "expected_cash_amount": 893500,
  "declared_cash_amount": 893000, "variance_amount": -500, "status": "CLOSED" }
```

## 7.3 `pos_sale`

**Rationale.** A single till transaction. Generated client-side (for offline), assigned a server `id` on push. `client_op_id` provides idempotency.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(60)` | NO | Client-namespaced: `TILL-3-20260513-00027`. UNIQUE. |
| `client_op_id` | `UUID` | NO | UNIQUE. |
| `till_session_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `customer_id` | `BIGINT` | NO | Walk-in customer if not specified. |
| `cashier_id` | `BIGINT` | NO | |
| `supervisor_id` | `BIGINT` | YES | If supervisor PIN was used. |
| `sale_at` | `TIMESTAMP` | NO | Client time, kept for receipts; `server_at` separate. |
| `server_at` | `TIMESTAMP` | NO | When server received it. |
| `subtotal_amount` | `DECIMAL(18,4)` | NO | |
| `discount_amount` | `DECIMAL(18,4)` | NO | |
| `tax_amount` | `DECIMAL(18,4)` | NO | |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `change_amount` | `DECIMAL(18,4)` | NO | Tendered − total, when positive. |
| `status` | `VARCHAR(32)` | NO | `POSTED`, `VOIDED`. POS sales are never `DRAFT` — they are committed locally first. |
| `voided_at` | `TIMESTAMP` | YES | |
| `voided_by` | `BIGINT` | YES | |
| `void_reason` | `VARCHAR(200)` | YES | |
| `fiscal_signature` | `VARCHAR(200)` | YES | From fiscal printer, if used. |

## 7.4 `pos_sale_line`

Mirrors `sales_invoice_line` shape.

## 7.5 `pos_payment`

**Rationale.** Mixed tender — a sale can be paid by cash + card + mobile money in one go.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `pos_sale_id` | `BIGINT` | NO | |
| `method` | `VARCHAR(20)` | NO | `CASH`, `CARD`, `MOBILE_MONEY`, `VOUCHER`, `STORE_CREDIT`. |
| `amount` | `DECIMAL(18,4)` | NO | |
| `reference` | `VARCHAR(80)` | YES | Approval code, MMO trans ID, voucher code. |
| `terminal_id` | `VARCHAR(40)` | YES | Card terminal ID. |
| `last4` | `VARCHAR(4)` | YES | Card last 4 — never the PAN. |

## 7.6 `cash_pickup`

**Rationale.** Cash removed from the drawer mid-shift for safety (large notes), recorded so the close reconciles.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `till_session_id` | `BIGINT` | NO | |
| `amount` | `DECIMAL(18,4)` | NO | |
| `at` | `TIMESTAMP` | NO | |
| `picked_up_by` | `BIGINT` | NO | |
| `authorised_by` | `BIGINT` | NO | Supervisor. |
| `note` | `VARCHAR(200)` | YES | |

## 7.7 `petty_cash`

**Rationale.** Small payouts from the till drawer (deliveries, courier, sundries). Tracked so the close reconciles and the expense is captured.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `till_session_id` | `BIGINT` | YES | NULL = paid from main cash book, not a till. |
| `branch_id` | `BIGINT` | NO | |
| `amount` | `DECIMAL(18,4)` | NO | |
| `at` | `TIMESTAMP` | NO | |
| `category` | `VARCHAR(40)` | NO | `TRANSPORT`, `OFFICE`, `MAINTENANCE`, `OTHER`. |
| `paid_to` | `VARCHAR(120)` | YES | |
| `paid_by` | `BIGINT` | NO | |
| `authorised_by` | `BIGINT` | NO | |
| `description` | `TEXT` | YES | |
| `receipt_attachment_key` | `VARCHAR(200)` | YES | |

---

# 8. WMS (Field Sales)

The field-agent workflow. A daily route → load van → sell at customer sites → settle at end of day.

## 8.1 `sales_list`

**Rationale.** Stock loaded onto an agent's vehicle for the day. Posting it: `TRANSFER_OUT` from branch to "van" (treated as a virtual branch per agent).

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | Issuing branch. |
| `agent_id` | `BIGINT` | NO | |
| `route_code` | `VARCHAR(40)` | YES | |
| `load_date` | `DATE` | NO | |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `LOADED`, `IN_FIELD`, `SETTLED`, `CANCELLED`. |
| `loaded_at` | `TIMESTAMP` | YES | |
| `loaded_by` | `BIGINT` | YES | |

## 8.2 `sales_list_line`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `sales_list_id` | `BIGINT` | NO | |
| `item_id` | `BIGINT` | NO | |
| `qty_loaded` | `DECIMAL(18,4)` | NO | |
| `qty_returned` | `DECIMAL(18,4)` | YES | Filled at end-of-day. |
| `qty_sold` | `DECIMAL(18,4)` | YES | Computed: loaded − returned. |
| `unit_cost` | `DECIMAL(18,4)` | NO | Snap at load. |

## 8.3 `sales_sheet`

**Rationale.** End-of-day settlement document. Sums all sales (cash and credit), expenses, and reconciles against declared cash. On approval at HQ, downstream postings (stock, debt, cash) commit.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `sales_list_id` | `BIGINT` | NO | UNIQUE. |
| `agent_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `sheet_date` | `DATE` | NO | |
| `sales_count` | `INT` | NO | |
| `total_sales_amount` | `DECIMAL(18,4)` | NO | |
| `total_credit_amount` | `DECIMAL(18,4)` | NO | |
| `total_cash_amount` | `DECIMAL(18,4)` | NO | Cash captured. |
| `total_expense_amount` | `DECIMAL(18,4)` | NO | |
| `declared_cash_amount` | `DECIMAL(18,4)` | NO | What agent handed over. |
| `expected_cash_amount` | `DECIMAL(18,4)` | NO | total_cash − total_expense. |
| `variance_amount` | `DECIMAL(18,4)` | NO | declared − expected. |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`. |
| `submitted_at` | `TIMESTAMP` | YES | |
| `approved_at` | `TIMESTAMP` | YES | |
| `approved_by` | `BIGINT` | YES | |
| `notes` | `TEXT` | YES | |

## 8.4 `sales_sheet_sale`

**Rationale.** Each individual sale captured on the route. Linked back to either a generated `sales_invoice` (on credit) or stays as a cash sale.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `sales_sheet_id` | `BIGINT` | NO | |
| `client_op_id` | `UUID` | NO | UNIQUE. |
| `customer_id` | `BIGINT` | NO | |
| `sold_at` | `TIMESTAMP` | NO | |
| `payment_terms` | `VARCHAR(20)` | NO | `CASH`, `CREDIT`. |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `paid_amount` | `DECIMAL(18,4)` | NO | |
| `sales_invoice_id` | `BIGINT` | YES | Generated server-side on approval. |
| `gps_lat` | `DECIMAL(10,7)` | YES | |
| `gps_lng` | `DECIMAL(10,7)` | YES | |
| `notes` | `TEXT` | YES | |

## 8.5 `sales_sheet_sale_line`

Mirrors `sales_invoice_line`.

## 8.6 `sales_expense`

**Rationale.** Out-of-pocket costs incurred on the route (fuel, tolls, parking). Deducted from cash collection.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `sales_sheet_id` | `BIGINT` | NO | |
| `category` | `VARCHAR(40)` | NO | `FUEL`, `TOLLS`, `PARKING`, `MEALS`, `OTHER`. |
| `amount` | `DECIMAL(18,4)` | NO | |
| `at` | `TIMESTAMP` | NO | |
| `description` | `VARCHAR(200)` | YES | |
| `receipt_attachment_key` | `VARCHAR(200)` | YES | |

## 8.7 `route`

**Rationale.** Named, reusable list of customers an agent visits on a recurring day. Optional in MVP; useful for analytics later.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `code` | `VARCHAR(40)` | NO | UNIQUE per company. |
| `name` | `VARCHAR(120)` | NO | |
| `default_agent_id` | `BIGINT` | YES | |
| `day_of_week` | `INT` | YES | 1=Mon … 7=Sun. |

## 8.8 `route_customer`

| Column | Type | Null | Notes |
|---|---|---|---|
| `route_id` | `BIGINT` | NO | PK. |
| `customer_id` | `BIGINT` | NO | PK. |
| `visit_order` | `INT` | NO | |

---

# 9. Production

Recipe-based and ad-hoc transformations of materials into products (or material to material, product to product).

## 9.1 `bom`

**Rationale.** Bill of Materials — the recipe. One BOM per output item per version; switching to a new recipe means a new version with `valid_from`.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `company_id` | `BIGINT` | NO | |
| `output_item_id` | `BIGINT` | NO | The thing produced. |
| `output_qty` | `DECIMAL(18,4)` | NO | What one execution yields. |
| `output_uom_id` | `BIGINT` | NO | |
| `version` | `INT` | NO | Composite UNIQUE with `output_item_id`. |
| `valid_from` | `DATE` | NO | |
| `valid_to` | `DATE` | YES | |
| `standard_yield_pct` | `DECIMAL(10,4)` | NO | Expected good output / theoretical output. |
| `notes` | `TEXT` | YES | |
| `status` | `VARCHAR(32)` | NO | |

## 9.2 `bom_line`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `bom_id` | `BIGINT` | NO | |
| `input_item_id` | `BIGINT` | NO | Material consumed. |
| `qty` | `DECIMAL(18,4)` | NO | Per one output execution. |
| `uom_id` | `BIGINT` | NO | |
| `wastage_pct` | `DECIMAL(10,4)` | NO | DEFAULT 0. |

**Example BOM** for one batch of cola: BOM #51, output `Cola 500ml × 24 case`, with lines `{ Cola Concentrate: 0.5 L }`, `{ Bottle 500ml: 24 EA }`, `{ Cap: 24 EA }`, `{ Label: 24 EA }`, `{ Water (treated): 11.5 L }`.

## 9.3 `production_batch`

**Rationale.** A single execution. Planned → in-progress → completed. Consumption and output postings happen on completion.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `bom_id` | `BIGINT` | YES | NULL for custom production. |
| `output_item_id` | `BIGINT` | NO | |
| `planned_qty` | `DECIMAL(18,4)` | NO | |
| `actual_qty` | `DECIMAL(18,4)` | YES | |
| `reject_qty` | `DECIMAL(18,4)` | YES | |
| `started_at` | `TIMESTAMP` | YES | |
| `completed_at` | `TIMESTAMP` | YES | |
| `started_by` | `BIGINT` | YES | |
| `completed_by` | `BIGINT` | YES | |
| `status` | `VARCHAR(32)` | NO | `PLANNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`. |

## 9.4 `production_consumption`

**Rationale.** Actual materials consumed by the batch. Both planned and actual are kept for variance reporting.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `production_batch_id` | `BIGINT` | NO | |
| `input_item_id` | `BIGINT` | NO | |
| `planned_qty` | `DECIMAL(18,4)` | NO | |
| `actual_qty` | `DECIMAL(18,4)` | YES | |
| `unit_cost` | `DECIMAL(18,4)` | YES | At time of completion. |

## 9.5 `production_output`

**Rationale.** What actually came out — often a single item, but allowing multiple lines lets co-products and by-products live in one batch.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `production_batch_id` | `BIGINT` | NO | |
| `output_item_id` | `BIGINT` | NO | |
| `qty` | `DECIMAL(18,4)` | NO | |
| `unit_cost` | `DECIMAL(18,4)` | NO | Sum of consumption / total output qty. |
| `is_primary` | `BOOLEAN` | NO | True = main output; false = co/by-product. |

## 9.6 `conversion`

**Rationale.** Generic transformation row covering the legacy `MaterialToMaterial`, `MaterialToProduct`, `ProductToProduct`, `ProductToMaterial`. Cheaper than four tables.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `conversion_date` | `DATE` | NO | |
| `from_item_id` | `BIGINT` | NO | |
| `from_qty` | `DECIMAL(18,4)` | NO | |
| `to_item_id` | `BIGINT` | NO | |
| `to_qty` | `DECIMAL(18,4)` | NO | |
| `unit_cost` | `DECIMAL(18,4)` | NO | |
| `reason` | `VARCHAR(120)` | YES | |
| `status` | `VARCHAR(32)` | NO | `DRAFT`, `POSTED`, `CANCELLED`. |

---

# 10. Debt and Cash

These are the financial postings — append-only ledgers that derive customer/supplier balances and per-branch cash positions.

## 10.1 `debt_entry`

**Rationale.** Append-only ledger of receivables (customer owes us) and payables (we owe supplier). Every credit invoice, every receipt allocation, every credit note creates one or more rows.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `at` | `TIMESTAMP` | NO | |
| `party_id` | `BIGINT` | NO | Customer or supplier. |
| `direction` | `VARCHAR(20)` | NO | `RECEIVABLE` (customer debt), `PAYABLE` (we owe supplier). |
| `amount` | `DECIMAL(18,4)` | NO | Positive = debt increased; negative = debt decreased. |
| `running_balance` | `DECIMAL(18,4)` | NO | Snapshot after this entry. Aids reporting. |
| `currency_code` | `VARCHAR(3)` | NO | |
| `ref_type` | `VARCHAR(40)` | NO | `SalesInvoice`, `SalesReceipt`, `CustomerCreditNote`, `SupplierInvoice`, `SupplierPayment`, `VendorCreditNote`. |
| `ref_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `due_date` | `DATE` | YES | For ageing buckets. |
| `actor_id` | `BIGINT` | NO | |

**Example.**
```json
{ "id": 220411, "at": "2026-05-13T08:42:11Z", "party_id": 540, "direction": "RECEIVABLE",
  "amount": 118000, "running_balance": 268000, "currency_code": "TZS",
  "ref_type": "SalesInvoice", "ref_id": 71228, "branch_id": 12,
  "due_date": "2026-06-12", "actor_id": 4501 }
```

## 10.2 `cash_entry`

**Rationale.** Append-only ledger of cash in/out per branch. Drives the cash book and end-of-day reconciliation.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `at` | `TIMESTAMP` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `account` | `VARCHAR(40)` | NO | `TILL`, `CASH_BOX`, `BANK`, `MOBILE_MONEY`. |
| `direction` | `VARCHAR(20)` | NO | `IN`, `OUT`. |
| `amount` | `DECIMAL(18,4)` | NO | Always positive; direction in the dedicated column. |
| `currency_code` | `VARCHAR(3)` | NO | |
| `ref_type` | `VARCHAR(40)` | NO | `PosSale`, `SalesReceipt`, `CashPickup`, `PettyCash`, `SupplierPayment`, `BankDeposit`. |
| `ref_id` | `BIGINT` | NO | |
| `actor_id` | `BIGINT` | NO | |

## 10.3 `cash_book`

**Rationale.** Daily summary per branch per account. Computed/maintained, not source-of-truth — but cheap to query.

| Column | Type | Null | Notes |
|---|---|---|---|
| `branch_id` | `BIGINT` | NO | Composite PK. |
| `account` | `VARCHAR(40)` | NO | Composite PK. |
| `business_date` | `DATE` | NO | Composite PK. |
| `opening_amount` | `DECIMAL(18,4)` | NO | |
| `in_amount` | `DECIMAL(18,4)` | NO | |
| `out_amount` | `DECIMAL(18,4)` | NO | |
| `closing_amount` | `DECIMAL(18,4)` | NO | opening + in − out. |

## 10.4 `supplier_payment`

**Rationale.** Money going out to a supplier. Allocated against open supplier invoices.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `company_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `supplier_id` | `BIGINT` | NO | |
| `payment_date` | `DATE` | NO | |
| `method` | `VARCHAR(20)` | NO | `CASH`, `BANK_TRANSFER`, `CHEQUE`, `MOBILE_MONEY`. |
| `reference` | `VARCHAR(80)` | YES | |
| `total_amount` | `DECIMAL(18,4)` | NO | |
| `allocated_amount` | `DECIMAL(18,4)` | NO | |
| `status` | `VARCHAR(32)` | NO | |

## 10.5 `supplier_payment_allocation`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `supplier_payment_id` | `BIGINT` | NO | |
| `supplier_invoice_id` | `BIGINT` | NO | |
| `amount` | `DECIMAL(18,4)` | NO | |

## 10.6 `debt_writeoff`

**Rationale.** Authorised forgiveness of an outstanding receivable (or payable, rare). Recorded for audit; reduces ageing.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `number` | `VARCHAR(40)` | NO | |
| `party_id` | `BIGINT` | NO | |
| `direction` | `VARCHAR(20)` | NO | |
| `amount` | `DECIMAL(18,4)` | NO | |
| `reason` | `TEXT` | NO | |
| `approved_by_supervisor_id` | `BIGINT` | NO | |
| `approved_by_accountant_id` | `BIGINT` | NO | |
| `writeoff_date` | `DATE` | NO | |

---

# 11. Day Management

## 11.1 `business_day`

**Rationale.** A logical day per branch, opened and closed explicitly. Drives posting dates and prevents back-dated entries except via supervisor override.

| Column | Type | Null | Notes |
|---|---|---|---|
| `branch_id` | `BIGINT` | NO | Composite PK. |
| `business_date` | `DATE` | NO | Composite PK. |
| `status` | `VARCHAR(32)` | NO | `OPEN`, `CLOSING`, `CLOSED`. |
| `opened_at` | `TIMESTAMP` | NO | |
| `opened_by` | `BIGINT` | NO | |
| `closed_at` | `TIMESTAMP` | YES | |
| `closed_by` | `BIGINT` | YES | |
| `eod_report_object_key` | `VARCHAR(200)` | YES | PDF in object store. |

**Invariant.** At most one row per branch with `status = OPEN`.

## 11.2 `business_day_override`

**Rationale.** When supervisors back-date an entry into a closed day, the override is recorded for audit. Rare and tightly scoped.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `target_business_date` | `DATE` | NO | The closed day being posted into. |
| `entity_type` | `VARCHAR(40)` | NO | What was posted. |
| `entity_id` | `BIGINT` | NO | |
| `reason` | `TEXT` | NO | |
| `authorised_by` | `BIGINT` | NO | |
| `at` | `TIMESTAMP` | NO | |

---

# 12. HR (light)

## 12.1 `shift`

**Rationale.** Planned and actual shift for an employee. Links to till sessions for cashiers.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `employee_id` | `BIGINT` | NO | |
| `branch_id` | `BIGINT` | NO | |
| `shift_date` | `DATE` | NO | |
| `planned_start` | `TIMESTAMP` | YES | |
| `planned_end` | `TIMESTAMP` | YES | |
| `actual_start` | `TIMESTAMP` | YES | |
| `actual_end` | `TIMESTAMP` | YES | |
| `till_session_id` | `BIGINT` | YES | If cashier shift. |
| `status` | `VARCHAR(32)` | NO | `PLANNED`, `ACTIVE`, `COMPLETED`, `MISSED`. |

---

# 13. Reporting

Reports are mostly read models, not stored entities. Two persisted entities support the user experience.

## 13.1 `report_definition`

**Rationale.** A parameterised query + output schema. Lets admins create custom report variants without code changes (Phase 2).

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `code` | `VARCHAR(60)` | NO | UNIQUE. |
| `name` | `VARCHAR(120)` | NO | |
| `category` | `VARCHAR(40)` | NO | `SALES`, `INVENTORY`, `PRODUCTION`, `FINANCE`. |
| `query_spec_json` | `TEXT` | NO | Builder spec (not raw SQL). |
| `param_spec_json` | `TEXT` | NO | Parameter schema. |
| `is_system` | `BOOLEAN` | NO | |
| `status` | `VARCHAR(32)` | NO | |

## 13.2 `report_run`

**Rationale.** Audit + cache. Every report run is recorded with parameters, who ran it, and a pointer to the rendered output.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `BIGINT` | NO | |
| `report_definition_id` | `BIGINT` | NO | |
| `params_json` | `TEXT` | NO | |
| `run_by` | `BIGINT` | NO | |
| `run_at` | `TIMESTAMP` | NO | |
| `row_count` | `INT` | YES | |
| `output_object_key` | `VARCHAR(200)` | YES | PDF/Excel/CSV in object store. |
| `duration_ms` | `INT` | YES | |
| `status` | `VARCHAR(32)` | NO | `RUNNING`, `COMPLETED`, `FAILED`. |

---

# 14. Enum Catalogue

A consolidated view of the values used across the model. The application enforces these; no native DB enums (DB-agnostic rule).

| Enum | Values |
|---|---|
| Master status | `ACTIVE`, `INACTIVE`, `ARCHIVED` |
| Transaction status | `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `POSTED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `PARTIALLY_PAID`, `PAID`, `CANCELLED`, `VOIDED` |
| Session status | `OPEN`, `IN_PROGRESS`, `CLOSING`, `CLOSED`, `RECONCILED` |
| Event/op status | `PENDING`, `DISPATCHED`, `RECEIVED`, `APPLIED`, `REJECTED`, `FAILED`, `DEAD_LETTERED` |
| Branch type | `SHOP`, `WAREHOUSE`, `DEPOT`, `PLANT`, `HQ` |
| Party category | `INDIVIDUAL`, `BUSINESS`, `GOVERNMENT`, `NGO` |
| Item type | `SELLABLE`, `CONSUMABLE`, `BOTH`, `SERVICE` |
| Move type | `GRN`, `SALE`, `RETURN_IN`, `RETURN_OUT`, `DAMAGE`, `ADJUSTMENT`, `TRANSFER_OUT`, `TRANSFER_IN`, `PROD_CONSUME`, `PROD_OUTPUT`, `OPENING` |
| Payment method | `CASH`, `CARD`, `BANK_TRANSFER`, `MOBILE_MONEY`, `CHEQUE`, `VOUCHER`, `STORE_CREDIT` |
| Payment terms | `CASH`, `CREDIT` |
| Debt direction | `RECEIVABLE`, `PAYABLE` |
| Cash account | `TILL`, `CASH_BOX`, `BANK`, `MOBILE_MONEY` |
| Promotion type | `PERCENT_OFF`, `AMOUNT_OFF`, `BOGO`, `BUNDLE`, `STEP_QTY` |
| Conversion / return reason | `DAMAGED`, `EXPIRED`, `WRONG_ITEM`, `OVERSUPPLY`, `BUYER_REMORSE`, `OTHER` |
| Flag scope | `GLOBAL`, `COMPANY`, `BRANCH`, `ROLE`, `USER` |
| Flag type | `RELEASE`, `OPERATIONAL`, `PERMISSION`, `EXPERIMENT` |
| Audit action | `CREATE`, `UPDATE`, `DELETE`, `POST`, `VOID`, `APPROVE`, `LOGIN`, `LOGOUT`, `FLAG_CHANGE`, `OVERRIDE` |

---

# 15. ERD (high-level cross-references)

```
organisation
  └── company
        ├── branch ───────────────────────────┐
        ├── party ── customer / supplier /    │
        │              employee / sales_agent │
        ├── item ── item_group, item_barcode, │
        │              item_supplier          │
        ├── price_list ── price_list_item     │
        ├── vat_group                         │
        ├── promotion ── promotion_item       │
        └── (transactions, scoped to branch ──┘)
              ├── purchase_quotation ── lines
              ├── lpo_order ── lines ──► grn ── lines ──► supplier_invoice
              ├── vendor_return ── lines ──► vendor_credit_note
              ├── sales_quotation ── lines
              ├── sales_invoice ── lines ──► sales_receipt ──► receipt_allocation
              ├── customer_return ── lines ──► customer_credit_note
              ├── packing_list ── lines
              ├── till ── till_session ──► pos_sale ── lines, payments
              ├── sales_list ── lines ──► sales_sheet ── sales + expenses
              ├── production_batch ──► consumption, output
              ├── stock_transfer ── lines
              └── stock_count ── lines

ledgers (append-only, fed by every relevant business transaction)
  ├── stock_move
  ├── debt_entry
  ├── cash_entry
  ├── audit_log     (hash-chained)
  └── domain_event  (outbox)

infrastructure
  ├── client_op           (sync idempotency)
  ├── refresh_token       (auth)
  ├── feature_flag + overrides
  ├── number_sequence     (document numbering)
  └── business_day        (per-branch logical day)
```

---

# 16. Open Modelling Decisions

These are choices that should be confirmed by domain owners before MVP cut. They are not blockers but they constrain how some screens behave.

1. **Should `customer` and `supplier` allow the same `party_id`?** Architecturally yes; operationally rare but real (a supplier who is also a small customer). Confirm.
2. **Cost method per item vs per branch.** Current model keeps both: a global `item.avg_cost` for aggregate reporting and a per-branch `item_branch_balance.avg_cost` for accurate margin per branch. Confirm both are wanted.
3. **Walk-in customer at POS.** Recommend one synthetic `customer` per branch (`WALK-IN-BR-DSM-01`) so all sales link to a real party. Cleaner than NULL FKs.
4. **Negative stock policy.** Schema allows negative balances; rule is application-level via privilege `STOCK.OVERSELL`. Confirm.
5. **Number sequence gaps on rollback.** A transaction that allocates a number then rolls back leaves a gap. Acceptable for retail; confirm.
6. **Per-branch cash accounts.** Recommend one `TILL` account per till session, one `CASH_BOX` per branch, one `BANK` per branch. Confirm whether multi-bank is in scope.
7. **Production co-products.** Schema allows multiple outputs per batch. Confirm any current batch genuinely has more than one output, otherwise simplify.
8. **Sales agent commission storage.** Currently a rate on `sales_agent`. If commissions are tier-based or item-based, a `commission_rule` table is needed — confirm.

---

# 17. Phase 1.1 — Scope additions

The following entities and existing-entity changes are introduced in Phase 1.1 (supermarket operationalisation: sections, weighed items, batches/expiry, production extensions, layby/pre-orders, gift cards, internal consumption, refund-at-till, foreign-currency tender). See [docs/design/PHASE-1.1-ADDITIONS.md](docs/design/PHASE-1.1-ADDITIONS.md) for the locked decisions.

## 17.1 `section` (admin module)

Department within a branch (bakery, butchery, deli, retail floor, dairy, fresh, dry goods, household, electronics, other). Required dimension on `pos_sale`, `till`, `bom`, `production_batch`.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | `section_seq` |
| `branch_id` | BIGINT FK → branch | |
| `code` | VARCHAR(20) | UNIQUE `(branch_id, code)` |
| `name` | VARCHAR(80) | |
| `type` | VARCHAR(20) | `RETAIL_FLOOR`, `BAKERY`, `BUTCHERY`, `DELI`, `FRESH`, `DAIRY`, `DRY_GOODS`, `HOUSEHOLD`, `ELECTRONICS`, `OTHER` |
| `manager_user_id` | BIGINT FK → app_user | nullable |
| `status` | VARCHAR(32) | `ACTIVE`, `INACTIVE` |
| audit cols + version | | |

## 17.2 `currency` (admin module)

ISO 4217 registry. PK is the 3-letter code; no surrogate id.

| Column | Type | Notes |
|---|---|---|
| `code` | VARCHAR(3) PK | UGX, USD, EUR… |
| `name` | VARCHAR(60) | |
| `symbol` | VARCHAR(8) | UGX, $, € |
| `minor_unit_digits` | INT | 0 for UGX/JPY, 2 for USD/EUR |
| `status` | VARCHAR(32) | `ACTIVE`, `INACTIVE` |

## 17.3 `fx_rate` (admin module)

Daily snapshot. Most recent rate with `effective_at ≤ sale time` wins. Manual quote only at MVP; feed-based later.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | `fx_rate_seq` |
| `from_currency` | VARCHAR(3) FK → currency | |
| `to_currency` | VARCHAR(3) FK → currency | |
| `rate` | DECIMAL(20,8) | `qty_in_to = qty_in_from × rate` |
| `effective_at` | TIMESTAMP | |
| `created_by` | BIGINT | |
| `created_at` | TIMESTAMP | |

## 17.4 `till_currency` (admin module)

Foreign currencies a till is allowed to accept. Composite PK.

| Column | Type | Notes |
|---|---|---|
| `till_id` | BIGINT FK → till | composite PK |
| `currency_code` | VARCHAR(3) FK → currency | composite PK |

Functional currency for the company is implicitly accepted on every till (not stored here).

## 17.5 `stock_batch` (stock module)

Per-branch batch row carrying manufacture / expiry date. Created on GRN (when item is batch-tracked) or on `production_output`. Drains via FEFO consumption.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | `stock_batch_seq` |
| `item_id` | BIGINT FK | |
| `branch_id` | BIGINT FK | |
| `batch_no` | VARCHAR(40) | UNIQUE `(branch_id, item_id, batch_no)` |
| `manufactured_at` | DATE | nullable |
| `expiry_at` | DATE | nullable but typical |
| `qty_received` | DECIMAL(18,4) | immutable |
| `qty_on_hand` | DECIMAL(18,4) | drains on consume |
| `cost` | DECIMAL(18,4) | unit cost at receipt |
| `source_doc_type` | VARCHAR(40) | `GRN`, `PRODUCTION_OUTPUT`, `OPENING` |
| `source_doc_id` | BIGINT | |
| `status` | VARCHAR(32) | `ACTIVE`, `EXHAUSTED`, `EXPIRED`, `RECALLED` |
| audit + version | | |

## 17.6 `gift_card` (giftcard module)

Bearer stored-value voucher. Liability ledger separate from `cash_book`.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | `gift_card_seq` |
| `code` | VARCHAR(40) UNIQUE | bearer secret |
| `initial_value` | DECIMAL(18,4) | |
| `current_balance` | DECIMAL(18,4) | derived from `gift_card_txn` |
| `status` | VARCHAR(32) | `ACTIVE`, `FULLY_REDEEMED`, `EXPIRED`, `FROZEN`, `REFUNDED` |
| `issued_at` | TIMESTAMP | |
| `expires_at` | TIMESTAMP | nullable |
| `issued_by_branch_id` | BIGINT FK | |
| `issued_by_user_id` | BIGINT FK | |
| `company_id` | BIGINT FK | |
| audit + version | | |

## 17.7 `gift_card_txn` (giftcard module)

Append-only ledger of gift card balance changes.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | `gift_card_txn_seq` |
| `gift_card_id` | BIGINT FK | |
| `kind` | VARCHAR(20) | `LOAD`, `REDEEM`, `REFUND`, `EXPIRE` |
| `amount` | DECIMAL(18,4) | always positive |
| `balance_after` | DECIMAL(18,4) | post-tx snapshot |
| `ref_doc_type` | VARCHAR(40) | `POS_SALE`, `ADMIN_ADJUSTMENT`, … |
| `ref_doc_id` | BIGINT | |
| `occurred_at` | TIMESTAMP | |
| `by_user_id` | BIGINT FK | |

## 17.8 `customer_order` (orders module)

Layby + pre-order header. Distinct from `sales_invoice` — ownership doesn't transfer until `COLLECTED`.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | `customer_order_seq` |
| `company_id` | BIGINT FK | |
| `branch_id` | BIGINT FK | |
| `section_id` | BIGINT FK | nullable; required for production-tied pre-orders |
| `number` | VARCHAR(40) | per-branch sequence `ORD-BR1-000123` |
| `customer_id` | BIGINT FK | |
| `type` | VARCHAR(20) | `LAYBY`, `PRE_ORDER` |
| `status` | VARCHAR(32) | `DRAFT`, `RESERVED`, `DEPOSIT_PAID`, `PARTIALLY_PAID`, `READY`, `COLLECTED`, `CANCELLED`, `EXPIRED` |
| `reserved_until` | TIMESTAMP | nullable |
| `deposit_required_amount` | DECIMAL(18,4) | |
| `deposit_paid_amount` | DECIMAL(18,4) | |
| `total_amount` | DECIMAL(18,4) | |
| `balance_due` | DECIMAL(18,4) | |
| `notes` | TEXT | |
| audit + version | | |

## 17.9 `customer_order_line` (orders module)

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `customer_order_id` | BIGINT FK | |
| `item_id` | BIGINT FK | |
| `qty` | DECIMAL(18,4) | |
| `unit_price` | DECIMAL(18,4) | snapshot |
| `discount_amount` | DECIMAL(18,4) | |
| `line_total` | DECIMAL(18,4) | |

## 17.10 `customer_order_payment` (orders module)

Deposit / instalment receipts. Each row triggers a `cash_entry` in the `cash` module.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `customer_order_id` | BIGINT FK | |
| `amount` | DECIMAL(18,4) | |
| `method` | VARCHAR(20) | `CASH`, `CARD`, `BANK_TRANSFER`, `MOBILE_MONEY`, `CHEQUE`, `GIFT_CARD` |
| `ref_cash_entry_id` | BIGINT FK | |
| `occurred_at` | TIMESTAMP | |

## 17.11 `production_wastage` (production module)

Category-tagged loss recorded against a batch. Wastage qty does NOT enter stock; it's logged for reporting and variance analysis.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | `production_wastage_seq` |
| `production_batch_id` | BIGINT FK | |
| `item_id` | BIGINT FK | |
| `qty` | DECIMAL(18,4) | |
| `category` | VARCHAR(20) | `BURNT`, `EXPIRED`, `DROPPED`, `SAMPLED`, `DONATED`, `OTHER` |
| `reason` | TEXT | non-empty |
| `recorded_by` | BIGINT | |
| `recorded_at` | TIMESTAMP | |

## 17.12 Changes to existing tables

### `item` (§3.2)
- `+ is_weighed BOOLEAN NOT NULL DEFAULT FALSE`
- `+ weighing_unit VARCHAR(10)` — `KG`, `G`, `L`, `ML`; null if not weighed
- `+ tracks_batches BOOLEAN NOT NULL DEFAULT FALSE`

### `item_barcode` (§3.3)
- `+ barcode_type VARCHAR(20) NOT NULL` — `UPC`, `EAN13`, `EAN8`, `PLU`, `EMBEDDED_WEIGHT`, `EMBEDDED_PRICE`

### `stock_move` (§4.1)
- `+ batch_id BIGINT FK → stock_batch` nullable (set for batch-tracked items)
- `+ section_id BIGINT FK → section` nullable (stamped on production / section-transfer)
- `+ consumption_category VARCHAR(20)` nullable — `CANTEEN`, `DISPLAY`, `SAMPLES`, `DONATION`, `MAINTENANCE`, `OTHER` (required for `INTERNAL_CONSUMPTION` moves)
- `+ authorised_by_user_id BIGINT FK` nullable (required for internal-consumption / oversell / above-threshold adjustment)
- New `move_type` values: `INTERNAL_CONSUMPTION`, `STAFF_PURCHASE`, `EMPLOYEE_GIFT`, `RESERVED` (for layby reservation), `EXPIRY_WRITE_OFF`

### `bom` (§9.1)
- `+ parent_bom_id BIGINT FK → bom` nullable (sub-recipes)
- `+ section_id BIGINT FK → section` required

### `production_batch` (§9.3)
- `+ section_id BIGINT FK → section` required
- `+ lifecycle_state VARCHAR(32)` — `PLANNED`, `IN_PROGRESS`, `OUTPUT_HOT_DISPLAY`, `OUTPUT_COLD_DISPLAY`, `OUTPUT_DISCOUNTED`, `OUTPUT_DONATED`, `OUTPUT_WRITE_OFF`, `CLOSED`

### `production_output` (§9.5)
- `+ is_pack_by_weight BOOLEAN NOT NULL DEFAULT FALSE`
- `+ batch_id BIGINT FK → stock_batch` — production output gets its own batch row

### `grn_line` (§5.6)
- `+ batch_no VARCHAR(40)` nullable
- `+ expiry_at DATE` nullable

### `pos_sale` (§7.3)
- `+ section_id BIGINT FK → section` **REQUIRED**
- `+ kind VARCHAR(20)` — `SALE`, `REFUND`, `NO_SALE`; default `SALE`
- `+ refunded_from_sale_id BIGINT FK → pos_sale` self nullable

### `pos_sale_line` (§7.4)
- `+ section_id BIGINT FK → section` **REQUIRED**
- `+ batch_id BIGINT FK → stock_batch` nullable (FEFO at scan for batch-tracked items)

### `pos_payment` (§7.5)
- `+ tender_currency VARCHAR(3) FK → currency` **REQUIRED**
- `+ tender_amount DECIMAL(18,4)` — value in tender currency
- `+ fx_rate_snapshot DECIMAL(20,8)`
- Existing `amount` column re-interpreted as **functional-currency-converted amount**

### `cash_book` (§10.3)
- PK changes to `(branch_id, account, currency_code, business_date)`
- `+ currency_code VARCHAR(3) FK → currency` (part of PK)

### `cash_entry` (§10.2)
- `+ currency_code VARCHAR(3) FK → currency`
- `+ fx_rate_snapshot DECIMAL(20,8)`

### `till` (§7.1)
- `+ section_id BIGINT FK → section` **REQUIRED**

### `employee` (§2.6)
- `+ default_section_id BIGINT FK → section` nullable
- `+ staff_price_list_id BIGINT FK → price_list` nullable

## 17.13 Policy rules locked

- **Refund-at-till.** Same-day + receipt scanned: cashier up to threshold (company-config, default UGX 50,000); above → manager PIN. Same-day, no receipt: manager PIN always. Past business day: back-office `customer_return` only.
- **Foreign currency.** Functional currency single per company. Foreign tender allowed only at `pos_payment`. Backend stores tender + functional + fx-snapshot. Close-till variance per currency.
- **Weighed items.** EAN-13 starting with `2` is embedded data: PLU bytes 2..7, weight bytes 8..12, check byte 13. POS client parses; backend trusts.
- **Section dimension.** REQUIRED on `pos_sale`, `pos_sale_line`, `till`, `bom`, `production_batch`. OPTIONAL on `stock_move`. NOT on `sales_invoice` / `supplier_invoice`.
- **Expiry / batch.** Item-level opt-in via `item.tracks_batches`. GRN captures `batch_no` + `expiry_at`. Consumption uses FEFO. Expired stock flagged by scheduled job.
- **Internal consumption.** Requires `authorised_by_user_id`, `consumption_category`, and non-empty reason.

## 17.14 New sequences

Add to `db/migration/{mysql,postgres}/V1_3__phase11_sequences.sql`:
`section_seq`, `fx_rate_seq`, `stock_batch_seq`, `gift_card_seq`, `gift_card_txn_seq`, `customer_order_seq`, `customer_order_line_seq`, `customer_order_payment_seq`, `production_batch_seq`, `production_consumption_seq`, `production_output_seq`, `production_wastage_seq`, `bom_seq`, `bom_line_seq`.

---

*End of Data Model. See [PRD.md](PRD.md) and [ARCHITECTURE.md](ARCHITECTURE.md).*
