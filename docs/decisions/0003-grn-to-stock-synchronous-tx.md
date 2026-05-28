# 0003 — GRN → Stock is a synchronous in-transaction dependency

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-05-27 |
| Deciders | Godfrey |

## Context

CLAUDE.md "Cross-module communication" states the project rule plainly:

> Domain events via a **transactional outbox**. Use it for cross-module side
> effects — don't reach into another module's service or repository.

`GrnServiceImpl#post(String uid)` breaks that rule in spirit. On posting a
GRN, the procurement service synchronously invokes two stock-module services
inside the same `@Transactional` write:

- `StockBatchService#createBatch(...)` for batch-tracked items
  (`GrnServiceImpl.java:213`).
- `StockMoveService#post(...)` for the receipt move
  (`GrnServiceImpl.java:221`).

Both calls are typed against the `*Service` interface (not the `*Impl`), and
the receiving stock module is part of the same modular-monolith deployment
(ADR 0001). The Slice B procurement gap audit
(`docs/design/slice-b-procurement-gap-audit.md`) flagged the call as a
candidate for explicit policy — either route it through the outbox like
everything else, or document the exemption.

The forces pulling against an outbox event here:

1. **Stock balance must reflect the receipt atomically.** Accountants and POS
   till operators query `stock_on_hand` seconds after a GRN is posted. A lag
   between "GRN posted" and "stock visible" breaks user trust on the hot
   retail path.
2. **The outbox poll cycle is 1–5 s in normal operation** (single-thread
   dispatcher, configurable via `orbix.outbox.*`). That window is unacceptable
   for stock-on-hand consistency in a POS context: a sale issued during the
   window would underflow or split incorrectly.
3. **Rollback semantics are load-bearing.** If either the GRN write or the
   stock_move / stock_batch write fails (validation, optimistic-lock collision,
   FK violation), both must roll back. A single `@Transactional` boundary
   gives that for free; outbox-mediated handoff to an async consumer breaks
   the guarantee — the outbox row commits with the GRN, and consumer failure
   leaves the GRN posted but stock un-moved (or worse, partially moved).
4. **No remote boundary to cross.** Stock and procurement co-deploy in the
   same JVM, share the connection pool, and run inside the same transaction
   manager. The forces that motivate outbox-style decoupling (independent
   deploy, separate DB, network partition) do not apply.

## Decision

GRN → Stock is a **permitted synchronous in-transaction dependency**, NOT an
outbox event.

Concretely:

1. **The dependency direction is fixed and downstream-only.** Procurement
   depends on Stock. Stock never depends on Procurement. ADR-0001's modular-
   monolith stance is preserved — the boundary is one-way and explicit.
2. **The import surface is the interface only.** `GrnServiceImpl` may import
   `com.orbix.engine.modules.stock.service.StockMoveService` and
   `com.orbix.engine.modules.stock.service.StockBatchService` (the
   `*Service` interfaces from CLAUDE.md "service interface + impl"), and the
   request/response DTOs in `com.orbix.engine.modules.stock.domain.dto..`.
   It MUST NOT import any `*Impl`, any entity from
   `com.orbix.engine.modules.stock.domain.entity..`, or any
   `com.orbix.engine.modules.stock.repository..`. The interface seam is the
   exemption boundary.
3. **`ModuleBoundaryTest` should encode the exemption explicitly.** The
   current rule (`modules_only_depend_on_published_dtos_or_infrastructure`,
   `ModuleBoundaryTest.java:35`) whitelists `..service..` globally, which
   silently permits today's import. That is too permissive: it would
   equally permit any module to call any other module's service. The rule
   should tighten to "modules may depend on their own `..service..` only"
   and add a *named exemption* for
   `com.orbix.engine.modules.procurement.service` →
   `com.orbix.engine.modules.stock.service` (interfaces only). The single
   widened rule then doubles as the audit log for future exemptions —
   any new entry to that list requires a new ADR.
4. **The `GrnPosted.v1` outbox event is still emitted** in the same TX
   (`GrnServiceImpl.java:239`). Downstream observers that *can* tolerate
   eventual consistency (reporting, debt-opener once Slice C lands,
   notifications) consume it. The exemption affects the stock write only,
   not the event fan-out.

## Consequences

### What this codifies

- Stock-on-hand is consistent the instant a GRN row hits `status='POSTED'`.
  POS, web inventory list, and any in-process consumer see the new quantity
  in the same TX commit.
- A failure in `StockMoveService#post` (e.g. concurrent stock-take lock)
  cascades into a GRN-post rollback. The GRN row stays in `DRAFT`, the user
  retries. No half-posted state is possible.
- Cross-module side effects that *don't* need read-after-write
  consistency stay outbox-only (every other procurement → X interaction,
  every catalog price-change event, every business-day projection). The
  outbox remains the default; this ADR carves out the single exemption.

### What this does NOT permit

- Stock depending on Procurement. The direction is one-way.
- Importing stock entities or repositories from procurement. The interface
  is the seam.
- Generalising "sync TX call" to any other cross-module pair without a new
  ADR. Each future exemption (e.g. POS sale → stock_move, sales invoice →
  AR posting) earns its own decision record and its own entry in the
  ModuleBoundaryTest exemption list.

### Engineering follow-up

- **`ModuleBoundaryTest` whitelist update** — backend-engineer to encode
  the procurement→stock service-interface exemption as a named rule
  (`ModuleBoundaryTest.java:35`). The change is policy enforcement, not
  policy itself.
- **Module README** — `modules/procurement/README.md` Published-events
  table should add a "Synchronous dependencies" sub-section citing this
  ADR.

## Alternatives Considered

### A. Outbox event for stock (default rule)

Emit a `GrnPosted.v1` event; a stock-side consumer reads it and writes the
`stock_move` / `stock_batch` rows asynchronously.

Rejected:

- Stale stock-on-hand window (1–5 s typical, longer under load) is
  intolerable on the POS hot path.
- Rollback is no longer atomic. A failed stock write leaves a posted GRN,
  requiring compensating-event machinery the project does not have.
- Buys nothing — both modules deploy together, share the connection pool,
  and run under one transaction manager.

### B. Two-phase commit / saga

Coordinate the GRN write and the stock write across explicit prepare/commit
phases (XA TX or application-level saga with compensations).

Rejected: over-engineered for a single deployment monolith. XA on top of one
DB connection pool is needless ceremony; sagas are the right answer when
modules are independently deployed and the network can partition between
them — neither applies here.

### C. Move the stock write into a `domain_event` consumer inside the
*procurement* module

A procurement-internal handler reads `GrnPosted.v1` from the outbox and
performs the stock write in a new TX, still in-process.

Rejected: adds latency without buying anything. The original objection (stale
stock-on-hand, non-atomic rollback) still applies because the consumer runs
in a separate TX. The interface-only call across modules is the cleanest
expression of the dependency that already exists.

## References

- [ADR 0001 — Modular monolith over microservices](0001-modular-monolith.md)
- [CLAUDE.md "Cross-module communication"](../../CLAUDE.md)
- [docs/design/slice-b-procurement-gap-audit.md](../design/slice-b-procurement-gap-audit.md)
- `GrnServiceImpl.java:213` — `stockBatchService.createBatch(...)` call site.
- `GrnServiceImpl.java:221` — `stockMoveService.post(...)` call site.
- `ModuleBoundaryTest.java:35` — rule that currently permits the import via
  the broad `..service..` whitelist; should be tightened to encode the
  named exemption.
- `GrnController.java:55` — `POST /api/v1/grns/uid/{uid}/post` returning
  `GrnDto` (200) — the state-transition response shape now codified in the
  hardening checklist §5.
