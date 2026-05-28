# 0005 — `DEBT.*` permission namespace and no `debt_entry` ledger

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-05-28 |
| Deciders | Godfrey |

## Context

Slice G replaces the placeholder `/debt` hub with a working customer-AR
debt-management surface (aging buckets, dunning queue, per-customer
drill-down, chase notes, credit-limit adjust from the debt surface).
Two architectural questions block backend work and both have to be
answered before the migration + permission band lands:

1. **Permission namespace.** Slice C added `SALES.REPORT.AR_SUMMARY`
   (perm 125) under `SALES.*`. Slice G adds four new perms (read the
   dunning surface, create + archive a chase note, adjust a credit
   limit from the debt surface). The question: do these live under
   `SALES.DEBT.*` (because the data is customer-AR, which is owned by
   `modules/sales/`), or under a new top-level `DEBT.*` namespace?
   `docs/conventions/hardening-checklist.md` calls out "new top-level
   permission namespace" as ADR-worthy, which is what makes this an
   ADR decision rather than a checklist tick.

2. **Ledger shape.** `USER-STORIES.md` US-DEBT-001 through US-DEBT-007,
   `ARCHITECTURE.md`, and several module READMEs reference a
   `debt_entry` ledger table that **does not exist**. Outstanding-AR
   math is done inline from `sales_invoice` + `sales_receipt` +
   `customer_credit_note` (see `SalesInvoiceRepository.sumOutstandingForBranch`,
   `sumOutstandingBefore`, `countOpenForBranch`, `countOverdueForBranch`,
   plus the same shape on the supplier side). Slice C explicitly
   deferred opening the ledger and named Slice F/G as the decision
   point. Slice G is the decision point and the question is binary —
   land it now, or commit to never landing it.

3. **Module shape (consequence of 1 + 2).** If `DEBT.*` is its own
   namespace and there is no `debt_entry` table, does Slice G create
   a new `com.orbix.engine.modules.debt` Java module, or extend
   `modules/sales` (read model for aging / dunning / position) and
   `modules/party` (the new `party_note` table for chase notes)? The
   answer ties to ADR-0001's modular-monolith discipline and ADR-0004's
   cross-module boundary inventory — a new module would either
   reach into `sales..repository..` (breaking `ModuleBoundaryTest`)
   or duplicate JPQL through DTO seams (losing
   `ix_sales_invoice_branch_due`).

The three are coupled: the namespace name signals operator-cohesion
(debt-collection workflow ≠ sales-clerk workflow); the absence of a
ledger removes the only candidate aggregate a `modules/debt` module
could own; the module shape falls out as "extend sales + party".

## Decision

### 1. `DEBT.*` is a new top-level permission namespace

Slice G introduces the namespace `DEBT.*` for permissions gating the
debt-management operator surface. Slice G ships four codes in band
130–133:

| Id | Code | Surface |
|---|---|---|
| 130 | `DEBT.READ` | `/api/v1/debt/aging`, `/dunning`, `/customer/uid/{uid}` |
| 131 | `DEBT.NOTE.CREATE` | append a chase note |
| 132 | `DEBT.NOTE.ARCHIVE` | archive a chase note (soft-delete) |
| 133 | `DEBT.CREDIT_LIMIT.UPDATE` | adjust a customer credit limit from the debt surface |

`SALES.*` perms continue to gate the underlying domain mutations
(invoice post, receipt post, credit-note issue). `PROCUREMENT.*` will
gate the supplier-side mutations when Slice G.1 lands. `DEBT.*` gates
**the operator workflow on top of those mutations** — the credit
controller reading exposure, chasing customers, annotating accounts,
and adjusting limits without needing the broader `CUSTOMER.UPDATE`
grant that the customer-edit page requires.

When supplier-AP dunning lands in Slice G.1, the same `DEBT.*`
namespace covers it (chase notes already span AR + AP through the
shared `party_note` table; aging endpoints mirror as `/api/v1/debt/ap-*`
or `/debt/ar` + `/debt/ap` siblings). The namespace stays singular and
the perm count stays small.

### 2. No standalone `debt_entry` ledger table

The customer-AR ledger **is** the existing trio: `sales_invoice` +
`sales_receipt` + `customer_credit_note`. The supplier-AP ledger
**is** `supplier_invoice` + `supplier_payment`. Slice G computes every
US-DEBT story (US-DEBT-001 customer drill-down, US-DEBT-003 aging
buckets, US-DEBT-007 chase activity) from those tables — no new
ledger.

The Slice G aging endpoint is a single JPQL aggregation on
`SalesInvoice` with a `(today − dueDate)` CASE expression, backed by
the existing `ix_sales_invoice_branch_due` index. The chase-note
activity log is a separate concern owned by a new `party_note` table
(party-scoped, not invoice-scoped, so it covers AR + AP without
schema duplication).

If a future need for a denormalised ledger surfaces — opening-balance
import from a legacy system, credit-bureau export, or a
write-off + dual-approval flow (US-DEBT-004) that wants a tidy
"adjustment" row — re-open this question with a new ADR. Do not land
the table pre-emptively.

### 3. Extend `modules/sales` + `modules/party`; no new `modules/debt`

The new code lives in two existing modules:

- **`modules/sales/`** — `DebtReadModelService` + `*Impl` for the
  aging / dunning / customer-position read model, plus
  `adjustCreditLimit` (writes to `Customer`, audit-logged, emits
  `CustomerCreditLimitChanged.v1`). DTOs `ArAgingReportDto`,
  `CustomerDebtPositionDto`, `AdjustCreditLimitRequestDto`, enum
  `AgingBucket`. The aging JPQL extends `SalesInvoiceRepository`.
- **`modules/party/`** — `party_note` table + `PartyNote` entity +
  `PartyNoteService` + `*Impl`, plus enum `PartyNoteKind` (`AR_CHASE`,
  `AP_CHASE`, `GENERAL`) and `PartyNoteStatus` (`ACTIVE`, `ARCHIVED`).
  Chase notes are party-scoped, not customer-scoped, so the same
  table serves AR + AP without a separate schema in Slice G.1.

Controllers `DebtController` + `PartyNoteController` are flat in
`com.orbix.engine.api` per the existing layout. The URL surface
`/api/v1/debt/*` gives operators the debt-management vocabulary
without coupling the data-model to the route layer.

No new Java module is created. No new ADR-0004 named exemption is
needed — the one cross-module call introduced (`DebtController` reads
`party.PartyService` for customer-name resolution on the dunning
queue) is a read-only `..service..` interface call already covered by
the broad `..service..` allowance in `ModuleBoundaryTest`.

## Consequences

### What this codifies

- **`DEBT.*` is the canonical permission prefix** for any operator
  workflow that consolidates receivables / payables exposure. Future
  perms (Slice G.1 supplier-AP, write-off approval, statement export)
  go here, not under `SALES.*` or `PROCUREMENT.*`.
- **Domain-mutation perms stay where they are.** `SALES.MANAGE_INVOICE`
  still gates invoice post; `PROCUREMENT.MANAGE_GRN` still gates GRN
  post. `DEBT.*` is additive over the existing namespaces, never a
  replacement.
- **The `accountant` persona is the canonical credit controller** —
  it receives all four Slice G perms. `sales-clerk` does not get
  `DEBT.READ` (they post invoices, they do not chase). `store-manager`
  does not get `DEBT.READ` (branch operations, not finance). A future
  `credit-controller` persona is one role-edit away if the user-org
  demands the split — the perms are already factored
  (`DEBT.CREDIT_LIMIT.UPDATE` is distinct from `CUSTOMER.UPDATE`).
- **There is no `debt_entry` table.** All references to it in
  `USER-STORIES.md`, `ARCHITECTURE.md`, and module READMEs should be
  read as describing the **virtual ledger** materialised by the
  invoice + receipt + credit-note trio. Documentation updates that
  remove the literal table reference are welcome but not required —
  the absence of the table in the schema is authoritative.
- **The debt module is a read-and-annotate surface.** The only writes
  it owns are (a) chase notes on `party_note`, (b) credit-limit
  adjustments on `Customer.credit_limit_amount`. Every other read on
  the surface (aging, dunning, customer position) is a projection
  over the existing AR ledger.

### What this does NOT permit

- **No parallel `SALES.DEBT.*` namespace.** All debt-management perms
  go under `DEBT.*`. Reviewers should reject any new `SALES.DEBT.X`
  or `PROCUREMENT.DEBT.X` proposal.
- **No new `com.orbix.engine.modules.debt` Java package.** Code lives
  in `modules/sales/` (read model + credit-limit adjust) and
  `modules/party/` (notes). A PR that introduces a `modules/debt`
  module without a superseding ADR fails review.
- **No `debt_entry` migration.** A `V__create_debt_entry.sql` or
  equivalent will not merge under this ADR. The duplication of state
  it would create (the invoice / receipt / credit-note rows already
  carry the same information) trades correctness for a perceived
  simplification.
- **Reaching into `sales..repository..` from a new module.** The read
  model that backs `/api/v1/debt/*` lives **inside** `modules/sales/`
  precisely so it can use the existing repositories and indexes
  directly. Spinning the read model out into a sibling module would
  force either a cross-module repository reach (breaks
  `ModuleBoundaryTest`) or a DTO-seam duplication (loses
  `ix_sales_invoice_branch_due` and forces an in-memory aggregation
  at non-trivial scale).

### Engineering follow-up

- **`Permissions.java`** grows a new `DEBT` static-inner-class section
  with the four codes. Mirror the existing `SALES` / `STOCK` / `IAM`
  section shape.
- **`hardening-checklist.md`** — no change. The checklist already
  flags new top-level permission namespaces as ADR-worthy; this ADR
  is the discharge.
- **`USER-STORIES.md`** — when US-DEBT-001, US-DEBT-003, US-DEBT-007
  land as Hardened, leave the prose's "debt entry" terminology in
  place; it describes the virtual ledger, not a literal table.
- **`ARCHITECTURE.md`** — when next touched, replace any literal
  `debt_entry` table reference with "AR ledger (computed)" /
  "AP ledger (computed)" pointing at the underlying tables. Not a
  blocker for Slice G.
- **Slice G.1 (supplier-AP dunning)** inherits this decision: same
  namespace, same module shape (extend `modules/procurement` for the
  AP read model, reuse `modules/party/party_note` for AP chase notes),
  no new ledger.

## Alternatives considered

### A. Put the new perms under `SALES.DEBT.*`

Rejected. The surface is operator-cohesive (the credit controller's
workflow), not data-cohesive. Coupling the perms to `SALES.*` forces
a parallel `PROCUREMENT.DEBT.*` for the AP side when Slice G.1 lands,
which splits a single operator workflow across two namespaces and
makes role-design (e.g. "credit-controller: AR + AP, no domain
mutations") a multi-namespace exercise. `DEBT.*` keeps the workflow
in one place.

### B. Land the `debt_entry` ledger now (proactive)

Rejected. The aging + outstanding queries answer every US-DEBT story
without it; the parallel state would create a reconciliation surface
(invoice posts must mirror to `debt_entry`; receipts must allocate
against `debt_entry`; credit notes must reduce `debt_entry`) that we
do not have today and that is non-trivial to keep correct across the
sync-TX exemption inventory in ADR-0004. The "future need" for the
ledger is hypothetical (legacy import, write-off approval) — defer
to the slice that actually has that need, with an ADR that scopes
the table to exactly what that slice requires.

### C. Create a new `com.orbix.engine.modules.debt` module

Rejected. Without a `debt_entry` aggregate to own, the module has
nothing to be the keeper of beyond chase notes — and chase notes
belong with `Party` (cross-cuts AR + AP). The aging / dunning /
position read model is fundamentally a view over `sales_invoice` +
`sales_receipt` + `customer_credit_note`; placing it in a sibling
module forces either a repository-reach (boundary violation) or a
DTO-seam duplication (index loss + redundant query maintenance).
Extending `modules/sales/` keeps the read model adjacent to the
repositories and indexes it depends on, which is the same
constructional argument as keeping `SalesReportServiceImpl` inside
`modules/sales/` rather than extracting a `reporting` module
prematurely (the deferred `reporting` extraction in ADR-0004 #19 is
a separate, larger conversation).

### D. Split `DEBT.CREDIT_LIMIT.UPDATE` into `.RAISE` + `.LOWER`

Rejected for Slice G. Real-world credit policy often grants
controllers free authority to lower limits while reserving raise
authority for senior managers, but the project has no approval-queue
vehicle outside `stock-approver` and modelling a parallel approval
queue here is premature. A single perm with audit-log capture
(`@Auditable` already on the write path) preserves the option — if
an approval queue lands later, splitting the perm is a band 134/135
reservation away.

## References

- [ADR 0001 — Modular monolith over microservices](0001-modular-monolith.md)
- [ADR 0002 — uid on composite-key aggregates](0002-uid-on-composite-key-aggregates.md)
- [ADR 0003 — GRN → Stock synchronous TX](0003-grn-to-stock-synchronous-tx.md)
- [ADR 0004 — Sync-TX exemption inventory](0004-sync-tx-exemption-inventory.md)
- [docs/design/slice-g-debt-plan.md](../design/slice-g-debt-plan.md) §2 (perm band), §3 (module structure), §10 (ADR call)
- [docs/design/slice-g-debt-gap-audit.md](../design/slice-g-debt-gap-audit.md) §7 (backend touchpoints), "What's intentionally NOT in scope"
- [docs/conventions/hardening-checklist.md](../conventions/hardening-checklist.md) — "What this checklist is NOT" flags new top-level permission namespaces as ADR-worthy
- `USER-STORIES.md` — US-DEBT-001, US-DEBT-003, US-DEBT-007 (the in-scope stories for Slice G)
- `SalesInvoiceRepository#sumOutstandingForBranch`, `countOpenForBranch`, `countOverdueForBranch` — the existing virtual-ledger queries
- `StatementReportServiceImpl#customerStatement` — opening-balance + running-balance proof that the existing trio is a complete ledger
