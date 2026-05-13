# Orbix ERP — Next Generation
## Product Requirements Document (PRD)

| Field | Value |
|---|---|
| Document | PRD v0.1 (draft) |
| Author | Godfrey (with Claude) |
| Date | 2026-05-13 |
| Status | Draft — pending review |
| Companion | [ARCHITECTURE.md](ARCHITECTURE.md), [DATA-MODEL.md](DATA-MODEL.md), [USER-STORIES.md](USER-STORIES.md) |

---

## 1. Background and Motivation

An ERP system is currently operating in the field for retail and distribution clients. It was built incrementally over years and is composed of four components: a Spring Boot 2.2 backend, two Angular 13 web back-office variants that have diverged (`orbix-erp`, `orbix-erp-x`), an Ionic mobile sales app, and a VB.NET WinForms POS. The business workflow is proven — sales, inventory, production, debt management, and field sales all work — but the codebase has accumulated structural debt:

- Two web front-ends drifting from each other with no clear canonical version.
- Hard-coded credentials, weak JWT secret, and CORS allowing all origins.
- An ageing VB.NET POS targeting .NET Framework 4.5, with publish paths bound to a developer machine.
- No tests, no migrations, `ddl-auto=update`, Eclipse workspace metadata committed.
- 94 JPA entities with many overlapping concepts (e.g. `Material` vs `Product`, `Lpo` vs `Quotation` vs `Purchase`) that need rationalisation.

The goal of this project is **not** to replace the business model — it works — but to rebuild the system on a clean foundation with better design, sharper data model, and a delivery architecture that supports growth.

## 2. Goals and Non-Goals

### 2.1 Goals
- **G1.** Preserve every business process currently in production. Nothing in the field workflow should regress.
- **G2.** One canonical codebase per client (web ERP, desktop POS, mobile WMS) — no forks.
- **G3.** Multi-company / multi-branch support inside a single deployment, with consolidated and per-entity reporting.
- **G4.** Offline-capable desktop POS — a till must never stop selling because the network blipped.
- **G5.** Tighter security baseline: rotated secrets, RBAC enforced at the API, audit log on financial events.
- **G6.** A data model that distinguishes clearly between *master data*, *transactions*, and *postings*.
- **G7.** Predictable delivery in phases (MVP first), so the business is not blocked while the rewrite proceeds.

### 2.2 Non-Goals (this release)
- **NG1.** General-ledger / full double-entry accounting. The system tracks debt, payments, cash, and sales but does not aspire to replace an accounting package. Export to accounting is in scope.
- **NG2.** Multi-currency. Single base currency per deployment.
- **NG3.** Multi-tenant SaaS. Each customer gets their own deployment (multi-company *within* it is in scope).
- **NG4.** Built-in e-commerce / customer-facing storefront.
- **NG5.** HR payroll calculations. Employee register and shifts are in scope; payroll math is not.
- **NG6.** Manufacturing MRP / capacity planning. Recipe-based production (existing) stays; demand planning does not.

## 3. Personas and Roles

| Persona | Primary Surface | Core Tasks |
|---|---|---|
| **Cashier / Till Operator** | Desktop POS | Open till, take sales, hold/recall carts, accept payments, void/discount with supervisor PIN, pickups, close till. |
| **Floor Supervisor** | Desktop POS + Web ERP | Authorise voids/discounts, manage cash floats, end-of-shift checks, oversee returns. |
| **Storekeeper / Inventory Clerk** | Web ERP | Receive GRNs, count stock, post damages, manage conversions (material↔product), set reorder points. |
| **Merchandiser / Buyer** | Web ERP | Raise LPOs to suppliers, manage quotations, supplier credit notes, supplier debt. |
| **Salesperson (Counter)** | Web ERP | Raise sales invoices, take receipts, allocate receipts to invoices, manage customer credit. |
| **Field Sales Agent** | Mobile WMS | Plan route, load van, capture sales at customer site, record expenses, settle sales sheet at day-end. |
| **Production Operator** | Web ERP | Run batch and custom production orders, post material consumption, post finished output. |
| **Accountant / Finance** | Web ERP | Reconcile cash books, monitor debt, export to accounting, run financial reports. |
| **Branch Manager** | Web ERP | View branch performance, approve write-offs, request inter-branch transfers, sign off end-of-day. |
| **System Administrator** | Web ERP | Manage users, roles, privileges, biometrics, till assignments, company profile, VAT groups. |
| **HQ / Group Owner** | Web ERP | Consolidated reporting across companies and branches, master-data governance. |

## 4. System Surfaces

The new system has three client surfaces backed by one API:

1. **Web ERP** (back-office) — Angular SPA. Used for all configuration, master data, reporting, financial workflows, and counter sales.
2. **Desktop POS** (till) — Flutter Desktop application running on Windows tills. Offline-capable with local SQLite + bidirectional sync.
3. **Mobile WMS** (field) — Flutter mobile application for Android (iOS optional later) for field sales agents.

The single backend exposes a versioned REST API; clients consume it via JWT bearer tokens issued at login.

## 5. Functional Modules

Each module lists what it must do. **MVP** tags mark Phase-1 scope (see §10).

### 5.1 Identity, Access, and Audit
- Users with username/password and optional biometric (fingerprint) auth on POS. **MVP**
- Role-based access control with hierarchical privileges (Privilege → Role → User). **MVP**
- Per-company role assignments (a user can be cashier in branch A, supervisor in branch B).
- Session management: JWT access token (short) + refresh token, server-side revocation list. **MVP**
- Audit log on every create/update/delete of financial or stock-affecting entities (Envers replacement). **MVP**
- Supervisor PIN flow for in-app authorisations (void, over-discount, negative-stock sale).

### 5.2 Company, Branch, and Reference Data
- Company profile: name, TIN, VRN, addresses, phones, bank accounts, document notes per template. **MVP**
- Multi-company group: an *Organisation* holds one or more *Companies*; each Company holds one or more *Branches*. Users, products, customers, suppliers, tills, sales agents all belong to a Company; transactions belong to a Branch.
- VAT groups (rate %, name, exemption rules). **MVP**
- UoM (units of measure) and pack/case conversions.
- Number sequences per document type, per branch (LPO-BR1-000123).

### 5.3 Item Master (Products and Materials)
- Single `Item` aggregate replaces the legacy `Product` vs `Material` split. Items have a `type` (sellable / consumable / both) and an optional BOM. **MVP**
- Four-level grouping hierarchy (Department → Class → SubClass → Category → SubCategory) — preserved from legacy, modelled cleanly. **MVP**
- Multi-barcode per item, multi-supplier, supplier item codes.
- Pricing: cost (moving average), wholesale, retail, agent, customer-specific overrides.
- Reorder min/max per branch, bin location.
- Promotions / product offers: bundle, BOGO, scheduled discount.
- Mass manager (CSV-style edit grid) for bulk updates. **MVP**
- Price change history.

### 5.4 Stock and Movements
- Stock on hand per item per branch, with last-cost and moving-average cost. **MVP**
- Stock card (movement ledger) per item per branch. **MVP**
- Movement types: GRN, sales, returns, damages, conversions, transfers, adjustments, production consume, production output, opening balance.
- Negative-stock rules: blocked by default, overridable by supervisor PIN.
- Stock count / cycle count with variance posting.
- Inter-branch transfer (issue → in-transit → receive).

### 5.5 Procurement (Merchandiser inbound)
- Supplier master with contact, payment terms, tax info, credit limit. **MVP**
- LPO (purchase order) → GRN (goods receipt) → Supplier Invoice → Payment lifecycle. **MVP**
- Quotation request to multiple suppliers, compare and convert to LPO.
- Return to vendor → Vendor credit note.
- Supplier debt ledger and ageing.
- Supplier stock status report (what we hold of their items).

### 5.6 Sales (counter and back-office)
- Quotation → Sales Invoice → Sales Receipt → Debt Allocation lifecycle. **MVP**
- Customer master with credit limit, agent, price tier, default branch. **MVP**
- Customer claim → Customer credit note → re-issue / refund.
- Packing list (deliver against an invoice in shipments).
- Sales agent assignments (which agent owns which customer).
- Web POS (the legacy thin counter sale) — kept as a back-office quick-sale screen. **MVP**
- Bill reprint with full audit trail (who, when, original vs reprint copy number).

### 5.7 Point of Sale (Desktop)
- Till session: open with float → sales → cash pickups → petty cash → close with declared cash and variance. **MVP**
- Mixed-tender payments: cash, card, mobile money, voucher, store credit. **MVP**
- Hold / recall cart. **MVP**
- Discount and void with supervisor authorisation. **MVP**
- Receipts to thermal printer (ESC/POS) and fiscal printer integration (where present).
- Loyalty / customer lookup at till (optional — Phase 2).
- Offline mode: full cart, receipt, and till close work offline. **MVP**
- End-of-day Z-report and X-report.

### 5.8 Mobile WMS / Field Sales
- Sales agent logs in, downloads route (customers, products, prices). **MVP**
- Capture sale at customer location, print or email invoice. **MVP**
- Record sales expenses (fuel, tolls). **MVP**
- Settle the daily sales sheet at end of route — declared cash vs system, with variance. **MVP**
- Customer creation/edit from the field (with supervisor approval at HQ).
- Online debt-receipt capture against existing customer debts.
- Map view of customer route, optional GPS check-in (Phase 2).

### 5.9 Debt Management
- Sales debt: every credit invoice creates a debt. Receipts allocate against one or many debts. **MVP**
- Supplier debt: every GRN creates a payable; payments allocate against one or many.
- Debt tracker / ageing buckets (0-30, 31-60, 61-90, 90+). **MVP**
- Debt write-off with supervisor + accountant approval.
- Statements per customer / per supplier.

### 5.10 Production
- BOM (Bill of Materials) per product: list of materials × quantities + standard yield.
- Batch production: pick a product + quantity, system computes material requirements, operator confirms actual consumption and output.
- Custom production: ad-hoc material → product transformation without a BOM.
- Material-to-material, product-to-product, product-to-material conversions (existing).
- Production report (planned vs actual, yield variance).

### 5.11 Day Management
- "Business date" concept: end-of-day moves the date forward; once closed, no posting to that date without override. **MVP**
- End-of-day per branch: validate till closures, post pending GRNs, generate Z-reports.
- Custom date override (history corrections) — supervisor only, fully audited.

### 5.12 Human Resources (light)
- Employee register: personal info, role, branch, hire date, status. **MVP**
- Shift assignment (linked to till sessions).
- Biometric enrolment (POS supervisor / cashier fingerprint).
- *Out of scope*: payroll calculation, leave balances, performance reviews.

### 5.13 Reporting
- All legacy reports preserved: Daily Sales/Purchases/Summary, Z-history, Product Listing, Supply Sales (+ extended), Fast/Slow Moving, Stock Card, Negative Stock, LPO/GRN, Production, Material Usage, Material vs Production, Sales & Purchases Summary. **MVP** for the daily/Z reports.
- Filters: date range, branch, company, agent, supplier, customer.
- Export: PDF, Excel, CSV.
- Saved report definitions per user.
- Scheduled email delivery (Phase 2).
- Group-level consolidated reports across companies (HQ persona).

### 5.14 Integrations
- Fiscal printer (regional tax compliance) — driver per region.
- Mobile money gateways (Phase 2).
- Bank reconciliation file imports (CSV / OFX) — Phase 2.
- Accounting export (CSV / API) — chart-of-accounts mapping per company.

## 6. End-to-End Process Flows

These are the canonical "happy paths" the system must support. They are written so they survive a re-implementation; each one will be backed by acceptance tests.

### 6.1 Open Till → Cashier Sale → Close Till (POS)
1. Cashier logs in to POS with username + password (or fingerprint).
2. Cashier chooses an assigned till and enters opening float; system opens a `TillSession`.
3. POS syncs latest item master, prices, and active promotions (full or delta).
4. Sales loop: scan item → cart → repeat → press Pay → mixed tender → print receipt. Each sale lives in `local.sales` immediately.
5. Cash pickups, petty-cash payouts, voids, and discounts go through supervisor PIN where required.
6. At close: cashier counts cash, declares, system computes variance vs expected, supervisor signs off, `TillSession` closes and Z-report prints.
7. POS pushes any unsynced sales to backend (idempotent). Backend posts stock and debt.

### 6.2 LPO → GRN → Stock & Payable
1. Merchandiser raises LPO for Supplier S, branch B, items + quantities + prices.
2. LPO is approved per policy (auto for small, manager for large).
3. Goods arrive, storekeeper raises GRN against the LPO (partial allowed).
4. On GRN post: stock on hand increases at branch B with cost; supplier payable created; LPO marks fulfilled (or partial).
5. Supplier invoice can be attached; payment(s) allocate against it.

### 6.3 Field Sale Day (WMS)
1. Agent logs into mobile, syncs route + price list + customer balances.
2. Agent loads van: a `SalesList` is created (planned outbound stock).
3. At each stop: pick customer, build cart, capture payment or credit, print/email receipt. Works offline.
4. Mid-route: agent may record `SalesExpense` (fuel, etc.).
5. End of route: agent submits `SalesSheet` summarising sales + expenses + remaining stock; declared cash collected.
6. Back-office reviews and approves the sheet; on approval, stock movements, customer debts, and cash posting are committed.

### 6.4 Batch Production
1. Production planner creates a batch order: Product P, qty Q, branch B.
2. System computes BOM-derived material reservation.
3. Operator starts batch; records actual material consumption.
4. Operator completes batch; declares actual finished qty (+ rejects).
5. On commit: materials decremented, finished product incremented, yield variance posted to report.

### 6.5 Customer Sale on Credit → Receipt → Allocation
1. Salesperson raises a Sales Invoice for Customer C on credit (subject to credit-limit check).
2. Stock decrements; a `Debt` record is opened.
3. Days later, Receipt is captured (cash/cheque/transfer).
4. Allocation screen lets user apply the receipt across one or many open debts; ageing recomputes.

### 6.6 End of Day (per branch)
1. Branch manager invokes End of Day.
2. System checks: all till sessions closed, no unposted GRNs, no orphan production, no unsubmitted sales sheets.
3. Z-report assembled for the branch; business date advances; new day opens.

## 7. Cross-cutting Requirements

### 7.1 Security
- All passwords stored as bcrypt (cost ≥ 12).
- JWT access tokens ≤ 15 min; refresh tokens stored hashed, server-side revocation.
- TLS required in production; HSTS on web ERP.
- Secrets via environment variables / sealed config — never in source.
- Role + privilege check on **every** endpoint, not just by URL.
- Audit log immutable (append-only) for: user/role changes, price changes, voids, discounts above threshold, write-offs, custom-date use, manual stock adjustments.

### 7.2 Reliability and Offline
- POS must continue selling for ≥ 8 hours offline without degradation.
- Sync conflicts resolved with documented rules: stock is server-authoritative; receipt numbers are client-prefixed (`TILL-3-20260513-00027`) so they never collide.
- Backend target uptime: 99.5%. RPO 5 min, RTO 1 hour.

### 7.3 Performance
- Item search at POS: P95 < 80 ms on a 50k-item catalogue (local).
- Backend p95 for a transactional POST < 250 ms under 50 concurrent users.
- Reports up to 100k rows render to PDF/Excel in < 30 s.

### 7.4 Internationalisation
- Date/number formats locale-aware.
- UI string externalisation from day 1 (single language at launch, multi-language ready).
- Receipt and invoice templates configurable per company.

### 7.5 Observability
- Structured JSON logging with correlation IDs across client→backend.
- Metrics: API latency, error rate, sync queue depth (POS), DB connection pool.
- A "system health" page in Web ERP showing branch sync status and pending posts.

### 7.6 Backups
- Nightly full DB backup + binlog/WAL streaming.
- Backups restorable test executed monthly (operational requirement, documented).

### 7.7 Accessibility
- Web ERP targets **WCAG 2.1 AA** compliance across all primary flows.
- All shared UI components are accessible by construction (keyboard navigation, ARIA roles, focus management, colour-contrast tokens).
- Automated accessibility checks (axe-core) run in CI; new violations of WCAG AA serious/critical rules block merge.
- POS and WMS: keyboard / scanner / large-touch-target friendly by default. Screen-reader support on POS is a Phase-2 commitment.

### 7.8 Compliance
- **Card data**: by default the POS does not store, process, or transmit PANs — payments are tokenised via certified terminals. This keeps deployments in reduced PCI-DSS scope. In-app card processing is opt-in per deployment.
- **Data residency**: each deployment runs inside the customer's regulatory boundary; backups stay in-region unless explicitly configured otherwise.
- **PII handling**: subject-access export and deletion endpoints exist for every party. Deletion anonymises personal identity while preserving transactional history (financial law typically requires retention).
- **Fiscal compliance**: per-region fiscal-printer integration is pluggable. Unsigned receipts in a region that requires signing are flagged and reported.
- **Audit retention**: 7 years default, configurable. Audit log is append-only and hash-chained so tampering is detectable.

### 7.9 Product Analytics
- Self-hosted analytics (PostHog) captures a curated set of product-usage events — feature adoption, funnel completion, time-to-action.
- Distinct from operational telemetry (§7.5) and from the audit log (§7.1). Carries safe metadata only — no PII, no money amounts, no party names.
- Drives prioritisation: which features get used, which reports gather dust, where users drop off.

### 7.10 Feature Flags
- Every release-in-progress feature ships behind a flag, defaulted off; flipped on per company / branch / role / user as confidence grows.
- Long-lived operational flags (e.g. fiscal printing on/off per branch, mobile-money provider on/off per company) are the canonical way to express per-tenant configuration.
- All flag toggles are audited.
- Release flags carry an expiry; flags older than 90 days raise a build warning ("clean up or promote to operational").

## 8. Data Model Principles

Full ER design is in [ARCHITECTURE.md §4](ARCHITECTURE.md). Principles here:

- **Master data vs Transactions vs Postings.** Master = `Item`, `Customer`, `Supplier`. Transactions = `LpoOrder`, `Grn`, `SalesInvoice`, `Receipt` — they capture intent and are editable until posted. Postings = `StockMove`, `DebtEntry`, `CashEntry` — immutable, append-only, the source of truth for balances.
- **One `Item`, not `Product` + `Material`.** A single table with `type` and BOM relationships. Eliminates the duplicate hierarchy (`Department/Class/...` once, not twice).
- **Branches own transactions.** Every transactional row carries `company_id` and `branch_id`. Reports filter trivially.
- **Soft delete via `status` field**, not row deletion. Audit needs the history.
- **Money is stored as `DECIMAL(18,4)`** at rest; UI rounds for display.
- **Number sequences** are issued by the backend, never the client (except offline POS which uses a guaranteed-unique client prefix).

## 9. Migration from the Legacy System

- A one-way ETL extracts from the legacy MySQL `erp_db_test` schema and loads the clean schema.
- Mapping decisions documented per table (e.g. `Product` + `Material` → `Item`; `Lpo` → `LpoOrder`; legacy `Receipt` → `SalesReceipt` + `CashEntry`).
- Open balances per item/customer/supplier are loaded as `OpeningBalance` postings dated the cut-over date.
- The legacy system remains read-only for 90 days after cut-over for verification.
- Cut-over is per *branch*, not big-bang — one branch goes live, stabilises, next branch follows.

## 10. Phased Delivery

### Phase 1 — MVP (target: launch one pilot branch)
Everything tagged **MVP** above. In one sentence: a cashier can sell, a back-office user can manage items / suppliers / customers / invoices / GRNs, a field agent can run a route, debts are tracked, and end-of-day works.

### Phase 2 — Hardening and breadth
- Production module (BOMs, batches, custom production, conversions).
- Returns flows (customer returns, return-to-vendor, both with credit notes).
- Promotions / product offers.
- Mass managers (bulk edit grids) for items, suppliers, customers.
- Scheduled report delivery.
- Loyalty at POS.

### Phase 3 — Group, integrations, and intelligence
- Multi-company consolidation reports.
- Inter-branch transfers.
- Accounting export (driven by the domain event log — see Architecture §2.10).
- Mobile money / payment gateway integrations.
- Fiscal printer driver matrix per region.
- iOS WMS build.
- **AI / LLM-assisted features** (opt-in, pluggable provider):
  - Natural-language report queries over the existing report definitions.
  - Vendor invoice OCR producing draft GRNs for storekeeper confirmation.
  - Anomaly detection on debt ageing, stock variance, void rates per cashier.
  - Field-agent end-of-route summary from dictated notes.
  - All AI output is advisory; humans confirm and the audit log records both suggestion and decision.

## 11. Success Metrics

- **Adoption:** pilot branch operates 30 consecutive days on the new system with zero rollback events.
- **Reliability:** ≤ 1 P1 incident per month after stabilisation; POS offline-to-online sync failure rate < 0.1%.
- **Data quality:** post-migration variance vs legacy on stock value < 0.5%, on outstanding debt < 0.1%.
- **Cycle time:** raising an LPO, receiving a GRN, and posting stock takes < 50% of the clicks of the legacy system (measured on a defined script).
- **Code health:** unit + integration test coverage on backend ≥ 70%; zero hard-coded secrets; one canonical web ERP build, not two.

## 12. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Field agents reject Flutter UX vs current familiar Ionic flow | Med | High | Co-design with two agents during MVP; mirror current screen flow before optimising. |
| Offline POS sync edge cases (clock skew, partial pushes) | High | High | Idempotency keys on every push; server-side replay-safe handlers; ship `sync-diagnose` tooling. |
| Data migration loses historical detail | Med | High | Run dual-write for 30 days at pilot; compare daily totals; legacy stays read-only for 90 days. |
| BOM math (production yields, conversions) doesn't match legacy quirks the operators rely on | High | Med | Spend a week shadowing production before redesigning; preserve legacy yield rules as configuration. |
| Scope creep ("can it also do payroll / accounting / e-commerce") | High | Med | Non-goals explicitly listed (§2.2); add new asks to a parking lot, not the current plan. |
| Single canonical web ERP — but `orbix-erp-x` may have features `orbix-erp` lacks | Med | Med | Diff the 70 divergent files, catalogue what `-x` adds, decide per-feature in/out before MVP scope freezes. |

## 13. Open Questions

1. Which region/regulatory regimes must the fiscal printer integration cover at launch? Affects Phase-1 vs Phase-3 placement of fiscal compliance.
2. What is the actual concurrency target — peak tills/users at the largest current branch? Drives DB sizing and rate-limit defaults.
3. Is there an existing accounting package that must be exported to (QuickBooks, Tally, Sage)? Determines the shape of the export schema and the first domain-event subscriber to ship.
4. Do field agents have reliable Android device specs (RAM, Android version)? Floor sets minimum supported version for the WMS app.
5. Should suppliers and customers share a `Party` table (they often overlap — a supplier can also be a customer)? Recommend yes; needs confirmation. *(Provisionally answered yes in Architecture §4.2.)*
6. Pricing tiers — how many price lists per item are needed in practice? Three (retail/wholesale/agent) covers the legacy model; confirm before locking.
7. Are returns ever processed at POS, or always at back-office? Affects POS scope.
8. Do branches operate in different time zones? Affects business-date logic.
9. Will the POS ever need to take card payments in-app rather than via a separate terminal? If yes, deployment enters full PCI-DSS scope — different operational baseline.
10. What is the target hosting model for each customer — self-hosted on-prem, customer's cloud account, or our managed cloud? Affects backup, OTA, and observability infrastructure decisions.
11. Are there customers in jurisdictions with explicit data-residency laws (e.g. country-X data must remain in country X)? Affects deployment topology.
12. Which release channels do branches map to (stable / beta / canary) and who decides? Operational policy, not a build-time decision.

---

*End of PRD. See [ARCHITECTURE.md](ARCHITECTURE.md) for the technical design.*
