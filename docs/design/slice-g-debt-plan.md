# Slice G — Standalone Debt Module (customer AR)

| Field | Value |
|---|---|
| Branch | `harden/debt-module` |
| Prereqs | Slice C (Sales invoice + receipt + AR-summary) merged · Slice F (dashboard drill-throughs + rollup) merged at `470ad1f` lineage |
| Owner | PM coordinating; backend + frontend + qa + architect |
| Date | 2026-05-28 |

Current state confirmed by code-read (not just docs):

- `/debt` is a **placeholder hub** (`debt.component.ts:23-31` — "Debt
  module coming soon" banner + four shortcut tiles routing to existing
  features). No backend module `com.orbix.engine.modules.debt` exists.
- AR aggregates are already live and feeding the dashboard: `arSummary`
  returns `arOutstanding` + `openInvoices` + `overdueInvoices`
  (`SalesReportServiceImpl#arSummary` at line 186). It does NOT bucket by
  age.
- Customer + supplier **statements already work end-to-end**:
  `StatementReportController#customerStatement` →
  `StatementReportServiceImpl#customerStatement` opens balance + period
  entries (invoices, receipts, credit notes) with running balance
  (`StatementReportServiceImpl.java:42-89`). The Angular route
  `/reports/customer-statement` exists today (linked from the debt hub).
- Customer **credit limit** is a first-class field on `Customer`
  (`Customer.java:26-27` — `credit_limit_amount` + `credit_terms_days`,
  `BigDecimal` precision 18,4). Slice C added the `SALES_INVOICE.OVERRIDE_CREDIT`
  permission and the post-time block in `SalesInvoiceServiceImpl#checkCreditLimit`.
  The party UI already shows + edits the limit
  (`customers.component.ts`).
- A `debt_entry` ledger is **referenced everywhere** (US-DEBT-001 to
  US-DEBT-007, ARCHITECTURE.md, multiple module READMEs) but **does not
  exist** as a table or entity. Outstanding-debt math is done inline from
  the invoice + receipt tables (`SalesInvoiceRepository.sumOutstandingDebt`,
  `sumOutstandingForBranch`, `sumOutstandingBefore`, ...). Slice C
  explicitly deferred opening the `debt_entry` ledger and recommended
  "Slice F/G" as the place to land it — this is that decision point.

Slice G replaces the placeholder hub with a working **customer-AR debt
management surface**. The gap is not "build a ledger" — the receivables
data is all there. The gap is **aging buckets + dunning queue + chase
notes + a navigable surface** that consolidates what's scattered across
the sales + reports modules into one operator workflow.

---

## 1. Scope

**In** (customer-AR, sales-domain extensions — no new module)

- **Aging-bucket endpoint** `GET /api/v1/sales/reports/ar-aging` —
  buckets open + overdue invoices into `CURRENT` / `D_1_30` / `D_31_60`
  / `D_61_90` / `D_90_PLUS` by `(today − dueDate)`. JPQL-only. Returns
  per-customer rollup (customer, bucket totals, total outstanding,
  oldest invoice days-overdue) so the dunning queue renders from one
  call.
- **Dunning queue UI** `/debt` (replaces the placeholder) — table of
  customers with overdue exposure, sortable by aging-bucket and
  outstanding-amount, with `STOCK.COUNT`-shape filter UX (default
  view = customers with `D_31_60` or older; user toggles to widen).
- **Customer debt-position view** `/debt/customer/uid/{uid}` — single
  customer drill-down: aging row (5 cells), open-invoices table (sorted
  by due-date asc), receipts list (last 30 days), credit-limit
  read-out + edit button (deep-links to existing customer-edit page).
  US-DEBT-001 surface.
- **Customer statement re-link** — the statement route already exists
  at `/reports/customer-statement`. Slice G adds a "View statement"
  button on the debt-customer drill-down that passes the customer's
  `uid` through. No new statement work.
- **Chase notes (party_note)** — new `party_note` table + entity + CRUD
  endpoints. Scope is **note CRUD on a Party**, not "note CRUD on a
  debt_entry". Generalises so the same table covers future supplier-AP
  notes (Slice G.1). Records `kind` (`AR_CHASE` for Slice G; reserved
  `AP_CHASE` for G.1), free-text body (≤ 1000 chars), `actor_id` +
  `created_at`. Activity log surfaces on the customer drill-down,
  newest-first.
- **Permissions band 130–135** seeded in a new
  `V70__seed_debt_permissions.sql`:
  - 130 `DEBT.READ` — class-level grant on the new debt endpoints
    (aging + dunning + customer-debt-position). Read-only AR view.
  - 131 `DEBT.NOTE.CREATE` — append a chase note.
  - 132 `DEBT.NOTE.ARCHIVE` — soft-delete a chase note (no edit;
    notes are append-only; archive hides from the activity log).
  - 133 `DEBT.CREDIT_LIMIT.UPDATE` — adjust a customer credit limit
    from the debt surface. Distinct from `CUSTOMER.UPDATE` so credit
    controllers can be granted limit-adjust without the broader
    customer-edit grant. Audit-logged via existing `@Auditable`.
  - (134, 135 reserved for slice fix-ups.)
- **`accountant` persona widened** — gains `DEBT.READ`,
  `DEBT.NOTE.CREATE`, `DEBT.NOTE.ARCHIVE`,
  `DEBT.CREDIT_LIMIT.UPDATE`. The accountant is the canonical
  credit-controller in the existing roster — no new persona.
- **New outbox event** `DebtNoteCreated.v1` — emitted on note create;
  payload `{ noteId, partyId, kind, actorId, createdAt }`. Used by
  reporting projection (when it lands) for "chase activity" dashboards.
  Note archive emits `DebtNoteArchived.v1` for symmetry.
- **Hardening-checklist pass** on the new `party_note` aggregate and
  the new endpoints (uid URLs, JSON pin, ArchUnit, JPQL only,
  multi-tenant predicate, e2e gate). The aggregate uses the standard
  `UidEntity` shape.

**Out** (deferred)

- **Supplier-AP** (statements + aging + dunning for `Supplier` /
  `SupplierInvoice` / `SupplierPayment`). The endpoint already exists at
  `/reports/supplier-statement`. The right home for the AP equivalents
  is a follow-up **Slice G.1 — AP dunning**, modelled on this slice and
  reusing the `party_note` table (so chase notes work for both sides
  without schema change). Out-of-scope rationale: the customer-AR
  surface alone is two days of work; bundling AP balloons it and the
  user explicitly listed `/debt` as the surface to attack first. The
  hub-page tile "Supplier statements" continues to link to
  `/reports/supplier-statement` until G.1 lands.
- **Standalone `debt_entry` ledger table** (US-DEBT-001 et al.). Slice
  C deferred opening it; Slice G defers it again. **Recommendation:
  never land it.** The invoice + receipt + credit-note tables ARE the
  customer-AR ledger; bolting a parallel `debt_entry` ledger on top
  duplicates state and creates a reconciliation problem we do not
  have today. The aging + outstanding queries answer every US-DEBT
  story without it. If a future need surfaces (e.g. opening-balance
  imports from a legacy system), reopen the question with an ADR —
  but do not land the ledger pre-emptively.
- **US-DEBT-004 (write-off with dual approval)** — separate work item
  with its own approval gate and accounting policy. Defer to a future
  slice once the dual-control pattern from `stock-approver` is in place.
- **US-DEBT-005 (PDF statement export)** — defer. No PDF
  infrastructure in the codebase today; adding it is a separate
  cross-cutting slice.
- **Automated dunning emails / SMS reminders** — no notification
  infrastructure; out of scope.
- **Credit-bureau integration** — out of scope.
- **Allowance-for-doubtful-accounts accounting** — GL territory; out of
  scope for this slice.

**Prerequisites confirmed**

- Slice C merged: `SALES.REPORT.AR_SUMMARY` (id 125, V69) seeded;
  `SalesInvoiceRepository#sumOutstandingForBranch` + `countOpenForBranch`
  + `countOverdueForBranch` live.
- Slice F merged (PR #37 lineage): rollup endpoint + status filter on
  sales-invoices + accountant persona widening (already has
  `SALES.MANAGE_INVOICE` + `STOCK.COUNT` + `PROCUREMENT.MANAGE_LPO.READ`).
- Customer statement endpoint live since `StatementReportController`
  shipped pre-Slice C.

## 2. Permission band

Current high-water across all migrations: **125**
(`SALES.REPORT.AR_SUMMARY`, V69 Slice C). Slice F consumed no perms.
Next free band: **130–135** (skip 126–129 for any in-flight slice
fix-ups).

Proposed ids (Slice G):

| Id | Code | Surface |
|---|---|---|
| 130 | `DEBT.READ` | `/api/v1/debt/aging`, `/api/v1/debt/dunning`, `/api/v1/debt/customer/uid/{uid}` |
| 131 | `DEBT.NOTE.CREATE` | `POST /api/v1/debt/customer/uid/{uid}/notes` |
| 132 | `DEBT.NOTE.ARCHIVE` | `POST /api/v1/debt/notes/uid/{uid}/archive` |
| 133 | `DEBT.CREDIT_LIMIT.UPDATE` | `POST /api/v1/debt/customer/uid/{uid}/credit-limit` |
| 134 | reserved | slice fix-up |
| 135 | reserved | slice fix-up |

Collision check (one-liner):
```powershell
Select-String -Path 'orbix-engine-api/src/main/resources/db/migration/common/V*permission*.sql' `
              -Pattern '^\s*\(13[0-5],'
```

`DEBT.*` is a **new top-level permission namespace** — explicitly
called out in `hardening-checklist.md §What this checklist is NOT` as
ADR-worthy. The ADR question is "is `DEBT.*` a real namespace or
should these grants live under `SALES.*` since the data is sales-AR?"
The recommendation in §10 below: **`DEBT.*` is the right namespace**
because the surface is operator-driven (credit controller workflow,
chase activity) and should not be coupled to the `SALES.MANAGE_INVOICE`
bundle. ADR-0005 records this.

## 3. Module structure question

**Recommendation: extend `modules/sales/`. Do NOT create a standalone
`modules/debt/`.**

The forces:

1. **Data ownership** — all the read sources are sales-module entities
   (`SalesInvoice`, `SalesReceipt`, `CustomerCreditNote`,
   `ReceiptAllocation`). The aging-bucket endpoint is a JPQL query
   over `SalesInvoice` with a derived `(today − dueDate)` bucket
   expression. A standalone `modules/debt/` would either (a) reach
   across into `sales..repository..` (breaks `ModuleBoundaryTest`) or
   (b) duplicate the queries through DTO seams (loses the index — the
   existing `ix_sales_invoice_branch_due` from Slice C is already the
   right backing index).
2. **No new ledger entity** — the slice does not introduce a
   `debt_entry` table. Without a new aggregate to own, a standalone
   module has nothing to be the keeper of beyond the chase-notes
   table, and chase-notes belongs naturally with `Party` (cross-cuts
   AR + AP).
3. **Naming clarity** — operators say "debt management" but the data
   model says "outstanding AR". The URL surface (`/debt`, `/debt/*`,
   `/api/v1/debt/*`) gives the operator vocabulary at the route layer;
   the module structure remains the data-model truth.
4. **ADR-0004 boundary inventory** stays clean — no new cross-module
   sync-TX exemption, no new ArchUnit named rule. The new code lives
   in `modules/sales/service/DebtReadModelService` and
   `modules/party/service/PartyNoteService`, exposed through new
   controllers at `com.orbix.engine.api.DebtController` and
   `com.orbix.engine.api.PartyNoteController`.

**Concretely:**

- New endpoints under `/api/v1/debt/*` (sales-domain read model — lives
  in `modules.sales.service.DebtReadModelService` + `*Impl`).
- New `party_note` entity + table + repository + service in
  `modules.party` (the data crosses customer + supplier symmetry; party
  is the right owner).
- Controllers `DebtController` + `PartyNoteController` flat in
  `com.orbix.engine.api`.

The new code reaches across exactly **one** module seam:
`DebtControllerImpl` reads `party.PartyService` for customer name
resolution on the dunning queue (single round-trip, batch lookup by
`customerId in (..)`). That's a `..service..` interface call to a
non-infrastructure module — it is already covered by the broad
`..service..` allowance in `ModuleBoundaryTest` and does **not** need
a new ADR-0004 named exemption because (a) it's read-only and (b) the
caller is a read service, not a write. The right architectural fix
long-term is the deferred `reporting` module extraction (ADR-0004
#19) — Slice G does not unblock it.

## 4. Aging bucket contract

Locked bucket definitions (per US-DEBT-003 + the LaybyAgeing precedent
at `LaybyAgeingBucketDto`):

| Bucket | Days overdue | Includes |
|---|---|---|
| `CURRENT` | `dueDate >= today` OR `dueDate is null` | Not yet due. Sums outstanding so the rollup is complete. |
| `D_1_30` | `1 <= (today − dueDate) <= 30` | 1-30 days overdue. |
| `D_31_60` | `31 <= (today − dueDate) <= 60` | 31-60. |
| `D_61_90` | `61 <= (today − dueDate) <= 90` | 61-90. |
| `D_90_PLUS` | `(today − dueDate) > 90` | 90+. |

JPQL expression (sketch, in `SalesInvoiceRepository`):

```jpql
select c.id as customerId, c.partyId as partyId,
       sum(case when s.dueDate is null or s.dueDate >= :today
                then s.totalAmount - s.paidAmount else 0 end)  as current,
       sum(case when s.dueDate is not null
                 and s.dueDate < :today
                 and function('datediff', :today, s.dueDate) between 1 and 30
                then s.totalAmount - s.paidAmount else 0 end)  as d1_30,
       ... (D_31_60, D_61_90, D_90_PLUS)
       sum(s.totalAmount - s.paidAmount) as totalOutstanding,
       max(function('datediff', :today, s.dueDate))            as oldestDaysOverdue
  from SalesInvoice s join Customer c on c.partyId = s.customerId
 where s.companyId = :companyId
   and (:branchId is null or s.branchId = :branchId)
   and s.status in (POSTED, PARTIALLY_PAID)
   and s.totalAmount > s.paidAmount
 group by c.id, c.partyId
having sum(s.totalAmount - s.paidAmount) > 0
 order by sum(case when s.dueDate < :today
                   then s.totalAmount - s.paidAmount else 0 end) desc
```

**DB portability check.** `function('datediff', ...)` resolves to
`DATEDIFF(date1, date2)` on MariaDB (returns date1 − date2 in days) and
`DATE_PART('day', date1 - date2)` on PostgreSQL. JPA's `function()`
escape hatch dispatches to the active dialect — verify with a portable
fixture (test runs against both H2/MySQL-mode and H2/PG-mode, modelled
on `SalesInvoiceRepositoryTest`). If `datediff` proves portability-flaky
across the two dialects, fall back to computing the bucket in Java
after pulling `(customerId, dueDate, outstanding)` rows — at the
expected scale (≤ ~5k open invoices per company) the in-memory roll-up
is cheap and DB-portable by construction. Track as a portability check
in §7 task 3 — backend-engineer picks JPQL aggregation OR in-memory
roll-up based on the test results, not a guess.

DTO shape:

```java
public record ArAgingReportDto(
    LocalDate asOf,
    Long branchId,
    String currencyCode,
    AgingTotalsDto totals,
    List<CustomerAgingRowDto> rows  // sorted by oldest-overdue desc
) {
    public record AgingTotalsDto(
        BigDecimal current,
        BigDecimal d1_30,
        BigDecimal d31_60,
        BigDecimal d61_90,
        BigDecimal d90_plus,
        BigDecimal totalOutstanding
    ) {}

    public record CustomerAgingRowDto(
        Long customerId,                // serialises as string (global modifier)
        String customerName,
        String customerUid,
        BigDecimal current,
        BigDecimal d1_30,
        BigDecimal d31_60,
        BigDecimal d61_90,
        BigDecimal d90_plus,
        BigDecimal totalOutstanding,
        Integer oldestDaysOverdue,
        BigDecimal creditLimit,         // from Customer
        BigDecimal creditUtilisation    // totalOutstanding / creditLimit (null if limit = 0)
    ) {}
}
```

Field names match `LaybyAgeingBucketDto` precedent for terminology
consistency. The frontend renders the 5-bucket row + totals row + a
per-customer drill-down link `/debt/customer/uid/{customerUid}`.

## 5. Endpoints — explicit

### 5.1 `DebtController` (new, `/api/v1/debt`)

```java
@RestController
@RequestMapping("/api/v1/debt")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('DEBT.READ')")
public class DebtController {

    private final DebtReadModelService service;
    private final PartyNoteService notes;

    // 5.1.1 Aging report — feeds the /debt dunning queue
    @GetMapping("/aging")
    public ArAgingReportDto aging(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.aging(branchId, asOf);
    }

    // 5.1.2 Dunning queue (the "operator" shape — same data as aging but
    // sorted + paginated). Default sort = oldest-overdue desc. Optional
    // bucketFilter param so the UI can drill into a single bucket.
    @GetMapping("/dunning")
    public PageDto<CustomerAgingRowDto> dunning(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) AgingBucket bucketFilter,  // CURRENT, D_1_30, ...
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.dunning(branchId, bucketFilter, PageRequest.of(page, size));
    }

    // 5.1.3 Single-customer debt position — US-DEBT-001
    @GetMapping("/customer/uid/{uid}")
    public CustomerDebtPositionDto customer(@PathVariable @ValidUlid String uid) {
        return service.customerPosition(uid);
    }

    // 5.1.4 Adjust customer credit limit from the debt surface
    @PostMapping("/customer/uid/{uid}/credit-limit")
    @PreAuthorize("hasAuthority('DEBT.CREDIT_LIMIT.UPDATE')")
    public CustomerDebtPositionDto adjustCreditLimit(
            @PathVariable @ValidUlid String uid,
            @Valid @RequestBody AdjustCreditLimitRequestDto req) {
        return service.adjustCreditLimit(uid, req);
    }

    // 5.1.5 Append a chase note (DEBT.NOTE.CREATE)
    @PostMapping("/customer/uid/{uid}/notes")
    @PreAuthorize("hasAuthority('DEBT.NOTE.CREATE')")
    public ResponseEntity<PartyNoteDto> addNote(
            @PathVariable @ValidUlid String uid,
            @Valid @RequestBody CreatePartyNoteRequestDto req) {
        PartyNoteDto dto = notes.addCustomerNote(uid, req);
        return ResponseEntity.created(
            URI.create("/api/v1/debt/notes/uid/" + dto.uid())).body(dto);
    }
}
```

### 5.2 `PartyNoteController` (new, `/api/v1/debt/notes`)

```java
@RestController
@RequestMapping("/api/v1/debt/notes")
@RequiredArgsConstructor
public class PartyNoteController {

    private final PartyNoteService service;

    @PostMapping("/uid/{uid}/archive")
    @PreAuthorize("hasAuthority('DEBT.NOTE.ARCHIVE')")
    public PartyNoteDto archive(@PathVariable @ValidUlid String uid) {
        return service.archiveByUid(uid);
    }
}
```

### 5.3 `CustomerDebtPositionDto` (US-DEBT-001 payload)

```java
public record CustomerDebtPositionDto(
    Long customerId,
    String customerUid,
    String customerName,
    BigDecimal creditLimit,
    BigDecimal creditUtilisation,    // outstanding / limit, null if limit = 0
    BigDecimal totalOutstanding,
    Integer overdueInvoiceCount,
    AgingTotalsDto aging,            // reuse from ArAgingReportDto
    List<OpenInvoiceRowDto> openInvoices,    // sorted by dueDate asc, max 100
    List<RecentReceiptRowDto> recentReceipts,// last 30 days, max 50
    List<PartyNoteDto> notes         // last 50, newest first
) { ... }
```

Open invoice row carries `(invoiceId, invoiceUid, number, invoiceDate,
dueDate, totalAmount, paidAmount, outstanding, daysOverdue)`.
Recent receipt row carries `(receiptId, receiptUid, number,
receiptDate, totalAmount, method, allocations[])`.

Note: this shape mirrors the existing `PartyStatementDto` for the
fields that overlap, so a future client can render the position +
statement side-by-side without two type systems.

## 6. Schema — `party_note`

Per CLAUDE.md "ephemeral migrations": pre-stable schema → **add a new
migration** rather than editing `V8__party.sql` (the party baseline is
considered stable enough that adding a sibling table is cleaner than
re-running the baseline). New file: `V71__party_note.sql`.

```sql
CREATE TABLE party_note (
    id            BIGINT NOT NULL,
    uid           CHAR(26) NOT NULL,
    company_id    BIGINT NOT NULL,
    party_id      BIGINT NOT NULL,
    kind          VARCHAR(20) NOT NULL,   -- 'AR_CHASE' | 'AP_CHASE' | 'GENERAL'
    body          VARCHAR(1000) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | ARCHIVED
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    created_by    BIGINT NOT NULL,
    updated_by    BIGINT NOT NULL,
    version       INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_party_note PRIMARY KEY (id),
    CONSTRAINT uk_party_note_uid UNIQUE (uid),
    CONSTRAINT fk_party_note_party FOREIGN KEY (party_id) REFERENCES party (id),
    CONSTRAINT fk_party_note_company FOREIGN KEY (company_id) REFERENCES company (id)
);

CREATE INDEX ix_party_note_party_created  ON party_note (party_id, created_at DESC);
CREATE INDEX ix_party_note_company_kind   ON party_note (company_id, kind, status);

CREATE SEQUENCE party_note_seq START WITH 1 INCREMENT BY 50;
```

Two indexes: `(party_id, created_at DESC)` for the customer-drilldown
activity log; `(company_id, kind, status)` for the (future) chase-activity
roll-up report.

Entity follows the standard `UidEntity` pattern (extends, declares
the constraint on `@Table(uniqueConstraints = ...)`). Lifecycle is
`ACTIVE` ↔ `ARCHIVED` via `archiveByUid` — no edit endpoint (notes are
append-only; if the user wrote the wrong thing, archive + repost).

## 7. Outbox events

| Event | Payload keys | Subscribers |
|---|---|---|
| **`DebtNoteCreated.v1`** | `noteId, partyId, companyId, kind, actorId, createdAt` | future reporting (chase-activity dashboard) |
| **`DebtNoteArchived.v1`** | `noteId, partyId, companyId, actorId, archivedAt` | future reporting |
| **`CustomerCreditLimitChanged.v1`** | `customerId, oldLimit, newLimit, actorId, changedAt` | future reporting; future credit-bureau gateway (out of scope here, but the event is the seam) |

`CustomerCreditLimitChanged.v1` may already be emitted by `CustomerServiceImpl`
on the existing customer-update endpoint — verify with `grep` and, if so,
re-use the same event-type from the new debt-surface endpoint rather
than introduce a parallel name. (One event-type per business fact, not
per controller.) If absent today, land the event in this slice for both
the customer-edit page AND the new debt-surface adjust endpoint.

## 8. Persona impact

**Recommendation: extend `accountant` persona, no new persona.**

Today (`test-users.ts:112-142`):

- `accountant` is the canonical "chases overdue invoices and reviews
  stock variance" persona. Already holds `SALES.REPORT.AR_SUMMARY` +
  `SALES.MANAGE_INVOICE` + `STOCK.COUNT` + `PROCUREMENT.MANAGE_LPO.READ`
  + cash perms.

Slice G additions:
- `DEBT.READ` — read the dunning queue + customer-position view.
- `DEBT.NOTE.CREATE` — append chase notes.
- `DEBT.NOTE.ARCHIVE` — archive a chase note.
- `DEBT.CREDIT_LIMIT.UPDATE` — adjust credit limit from the debt surface
  (separate from `CUSTOMER.UPDATE` so a future "credit controller"
  role-split is one perm-grant away without re-modelling).

**No new persona.** The user explicitly asked: "extend the existing
`accountant` persona; add a `credit-controller` persona only if there's a
real role separation." A credit-controller-vs-accountant split is real
in larger finance teams but premature for the Tanzania-first deployment
target — the AR / cash / banking grants on the existing accountant are
the same person in a small-to-medium ERP customer. If the split surfaces
later, the perms are already factored (`DEBT.CREDIT_LIMIT.UPDATE` is
distinct, `DEBT.NOTE.*` are distinct) — split the persona at that point.

The `sales-clerk` persona deliberately does **not** get `DEBT.READ` —
they post invoices, they don't chase. The QA spec exercises this as
the 403-path persona on `/debt`.

## 9. Task list (TDD-style — QA first, ADR second, backend third)

| # | Owner | Deliverable | Acceptance signal |
|---|---|---|---|
| 1 | **qa-engineer** | Failing Playwright spec `debt.spec.ts` — accountant persona opens `/debt`, sees 5-bucket aging totals, sorts dunning queue, drills into a customer with overdue invoices, appends a chase note, adjusts a credit limit, archives the note. Plus sales-clerk persona attempts `/debt` and is 403'd. ~10 scenarios, all expected-fail at start. | Spec runs, ≥8 expected-fails on `main`. Land before backend work. |
| 2 | **solutions-architect** | ADR-0005 — naming + ownership of the `DEBT.*` permission namespace and the decision NOT to land a standalone `debt_entry` ledger. One-pager (Status / Context / Decision / Consequences / Alternatives). Ratifies the §3 module-structure call (extend sales + party, no new module). | ADR file committed; backend can start. |
| 3 | **backend-engineer** | Backend land: (a) `V70__seed_debt_permissions.sql` (perms 130–133); (b) `V71__party_note.sql` (table + indexes + sequence); (c) `PartyNote` entity + DTO + repository + `PartyNoteService` + `*Impl` in `modules.party`; (d) `DebtReadModelService` + `*Impl` in `modules.sales.service`; (e) `ArAgingReportDto` + `CustomerDebtPositionDto` + `PartyNoteDto`; (f) `DebtController` + `PartyNoteController` flat in `com.orbix.engine.api`; (g) aging-bucket JPQL query on `SalesInvoiceRepository` + portability fallback path; (h) outbox events `DebtNoteCreated.v1` / `DebtNoteArchived.v1` / `CustomerCreditLimitChanged.v1` (verify reuse of an existing emission first); (i) JSON wire-shape pin tests on all three new response DTOs; (j) `Permissions.java` constants. | `mvn -pl orbix-engine-api test` green; ArchUnit green; aging query passes portability test (MySQL + Postgres dialects). |
| 4 | **frontend-engineer** | Web land: (a) replace `debt.component.ts` with the dunning queue (5-bucket totals row, customer rows, sort + bucket-filter, deep-link to drill-down); (b) new `/debt/customer/:uid` route + `customer-debt-position.component.ts` (aging row, open-invoices table, recent receipts list, credit-limit panel with edit, chase-notes activity log + append-form); (c) `DebtService` Angular service for the 5 endpoints; (d) model types in `debt.models.ts` (id stringified per global modifier); (e) loading / empty / error / populated states on every screen; (f) deep-link "View statement" button passing the customer uid through to `/reports/customer-statement?customerId=<id>`. | `npm test` + `npm run e2e` green for debt feature + dashboard re-checks. |
| 5 | **backend-engineer + frontend-engineer** | Hardening-checklist sweep: JSON pin tests committed, ArchUnit green, README "Published events" tables updated in `modules/party/README.md` + `modules/sales/README.md`, `Permissions.java` constants added, e2e accessibility check green on the new pages. | All 10 checklist boxes ticked in PR body. |
| 6 | **qa-engineer (final QA gate)** | Re-run `debt.spec.ts` (expected-fails removed), full e2e suite, QA-image rebuild from `orbix-engine-infra/qa/Dockerfile`, smoke-test against the QA container with 3 personas (accountant happy path, sales-clerk 403 path, store-manager 403-on-debt + happy-on-stock as the cross-check). | All scenarios green; QA report attached to PR. |
| 7 | **pm** | PR merge → sync `main` locally. Update `USER-STORIES.md` to mark US-DEBT-001 (Hardened), US-DEBT-003 (Hardened — aging buckets), US-DEBT-007 (Hardened — chase notes). Leave US-DEBT-002 / 005 / 006 (supplier-AP / PDF) as Open. Open Slice G.1 (supplier-AP dunning) draft if priority demands. | Main green; status report appended. |

## 10. Need ADR?

**Yes — ADR-0005**, title:
> `0005-debt-namespace-and-no-debt-entry-ledger.md`

It records two coupled decisions:

1. **`DEBT.*` is the right top-level permission namespace** — not
   `SALES.DEBT.*` or `FINANCE.AR.*`. Rationale: the surface is
   operator-driven (credit-controller workflow, chase activity) and
   reuses across AR + AP. Coupling it to `SALES.*` would force a
   parallel `PROCUREMENT.DEBT.*` for the AP side and split a single
   workflow across two namespaces. (`hardening-checklist.md` flags a
   new top-level permission namespace as ADR-worthy explicitly.)

2. **No standalone `debt_entry` ledger table.** Customer-AR is
   computed from the existing `sales_invoice` + `sales_receipt` +
   `customer_credit_note` tables; supplier-AP from
   `supplier_invoice` + `supplier_payment`. A parallel `debt_entry`
   ledger duplicates state and creates a reconciliation surface we
   don't have today. The aging + outstanding queries answer every
   US-DEBT story without it. If a legacy-import or external-bureau
   need ever surfaces, reopen with a new ADR; do not pre-emptively
   land it.

A new ADR is also the right place to record the **module-structure
decision** from §3 (extend `modules/sales` + `modules/party`, no new
`modules/debt`). One ADR covers both the namespace and the absence of
a new module — they share the same force-diagram (cohesion at the
URL layer; minimal touch to the data model).

## 11. Open questions for Godfrey

1. **Aging bucket boundaries — 30/60/90 or 7/30/60/90?** Recommendation:
   `CURRENT / 1-30 / 31-60 / 61-90 / 90+`, matching US-DEBT-003 verbatim
   ("0-30 / 31-60 / 61-90 / 90+"). LaybyAgeingReport uses 7/30/60/90 for
   a different (short-tail layby) concern, so the precedent does NOT
   carry. Decision affects the JPQL CASE expression and the
   `ArAgingReportDto` shape.

2. **`DEBT.CREDIT_LIMIT.UPDATE` — single perm or split into `.RAISE` /
   `.LOWER` for asymmetric authority?** Recommendation: single perm.
   Real-world credit-policy is usually "controllers can lower freely,
   only senior managers can raise" but that is a workflow rule we
   don't have a vehicle for today (no approval queues outside stock).
   Single perm with audit-log capture preserves the option without
   over-modelling.

3. **Chase-note `kind` — start with `AR_CHASE` only or seed all three
   (`AR_CHASE`, `AP_CHASE`, `GENERAL`) now?** Recommendation: seed
   `AR_CHASE` (used) + `AP_CHASE` (reserved, Slice G.1) + `GENERAL`
   (reserved, for non-debt party notes). String column, no enum
   constraint at the DB level — change is a Java-only refactor when
   new kinds land. Enum `PartyNoteKind` in `domain/enums/`. Decision
   affects DTO + entity but is reversible.

---

**Need decision on:** the three open questions in §11. Slice is
otherwise unblocked — task #1 (qa-engineer writes the failing spec)
can start in parallel with this plan landing.
