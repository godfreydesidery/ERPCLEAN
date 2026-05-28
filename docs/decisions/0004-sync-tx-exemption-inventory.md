# 0004 — Cross-module synchronous-TX exemptions: the named inventory

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-05-27 |
| Deciders | Godfrey |

## Context

ADR-0003 carved out one named exemption from the "outbox-only" cross-module
rule: GRN → Stock writes the receipt's `stock_move` rows synchronously inside
the GRN-post transaction, against the `stock.service.*Service` interface seam.
At the time the ArchUnit whitelist was tightened only for the
procurement → stock direction; every other cross-module `..service..` call was
left tolerated by the broad `..service..` allowance in
`ModuleBoundaryTest.modules_only_depend_on_published_dtos_or_infrastructure`
(`ModuleBoundaryTest.java:45-62`).

The Slice C sales hardening adds two more sync-TX writes on the same shape:

- `SalesInvoiceServiceImpl#post` → `stockMoveService.post(...)` and
  `stockBatchService.drainFefo(...)` for FEFO drain + outbound `stock_move`
  rows (`SalesInvoiceServiceImpl.java:223,228,246`), plus the same-day void
  compensating call at line 173.
- `SalesReceiptServiceImpl#post` → `cashLedger.post(...)` for the `cash_entry`
  IN row matching the tender's `CashAccount`
  (`SalesReceiptServiceImpl.java:124`).

If we keep adding boundary exceptions ADR-by-ADR, we drift toward a forest of
single-pair rules with no rule of construction. The right move is to **take
inventory of every cross-module service-typed injection in the codebase**,
categorise each as (i) keep-as-sync-exemption or (ii) refactor-to-outbox, and
encode the result as the authoritative named list in `ModuleBoundaryTest`.
ADR-0004 is that inventory and that rule.

## Decision

Cross-module synchronous-TX exemptions are an **enumerated, closed set**.
Adding to the set requires a new ADR. The default for every cross-module
side effect remains the transactional outbox.

### 1. Rule for the sync exemption

A boundary may be exempted from outbox-only if **all four** hold:

1. **Same-deployment monolith.** Caller and callee co-deploy in the same JVM
   and share the connection pool (ADR-0001).
2. **One-way dependency.** Caller depends on callee; callee never reaches
   back. The exemption is directional and recorded as such.
3. **Interface seam only.** The caller imports the callee's
   `..service.*Service` interface and the callee's
   `..domain.dto..` / `..domain.enums..`. Reaches into the callee's `*Impl`,
   `..repository..`, or `..domain.entity..` are forbidden.
4. **Atomic state required.** The callee's write must be visible the instant
   the caller's TX commits — i.e. the read-after-write consistency window
   must be zero. If the consumer can tolerate the 1-5 s outbox poll window,
   it is NOT a candidate for the exemption.

If any of (1)-(4) fails, the call routes through the outbox.

### 2. The inventory

Cross-module service-typed injections currently in `modules/`, excluding
the always-allowed `common` / `auth` / `iam` infrastructure modules. Each
entry is categorised.

| # | Caller → Callee (interface) | Call site | Status |
|---|---|---|---|
| 1 | `procurement.GrnServiceImpl` → `stock.StockMoveService`, `stock.StockBatchService` | `GrnServiceImpl.java:213,221` | (i) Sync — ADR-0003. |
| 2 | `sales.SalesInvoiceServiceImpl` → `stock.StockMoveService`, `stock.StockBatchService` | `SalesInvoiceServiceImpl.java:173,223,228,246` | **(i) Sync — ADR-0004 named.** |
| 3 | `sales.CustomerReturnServiceImpl` → `stock.StockMoveService` | `CustomerReturnServiceImpl.java:126` | **(i) Sync — ADR-0004 named.** |
| 4 | `sales.SalesReceiptServiceImpl` → `cash.CashLedgerService` | `SalesReceiptServiceImpl.java:124` | **(i) Sync — ADR-0004 named.** |
| 5 | `pos.PosSaleServiceImpl` → `stock.StockMoveService`, `stock.StockBatchService` | `PosSaleServiceImpl.java:53-54` (imports; FEFO drain on cashier finalise) | **(i) Sync — ADR-0004 named.** |
| 6 | `pos.PosSaleServiceImpl` → `cash.CashLedgerService` | `PosSaleServiceImpl.java:14` (import; tender capture on finalise) | **(i) Sync — ADR-0004 named.** |
| 7 | `pos.PosSaleServiceImpl` → `giftcard.GiftCardService` | `PosSaleServiceImpl.java:535,550` (redeem / refundCredit) | **(i) Sync — ADR-0004 named.** |
| 8 | `pos.TillSessionServiceImpl` → `cash.CashLedgerService` | `TillSessionServiceImpl.java:8` (import; float-in / float-out / EOD) | **(i) Sync — ADR-0004 named.** |
| 9 | `pos.CashPickupServiceImpl` → `cash.CashLedgerService` | `CashPickupServiceImpl.java:8` (import; pickup posts a transfer entry) | **(i) Sync — ADR-0004 named.** |
| 10 | `pos.PettyCashServiceImpl` → `cash.CashLedgerService` | `PettyCashServiceImpl.java:8` (import; petty-cash spend posts a cash OUT) | **(i) Sync — ADR-0004 named.** |
| 11 | `giftcard.GiftCardServiceImpl` → `cash.CashLedgerService` | `GiftCardServiceImpl.java:81` (cash IN on gift-card issuance) | **(i) Sync — ADR-0004 named.** |
| 12 | `orders.CustomerOrderServiceImpl` → `stock.StockReservationService`, `stock.StockMoveService` | `CustomerOrderServiceImpl.java:40-41` | **(i) Sync — ADR-0004 named.** Reservation lock + delivery move must hold atomically with the order header. |
| 13 | `orders.CustomerOrderServiceImpl` → `cash.CashLedgerService` | `CustomerOrderServiceImpl.java:9` (deposit / refund posts a cash entry) | **(i) Sync — ADR-0004 named.** |
| 14 | `orders.CustomerOrderServiceImpl` → `giftcard.GiftCardService` | `CustomerOrderServiceImpl.java:20` (gift-card tender on deposit) | **(i) Sync — ADR-0004 named.** |
| 15 | `production.ConversionServiceImpl` → `stock.StockMoveService` | `ConversionServiceImpl.java:20` (CONSUME + PRODUCE move pair must be atomic) | **(i) Sync — ADR-0004 named.** |
| 16 | `production.ProductionBatchServiceImpl` → `stock.StockMoveService`, `stock.StockBatchService`, `stock.StockReservationService` | `ProductionBatchServiceImpl.java:37-39` | **(i) Sync — ADR-0004 named.** |
| 17 | `admin.BranchServiceImpl` → `party.CustomerService` | `BranchServiceImpl.java:78` (`createWalkInCustomer`) | **(i) Sync — ADR-0004 named.** Branch-creation must atomically materialise the walk-in customer; orphaned branch is a setup-failure state. |
| 18 | `iam.SessionServiceImpl` → `auth.AuthService` | `SessionServiceImpl.java:91` (`reissueTokens` on active-branch switch) | (i) Sync — already permitted by the `auth..` infrastructure whitelist. Documented here for completeness; no new rule entry needed. |
| 19 | `sales.SalesReportServiceImpl` → `pos.TillReportService` | `SalesReportServiceImpl.java:196` (`zReport`) | **(ii) Latent gap — refactor.** Reports are a read-only cross-cut; the right shape is a dedicated `reporting` module that owns the projection, not sales reaching across to pos. Out of scope for Slice C. Leave the named exemption in place for now (read-only, no TX correctness risk) and track the refactor as a future hardening slice. |
| 20 | `sales.DebtWriteOffServiceImpl` → `sales.SalesInvoiceService`, `procurement.SupplierInvoiceService` | `DebtWriteOffServiceImpl.java` (`applyToInvoice`) | **(i) Sync — ADR-0004 named (Slice G.2).** `debt_write_off` write + `sales_invoice`/`supplier_invoice` paidAmount update happen in the same DB tx as the outbox `DebtWriteOffPosted.v1` event. Invariant: if write-off is POSTED, the invoice paidAmount reflects it — eventual consistency would break aging queries during the poll window. |

**Totals.** 18 directional call sites that currently cross a non-infrastructure
module boundary. 17 keep the sync exemption (categorised (i)). 1 is a latent
quality issue (categorised (ii)) that does not violate atomicity but signals
a missing `reporting` module.

### 3. Latent gaps that ADR-0004 does NOT close

Three observations the inventory surfaced that are out of scope here
(none are sync-TX correctness issues; they are separate boundary issues):

- **`SalesReportServiceImpl` reaches into `pos`, `procurement`, `cash`
  repositories** (`SalesReportServiceImpl.java:48-50,109-111,133`). The
  broad `..repository..` allowance tolerates this. The right fix is the
  same `reporting` module refactor as item #19. Track separately.
- **`Permissions.java` missing sections for many modules** (procurement,
  POS, orders, production, gift-card). String-literal SpEL works; the
  constant-grep audit is poorer. Resolve module-by-module.
- **`ApplicationEventPublisher` use** — none found. Outbox is the only
  event-emission path in the codebase, as CLAUDE.md mandates.

### 4. The posting-engine `..common.posting..` package

Sales and POS keep separate posting services (Slice C decision: option (b)).
Shared helpers — `StockMoveType.SALE` outbound + FEFO drain wrapper, the
`accountFor(ReceiptMethod / PosTenderMethod)` cash-account mapping, the
margin-cost snapshot helper — go into a **new** `com.orbix.engine.modules.common.posting`
package.

`common..` is already permitted from every module by the existing
`modules_only_depend_on_published_dtos_or_infrastructure` rule
(`ModuleBoundaryTest.java:49`). `..common.posting..` therefore needs **no
named exemption entry** — it is infrastructure by construction. The helpers
must be stateless and contain no domain entity references; they take and
return DTOs / enums only.

### 5. `ModuleBoundaryTest` whitelist update

The current rule (`modules_only_depend_on_published_dtos_or_infrastructure`,
`ModuleBoundaryTest.java:45-62`) globally whitelists `..service..`. That is
too permissive — it would permit any new cross-module service call without
review. **Tighten it** so the default is "a module may depend on its OWN
`..service..` only", and add a single named-exemption rule that enumerates
the 17 sync-TX exempt boundaries from §2.

The encoding is straightforward — one ArchUnit rule per caller→callee
direction, modelled on the existing
`procurement_may_only_call_stock_service_interfaces` rule
(`ModuleBoundaryTest.java:74-79`). Each rule asserts that the caller
imports only the callee's `..service.*Service` interface and the callee's
`..domain.dto..` / `..domain.enums..` — never `*Impl`, never `..repository..`,
never `..domain.entity..`.

Concretely, the rule set after this ADR:

| Rule | Caller → Callee |
|---|---|
| ADR-0003 (kept) | `procurement` → `stock` |
| ADR-0004 (new) | `sales` → `stock`, `sales` → `cash` |
| ADR-0004 (new) | `pos` → `stock`, `pos` → `cash`, `pos` → `giftcard` |
| ADR-0004 (new) | `giftcard` → `cash` |
| ADR-0004 (new) | `orders` → `stock`, `orders` → `cash`, `orders` → `giftcard` |
| ADR-0004 (new) | `production` → `stock` |
| ADR-0004 (new) | `admin` → `party` |
| ADR-0004 (latent) | `sales` → `pos` (read-only; refactor to `reporting` later) |

Implementation note: the existing helper
`reachStockOnlyViaServiceInterfaces` is the right template — generalise it
to a `reachOnlyViaServiceInterfaces(String calleeRootPackage)` factory.
That is `ModuleBoundaryTest` engineering, not an architecture decision.

### 6. Outbox is still default; outbox is still emitted in addition

Every sync-TX exempt call site continues to emit its outbox event in the
same `@Transactional` write. The exemption is about the **stock / cash /
gift-card / reservation** atomic write, not about silencing the event
fan-out. Downstream observers (reporting, future debt module, alerts) read
the event from `domain_event` and tolerate the outbox poll cycle as
intended.

## Consequences

### What this codifies

- **Default is outbox.** Any new cross-module side effect after 2026-05-27
  must use the transactional outbox unless a new ADR adds it to the
  named-exemption inventory.
- **Inventory is the audit log.** The §2 table is the authoritative answer
  to "is this boundary an exemption?" A boundary not in the table is not
  exempt.
- **Interface seam is non-negotiable.** Every named exemption is restricted
  to the callee's `*Service` interface + published DTOs / enums. Tests
  enforce; reviewers can call out any `*Impl` / repository / entity
  cross-module import without consulting the spec.
- **`common.posting` is sanctioned.** Shared helpers callable from any
  posting service have a clear home. Sales and POS keep their own engines.

### What this does NOT permit

- A new cross-module sync-TX call without an ADR addition. The PR will fail
  `ModuleBoundaryTest` until the ADR + rule lands together.
- Pulling the callee's `*Impl` / entity / repository even if the call
  is on the exempt list. The seam is the interface only.
- Using `ApplicationEventPublisher` to dodge the rule. Outbox or
  named-exempt call — those are the only two options.

### Engineering follow-up

- **`ModuleBoundaryTest` rewrite.** Backend-engineer to refactor §5 into one
  parameterised condition + N rule entries (one per exemption row).
  Tracked under the Slice C "Hardening-checklist sweep" task (task 6 in
  the Slice C plan).
- **Reporting module extraction.** `SalesReportServiceImpl`'s pos /
  procurement / cash repository reach-ins (and the `TillReportService`
  call) are the seed of a new `reporting` module. Out of scope for
  Slice C; track for a future slice and stop adding to the cross-module
  read pile in the meantime.
- **Module READMEs.** Each module whose service appears as a callee in §2
  should grow a "Synchronous callers" sub-section in its README citing
  this ADR (the catalog/procurement README precedent).

## Alternatives Considered

### A. Keep the broad `..service..` whitelist; rely on review

Rejected: 18 cross-module call sites in the codebase today, several
introduced without an ADR (sales→stock, sales→cash, pos→cash, orders→*,
production→stock, admin→party). Review-only enforcement has demonstrably
not held the line. Encoding the inventory in `ModuleBoundaryTest` makes
new violations fail at PR time, not three slices later when the audit catches up.

### B. One ADR per exemption pair

Rejected: would have produced ADR-0004 through ADR-0012 in one slice, each
restating the same four-condition rule, each citing ADR-0003 verbatim.
A single inventory ADR is faster to read, faster to maintain, and gives
future engineers one file to consult.

### C. Refactor every sync-TX call to outbox

Rejected for the same forces ADR-0003 enumerates per pair: stock-on-hand
on the POS / sales hot path needs zero-lag consistency; cash entries on
receipt / till-session post must roll back with the business write;
production conversion's CONSUME / PRODUCE move pair has no half-state.
Outbox-on-everything would force compensating-event machinery the
project does not have and trade correctness for purity.

## References

- [ADR 0001 — Modular monolith over microservices](0001-modular-monolith.md)
- [ADR 0003 — GRN → Stock is a synchronous in-transaction dependency](0003-grn-to-stock-synchronous-tx.md)
- [CLAUDE.md "Cross-module communication"](../../CLAUDE.md)
- [docs/design/slice-c-sales-plan.md §4 (posting-engine), §10 (ADR)](../design/slice-c-sales-plan.md)
- [docs/conventions/hardening-checklist.md §3 (outbox bullet)](../conventions/hardening-checklist.md)
- `SalesInvoiceServiceImpl.java:173,223,228,246` — sales → stock call sites.
- `SalesReceiptServiceImpl.java:124` — sales → cash call site.
- `ModuleBoundaryTest.java:45-62` — broad `..service..` allowance to tighten.
- `ModuleBoundaryTest.java:74-126` — ADR-0003 named-exemption template to generalise.
