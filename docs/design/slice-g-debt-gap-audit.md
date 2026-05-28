# Slice G — Debt module gap audit

Workflow-by-workflow diff of the current `/debt` placeholder hub
against the Slice G locked contract
(`docs/design/slice-g-debt-plan.md §1, §4, §5`). Mirrors the shape of
`slice-f-reports-gap-audit.md` and `slice-c-sales-gap-audit.md`.

Backend-engineer + frontend-engineer drive implementation from this;
PM uses it to scope.

## Source files audited

- `orbix-engine-web/src/app/features/debt/debt.component.ts`
  (placeholder hub — 4 shortcut tiles, no live data).
- `orbix-engine-api/src/main/java/com/orbix/engine/api/SalesAggregateReportController.java`
  (`/sales/reports/ar-summary` — aggregate counts only, no aging buckets).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/sales/service/SalesReportServiceImpl.java`
  (`arSummary` at line 186; no `arAging` method).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/sales/repository/SalesInvoiceRepository.java`
  (`sumOutstandingForBranch`, `countOpenForBranch`, `countOverdueForBranch`,
  `findOpenForBranch`, `findOverdueForBranch`, `findForStatement`,
  `sumOutstandingBefore` — every primitive needed for aging is there
  EXCEPT the per-bucket aggregate).
- `orbix-engine-api/src/main/java/com/orbix/engine/api/StatementReportController.java`
  (`/reports/customer-statement` + `/reports/supplier-statement` — live).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/sales/service/StatementReportServiceImpl.java`
  (full statement with running balance — works end-to-end).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/party/domain/entity/Customer.java`
  (`credit_limit_amount` + `credit_terms_days` — live since Slice A).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/sales/service/SalesInvoiceServiceImpl.java`
  (`checkCreditLimit` — credit-limit gate fires at POST; Slice C).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/iam/domain/enums/Permissions.java`
  (no `DEBT.*` namespace today — last grant is `SALES.REPORT.AR_SUMMARY`
  at line 111).
- `orbix-engine-web/src/app/features/party/customers.component.ts`
  + `party.models.ts` (credit-limit field already on the customer
  edit form).

---

## Section 1 — Customer statement

### GAP 1.A — Customer statement endpoint

- **Current state**: **Live.** `GET /api/v1/reports/customer-statement?customerId={id}&from=...&to=...`
  returns `PartyStatementDto` with opening balance, period entries
  (invoices, receipts, credit notes), period debits + credits, closing
  balance, and per-row running balance. Default window: last 30 days.
- **Backend gap**: **None.** `StatementReportServiceImpl#customerStatement`
  (line 42) covers the full F8.7 / US-RPT-007 contract; verified the
  query joins `SalesInvoice`, `SalesReceipt`, `CustomerCreditNote` and
  computes opening from `sumOutstandingBefore − sumReceiptsBefore −
  sumCreditNotesBefore`.
- **Frontend gap (UI)**: A `/reports/customer-statement` route exists
  (linked from the placeholder hub). Slice G adds a **deep-link
  button "View statement"** on the new customer-drill-down page that
  passes `customerId` through. No new statement component work.
- **Effort**: **S** (one-line link from the new debt drill-down).

---

## Section 2 — Aging buckets (the load-bearing gap)

### GAP 2.A — Per-customer aging endpoint

- **Current state**: `arSummary` returns three aggregate counts
  (`arOutstanding`, `openInvoices`, `overdueInvoices`) for a branch.
  It does NOT bucket by age and does NOT split by customer.
- **Backend gap**: **YES — new endpoint** `GET /api/v1/debt/aging`.
  JPQL aggregation grouping by customer with `CASE` expressions for
  the 5 buckets (`CURRENT`, `D_1_30`, `D_31_60`, `D_61_90`,
  `D_90_PLUS`). Backed by `ix_sales_invoice_branch_due` (Slice C
  GAP 1.B) for the predicate, plus `(customer_id)` index already on
  `sales_invoice` from the V27 baseline. Verify portability of the
  `function('datediff', :today, s.dueDate)` JPQL escape on MySQL +
  Postgres; if fragile, fall back to in-memory bucketing of rows
  pulled by `findOpenForBranch` + a `customerId` projection (plan §4
  paragraph "DB portability check").
- **DTO**: `ArAgingReportDto` with `asOf`, `branchId`, `currencyCode`,
  `AgingTotalsDto totals`, `List<CustomerAgingRowDto> rows`. Per-row
  fields: customer (id + uid + name), 5 bucket cells, total
  outstanding, oldest days-overdue, credit limit + utilisation.
- **Frontend gap**: A 5-bucket totals row at the top of `/debt` +
  per-customer rows below (each row drills to
  `/debt/customer/uid/{uid}`). Renders bucket filter (chips: All /
  Current / 1-30 / 31-60 / 61-90 / 90+) that re-reads the same
  endpoint with a `bucketFilter` query param (the dunning endpoint
  in §3 handles this — aging is the rollup; dunning is the paged
  drill).
- **RBAC**: `DEBT.READ` (new perm 130).
- **Effort**: **M** (one new JPQL query + one new DTO + one new
  controller endpoint + one new Angular page).

### GAP 2.B — Aging-bucket UI on dashboard?

- **Current state**: Dashboard has the AR-summary tile (count of open
  invoices + AR outstanding). It does NOT show the 5-bucket
  breakdown.
- **Backend gap**: None (the aging endpoint from 2.A is the source).
- **Frontend gap**: **OUT-OF-SCOPE for Slice G.** Surfacing the
  bucket breakdown as a new dashboard tile is a separate UX call;
  Slice F's dashboard work just landed and adding a new tile shape
  here would re-open a stabilised surface. Defer to a "dashboard
  v2" slice if priorities demand it. The drill-through from the
  existing AR tiles to `/debt` is the path forward.
- **Effort**: **N/A** (deferred).

---

## Section 3 — Dunning queue

### GAP 3.A — Sortable per-customer chase list

- **Current state**: `/debt` is the placeholder hub. There is no
  dunning queue today; the closest surface is `/sales/invoices?status=OVERDUE`
  (Slice F) which lists individual overdue invoices, not customers.
  A user trying to chase debt has to manually group the invoices by
  customer in their head.
- **Backend gap**: **YES — new endpoint** `GET /api/v1/debt/dunning`.
  Paged variant of the aging report, default-sorted by
  oldest-overdue desc, optional `bucketFilter` (single bucket) +
  `branchId` (optional). Page size 25 default. Reuses the same
  underlying JPQL as 2.A with a `Pageable` overload (or in-memory
  page-slice if the portability fallback is used — cheap at the
  expected scale).
- **DTO**: `PageDto<CustomerAgingRowDto>` (existing page envelope
  shape; the row DTO is shared with the aging report).
- **Frontend gap**: New `/debt` view replaces the placeholder. Table
  shape: customer name + 5 bucket cells + total outstanding +
  oldest-overdue days + credit utilisation + drill-down chevron.
  Sortable by total-outstanding-desc or oldest-overdue-desc;
  bucket-filter chips on top. Default sort = oldest-overdue desc
  ("chase the worst first").
- **RBAC**: `DEBT.READ`.
- **Effort**: **M** (folds with 2.A — same JPQL, paged variant).

### GAP 3.B — Customer drill-down (US-DEBT-001 surface)

- **Current state**: Nothing. Clicking a "Customer statements" tile
  on the placeholder hub lands on `/reports/customer-statement`,
  which is a statement view (chronological entries with running
  balance) — useful but NOT a debt position view.
- **Backend gap**: **YES — new endpoint** `GET /api/v1/debt/customer/uid/{uid}`.
  Returns `CustomerDebtPositionDto`: customer header, credit limit +
  utilisation, total outstanding + overdue count, 5-bucket aging row,
  open-invoices table (sorted by dueDate asc, max 100), recent
  receipts (last 30 days, max 50), chase notes (last 50, newest first).
- **Frontend gap**: New `/debt/customer/:uid` route + component. Five
  panels:
  1. Header — name + credit limit + utilisation bar + "Adjust limit"
     button (perm-gated) + "View statement" link to the existing
     statement page.
  2. Aging row — 5 bucket cells matching the dunning queue's.
  3. Open invoices table — invoice number → drills to existing
     `/sales/invoices` detail; status badge; days-overdue cell.
  4. Recent receipts — receipt number → drills to existing
     `/sales/receipts`.
  5. Chase notes activity log — newest first; append-form at top;
     archive button per note (perm-gated).
- **RBAC**: `DEBT.READ` for view; `DEBT.CREDIT_LIMIT.UPDATE` for the
  limit-adjust action; `DEBT.NOTE.CREATE` / `DEBT.NOTE.ARCHIVE` for
  the activity log mutations.
- **Effort**: **L** (largest single piece of UI work in the slice —
  one route, one component with five composable panels, drill-links
  to four existing routes).

---

## Section 4 — Chase notes / activity log

### GAP 4.A — `party_note` schema

- **Current state**: No `party_note` table; no notes on any party.
  `Party` entity carries only the static identification fields. The
  invoice + receipt history serves as an implicit activity log but
  is not editable.
- **Backend gap**: **YES — new migration** `V71__party_note.sql`
  (plan §6). Table: `(id, uid, company_id, party_id, kind, body,
  status, audit cols, version)`. Two indexes:
  `(party_id, created_at DESC)` for the drill-down activity log;
  `(company_id, kind, status)` for future reporting.
  Sequence `party_note_seq`. FK to `party` + `company`. No FK to
  `customer` directly — `party_id` is the join key so the table
  serves AR + AP symmetrically without schema change.
- **Entity**: `PartyNote` extends `UidEntity` per the hardening
  checklist. Lombok `@Data` + protected `@NoArgsConstructor` +
  `@EqualsAndHashCode(of = "id")`.
- **DTO**: `PartyNoteDto` (response: id, uid, partyId, kind, body,
  status, createdAt, createdBy, archivedAt nullable);
  `CreatePartyNoteRequestDto` (kind, body — `@NotBlank` + `@Size(max=1000)`).
- **Enum**: `PartyNoteKind` in `modules.party.domain.enums` —
  `AR_CHASE`, `AP_CHASE`, `GENERAL`. Seeded all three (plan §11
  open question 3); `AR_CHASE` used by this slice, others reserved.
- **Effort**: **M** (one migration + one entity + one DTO trio + one
  repository + one service interface + one impl).

### GAP 4.B — Append + list + archive endpoints

- **Backend gap**: **YES**.
  - `POST /api/v1/debt/customer/uid/{uid}/notes` — append (perm
    `DEBT.NOTE.CREATE`). Resolves customer → party, writes a row
    with `kind=AR_CHASE`. Returns 201 + Location.
  - `POST /api/v1/debt/notes/uid/{uid}/archive` — archive (perm
    `DEBT.NOTE.ARCHIVE`). Returns 200 + updated DTO. Re-archiving
    an `ARCHIVED` note throws (mirror Uom lifecycle).
  - GET (list) is bundled into the customer-position endpoint (3.B)
    — there is no standalone notes GET. If a future need surfaces
    for filtered/paged notes, add it then.
- **Service**: `PartyNoteService` in `modules.party.service` with
  `addCustomerNote(String customerUid, CreatePartyNoteRequestDto)`,
  `addSupplierNote(...)` (reserved for G.1, can stub),
  `archiveByUid(String)`, `findRecentByPartyId(Long, int limit)`.
- **Outbox events**: `DebtNoteCreated.v1` + `DebtNoteArchived.v1`
  (plan §7). Emitted in the same `@Transactional` write.
- **Effort**: **M** (folds with 4.A — same migration window).

### GAP 4.C — Activity log UI

- **Current state**: No notes UI anywhere.
- **Frontend gap**: Activity-log panel on the customer drill-down
  (3.B). Append-form at the top (textarea, char counter, submit).
  List below — each row: avatar / actor name / created-at / body /
  archive button (perm-gated). Archived notes hide by default,
  filterable in. Loading + empty + error states per the
  hardening-checklist UX bar.
- **Effort**: **S** (subordinate to 3.B's component).

---

## Section 5 — Customer credit limit (view + adjust)

### GAP 5.A — Credit limit display + utilisation

- **Current state**: Credit limit lives on `Customer.credit_limit_amount`
  and is visible on the customer-edit form (`customers.component.ts`).
  It is NOT surfaced on any debt-management page (no debt-management
  page exists). Credit-limit gating fires at invoice POST
  (`SalesInvoiceServiceImpl#checkCreditLimit`) but there is no
  read-side surface to inspect a customer's current utilisation.
- **Backend gap**: **None on the field itself** (it's already there).
  The `CustomerDebtPositionDto` from 3.B includes `creditLimit` +
  `creditUtilisation` (= `totalOutstanding / creditLimit`, null when
  `creditLimit == 0`).
- **Frontend gap**: Panel on the customer drill-down — credit limit
  amount, utilisation bar (green < 80%, amber 80-100%, red > 100%),
  available headroom. Same pattern as the existing dashboard tiles.
- **Effort**: **S** (one panel inside 3.B's component).

### GAP 5.B — Adjust credit limit from the debt surface

- **Current state**: Credit limit is editable on the customer-edit
  page (`/party/customers/edit/{uid}`). Reaching it requires
  navigating away from the debt surface and finding the customer
  manually.
- **Backend gap**: **YES — new endpoint** `POST /api/v1/debt/customer/uid/{uid}/credit-limit`.
  Body: `AdjustCreditLimitRequestDto(BigDecimal newLimit,
  String reason)`. Service-impl writes the new limit on `Customer`,
  audit-logs via `@Auditable`, emits `CustomerCreditLimitChanged.v1`
  (verify the existing customer-update path doesn't already emit
  this — if it does, reuse; if not, land in both paths).
  Recommendation in plan §7: **single perm** `DEBT.CREDIT_LIMIT.UPDATE`
  (not split into RAISE/LOWER — premature without an approval-queue
  vehicle).
- **Frontend gap**: "Adjust limit" button on the credit-limit panel
  (5.A). Modal with current value pre-filled, new value input, reason
  text. Submit → POST → refresh the drill-down. Perm-gated via
  `*orbixHasPermission="'DEBT.CREDIT_LIMIT.UPDATE'"`.
- **RBAC**: `DEBT.CREDIT_LIMIT.UPDATE` (new perm 133). Distinct from
  `CUSTOMER.UPDATE` so a future "credit-controller" persona is one
  perm grant away.
- **Effort**: **M** (one endpoint + modal + service wiring +
  outbox event).

---

## Section 6 — Supplier-AP equivalents

### GAP 6.A — Aging buckets / dunning / position for suppliers

- **Current state**: `/reports/supplier-statement` exists and works
  end-to-end (statement with opening balance, running balance — same
  shape as customer statement). There is NO supplier aging endpoint,
  NO supplier dunning queue, NO supplier debt-position view. AP
  outstanding math is done inline on `SupplierInvoiceRepository`
  + `SupplierPaymentRepository` (same shape as the customer-side
  `sumOutstandingBefore` / `sumPaymentsBefore`).
- **Backend gap (if landed)**: Mirror endpoints under `/api/v1/debt/ap-*`:
  `/ap-aging`, `/ap-dunning`, `/supplier/uid/{uid}`. Same DTO shapes
  with `Supplier` instead of `Customer`. Aging-bucket JPQL on
  `SupplierInvoiceRepository`. Reuses the `party_note` table for AP
  chase notes (kind `AP_CHASE`, already seeded).
- **Frontend gap (if landed)**: Mirror UI under `/debt` with a top-level
  AR / AP toggle, OR sibling routes `/debt/ar` + `/debt/ap`. The
  user's mental model is "/debt is a finance surface" so a single
  surface with the toggle is the right shape.
- **Recommendation**: **DEFER to Slice G.1.** Rationale (per plan
  §1 "Out"):
  1. Customer-AR alone is 2 days of work (the hard parts: aging
     JPQL portability check, new module shape, chase-notes schema,
     full drill-down UI). Adding AP doubles the surface area and
     blows the 2-day box.
  2. The user listed `/debt` as the surface to attack; "customer-AR
     first" is the natural reading.
  3. The Slice G work makes G.1 trivial: the `party_note` table
     already covers AP (kind `AP_CHASE` is seeded); the DTO shapes
     are templated; the permission band 134–137 is reserved (or use
     `DEBT.AP.*` if a namespace split is wanted — re-open ADR-0005
     then).
- **Effort (deferred)**: **M-L** for the whole G.1 (mirror of Slice G).

---

## Section 7 — Backend touchpoints (consolidated, Slice G only)

| File | Change |
|---|---|
| **NEW** `V70__seed_debt_permissions.sql` | Perms 130–133 + role_permission grant to role 1 (ADMIN). |
| **NEW** `V71__party_note.sql` | Table + 2 indexes + sequence + FKs. |
| **NEW** `modules.party.domain.entity.PartyNote` | Entity extends `UidEntity`. Lombok per checklist. |
| **NEW** `modules.party.domain.enums.PartyNoteKind` | `AR_CHASE`, `AP_CHASE`, `GENERAL`. |
| **NEW** `modules.party.domain.enums.PartyNoteStatus` | `ACTIVE`, `ARCHIVED`. |
| **NEW** `modules.party.domain.dto.PartyNoteDto` (record). | Response shape. |
| **NEW** `modules.party.domain.dto.CreatePartyNoteRequestDto` (record). | `@NotBlank` body, `@Size(max=1000)`, `@NotNull` kind. |
| **NEW** `modules.party.repository.PartyNoteRepository` | `findByUid`, `findByPartyIdAndStatusOrderByCreatedAtDesc(Long, PartyNoteStatus, Pageable)`. |
| **NEW** `modules.party.service.PartyNoteService` + `PartyNoteServiceImpl` | `addCustomerNote`, `addSupplierNote`, `archiveByUid`, `findRecentByPartyId`. `@Auditable` on writes; outbox emission on writes. |
| **NEW** `modules.sales.domain.dto.ArAgingReportDto` (record). | + nested `AgingTotalsDto`, `CustomerAgingRowDto`. |
| **NEW** `modules.sales.domain.dto.CustomerDebtPositionDto` (record). | + nested `OpenInvoiceRowDto`, `RecentReceiptRowDto`. |
| **NEW** `modules.sales.domain.dto.AdjustCreditLimitRequestDto` (record). | `@NotNull` newLimit, `@PositiveOrZero`, `@Size(max=500)` reason. |
| **NEW** `modules.sales.domain.enums.AgingBucket` | `CURRENT`, `D_1_30`, `D_31_60`, `D_61_90`, `D_90_PLUS`. |
| **NEW** `modules.sales.service.DebtReadModelService` + `*Impl` | `aging(branchId, asOf)`, `dunning(branchId, bucketFilter, pageable)`, `customerPosition(String uid)`, `adjustCreditLimit(String uid, AdjustCreditLimitRequestDto)`. Reads via existing `SalesInvoiceRepository` + `SalesReceiptRepository` + `Customer` lookup. |
| `modules.sales.repository.SalesInvoiceRepository` | Add `findOpenWithCustomerForBranch(companyId, branchId, pageable)` (or aggregation query if JPQL CASE+datediff portability holds — see plan §4). |
| `modules.party.repository.CustomerRepository` | Add `findByUid(String)` if absent (verify; precedent: `findByPartyId` exists). |
| **NEW** `com.orbix.engine.api.DebtController` | 5 endpoints per plan §5.1. |
| **NEW** `com.orbix.engine.api.PartyNoteController` | 1 endpoint per plan §5.2 (archive). |
| `modules.iam.domain.enums.Permissions` | Add `DEBT.*` section with 4 constants. |
| `modules.sales.README.md` | Add new "Debt read model" sub-section; published events table updated with `CustomerCreditLimitChanged.v1` if newly emitted here. |
| `modules.party.README.md` | Add "Party notes" sub-section + published events `DebtNoteCreated.v1` / `DebtNoteArchived.v1`. |
| `architecture.ModuleBoundaryTest` | **No change.** New code stays within existing seams (sales → party via service interface, already covered by the broad `..service..` allowance — re-verify, escalate if it tightens differently after Slice C's ArchUnit refactor). |

## Section 8 — Frontend touchpoints (consolidated, Slice G only)

| File | Change |
|---|---|
| `features/debt/debt.component.ts` | **Replace** — dunning queue (5-bucket totals row, customer rows, sort + bucket-filter, RouterLink drill-down). |
| **NEW** `features/debt/customer-debt-position.component.ts` | Customer drill-down page — 5 panels (header / aging row / open invoices / recent receipts / chase notes activity log). |
| **NEW** `features/debt/debt.service.ts` | `aging()`, `dunning(opts)`, `customer(uid)`, `addNote(customerUid, body)`, `archiveNote(noteUid)`, `adjustCreditLimit(customerUid, req)`. |
| **NEW** `features/debt/debt.models.ts` | `AgingTotals`, `CustomerAgingRow`, `CustomerDebtPosition`, `OpenInvoiceRow`, `RecentReceiptRow`, `PartyNote`, `AgingBucket`. All Long-id fields typed as `string`. |
| **NEW** `features/debt/debt.routes.ts` | `/` → `DebtComponent` (dunning queue); `customer/:uid` → `CustomerDebtPositionComponent`. |
| `app.routes.ts` | Mount `/debt` lazy with the new `debt.routes.ts` (currently mounts `DebtComponent` directly). |
| `features/sales/invoices.component.ts` | **Verify only** — overdue drill-through from dashboard already lands here (Slice F). No change unless QA spec surfaces a navigation gap. |

## Section 9 — Persona impact

**Recommendation: extend `accountant`. No new personas.**

Today (`test-users.ts:112-142`):
- `accountant` already holds AR-surface perms (`SALES.REPORT.AR_SUMMARY`,
  `SALES.MANAGE_INVOICE`) + cash perms + `STOCK.COUNT` +
  `PROCUREMENT.MANAGE_LPO.READ` (post-Slice-F widening).

Slice G additions to `accountant`:
- `DEBT.READ` — happy-path on `/debt` and `/debt/customer/uid/*`.
- `DEBT.NOTE.CREATE` — append chase notes.
- `DEBT.NOTE.ARCHIVE` — archive a chase note.
- `DEBT.CREDIT_LIMIT.UPDATE` — adjust limits from the debt surface.

`sales-clerk` deliberately does NOT get `DEBT.READ` — they post
invoices, they don't chase. The QA spec exercises this as the 403-path
persona (mirror of how `sales-clerk` is the negative-path persona for
AR-summary in Slice C).

`store-manager` already has `STOCK.COUNT` + day perms but no AR
visibility. They DO NOT get `DEBT.READ` in Slice G — branch managers
are not credit controllers. The QA spec exercises this as the
"403-on-debt + happy-on-stock" cross-check persona.

**No new persona.** A future "credit-controller" persona is one role
edit away from accountant (drop `CASH.*` perms, keep `DEBT.*` +
`SALES.REPORT.AR_SUMMARY`) if the user-org demands the split.

## Section 10 — Tests

| Gap | Test |
|---|---|
| **GAP 10.A** | `DebtReadModelServiceImplTest` — `aging()` covers: empty company, one customer one bucket, one customer all 5 buckets, multi-customer ordering by oldest-overdue, branch scoping, `asOf` override (e.g. report aging as of 2026-05-01). |
| **GAP 10.B** | `DebtReadModelServiceImplTest#dunning` — `bucketFilter` null returns all rows; `D_31_60` returns only customers with non-zero in that bucket; pagination respects size/page; default sort. |
| **GAP 10.C** | `DebtReadModelServiceImplTest#customerPosition` — happy path; non-existent uid → `NoSuchElementException`; cross-tenant customer uid → `NoSuchElementException` (tenant guard); customer with zero limit → `creditUtilisation` is null. |
| **GAP 10.D** | `DebtReadModelServiceImplTest#adjustCreditLimit` — happy path; persists new value; emits `CustomerCreditLimitChanged.v1`; audit entry written; negative limit → 400. |
| **GAP 10.E** | `PartyNoteServiceImplTest` — addCustomerNote happy path; addCustomerNote rejects empty body; addCustomerNote rejects unknown customer uid; archive happy path; double-archive throws; cross-tenant uid throws. |
| **GAP 10.F** | `SalesInvoiceRepositoryTest` — new aging-bucket query (or fall-back) tested with seed rows spanning all 5 buckets. Run on H2/MySQL-mode AND H2/PG-mode (or container-based dialect runner) to verify `datediff` portability. If aggregation can't be expressed portably, document the in-memory fallback and ensure performance for ≥ 1k open invoices. |
| **GAP 10.G** | JSON wire-shape pin `ArAgingReportDtoJsonTest` — customerId Long stringifies; BigDecimals stay numeric; bucket cells stay numeric; nested totals nested-DTO renders. |
| **GAP 10.H** | JSON wire-shape pin `CustomerDebtPositionDtoJsonTest` — all Long ids stringify; nested DTOs render; null `creditUtilisation` renders as `null`. |
| **GAP 10.I** | JSON wire-shape pin `PartyNoteDtoJsonTest` — id + partyId stringify; createdAt as ISO instant; archived note renders `status: "ARCHIVED"`. |
| **GAP 10.J** | `ModuleBoundaryTest` re-run — no new cross-module dependency to declare. If it tightens (e.g. post-Slice-C ArchUnit refactor introduces a narrower default), DebtController stays in `api/` package and reads via existing interface seams — should remain green. Escalate if not. |
| **GAP 10.K** | `debt.spec.ts` Playwright spec — ~10 scenarios (plan §9 task 1 + the persona-403 paths). Includes the axe-core accessibility check on the new pages. |
| **GAP 10.L** | Web unit tests on `DebtService` (HTTP mocking, error paths) + `DebtComponent` + `CustomerDebtPositionComponent` (loading/empty/error/populated states). |

## Section 11 — Verification (QA-image smoke)

Owned by qa-engineer. Backend should expect the QA-image rebuild +
wipe + smoke flow on:

- **Accountant logs in** → opens `/debt` → sees 5-bucket totals row +
  dunning queue. Sorts by oldest-overdue. Filters to `D_31_60` bucket.
- **Accountant drills into a customer** → sees credit-limit panel +
  aging row + open invoices + recent receipts + chase notes (empty
  list initially).
- **Accountant appends a chase note** → note appears at top of the
  activity log; refresh persists; outbox `domain_event` row exists.
- **Accountant adjusts credit limit** → new value persists; refresh
  shows the new utilisation; `CustomerCreditLimitChanged.v1` event in
  `domain_event`; audit entry visible.
- **Accountant archives the chase note** → note disappears (or shows
  as archived if "show archived" toggle is on); `DebtNoteArchived.v1`
  event in `domain_event`.
- **Accountant clicks "View statement"** → lands on
  `/reports/customer-statement?customerId=<id>` with the right
  customer pre-loaded (no regression to the existing statement
  surface).
- **Sales-clerk logs in** → opens `/debt` → 403 / "Permission required"
  state. Direct URL `/debt/customer/uid/{uid}` → 403.
- **Store-manager logs in** → opens `/debt` → 403. Opens `/stock`
  surfaces (the existing happy paths) — still green.

---

## Cross-cutting (summary for backend-engineer)

- **One new flyway migration pair** (`V70` perms + `V71` party_note).
  Pre-stability rule: this is a new table, so a new migration is
  fine; do not edit `V8__party.sql`.
- **One new aging-bucket JPQL query** with a portability fallback.
  Default-try the JPQL `CASE` + `function('datediff')`; if H2
  dialect-mode tests fail, fall back to in-memory bucketing of rows
  pulled by an extended `findOpenWithCustomerForBranch` projection.
- **One new module-resident service** (`DebtReadModelService` in
  `modules/sales/service`) + one cross-cutting service in
  `modules/party/service` for notes.
- **Two new controllers** flat in `com.orbix.engine.api`.
- **Three new outbox events** (or two new + one reused — verify the
  existing customer-update path).
- **One new perm namespace** `DEBT.*` (4 codes, band 130–133).
- **No new module-boundary ADR** required for the data dependencies.
  The new `DEBT.*` namespace + the deliberate non-creation of a
  `debt_entry` ledger are the ADR's content (ADR-0005).

## Cross-cutting (summary for frontend-engineer)

- **Replace the placeholder hub** at `/debt`.
- **Add one new route** `/debt/customer/:uid` and one new component.
- **Add one new feature service + models file**.
- **Reuse existing routes** for the statement (`/reports/customer-statement`)
  and individual invoices (`/sales/invoices`) — no changes there.
- **All Long ids on the new models typed as `string`** (global
  serialiser modifier). URLs use uid; body joins use stringified id.
- **Four states on each new screen** — loading skeleton, empty, error
  (dismissable alert), populated. Axe-core accessibility green.

## Open questions

Three — both in `slice-g-debt-plan.md §11`:

1. **Aging bucket boundaries** — `0-30/31-60/61-90/90+` (recommended,
   matches US-DEBT-003) or `0-7/8-30/31-60/61-90/90+` (matches the
   layby precedent)?
2. **`DEBT.CREDIT_LIMIT.UPDATE` single perm or split** into
   `.RAISE` + `.LOWER`? Recommendation: single perm.
3. **`PartyNoteKind` seeded values** — `AR_CHASE` only or all three
   (`AR_CHASE`, `AP_CHASE`, `GENERAL`) now? Recommendation: all
   three (reversible).

## Total gap count by section

| Section | Gaps |
|---|---|
| 1 — Customer statement | 1 (already live; just a deep-link) |
| 2 — Aging buckets | 1 load-bearing (2.A) + 1 deferred (2.B) |
| 3 — Dunning queue | 2 (queue endpoint + drill-down) |
| 4 — Chase notes | 3 (schema + endpoints + UI) |
| 5 — Credit limit | 2 (display + adjust) |
| 6 — Supplier-AP | 0 in-scope (1 deferred) |
| 7 — Backend touchpoints | 22 file changes (12 new files + 10 edits) |
| 8 — Frontend touchpoints | 6 file changes (4 new files + 2 edits) |
| 9 — Persona | 1 widening (accountant) |
| 10 — Tests | 12 (10.A–10.L) |
| 11 — QA smoke | 0 (owned by qa-engineer) |
| **Total** | **~28 file changes across 9 numbered in-scope gaps** |

The four highest-leverage gaps:

1. **GAP 2.A — Aging endpoint** — the single piece of net-new
   backend data this slice introduces. Once the bucket query is
   solved (and portability verified), the rest is plumbing.
2. **GAP 3.B — Customer drill-down (US-DEBT-001)** — the
   largest single piece of UI. Composes credit-limit panel +
   aging row + open invoices + receipts + notes log. Drives the
   slice's perceived value for the operator.
3. **GAP 4.A + 4.B — Party notes schema + endpoints** — generalises
   so Slice G.1 (supplier-AP) gets the chase-note half for free
   when it lands. The investment pays back twice.
4. **GAP 5.B — Adjust credit limit from the debt surface** —
   short-circuits the "open another tab, find the customer, edit,
   come back" workflow that the placeholder hub forced.

## What's intentionally NOT in scope (Slice G)

- **Standalone `modules/debt/` Java module.** Extends `modules/sales` +
  `modules/party` instead (plan §3 ratifies; ADR-0005 records).
- **`debt_entry` ledger table.** Invoice + receipt + credit-note
  tables ARE the AR ledger; do not duplicate.
- **Supplier-AP** (aging, dunning, position, chase-notes-for-AP).
  Defer to Slice G.1 — reuses every piece of Slice G's groundwork.
- **PDF statement export** (US-DEBT-005). No PDF infra today.
- **Automated dunning emails / SMS / credit-bureau integration**.
  No notification infra today.
- **US-DEBT-004 (write-off with dual approval)**. Separate slice
  with its own approval gate.
- **Allowance-for-doubtful-accounts accounting**. GL territory;
  out of scope.
- **Dashboard aging tile** (5-bucket breakdown on the dashboard).
  Stabilised in Slice F just last week; do not re-open.
- **Per-bucket dashboard drill-throughs** (e.g. click "31-60"
  dashboard tile → land on filtered dunning queue). Defer until
  the aging surface lands and the operator habit is observed.

Backend-engineer can start after the qa-engineer's failing
`debt.spec.ts` lands (plan task #1).
