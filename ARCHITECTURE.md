# Orbix ERP — Next Generation
## Technical Design & Architecture

| Field | Value |
|---|---|
| Document | Architecture v0.1 (draft) |
| Author | Godfrey (with Claude) |
| Date | 2026-05-13 |
| Status | Draft — pending review |
| Companion | [PRD.md](PRD.md), [DATA-MODEL.md](DATA-MODEL.md), [USER-STORIES.md](USER-STORIES.md) |

This document is the technical companion to the PRD. The PRD defines *what* and *why*; this document defines *how*.

---

## 1. High-Level Architecture

```
                      ┌────────────────────────────────┐
                      │       Identity Provider        │
                      │   (in-API user store + JWT)    │
                      └────────────────┬───────────────┘
                                       │
   ┌────────────────────┐    ┌─────────▼─────────┐    ┌────────────────────┐
   │  Web ERP (Angular) │◄──►│   Orbix API       │◄──►│  Mobile WMS        │
   │  Back-office       │    │   (Spring Boot 3) │    │  (Flutter Android) │
   └────────────────────┘    │   Java 21         │    └────────────────────┘
                             │                   │
                             │   REST + JSON     │    ┌────────────────────┐
                             │   Async jobs      │◄──►│  Desktop POS       │
                             │   Event log       │    │  (Flutter Win)     │
                             └─────────┬─────────┘    │  + Local SQLite    │
                                       │              │  + Sync engine     │
                                       ▼              └────────────────────┘
                             ┌───────────────────┐
                             │     MySQL 8       │
                             │  + S3-compatible  │
                             │    object store   │
                             │  + Redis cache    │
                             └───────────────────┘
```

### 1.1 Component summary

| Component | Stack | Hosting |
|---|---|---|
| Orbix API | Spring Boot 3.3, Java 21, Maven, Hibernate 6, Flyway | Linux VM or container |
| Web ERP | Angular 17 (LTS at time of build), TypeScript, Bootstrap 5 | Static hosting (nginx) |
| Desktop POS | Flutter 3.x Desktop (Windows), Dart, Drift (SQLite) | Installed on till hardware |
| Mobile WMS | Flutter 3.x (Android), Dart, Drift (SQLite) | Play Store / sideload APK |
| Database | RDBMS via JPA/Hibernate — **MySQL 8 or PostgreSQL 15+** | Managed or self-hosted; choice deferred per deployment |
| Search index | Meilisearch (self-hosted) | Item / customer / supplier search at POS, WMS, and web |
| Object storage | S3-compatible (MinIO or cloud) | For receipts, exports, attachments |
| Cache / queue | Redis 7 | API rate-limits, refresh-token revocation, lightweight job queue, feature flags |

### 1.2 Why this stack

- **Spring Boot 3 / Java 21** — preserves the team's existing investment, brings native records, sealed types, virtual threads (key for sync endpoints under load), and modern Hibernate. Java 21 is already installed on the dev box.
- **Flutter for desktop + mobile** — single codebase across POS and WMS, identical business logic in Dart, native performance, mature offline story via Drift. Hardware integration (ESC/POS, fiscal, USB scanners, cash drawer) handled via existing Flutter packages and FFI bindings — confirmed viable.
- **Angular for back-office** — the team already writes Angular, the back-office is data-grid-heavy, and Angular Material/CDK fit that workload better than a mobile-first UI kit.
- **DB-agnostic RDBMS** — JPA/Hibernate is the only persistence API; no DB-specific SQL features are used at the application layer. The same build runs on MySQL 8 or PostgreSQL 15+. Migration tooling (Flyway) maintains parallel scripts only when a dialect difference is unavoidable. See §2.3.
- **Meilisearch for item search** — purpose-built, self-hostable, sub-100 ms typo-tolerant search over 50k+ items. Keeps the relational DB free of search-index responsibilities and the choice of DB engine agnostic.
- **Drift + SQLite on clients** — typed, reactive, well-supported in Flutter; fits the offline-first POS need.

## 2. Backend Design

### 2.1 Module structure

The API is a **modular monolith**, not microservices. Module boundaries are enforced by package and by build (ArchUnit tests block cross-module reaches). Splitting later is easy; recombining microservices is not.

```
com.orbix.erp
├── platform/          # cross-cutting: auth, audit, sequences, multi-company
│   ├── security/
│   ├── audit/
│   ├── company/       # Organisation, Company, Branch
│   ├── sequence/      # document numbering
│   ├── events/        # domain event bus + transactional outbox
│   ├── search/        # Meilisearch indexer + query adapter
│   ├── flags/         # feature flags (per company / per branch / per user)
│   └── analytics/     # product-analytics event emitter
├── party/             # Customer, Supplier, Employee, SalesAgent (shared Party root)
├── catalog/           # Item, Group hierarchy, VAT, UoM, Price lists, Promotions
├── stock/             # StockMove (postings), branch stock balance, stock cards
├── procurement/       # Quotation-in, LPO, GRN, Supplier Invoice, Vendor return/CN
├── sales/             # Quotation, SalesInvoice, Receipt, Allocation, Customer return/CN, PackingList
├── pos/               # TillSession, Cart, POS sync, fiscal hooks
├── wms/               # SalesList, SalesSheet, SalesExpense, route, agent settlement
├── production/        # BOM, Batch, CustomProduction, Conversions
├── debt/              # DebtEntry, Allocation, ageing
├── cash/              # CashBook, PettyCash, Float, CashPickup
├── day/               # business date, end-of-day per branch
├── hr/                # Employee, Shift, Biometric enrolment
├── reporting/         # report definitions, exports, scheduling
└── integration/       # fiscal drivers, accounting export, file imports
```

### 2.2 Layering inside each module

Two patterns, applied by module weight:

**Core modules** (sales, stock, procurement, production, debt, cash, pos, wms) use hexagonal layout:
- **api** — REST controllers, request/response DTOs, validation annotations.
- **app** — application services (transactions live here, orchestration only).
- **domain** — entities, value objects, domain services, domain events.
- **infra** — JPA repositories, integrations, adapters.

**Light modules** (day, hr, vat, uom, sequence, flags, audit) use a simpler service+repository layout — 3 files instead of 12 — to avoid ceremony where there is little domain logic.

Universal rules (enforced by ArchUnit):
- Controllers never touch repositories.
- Repositories never return entities outside `app`. DTOs are not entities.
- Modules talk to each other only via published API DTOs or domain events — never by reaching into another module's `domain` or `infra`.

### 2.3 Persistence strategy

- **Flyway** for schema migrations. `ddl-auto=validate` in production — never `update`.
- **Hibernate 6** with explicit DTO projections for read paths (reports, lists). Entities are for writes.
- **Optimistic locking** (`@Version`) on every transactional aggregate.
- **Append-only tables** for postings: `stock_move`, `debt_entry`, `cash_entry`, `audit_log`. No updates; corrections issue a new posting.
- **Soft-deletable masters** use a `status` enum (`ACTIVE`, `INACTIVE`, `ARCHIVED`).

#### DB-agnostic rules
The system must run on MySQL 8 *or* PostgreSQL 15+ from the same build. To hold that line:

- **No dialect-specific SQL** in app code or repositories. JPQL or `CriteriaBuilder` only; native queries banned unless wrapped in a dialect-resolver adapter.
- **No vendor-only features at the app layer**: no PostgreSQL `JSONB` operators, no MySQL `FULLTEXT`, no row-level security, no listen/notify, no Oracle hints. Search lives in Meilisearch (§2.11), not in the DB.
- **Standard column types only**: `VARCHAR`, `TEXT`, `DECIMAL(18,4)`, `BIGINT`, `TIMESTAMP`, `BOOLEAN`. Audit payloads are `TEXT` (JSON serialised by the app, parsed by the app), not `JSONB`/`JSON`.
- **IDs** are `BIGINT` from a Hibernate-managed sequence/table generator (`SEQUENCE` strategy with a table fallback for MySQL), or `BINARY(16)` UUIDs where externally referenced (e.g. `clientOpId`). Never auto-increment `IDENTITY` (Postgres and MySQL differ on transaction semantics).
- **Booleans** stored as `BOOLEAN` (Hibernate maps to `TINYINT(1)` / `BOOLEAN` correctly).
- **Flyway scripts** live in `db/migration/common/` by default. Dialect-specific scripts go in `db/migration/mysql/` or `db/migration/postgres/` only when unavoidable (e.g. index syntax) — and the rule is "fail the build if a script ends up in either subfolder without a `// DIALECT-SPECIFIC:` reason comment".
- **Test matrix**: CI runs the integration test suite against both MySQL 8 and PostgreSQL 15 via Testcontainers on every PR. A green build means both backends work.
- **Connection pool**: HikariCP (DB-neutral). Connection URL and driver are the only environment-specific configuration.

### 2.4 Transactions and consistency

- One transaction per API request unless explicitly chunked.
- A *posting* (stock/debt/cash) and its parent *transaction* (e.g. `SalesInvoice`) write in the same DB transaction. No eventual consistency between them.
- **Cross-module side effects use domain events via the transactional outbox** (see §2.10). The event row is written in the same DB transaction as the business write, then dispatched by a poller. This gives at-least-once delivery with no two-phase commit, on any RDBMS.
- Background work (report generation, email, large exports) goes through a job table + worker, not in-request.

### 2.5 AuthN / AuthZ

- Login → access JWT (15 min, RS256-signed, includes `userId`, `companyId`, `branchId`, role set) + refresh token (30 day, stored hashed in `refresh_token` table, rotation on each use).
- Logout / revoke → refresh token deleted, access JWT JTI added to Redis blacklist until natural expiry.
- Authorisation: Spring Security `@PreAuthorize` on every service method using a custom expression language: `@PreAuthorize("hasPriv('SALES_INVOICE_POST', #branchId)")`.
- Supervisor authorisation flow: a short-lived "authorisation token" issued when a supervisor PINs in, scoped to one action.

### 2.6 Multi-company / multi-branch

- Tables that hold transactions carry `company_id` + `branch_id`.
- A `RequestContext` filter sets the current user, current company, current branch from JWT + request header (branch can be switched via header without re-login).
- Repository queries that touch transactional tables go through a base interface that injects the company/branch predicate automatically.
- Master data is scoped per company by default but can be marked group-shared (e.g. supplier master might be group-wide).

### 2.7 Audit

- Every transactional write emits an `AuditEvent(actor, action, entity, entityId, before, after, branchId, at)`.
- Audit events are written by an aspect, not by the calling code, so they cannot be forgotten.
- Audit log is append-only and indexed by `(entity, entityId, at)` and `(actor, at)`.

### 2.8 API conventions

- Base path `/api/v1`. Resource-oriented URLs (`/sales-invoices`, `/grns/{id}/post`).
- All money fields use plain numbers with documented scale (4 decimal places).
- All timestamps are ISO 8601 UTC; clients convert for display.
- Pagination via `?page=&size=&sort=`; lists return `{content, page, totalElements}`.
- Errors follow RFC 7807 (Problem Details).
- API documented with springdoc-openapi (replacing legacy springfox), regenerated on each build.

### 2.9 Sync endpoint contract (POS & WMS)

```
POST /api/v1/sync/push
  body: { clientId, lastAckId, ops: [SyncOp...] }
  resp: { ackedClientOpIds, conflicts: [...], serverOps: [...] }

GET  /api/v1/sync/pull?clientId=...&since=...
  resp: { ops: [...], cursor: "..." }
```

- Every client operation has a `clientOpId` (UUID v7) — backend writes are idempotent keyed by it.
- Conflict policy:
  - Stock balance: **server wins** (server-authoritative).
  - Sale: **never conflicts** — client-generated receipt numbers are namespaced (`TILL-3-20260513-00027`); the server accepts whatever the client sends as long as the till session, items, and totals are valid.
  - Master data edits: **last-write-wins on a field basis**, with audit.

### 2.10 Domain Events (Transactional Outbox)

Cross-module communication and external integrations go through domain events, dispatched from a transactional outbox so the business write and the event publication are atomic.

#### 2.10.1 Mechanism
- Every business write that other modules might care about (e.g. `SalesInvoicePosted`, `GrnPosted`, `DebtAllocated`, `BatchProductionCompleted`, `TillSessionClosed`) calls `events.publish(event)`.
- `publish()` inserts a row into `domain_event(id, type, aggregate_type, aggregate_id, payload_json, occurred_at, branch_id, company_id, status='PENDING')` — in the same transaction as the business write.
- A poller (Spring scheduled job, leader-elected via Redis) reads `PENDING` events in `occurred_at` order, dispatches them to in-process listeners and external subscribers, and marks them `DISPATCHED`.
- Listeners are **idempotent** — they receive the same event ID more than once during retries and must tolerate it.

#### 2.10.2 Why this and not Spring events directly
- Spring application events are in-memory only; a crash between the business write and the listener loses the side effect.
- The outbox pattern survives crashes, supports retries, lets you replay history (rebuild a search index, re-emit to a new subscriber), and exposes a natural integration point — external systems read from `domain_event` (or a webhook fed by it) instead of polling business tables.
- DB-agnostic by design — no message broker required at MVP.

#### 2.10.3 Event schema
- Events carry a **stable contract**, versioned (`type` includes a version: `SalesInvoicePosted.v1`).
- Payload contains the IDs and the *minimum* data a subscriber needs to act without calling back. Big objects stay out — listeners fetch what they need.
- All events are documented in `docs/events/` alongside their schemas.

#### 2.10.4 Subscribers
- **In-process** listeners react synchronously to the dispatcher (search index update, balance recomputation hint, audit projection).
- **External**: webhook delivery service reads from the outbox, posts to subscriber URLs with HMAC signing and exponential backoff. Used for accounting export, BI ingestion, third-party integrations.
- **Replay**: an admin endpoint can re-dispatch a range of events to a named subscriber — useful when a new integration goes live or after a bug fix.

#### 2.10.5 Retention
- Dispatched events kept for 90 days hot, then archived to object storage with a manifest. Long enough for debugging, short enough that the outbox table stays small.

### 2.11 Search Infrastructure

A purpose-built search index serves item, customer, supplier, and invoice lookups across all clients.

#### 2.11.1 Engine
- **Meilisearch** (Apache 2.0, self-hostable single binary, sub-100 ms typo-tolerant search, drop-in REST API). Alternative: Typesense — same shape, same fit.
- Runs as one container alongside the API. No cluster needed at MVP scale; can move to a managed search service later without code changes (the search adapter abstracts the engine).

#### 2.11.2 Indexes
| Index | Source | Primary search fields | Filters |
|---|---|---|---|
| `items` | `item` + active prices + barcodes | `code`, `name`, `barcodes`, `supplier_codes` | `company_id`, `branch_id` (via balances), `status`, `item_group_id` |
| `customers` | `party` + `customer` | `name`, `code`, `phone`, `tin` | `company_id`, `sales_agent_id`, `status` |
| `suppliers` | `party` + `supplier` | `name`, `code`, `phone`, `tin` | `company_id`, `status` |
| `invoices` | `sales_invoice` header | `number`, `customer_name` | `company_id`, `branch_id`, `date_range`, `status` |

#### 2.11.3 Indexing pipeline
- The `platform.search` module subscribes to relevant domain events (§2.10): `ItemCreated`, `ItemUpdated`, `PriceChanged`, `CustomerCreated`, …
- On each event, the indexer fetches the current projection and upserts the document. Eventually consistent, typical lag <1 s.
- A full reindex job exists for cold starts, search-engine swaps, or recovery.

#### 2.11.4 Client behaviour
- **Web ERP** calls the API; the API proxies to Meilisearch (search keys never exposed to the browser, scoped per company/branch).
- **POS** and **WMS** keep their own local SQLite-backed search for offline mode (simple `LIKE` plus n-gram for typo tolerance, scoped to the data already on the device — typically a few thousand active items per branch, not 50k). Online searches go via the API to Meilisearch for full coverage.

### 2.12 Feature Flags

Feature flags are a first-class platform capability — used for progressive rollout, per-tenant configuration, branch experiments, and kill switches.

#### 2.12.1 Scopes
A flag is keyed by `(flag_code, scope)` where scope can be:
- **global** — on/off everywhere,
- **company** — `(code, company_id)`,
- **branch** — `(code, branch_id)`,
- **role** — `(code, role_id)`,
- **user** — `(code, user_id)`.

Resolution order: user > role > branch > company > global > default.

#### 2.12.2 Flag types
- **Release flag** — short-lived, hides incomplete work. Removed once the feature is GA.
- **Operational flag** — long-lived kill switch (e.g. `pos.fiscal_printer.enabled` per branch).
- **Permission flag** — gates a feature for paying tiers / opt-in beta.
- **Experiment flag** — A/B percentage rollout, scoped per company.

Each flag declares its type and an expected `expires_at`. Release flags older than 90 days raise a CI warning ("clean up or convert").

#### 2.12.3 Storage and delivery
- Flag definitions in `feature_flag` table; per-scope overrides in `feature_flag_override`.
- Resolved flags cached in Redis with a 30-second TTL and a pub/sub invalidation on change.
- Backend exposes `GET /api/v1/flags?context=...` returning the resolved set for the calling user — clients fetch on login and on long-poll updates.
- Flags are **never** evaluated by string-matching in the codebase — only via a typed helper: `if (flags.enabled(POS_LOYALTY, ctx)) { … }`. A central registry lists every known flag so dead flags are visible.

#### 2.12.4 Audit
- Every flag change writes to the audit log with actor, scope, before, after.
- Flag state at any point in time can be reconstructed from the log (useful for incident review: "was this flag on for branch X at 14:02?").

## 3. Client Designs

### 3.1 Desktop POS (Flutter)

```
lib/
├── app/                  # router, theme, app entry
├── features/
│   ├── auth/
│   ├── till_session/     # open, close, X/Z reports
│   ├── cart/             # build, hold, recall
│   ├── payment/          # tender screens
│   ├── supervisor/       # PIN flows
│   └── settings/
├── data/
│   ├── local/            # Drift schema + DAOs
│   ├── remote/           # API client
│   └── sync/             # outbox, push/pull engine, conflict resolution
└── core/                 # money, formatting, hardware (printer, scanner, drawer)
```

- **Local DB**: Drift on SQLite. Tables for `items`, `prices`, `customers`, `till_sessions`, `sales`, `sale_lines`, `payments`, `outbox`, `meta`.
- **Outbox pattern**: every user action that mutates state writes to `outbox` first; a background isolate pushes outbox to backend; on success, op is marked acked.
- **Initial sync**: download the active price list, customer list (up to N for performance), item master, promotions.
- **Hardware abstraction**: a `PrinterService` interface with implementations for ESC/POS over USB/Serial, Windows raw printer driver, and a noop for dev. Fiscal printer drivers behind the same interface.
- **Crash safety**: cart auto-saves every 2 seconds; on relaunch, in-progress cart is restored.
- **Cashier auth**: username/password by default; optional fingerprint via Windows Hello SDK or USB fingerprint reader (where present).

### 3.2 Mobile WMS (Flutter)

```
lib/
├── app/
├── features/
│   ├── auth/
│   ├── route/            # daily route, customer list, stock-on-van
│   ├── visit/            # at-customer flow: cart, payment, receipt
│   ├── expense/
│   ├── sales_sheet/      # end-of-day submission
│   └── settings/
├── data/
│   ├── local/            # Drift
│   ├── remote/           # API client
│   └── sync/
└── core/                 # money, formatting, printing (Bluetooth thermal), camera (barcodes)
```

- Same offline architecture as POS — Drift local store, outbox push, idempotent server.
- Stock-on-van is a **client-side computed balance**: van loaded qty − sales since load. Server reconciles on sheet submission.
- Bluetooth thermal printer support for in-van receipts.
- Camera-based barcode scan (mobile_scanner package).
- Optional GPS capture per visit (Phase 2) — stored opportunistically, not blocking.

### 3.3 Web ERP (Angular)

- One canonical app — no `-x` fork. The diverging features in legacy `orbix-erp-x` are merged in or dropped per decision (see PRD §12 risks).
- Module per business area, lazy-loaded.
- Shared `ui-kit` library: data grid (ag-grid or similar), form controls, currency display, date picker, multi-select. **All ui-kit components ship with WCAG 2.1 AA compliance built in** (keyboard navigation, ARIA roles, focus management, colour-contrast tokens) so feature teams cannot accidentally regress accessibility.
- HTTP layer wraps every call with JWT, branch header, error toast, and spinner integration.
- Reports module: a generic table → PDF/Excel renderer plus per-report parameter screens.
- Accessibility test gate in CI: axe-core run against representative routes; build fails on new violations of WCAG AA serious/critical rules.

## 4. Data Model (key entities)

This section names the high-priority entities. **Full attribute lists, datatypes, examples, rationale, and relationships are in [DATA-MODEL.md](DATA-MODEL.md).** Treat this section as a table of contents.

### 4.1 Platform
- `organisation(id, name, ...)` — top of multi-company tree.
- `company(id, organisation_id, name, tin, vrn, ...)` — legal entity.
- `branch(id, company_id, code, name, address, time_zone, ...)`.
- `app_user(id, username, password_hash, status, default_branch_id, ...)`.
- `role(id, name, scope)`.
- `privilege(id, code, description)`.
- `role_privilege(role_id, privilege_id)`.
- `user_role(user_id, role_id, company_id)` — roles scoped per company.
- `audit_log(id, actor_id, action, entity_type, entity_id, before, after, branch_id, at)` — append-only.
- `number_sequence(branch_id, doc_type, current)`.

### 4.2 Party (shared base)
- `party(id, type, name, tin, vrn, status, ...)` where `type` ∈ {CUSTOMER, SUPPLIER, EMPLOYEE, AGENT}. A party can hold multiple roles.
- `customer(party_id, credit_limit, price_tier, sales_agent_id, default_branch_id, ...)`.
- `supplier(party_id, payment_terms, credit_limit, ...)`.
- `employee(party_id, role, branch_id, hire_date, status, ...)`.
- `sales_agent(party_id, route_code, ...)`.
- `customer_address`, `supplier_address`.

### 4.3 Catalog
- `item_group(id, parent_id, level, name)` — single tree, level ∈ {DEPARTMENT, CLASS, SUBCLASS, CATEGORY, SUBCATEGORY}.
- `item(id, code, name, type, item_group_id, uom_id, vat_group_id, status, ...)` where `type` ∈ {SELLABLE, CONSUMABLE, BOTH}.
- `item_barcode(item_id, barcode)`.
- `item_supplier(item_id, supplier_id, supplier_code, last_cost)`.
- `price_list(id, name, type)` and `price_list_item(price_list_id, item_id, price)`.
- `vat_group(id, code, rate)`.
- `uom(id, code, name)` and `uom_conversion(from_uom_id, to_uom_id, factor)`.
- `promotion(id, type, start, end, ...)` and `promotion_item(promotion_id, item_id, params)`.

### 4.4 Stock
- `item_branch_balance(item_id, branch_id, qty_on_hand, qty_reserved, avg_cost, last_cost)`.
- `stock_move(id, item_id, branch_id, qty, direction, cost, ref_type, ref_id, at, ...)` — append-only ledger.
- `stock_count(id, branch_id, status, ...)` and `stock_count_line(count_id, item_id, system_qty, counted_qty)`.

### 4.5 Procurement
- `lpo_order(id, supplier_id, branch_id, status, ...)`, `lpo_order_line(...)`.
- `grn(id, lpo_order_id, supplier_id, branch_id, status, ...)`, `grn_line(...)`.
- `supplier_invoice(id, grn_id, supplier_id, total, ...)`.
- `vendor_return(id, supplier_id, branch_id, ...)` and `vendor_credit_note(...)`.
- `purchase_quotation(...)` and lines.

### 4.6 Sales
- `sales_quotation(...)`.
- `sales_invoice(id, customer_id, branch_id, agent_id, total, status, ...)`, `sales_invoice_line(...)`.
- `sales_receipt(id, customer_id, branch_id, total, method, status, ...)`.
- `receipt_allocation(receipt_id, debt_id, amount)`.
- `customer_return(...)`, `customer_credit_note(...)`.
- `packing_list(...)` and lines.

### 4.7 POS
- `till(id, branch_id, code, name)`.
- `till_session(id, till_id, opened_by, opened_at, opening_float, closed_at, closing_cash, closing_variance, status)`.
- `pos_sale(id, till_session_id, customer_id, total, status, client_op_id, ...)`, `pos_sale_line(...)`.
- `pos_payment(sale_id, method, amount)`.
- `cash_pickup(till_session_id, amount, by, at)`, `petty_cash(till_session_id, amount, reason, by, at)`.

### 4.8 WMS
- `sales_list(id, agent_id, branch_id, date, status)`, `sales_list_line(item_id, qty_loaded, qty_returned)`.
- `sales_sheet(id, sales_list_id, agent_id, date, status, declared_cash, system_cash, variance)`.
- `sales_sheet_sale(sheet_id, customer_id, total, ...)` and lines.
- `sales_expense(sheet_id, type, amount, note)`.

### 4.9 Production
- `bom(id, item_id, output_qty)`, `bom_line(bom_id, material_item_id, qty)`.
- `production_batch(id, item_id, branch_id, planned_qty, actual_qty, reject_qty, status, ...)`.
- `production_consumption(batch_id, material_item_id, planned_qty, actual_qty)`.

### 4.10 Debt & Cash
- `debt_entry(id, party_id, direction, amount, balance, ref_type, ref_id, branch_id, at)` — direction ∈ {RECEIVABLE, PAYABLE}, append-only.
- `cash_entry(id, branch_id, account, direction, amount, ref_type, ref_id, at)`.
- `cash_book(id, branch_id, date, opening, closing)`.

### 4.11 Day
- `business_day(branch_id, date, status)` — only one open per branch.

## 5. Sync Protocol (detailed)

### 5.1 Client outbox
Each client op:
```json
{
  "clientOpId": "01HX7Q...",
  "type": "POS_SALE_POSTED",
  "occurredAt": "2026-05-13T08:42:00Z",
  "payload": { /* full sale */ }
}
```
- Ops are durable on disk before the user sees "Saved".
- Outbox flushed to `/sync/push` on a background timer (e.g. every 5 s when online) and on network reconnect.

### 5.2 Server idempotency
- `client_op(client_op_id PK, server_resource_id, status, first_seen_at)` table.
- Handler checks `client_op` first; if present, returns the prior result without re-applying.

### 5.3 Pull
- Each client tracks a `lastCursor` per topic (`items`, `prices`, `customers`, `customer_balances`).
- `/sync/pull` returns deltas + a new cursor. Reasonable batch limits.

### 5.4 Clock policy
- Client clocks are not trusted for business dates. Server stamps `serverAt`. Receipts show client time too for the user.
- Till sessions are anchored to a `business_day(branch_id, date)` decided by the server.

## 6. Reports

- Reports defined as parameterised SQL views or stored queries, **read-only**, run against a read replica (when present) to avoid impacting transactions.
- Each report has a `definition` (SQL or query plan), `parameters`, and `output_columns`.
- Rendering pipeline: definition → params → DTO list → PDF/Excel/CSV via a single rendering service.
- Long reports go to the job queue and notify the user when ready.

## 7. Deployment & Operations

### 7.1 Topology
- Backend: 1 API node + 1 DB primary at MVP; horizontal scale by adding API nodes behind a load balancer when needed (stateless).
- DB: MySQL 8 primary + 1 read replica (used by reports and pull endpoints).
- Object storage: MinIO (self-host) or S3 (cloud).
- Redis: 1 node at MVP; for sessions, blacklist, rate-limit, and the job queue.

### 7.2 Environments
- `local` (Docker Compose), `staging`, `prod`. Same images promoted across.
- Config via env vars / Spring profiles. Secrets via env / mounted files, not bundled.

### 7.3 CI/CD
- Single mono-repo (recommended) or four repos (api / web / pos / wms). Mono-repo simplifies cross-cutting changes (e.g. API contract + client wrapper in one PR).
- **Trunk-based development**: short-lived branches (≤ 2 days), feature flags hide work-in-progress on trunk, no long-lived feature branches.
- Per-component pipelines: lint → unit → integration → security scans → build → image → push.
- API integration tests run against **both MySQL 8 and PostgreSQL 15** containers (Testcontainers) on every PR — green build means both backends pass.
- Database migrations applied by API container on start (Flyway), with a pre-flight check that aborts startup if the migration set diverges from the embedded checksum.

#### Security scanning (mandatory gates)
- **Dependency scan**: Dependabot + OWASP Dependency-Check on every PR. Fail on high CVEs.
- **SAST**: Semgrep with a curated ruleset for Java, TypeScript, Dart; SonarQube for code quality and security hotspots.
- **Secret scan**: gitleaks on every PR and as a pre-commit hook. The build fails if any committed file matches secret patterns (caught the legacy `jwt.secret=javainuse` instantly when run retroactively).
- **Container scan**: Trivy on every image built. Block on high/critical vulnerabilities in the base image or installed packages.
- **License scan**: surface non-permissive licences (GPL, AGPL) on new dependencies before merge.

#### Deployment cadence
- Backend: continuous deployment to staging on merge to `main`; production promoted manually with one-click rollback (previous image is always kept warm).
- Web ERP: same pipeline as backend.
- POS / WMS: see §7.6 (OTA Updates).

### 7.4 Observability
- Logs: JSON, shipped to a log store (Loki, Elastic, or cloud equivalent).
- Metrics: Micrometer → Prometheus.
- Tracing: OpenTelemetry, sampled.
- Dashboards: API latency, error rate, sync queue depth (per client), business-day status per branch, top-N slow queries.

### 7.5 Backups
- DB: daily full + binlog/WAL continuous.
- Object store: versioning enabled.
- Restore drill monthly; tested into staging from a real backup.

### 7.6 OTA Updates (POS & WMS)

POS tills and WMS phones must update without a site visit. The update path is a first-class subsystem, not an afterthought.

#### 7.6.1 Channels
- **stable** — default channel for production tills/devices.
- **beta** — opted-in branches running a release ahead of stable.
- **canary** — 1-2 internal tills running the next build for smoke-testing real workflows.

Each device is pinned to a channel, set centrally by an admin in Web ERP.

#### 7.6.2 POS (Windows desktop)
- App is shipped as an MSIX/Squirrel-style installer; auto-updater service checks the update endpoint on launch and once per business day.
- Update endpoint serves a signed manifest (`version`, `channel`, `min_supported_api`, `download_url`, `sha256`, `rollback_to`).
- Update is downloaded in background, applied on next idle moment with no in-progress till session.
- **Never updates during an open till session** — the running cashier is protected from a mid-shift restart.
- Rollback: if launch crashes ≥ 2 times within 5 minutes of an update, the updater reverts to the previous installed version automatically and reports the failure.

#### 7.6.3 WMS (Android)
- Distributed via Play Store (internal app sharing or closed track at MVP), with optional sideload APK channel for organisations that don't use Play.
- In-app version check on login: if the device is below `min_supported_api`, the user is blocked from working offline until they update — prevents data being captured on a build that the server cannot parse.

#### 7.6.4 Version compatibility
- API exposes `GET /api/v1/meta/min-client-versions` returning the lowest supported POS / WMS build per channel.
- Backend API is **versioned and additive only** within a major version — clients up to N-2 minor releases are guaranteed to work. Breaking changes force a major version bump and a coordinated rollout.

#### 7.6.5 Telemetry on rollout
- Each client reports its version + channel on every API call (`X-Client-Version` header).
- Admin dashboard shows: devices per version, % on latest, crash rate per version, update success rate. A bad rollout is visible within minutes, not days.

### 7.7 Product Analytics

Operational observability (§7.4) covers "is the system healthy". Product analytics covers "is the product being used and where do users struggle". Distinct system, distinct retention.

- **Engine**: self-hosted PostHog (open source, owns the data, no third-party data exit).
- **Event taxonomy**: a short, curated list of high-value events — *not* automatic tracking of every click.
  - `pos.sale.completed`, `pos.cart.held`, `pos.discount.applied`, `pos.void.requested`, `pos.till.opened`, `pos.till.closed`
  - `web.invoice.created`, `web.grn.posted`, `web.report.run`, `web.feature_flag.toggled`
  - `wms.route.started`, `wms.visit.completed`, `wms.sheet.submitted`
- **Properties** carry safe metadata only (company_id, branch_id, role, version, durations) — never PII, never amounts, never customer/supplier names. The privacy boundary is the difference between *analytics* and the *audit log*.
- **Use cases**: feature adoption (is anyone using "mass manager"?), funnel completion (what % of held carts get recalled and completed?), prioritisation (which reports are run, which gather dust?), regression detection (did p95 cart-completion time get worse after the last release?).
- Emission is non-blocking; failure to send analytics never affects business operations.

## 8. Security Hardening Checklist

- [ ] All secrets externalised; CI fails if any committed file matches a secret pattern (gitleaks pre-commit hook + CI gate).
- [ ] Dependency scan (Dependabot + OWASP Dependency-Check) green on every PR; high CVEs block merge.
- [ ] SAST (Semgrep + SonarQube) green on every PR; security hotspots reviewed.
- [ ] Container scan (Trivy) green on every image; high/critical CVEs block deploy.
- [ ] JWT signing key is RSA, rotated annually, key ID (`kid`) in token header; both old and new key accepted during rotation window.
- [ ] Access tokens ≤ 15 min; refresh tokens hashed at rest, rotated on each use, single-use.
- [ ] CORS allow-list is explicit (no `*` in production); per environment.
- [ ] Rate-limit on `/login`, `/sync/push`, `/sync/pull` per IP **and** per user.
- [ ] Brute-force lockout after N failed logins per username, exponentially decayed.
- [ ] Bcrypt cost ≥ 12, periodic re-hash on login if cost upgraded.
- [ ] Input validation on every DTO (jakarta-validation); explicit DTOs, no entity binding from request.
- [ ] SQL only through JPA / parameterised queries; ban string concat in repositories (ArchUnit rule).
- [ ] HTTP security headers configured (CSP, X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Permissions-Policy, HSTS) for web ERP.
- [ ] Audit log retention ≥ 7 years (configurable per deployment).
- [ ] Audit log integrity: hash-chained rows so tampering is detectable.
- [ ] PII export and deletion endpoint for compliance requests (GDPR / similar).
- [ ] Annual third-party penetration test before each major version GA.

## 9. Testing Strategy

- **Backend unit tests** — pure domain logic (calculators, allocators, BOM expanders): aim ≥ 80% on domain packages.
- **Backend integration tests** — Testcontainers running **both MySQL 8 and PostgreSQL 15**, full Spring context, WebTestClient: cover every controller happy path + one failure. Build must be green on both engines.
- **Backend contract tests** — OpenAPI spec is the source of truth; clients depend on a generated TypeScript / Dart client. Schema-breaking changes detected at PR time.
- **Web ERP** — component tests (Jest/Karma), a small Playwright suite for critical flows (login → invoice → receipt → allocation), and **axe-core accessibility checks** wired into the same Playwright suite (build fails on new WCAG AA violations).
- **POS / WMS** — widget tests for screens, integration tests on Drift schemas, golden tests for receipt rendering, an end-to-end offline scenario test on each release, and a sync-conflict test suite that scripts network outages and asserts no data loss.
- **Migration tests** — every Flyway script is applied against a snapshot of production schema in CI, on both DB engines.
- **Performance tests** — k6 scripts hitting `/sync/push` at 50 concurrent clients before each release; Lighthouse perf budgets on web ERP critical routes.
- **Event-contract tests** — domain event payloads (§2.10) are pinned with golden snapshots; a schema change requires a new event version (`SalesInvoicePosted.v2`), never an in-place edit.

## 10. Coding Standards

- **Java**: Google Java Style (auto-formatted), `final` everywhere reasonable, records for DTOs, no Lombok on domain entities (Lombok stays for DTOs / builders only).
- **Dart**: official Dart style, `analysis_options.yaml` strict, `package:lints/recommended.yaml`.
- **TypeScript**: strict mode on, ESLint + Prettier, no `any` without justification comment.
- **Commit / PR**: Conventional Commits, one logical change per PR, mandatory code review.
- **Architectural rules** (ArchUnit on backend): controllers may not call repositories; modules may not import each other's `domain`/`infra` packages — only `api` (DTOs) and Spring events.

## 11. Compliance Considerations

The system is designed to operate in retail contexts that touch sensitive financial data. Compliance posture is set per deployment.

### 11.1 PCI-DSS (card-data scope)
- **Default stance**: the POS **does not store, process, or transmit primary account numbers (PANs)**. Card payments are handled by a separate certified payment terminal (PIN-pad) that returns only a tokenised reference + last-4 + auth code. The POS records the reference, not the card.
- This keeps the system in PCI-DSS **SAQ B-IP / SAQ C** scope (substantially reduced), not full Level 1 — orders of magnitude cheaper to operate.
- A deployment that wants to integrate card processing in-app must explicitly opt in and accept the full PCI-DSS scope expansion (network segmentation, vulnerability scans, dedicated logging retention). Default is opt-out.

### 11.2 Data residency
- Each deployment runs on infrastructure inside the customer's regulatory boundary (single-tenant by design — see PRD §2.2 NG3).
- Backup target is configurable per deployment and defaults to the same region as the primary.
- Cross-border data egress (for support, telemetry, etc.) is opt-in and documented.

### 11.3 PII handling
- A `PII` annotation marks entity fields and DTO fields that contain personal data (customer name, phone, email, TIN).
- Logs are scrubbed of `PII`-annotated fields by an aspect; never log a customer object directly.
- A PII subject-access export endpoint produces all data held about a named party.
- A PII deletion endpoint anonymises a party (replaces name/phone/email with `[ERASED-{id}]`) while preserving transactional history — financial law usually requires keeping the transactions, just not the personal identity.

### 11.4 Fiscal compliance
- Many of the regions Orbix operates in mandate fiscal printers and government receipt signing. This is implemented as a per-region driver behind a single `FiscalDevice` interface. Adding a new region = adding a driver, not changing the application.
- Receipts unsigned by a fiscal device in a region that requires them are flagged and reported daily.

### 11.5 Audit retention
- Default audit retention is 7 years (covers most tax / corporate audit horizons). Configurable per deployment for stricter regimes.
- Audit log is append-only and hash-chained — every row carries a hash of the previous row. Tampering detection is operational, not aspirational.

## 12. AI / LLM (Phase 3 Considerations)

These are deferred — not MVP — but the architecture should not preclude them. Listing here so the design is consciously forward-compatible.

### 12.1 Candidate features
- **Natural-language report queries** — "show me sales of beverages last week in branch 3 vs same week last year". A constrained NL → safe query layer over the same report definitions in §6.
- **Vendor invoice OCR + auto-GRN draft** — photograph or upload a supplier invoice; LLM extracts line items, matches against the LPO, and prepares a draft GRN for the storekeeper to confirm.
- **Anomaly detection** — debt ageing patterns out of trend, sudden stock variance, unusual void rates per cashier. Surfaces a daily "exceptions to review" feed.
- **Cashier copilot at POS** — suggest cross-sells based on cart composition; answer "what's the price of X?" via voice.
- **Field-agent summary** — at end of route, agent dictates notes; LLM produces a structured visit summary attached to the sales sheet.

### 12.2 Architectural posture
- **Pluggable LLM provider**: an `AiProvider` interface with implementations for hosted models (OpenAI/Anthropic), self-hosted (Llama/Qwen via vLLM), or none. The default in a fresh deployment is "disabled". Customers choose their provider on data-sovereignty grounds.
- **No customer or financial data leaves the deployment without explicit consent.** When an external provider is used, payloads are redacted to the minimum needed (e.g. line items + totals, never customer names).
- **All AI suggestions are advisory** — a human posts the GRN, accepts the suggestion, or confirms the report. The audit log records both the suggestion and the human decision.
- **Cost and rate limits** are operational flags (§2.12) per company.

### 12.3 Data foundation that makes this possible
The work already in MVP makes AI features cheap to add later:
- The domain event log (§2.10) is the natural input stream for anomaly detection.
- The Meilisearch index (§2.11) gives semantic + lexical lookup that the NL query layer reuses.
- Audit log (§11.5) gives the "what did the human do after the AI suggested X" feedback loop.

## 13. Migration Strategy (detail)

1. **Schema map**: produce a table-by-table mapping from legacy → new (one document, reviewed).
2. **ETL tool**: a standalone Spring Boot Java module reads legacy DB, writes new DB via the new API where possible (so all invariants are enforced) and direct insert where required (history).
3. **Dry runs** on a copy of production, comparing totals (stock value, AR, AP, sales-to-date) before and after.
4. **Cut-over per branch**: legacy DB read-only on the cut-over morning for that branch, ETL run, new system goes live by mid-morning.
5. **Dual-read 30 days**: legacy reports still available; daily totals compared automatically; discrepancies flagged.
6. **Decommission** after 90 days of clean operation.

## 14. Repository Layout (proposed)

```
ERPCLEAN/
├── PRD.md
├── ARCHITECTURE.md
├── DATA-MODEL.md
├── USER-STORIES.md
├── docs/
│   ├── decisions/             # ADRs (Architecture Decision Records)
│   ├── events/                # Domain event schemas + change notes
│   ├── data-model/            # ERD images, DDL exports
│   └── runbooks/              # ops procedures (restore, cut-over, etc.)
├── orbix-engine-api/          # Spring Boot 3, Maven; Java root package com.orbix.engine
├── orbix-engine-web/          # Angular 17 — back-office Web ERP
├── orbix-engine-pos/          # Flutter Desktop — Point of Sale
├── orbix-engine-wms/          # Flutter Mobile — Field Sales
├── orbix-engine-contracts/    # OpenAPI 3.1 spec + generated TS / Dart clients
└── orbix-engine-infra/
    ├── docker/                # compose for local
    └── deploy/                # IaC for staging / prod
```

## 15. Glossary

| Term | Meaning |
|---|---|
| **Item** | Anything that moves through stock — sellable, consumable, or both. Replaces legacy `Product` + `Material`. |
| **Posting** | An immutable ledger entry: `stock_move`, `debt_entry`, `cash_entry`, `audit_log`. The source of truth for balances. |
| **Transaction** | A user-facing document (LPO, GRN, Sales Invoice) that, when posted, emits postings. |
| **Branch** | A physical location where stock and tills live. Smallest unit transactions are scoped to. |
| **Company** | A legal entity. Owns branches, customers, suppliers, items. |
| **Organisation** | The top of the hierarchy. Owns one or more companies. Used for consolidated reporting. |
| **Sync Op** | A client-originated, idempotent operation pushed to the backend, identified by `clientOpId`. |
| **Business Day** | A logical day per branch, opened and closed explicitly. Drives posting dates. |
| **Domain Event** | An immutable record that something business-meaningful happened (e.g. `SalesInvoicePosted.v1`). Emitted via the transactional outbox; consumed by in-process listeners and external subscribers. |
| **Outbox** | The `domain_event` table, written in the same DB transaction as the business write, dispatched asynchronously by a poller. Guarantees at-least-once delivery without a message broker. |
| **Feature Flag** | A named runtime switch resolved per user / role / branch / company / global. Used for rollout, kill switches, and per-tenant configuration. |
| **PII** | Personally Identifiable Information. Fields tagged with the `@PII` annotation are scrubbed from logs and surfaced by the subject-access / deletion endpoints. |

---

# Phase 1.1 — Architectural notes

The Phase 1.1 scope additions ([PRD §14](PRD.md), [DATA-MODEL §17](DATA-MODEL.md), [docs/design/PHASE-1.1-ADDITIONS.md](docs/design/PHASE-1.1-ADDITIONS.md)) introduce four new modules and several architectural patterns worth calling out.

## Section as a transactional dimension

A new `section` entity sits between `branch` and the transactional aggregates. **Required** on `pos_sale`, `pos_sale_line`, `till`, `bom`, and `production_batch`. **Optional** on `stock_move` (stamped only when the move crosses sections — bakery output to retail floor, or section-to-section transfer). **Not on** `sales_invoice` or `supplier_invoice` — back-office docs are HQ-level.

Section P&L is a primary report dimension. Reports group by `(company_id, branch_id, section_id)`.

## Multi-currency tender, single functional currency

Each company has one functional currency (its books). Foreign currency is accepted only at the POS tender step:

- `pos_payment` carries both the tender currency + amount AND the back-converted functional amount, plus the `fx_rate_snapshot` used.
- `cash_entry` and `cash_book` are scoped per currency: composite PK `(branch_id, account, currency_code, business_date)`.
- A till declares its accepted currencies via `till_currency`.
- Close-till variance is computed **per currency** — the till may be short USD but long UGX.

The functional currency is implicit; no `till_currency` row needed for it. Most-recent `fx_rate` with `effective_at ≤ sale time` wins at tender time.

## Batch tracking with FEFO

Per-item opt-in via `item.tracks_batches`. Each receipt (GRN line) or production output creates a `stock_batch` row carrying `manufactured_at`, `expiry_at`, `qty_on_hand`, and `cost`. Consumption follows **first-expired-first-out**:

1. POS / sales / production-consume picks the batch with the earliest non-null `expiry_at` (FIFO among same-expiry).
2. Cost flows from the consumed batch, not the moving average.
3. A scheduled job flags `ACTIVE` batches with `expiry_at < now` for write-off; the actual write-off remains a manual `stock_move` of type `EXPIRY_WRITE_OFF` (so accountants approve it).

Items without `tracks_batches = true` continue to use the moving-average cost model on `item_branch_balance`.

## Liability ledgers (gift cards) sit beside the cash ledger

`gift_card` + `gift_card_txn` are a self-contained ledger. They are **not** in `cash_book` because the balance is a liability, not cash. The POS treats gift cards as a tender method (`pos_payment.method = GIFT_CARD`), but redemptions do not create a `cash_entry`; they create a `gift_card_txn` of kind `REDEEM`.

The same pattern is reserved for future loyalty-points and store-credit ledgers — each gets its own balance and ledger; cash never absorbs liabilities.

## Reservation as a stock direction

Layby and pre-order reservations write a `stock_move` with `direction = RESERVED` (a new direction alongside IN / OUT). Reserved stock is **on hand** but **not available** — `item_branch_balance` carries both `qty_on_hand` and `qty_reserved`; oversell checks use `qty_on_hand − qty_reserved`. When an order is collected, the RESERVED move is reversed and a SALE move is posted in the same transaction.

## Production lifecycle as discrete event states

A `production_batch` transitions through eight states: `PLANNED` → `IN_PROGRESS` → `OUTPUT_HOT_DISPLAY` → `OUTPUT_COLD_DISPLAY` → `OUTPUT_DISCOUNTED` → `OUTPUT_DONATED` / `OUTPUT_WRITE_OFF` → `CLOSED`. Each transition emits `ProductionLifecycleAdvanced.v1`. The terminal donated / write-off transitions emit compensating `stock_move` rows; reporting picks up the wastage breakdown.

## Module count grows: 10 → 14

Adds: `admin`, `production` (re-introduced), `orders`, `giftcard`.

The cross-cutting modules `common` and `auth` remain the only "infrastructure" peers other modules may depend on directly (per `ModuleBoundaryTest`). The 12 business + admin modules talk to each other only via published DTOs/enums or domain events.

---

*End of Architecture. See [PRD.md](PRD.md) for the product requirements.*
