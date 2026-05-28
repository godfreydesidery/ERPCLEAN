# Slice E1 — Stock Hardening Gap Audit

Section-by-section diff of stock aggregates against
`docs/conventions/hardening-checklist.md`. Backend-engineer drives implementation
from this; PM uses it to scope. Mirrors `slice-c-sales-gap-audit.md`.

## Aggregates audited

- `StockMove`, `ItemBranchBalance` (ledger spine).
- `StockCount`, `StockCountLine`.
- `StockTransfer`, `StockTransferLine`.
- `StockBatch` (FEFO).
- `Adjustment` + `InternalConsumption` (no aggregate; post into `stock_move`).
- Migrations: V11/V13/V15/V17 + permission seeds V12/V14/V16/V18.
- Out: internal-consumption deep redesign, batch-recall expansion, Z-report,
  reporting-module extraction.
- Cross-cutting ADRs: ADR-0003 + ADR-0004 — **stock is the callee in 5 named
  exemptions (procurement, sales, pos, orders, production → stock); this slice
  does not change them.**

## Section 1 — Data layer

**Ledger** (V11):
- [x] Append-only by policy; `StockMove` correctly has no uid / `updated_at` /
  `@Version`. `ItemBranchBalance` has composite key (item_id, branch_id) and
  no uid by design (rebuildable cache).
- [x] Multi-tenant (`company_id` + `branch_id`), BIGINT SEQUENCE, FKs everywhere,
  decimal precision per checklist.
- [ ] **GAP 1.A**: `item_branch_balance` has no `@Version` for optimistic locking
  despite being mutated by every channel. README §7 names row-lock OR `@Version`;
  today's code uses neither — relies on `findById → mutate → save` within one TX.
  Confirm MariaDB row-lock semantics hold under REPEATABLE-READ; if not, edit V11
  to add `version INT NOT NULL DEFAULT 0` + `@Version` on the entity. Not
  E1-blocking unless contention corruption is observed.

**Count** (V13):
- [x] `uid` + `uk_stock_count_uid`; tenancy on header; child rows inherit.
- [ ] **GAP 1.B**: `stock_count` lacks the five audit columns (`created_at`,
  `updated_at`, `created_by`, `updated_by`, `version INT NOT NULL DEFAULT 0`)
  per checklist §1. Reference shape: `stock_batch` (V15:22-26). Edit V13.
- [ ] **GAP 1.C**: Add `ix_stock_count_company_status` for the list-by-status
  filter at scale.

**Transfer** (V13):
- [x] `uid` + `uk_stock_transfer_uid`; bi-branch tenancy clean.
- [ ] **GAP 1.D**: `stock_transfer` lacks the same five audit columns — same fix
  as 1.B.
- [ ] **GAP 1.E**: `StockTransferStatus.IN_TRANSIT` is enum-defined but unreached
  by code (issue jumps DRAFT → ISSUED). Keep the value (web models reference it
  `stock.models.ts:89`); document as "reserved for future explicit-arrival
  transitions" in the enum javadoc.

**Batch** (V15):
- [x] All sections clean — `uid`, audit columns + `@Version`, FEFO indexes,
  `stock_move.batch_id` FK. Reference shape for 1.B + 1.D.

## Section 2 — Domain layer (DTOs + enums)

- [x] All DTOs end with `Dto`; immutable as records; entities Lombok-annotated.
- [x] Response DTOs on uid aggregates carry both `id` + `uid`
  (`StockCountDto`, `StockTransferDto`, `StockBatchDto`).
- [x] Enums in `domain/enums/`, `@Enumerated(EnumType.STRING)` everywhere.
- [ ] **GAP 2.A**: Bean validation **uneven**.
  - `PostAdjustmentRequestDto` has `@NotNull`/`@NotBlank` but no `@Size` on
    `reason`, no `@Digits` on `qty`/`unitCost`, no `@DecimalMin` constraints.
  - `RecallStockBatchRequestDto.reason` — verify `@Size(max=500)`.
  - `ReceiveTransferRequestDto.lines` + `RecordCountsRequestDto.counts` — add
    `@Valid` on the list field so nested constraints cascade.
  - `PostInternalConsumptionRequestDto` — same pass.
  - `PostStockMoveRequestDto` is an internal service-DTO (no validation needed —
    not a request body). Document.
- [ ] **GAP 2.B**: No `StockMoveDtoJsonTest` / `ItemBranchBalanceDtoJsonTest`
  (glob confirmed — only Batch/Count/Transfer pin tests exist). Both DTOs cross
  the wire on every list / stock-card / dashboard read. Pin per checklist §7.

## Section 3 — Service layer

- [x] `*Service` + `*ServiceImpl` pairs on all 8 services; impls
  `@Service @RequiredArgsConstructor` with `final` collaborators.
- [x] Every public method `@Transactional`; writes `@Auditable`.
- [x] External entry points take `String uid`; tenant predicate enforced in
  every `requireXByUid` (`StockCountServiceImpl:147`, `StockTransferServiceImpl:144`,
  `StockBatchServiceImpl:202`).
- [x] No cross-module calls FROM stock (stock is the callee).
- [ ] **GAP 3.A — load-bearing**: `STOCK.OVERSELL` gate half-built.
  `StockMoveServiceImpl#post` (`StockMoveServiceImpl.java:66-70`) checks
  `request.allowOversell()` but does NOT verify the caller's user actually
  holds the perm. The flag alone is self-authorising — that is the hole.
  `AdjustmentServiceImpl#validateAuthoriser` (lines 80-98) belt-and-braces
  this for the adjustment caller, but the other 4 callers (sales / pos /
  customer-return / orders / production) do not. Lock the contract in the
  **stock service** layer: when `allowOversell=true` AND guard would fire,
  also verify `PermissionResolverService.resolve(actorId, ...)
  .contains(STOCK.OVERSELL)`. Throw `AccessDeniedException` → 403. Code in
  plan §5.
- [ ] **GAP 3.B**: `StockMoveServiceImpl` does NOT emit `NegativeStockBlocked.v1`
  before the throw at line 67-70. README §5 promises this event. Emit before
  the throw with payload per plan §4. Same TX (outbox row commits even when
  business write rolls back — acceptable; the alert fires anyway).
- [ ] **GAP 3.C**: `StockCountServiceImpl#postCount` posts variances with
  `allowOversell=true` unconditionally (`StockCountServiceImpl.java:142`). No
  authoriser check fires regardless of variance value. Plan §8 open question
  O1 — does count-post need its own `STOCK.COUNT_APPROVE` dual-control above
  threshold? Decision-blocked.
- [ ] **GAP 3.D**: `StockCountServiceImpl` emits NO outbox events for any
  lifecycle transition. README §5 promises `StockCountClosed.v1`. Add the
  three events from plan §4 (`StockCountStarted.v1`, `StockCountClosed.v1`,
  `StockCountPosted.v1`). The per-variance `StockMoved.v1` events continue
  to fire from the underlying `StockMoveService#post` — the new
  `StockCountPosted.v1` is the aggregated header-level summary.
- [ ] **GAP 3.E**: `StockTransferServiceImpl` emits NO outbox events.
  README §5 promises `TransferIssued.v1` + `TransferReceived.v1`. Plan §4
  renames to `StockTransferIssued.v1` / `StockTransferReceived.v1` for
  consistency with the `Stock*` prefix; document the rename in README.
- [ ] **GAP 3.F**: `AdjustmentServiceImpl` does NOT emit `StockAdjusted.v1` —
  relies on the underlying `StockMoveService#post` emitting `StockMoved.v1`,
  which loses adjustment-specific context (`reason`, `aboveThreshold`,
  `authorisedByUserId`). Add per plan §4.
- [ ] **GAP 3.G**: `StockTransferServiceImpl#issueTransfer` posts with
  `allowOversell=false` (`StockTransferServiceImpl.java:102`) — correct
  semantically (cannot issue stock you don't have, no supervisor override
  path on issue). Document in README; no code change.

## Section 4 — Repository layer

- [x] All repos `extends JpaRepository<X, Long>`; no native queries; portable
  JPQL throughout.
- [x] `findByUid` on every uid-exposing aggregate (Count, Transfer, Batch).
  `StockMoveRepository` correctly has none (no uid). `ItemBranchBalanceRepository`
  correctly has none (composite key).
- [x] Column-named lookups (`existsByBranchIdAndNumber`,
  `findByCompanyIdOrderByCountDateDesc`, `findInvolvingBranch`,
  `findNegativeOnHand`).
- [x] `ModuleBoundaryTest` green (controllers don't touch repos; stock is
  the callee, no FROM-stock cross-module reaches).
- [ ] **GAP 4.A**: `findNegativeOnHand` is unbounded — could return hundreds of
  rows at the long tail. Add a `Pageable` overload or hard LIMIT. Not blocking.

## Section 5 — REST layer

- [x] URL shape `/api/v1/<resource>/uid/{uid}` on all uid-routed controllers
  (Count, Transfer, Batch).
- [x] All `{uid}` `@ValidUlid`; bodies `@Valid`; controllers `@Validated`;
  no manual `ApiResponse` wrap.
- [x] `POST` returns `ResponseEntity.created(URI...)` with Location header.
- [x] **State-transitions return 200 + DTO** on count + transfer (the original
  cohort that landed this pattern — `StockCountController:50,55,61,66`;
  `StockTransferController:51,56,62`).
- [x] `@PreAuthorize` class-level on Count (`STOCK.COUNT`), Transfer
  (`STOCK.TRANSFER`), Batch (`STOCK.BATCH`), Adjustment (`STOCK.ADJUST`),
  InternalConsumption (`STOCK.INTERNAL_CONSUMPTION`).
- [ ] **GAP 5.A**: `StockController` (`/stock-moves`, `/balances`, `/stock-card`)
  has NO class-level `@PreAuthorize` (`StockController.java:21-23`). Pin to
  `STOCK.COUNT` for symmetry with the rest of the read-capable surface.
- [ ] **GAP 5.B**: `StockReportController` (`/reports/stock-negative`,
  `/reports/stock-fast-movers`, `/reports/stock-slow-movers`) has NO
  `@PreAuthorize` (`StockReportController.java:21-23`). Same fix as 5.A
  — pin all three to `STOCK.COUNT` (plan §6 + open question O2).
- [ ] **GAP 5.C**: `StockTransferController#receiveTransfer` (PUT) +
  `StockCountController#recordCounts` (PUT) use PUT rather than the §5
  recommended `POST /uid/{uid}/{action}`. Both are payload-replacement on a
  child list, not a header-state transition — defensible per the §5 bulk-ops
  exception. Document in javadoc; no behavioural change.

## Section 6 — Permissions

- [x] All seven stock perms seeded (ids 18-24) across V12/V14/V16/V18 and
  granted to role 1.
- [x] Codes follow `MODULE.ACTION`.
- [x] **No new perms needed** for Slice E1 (plan §3 confirmed).
- [ ] **GAP 6.A**: `Permissions.java §62-65` declares only 3 of the 7 stock
  perms (`STOCK_OVERSELL`, `STOCK_COUNT`, `STOCK_TRANSFER`). Add the four
  missing constants — `STOCK_BATCH`, `STOCK_ADJUST`, `STOCK_ADJUST_APPROVE`,
  `STOCK_INTERNAL_CONSUMPTION` — in the same `// ---- stock ----` section.
- [ ] **GAP 6.B**: IF open question O1 lands in favour, add
  `V69__seed_stock_count_approve_permission.sql` with **id 123**
  (`STOCK.COUNT_APPROVE`). Collision-check clean.

## Section 7 — Tests

- [x] Service-impl tests for all 6 major services; JSON pins for Batch +
  Count + Transfer DTOs; `@PrePersist` bypassed via reflection.
- [ ] **GAP 7.A**: Missing JSON wire-shape pins for `StockMoveDto` +
  `ItemBranchBalanceDto` (per 2.B). Both have Long-id fields that must
  stringify and enums that must serialise as strings.
- [ ] **GAP 7.B**: No test covers GAP 3.A. After 3.A lands, add:
  (i) `allowOversell=false` + negative → 400 + `NegativeStockBlocked.v1`;
  (ii) `allowOversell=true` + actor without `STOCK.OVERSELL` → 403;
  (iii) `allowOversell=true` + actor with the perm → passes;
  (iv) `allowOversell=true` + no-negative path → resolver not called (perf).
- [ ] **GAP 7.C**: No test asserts the seven NEW outbox events emit with the
  right payload keys. Add `verify(events).publish(eq("StockAdjusted.v1"), …)`
  style assertions — one per new event type, per checklist §7 final bullet.
- [ ] **GAP 7.D**: `ModuleBoundaryTest` — no change required. Stock is the
  callee in 5 exemptions (`ModuleBoundaryTest.java:78-162`); rules stay.
  Verify green.

## Section 8 — Web (Angular)

Stock feature: 8 components at `orbix-engine-web/src/app/features/stock/`
(`adjust`, `balances`, `counts`, `transfers`, `batches`,
`internal-consumption`, `stock-card`, root `stock`). Service + models use
`string` uid throughout.

- [x] Models declare `id: string` + `uid: string` (`stock.models.ts:13-31`,
  62-75, 99-110, 128-143). Every `…Id` field is `string`.
- [x] Service calls uid endpoints; unwraps `ApiResponse<T>`.
- [x] Standalone components, lazy-loaded.
- [ ] **GAP 8.A — Dashboard negative-stock alert (load-bearing per plan §6)**.
  `stockAlertCount` already live (`dashboard.service.ts:43-47`). Add a
  parallel `negativeStockCount(branchId)` calling
  `GET /api/v1/reports/stock-negative?branchId={id}` (endpoint exists at
  `StockReportController:30`). Wire into the dashboard's `alerts` array
  (`dashboard.component.ts:375-396`) as a rose-tinted alert row:
  "N items in negative stock — investigate". Add
  `DASHBOARD_LIVE.negativeStockCount: true`. **Recommend: stay an alert row,
  not a fourth KPI tile** (four KPIs is right; alerts grow).
- [ ] **GAP 8.B — Oversell-override modal**. When `POST /adjustments` returns
  400 with the negative-stock message AND the session holds `STOCK.OVERSELL`,
  surface a confirm-with-reason modal that resubmits with `allowOversell=true`.
  Without the perm: surface the original error + "Contact a supervisor". Lives
  in `adjust.component.ts`. Mirrors the Slice C credit-override modal pattern.
- [ ] **GAP 8.C — Four-state rendering** (loading / empty / error / populated)
  across all 8 components. Cursory read of `balances.component.ts` +
  `counts.component.ts` suggests loading + empty states are thin — tighten
  to the catalog/price-list precedent.
- [ ] **GAP 8.D — Tests**: `npm test` + `npm run e2e` green for stock +
  dashboard. The qa-engineer's `stock.spec.ts` covers cross-component flows.

## Section 9 — Cross-module events

Stock is a **callee** in 5 named exemptions; no calling module SUBSCRIBES to
stock events. The new events from plan §4 are write-and-forget signals for
the future reporting module.

| Event | Status |
|---|---|
| `StockMoved.v1`, `BalanceUpdated.v1`, `LowStockTriggered.v1` | unchanged (StockMoveServiceImpl:81,84,88) |
| `StockBatchCreated.v1`, `StockBatchExhausted.v1`, `StockBatchExpired.v1`, `BatchRecalled.v1` | unchanged (StockBatchServiceImpl) |
| `StockReservationChanged.v1` | unchanged (StockReservationServiceImpl:114) |
| **`StockAdjusted.v1`** | NEW — GAP 3.F |
| **`StockCountStarted.v1`, `StockCountClosed.v1`, `StockCountPosted.v1`** | NEW — GAP 3.D |
| **`StockTransferIssued.v1`, `StockTransferReceived.v1`** | NEW — GAP 3.E |
| **`NegativeStockBlocked.v1`** | NEW — GAP 3.B |

Full payload keys in plan §4.

- [x] Versioned `<Aggregate><Action>.v1`; payload `Map<String,Object>` with
  stable keys; emitted inside the `@Transactional` write.
- [ ] **GAP 9.A — README §5 fidelity**. `modules/stock/README.md §5` lists 7
  events; only 3 are actually emitted. README §11 lists 8 more under "Phase 1.1
  additions"; code emits 4 of those (with `Stock*` prefix drift on 3) plus
  `StockReservationChanged.v1` (which consolidates the promised
  reserve+release pair). Backend: rewrite §5 as a table of (event, type,
  payload keys, emitted-from method) matching the catalog/sales precedent;
  drop §11 as superseded.
- [ ] **GAP 9.B — README "Synchronous callers" sub-section**. Stock is the
  callee in 5 ADR-0004 exemptions (procurement, sales, pos, orders, production).
  Add the sub-section per the procurement README precedent, citing
  ADR-0003 + ADR-0004.

## Section 10 — Verification (QA-image smoke)

Owned by qa-engineer. Expected flows:

- Stock card → list balances → list moves with filter.
- Adjustment list → under-threshold post → above-threshold + authoriser →
  oversell + `STOCK.OVERSELL` → oversell WITHOUT the perm (rejects 403, new
  path from GAP 3.A).
- Count → create → start → record → close → post. Verify three new lifecycle
  events in `domain_event`.
- Transfer → create → issue → receive with variance → close. Verify two new
  events + variance lines on the receive event.
- Dashboard `stockAlerts` (live) + new `negativeStock` alert row both render
  with real data.
- Stock-controller persona passes all flows; stock-clerk persona blocked on
  `STOCK.ADJUST_APPROVE`, `STOCK.OVERSELL`, `STOCK.TRANSFER`; other personas
  regress green.

---

## Cross-cutting (summary for backend-engineer)

- **`STOCK.OVERSELL` service-side perm check** (GAP 3.A + 7.B): the load-
  bearing E1 change. Half-built today — flag is self-authorising.
- **Seven NEW outbox events** (GAP 3.B + 3.D + 3.E + 3.F + 9.A): biggest
  surface change. Same-TX writes; ratifies the README §5 promise.
- **README §5 rewrite** (GAP 9.A + 9.B): list is wrong today; add
  "Synchronous callers" sub-section.
- **JSON pins** for `StockMoveDto` + `ItemBranchBalanceDto` (GAP 2.B + 7.A).
- **DTO validation pass** (GAP 2.A): `@Size` / `@Digits` / `@Valid` constraints
  across the request DTOs.
- **`Permissions.java` four missing constants** (GAP 6.A): same shape as
  Slice B / C cleanup.
- **`@PreAuthorize` on `StockController` + `StockReportController`**
  (GAP 5.A + 5.B): pin both to `STOCK.COUNT`.

## Open questions

Two — both in `slice-e1-plan.md §8`:

1. **Count-post dual control** — adds task #5 + perm id 123 if yes.
2. **`/reports/stock-negative` permission** — pin to `STOCK.COUNT` or leave
   open? Recommend: pin all three stock-report endpoints together.

## Total gap count by section

| Section | Gaps |
|---|---|
| 1 — Data layer | 5 (1.A–1.E) |
| 2 — Domain layer | 2 (2.A, 2.B) |
| 3 — Service layer | 7 (3.A–3.G) |
| 4 — Repository | 1 (4.A) |
| 5 — REST | 3 (5.A–5.C) |
| 6 — Permissions | 2 (6.A, 6.B-conditional) |
| 7 — Tests | 4 (7.A–7.D, where 7.D is no-op verify) |
| 8 — Web | 4 (8.A–8.D) |
| 9 — Events / README | 2 (9.A, 9.B) |
| 10 — QA smoke | 0 (owned by qa-engineer) |
| **Total** | **30 (29 if O1 doesn't land)** |

The three biggest gaps backend will close:
1. **GAP 3.A** — close the `STOCK.OVERSELL` service-side perm check.
2. **GAP 3.B + 3.D + 3.E + 3.F** — emit the seven NEW outbox events.
3. **GAP 9.A** — rewrite the README §5 events table (today's list is fiction).

## What's intentionally NOT in scope (Slice E1)

- Internal-consumption deep redesign.
- Batch / FEFO recall workflow expansion.
- Z-report on count post.
- Reporting-module extraction (ADR-0004 #19 — deferred per Godfrey).
- POS supervisor-PIN UI for oversell (Slice E2).
- `@Version` on `ItemBranchBalance` (GAP 1.A — verify row-lock holds first).

Backend-engineer can start after the qa-engineer's failing `stock.spec.ts`
lands (plan task #1).
