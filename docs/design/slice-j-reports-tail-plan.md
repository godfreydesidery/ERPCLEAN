# Slice J — Reports tail (stock card + fast/slow movers + negative-stock page)

| Field | Value |
|---|---|
| Branch | `harden/reports-tail` |
| Prereqs | Slice F (dashboard) + Slice I (universal PDF/Excel/CSV export). |
| Owner | PM coordinating; frontend-engineer + qa-engineer (no backend work). |
| Date | 2026-05-28 |
| Target | ~1 day end-to-end |
| Closes | **US-RPT-004** (stock card report) + **US-RPT-005** (fast / slow moving items) + **US-RPT-006** (standalone negative-stock page; currently only a dashboard tile). |

## Background — and a pivot note

Slice J was originally scoped as **Sales tail** (US-SALES-012 packing list + US-SALES-013 reprint with audit). A pre-work recon showed both stories are **already shipped** in Slice C: V31 schema + `PackingListController` (DRAFT → DISPATCHED → DELIVERED → CANCELLED state machine, full CRUD, 424-line Angular `packing-lists.component.ts`); `POST /api/v1/sales-invoices/uid/{uid}/reprint` increments `reprint_count`, emits `SalesInvoiceReprinted.v1`, and validates a `ReprintReason` enum (`DUPLICATE / REISSUE_TO_CUSTOMER / INTERNAL_FILE / OTHER`). Nothing to add.

Pivoting to **Reports tail**, where the same pattern holds — backend data endpoints all exist (`StockController.stockCard`, `StockReportController.negativeOnHand / fastMovers / slowMovers`), only the Angular pages are missing.

## 1. Scope

**In:**

- **NEW `stock-card.component.ts`** — `/reports/stock-card` page rendering `GET /api/v1/stock/stock-card?itemId=&branchId=&page=&size=`. Filters: item (typeahead, reuse `ItemTypeaheadComponent` from Slice H.1), branch (default = current branch), optional date range (client-side filter for the rendered page since the endpoint doesn't take from/to; document the limitation). Table columns: move type, doc number, doc date, qty in, qty out, running balance, reference (FK to source doc with deep-link). Paged. 4 states (loading / empty / error / populated). Axe-AA clean. Export menu wired.
- **NEW `negative-stock.component.ts`** — `/reports/negative-stock` standalone page rendering `GET /api/v1/reports/stock-negative?branchId=`. Filter: branch (default = current). Returns `List<ItemBranchBalanceDto>`. Columns: item code, item name, branch, on-hand (red highlight when < 0), last move date. Click-through to `/reports/stock-card?itemId=…&branchId=…` for the item's move history. Export menu wired.
- **NEW `stock-movers.component.ts`** — `/reports/stock-movers` page with two tabs (Fast / Slow). Filters: branch, date range (defaults to last 30 days), comma-separated `moveTypes` (default `SALE`), limit (default 20). Each tab renders `List<ItemMovementRowDto>` from the respective endpoint. Columns: rank, item code, item name, total qty moved, total move count, last move date. Export menu wired (separate export per tab — file name reflects fast or slow).
- **MOD `reports.component.ts`** — index page lists the three new reports.
- **MOD `reports.routes.ts`** — add 3 new routes.
- **MOD sidebar shell** — add 3 new nav links under Reports section: "Stock card", "Negative stock", "Stock movers".
- **MOD dashboard "Negative stock" tile** (Slice F) — drill-through now points to `/reports/negative-stock` (currently it likely opens the dashboard fragment only). Confirm + update if needed.
- **E2E spec** — extend `e2e/reports.spec.ts` (or create a new `e2e/stock-reports.spec.ts`) with ~6 expected-fail scenarios: stock-controller opens each of the 3 pages, sees rows, exports CSV/Excel/PDF; cashier 403 on `/reports/negative-stock` (or FE-guard equivalent if backend isn't perm-gated tightly enough); empty-state on a stock-fresh date range.

**Out:**

- US-RPT-009 (schedule report by email) — Phase 2; needs SMTP infra.
- US-RPT-010 (group consolidation) — Phase 3.
- Server-side date-range filter on the stock-card endpoint — backend follow-up if requested; client-side filter is enough for in-browser ≤5000 rows.
- **No backend changes**, no migrations, no perms, no outbox events, no ADR.

## 2. Backend changes

**None.** All endpoints are live:

- `GET /api/v1/stock/stock-card?itemId&branchId&page&size` → `PageDto<StockMoveDto>` (gated by `STOCK.COUNT`).
- `GET /api/v1/reports/stock-negative?branchId` → `List<ItemBranchBalanceDto>` (gated by `STOCK.COUNT`).
- `GET /api/v1/reports/stock-fast-movers?branchId&from&to&moveTypes&limit` → `List<ItemMovementRowDto>` (gated by `STOCK.COUNT`).
- `GET /api/v1/reports/stock-slow-movers?...` → `List<ItemMovementRowDto>` (gated by `STOCK.COUNT`).

If during FE work a column the AC requires is missing from the DTO, the FE agent flags it and we open a follow-up. **Do not block this slice on backend shape tweaks.**

## 3. Frontend file changes

| File | Role |
|---|---|
| NEW `features/reports/stock-card.component.ts` (+ template + spec) | US-RPT-004 page |
| NEW `features/reports/negative-stock.component.ts` (+ template + spec) | US-RPT-006 page (dashboard already has the tile) |
| NEW `features/reports/stock-movers.component.ts` (+ template + spec) | US-RPT-005 page with fast/slow tabs |
| MOD `features/reports/reports.component.ts` | List the three new reports |
| MOD `features/reports/reports.routes.ts` | Add 3 routes |
| MOD shell sidebar | Add 3 nav links |
| MOD dashboard tile (if needed) | Drill-through to `/reports/negative-stock` |

Reuse from prior slices:
- `ItemTypeaheadComponent` for the stock-card's item filter (Slice H.1).
- `ReportExportService` + `ReportExportMenuComponent` for the export buttons (Slice I).

## 4. Persona impact

`stock-controller` (already has `STOCK.COUNT` per Slice E1) is the natural actor. `accountant` likely also holds `STOCK.COUNT` for cross-cutting reports. `cashier` and `sales-clerk` are 403 paths. No persona widening required.

## 5. Tests

- Unit specs per page (4 states + filter changes + export integration).
- E2E spec — see scenarios in §1.

## 6. Task list — parallel fan-out

| # | Owner | Deliverable | Acceptance |
|---|---|---|---|
| 1 | **qa-engineer** | Extend `e2e/reports.spec.ts` with 6 `test.fail` scenarios: 3 happy paths (one per report) + 3 supporting (cashier 403, empty-state disabled export, axe-core on each page). | Spec compiles; lists 11+ tests overall. |
| 2 | **frontend-engineer** | All files in §3; unit specs; build green; no main-bundle regression (xlsx + jspdf still in lazy chunks). | `npm test` green, `npm run build` green. |
| 3 | **integration** | Validate + QA-image rebuild + container swap + smoke. | Stack UP, all three new routes load. |

## 7. Open questions — none requiring user input

- The fast/slow endpoints accept a comma-separated `moveTypes` query param. Defaulting to `SALE` is sensible; expose a chip-multi-select on the page so the operator can broaden it to include `PROD_CONSUME`, `RETURN_OUT`, etc.
- Stock-card has no server-side date filter today. Apply a client-side filter on the rendered page and document the limitation; the existing API returns paged moves chronologically so the user can scroll.

---

**Total estimate**: ~1 day. Pure frontend; backend untouched. Builds entirely on already-shipped infrastructure (typeahead + export menu + 4 live backend endpoints).
