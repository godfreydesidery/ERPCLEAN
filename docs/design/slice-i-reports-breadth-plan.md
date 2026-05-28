# Slice I — Reports breadth (daily summary + Z-history + universal PDF/Excel/CSV export)

| Field | Value |
|---|---|
| Branch | `harden/reports-breadth` |
| Prereqs | Slice F (dashboard drill-throughs + sales-daily backend). |
| Owner | PM coordinating; frontend-engineer + qa-engineer (no backend work needed). |
| Date | 2026-05-28 |
| Target | ~1.5 days end-to-end |
| Closes | **US-RPT-002** (daily summary report) + **US-RPT-003** (Z-history report) + **US-RPT-008** (export any report as PDF / Excel / CSV — small-export path). |

## Background

Slice F shipped the **backend data endpoints** for the operator reports:
- `GET /api/v1/reports/sales-daily` (US-RPT-001) — frontend exists (`sales-daily.component.ts`).
- `GET /api/v1/reports/sales-summary` (US-RPT-002) — **no frontend yet**.
- `GET /api/v1/reports/z-history` (US-RPT-003) — **no frontend yet**.

US-RPT-008 (export) is a horizontal feature listed against every report. AC: "Exports run as background jobs for results > 5000 rows; small exports stream directly." Slice I ships the **small-export path** (frontend-rendered, ≤5000 rows). The >5000-row background-job variant is a Phase 2 follow-up.

## 1. Scope

**In:**

- **NEW `sales-summary.component.ts`** — `/reports/sales-summary` page rendering `GET /api/v1/reports/sales-summary`. Filters: branch (default = current branch context), business-date (default = today), optional comparison period. Layout: KPI tiles (sales total, purchases total, cash collected, cash paid, net) + sub-tables (top items, top customers, payment-method breakdown — whichever the backend returns; the FE agent verifies the shape and renders accordingly). 4 states (loading skeleton / empty / error / populated). Axe-AA clean.
- **NEW `z-history.component.ts`** — `/reports/z-history` page rendering `GET /api/v1/reports/z-history`. Filters: branch (default current), date range (default last 7 days), optional till id. Table: business-date, till, opened-at, closed-at, opened-by, closed-by, gross sales, net sales, refunds, discounts, declared cash, variance, view-link → `/reports/z-report?tillSessionId=…` (existing endpoint already wired). 4 states.
- **NEW `report-export.service.ts`** — single service exporting any table-shaped data to PDF / Excel / CSV. Contract:
  ```ts
  interface ReportExport {
    title: string;
    subtitle?: string;     // e.g. "Branch: HQ · 2026-05-28"
    columns: Array<{ key: string; label: string; align?: 'left' | 'right' | 'center'; format?: 'currency' | 'date' | 'number' | 'text' }>;
    rows: Array<Record<string, unknown>>;
    totals?: Record<string, number | string>;
  }
  exportCsv(data: ReportExport): void          // pure TS, no deps
  exportExcel(data: ReportExport): Promise<void>  // lazy-loads `xlsx`
  exportPdf(data: ReportExport): Promise<void>    // lazy-loads `jspdf` + `jspdf-autotable`
  ```
  **Lazy-load** the libraries via dynamic `import('xlsx')` and `import('jspdf')` so they don't bloat the main bundle. Track approximate added size in the agent's report.
- **NEW `report-export-menu.component.ts`** — small button-group component `[PDF] [Excel] [CSV]` that takes a `ReportExport` builder function as input and calls the service. Reusable across the report pages. Loading state while the lazy import resolves.
- **Wire export into 3 pages** — `sales-daily`, `sales-summary`, `z-history`. Each page provides a `buildExport()` function returning the `ReportExport` for the currently-displayed data.
- **Sidebar nav** — add nav links for the two new reports under the existing Reports section.
- **E2E spec** — `e2e/reports.spec.ts` (new): accountant opens each of the 3 reports → sees rows → clicks Export → file downloads. ~5 scenarios as `test.fail` initially. Also a 403 path for a persona without report permissions.

**Out:**

- US-RPT-004 (stock card by item-branch). Defer — needs a backend audit first.
- US-RPT-005 (fast/slow moving items). Defer.
- US-RPT-006 (negative-stock report). Already in the Slice F dashboard tile.
- US-RPT-009 (schedule report by email). Needs SMTP infra; deferred.
- US-RPT-010 (group-consolidated). Phase 3.
- **Backend-rendered PDFs** + **>5000-row background-job export**. Phase 2 follow-up. Slice I FE-renders; rows > 5000 surface a toast "Too many rows for in-browser export — use a tighter date range, or wait for the scheduled-export feature."
- **No new permissions, no new outbox events, no migrations, no ADR.**

## 2. Backend changes

**None.** All three report endpoints (`/sales-daily`, `/sales-summary`, `/z-history`) already exist and are not gated by `@PreAuthorize` per the comment in `SalesReportController` ("not @PreAuthorize-gated"). The existing `SalesReportServiceImpl` produces the data; FE just renders it.

If during FE work the DTO shape is found inadequate (missing a column needed for the AC), the FE agent flags it and we open a follow-up. **Do not let the BE shape become a blocker for this slice** — render whatever ships and note the gap.

## 3. Frontend file changes

| File | Role |
|---|---|
| NEW `features/reports/sales-summary.component.ts` (+ template + spec) | US-RPT-002 page |
| NEW `features/reports/z-history.component.ts` (+ template + spec) | US-RPT-003 page |
| NEW `features/reports/report-export.service.ts` (+ spec) | Universal export service with CSV / Excel / PDF |
| NEW `features/reports/report-export-menu.component.ts` (+ template + spec) | `[PDF] [Excel] [CSV]` button group |
| MOD `features/reports/sales-daily.component.ts` | Wire export menu in |
| MOD `features/reports/reports.routes.ts` | Add `/reports/sales-summary` + `/reports/z-history` |
| MOD shell sidebar | Add nav links |
| MOD `features/reports/reports.component.ts` | Update the index page to list the new reports |
| MOD `package.json` | `xlsx@latest` + `jspdf@latest` + `jspdf-autotable@latest`. **Lazy-load only — never imported eagerly anywhere.** |

## 4. Persona impact

`accountant` is the natural happy-path actor; already has the relevant `SALES.REPORT.*` perms (Slice F). `cashier` continues to see 403 on report endpoints. No persona widening.

## 5. Tests

- Unit specs on each new component + the export service (CSV correctness, Excel mock, PDF mock).
- E2E new spec `e2e/reports.spec.ts` — 5 scenarios as `test.fail`:
  1. Accountant opens `/reports/sales-daily` → sees rows → exports CSV → file downloads with correct content type. Axe.
  2. Accountant opens `/reports/sales-summary` → sees KPI tiles → exports Excel.
  3. Accountant opens `/reports/z-history` → sees rows → exports PDF.
  4. Cashier → 403 on `/reports/sales-summary` (or whichever endpoint is the gate — confirm with backend code; if endpoints really are non-gated as the comment says, this scenario flips to "cashier doesn't see Reports nav").
  5. Empty state — date range with no sales → renders empty state, export menu disabled.

## 6. Risks / things to watch

- **Bundle size** from `xlsx` + `jspdf` — must lazy-load. Verify with `npm run build` that no chunk grew by > 500KB.
- **Locale** — TZ uses `TZS` with no decimals; dates `YYYY-MM-DD`; thousands separator with comma. Confirm the formatter uses `Intl.NumberFormat('sw-TZ', { style: 'currency', currency: 'TZS' })` or the existing project formatter pipe.
- **Row cap** — guard with `if (rows.length > 5000) { toast.error('Too many rows…'); return; }` per AC.
- **`SalesReportController` is non-`@PreAuthorize`** today (per its own comment). If we want to gate by role later, it's a separate slice; Slice I assumes the current behaviour.

## 7. Task list — parallel fan-out

| # | Owner | Deliverable | Acceptance |
|---|---|---|---|
| 1 | **qa-engineer** | NEW `e2e/reports.spec.ts` with 5 `test.fail` scenarios per §5; confirm whether report endpoints are perm-gated and adjust the 403 scenario accordingly. | Spec compiles, lists 5+ tests. |
| 2 | **frontend-engineer** | All files in §3. Install `xlsx`, `jspdf`, `jspdf-autotable` and lazy-load them in `report-export.service.ts`. Unit tests on each component + service. Wire export menu into the 3 reports. Verify `npm run build` bundle size delta acceptable (< 500KB on main chunk). | `npm test` green, `npm run build` green. |
| 3 | **integration** | Cherry-pick (if needed), validate, QA-image rebuild, smoke. | All green. |

## 8. Open questions — none requiring user input

- **Backend perm gating** on report endpoints is intentionally absent per the controller comment. We leave it that way for Slice I; revisit if the user demands report-RBAC.
- **PDF page size / orientation** — default `A4 portrait`; landscape if column count > 6.
- **Localized currency format** — use `TZS` per project memory `project-tanzania-locale`.

---

**Total estimate**: ~1 day. Pure frontend + export-library wiring; backend untouched.
