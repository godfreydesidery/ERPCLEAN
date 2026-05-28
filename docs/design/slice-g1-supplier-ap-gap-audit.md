# Slice G.1 — Supplier-AP gap audit

Workflow-by-workflow diff of the current AP debt surface (none — Slice
G shipped customer-AR only) against the Slice G.1 locked contract
(`docs/design/slice-g1-supplier-ap-plan.md §1, §5, §6`). Mirrors the
shape of `slice-g-debt-gap-audit.md` so backend-engineer +
frontend-engineer can fan out the same way.

## Source files audited

- `orbix-engine-api/src/main/java/com/orbix/engine/api/DebtController.java`
  (Slice G — 9 endpoints for customer-AR; no supplier endpoints).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/sales/service/DebtReadModelServiceImpl.java`
  (Slice G — the read-model pattern to mirror; in-memory bucketing at
  line 80 + lines 280-330 accumulator).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/procurement/domain/entity/SupplierInvoice.java`
  (lives; carries dueDate, totalAmount, paidAmount, status —
  everything the aging scan needs).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/procurement/repository/SupplierInvoiceRepository.java`
  (`findForStatement` + `sumOutstandingBefore` exist; **no
  `findAllOpenForAging`** — load-bearing gap).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/cash/domain/entity/SupplierPayment.java`
  (lives; payment_date, totalAmount, status).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/cash/repository/SupplierPaymentRepository.java`
  (`findForStatement` + `sumPaymentsBefore` exist; no
  "recent payments for supplier" method).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/party/domain/entity/Supplier.java`
  (paymentTermsDays + creditLimitAmount + defaultCurrencyCode shipped
  in V7 baseline; we surface `paymentTermsDays` read-only).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/party/service/PartyNoteServiceImpl.java`
  (kind-agnostic; AP_CHASE works through the existing addNote path).
- `orbix-engine-api/src/main/resources/db/migration/common/V23__supplier_invoices.sql`
  (only `(company_id, status)` + `(supplier_id)` indexes on
  `supplier_invoice` — the `(company_id, branch_id, due_date, status)`
  mirror of `ix_sales_invoice_branch_due` is missing).
- `orbix-engine-api/src/main/java/com/orbix/engine/modules/iam/domain/enums/Permissions.java`
  (DEBT.* band 130-133 already present, lines 113-117 — G.1 adds
  nothing).
- `orbix-engine-web/src/app/features/debt/debt.component.ts`
  + `debt-customer.component.ts` + `debt.routes.ts` + `debt.service.ts`
  + `debt.models.ts` (Slice G — customer-AR only; need AP tab +
  drill-down + service + model extensions).
- `orbix-engine-web/e2e/test-users.ts` (accountant persona holds
  `DEBT.*`; no widening needed).
- `orbix-engine-web/e2e/debt.spec.ts` (Slice G AR spec — extend, do
  not replace).

---

## Section 1 — Supplier statement

### GAP 1.A — Supplier statement endpoint

- **Current state**: **Live.** `GET /api/v1/reports/supplier-statement?supplierId={id}&from=...&to=...`
  returns `PartyStatementDto` with opening balance, period entries
  (supplier invoices + supplier payments), period debits + credits,
  closing balance, and per-row running balance
  (`StatementReportServiceImpl#supplierStatement` line 97). Default
  window: last 30 days. Mirror of the customer statement, same DTO
  shape.
- **Backend gap**: **None.** The endpoint covers the full F8.7 /
  US-RPT-007 contract and is already wired.
- **Frontend gap (UI)**: A `/reports/supplier-statement` route exists
  today (linked from the Slice G placeholder hub before it was
  replaced). G.1 adds a **deep-link button "View statement"** on the
  new supplier-drill-down page that passes `supplierId` (the
  numeric handle inside the DTO) through. No new statement component
  work.
- **Effort**: **S** (one-line deep link from the new supplier
  drill-down).

---

## Section 2 — Supplier aging buckets (the load-bearing gap)

### GAP 2.A — Per-supplier aging endpoint

- **Current state**: No supplier aging endpoint. AP-outstanding math
  lives in `SupplierInvoiceRepository.sumOutstandingBefore` (statement
  use only). There is no bucket-by-age or per-supplier rollup.
- **Backend gap**: **YES — new endpoint** `GET /api/v1/debt/supplier-aging`.
  Mirror of the AR `/aging` endpoint:
  - New repository method `SupplierInvoiceRepository.findAllOpenForAging(companyId, branchId)`
    — JPQL pulling every POSTED + PARTIALLY_PAID supplier invoice with
    `totalAmount > paidAmount`, ordered by `supplierId asc, dueDate asc, id asc`.
  - New service `SupplierDebtReadModelService` + `Impl` in
    `modules/procurement/service/`. `aging(Long branchId, LocalDate asOf)`
    pulls everything open, groups in Java with the same 5-bucket
    accumulator pattern as `DebtReadModelServiceImpl` (lines 282-330),
    sorts by oldest-overdue desc.
  - In-memory bucketing — **same portability call as Slice G**: avoid
    JPQL `function('datediff', ...)` because MariaDB and Postgres
    disagree on the signature. At the expected scale (≤ ~5k open
    supplier invoices per company per branch) the in-memory rollup is
    cheap.
- **DTO**: `SupplierAgingDto` (new, in
  `modules.procurement.domain.dto`) with `asOf`, `branchId`,
  `currencyCode`, `Totals totals`, `List<SupplierRow> rows`. Per-row
  fields mirror the AR `DebtAgingDto.CustomerRow` exactly with
  `customer*` → `supplier*` and `creditLimit/creditUtilisation` →
  `paymentTermsDays` (the supplier-side surfaced policy field —
  read-only).
- **Frontend gap**: AP tab on `/debt` renders a 5-bucket totals row +
  per-supplier rows below (each row drills to
  `/debt/supplier/uid/{uid}`).
- **RBAC**: `DEBT.READ` (existing perm 130 — reused).
- **Effort**: **M** (one new JPQL method + one new DTO + one new
  service interface/impl + one new controller endpoint + frontend
  table render).

### GAP 2.B — `supplier_invoice` index for the aging scan

- **Current state**: `supplier_invoice` carries
  `ix_supplier_invoice_company_status` and
  `ix_supplier_invoice_supplier` (V23 baseline). **There is no
  `(company_id, branch_id, due_date, status)` index** — the AR mirror
  `ix_sales_invoice_branch_due` was added in V27 to back the Slice C
  AR-summary queries.
- **Backend gap**: **YES — new migration**
  `V72__supplier_invoice_branch_due_index.sql`:
  ```sql
  CREATE INDEX ix_supplier_invoice_branch_due
      ON supplier_invoice (company_id, branch_id, due_date, status);
  ```
  Backs `findAllOpenForAging` + the open-invoices-for-supplier scan on
  the drill-down. Without it the aging sweep falls back to
  `(company_id, status)` + filesort on `due_date`.
- **Effort**: **S** (one-line migration).

---

## Section 3 — Supplier obligations queue (the dunning-equivalent)

### GAP 3.A — Sortable per-supplier chase list

- **Current state**: Nothing. The closest is
  `/procurement/supplier-invoices?status=POSTED` (if it exists — not
  pinned in this audit) which lists invoices, not suppliers.
  The accountant has no surface to ask "who do we owe money to,
  ranked by oldest-overdue?"
- **Backend gap**: **YES — new endpoint** `GET /api/v1/debt/supplier-dunning`.
  Paged variant of `/supplier-aging`, default-sorted by oldest-overdue
  desc, optional `bucketFilter` (single bucket) + `branchId`. Page
  size 25 default. Reuses the same in-memory bucket pass —
  `SupplierDebtReadModelServiceImpl#dunning` mirrors
  `DebtReadModelServiceImpl#dunning` exactly.
- **DTO**: `PageDto<SupplierDunningQueueRowDto>` (existing page
  envelope, new row DTO). Row fields: supplier identity + paymentTermsDays
  + totalOutstanding + oldestDaysOverdue + oldestDueDate + worstBucket
  + overdueInvoiceCount.
- **Frontend gap**: New table render on the AP tab of `/debt`.
  Sortable by total-outstanding-desc or oldest-overdue-desc;
  bucket-filter chips on top. Default sort = oldest-overdue desc
  (chase the worst first).
- **RBAC**: `DEBT.READ` (reused).
- **Effort**: **M** (folds with 2.A — same in-memory pass, paged
  variant).

### GAP 3.B — Supplier drill-down (US-DEBT-002 surface)

- **Current state**: Nothing for AP. The supplier statement at
  `/reports/supplier-statement` is a chronological entries view —
  useful but NOT a debt position view.
- **Backend gap**: **YES — new endpoint** `GET /api/v1/debt/supplier/uid/{uid}`.
  Returns `SupplierStatementDto`: supplier header (name + currencyCode
  + paymentTermsDays read-only), total outstanding + overdue count,
  open AP invoices (sorted by dueDate asc, max 100), recent payments
  (last 30 days, max 50). Notes are pulled separately via the existing
  `GET /api/v1/debt/notes?customerUid=<supplierUid>&kind=AP_CHASE`
  endpoint (see Note 1 below).
- **Frontend gap**: New `/debt/supplier/uid/:uid` route + component.
  Five panels:
  1. Header — name + payment-terms-days (read-only, with explainer
     tooltip "From supplier master — change via /party/suppliers") +
     "View statement" link to `/reports/supplier-statement?supplierId=<id>`.
  2. Aging row — 5 bucket cells (computed inline from openInvoices).
  3. Open AP invoices table — supplier-invoice-number → drills to
     `/procurement/supplier-invoices/uid/{uid}`; days-overdue cell.
  4. Recent payments — payment-number → drills to
     `/cash/supplier-payments/uid/{uid}` (or the existing supplier
     payment surface, whichever is canonical).
  5. AP chase notes activity log — newest first; append-form at top
     (posts to existing `/api/v1/debt/notes` with `kind = 'AP_CHASE'`);
     archive button per note (perm-gated by existing
     `DEBT.NOTE.ARCHIVE`).
- **RBAC**: `DEBT.READ` for view; `DEBT.NOTE.CREATE` /
  `DEBT.NOTE.ARCHIVE` for the activity log mutations. All existing
  perms — no new band.
- **Effort**: **L** (largest single piece of UI work in G.1 — but
  smaller than Slice G's customer drill-down because the credit-limit
  panel + adjust modal are gone).

**Note 1 — list-notes endpoint reuse**: The Slice G `GET /api/v1/debt/notes?customerUid=...`
endpoint resolves uid → party then lists `kind = AR_CHASE` only,
hard-coded. G.1 has two options:
- (a) Add a `kind` query param to the existing endpoint (preferred —
  one extra optional `@RequestParam(required = false) PartyNoteKind kind`
  on the controller method, default to `AR_CHASE` for backwards-compat).
- (b) Add a sibling `/api/v1/debt/notes/supplier?supplierUid=...`.
Recommendation: **(a)**, one-line controller change. Plan §1 absorbs
this as part of the G.1 backend work without listing it as a separate
gap — it's a 5-line touch.

---

## Section 4 — AP chase notes / activity log

### GAP 4.A — `party_note` schema

- **Current state**: **Live.** Slice G shipped `party_note` (V71) +
  `PartyNote` entity + `PartyNoteDto` + `CreatePartyNoteRequestDto`
  + `PartyNoteRepository` + `PartyNoteService` + `Impl`. Kind enum
  values `AR_CHASE`, `AP_CHASE`, `GENERAL` all seeded.
- **Backend gap**: **None.** G.1 reuses the entire stack as-is.
- **Effort**: **None.**

### GAP 4.B — Append + list + archive endpoints

- **Current state**: **Live.** `POST /api/v1/debt/notes` accepts a
  `CreatePartyNoteRequestDto` with `kind` already on the body —
  the service is kind-agnostic
  (`PartyNoteServiceImpl#addNote` line 39).
  `POST /api/v1/debt/notes/uid/{uid}/archive` is kind-agnostic.
  `GET /api/v1/debt/notes?customerUid=...` currently filters to
  `kind = AR_CHASE` (see Note 1 in §3.B above).
- **Backend gap**: **One small extension** — add optional `kind`
  query param to the list endpoint so the supplier drill-down can
  fetch `kind = AP_CHASE` notes. Or rename the path
  `?customerUid=` → `?partyUid=` (see GAP 4.D).
- **Effort**: **S** (5-line controller change + service-layer
  parameter passthrough).

### GAP 4.C — Activity log UI

- **Current state**: AR-only on `/debt/customer/uid/:uid`. The
  append-form posts `kind: 'AR_CHASE'` and lists call with the
  customer's UID.
- **Frontend gap**: Activity-log panel on the supplier drill-down
  (3.B). Append-form at the top (textarea, char counter, submit
  posts `kind: 'AP_CHASE'`). List below. Same component skeleton as
  the AR side — share the panel component between AR and AP drill-downs
  parameterised by `kind` + `partyUid`.
- **Effort**: **S** (panel re-use; ~30 lines net).

### GAP 4.D — Refactor `CreatePartyNoteRequestDto.customerUid` → `partyUid`

- **Current state**: The DTO field is named `customerUid`
  (`CreatePartyNoteRequestDto.java:15`) but the entity is
  party-scoped. The Slice G design generalised the table for AR + AP
  but kept a customer-leaning field name on the wire — readable for
  Slice G, misleading for G.1 (`{ "customerUid": "01HZ...", "kind":
  "AP_CHASE", ... }` reads as "a customer uid with kind AP_CHASE",
  which is confusing).
- **Backend gap**: **Optional refactor**. Rename:
  - `CreatePartyNoteRequestDto.customerUid` → `partyUid`
  - `PartyNoteServiceImpl#addNote` → `request.partyUid()` call site
  - `partyService.requireInCompanyByUid(request.partyUid())`
  - Existing list endpoint `?customerUid=` → `?partyUid=` query param
    rename
- **Frontend gap**: 2 call sites in `debt.service.ts` (createNote +
  listNotes) and 0 in the components (they use the service).
- **Effort**: **S** (mechanical rename). Mark as **low-priority** —
  ship G.1 with the misleading name if the QA gate is tight; rename
  in a 1-commit cleanup later. The Slice G payload is pre-staging so
  there is no compat surface to preserve.
- **Decision recommendation**: Do it in the same G.1 PR if there is
  headroom — the rename is 10 lines total. Skip only if the gate is
  tight.

---

## Section 5 — Supplier credit-limit / payment-terms (display only)

### GAP 5.A — Payment-terms display

- **Current state**: `Supplier.paymentTermsDays` is editable on the
  supplier-edit form at `/party/suppliers/edit/{uid}`. It is NOT
  surfaced on any AP debt page (no AP debt page exists).
- **Backend gap**: **None on the field itself** (already on
  `Supplier`). The `SupplierStatementDto` from 3.B includes
  `paymentTermsDays` (read-only).
- **Frontend gap**: Panel on the supplier drill-down — read-only
  payment-terms-days with explainer tooltip "Change via supplier
  master". No bar / utilisation gauge equivalent (payment terms is a
  days integer, not a percentage). Mirrors the AR side's credit-limit
  panel shape but read-only.
- **Effort**: **S** (one panel inside 3.B's component).

### GAP 5.B — Adjust payment terms from the debt surface

- **Recommendation**: **OUT-OF-SCOPE for G.1.** Per the task brief:
  > suppliers don't really have credit limits we adjust; instead,
  > payment-terms might be the editable handle. Recommend: defer
  > payment-terms editing; out-of-scope
- **Rationale**: Payment-terms changes in the real world are
  negotiated with the supplier, not set unilaterally by the
  accountant — surfacing an "Adjust terms" button on the debt page
  invites a workflow we don't want. If the user-org demands it later,
  add `DEBT.PAYMENT_TERMS.UPDATE` (band 134) in a follow-up.
- **Effort**: **N/A** (deferred).

### GAP 5.C — Supplier credit-limit display

- **Current state**: `Supplier.creditLimitAmount` is editable on the
  supplier-edit form. This is **our credit limit FROM the supplier**
  (how much we can buy on credit before they cut us off), not a
  limit we set on them. Surfacing it on the AP debt page is
  meaningful (it tells the accountant "we're at 80% of our line of
  credit with this supplier — should we slow down on POs?").
- **Recommendation**: **Defer to G.2 / follow-up.** Slice G.1 keeps
  the AP drill-down read-only and shows only `paymentTermsDays`. The
  credit-limit-from-supplier surface is a different workflow (PO
  planning, not chase) that doesn't compete for screen real estate on
  the debt page. If the user-org demands it, add a 6th panel to
  the supplier drill-down in a follow-up.
- **Effort**: **N/A** (deferred).

---

## Section 6 — Backend touchpoints (consolidated, G.1 only)

| File | Change |
|---|---|
| **NEW** `V72__supplier_invoice_branch_due_index.sql` | One-line index on `(company_id, branch_id, due_date, status)` mirroring `ix_sales_invoice_branch_due`. |
| `modules.procurement.repository.SupplierInvoiceRepository` | Add `findAllOpenForAging(Long companyId, Long branchId)` (JPQL) + `findOpenForSupplier(Long supplierId)` (or `Pageable` variant). |
| `modules.cash.repository.SupplierPaymentRepository` | Add `findRecentPostedForSupplier(Long supplierId, LocalDate fromDate, Pageable pageable)` (POSTED payments dated `>= fromDate`, ordered desc). |
| **NEW** `modules.procurement.domain.dto.SupplierAgingDto` (record) | + nested `Totals`, `SupplierRow`. Mirrors `DebtAgingDto` field shape. |
| **NEW** `modules.procurement.domain.dto.SupplierDunningQueueRowDto` (record) | Mirror of `DunningQueueRowDto` with `customer*` → `supplier*` and `creditLimit` → `paymentTermsDays`. |
| **NEW** `modules.procurement.domain.dto.SupplierStatementDto` (record) | + nested `OpenInvoiceRow`, `RecentPaymentRow`. Mirror of `CustomerStatementDto`. |
| **NEW** `modules.procurement.service.SupplierDebtReadModelService` | Interface — 3 read methods: `aging`, `dunning`, `supplierStatement`. No writes. |
| **NEW** `modules.procurement.service.SupplierDebtReadModelServiceImpl` | Implementation — same in-memory bucket pattern as `DebtReadModelServiceImpl` (lines 75-149 + accumulator at 282-330). Reads `SupplierInvoiceRepository` + `SupplierPaymentRepository` + `SupplierRepository` + `PartyRepository`. |
| **NEW** `com.orbix.engine.api.SupplierDebtController` | 3 GET endpoints (`/supplier-aging`, `/supplier-dunning`, `/supplier/uid/{uid}`). `@PreAuthorize("hasAuthority('DEBT.READ')")` class-level. Flat in the `api` package per the existing layout. |
| `com.orbix.engine.api.DebtController` | **Small extension** — add optional `kind` query param to `listNotes(...)` so the supplier drill-down can fetch `kind = AP_CHASE`. 5-line change. Default `AR_CHASE` for backwards-compat. |
| `modules.party.service.PartyNoteService` + `Impl` | **One method signature change** — `listNotesForCustomerUid(...)` gains an optional `PartyNoteKind kind` parameter (or add a second method `listNotesForPartyUid(...)`). Service signature is internal, no compat constraint. |
| **OPTIONAL** `modules.party.domain.dto.CreatePartyNoteRequestDto` | Rename `customerUid` → `partyUid` (GAP 4.D). Mechanical. Skip if the QA gate is tight. |
| `modules.procurement.README.md` | Add new "Supplier debt read model" sub-section. Note: no new published events (the existing `PartyNoteCreated.v1` / `PartyNoteArchived.v1` carry the `kind` payload field; reporting subscribers filter on it). |
| `architecture.ModuleBoundaryTest` | **No change.** New code stays within existing seams (procurement → party via `..service..` interface for supplier-name resolution, already covered by the broad allowance). |
| `modules.iam.domain.enums.Permissions` | **No change.** Existing `DEBT_READ` / `DEBT_NOTE_CREATE` / `DEBT_NOTE_ARCHIVE` constants cover G.1. |

## Section 7 — Frontend touchpoints (consolidated, G.1 only)

| File | Change |
|---|---|
| `features/debt/debt.component.ts` | **Extend** — add `signal<'AR' \| 'AP'>('AR')` active-tab signal + tab UI control. Render AR table on `'AR'`, AP table on `'AP'`. Share the `BucketCells` template between the two. |
| **NEW** `features/debt/debt-supplier.component.ts` | Supplier drill-down page — 5 panels (header with read-only payment-terms / aging row / open AP invoices / recent payments / AP chase notes activity log). Mirror of `debt-customer.component.ts` minus the credit-limit edit modal. |
| **NEW** `features/debt/chase-notes-panel.component.ts` (optional refactor) | Lift the AR drill-down's chase-notes panel into a shared component parameterised by `partyUid` + `kind` so AR + AP drill-downs share the code. Optional but recommended — saves ~80 lines duplicated. Mark as **low-priority** if QA gate is tight. |
| `features/debt/debt.service.ts` | Add 3 new methods: `supplierAging(branchId?, asOf?)`, `supplierDunning(branchId?, bucketFilter?, page?, size?)`, `supplierStatement(uid)`. Extend `listNotes(...)` to accept an optional `kind` argument. Extend `createNote(...)` to accept `kind` (it already does — just exercise it with `'AP_CHASE'`). |
| `features/debt/debt.models.ts` | Add `SupplierAging`, `SupplierAgingRow`, `SupplierDunningQueueRow`, `SupplierStatement`, `OpenSupplierInvoiceRow`, `RecentSupplierPaymentRow`. All Long-id fields typed as `string`. |
| `features/debt/debt.routes.ts` | Add `{ path: 'supplier/uid/:uid', loadComponent: () => import('./debt-supplier.component').then(m => m.DebtSupplierComponent) }`. |
| `e2e/debt.spec.ts` | Extend with ~5 new scenarios (see §9 GAP 10.K below). |

## Section 8 — Persona impact

**Recommendation: no widening.** `accountant` persona
(`test-users.ts:112-159`) already holds `DEBT.READ`, `DEBT.NOTE.CREATE`,
`DEBT.NOTE.ARCHIVE` — exactly what G.1 needs. `DEBT.CREDIT_LIMIT.UPDATE`
is unused in G.1.

`sales-clerk` continues to be the 403-path persona on AR endpoints
(`debt.spec.ts` already exercises this); G.1 extends the spec to
also pin sales-clerk 403 on the new AP endpoints.

`procurement-officer` is **not** widened. They post LPOs and GRNs;
they do not chase what we owe. This is intentional — the AP side
might intuitively belong to procurement, but the workflow (chase
exposure, manage cash) is finance, not procurement. The QA spec
pins this with a new "procurement-officer 403 on `/api/v1/debt/*`"
scenario.

`store-manager` is **not** widened. Already 403'd on `/debt` by Slice
G; same 403 covers the new AP endpoints by class-level annotation.

**No new persona.** A future "AP-only credit-controller" sub-persona
is one role-edit away from accountant if the user-org demands the
split — but the perms are not yet factored that way and the plan §2
explicitly rejects splitting `DEBT.READ` into `DEBT.AR.READ` /
`DEBT.AP.READ` for G.1.

## Section 9 — Tests

| Gap | Test |
|---|---|
| **GAP 10.A** | `SupplierDebtReadModelServiceImplTest` — `aging()` covers: empty company, one supplier one bucket, one supplier all 5 buckets, multi-supplier ordering by oldest-overdue, branch scoping, `asOf` override. Mirror of `DebtReadModelServiceImplTest`. |
| **GAP 10.B** | `SupplierDebtReadModelServiceImplTest#dunning` — `bucketFilter` null returns all rows; `D_31_60` returns only suppliers with non-zero in that bucket; pagination respects size/page; default sort. |
| **GAP 10.C** | `SupplierDebtReadModelServiceImplTest#supplierStatement` — happy path; non-existent uid → `NoSuchElementException`; cross-tenant supplier uid → `NoSuchElementException` (tenant guard); supplier with zero paymentTermsDays → field renders 0 (not null). |
| **GAP 10.D** | `SupplierInvoiceRepositoryTest` — `findAllOpenForAging` covers: empty company, single open invoice, mix of POSTED + PARTIALLY_PAID + PAID + CANCELLED (only the first two come back), branch-scoped filter, `branchId = null` returns company-wide. Seed rows hit all 5 buckets so the in-memory pass test exercises the boundary cases (0/1, 30/31, 60/61, 90/91 days). |
| **GAP 10.E** | `SupplierPaymentRepositoryTest` — `findRecentPostedForSupplier` covers: only POSTED payments come back, date filter excludes older rows, ordering is `payment_date desc, id desc`, pagination respects limit. |
| **GAP 10.F** | JSON wire-shape pin `SupplierAgingDtoJsonTest` — supplierId Long stringifies; BigDecimals stay numeric; bucket cells stay numeric; `Totals.supplierCount` stays numeric long (not stringified — it's a count, not an id); nested totals nested-DTO renders. |
| **GAP 10.G** | JSON wire-shape pin `SupplierStatementDtoJsonTest` — all Long ids stringify (supplierId, invoiceId, paymentId); paymentTermsDays renders as numeric integer; nested DTOs render. |
| **GAP 10.H** | JSON wire-shape pin `SupplierDunningQueueRowDtoJsonTest` — Long ids stringify; `overdueInvoiceCount` stays numeric long (count, not id); enum `worstBucket` renders as `"D_31_60"` string. |
| **GAP 10.I** | `ModuleBoundaryTest` re-run — no new cross-module dependency to declare. `SupplierDebtReadModelServiceImpl` reads `PartyRepository` (already allowed; the AR-side `DebtReadModelServiceImpl` does the same). Escalate if it tightens unexpectedly. |
| **GAP 10.J** | `debt.spec.ts` Playwright extension — ~5 new scenarios: (1) accountant opens `/debt`, clicks AP tab, sees supplier aging totals; (2) drills into a supplier with overdue AP; (3) appends an AP_CHASE note; (4) archives the AP note; (5) sales-clerk 403 on `/supplier-aging`; (6) procurement-officer 403 on `/api/v1/debt/*`. Includes axe-core accessibility check on the new pages. |
| **GAP 10.K** | Web unit tests on the extended `DebtService` (3 new methods + extended listNotes/createNote) + `DebtSupplierComponent` (loading/empty/error/populated states) + AR/AP tab switch on `DebtComponent`. |

No new portability test needed — the in-memory bucket pattern is
portable by construction (no `function('datediff', ...)`). The existing
Slice G portability story carries over.

## Section 10 — Verification (QA-image smoke)

Owned by qa-engineer. Backend should expect the QA-image rebuild +
wipe + smoke flow on:

- **Accountant logs in** → opens `/debt` → AR tab renders (Slice G
  regression). Clicks AP tab → sees 5-bucket totals row + supplier
  dunning queue.
- **Accountant drills into a supplier** → sees payment-terms-days
  panel + aging row + open AP invoices + recent payments + AP chase
  notes (empty list initially).
- **Accountant appends an AP chase note** → note appears at top of
  the activity log; refresh persists; outbox `domain_event` row
  exists with `eventType = 'PartyNoteCreated.v1'` and payload
  `kind: 'AP_CHASE'`.
- **Accountant archives the AP chase note** → note disappears (or
  shows as archived if toggle on); `PartyNoteArchived.v1` event in
  `domain_event`.
- **Accountant clicks "View statement" on the supplier drill-down** →
  lands on `/reports/supplier-statement?supplierId=<id>` with the
  right supplier pre-loaded (no regression to the existing statement
  surface).
- **Accountant happy-path on AR side stays green** (regression check
  — confirms tab toggle didn't break the existing
  `debt-customer.component.ts`).
- **Sales-clerk logs in** → opens `/debt` → 403 / "Permission
  required" state (regression). Direct URL
  `/debt/supplier/uid/{uid}` → 403. `/api/v1/debt/supplier-aging` →
  403.
- **Procurement-officer logs in** → opens `/debt` → 403 (cross-check
  — confirms AP doesn't accidentally fall under procurement perms).
  Direct URL `/debt/supplier/uid/{uid}` → 403.
- **Store-manager logs in** → opens `/debt` → 403 (regression).

---

## Cross-cutting (summary for backend-engineer)

- **One new flyway migration** (`V72` supplier_invoice index). No
  schema changes to `party_note` or `supplier_invoice` columns.
- **One new in-memory aging pass** in
  `SupplierDebtReadModelServiceImpl` — template from
  `DebtReadModelServiceImpl` lines 75-149 + accumulator 282-330.
- **One new module-resident service** in
  `modules/procurement/service/`. No new module created.
- **One new controller** (`SupplierDebtController`) flat in
  `com.orbix.engine.api`. `DebtController` gets a 5-line extension
  for the `kind` query param on listNotes.
- **Zero new outbox events.** `PartyNoteCreated.v1` and
  `PartyNoteArchived.v1` already cover both AR and AP via the `kind`
  payload field.
- **Zero new permissions.** Existing `DEBT.*` band 130-133 covers
  G.1 entirely.
- **Zero new ADR.** ADR-0005 already covers G.1.
- **One optional refactor** — rename
  `CreatePartyNoteRequestDto.customerUid` → `partyUid` (GAP 4.D).
  Low-priority. Mechanical.

## Cross-cutting (summary for frontend-engineer)

- **Extend the `/debt` landing page** with an AR/AP tab toggle. The
  AR table stays as-is; the AP table is new.
- **Add one new route** `/debt/supplier/:uid` and one new component
  (mirror of `debt-customer.component.ts` minus the credit-limit
  modal).
- **Extend `debt.service.ts`** with 3 new GET methods + optional
  `kind` parameter on `listNotes` + `createNote`.
- **Extend `debt.models.ts`** with the supplier-side types.
- **Reuse existing routes** for the statement
  (`/reports/supplier-statement`) and individual invoices /
  payments — no changes there.
- **All Long ids on the new models typed as `string`** (global
  serialiser modifier). URLs use uid; body joins use stringified id.
- **Four states on each new screen** — loading skeleton, empty,
  error (dismissable alert), populated. Axe-core accessibility green.
- **Optional refactor** — lift the chase-notes panel into a shared
  component parameterised by `partyUid` + `kind`. Low-priority; saves
  ~80 duplicated lines if done.

## Open questions

**None requiring user input.** Three call-outs for visibility (not
blocking):

1. **Sibling `SupplierDebtController` vs extend `DebtController`** —
   plan recommends sibling controller for cohesion (`DebtController`
   already has 9 endpoints). Soft call.
2. **Tab toggle on `/debt` vs sibling routes `/debt/ar` + `/debt/ap`**
   — plan recommends the tab (operator workflow is one page).
3. **`customerUid` → `partyUid` DTO rename** — plan recommends doing
   it in the same G.1 PR if there is headroom, otherwise punt to a
   cleanup commit.

## Total gap count by section

| Section | Gaps |
|---|---|
| 1 — Supplier statement | 1 (already live; just a deep-link) |
| 2 — Supplier aging | 1 load-bearing (2.A) + 1 index (2.B) |
| 3 — Supplier dunning | 2 (queue endpoint + drill-down) |
| 4 — AP chase notes | 0 schema/endpoints (reused) + 1 list-endpoint extension + 1 optional rename |
| 5 — Payment terms | 1 display + 2 deferred |
| 6 — Backend touchpoints | 12 file changes (6 new files + 6 edits; 1 migration) |
| 7 — Frontend touchpoints | 6 file changes (2 new + 4 edits) |
| 8 — Persona | 0 (accountant already covers) |
| 9 — Tests | 11 (10.A–10.K) |
| 10 — QA smoke | 0 (owned by qa-engineer) |
| **Total** | **~18 file changes across 7 numbered in-scope gaps (half of Slice G's 28)** |

The four highest-leverage gaps:

1. **GAP 2.A — Supplier aging endpoint** — the single piece of
   net-new backend data G.1 introduces. Mechanical mirror of the AR
   side; in-memory bucketing is already templated. Once the JPQL
   `findAllOpenForAging` lands on `SupplierInvoiceRepository`, the
   rest of the read model falls out.
2. **GAP 3.B — Supplier drill-down (US-DEBT-002)** — the largest
   single piece of UI. Smaller than the AR drill-down because no
   credit-limit edit modal. Drives G.1's perceived value for the
   operator.
3. **GAP 2.B — `supplier_invoice` branch+due index** — small but
   load-bearing. Without it the aging scan falls back to filesort.
   One-line migration.
4. **GAP 4.B — listNotes endpoint `kind` parameter** — 5-line touch
   that unlocks AP_CHASE retrieval. Easy to overlook in the
   work-list.

## What's intentionally NOT in scope (G.1)

- **Payment-terms editing from the AP surface.** Read-only display
  only. ADR-0005 stays as-is. Defer to a follow-up if user-org
  demands it.
- **Supplier credit-limit display** (the limit the supplier sets on
  us). Different workflow (PO planning); doesn't compete for screen
  real estate with chase. Defer to a follow-up.
- **AR / AP permission split** (`DEBT.AR.READ` / `DEBT.AP.READ`).
  Premature without a corresponding role split.
- **Automated payment scheduling / bank file generation / email
  reminders / supplier credit-bureau integration.** No
  infrastructure today; out of scope.
- **AP write-off + dual approval** (supplier analogue of US-DEBT-004).
  Separate slice with its own approval gate.
- **`debt_entry` ledger table.** ADR-0005 reaffirmed; AP ledger is
  `supplier_invoice` + `supplier_payment` computed.
- **New outbox events.** Confirmed against the task brief
  constraint.
- **New top-level module `modules/debt`.** ADR-0005 reaffirmed.

Backend-engineer can start after the qa-engineer's `debt.spec.ts`
extension lands (plan task #1).
