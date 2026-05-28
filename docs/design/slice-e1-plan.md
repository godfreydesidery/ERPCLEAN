# Slice E1 — Harden the Stock spine

| Field | Value |
|---|---|
| Branch | `harden/stock-spine` |
| Prereqs | Slice A (Party), Slice D (Day/Cash), Slice B (LPO + GRN), Slice C (Sales) all merged at `4494a53` |
| Owner | PM coordinating; backend + frontend + qa + architect |
| Date | 2026-05-27 |

Current state confirmed by code-read (not just docs): stock is the **callee** in ADR-0004's
named-exemption inventory — five caller modules (procurement, sales, pos, orders,
production) reach in via `StockMoveService` / `StockBatchService` / `StockReservationService`
interfaces. The stock module itself already has uid on `StockCount`, `StockTransfer`,
`StockBatch`; state-transition endpoints already return 200 + DTO; FEFO + moving-average
cost are implemented and stable; `STOCK.OVERSELL` is already seeded (V12, id 18) and
already wired into the negative-stock guard inside `StockMoveServiceImpl.post(...)`. The
gaps are not "build the spine" — they are checklist conformance (validation, JSON pins,
audit on a few missing transitions, missing UI hook for the dashboard's negative-stock
tile), README accuracy vs. emitted events, and a dual-control gap on stock counts.

---

## 1. Scope

**In**
- `StockMove` + `ItemBranchBalance` hardened to the checklist bar (JSON pins, README
  fidelity, validation on the service-DTO inputs, audit-emission test).
- `StockCount` + `StockCountLine`: full count-on-post variance flow audited end-to-end;
  add a dual-control gate on big-variance count post (mirrors `STOCK.ADJUST_APPROVE`).
- `StockTransfer` + `StockTransferLine`: receive-with-variance event payload, lifecycle
  audit, ineligible-status responses.
- `StockBatch`: confirm FEFO drain + recall paths are checklist-clean; document the
  expiry job ownership in the README.
- `Adjustment` + `InternalConsumption`: confirm the `allowOversell` flag + dual-control
  on above-threshold + verify oversell guard fires from `StockMoveServiceImpl`.
- **`STOCK.OVERSELL` gate**: lock the contract — the perm + service-side guard already
  exist; verify the precondition that EVERY caller threads the `allowOversell` boolean
  honestly (5 callers audited). Add a service-side check that the caller's user actually
  HOLDS `STOCK.OVERSELL` when `allowOversell=true` — today the guard only checks the flag.
- **Dashboard `negativeStock` tile**: surface the existing
  `GET /api/v1/reports/stock-negative` count alongside `stockAlerts`. The endpoint already
  exists; the web layer just doesn't call it yet.
- Outbox event catalogue: ratify the existing `StockMoved.v1` / `BalanceUpdated.v1` /
  `LowStockTriggered.v1` shapes, ADD `StockAdjusted.v1`, `StockCountStarted.v1`,
  `StockCountClosed.v1`, `StockCountPosted.v1`, `StockTransferIssued.v1`,
  `StockTransferReceived.v1`, `NegativeStockBlocked.v1` per the §10 module-README
  promise that today's code does not deliver.

**Out**
- **Internal-consumption deep redesign** (the categories + per-category report pivots).
  Today's two-step `InternalConsumptionService` is correct shape; report refactor is a
  future slice.
- **Batch / FEFO recall workflow expansion** (post-stability — multi-line recall, recall
  with replacement, recall-customer-notification). Today's single-batch `recall(uid)` is
  enough for Slice E1.
- **Z-report on count post** (the cashier-style summary print). Counts close with a list
  of variances; the printable Z-report is a reporting-module concern.
- **Reporting-module extraction** — ADR-0004 #19 latent gap; deferred per Godfrey.
  ArchUnit stays on the "relaxed" `..repository..` allowance form per ADR-0004 §5; no
  named exemption changes in this slice.
- **POS supervisor-PIN UI for oversell** — E1 lands the perm + service-side guard; the
  modal lands in E2.

**Prerequisites confirmed**
- ADR-0004 named exemptions for `procurement → stock`, `sales → stock`, `pos → stock`,
  `orders → stock`, `production → stock` are live and tested
  (`ModuleBoundaryTest.java:78-162`). Stock is the **callee**; this slice does not change
  exemptions.

## 2. Aggregates in scope

| Aggregate | State |
|---|---|
| `ItemBranchBalance` | Live; rebuildable cache; no uid (composite key — by design). |
| `StockMove` | Live; append-only; no uid by design (immutable ledger row). |
| `StockCount` + `StockCountLine` | Live; DRAFT → IN_PROGRESS → CLOSED → POSTED; uid on header. |
| `StockTransfer` + `StockTransferLine` | Live; DRAFT → ISSUED → (IN_TRANSIT) → RECEIVED → CLOSED; uid on header. `IN_TRANSIT` is reserved but unused (issue jumps straight to ISSUED today — verify in gap audit). |
| `StockBatch` (FEFO) | Live; uid present; ACTIVE → EXHAUSTED / EXPIRED / RECALLED. |
| `Adjustment` (no aggregate, posts straight to `stock_move`) | Live; dual-control + oversell gates wired in `AdjustmentServiceImpl`. |
| `InternalConsumption` (no aggregate; posts to `stock_move`) | Live; auth + category enforced. |

## 3. Permission band

Current high-water across all migrations: **122** (`SALES_INVOICE.REPRINT`, V28 Slice C).

**Stock perm-id band confirmed already seeded** — no new ids needed:

| Id | Code | Seeded in |
|---|---|---|
| 18 | `STOCK.OVERSELL` | V12 |
| 19 | `STOCK.COUNT` | V14 |
| 20 | `STOCK.TRANSFER` | V14 |
| 21 | `STOCK.BATCH` | V16 |
| 22 | `STOCK.ADJUST` | V18 |
| 23 | `STOCK.ADJUST_APPROVE` | V18 |
| 24 | `STOCK.INTERNAL_CONSUMPTION` | V18 |

**Proposed: no new perms for Slice E1.** The full stock spine is already encoded.
Collision-check is trivially clean — ids 18-24 are continuous and below the
Slice-B/C bands (110-113, 120-122). If a `STOCK.COUNT_APPROVE` becomes necessary
for the dual-control gap (§7 task 4 — see open question O1), it lands as id **123**
in a new `V69__seed_stock_count_approve_permission.sql`. Collision check:

```powershell
Select-String -Path 'orbix-engine-api/src/main/resources/db/migration/common/V*permission*.sql' `
              -Pattern '^\s*\(123, '''
```

→ no matches today.

`Permissions.java §62-65` already declares the three E1-relevant constants
(`STOCK_OVERSELL`, `STOCK_COUNT`, `STOCK_TRANSFER`). Slice gap-close adds constants
for the four remaining seeded perms (`STOCK_BATCH`, `STOCK_ADJUST`,
`STOCK_ADJUST_APPROVE`, `STOCK_INTERNAL_CONSUMPTION`) so the SpEL string-literal use
sites can switch to the constant.

## 4. Outbox event catalogue

The §10 stock README promises seven events; today's code emits five distinct types
(`StockMoved.v1`, `BalanceUpdated.v1`, `LowStockTriggered.v1`, `StockBatchCreated.v1`,
`StockBatchExhausted.v1`, `StockBatchExpired.v1`, `BatchRecalled.v1`,
`StockReservationChanged.v1`). The discrepancy is the slice-relevant gap.

| Event | Payload keys | Status | Known subscribers |
|---|---|---|---|
| `StockMoved.v1` | `stockMoveId`, `itemId`, `branchId`, `qty` | unchanged (emit lives in `StockMoveServiceImpl:81`) | reporting |
| `BalanceUpdated.v1` | `itemId`, `branchId`, `qtyOnHand` | unchanged (`StockMoveServiceImpl:84`) | reporting |
| `LowStockTriggered.v1` | `itemId`, `branchId`, `qtyOnHand` | unchanged (`StockMoveServiceImpl:88`) | reporting; dashboard `stockAlerts` tile |
| `StockBatchCreated.v1` | `batchId`, `itemId`, `branchId`, `batchNo`, `qty`, `expiryAt` | unchanged | reporting |
| `StockBatchExhausted.v1` | `batchId`, `itemId`, `branchId` | unchanged | reporting |
| `StockBatchExpired.v1` | `batchId`, `itemId`, `branchId`, `batchNo`, `qtyOnHand`, `expiryAt` | unchanged | reporting |
| `BatchRecalled.v1` | `batchId`, `itemId`, `branchId`, `batchNo`, `qtyWrittenOff`, `reason` | unchanged | reporting |
| `StockReservationChanged.v1` | `stockMoveId`, `itemId`, `branchId`, `qty`, `refType`, `refId` | unchanged | reporting; orders |
| **`StockAdjusted.v1`** | `stockMoveId`, `itemId`, `branchId`, `qty`, `unitCost`, `reason`, `authorisedByUserId`, `aboveThreshold`, `oversell` | **NEW** — emit from `AdjustmentServiceImpl` post the move | reporting |
| **`StockCountStarted.v1`** | `stockCountId`, `uid`, `number`, `branchId`, `lineCount` | **NEW** — `StockCountServiceImpl#startCount` | reporting |
| **`StockCountClosed.v1`** | `stockCountId`, `uid`, `branchId`, `varianceCount`, `varianceValue` | **NEW** — `StockCountServiceImpl#closeCount` | reporting; future reporting |
| **`StockCountPosted.v1`** | `stockCountId`, `uid`, `branchId`, `adjustmentLines: List<{itemId, variance, cost}>` | **NEW** — `StockCountServiceImpl#postCount` after variance moves | reporting |
| **`StockTransferIssued.v1`** | `stockTransferId`, `uid`, `number`, `fromBranchId`, `toBranchId`, `totalCost` | **NEW** — `StockTransferServiceImpl#issueTransfer` | reporting |
| **`StockTransferReceived.v1`** | `stockTransferId`, `uid`, `fromBranchId`, `toBranchId`, `varianceLines: List<{itemId, issuedQty, receivedQty}>` | **NEW** — `StockTransferServiceImpl#receiveTransfer` | reporting |
| **`NegativeStockBlocked.v1`** | `itemId`, `branchId`, `requestedQty`, `availableQty`, `refType`, `refId` | **NEW** — emit from the negative-stock guard before the throw at `StockMoveServiceImpl:67` | dashboard alerting; reporting |

No event today is consumed by another stock-callee module synchronously — all stock
writes from procurement / sales / pos / orders / production are **direct sync-TX calls**
into `StockMoveService` per ADR-0004. The new events are write-and-forget signals for
the future reporting module; no cross-module subscribers are wired today.

## 5. STOCK.OVERSELL gate — locked design

Mirror procurement's `allowOversell` flag pattern. The contract:

- **Where**: `StockMoveServiceImpl#post(PostStockMoveRequestDto)` — the negative-stock
  guard at `StockMoveServiceImpl.java:66-70` already throws unless
  `request.allowOversell()` is true. Slice E1 closes the half-built gate.
- **Field shape**: `PostStockMoveRequestDto.allowOversell` boolean (already exists).
  Caller request DTOs that wrap stock-move posting carry their own `allowOversell`
  (`PostAdjustmentRequestDto.allowOversell` already present; symmetric on sales /
  pos / customer-return / orders / production request shapes — verify in gap audit).
- **Service-side guard** (NEW): when `request.allowOversell() == true` AND the negative
  guard would fire, **also** verify the caller's user holds `STOCK.OVERSELL` via
  `PermissionResolverService`. Today the flag alone is sufficient — that is a hole.
  Locked contract:
  ```java
  if (qty.signum() < 0 && balance.wouldGoNegative(qty.abs())) {
      if (!request.allowOversell()) {
          events.publish("NegativeStockBlocked.v1", ...);
          throw new IllegalArgumentException("Insufficient stock ... needs STOCK.OVERSELL override");
      }
      // NEW: the flag is not self-authorising; the user must hold the perm.
      if (!permissions.resolve(context.userId(), context.companyId(), branchId)
              .contains(Permissions.STOCK_OVERSELL)) {
          throw new AccessDeniedException("STOCK.OVERSELL required to drive qty negative");
      }
  }
  ```
  → 400 on missing flag (stays the same), **403** on flag-without-perm (new branch).
  Caller-side perm checks (`AdjustmentServiceImpl#validateAuthoriser`) are belt-and-
  braces; the **stock service itself** is the new single source of truth.

- **Audit**: existing `@Auditable(action="POST", entityType="StockMove")` already
  records the actor. The new `NegativeStockBlocked.v1` event captures the failed
  attempts; the successful overrides surface via the `StockMoved.v1` event's
  authoriser fields already on `StockMove` (`authorised_by_user_id`).

This is the single load-bearing service-layer change Slice E1 makes. Pattern mirrors
procurement's `allowOversell` on `PostAdjustmentRequestDto` — no new architectural shape.

## 6. Dashboard payoff

Confirmed by code-read:

- `stockAlertCount` is **already live** (`dashboard.service.ts:43-47`,
  `dashboard.component.ts:354,476-479`). Counts items where `reorderMin != null` and
  `qtyOnHand <= reorderMin`; web-side derivation over `GET /api/v1/balances`. The
  signal name is `stockAlerts`; live flag is `DASHBOARD_LIVE.stockAlertCount`.
- **NEW**: `negativeStock` tile, alongside the existing `stockAlerts`. The backend
  endpoint already exists: `GET /api/v1/reports/stock-negative?branchId={id}` returns
  `List<ItemBranchBalanceDto>` (`StockReportController.java:30`). The web layer adds
  a derived `negativeStockCount` observable on `DashboardService` and a third tile or
  rolls it into the alerts list (`dashboard.component.ts:375-396`). Recommendation:
  surface it as a **rose-tinted alert row** ("3 items in negative stock — investigate")
  rather than a fourth KPI tile. Four KPIs is the right number; alerts grow.
- New `DASHBOARD_LIVE.negativeStockCount: true` flag alongside `stockAlertCount`.

No new permission. The `/reports/stock-negative` endpoint has no `@PreAuthorize` today
(`StockReportController:24` — inherits no class-level annotation). Slice gap-close
either pins it to `STOCK.COUNT` (the closest read-capable seeded grant — same shape
as the `store-manager` persona uses today) or leaves open. Recommendation: pin to
`STOCK.COUNT` for symmetry with the rest of the read endpoints; the audit lists this
as GAP 5.E.

## 7. Task list (TDD-style — QA first, architect second, backend third)

| # | Owner | Deliverable | Acceptance signal |
|---|---|---|---|
| 1 | **qa-engineer** | Failing Playwright spec `stock.spec.ts` — stock-controller persona posts an adjustment (under and above threshold), runs a full count cycle (create → start → record → close → post with variance), issues a transfer + receives it with variance, exercises oversell-with-perm vs. oversell-without-perm; stock-clerk persona tests the negative branches (no `STOCK.ADJUST_APPROVE`, no `STOCK.OVERSELL`, no `STOCK.TRANSFER`). ~10 scenarios, all expected-fail at start. | Spec runs, ≥7 expected-fails on `main`. Land before backend work. |
| 2 | **qa-engineer** | Extend `e2e/test-users.ts` — already done at lines 192-228 (`stock-controller` with the five perms, `stock-clerk` with two). Verify the bootstrap silently drops `STOCK.OVERSELL` is no longer needed (the perm is seeded). | Persona bootstrap logs no skipped codes for stock personas. |
| 3 | **solutions-architect (you)** | This plan + the gap audit (`slice-e1-stock-gap-audit.md`). No ADR; existing ADR-0004 already covers stock as callee. | Both docs committed; backend can start. |
| 4 | **backend-engineer** | Gap-close pass on `stock/`: (a) close the `STOCK.OVERSELL` service-side perm check (§5); (b) emit the seven NEW outbox events (§4) inside the same `@Transactional` writes; (c) bring DTO validation to checklist § 2 (`CreateStockTransferRequestDto.lines.itemId @Positive`, missing `@Size` on `notes`, `reason` constraints); (d) pin `/reports/stock-negative` to `STOCK.COUNT`; (e) add `Permissions.java` constants for the four missing stock perms; (f) JSON wire-shape pins for `StockMoveDto` + `ItemBranchBalanceDto`; (g) audit-emission test on `StockMoveService` (existing tests mock the publisher; add one that asserts the negative-block event). | `mvn -pl orbix-engine-api test` green; `ModuleBoundaryTest` green with no exemption changes. |
| 5 | **backend-engineer** | Optional dual-control on count post — only land if the open question O1 is decided in favour. Adds `STOCK.COUNT_APPROVE` perm (id 123), wires it into `StockCountServiceImpl#postCount` when total variance value exceeds `STOCK_ADJUSTMENT_THRESHOLD`. Mirror of the adjustment dual-control. | Service-test branches: under-threshold posts unaided; above-threshold without authoriser rejects; above-threshold with authoriser holding the new perm passes. |
| 6 | **frontend-engineer** | Bring stock screens to 200-DTO + four-state rendering (`adjust.component`, `balances.component`, `counts.component`, `transfers.component`, `batches.component`, `internal-consumption.component`, `stock-card.component`). Add oversell-override modal on the adjust screen — when 400 returns with the negative-stock error AND the session holds `STOCK.OVERSELL`, surface a confirm-with-reason modal that resubmits with `allowOversell=true`. Add the negative-stock alert tile on the dashboard (§6). Stock models already use `id: string` + `uid: string` — verify. | `npm test` + `npm run e2e` green for stock + dashboard features. |
| 7 | **backend-engineer + frontend-engineer** | Hardening-checklist sweep on stock (sections 1-9): wire-shape JSON pins (4), README accuracy fix (events table is wrong today — §10 promises seven events the code doesn't emit), README "Synchronous callers" sub-section citing ADR-0003 + ADR-0004 (stock is the callee five times). | All 10 checklist boxes ticked in PR body. |
| 8 | **qa-engineer (final QA gate)** | Re-run `stock.spec.ts` (expected-fails removed), full e2e suite, QA-image rebuild from `orbix-engine-infra/qa/Dockerfile`, smoke-test against the QA container with **2 new + 4 existing personas** (stock-controller all flows; stock-clerk all blocked paths; cashier/store-manager untouched; accountant + procurement-officer regression). | All scenarios green; QA report attached to PR. |

## 8. Open questions for Godfrey

1. **Count-post dual control** — should `StockCountServiceImpl#postCount` require a
   `STOCK.COUNT_APPROVE` authoriser when the total signed variance value exceeds the
   `STOCK_ADJUSTMENT_THRESHOLD`? The adjustment path enforces this; count post bypasses
   today (`StockCountServiceImpl.java:140-143` posts with `allowOversell=true` and no
   authoriser). Argument for: a 10000-unit count variance is functionally a giant
   adjustment; the dual-control is what stops shrinkage cover-ups. Argument against:
   counts are end-of-cycle with managers physically present; the START transition is
   already gated by `STOCK.COUNT`. Recommendation: yes — adds task #5, one perm
   (id 123), one rule. Decision blocks task #5 only; rest of the slice ships either way.
2. **`/reports/stock-negative` permission** — pin to `STOCK.COUNT` (the closest
   read-capable seeded grant) or leave open like `/reports/stock-fast-movers` and
   `/reports/stock-slow-movers` are today? Both report endpoints inherit no
   `@PreAuthorize` (`StockReportController.java:24-28`). Pinning closes the hole;
   leaving open is the consistent shape with the other two report endpoints — which
   is itself a latent gap. Recommendation: pin all three to `STOCK.COUNT` in the
   same gap-close pass; opens a 30-line audit issue separately to track stock-report
   permissioning at the slice level.

## 9. Need ADR?

**No.** Stock is the callee in ADR-0004's named exemptions; the five caller pairs
(procurement, sales, pos, orders, production → stock) are already encoded in
`ModuleBoundaryTest.java:78-162`. The `STOCK.OVERSELL` gate is a service-internal
permission check, not a cross-module pattern; the `allowOversell` flag pattern
already follows the procurement precedent on `PostAdjustmentRequestDto`. New event
types are documented in the module README and DATA-MODEL.md §4 already lists them
as the intended shape — emission ≠ ADR.

If task #5 lands (count-post dual control), the new `STOCK.COUNT_APPROVE` perm is
a per-module convention extension, **not** an architectural decision — it follows
the existing `STOCK.ADJUST_APPROVE` shape (`AdjustmentServiceImpl#validateAuthoriser`).
No ADR needed.

---

**Need decision on:** the two open questions in §8. The slice is unblocked otherwise —
task #1 (qa-engineer writes the failing spec) can start in parallel with this plan
landing.
