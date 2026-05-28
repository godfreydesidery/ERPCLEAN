# Slice F — Dashboard drill-throughs & consolidated reports

| Field | Value |
|---|---|
| Branch | `harden/dashboard-drillthroughs` |
| Prereqs | Slice C (Sales AR-summary) merged · Slice E1 (Stock negative report) merged · PR #37 (LPO pending-approval count) merged at `470ad1f` lineage |
| Owner | PM coordinating; backend + frontend + qa + architect |
| Date | 2026-05-28 |

Current state confirmed by code-read (not just docs): all seven dashboard
tiles + four alert rows are now backed by real endpoints
(`dashboard.service.ts:32-81`, `DASHBOARD_LIVE.* all true at 85-93`). The
question this slice answers is not "is the data live" but **"when the user
clicks a tile or an alert, does the destination view actually filter to
the subset that tile counted, or does it dump them on an unfiltered list
and force them to re-do the filtering by hand?"**

The gap is consistently the latter. `routerLink="/stock/balances"` lands
on a balances grid with a "below reorder only" checkbox that defaults to
**off**; the user has to find and tick it. `routerLink="/procurement"`
lands on a four-tile hub, not the LPO list, and the LPO list itself
defaults to "all statuses ordered by id desc" — no `PENDING_APPROVAL`
filter. `routerLink="/debt"` lands on a placeholder hub
(`debt.component.ts:23-31` — "Debt module coming soon"). The slice is
checklist conformance for the **drill-through experience**, not new
backend modules.

---

## 1. Scope

**In**
- Per-tile / per-alert **deep-link query-param contract** — every dashboard
  link carries the filter that matches what the tile is counting, and the
  destination component reads it from `ActivatedRoute.queryParamMap` and
  pre-applies it.
- Three **destination-page filter UIs** that don't exist today — status
  filter on `lpos.component.ts`, "negative on-hand only" toggle on
  `balances.component.ts`, and a sales-invoices status filter on
  `invoices.component.ts` (for the "open invoices" + "overdue invoices"
  drill-throughs).
- **Three backend list endpoints** widened with a `status` query param —
  `GET /api/v1/lpos?status=PENDING_APPROVAL`,
  `GET /api/v1/sales-invoices?status=POSTED,PARTIALLY_PAID&overdueOnly=true`,
  `GET /api/v1/balances?negativeOnly=true&belowReorderOnly=true`. JPQL
  only; portable on MySQL + Postgres.
- **Alert / KPI re-pointing** — the overdue-invoices alert currently
  routes to `/debt` (a placeholder hub). Re-point to
  `/sales/invoices?status=OVERDUE` (the dedicated filtered view) and
  drop the dead `/debt` link until the standalone debt module lands
  (deferred from Slice C).
- **AR-summary tile drill-through** — the two AR tiles (`openInvoiceCount`,
  `arOutstanding`) currently have **no `routerLink` at all**
  (`dashboard.component.ts:374-380` — they're inert KPI cards). Wire a
  Slice-F-spec drill-through that lands on `/sales/invoices` filtered
  to the matching status set.
- **A consolidated `/api/v1/reports/dashboard-rollup` endpoint** — one
  trip that returns all seven tile values + four alert counts. Replaces
  the four parallel `ngOnInit` HTTP calls
  (`dashboard.component.ts:464-489`) with a single round-trip. Optional
  (see §4 — fall to QA's call); recommended.
- **`accountant` persona widened** — already has `SALES.REPORT.AR_SUMMARY`
  (test-users.ts:129); add `STOCK.COUNT` so the same persona can verify
  the negative-stock drill-through. Trivial — no new persona.

**Out**
- **No standalone debt module** — still deferred. The overdue-invoices
  alert re-points to `/sales/invoices`; the `/debt` placeholder hub
  stays inert until a future slice promotes it.
- **No CSV / PDF export** off the report endpoints. Pure JSON.
- **No scheduled / emailed reports** — out-of-scope; would need a
  cron-spool aggregate.
- **No POS-side or production-side reports** — Z-history, layby-ageing,
  section-pnl, production-wastage, production-variance, gift-card-
  liability all already have endpoints; this slice does not wire them
  to the dashboard.
- **No new aggregate `reports` module** — the ADR-0004 #19 deferred
  extraction is still deferred. Report endpoints stay where they live
  today (`SalesReportController`, `SalesAggregateReportController`,
  `StockReportController`, `LpoOrderController`).
- **No drill-through analytics** (heatmaps, trend lines, cohort) —
  scope is "click takes you to the matching list view", not "click
  opens a graph".

**Prerequisites confirmed**
- Slice C merged: `SalesAggregateReportController#arSummary` live;
  `SALES.REPORT.AR_SUMMARY` (id 125) seeded; accountant persona holds
  it (test-users.ts:129).
- Slice E1 merged: `StockReportController#negativeOnHand` pinned to
  `STOCK.COUNT`; dashboard tile + alert row reads from it.
- PR #37 merged: `LpoOrderController#pendingApprovalCount` live with
  the tri-grant guard
  (`MANAGE_LPO` / `APPROVE_LPO` / `MANAGE_LPO.READ`).

## 2. Permission band

**Recommendation: no new permissions for Slice F.** The drill-through
endpoints all read existing aggregates that already have grants:

- `/api/v1/lpos?status=PENDING_APPROVAL` — same `PROCUREMENT.MANAGE_LPO`
  class-level grant as today's `GET /lpos`.
- `/api/v1/sales-invoices?status=...&overdueOnly=true` — same
  `SALES.MANAGE_INVOICE` class-level grant.
- `/api/v1/balances?negativeOnly=true` — `StockController#listBalances`
  has no `@PreAuthorize` today
  (`StockController.java:21-23` — Slice E1 GAP 5.A). Pin to
  `STOCK.COUNT` in this slice (folds the deferred Slice-E1 gap).
- `/api/v1/reports/dashboard-rollup` — new shape; gates per-fragment by
  the underlying perm (returns null for fragments the caller lacks,
  same shape as the dashboard tiles do today on 403). No new perm
  needed.

Current high-water across all migrations: **125** (`SALES.REPORT.AR_SUMMARY`,
V69 Slice C). The next-free permission band 126–130 is reserved for
slice fix-ups; Slice F does **not** consume it.

Collision check (one-liner):
```powershell
Select-String -Path 'orbix-engine-api/src/main/resources/db/migration/common/V*permission*.sql' `
              -Pattern '^\s*\(12[0-9],'
```

## 3. Tile-by-tile drill-through contract

Locked URL shape per dashboard tile / alert. The gap audit
(`slice-f-reports-gap-audit.md`) tracks the current state and the
delta to ship.

| Tile / alert | What it counts | Drill-through target |
|---|---|---|
| **KPI · Today's sales** | `dailySummary.sales.grandTotal` for branch + business date | `/reports/sales-daily?branchId={id}&businessDate={iso}` — feeds the existing `SalesReportController#dailySales` endpoint. **Status: new destination page** (`/reports/sales-daily` is not yet a route). |
| **KPI · Stock alerts** | Items at or below reorder min for branch | `/stock/balances?belowReorderOnly=true` — destination component pre-checks the "below reorder only" toggle. **FE-only.** |
| **KPI · Negative stock** | Items currently in negative on-hand | `/stock/balances?negativeOnly=true` — destination component switches to negative-only view; reads from `/api/v1/balances?negativeOnly=true` (new param). **BE + FE.** |
| **KPI · Open invoices** | Count of POSTED + PARTIALLY_PAID with outstanding | `/sales/invoices?status=OPEN` — destination component pre-filters to that status set. **BE + FE.** |
| **KPI · AR outstanding** | Sum of outstanding on open invoices | `/sales/invoices?status=OPEN&sort=outstanding,desc` — same destination, sorted by outstanding descending. **BE + FE.** |
| **Alert · N SKUs at or below reorder** | `stockAlerts` > 0 | `/stock/balances?belowReorderOnly=true` — same as KPI tile. Re-point from current `/stock/balances` (no query param). |
| **Alert · N items in negative stock** | `negativeStockCount` > 0 | `/stock/balances?negativeOnly=true` — same as KPI tile. Re-point from current `/stock/balances`. |
| **Alert · N invoices past due** | `overdueInvoices` > 0 | `/sales/invoices?status=OVERDUE` — re-point from current `/debt` (placeholder hub). **BE + FE.** |
| **Alert · N LPOs awaiting approval** | `lposPending` > 0 | `/procurement/lpos?status=PENDING_APPROVAL` — re-point from current `/procurement` (hub). **BE + FE.** |

The `OPEN` / `OVERDUE` enum bucket names on sales-invoices are
**filter-bucket aliases**, not new `SalesInvoiceStatus` values. The
backend interprets:

- `status=OPEN` → `POSTED, PARTIALLY_PAID` with `totalAmount > paidAmount`
  (matches `SalesInvoiceRepository#countOpenForBranch` today).
- `status=OVERDUE` → `OPEN` + `dueDate is not null and dueDate < today`
  (matches `SalesInvoiceRepository#countOverdueForBranch`).

The aliases match the AR-summary tile semantics exactly — so the tile
count and the destination row count agree. Raw `SalesInvoiceStatus`
values (`DRAFT`, `POSTED`, `PARTIALLY_PAID`, `PAID`, `VOIDED`,
`CANCELLED`) remain accepted for direct filtering, with `OPEN` and
`OVERDUE` as the only documented aggregate buckets.

## 4. Consolidated rollup endpoint — recommendation

**Recommendation: land `/api/v1/reports/dashboard-rollup` in Slice F.**
The dashboard currently fires four HTTP calls in parallel on every
load (`dashboard.component.ts:464-489`), one per live tile group:

1. `GET /sales/reports/ar-summary?branchId={id}`
2. `GET /reports/stock-negative?branchId={id}`
3. `GET /lpos/pending-approval/count?branchId={id}`
4. `GET /reports/sales-summary?branchId={id}&businessDate={iso}`
5. `GET /balances?branchId={id}` (consumed by `stockAlertCount` derivation)

Five round-trips, five separate auth + tenancy + branch-scope checks,
five separate response envelopes. The consolidated endpoint cuts this
to one. The response shape pins the dashboard's read contract:

```json
{
  "branchId": "1",
  "businessDate": "2026-05-28",
  "currencyCode": "TZS",
  "kpi": {
    "todaysSales": 425000.00,
    "stockAlerts": 7,
    "negativeStockCount": 3,
    "openInvoices": 12,
    "arOutstanding": 4250000.00
  },
  "alerts": {
    "stockAlertCount": 7,
    "negativeStockCount": 3,
    "overdueInvoiceCount": 5,
    "lposPendingApproval": 2
  }
}
```

Per-fragment authorisation: fragments the caller lacks the perm for
serialize as `null`. The component renders `null` as
"Permission required" — same UX as today's per-tile 403 handling
(`dashboard.component.ts:511-518, 535-547`). The rollup service
internally calls `arSummary` / `negativeOnHand` / `pendingApprovalCount`
/ `dailySummary` and catches `AccessDeniedException` per fragment.

**Trade-off**: option B is to keep the four-call shape and just wire
the drill-through query params. One day fewer of work. The arguments
against:
1. Five round-trips is a real cost on a slow link (Tanzania field
   branches over LTE).
2. The component logic for "load each tile in parallel, race the slow
   ones, surface 403 as a different state per tile" is non-trivial
   and brittle — a single endpoint flattens it.
3. The Slice E1 dashboard adds a fifth call already; adding more tiles
   later compounds the cost.

**Recommendation: land both.** The rollup is a small backend addition
(one new service method `DashboardReportService#rollup(branchId,
businessDate)`, one controller, JSON pin test) — the bulk of the
slice is the drill-through wiring regardless.

## 5. Drill-through query-param convention (frontend)

Locked contract for every destination component that reads dashboard
deep-links:

```ts
import { ActivatedRoute } from '@angular/router';

ngOnInit(): void {
  this.route.queryParamMap.subscribe(params => {
    const status = params.get('status');             // null | 'PENDING_APPROVAL' | 'OPEN' | 'OVERDUE' | ...
    const negativeOnly = params.get('negativeOnly') === 'true';
    const belowReorderOnly = params.get('belowReorderOnly') === 'true';
    // ... pre-apply to local signal, trigger refresh
  });
}
```

- Query-param **null / absent** → default behaviour (today's
  "show everything ordered by id desc").
- Query-param **set** → pre-fill the matching filter UI control on
  the destination page so the user can adjust without re-finding the
  filter. The filter UI is the source of truth; the URL is the
  initial state.
- Component **writes back to the URL via `Router.navigate(..., {
  queryParamsHandling: 'merge' })`** when the user changes the
  filter, so deep-links stay shareable.

The pattern lives next to the existing `BranchService.activeBranchId`
behaviour — query-param-driven, not router-state-driven.

## 6. Backend endpoint changes — explicit

### 6.1 `LpoOrderController#list` — add `status` param

```java
@GetMapping
@PreAuthorize("hasAuthority('PROCUREMENT.MANAGE_LPO')")
public PageDto<LpoOrderDto> list(@RequestParam(required = false) Long branchId,
                                 @RequestParam(required = false) LpoOrderStatus status,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
    return service.list(branchId, status, PageRequest.of(page, size));
}
```

Backend repository already has the building blocks
(`LpoOrderRepository#findByCompanyIdAndStatusOrderByIdDesc` exists for
the list shape; needs a `findByCompanyIdAndBranchIdAndStatusOrderByIdDesc`
+ `Pageable` variants).

### 6.2 `SalesInvoiceController#list` — add filter-bucket param

```java
@GetMapping
public PageDto<SalesInvoiceDto> list(@RequestParam(required = false) Long branchId,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
    return service.list(branchId, status, PageRequest.of(page, size));
}
```

Service-impl branches on the `status` token:
- `null` → today's behaviour (`findByCompanyIdOrderByIdDesc`).
- `"OPEN"` → POSTED + PARTIALLY_PAID with `totalAmount > paidAmount`.
- `"OVERDUE"` → OPEN + `dueDate < today`.
- Any literal `SalesInvoiceStatus` value (e.g. `"DRAFT"`, `"VOIDED"`)
  → exact match.

Backed by two new JPQL queries on `SalesInvoiceRepository`:
`findOpenForBranch(companyId, branchId, pageable)` and
`findOverdueForBranch(companyId, branchId, today, pageable)`. Both
indexes from Slice C GAP 1.B already exist
(`ix_sales_invoice_branch_status`, `ix_sales_invoice_branch_due`) —
no new schema work.

### 6.3 `StockController#listBalances` — add filter params

```java
@GetMapping("/balances")
@PreAuthorize("hasAuthority('STOCK.COUNT')")  // NEW — closes Slice E1 GAP 5.A
public List<ItemBranchBalanceDto> listBalances(
        @RequestParam Long branchId,
        @RequestParam(defaultValue = "false") boolean negativeOnly,
        @RequestParam(defaultValue = "false") boolean belowReorderOnly) {
    return service.listBalances(branchId, negativeOnly, belowReorderOnly);
}
```

Service impl filters in-memory (small result set — same as today's
`stockAlertCount` derivation on the frontend). If the result set grows
larger than ~1k rows, push the filter into JPQL with a `Pageable`
overload — out-of-scope for Slice F.

### 6.4 NEW `/api/v1/reports/dashboard-rollup` controller

```java
@GetMapping("/dashboard-rollup")
public DashboardRollupDto rollup(@RequestParam(required = false) Long branchId,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
    return service.rollup(branchId, businessDate);
}
```

No class-level `@PreAuthorize`. Per-fragment guard inside the service
catches `AccessDeniedException` from each sub-call and serialises the
fragment as `null`. Authentication still required (default Spring
Security filter chain).

DTO:
```java
public record DashboardRollupDto(
    @Nullable Long branchId,
    LocalDate businessDate,
    String currencyCode,
    @Nullable KpiSection kpi,
    @Nullable AlertSection alerts
) {
    public record KpiSection(@Nullable BigDecimal todaysSales,
                             @Nullable Integer stockAlerts,
                             @Nullable Integer negativeStockCount,
                             @Nullable Long openInvoices,
                             @Nullable BigDecimal arOutstanding) {}
    public record AlertSection(@Nullable Integer stockAlertCount,
                               @Nullable Integer negativeStockCount,
                               @Nullable Long overdueInvoiceCount,
                               @Nullable Long lposPendingApproval) {}
}
```

`stockAlertCount` and `negativeStockCount` appear in both sections
because the dashboard renders them both as a KPI tile (count) and as
an alert row (descriptive text). Computed once, serialised twice.

## 7. Task list (TDD-style — QA first, architect second, backend third)

| # | Owner | Deliverable | Acceptance signal |
|---|---|---|---|
| 1 | **qa-engineer** | Failing Playwright spec `dashboard-drillthrough.spec.ts` — accountant persona opens dashboard, clicks each of 4 alert rows + 5 KPI tiles, asserts the destination URL carries the right query params and the destination component renders the matching filter. ~10 scenarios, all expected-fail at start. Persona harness extends `accountant` to include `STOCK.COUNT` (one-line bootstrap edit). | Spec runs, ≥8 expected-fails on `main`. Land before backend work. |
| 2 | **solutions-architect (you)** | This plan + the gap audit (`slice-f-reports-gap-audit.md`). No ADR — Slice F is checklist conformance, not new architecture. The consolidated rollup endpoint is a pattern extension of Slice C's `SalesAggregateReportController`, not a new architectural shape. | Both docs committed; backend can start. |
| 3 | **backend-engineer** | Backend endpoint widening: (a) `LpoOrderController#list` adds `status` param + repo method `findByCompanyIdAndStatusOrderByIdDesc` + branch variant; (b) `SalesInvoiceController#list` adds `status` param + two new JPQL queries `findOpenForBranch` + `findOverdueForBranch`; (c) `StockController#listBalances` adds `negativeOnly` + `belowReorderOnly` params and pins to `STOCK.COUNT`; (d) NEW `/api/v1/reports/dashboard-rollup` endpoint + `DashboardReportService` + `DashboardRollupDto` + JSON wire-shape pin test. | `mvn -pl orbix-engine-api test` green; ArchUnit green. |
| 4 | **frontend-engineer** | Drill-through wiring: (a) `dashboard.component.ts` adds `routerLink` + `queryParams` to all 5 KPI tiles (currently zero have one) and re-points 2 alert rows; (b) `lpos.component.ts` reads `status` from `ActivatedRoute.queryParamMap`, adds a status filter dropdown wired to `listLpos(branchId, status, page, size)`; (c) `invoices.component.ts` reads `status`, adds bucket-filter dropdown (`ALL`, `OPEN`, `OVERDUE`, plus raw statuses); (d) `balances.component.ts` reads `negativeOnly` + `belowReorderOnly`, pre-checks the existing "below reorder only" checkbox and adds a sibling "negative only" checkbox; (e) `DashboardService` swaps the four parallel calls for a single `rollup(branchId, businessDate)` call (the existing per-fragment methods stay for callers outside the dashboard, but the component switches). | `npm test` + `npm run e2e` green for dashboard + sales + procurement + stock features. |
| 5 | **backend-engineer + frontend-engineer** | Hardening-checklist sweep on the new + widened endpoints: JSON pin for `DashboardRollupDto`; widened-endpoint JSON pin updates on `LpoOrderDto`-page / `SalesInvoiceDto`-page (status filter doesn't change DTO shape, but a query-param round-trip test asserts the right repo method is called); ArchUnit green; README "Sync callers" sub-section unchanged (no new cross-module reach). | All 10 checklist boxes ticked in PR body. |
| 6 | **qa-engineer (final QA gate)** | Re-run `dashboard-drillthrough.spec.ts` (expected-fails removed), full e2e suite, QA-image rebuild from `orbix-engine-infra/qa/Dockerfile`, smoke-test against the QA container with **3 personas** (accountant happy path — every tile + alert; sales-clerk negative path — AR drill-throughs 403; store-manager partial path — only stock-balances drill-through visible). | All scenarios green; QA report attached to PR. |
| 7 | **pm** | PR merge → sync `main` locally. Update `USER-STORIES.md` to mark the relevant dashboard drill-through stories as Hardened. Open Slice G (debt module / standalone debt UI) draft if it survives the next priority pass. | Main green; status report appended. |

## 8. Open questions for Godfrey

1. **Consolidated rollup endpoint — land it or defer it?** Recommendation
   in §4 is to land it (one extra day, four fewer HTTP calls per
   dashboard render). The alternative is to keep five parallel calls
   and ship Slice F faster. If field-branch LTE latency is a real
   concern (which the Tanzania-first deployment makes likely), land
   it now — the migration cost grows the more tiles get added later.
   Decision affects task #3 (omit (d)) and task #4 (sub-task (e)).

2. **`overdueInvoices` alert — re-point to `/sales/invoices?status=OVERDUE`
   (recommended) or leave on `/debt`?** The `/debt` page is a placeholder
   hub (`debt.component.ts:23-31`) with a "coming soon" banner. Today
   the alert lands the user on a page that says "go to `/reports`".
   Recommendation: re-point. Decision affects the audit's GAP 2.A
   (alert routing) and frees `/debt` to stay a hub.

3. **`STOCK.COUNT` on `StockController#listBalances` — fold the Slice-E1
   GAP 5.A here?** It's a one-liner that closes a known hole; the
   `negativeOnly` param needs it anyway because the alternative is
   to leave a read-anyone endpoint that exposes branch balances to
   any authenticated user. Recommendation: yes, fold. Decision
   affects task #3 (c) — closes the latent gap in the same
   migration window.

## 9. Need ADR?

**No.** Slice F is checklist conformance + drill-through UX. The
consolidated rollup endpoint extends the Slice C
`SalesAggregateReportController` pattern (one controller per consumer
context, per-fragment auth resolved inside the service), which is
already in use. No cross-module boundary changes; no new outbox
events; no new sync-TX exemptions for `ModuleBoundaryTest`. The
`OPEN` / `OVERDUE` filter-bucket aliases are a controller-input
naming convention, not a domain concept (the underlying
`SalesInvoiceStatus` enum is unchanged).

If the rollup endpoint grows a second client (POS dashboard? mobile
WMS?), that future slice may justify an ADR on whether a generalised
`reports` module extraction (ADR-0004 #19) is the right home. Slice F
itself does not trigger it.

---

**Need decision on:** the three open questions in §8. The slice is
unblocked otherwise — task #1 (qa-engineer writes the failing spec)
can start in parallel with this plan landing.
