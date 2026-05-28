# Slice F — Dashboard drill-through gap audit

Tile-by-tile + alert-by-alert diff of the dashboard's current
drill-through behaviour against the Slice F locked contract
(`docs/design/slice-f-reports-plan.md §3`). Mirrors the shape of
`slice-e1-stock-gap-audit.md` and `slice-c-sales-gap-audit.md`.

Backend-engineer + frontend-engineer drive implementation from this;
PM uses it to scope.

## Source files audited

- `orbix-engine-web/src/app/features/dashboard/dashboard.component.ts`
  (KPI grid lines 109-131; alerts loop 165-188; signal definitions
  352-359; routerLink wiring 384-400).
- `orbix-engine-web/src/app/features/dashboard/dashboard.service.ts`
  (4 live calls at 32-81; `DASHBOARD_LIVE` flags at 85-93).
- Destination components: `stock/balances.component.ts`,
  `sales/invoices.component.ts`, `sales/receipts.component.ts`,
  `procurement/lpos.component.ts`, `procurement/procurement.component.ts`,
  `debt/debt.component.ts`, `reports/reports.component.ts`.
- Backend list endpoints: `LpoOrderController`, `SalesInvoiceController`,
  `StockController`, `SalesAggregateReportController`,
  `SalesReportController`, `StockReportController`.

---

## Section 1 — KPI tiles (5 tiles, all live)

### KPI 1.A — "Today's sales"

- **What it counts**: `dailySummary.sales.grandTotal` for the active
  branch + business date
  (`dashboard.service.ts:35-40` → `GET /reports/sales-summary`).
- **Current `routerLink`**: **none**. The KPI tile is an inert card,
  not a link (`dashboard.component.ts:113-128`, no `[routerLink]` on
  the wrapper).
- **Destination would be**: `/reports/sales-daily?branchId={id}&businessDate={iso}`
  feeding `SalesReportController#dailySales` — a per-document list of
  every invoice + POS sale that contributed.
- **Subset match: NO** (no destination at all today).
- **Backend filter needed**: none — endpoint already exists at
  `SalesReportController#dailySales` (signature
  `(branchId, businessDate) → List<DailySalesRowDto>`).
- **Frontend changes**: (a) add `[routerLink]` + `queryParams` to the
  KPI wrapper at `dashboard.component.ts:113`; (b) **new route**
  `/reports/sales-daily` mounted via `reports.routes.ts`; (c) **new
  component** `sales-daily.component.ts` that reads `branchId` +
  `businessDate` from `ActivatedRoute.queryParamMap` and renders a
  table of `DailySalesRowDto[]`.
- **RBAC**: `SalesReportController` has no `@PreAuthorize`
  (`SalesReportController.java:25-28` — open to any authenticated
  caller). Slice F leaves this open — matches the pattern for the
  other sales / statement report endpoints. (Latent gap; not Slice F's
  fight.)
- **Effort**: **M** (one new route + one new component + KPI wiring).

### KPI 1.B — "Stock alerts"

- **What it counts**: Items at or below reorder min for the active
  branch (`dashboard.service.ts:43-47` — frontend-derived over
  `GET /balances`).
- **Current `routerLink`**: **none** on the KPI tile.
- **Destination would be**: `/stock/balances?belowReorderOnly=true`.
- **Subset match: PARTIAL** — the destination component already has
  the toggle (`balances.component.ts:54-58`) but it defaults to **off**.
  User must find and tick it after landing.
- **Backend filter needed**: optional — frontend filter works today
  since `GET /balances` returns the whole branch. Adding
  `belowReorderOnly=true` as a backend filter is a nice-to-have but
  not load-bearing.
- **Frontend changes**: (a) add `routerLink` + `queryParams` to the
  KPI wrapper; (b) `balances.component.ts` reads `belowReorderOnly`
  from `ActivatedRoute.queryParamMap` on `ngOnInit` and pre-checks
  the `lowOnly` signal (`balances.component.ts:149-150`); (c)
  bidirectional URL sync when user toggles the checkbox
  (`Router.navigate(['/stock/balances'], { queryParams: { belowReorderOnly: this.lowOnly }, queryParamsHandling: 'merge' })`).
- **RBAC**: `StockController` has no `@PreAuthorize` today
  (`StockController.java:21-23` — Slice E1 GAP 5.A). Slice F closes
  this by pinning to `STOCK.COUNT` (see §6.3 of plan and ALERT 2.A).
- **Effort**: **S** (FE-only; backend pin folded into ALERT 2.A).

### KPI 1.C — "Negative stock"

- **What it counts**: Items in negative on-hand
  (`dashboard.service.ts:55-57`, Slice E1 endpoint
  `GET /reports/stock-negative`).
- **Current `routerLink`**: **none** on the KPI tile.
- **Destination would be**: `/stock/balances?negativeOnly=true`.
- **Subset match: NO** — `balances.component.ts` has **no "negative
  only" toggle today**. The component only has a "below reorder only"
  toggle (`balances.component.ts:54-58`). User clicking the KPI
  lands on a list of all balances and has no UI to filter to the
  3 negative ones.
- **Backend filter needed**: YES — new `negativeOnly=true` param on
  `GET /balances` (or alternatively, point the drill-through at
  `GET /reports/stock-negative` and render that list in a
  balances-table shell). **Recommendation: extend `GET /balances`** so
  the destination is the same component as KPI 1.B — keeps the user's
  mental model "drill into stock balances" stable. Plan §6.3.
- **Frontend changes**: (a) add KPI tile routerLink; (b) add a
  second checkbox "negative only" on `balances.component.ts`
  alongside the existing "below reorder only"; (c) read both
  query params on init; (d) service-side change in
  `stock.service.ts#listBalances` to accept the two flags
  and pass them as `HttpParams`.
- **RBAC**: same as 1.B — pin `StockController` to `STOCK.COUNT`
  in this slice. Negative-stock visibility is already gated by the
  existing tile (component sets `negativeStockPermissionDenied` on
  403); applying the same perm to the list endpoint is consistent.
- **Effort**: **M** (BE param + FE toggle + FE wiring).

### KPI 1.D — "Open invoices"

- **What it counts**: Count of POSTED + PARTIALLY_PAID with
  `totalAmount > paidAmount` for branch / company-wide
  (`SalesInvoiceRepository#countOpenForBranch`).
- **Current `routerLink`**: **none** on the KPI tile.
- **Destination would be**: `/sales/invoices?status=OPEN`.
- **Subset match: NO** — `invoices.component.ts` lists **all
  invoices** ordered by id desc (`SalesInvoiceServiceImpl#list` at
  `SalesInvoiceServiceImpl.java:279-286` — no status filter).
  User clicking the KPI sees 50 invoices including DRAFT, VOIDED,
  CANCELLED, PAID — none of which the "open" count was about.
- **Backend filter needed**: YES — `status` query param on
  `GET /sales-invoices`. The backend recognises a bucket alias
  `OPEN` (POSTED + PARTIALLY_PAID with outstanding > 0) and
  `OVERDUE` (OPEN + due_date < today), plus raw
  `SalesInvoiceStatus` values for direct filtering. Two new JPQL
  queries `findOpenForBranch(companyId, branchId, pageable)` +
  `findOverdueForBranch(companyId, branchId, today, pageable)` on
  `SalesInvoiceRepository`. Indexes already exist from Slice C
  GAP 1.B (`ix_sales_invoice_branch_status` +
  `ix_sales_invoice_branch_due`). Plan §6.2.
- **Frontend changes**: (a) KPI routerLink + queryParams; (b) status
  filter dropdown on `invoices.component.ts` with options
  `ALL`, `OPEN`, `OVERDUE`, `DRAFT`, `POSTED`, `PARTIALLY_PAID`,
  `PAID`, `VOIDED`, `CANCELLED`; (c) reads `status` from
  `ActivatedRoute.queryParamMap` on init; (d) URL sync on
  dropdown change; (e) `SalesService#listInvoices(branchId, status,
  page, size)` adds the status arg.
- **RBAC**: `SalesInvoiceController` is class-level
  `@PreAuthorize("hasAuthority('SALES.MANAGE_INVOICE')")` —
  unchanged. Sales-clerk persona holds the perm (test-users.ts:188-191).
- **Effort**: **M** (BE repo + service + controller; FE dropdown +
  wiring).

### KPI 1.E — "AR outstanding"

- **What it counts**: `arSummary.arOutstanding` — sum of
  `totalAmount - paidAmount` on POSTED + PARTIALLY_PAID invoices
  (`SalesInvoiceRepository#sumOutstandingForBranch`).
- **Current `routerLink`**: **none** on the KPI tile.
- **Destination would be**: `/sales/invoices?status=OPEN&sort=outstanding,desc`
  (same destination as 1.D, but sorted to put the biggest balances
  first — answers "which customers do I need to chase").
- **Subset match: NO** — same gap as 1.D + no `outstanding,desc`
  sort option on the backend list today.
- **Backend filter needed**: same as 1.D for `status=OPEN`. The
  `sort=outstanding,desc` is a `Pageable` sort over a computed
  field — JPQL `order by (s.totalAmount - s.paidAmount) desc`.
  Recommendation: **add it as a service-side sort branch**, not a
  raw `Sort` argument, because Pageable's Sort property names a
  column, not an expression. Service-impl recognises the literal
  `sort=outstanding,desc` and applies the JPQL ordering;
  default ordering stays `id desc`.
- **Frontend changes**: same as 1.D for the destination component.
  KPI tile passes `{ status: 'OPEN', sort: 'outstanding,desc' }`.
- **RBAC**: same as 1.D — `SALES.MANAGE_INVOICE` on the destination;
  the AR-summary tile itself is gated by `SALES.REPORT.AR_SUMMARY`
  (Slice C). A persona without `SALES.MANAGE_INVOICE` will see the
  tile but 403 on the drill-through — Slice F surfaces this as
  "Permission required" on the destination (matches existing
  per-tile 403 UX).
- **Effort**: **M** (folds into 1.D; the sort branch is +0.5 day).

---

## Section 2 — Alert rows (4 rows, all live)

### ALERT 2.A — "N SKUs at or below reorder level"

- **What it shows**: When `stockAlerts > 0`. Same data source as
  KPI 1.B.
- **Current `routerLink`**: `/stock/balances`
  (`dashboard.component.ts:386` — no query param).
- **CTA**: "Review".
- **Subset match: PARTIAL** — same as KPI 1.B (destination has the
  toggle but defaults off).
- **Backend filter needed**: same as 1.B (optional; FE filter works
  today).
- **Frontend changes**: re-point to
  `/stock/balances?belowReorderOnly=true`. One-line edit at
  `dashboard.component.ts:386` (use `[queryParams]` instead of
  embedding in `link` string). Destination changes are the same as
  KPI 1.B's frontend changes (do once, both consumers benefit).
- **RBAC**: same as 1.B.
- **Effort**: **S** (one-line link change once KPI 1.B's destination
  work is done).

### ALERT 2.B — "N items in negative stock"

- **What it shows**: When `negativeStockCount > 0`. Same data source
  as KPI 1.C.
- **Current `routerLink`**: `/stock/balances`
  (`dashboard.component.ts:391` — no query param).
- **CTA**: "Review".
- **Subset match: NO** — same gap as KPI 1.C (destination has no
  negative-only toggle today).
- **Backend filter needed**: same as 1.C — `negativeOnly` param
  on `GET /balances`.
- **Frontend changes**: re-point to `/stock/balances?negativeOnly=true`
  once the destination component's negative-only toggle lands
  (KPI 1.C work).
- **RBAC**: same as 1.C.
- **Effort**: **S** (one-line link change once KPI 1.C work is done).

### ALERT 2.C — "N invoices past due"

- **What it shows**: When `overdueInvoices > 0`. Same data source as
  the `SalesInvoiceRepository#countOverdueForBranch` query.
- **Current `routerLink`**: `/debt` (`dashboard.component.ts:395`).
- **CTA**: "Chase".
- **Subset match: NO** — `/debt` is a **placeholder hub**
  (`debt.component.ts:23-31` — "Debt module coming soon" banner +
  four shortcut tiles to other modules). The user clicks "Chase"
  and lands on a page that says "go to Reports". The actual list
  of overdue invoices is nowhere reachable from the alert.
- **Backend filter needed**: `status=OVERDUE` on
  `GET /sales-invoices` (same as KPI 1.D).
- **Frontend changes**: re-point to `/sales/invoices?status=OVERDUE`.
  Destination component changes are the same as KPI 1.D's frontend
  changes (the bucket dropdown handles both `OPEN` and `OVERDUE`).
  Leave the `/debt` placeholder hub alone — it stays a hub for
  future debt module landing pages (customer statements, dunning
  workflows). Just don't route the dashboard alert through it.
- **RBAC**: `SALES.MANAGE_INVOICE` on the destination. The alert
  tile itself is gated by `SALES.REPORT.AR_SUMMARY` (Slice C —
  the tile only shows if the count came back).
- **Effort**: **S** (one-line link change once KPI 1.D work is
  done).

### ALERT 2.D — "N LPOs awaiting approval"

- **What it shows**: When `lposPending > 0`. Data source is the
  new `LpoOrderController#pendingApprovalCount` from PR #37.
- **Current `routerLink`**: `/procurement`
  (`dashboard.component.ts:399`).
- **CTA**: "Open".
- **Subset match: NO** — `/procurement` is a **hub page**
  (`procurement.component.ts:23-38` — four tiles linking to LPO,
  GRN, supplier-invoices, supplier-payments lists). The actual
  LPO list at `/procurement/lpos` is one click away, and even when
  reached, it has no status filter
  (`lpos.component.ts:435-454` — `refresh()` calls
  `listLpos(branchId, pageNo, pageSize)` with no status arg).
- **Backend filter needed**: YES — `status` query param on
  `GET /lpos`. Backend repository already has
  `findByCompanyIdAndStatusOrderByIdDesc` for the non-paged shape
  (`LpoOrderRepository.java:20`); add a paged variant +
  branch variant. Service interface gains
  `list(Long branchId, LpoOrderStatus status, Pageable pageable)`.
- **Frontend changes**: (a) re-point alert to
  `/procurement/lpos?status=PENDING_APPROVAL`; (b) `lpos.component.ts`
  reads `status` from `ActivatedRoute.queryParamMap`, adds a status
  filter dropdown (options: `ALL`, `DRAFT`, `PENDING_APPROVAL`,
  `APPROVED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED`), URL
  sync on change; (c) `ProcurementService#listLpos` adds the
  status arg.
- **RBAC**: `PROCUREMENT.MANAGE_LPO` on the destination
  (`LpoOrderController.java:31`). Procurement-officer persona holds
  it (test-users.ts:138-140). The alert tile itself is gated by
  the tri-grant on `pendingApprovalCount` from PR #37.
- **Effort**: **M** (BE repo + service + controller; FE dropdown +
  wiring + alert re-point).

---

## Section 3 — Consolidated rollup endpoint (new)

| Endpoint | Status |
|---|---|
| `GET /api/v1/sales/reports/ar-summary` | Live — Slice C |
| `GET /api/v1/reports/stock-negative` | Live — Slice E1 |
| `GET /api/v1/lpos/pending-approval/count` | Live — PR #37 |
| `GET /api/v1/reports/sales-summary` | Live — F8.2 |
| `GET /api/v1/balances` | Live — feeds frontend-derived `stockAlertCount` |
| **`GET /api/v1/reports/dashboard-rollup`** | **NEW — Slice F** |

- **GAP 3.A — `DashboardRollupDto` + `DashboardReportService`**.
  Single trip that returns the union of all five tile feeds + four
  alert counts. Per-fragment auth: each sub-call wrapped in
  `try { ... } catch (AccessDeniedException) { return null }`,
  serialised as `null` on the wire. Component reads `null` as
  "Permission required" (existing UX, same as today's per-tile 403
  handling). Plan §6.4 has the full DTO shape.
- **GAP 3.B — JSON wire-shape pin** for `DashboardRollupDto`.
  Asserts `branchId` Long stringifies (per `IdLongAsStringSerializerModifier`);
  `arOutstanding` + `todaysSales` BigDecimal stay numeric; counts
  stay numeric.
- **GAP 3.C — Dashboard component swap**. The four
  `ngOnInit` HTTP calls
  (`dashboard.component.ts:464-489`) collapse to one
  `this.dashboard.rollup(branchId, businessDate).subscribe(...)`.
  Per-fragment null handling stays — same observables, same
  `arPermissionDenied` / `negativeStockPermissionDenied` signals.
  The existing per-fragment service methods stay (callers outside
  the dashboard may use them); the component switches.
- **Effort**: **M** (one BE service + one BE controller + one JSON
  pin + one FE swap).

---

## Section 4 — Backend touchpoints (consolidated)

| File | Change |
|---|---|
| `LpoOrderController.java:32-36` | `list` signature gains `LpoOrderStatus status` param. |
| `LpoOrderService.java` | `list(Long branchId, Pageable)` → `list(Long branchId, LpoOrderStatus status, Pageable)`. |
| `LpoOrderServiceImpl.java:156-165` | Branch on `status != null` → call new repo method; otherwise unchanged. |
| `LpoOrderRepository.java` | Add `Page<LpoOrder> findByCompanyIdAndStatusOrderByIdDesc(Long, LpoOrderStatus, Pageable)` + `Page<LpoOrder> findByCompanyIdAndBranchIdAndStatusOrderByIdDesc(Long, Long, LpoOrderStatus, Pageable)`. |
| `SalesInvoiceController.java:36-41` | `list` signature gains `String status` param (bucket alias or raw enum). |
| `SalesInvoiceService.java:43` | Same signature widening. |
| `SalesInvoiceServiceImpl.java:277-286` | Branch on `status` token — `OPEN` / `OVERDUE` / raw enum / null. |
| `SalesInvoiceRepository.java` | Add `Page<SalesInvoice> findOpenForBranch(Long, Long, Pageable)` + `Page<SalesInvoice> findOverdueForBranch(Long, Long, LocalDate, Pageable)`. JPQL only; indexes already present. |
| `StockController.java:34-37` | `listBalances` gains `negativeOnly` + `belowReorderOnly` params; class-level `@PreAuthorize("hasAuthority('STOCK.COUNT')")` added (closes Slice E1 GAP 5.A). |
| `StockMoveService.java` | `listBalances(Long branchId)` → `listBalances(Long branchId, boolean negativeOnly, boolean belowReorderOnly)`. |
| **NEW** `DashboardReportController.java` (or fold into `SalesAggregateReportController` shape) | `GET /api/v1/reports/dashboard-rollup`. |
| **NEW** `DashboardReportService.java` + `DashboardReportServiceImpl.java` | Per-fragment auth wrap, parallel sub-call orchestration. |
| **NEW** `DashboardRollupDto.java` | Record + two nested record sections. |

No new permissions (plan §2). No new outbox events (read-only reports).
No new modules. No schema changes (indexes from Slice C suffice). No
ArchUnit named-exemption changes (the new `DashboardReportService`
internally calls existing module services through their existing
interface seams — same shape as today's per-fragment dashboard
service calls). The `STOCK.COUNT` pin on `StockController` closes
a Slice E1 latent gap.

## Section 5 — Frontend touchpoints (consolidated)

| File | Change |
|---|---|
| `dashboard.component.ts:113-128` | Wrap KPI tiles in `<a routerLink>` with `[queryParams]` per the §3 contract (currently inert cards). |
| `dashboard.component.ts:384-400` | Re-point 4 alert `link` strings to query-paramed URLs (current state: 2 alerts point to placeholder hubs). |
| `dashboard.component.ts:456-489` | Replace 4 parallel HTTP calls with one `this.dashboard.rollup(...)` (GAP 3.C). |
| `dashboard.service.ts:32-81` | Add `rollup(branchId, businessDate)` method; existing per-fragment methods stay. |
| `dashboard.service.ts:85-93` | Add `dashboardRollup: true` to `DASHBOARD_LIVE`. |
| `stock/balances.component.ts:54-58` | Add "negative only" checkbox alongside "below reorder only". |
| `stock/balances.component.ts:170-177` | Read `negativeOnly` + `belowReorderOnly` from `ActivatedRoute.queryParamMap` on init; pre-check toggles; bidirectional URL sync. |
| `stock/stock.service.ts` | `listBalances(branchId)` → `listBalances(branchId, opts?: { negativeOnly?: boolean, belowReorderOnly?: boolean })`. |
| `sales/invoices.component.ts` | Add status filter dropdown (`ALL`, `OPEN`, `OVERDUE`, raw statuses); read `status` from query params; URL sync; new `(statusChange)` handler. |
| `sales/sales.service.ts` | `listInvoices(branchId, page, size)` → `listInvoices(branchId, status, page, size)`. |
| `procurement/lpos.component.ts:435-454` | Add status filter dropdown; read `status` from query params; URL sync; thread through `refresh()`. |
| `procurement/procurement.service.ts:24-28` | `listLpos(branchId, page, size)` → `listLpos(branchId, status, page, size)`. |
| `reports/reports.routes.ts` | Add new route `sales-daily` → loadComponent. |
| **NEW** `reports/sales-daily.component.ts` | Daily-sales drill-through destination for KPI 1.A. Renders `DailySalesRowDto[]` from `GET /reports/sales-daily`. |

## Section 6 — Persona impact

**Recommendation: extend `accountant` persona, no new personas.**

Today (test-users.ts:112-131):
- `accountant` has `SALES.REPORT.AR_SUMMARY` → reads AR tiles.
- `accountant` does NOT have `STOCK.COUNT` → cannot read negative-stock
  drill-through (would 403 on `/stock/balances` once the pin lands).
- `accountant` does NOT have `PROCUREMENT.MANAGE_LPO` → cannot read
  LPO drill-through (would 403 on `/procurement/lpos`).

The role design intent is that the accountant **can read every alert
they get to see** — they're the persona who chases overdue invoices
and reviews stock variance ahead of EOD. Slice F should add:
- `STOCK.COUNT` to `accountant` (so they can drill into the
  negative-stock alert + stock-balances destination).
- `PROCUREMENT.APPROVE_LPO` or `MANAGE_LPO.READ` (whichever is
  semantically the right "I can read LPOs but I'm not the procurement
  team" perm — PR #37 declared both alongside `MANAGE_LPO`; verify
  with one-line check). **Recommendation: `PROCUREMENT.MANAGE_LPO.READ`**
  if it exists as a distinct seeded perm; otherwise leave the accountant
  blocked on the LPO drill-through and document — out-of-scope to
  introduce a new "read-only LPO" perm in Slice F.

Other personas (`sales-clerk`, `store-manager`, `procurement-officer`,
`stock-controller`) already have the right combinations for their
relevant drill-throughs. The QA spec from task #1 of the plan exercises
the accountant as the happy-path persona; sales-clerk + store-manager as
the negative-path personas for AR drill-throughs and procurement
drill-throughs respectively.

**No new persona required.**

## Section 7 — Tests

| Gap | Test |
|---|---|
| **GAP 7.A** | Service-impl tests: `LpoOrderServiceImplTest` adds status-filter branches (null / `PENDING_APPROVAL` / other); asserts the right repo method is called. |
| **GAP 7.B** | `SalesInvoiceServiceImplTest` adds bucket-alias branches (`OPEN` → `findOpenForBranch` call; `OVERDUE` → `findOverdueForBranch` with `today=now()` call; raw enum → status-equality branch). |
| **GAP 7.C** | `StockMoveServiceImplTest` extends the `listBalances` test with the two flags. |
| **GAP 7.D** | NEW `DashboardReportServiceImplTest` — happy path returns all fragments; per-fragment `AccessDeniedException` returns null for that fragment, populates the rest. |
| **GAP 7.E** | NEW JSON wire-shape pin `DashboardRollupDtoJsonTest` — branchId Long stringifies, BigDecimals stay numeric, counts stay numeric, null fragments render as `null`. |
| **GAP 7.F** | `dashboard-drillthrough.spec.ts` Playwright spec (the QA-task-#1 deliverable) — 10 scenarios across 3 personas. |
| **GAP 7.G** | Web unit tests on the four destination components — assert `ActivatedRoute.queryParamMap` is consumed and pre-applied. |
| **GAP 7.H** | ArchUnit green — no new module dependencies. `DashboardReportServiceImpl` reaches across into `SalesReportService`, `StockReportService`, `LpoOrderService` — all via their **interface seams** in `..modules.<m>.service..` (the `..common..` infra catch-all in `ModuleBoundaryTest`); same shape as `dashboard.service.ts` already does on the frontend. **Verify the test stays green** rather than adding new named exemptions. If it requires an exemption, escalate — that's an architectural decision (ADR), not Slice F. |

## Section 8 — Verification (QA-image smoke)

Owned by qa-engineer. Backend should expect the QA-image rebuild +
wipe + smoke flow on:

- Accountant logs in → dashboard loads → **all 5 KPI tiles are now
  links** + 4 alert rows present.
- Click each KPI tile → land on the right route with right query
  params → destination renders with the matching filter
  pre-applied.
- Click each alert row → same.
- URL is shareable: paste
  `/sales/invoices?status=OVERDUE` directly into the address bar →
  destination renders overdue-only.
- Sales-clerk logs in → AR tiles render "Permission required"
  (no `SALES.REPORT.AR_SUMMARY`). Click on tile → no-op or 403 page
  (decide UX — recommendation: tile is **not a link** when the
  perm is denied; same shape as today's "Permission required"
  state).
- Store-manager logs in → stock alert + negative-stock tiles render
  with values; click → land on `/stock/balances` filtered correctly.
  Procurement + AR tiles render "Permission required".
- `dashboard-rollup` endpoint returns one envelope; per-fragment
  nulls match the per-tile 403 states.

---

## Cross-cutting (summary for backend-engineer)

- **Three list endpoints widened** with status / filter params
  (LPO, sales-invoice, balances) — JPQL only, indexes already
  exist.
- **One new consolidated endpoint** `/api/v1/reports/dashboard-rollup`
  with per-fragment auth — biggest single surface change.
- **`STOCK.COUNT` pin on `StockController`** — closes Slice E1
  GAP 5.A in the same change window.
- **No schema migrations**. No permission seeds. No outbox events.
  No ADR.

## Cross-cutting (summary for frontend-engineer)

- **Five KPI tiles wrapped in routerLink** — all inert today.
- **Four alert links re-pointed** with query params; two of them
  (overdue + LPO-pending) re-route off placeholder hubs.
- **Three destination components grow a query-param-driven filter**
  — balances, invoices, lpos.
- **One new route + component** for KPI 1.A
  (`/reports/sales-daily`).
- **Dashboard component swap** from 4 parallel HTTP calls to one
  rollup call.

## Open questions

Three — both in `slice-f-reports-plan.md §8`:

1. **Consolidated rollup endpoint** — land in Slice F (recommended)
   or defer? Decision flips task #3(d) and #4(e) on / off.
2. **Overdue-invoices alert re-point** — to `/sales/invoices?status=OVERDUE`
   (recommended) or leave on `/debt` placeholder? Decision flips
   ALERT 2.C.
3. **`STOCK.COUNT` on `StockController#listBalances`** — fold
   the Slice-E1 GAP 5.A here (recommended) or leave for a future
   slice? Decision flips KPI 1.B + 1.C + ALERT 2.A + 2.B backend
   work.

## Total gap count by section

| Section | Gaps |
|---|---|
| 1 — KPI tiles | 5 (1.A–1.E) |
| 2 — Alert rows | 4 (2.A–2.D) |
| 3 — Rollup endpoint | 3 (3.A–3.C) |
| 4 — Backend touchpoints | 10 file changes (3 widened endpoints + 1 new endpoint + supporting service/repo/DTO) |
| 5 — Frontend touchpoints | 14 file changes |
| 6 — Persona | 1 widening (accountant) |
| 7 — Tests | 8 (7.A–7.H) |
| 8 — QA smoke | 0 (owned by qa-engineer) |
| **Total** | **~30 file changes across 9 numbered gaps** |

The three highest-leverage gaps:
1. **KPI 1.D + ALERT 2.C + KPI 1.E** (one BE change set) — adds
   `status` param to `GET /sales-invoices` with `OPEN` / `OVERDUE`
   bucket aliases; re-points two dashboard touchpoints (overdue
   alert + AR tiles) onto the same destination component.
2. **KPI 1.C + ALERT 2.B** (one BE + one FE change set) — adds
   `negativeOnly` param to `GET /balances` + the "negative only"
   toggle on the destination component; closes the **load-bearing**
   gap where the negative-stock alert leads to a list with no way
   to filter to negative.
3. **GAP 3.A + 3.C** (rollup endpoint + dashboard component swap)
   — single-trip dashboard load. Optional but recommended.

## What's intentionally NOT in scope (Slice F)

- Standalone debt module (defer to Slice G).
- CSV / PDF export off any report endpoint.
- Scheduled / emailed reports.
- POS-side reports surfaced on the dashboard (Z-history, gift-card
  liability, production-wastage, layby-ageing — all have endpoints
  but no dashboard surface).
- Reporting-module extraction (ADR-0004 #19 — still deferred).
- New "read-only LPO" / "read-only sales" perm splits.
- Drill-through analytics (graphs, cohorts) — Slice F lands the
  "click takes you to the matching list" baseline.

Backend-engineer can start after the qa-engineer's failing
`dashboard-drillthrough.spec.ts` lands (plan task #1).
