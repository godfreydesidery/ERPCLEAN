# Slice K — Reports statements (customer + supplier statement + layby ageing pages)

| Field | Value |
|---|---|
| Branch | `harden/reports-statements` |
| Prereqs | Slice F (sales-daily + dashboard drill-throughs), Slice I (universal PDF / Excel / CSV export), Slice J (stock-report tail). Backend already shipped in F8.6 (layby ageing) + F8.7 (statements). |
| Owner | PM coordinating; **frontend-engineer** + **qa-engineer** (no backend work in scope). |
| Date | 2026-05-29 |
| Target | ~1.5 days end-to-end |
| Closes | **US-RPT-007** (covers **US-DEBT-005** customer statement + **US-DEBT-006** supplier statement) + **US-RPT-014** (layby / pre-order ageing report). |

## Background

The reports index (`features/reports/reports.component.ts`) currently shows five tiles as SOON: Customer statement, Supplier statement, AR ageing, AP ageing, Layby ageing.

- **Customer statement** (`GET /api/v1/reports/customer-statement?customerId=&from=&to=`) — backend live since F8.7. No FE.
- **Supplier statement** (`GET /api/v1/reports/supplier-statement?supplierId=&from=&to=`) — backend live since F8.7. No FE.
- **Layby ageing** (`GET /api/v1/reports/layby-ageing?branchId=&type=&asOf=`) — backend live since F8.6. No FE.
- **AR ageing / AP ageing** — already addressed by `/debt` (Slice G / G.1) which surfaces both dunning queues with the 5-bucket ageing breakdown and per-party drill-down. **The reports-index tiles for AR/AP ageing should redirect to `/debt` rather than ship duplicate pages.** Flag for owner to confirm; if they want a separate reports view, that's a follow-up slice.

This is a pure FE slice — every endpoint is live and DTO shapes are stable. Builds on the export-menu and party-picker infrastructure from Slice H.1 / I / J.

## 1. Scope

**In:**

- **NEW `customer-typeahead.component.ts`** under `features/sales/` (mirror `features/procurement/supplier-typeahead.component.ts`). Standalone, debounced (250 ms), backed by `GET /api/v1/customers?q=`, emits `CustomerSelectedEvent { partyUid, id, code, name }`. WCAG AA (combobox role, arrow-key / Enter / Escape, focus return). Reusable beyond this slice.
- **NEW `customer-statement.component.ts`** — `/reports/customer-statement` page. Filters: customer (picker — `CustomerTypeaheadComponent`), date range (default last 30 days). Renders `PartyStatementDto`:
  - Header KPI strip: opening balance, period debits, period credits, closing balance (all currency-formatted TZS).
  - Table: date / kind (badge: INVOICE blue, RECEIPT green, CREDIT_NOTE amber) / number (deep-link where possible — invoice number to `/sales/invoices/uid/...`, receipt to `/sales/receipts/uid/...`, credit-note to `/sales/customer-credit-notes/uid/...`) / reference / debit / credit / running balance / voided badge.
  - 4 states (loading / empty / error / populated). Axe-AA. Export menu (PDF / Excel / CSV).
  - Accepts `?customerId=...&from=...&to=...` query params so `/debt` can deep-link in.
- **NEW `supplier-statement.component.ts`** — `/reports/supplier-statement` page. Same shape as customer statement; uses `SupplierTypeaheadComponent` (already exists). Kind values are INVOICE / PAYMENT only (no credit notes — vendor returns ship credit notes too but they're not modelled into the statement endpoint at MVP — leave a code comment). Deep-links: supplier invoice → `/procurement/supplier-invoices/uid/...`, payment → `/procurement/supplier-payments/uid/...`. Accepts `?supplierId=...&from=...&to=...`.
- **NEW `layby-ageing.component.ts`** — `/reports/layby-ageing` page. Filters: branch (default current), type chip (ALL / LAYBY / PRE_ORDER), as-of date (default now). Renders `LaybyAgeingReportDto`:
  - KPI strip: outstanding-by-type (LAYBY balance + count, PRE_ORDER balance + count), printed asOf.
  - Bucket rollup table: type, age bucket (0-7 / 8-30 / 31-60 / 61-90 / 91+), order count, total, paid, balance due.
  - Per-order drill-down (oldest first): number (deep-link `/orders/uid/...` even though `/orders` UI doesn't exist yet — keep the link, it'll wire up when Phase 7 web ships), branch, customer id, type, status, age days, days-until-expiry (red if negative), total, paid, balance due.
  - 4 states. Axe-AA. Export menu.
- **MOD `reports.component.ts`** — flip the three new tiles from `status: 'soon'` to `status: 'live'`. **Decide on AR / AP ageing tiles**: change their links to `/debt` (preferred — single source of ageing truth) **and update the description** to "Customer dunning queue with ageing buckets (see Debt module)" / "Supplier obligations queue with ageing buckets". Keep them `'live'`. Drop the standalone AR-ageing / AP-ageing tile rows if the owner prefers no duplication. **Default: redirect; flag in PR.**
- **MOD `reports.routes.ts`** — add 3 routes (`customer-statement`, `supplier-statement`, `layby-ageing`).
- **MOD sidebar shell** — add 3 nav links under Reports section.
- **MOD `debt.component.ts`** — "Statements" button (already present, points to `/reports/customer-statement`) — verify it preserves the active-customer context as a deep-link query param when launched from a per-customer row. If currently un-parameterised, add `?customerId=` to the relevant drill-down rows.
- **E2E spec** — extend `e2e/reports.spec.ts` (or new `e2e/statements.spec.ts`) with ~8 `test.fail` scenarios:
  1. Accountant opens `/reports/customer-statement`, picks a customer via typeahead, sees rows, exports CSV. Axe.
  2. Accountant opens `/reports/supplier-statement`, picks a supplier, sees rows, exports Excel.
  3. Accountant opens `/reports/layby-ageing`, sees per-type KPIs + bucket table + drill-down. Exports PDF.
  4. Layby-ageing type chip filters to LAYBY only — PRE_ORDER rows hidden.
  5. Customer statement deep-link `/reports/customer-statement?customerId=...&from=...&to=...` pre-loads the form + fetches.
  6. Cashier persona — 403 / FE-guard on `/reports/layby-ageing` (gated `ORDER.READ`).
  7. Empty-state (customer with no activity in window) — empty placeholder + export menu disabled.
  8. AR-ageing tile click navigates to `/debt` (regression guard for the tile redirect).

**Out:**

- Standalone `/reports/ar-ageing` and `/reports/ap-ageing` pages — covered by `/debt`. Reports-index tiles redirect.
- Backend `@PreAuthorize` gating on `/reports/customer-statement` + `/reports/supplier-statement` — currently ungated per existing controller convention. Out of scope; revisit if finance asks for narrower grant.
- US-RPT-009 (schedule report by email) — Phase 2, needs SMTP infra.
- US-RPT-010 (group consolidation) — Phase 3.
- Vendor-return credit notes folded into the supplier statement — backend ships invoice + payment only; vendor-returns add a TODO comment in the controller for a follow-up.
- **No backend changes, no migrations, no new perms, no outbox events, no ADR.**

## 2. Backend changes

**None.** All three endpoints are live and DTO shapes (`PartyStatementDto`, `StatementEntryDto`, `LaybyAgeingReportDto`, `LaybyAgeingBucketDto`, `LaybyAgeingOrderDto`) are stable.

If during FE work an AC-required column is missing, FE agent flags it and we open a follow-up. **Do not block this slice on backend shape tweaks.**

## 3. Frontend file changes

| File | Role |
|---|---|
| NEW `features/sales/customer-typeahead.component.ts` (+ template + spec) | Reusable customer picker (mirror of `supplier-typeahead`) |
| NEW `features/reports/customer-statement.component.ts` (+ template + spec) | US-DEBT-005 / US-RPT-007 page |
| NEW `features/reports/supplier-statement.component.ts` (+ template + spec) | US-DEBT-006 / US-RPT-007 page |
| NEW `features/reports/layby-ageing.component.ts` (+ template + spec) | US-RPT-014 page |
| MOD `features/reports/reports.routes.ts` | Add 3 routes |
| MOD `features/reports/reports.component.ts` | Flip 3 tiles to live + redirect AR/AP ageing tiles to `/debt` |
| MOD `features/reports/reports.service.ts` (+ spec) | Add `getCustomerStatement`, `getSupplierStatement`, `getLaybyAgeing` methods |
| MOD `features/reports/reports.models.ts` | Add TS interfaces matching `PartyStatementDto`, `StatementEntryDto`, `LaybyAgeingReportDto`, `LaybyAgeingBucketDto`, `LaybyAgeingOrderDto` — all `id` / `…Id` fields typed `string` per project convention |
| MOD shell sidebar | Add 3 nav links under Reports |
| MOD `features/debt/debt.component.ts` | Ensure the "Statements" link can carry `?customerId=` when launched from a per-customer row |

Reuse:
- `ReportExportService` + `ReportExportMenuComponent` (Slice I) for export buttons.
- `SupplierTypeaheadComponent` (Slice H.1) for supplier picker.
- The interceptor unwraps `ApiResponse<T>` — services see raw DTOs.

## 4. Persona impact

- `accountant` — natural happy-path actor on all three reports.
- Statements endpoints are **not** `@PreAuthorize`-gated (matches the SalesReportController pattern). Layby ageing **is** gated `ORDER.READ` or `ORDER.MANAGE`. FE adds a `*orbixHasPermission="'ORDER.READ'"` route guard or in-page check on `/reports/layby-ageing` so cashier sees a friendly permission-required panel rather than a network 403.
- No persona widening required. No new perms.

## 5. Tests

- **Unit specs** per new component (4 states each + filter changes + export integration + deep-link query-param parsing). Service-method unit spec for the 3 new `reports.service` methods. Component spec for `customer-typeahead` (debounce, keyboard nav, selection emission).
- **E2E spec** — 8 scenarios per §1. Follow the slice-j pattern: all `test.fail` initially, flip to passing as the FE lands.

## 6. Open questions

1. **AR / AP ageing tiles** — owner to confirm preference: (a) redirect both tiles to `/debt` (PM default, single source of truth); (b) ship dedicated `/reports/ar-ageing` + `/reports/ap-ageing` pages anyway (extra ~0.5 day). Recommend (a). Decision logged in PR body; can flip in a follow-up slice if (b) is preferred.
2. **Vendor return credit notes in supplier statements** — backend statement endpoint composes `supplier_invoice` (debit) + `supplier_payment` (credit) only. Vendor-return credit notes (Slice H.1) are not folded in. Treat as a known-gap TODO; not blocking. Cite in PR.
3. **Statement window default** — backend defaults to last 30 days when `from`/`to` omitted. FE should mirror (default the form to "last 30 days") for consistency.

## 7. Risks

- **Bundle size** — already mitigated by Slice I lazy-loading `xlsx` + `jspdf`. New pages must not eagerly import them. Verify `npm run build` doesn't grow the main chunk noticeably.
- **Deep-link links to `/orders/uid/...`** — `/orders` web feature doesn't exist yet (Phase 7 web is deferred per implementation-plan §88). Layby-ageing rows should still emit the link (cheap to keep) but the FE agent may render it as a disabled-looking placeholder until that feature lands.
- **Date locale** — TZ uses `YYYY-MM-DD`, currency `TZS` no decimals. Use the existing project formatter pipe per Slice I precedent.

## 8. Task list — parallel fan-out

| # | Owner | Deliverable | Acceptance |
|---|---|---|---|
| 1 | **qa-engineer** | Extend `e2e/reports.spec.ts` (or new `e2e/statements.spec.ts`) with 8 `test.fail` scenarios per §5. Confirm AR/AP tile redirect regression scenario points to `/debt`. | Spec compiles; lists 8+ tests. |
| 2 | **frontend-engineer** | All files in §3. New `customer-typeahead` mirrors `supplier-typeahead` shape exactly. Three new report pages share layout idiom with the existing slice-J pages (header strip + KPI tiles + table + 4 states + export menu). Update sidebar. `npm test` + `npm run build` green; main chunk size delta ≤ 50KB. | All green. |
| 3 | **integration** | Verify, QA-image rebuild + smoke. Confirm three new routes load. Confirm AR/AP tiles now redirect to `/debt`. | Stack UP. |

---

**Total estimate**: ~1.5 days. Pure frontend; backend untouched. Builds on already-shipped infrastructure (export menu + supplier picker; new customer picker mirrors the supplier one).
