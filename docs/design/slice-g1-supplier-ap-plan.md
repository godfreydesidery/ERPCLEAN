# Slice G.1 — Supplier-AP dunning (AP half of the debt module)

| Field | Value |
|---|---|
| Branch | `harden/debt-supplier-ap` |
| Prereqs | Slice G (`harden/debt-module`) merged through QA gate. `party_note` table + `PartyNoteKind.AP_CHASE` seeded · `DEBT.*` perm band 130-133 seeded · `accountant` persona widened with the full band · ADR-0005 ratified |
| Owner | PM coordinating; backend + frontend + qa |
| Date | 2026-05-28 |
| Target | 1 day end-to-end |

Slice G shipped the customer-AR debt surface. Slice G.1 mirrors it for
suppliers — same operator (the accountant chases what we owe, exactly
the way they chase what is owed to us). The infrastructure landed in G
is the leverage: `party_note` is party-scoped (covers AR + AP without
schema change), `PartyNoteKind.AP_CHASE` is already an enum value, the
`DEBT.*` permission band 130-133 already gates both sides per ADR-0005,
and the supplier-statement endpoint at `/api/v1/reports/supplier-statement`
shipped pre-Slice-C.

The gap is **aging buckets + dunning queue + per-supplier drill-down**,
mirroring `DebtReadModelServiceImpl` on the supplier side. Roughly half
the surface area of Slice G because there is no parallel work for
schema (`party_note` is shared), permissions (`DEBT.*` is shared), ADR
(0005 covers G.1), or persona (accountant already holds the full band).

Current state confirmed by code-read (not just docs):

- `/api/v1/debt/aging` + `/dunning` + `/statement` exist for customer-AR
  only (`DebtController.java:55-85`). No supplier-side endpoints.
- `DebtReadModelServiceImpl#aging` (line 75) reads
  `SalesInvoiceRepository.findAllOpenForAging(companyId, branchId)`
  (line 229) — JPQL pulls every POSTED + PARTIALLY_PAID invoice with
  `totalAmount > paidAmount` and aggregates the 5 buckets in Java. The
  in-memory roll-up was chosen over a JPQL CASE+`datediff()` aggregation
  for MariaDB/Postgres portability (`DebtReadModelServiceImpl.java:47-52`
  comment block). This is the pattern to mirror.
- `SupplierInvoiceRepository` (`SupplierInvoiceRepository.java:1-61`)
  exposes `findForStatement` and `sumOutstandingBefore` but **does not**
  have a `findAllOpenForAging` equivalent. New method needed.
- `SupplierPaymentRepository` (`SupplierPaymentRepository.java:1-53`)
  exposes `findForStatement` and `sumPaymentsBefore`. Sufficient for
  the per-supplier drill-down's "recent payments" list when extended
  with a `findRecentPostedForSupplier(supplierId, limit)` method.
- `Supplier` (`Supplier.java:20-57`) already carries `paymentTermsDays`
  + `creditLimitAmount` + `defaultCurrencyCode`. The `paymentTermsDays`
  field is the **only** editable handle that would be a meaningful
  supplier-side credit-policy adjustment — and per the task brief, we
  defer payment-terms editing.
- `PartyNoteService.addNote` (`PartyNoteServiceImpl.java:39`) accepts
  a `CreatePartyNoteRequestDto` with `kind` already on it — the
  service is kind-agnostic. The wire DTO field is named `customerUid`
  (`CreatePartyNoteRequestDto.java:15`) which is misleading for AP
  reuse: see §6 below for the recommended rename to `partyUid`.
- `supplier_invoice` table indexes (`V23__supplier_invoices.sql:39-40`)
  are `(company_id, status)` and `(supplier_id)`. **There is no
  `(company_id, branch_id, due_date, status)` index** — the AR side got
  `ix_sales_invoice_branch_due` in Slice C (`V27__sales_invoices.sql:58`).
  G.1 needs the mirror to make the AP aging scan cheap; see §6 below.
- `Permissions.java` already exposes `DEBT_READ`, `DEBT_NOTE_CREATE`,
  `DEBT_NOTE_ARCHIVE`, `DEBT_CREDIT_LIMIT_UPDATE`
  (`Permissions.java:113-117`). The first three are exactly what G.1
  needs; the fourth is intentionally not reused (see §2).
- `accountant` persona (`test-users.ts:112-159`) already holds the full
  `DEBT.*` band. **No persona widening** in G.1.

---

## 1. Scope

**In** (supplier-AP, procurement-domain extensions — no new module)

- **Supplier aging endpoint** `GET /api/v1/debt/supplier-aging` —
  per-supplier rollup of open AP invoices (`POSTED` +
  `PARTIALLY_PAID` with `totalAmount > paidAmount`) into the same 5
  buckets as the AR side (`CURRENT` / `D_1_30` / `D_31_60` / `D_61_90`
  / `D_90_PLUS`). Same JPQL-only + in-memory bucket pattern as
  `DebtReadModelServiceImpl#aging`. Returns per-supplier outstanding,
  oldest days-overdue, and **payment-terms-days** (read-only — the
  supplier-side equivalent of credit utilisation; G.1 surfaces it, G.1
  does not edit it).
- **Supplier obligations queue** `GET /api/v1/debt/supplier-dunning` —
  the "who do we owe money to" operator queue. Paged variant of the
  aging report, default-sorted by oldest-overdue desc (chase what's
  closest to default first), optional `bucketFilter` + `branchId`. Page
  size 25 default. Reuses the in-memory bucket pattern.
- **Supplier debt position** `GET /api/v1/debt/supplier/uid/{uid}` —
  single-supplier drill-down. Mirrors `CustomerStatementDto`:
  supplier header (name + payment-terms-days), 5-bucket aging row,
  open-invoices table (sorted by dueDate asc, max 100), recent
  payments (last 30 days, max 50), chase notes (last 50, newest
  first, kind = `AP_CHASE`). Read-only — no edit endpoints land in
  G.1.
- **AP chase notes** — reuse existing `party_note` table via
  `PartyNoteService.addNote(CreatePartyNoteRequestDto)` with
  `kind = AP_CHASE`. The service is already kind-agnostic; G.1 just
  exercises the existing path with a supplier UID. Renaming
  `CreatePartyNoteRequestDto.customerUid` → `partyUid` is the only
  minor refactor (see §6).
- **`DebtController` extension** — add three new GET endpoints
  (`/supplier-aging`, `/supplier-dunning`, `/supplier/uid/{uid}`). Do
  not grow the class beyond ~9 endpoints; if a sibling controller is
  cleaner, lift the supplier endpoints to a new
  `SupplierDebtController` flat in `com.orbix.engine.api`. See §3.
- **`SupplierDebtReadModelService`** — new interface + `Impl` in
  `modules/procurement/service/`. Same shape as
  `DebtReadModelService` minus the credit-limit-adjust write
  method. Reads `SupplierInvoiceRepository` +
  `SupplierPaymentRepository` + `SupplierRepository` + `PartyRepository`.
- **One supporting index migration**
  `V72__supplier_invoice_branch_due_index.sql` —
  `CREATE INDEX ix_supplier_invoice_branch_due ON supplier_invoice
  (company_id, branch_id, due_date, status)` mirroring
  `ix_sales_invoice_branch_due`. Required for the aging scan to be
  cheap on the QA box (current indexes are `(company_id, status)` +
  `(supplier_id)` — neither suits a branch + due-date sweep).
- **Frontend AP surface** — extend `/debt` with an AR/AP tab toggle at
  the top, OR add sibling routes `/debt/ar` (existing dunning queue
  moves here) + `/debt/ap` (new). Recommendation in §4: **tab toggle
  on `/debt`, no route-level split** — keeps the operator's URL
  surface unchanged and matches the user's mental model of `/debt` as
  a single finance page.
- **Hardening-checklist pass** on the three new endpoints (uid URLs,
  JSON pin tests on `SupplierDebtAgingDto` + `SupplierDunningQueueRowDto`
  + `SupplierStatementDto`, ArchUnit, JPQL only, multi-tenant
  predicate, e2e gate, axe-core green).

**Out** (deferred)

- **Payment-terms editing from the AP surface** — `Supplier.paymentTermsDays`
  is editable on the supplier-edit page (`/party/suppliers/edit/{uid}`).
  G.1 surfaces the value read-only on the drill-down; it does not add
  a write endpoint. Rationale: payment-terms changes in the real world
  are negotiated with the supplier, not set unilaterally by the
  accountant — surfacing an "Adjust terms" button on the debt page
  invites a workflow we don't want. If the user-org demands it later,
  add `DEBT.PAYMENT_TERMS.UPDATE` (band 134) in a follow-up and reopen
  the question with one paragraph in ADR-0005.
- **Automated payment scheduling** — no payment-batch infrastructure;
  out of scope.
- **Bank file generation** (CSV / pain.001 / etc.) — no integration
  shim today; out of scope.
- **Email/SMS reminders** — no notification infrastructure (same
  reason customer dunning emails were deferred in Slice G); out of
  scope.
- **Supplier credit checks / bureau queries** — no integration; out of
  scope.
- **AP write-off + dual approval** (the supplier analogue of US-DEBT-004)
  — separate slice with its own approval gate, same shape as the
  deferred AR write-off; out of scope.
- **New outbox events** — G.1 introduces **zero** new event types.
  Note create/archive already emits `PartyNoteCreated.v1` /
  `PartyNoteArchived.v1` regardless of kind (Slice G payload includes
  `kind`). No supplier-side mutation in G.1 means no
  `SupplierCreditLimitChanged.v1` or `SupplierPaymentTermsChanged.v1`.
  Confirmed against the constraint in the task brief.

**Prerequisites confirmed**

- Slice G merged through QA: `party_note` table (V71) and
  `seed_debt_permissions` (V70) on `main`. `accountant` persona has
  `DEBT.*` band. `PartyNoteKind.AP_CHASE` enum value present and
  documented as "reserved for Slice G.1".
- `SupplierInvoiceRepository.findForStatement` /
  `sumOutstandingBefore` and `SupplierPaymentRepository.findForStatement`
  / `sumPaymentsBefore` live since F8.7 / US-RPT-007.
- `Supplier.paymentTermsDays` and `Supplier.creditLimitAmount` shipped
  in the party baseline (V7).

## 2. Permission band

Current high-water across all migrations: **133** (`DEBT.CREDIT_LIMIT.UPDATE`,
V70, Slice G). G.1 consumes **zero** new permission ids.

**No new perms.** The task brief and ADR-0005 both pin this. Re-stated:

| Slice G perm | Reused in G.1 for | Why this is sufficient |
|---|---|---|
| `DEBT.READ` (130) | `/api/v1/debt/supplier-aging`, `/supplier-dunning`, `/supplier/uid/{uid}` | Class-level grant on `DebtController` (or `SupplierDebtController`). Same operator workflow as AR. |
| `DEBT.NOTE.CREATE` (131) | `POST /api/v1/debt/notes` with `kind = AP_CHASE` | Already used by Slice G; `PartyNoteService` is kind-agnostic. |
| `DEBT.NOTE.ARCHIVE` (132) | `POST /api/v1/debt/notes/uid/{uid}/archive` | Kind-agnostic — archives an AR note or an AP note. |
| `DEBT.CREDIT_LIMIT.UPDATE` (133) | **not used** | Supplier credit-limit on us is a static field we don't adjust; payment-terms editing is deferred. |

**Decision: do NOT split `DEBT.READ` into `DEBT.AR.READ` + `DEBT.AP.READ`.**
Defence — the operator workflow is one persona (`accountant`) doing
one job (chase exposure, both sides), so a perm split would be
modelling theatre without a corresponding role split. The Tanzania-first
deployment target has small finance teams where the accountant owns
both sides. If a larger user-org later wants an AR-only or AP-only
sub-persona, splitting the perm at that point is a one-migration
band-134/135 refactor — the `DEBT.*` namespace is designed exactly to
make that split painless (each endpoint has its own controller and
class-level annotation, so flipping from `hasAuthority('DEBT.READ')` to
`hasAuthority('DEBT.AP.READ')` is mechanical). **ADR-0005 is not
amended.**

Band 134, 135 stay reserved for slice fix-ups.

## 3. Controller shape — extend or sibling?

**Recommendation: lift the supplier endpoints into a new
`SupplierDebtController` flat in `com.orbix.engine.api`.** Reasons:

1. `DebtController` already holds 9 endpoints (aging / dunning /
   statement-by-customerId-query / statement-by-uid / credit-limit-adjust
   / 4 note operations). Adding 3 more puts it at 12 and tips it past
   the "one controller per resource family" eyeball heuristic the
   project follows elsewhere (`SalesInvoiceController`,
   `SalesReceiptController`, `SupplierInvoiceController`, etc. — each
   stays in the 4–10 endpoint band).
2. The two controllers stay symmetrical with the service layer:
   `DebtController` → `DebtReadModelService` (sales-AR) ;
   `SupplierDebtController` → `SupplierDebtReadModelService`
   (procurement-AP). The naming surfaces the data ownership without
   coupling the URL namespace.
3. **URLs stay under `/api/v1/debt/*`**. The controller split is
   internal — the operator sees `/api/v1/debt/supplier-aging`,
   `/api/v1/debt/supplier-dunning`, `/api/v1/debt/supplier/uid/{uid}`,
   matching the AR side's `/aging`, `/dunning`, `/statement`. The
   `DEBT.*` permission namespace stays a single concept at the URL
   layer (ADR-0005's promise). One `SupplierDebtController` with
   `@RequestMapping("/api/v1/debt")` is the seam.
4. Note CRUD endpoints (`/api/v1/debt/notes`, `/notes/uid/{uid}`,
   `/notes/uid/{uid}/archive`) **stay on `DebtController`** — they're
   kind-agnostic at the controller layer (the request DTO carries
   the kind). G.1 does not duplicate them.

Concretely:

- **`SupplierDebtController`** (new, flat in `com.orbix.engine.api`):
  ```java
  @RestController
  @RequestMapping("/api/v1/debt")
  @PreAuthorize("hasAuthority('DEBT.READ')")
  public class SupplierDebtController { ... }
  ```
  Three GETs (`/supplier-aging`, `/supplier-dunning`, `/supplier/uid/{uid}`).
- **`SupplierDebtReadModelService`** + `Impl` in
  `modules/procurement/service/`. Mirror methods: `aging(branchId, asOf)`,
  `dunning(branchId, bucketFilter, pageable)`,
  `supplierStatement(supplierUid)`. No write methods.
- **`AgingBucket` enum** stays in `modules/sales/domain/enums/`. Both
  controllers reference it via the `..domain.enums..` allowance —
  `ModuleBoundaryTest` accepts the cross-module read because enums are
  whitelisted (and the AR side already exports it for the AR aging
  surface). **Do not duplicate** the enum on the procurement side.
- **No new ADR-0004 named exemption.** The cross-module call from
  `SupplierDebtControllerImpl` (actually the service in procurement) to
  `PartyService` for supplier-name resolution is the same shape as the
  Slice G `DebtControllerImpl → PartyService` read — already covered
  by the broad `..service..` allowance.

If the user prefers extending `DebtController` for procedural cohesion
(one controller for everything debt), the 3 endpoints fit — the call is
soft. The plan recommends the sibling controller; the gap-audit §3
documents both paths.

## 4. Frontend shape — tab vs route split

**Recommendation: tab toggle on `/debt`, no new top-level routes.** The
`/debt` landing page gains an `AR | AP` tab control at the top. The
existing dunning-queue UI stays on the AR tab; the new
supplier-obligations queue lands on the AP tab. The customer
drill-down (`/debt/customer/uid/:uid`) and the new supplier drill-down
(`/debt/supplier/uid/:uid`) are siblings under the same feature folder
(`features/debt/`).

Forces:

1. The operator (`accountant`) does both sides in one workflow. A
   single page with a tab keeps the muscle memory ("open `/debt`,
   skim, drill into whoever's worst").
2. The dashboard tile for `/debt` (Slice F) doesn't need to change —
   it still points at one route.
3. Route-level `/debt/ar` + `/debt/ap` would force a redirect from the
   bare `/debt` and a decision about which tab is default, both of
   which the tab pattern dodges.
4. The drill-down routes stay parameterised by aggregate (`customer`
   vs `supplier`) which is the natural shape and reads cleanly in the
   URL.

Concretely:

- `features/debt/debt.component.ts` — extend with a `signal<'AR' | 'AP'>('AR')`
  active-tab signal; the two table renders share a layout component.
- **NEW** `features/debt/debt-supplier.component.ts` — supplier drill-down
  page (mirror of `debt-customer.component.ts`). Five panels (header
  with payment-terms read-only, aging row, open AP invoices, recent
  payments, AP chase notes activity log + append form).
- `features/debt/debt.service.ts` — add three new methods:
  `supplierAging(branchId?, asOf?)`,
  `supplierDunning(branchId?, bucketFilter?, page?, size?)`,
  `supplierStatement(uid)`. Reuse `createNote`, `listNotes`,
  `archiveNote` with `kind = 'AP_CHASE'`.
- `features/debt/debt.models.ts` — add `SupplierAgingRow`,
  `SupplierDunningQueueRow`, `SupplierStatement`,
  `OpenSupplierInvoiceRow`, `RecentSupplierPaymentRow`. Long-id fields
  typed as `string` per the global serialiser modifier.
- `features/debt/debt.routes.ts` — add
  `{ path: 'supplier/uid/:uid', loadComponent: () => ... }`.
  No top-level redirect; the existing `/debt` route handles tab
  switching client-side.

## 5. Endpoints — explicit

### 5.1 `SupplierDebtController` (new, `/api/v1/debt`)

```java
@RestController
@RequestMapping("/api/v1/debt")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('DEBT.READ')")
public class SupplierDebtController {

    private final SupplierDebtReadModelService readModel;

    // Aging — AP rollup. Per-supplier rows with 5 buckets.
    @GetMapping("/supplier-aging")
    public SupplierAgingDto supplierAging(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return readModel.aging(branchId, asOf);
    }

    // Dunning-equivalent — supplier obligations queue.
    @GetMapping("/supplier-dunning")
    public PageDto<SupplierDunningQueueRowDto> supplierDunning(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) AgingBucket bucket,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<SupplierDunningQueueRowDto> rows =
            readModel.dunning(branchId, bucket, PageRequest.of(page, size));
        return PageDto.of(rows, r -> r);
    }

    // Single-supplier debt position (US-DEBT-002 surface).
    @GetMapping("/supplier/uid/{uid}")
    public SupplierStatementDto supplier(@PathVariable @ValidUlid String uid) {
        return readModel.supplierStatement(uid);
    }
}
```

Note-CRUD endpoints stay on `DebtController` — G.1 does not add or
modify them. The frontend posts `kind = 'AP_CHASE'` to the existing
`POST /api/v1/debt/notes`.

### 5.2 DTO shapes

```java
public record SupplierAgingDto(
    LocalDate asOf,
    Long branchId,
    String currencyCode,
    Totals totals,
    List<SupplierRow> rows
) {

    public record Totals(
        BigDecimal current,
        BigDecimal d1_30,
        BigDecimal d31_60,
        BigDecimal d61_90,
        BigDecimal d90_plus,
        BigDecimal totalOutstanding,
        long supplierCount
    ) {}

    public record SupplierRow(
        Long supplierId,
        String supplierUid,
        String supplierName,
        BigDecimal current,
        BigDecimal d1_30,
        BigDecimal d31_60,
        BigDecimal d61_90,
        BigDecimal d90_plus,
        BigDecimal totalOutstanding,
        Integer oldestDaysOverdue,
        Integer paymentTermsDays  // read-only; mirror of customer creditLimit slot
    ) {}
}

public record SupplierDunningQueueRowDto(
    Long supplierId,
    String supplierUid,
    String supplierName,
    Integer paymentTermsDays,
    BigDecimal totalOutstanding,
    Integer oldestDaysOverdue,
    LocalDate oldestDueDate,
    AgingBucket worstBucket,
    long overdueInvoiceCount
) {}

public record SupplierStatementDto(
    Long supplierId,
    String supplierUid,
    String supplierName,
    String currencyCode,
    Integer paymentTermsDays,
    BigDecimal totalOutstanding,
    long openInvoiceCount,
    long overdueInvoiceCount,
    LocalDate asOf,
    List<OpenInvoiceRow> openInvoices,
    List<RecentPaymentRow> recentPayments
) {

    public record OpenInvoiceRow(
        Long invoiceId,
        String invoiceUid,
        String number,
        String supplierInvoiceNo,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstanding,
        Integer daysOverdue,
        SupplierInvoiceStatus status
    ) {}

    public record RecentPaymentRow(
        Long paymentId,
        String paymentUid,
        String number,
        LocalDate paymentDate,
        Instant postedAt,
        BigDecimal totalAmount,
        String currencyCode
    ) {}
}
```

DTO field names mirror the AR side exactly (`current`, `d1_30`, ...) so
the frontend can share a `BucketCells` template.

## 6. Schema + repository touchpoints

### 6.1 New migration — `V72__supplier_invoice_branch_due_index.sql`

```sql
-- Slice G.1 — mirror of ix_sales_invoice_branch_due. Backs the AP aging
-- scan (SupplierDebtReadModelServiceImpl#aging). Without this index the
-- "all open invoices in this branch, sorted by due_date" sweep would
-- fall back to (company_id, status) + filesort.
CREATE INDEX ix_supplier_invoice_branch_due
    ON supplier_invoice (company_id, branch_id, due_date, status);
```

**Pre-stable schema rule** — adding an index on an existing table is
the correct pattern (no need to edit V23). The migration is
DB-agnostic.

### 6.2 Repository extensions

| Repo | New method | JPQL sketch |
|---|---|---|
| `SupplierInvoiceRepository` | `findAllOpenForAging(Long companyId, Long branchId)` | `select s from SupplierInvoice s where s.companyId = :companyId and (:branchId is null or s.branchId = :branchId) and s.status in (POSTED, PARTIALLY_PAID) and s.totalAmount > s.paidAmount order by s.supplierId asc, s.dueDate asc, s.id asc` |
| `SupplierInvoiceRepository` | `findOpenForSupplier(Long supplierId, Pageable pageable)` (or `List<SupplierInvoice> findOpenForSupplier(Long supplierId)` capped at 100) | open invoices for a single supplier, dueDate asc |
| `SupplierPaymentRepository` | `findRecentPostedForSupplier(Long supplierId, LocalDate fromDate, Pageable)` | POSTED payments dated `>= fromDate`, ordered `payment_date desc, id desc` |

JPQL only. No native queries. The portability test pattern from
Slice G (in-memory bucketing rather than `function('datediff', ...)`)
carries over verbatim — `SupplierDebtReadModelServiceImpl#aging`
pulls everything open with `findAllOpenForAging` and groups in Java.

### 6.3 Optional refactor — `CreatePartyNoteRequestDto.customerUid` → `partyUid`

The note request DTO's field is named `customerUid` but the service is
party-scoped (kind-agnostic, party_note table). G.1 should rename to
`partyUid` so AP_CHASE notes don't read as "customerUid for a
supplier" on the wire. This is a small refactor:

- **Backend**: rename the record component + adjust 1 service line + 1
  test fixture. Backwards-compatible JSON is **not** required because
  Slice G is pre-staging — no client outside `orbix-engine-web` is
  consuming the field yet.
- **Frontend**: rename `customerUid` → `partyUid` in `debt.service.ts`
  + 1 call site in `debt-customer.component.ts`.

**Mark as low-priority** in the gap audit. If the QA gate is tight,
ship G.1 with the misleading field name and rename in a 1-commit
cleanup later. Document the call either way in the gap audit §4.

## 7. Outbox events — none

G.1 introduces **zero** new outbox events. Confirmed against the task
brief constraint.

- `PartyNoteCreated.v1` / `PartyNoteArchived.v1` already emitted by
  `PartyNoteServiceImpl` regardless of kind (Slice G payload includes
  `kind`). The AP chase note path exercises the same emission with
  `kind = "AP_CHASE"`. Any future reporting subscriber filters by
  `kind`.
- No supplier-side mutation in G.1 (read-only payment-terms display)
  means no `SupplierCreditLimitChanged.v1` or
  `SupplierPaymentTermsChanged.v1`.

ADR-0004 sync-TX exemption inventory is unchanged.

## 8. Persona impact — none

**Recommendation: no persona widening.** `accountant` already holds
the full `DEBT.*` band 130-133 (`test-users.ts:155-158`), which is
exactly what G.1 needs.

`sales-clerk` continues to be the 403-path persona — they have neither
`DEBT.READ` nor any need to chase suppliers.

`procurement-officer` is **not** widened. They post LPOs and GRNs;
they do not chase what we owe. This is the same architectural call
the Slice G plan made for `sales-clerk` ("post invoices, don't
chase").

`store-manager` is **not** widened. Branch operator, not finance.

The QA spec exercises the same three personas as Slice G plus one
extension:

- **`accountant`** — happy path on `/debt` AR tab + AP tab + AP
  drill-down + AP chase note + AP note archive.
- **`sales-clerk`** — 403 on `/api/v1/debt/supplier-aging` and
  `/supplier-dunning` and `/supplier/uid/{uid}` (mirror of Slice G's
  AR-side 403 check).
- **`procurement-officer`** — 403 on `/api/v1/debt/*`. New cross-check
  persona — the AP side might intuitively belong to procurement, so
  the spec pins that **no, the perm split is correct** and
  procurement-officer is debt-blind by design.

## 9. Task list (TDD-style — QA first, backend second, frontend third)

| # | Owner | Deliverable | Acceptance signal |
|---|---|---|---|
| 1 | **qa-engineer** | Extend `debt.spec.ts` with ~5 AP scenarios: accountant opens `/debt` → AP tab → sees aging totals + supplier dunning rows; drills into a supplier with overdue AP invoices; appends an `AP_CHASE` note; archives it; sales-clerk + procurement-officer both 403 on the new endpoints. ~5 new scenarios on top of the existing 10, expected-fail on `main`. | Spec runs, ≥4 expected-fails. Land before backend work. |
| 2 | **backend-engineer** | Backend land: (a) `V72__supplier_invoice_branch_due_index.sql`; (b) `SupplierInvoiceRepository.findAllOpenForAging` + `findOpenForSupplier`; (c) `SupplierPaymentRepository.findRecentPostedForSupplier`; (d) `SupplierAgingDto`, `SupplierDunningQueueRowDto`, `SupplierStatementDto` (records) in `modules.procurement.domain.dto`; (e) `SupplierDebtReadModelService` + `Impl` in `modules.procurement.service`; (f) `SupplierDebtController` flat in `com.orbix.engine.api`; (g) JSON wire-shape pin tests on the three new DTOs; (h) optional rename `CreatePartyNoteRequestDto.customerUid` → `partyUid` (low-priority, see §6.3). **Zero new perms. Zero new outbox events. Zero new migrations on `party_note`.** | `mvn -pl orbix-engine-api test` green; ArchUnit green. |
| 3 | **frontend-engineer** | Web land: (a) tab control on `debt.component.ts` (AR / AP); (b) new `debt-supplier.component.ts` mirror of `debt-customer.component.ts`; (c) `debt.service.ts` extensions (3 new GET methods); (d) `debt.models.ts` extensions; (e) `debt.routes.ts` adds `/supplier/uid/:uid`; (f) chase-note append form posts `kind = 'AP_CHASE'`; (g) loading/empty/error/populated states on every new screen. | `npm test` + `npm run e2e` green; axe-core green on `/debt` (AR tab + AP tab) and `/debt/supplier/uid/:uid`. |
| 4 | **backend-engineer + frontend-engineer** | Hardening-checklist sweep: JSON pins committed, ArchUnit green, README "Published events" tables verified unchanged in `modules/party/README.md` + `modules/sales/README.md`; new section "Supplier debt read model" added in `modules/procurement/README.md`. | All 10 checklist boxes ticked in PR body. |
| 5 | **qa-engineer (final QA gate)** | Re-run `debt.spec.ts` (expected-fails removed), full e2e suite, QA-image rebuild from `orbix-engine-infra/qa/Dockerfile`, smoke against the QA container with all three personas (accountant happy AR + happy AP, sales-clerk 403 AR + 403 AP, procurement-officer 403 AP). | All scenarios green; QA report attached to PR. |
| 6 | **pm** | PR merge → sync `main` locally. Update `USER-STORIES.md` to mark US-DEBT-002 (Hardened — supplier statements) and US-DEBT-008 if it exists for AP dunning. | Main green; status report appended. |

No solutions-architect step. ADR-0005 already covers G.1 (it explicitly
calls out "Slice G.1 (supplier-AP dunning) inherits this decision" at
line 201). No new ADR is needed unless `DEBT.READ` is split (which we
are not doing).

## 10. Need ADR amendment?

**No.** ADR-0005 (`docs/decisions/0005-debt-namespace-and-no-debt-entry-ledger.md`)
already covers G.1 with two explicit references:

> [G.1] inherits this decision: same namespace, same module shape
> (extend `modules/procurement` for the AP read model, reuse
> `modules/party/party_note` for AP chase notes), no new ledger.
> — ADR-0005, "Engineering follow-up" §
>
> When supplier-AP dunning lands in Slice G.1, the same `DEBT.*`
> namespace covers it (chase notes already span AR + AP through the
> shared `party_note` table; aging endpoints mirror as `/api/v1/debt/ap-*`
> or `/debt/ar` + `/debt/ap` siblings).
> — ADR-0005, "Decision §1" §

The plan's call to put the supplier endpoints under
`/api/v1/debt/supplier-*` (not `/api/v1/debt/ap-*`) is a refinement of
the ADR's "or" clause — the rationale is symmetry with the URL
vocabulary the operator already uses (`/debt/customer/uid/{uid}` →
`/debt/supplier/uid/{uid}`), not an architectural call. ADR-0005 stays
as-is.

If the user-org later demands an AR / AP perm split, that's the ADR
amendment — but only at that point, with the role design that motivates
the split documented.

## 11. Open questions — none requiring user input

All three Slice G open questions resolved by inheritance:

1. **Aging bucket boundaries**: locked at `CURRENT / 1-30 / 31-60 /
   61-90 / 90+` from Slice G via `AgingBucket` enum reuse. No
   re-litigation.
2. **`DEBT.CREDIT_LIMIT.UPDATE` single perm vs split**: moot — G.1
   does not use this perm.
3. **`PartyNoteKind` seeded values**: already seeded
   (`AR_CHASE` + `AP_CHASE` + `GENERAL`); G.1 just exercises
   `AP_CHASE`.

The three calls G.1 makes that the user should know about (not
blocking, listed for visibility):

- **Sibling `SupplierDebtController` instead of growing `DebtController`**.
  Soft call; either works. Plan recommends the sibling.
- **Tab toggle on `/debt` instead of `/debt/ar` + `/debt/ap` routes**.
  Soft call; plan recommends the tab.
- **Defer the `customerUid` → `partyUid` DTO rename to a cleanup commit**
  if the QA gate is tight. Plan recommends doing it in the same PR if
  there is headroom, otherwise punt.

---

**Total estimate**: **1 day** end-to-end (QA spec + backend + frontend +
hardening sweep + QA gate). Smaller than Slice G's 2-day box because:

- Zero new permissions
- Zero new outbox events
- Zero new persona widening
- Zero new ADR
- One index migration (no schema changes to `party_note`)
- The in-memory bucket pattern is templated from Slice G — no
  portability investigation
- The DTOs are a mechanical mirror

The backend is ~half a day; the frontend is ~half a day. QA writes the
extension spec in parallel with backend so the integration gate
collapses to a single same-day check.
