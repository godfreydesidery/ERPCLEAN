# Stock module

## 1. Purpose

The `stock` module owns the truth about *what we hold, where, and what it cost*. It maintains the append-only `stock_move` ledger — the system's single source of truth for inventory — and the denormalised `item_branch_balance` cache that screens and POS read against. Every other transactional module (procurement, sales, pos, wms, production) emits stock moves through this module's API; the ledger semantics, balance arithmetic, and moving-average cost calculation live here.

## 2. Scope

**In scope**
- The `stock_move` append-only ledger and its posting rules.
- The `item_branch_balance` cache: `qty_on_hand`, `qty_reserved`, `qty_in_transit`, `avg_cost`, `last_cost`, `last_moved_at`, `reorder_min`, `reorder_max`, `bin_location`.
- **Moving-average cost calculation.** Owned here, updated on every inbound move (`GRN`, `RETURN_IN`, `TRANSFER_IN`, `PROD_OUTPUT`, `OPENING` where cost is supplied). Outbound moves consume at current `avg_cost` and never recalculate it.
- Stock counts (full / cycle / spot) including variance posting as `ADJUSTMENT` moves on close.
- Inter-branch transfers (issue → in-transit → receive) including variance flagging.
- Stock adjustments with reason, supervisor threshold, and audit trail.
- Negative-stock policy enforcement (`STOCK.OVERSELL` privilege gate).
- Low-stock / negative-stock alert rule (the *rule*, evaluated on every balance update).

**Out of scope**
- **BOM definition and explosion.** Owned by `production`; this module only consumes `PROD_CONSUME` / `PROD_OUTPUT` moves emitted by it.
- **Reorder alert UI / dashboard tiles.** This module evaluates the threshold and emits `LowStockTriggered.v1`; `reporting` and the Web ERP shell render alerts.
- **Item master data** (price, group hierarchy, multi-barcode). Owned by `catalog`.
- **GRN / Sales Invoice posting workflow.** Procurement and sales orchestrate their own postings, then call into this module to apply the resulting moves.

## 3. Domain model

| Aggregate / table | Role |
|---|---|
| `stock_move` | Append-only signed-quantity ledger row. Immutable. Source of truth. |
| `item_branch_balance` | Denormalised current-position cache per `(item_id, branch_id)`. Rebuildable from `stock_move`. |
| `stock_count` | Physical count session header (`DRAFT` → `IN_PROGRESS` → `CLOSED` → `POSTED`). |
| `stock_count_line` | One row per item in a count, with frozen `system_qty`, captured `counted_qty`, computed `variance_qty`. |
| `stock_transfer` | Inter-branch transfer header (`DRAFT` → `ISSUED` → `IN_TRANSIT` → `RECEIVED` → `CLOSED`). |
| `stock_transfer_line` | Per-item issued/received quantities with cost frozen at issue. |

Move types recognised by the ledger: `GRN`, `SALE`, `RETURN_IN`, `RETURN_OUT`, `DAMAGE`, `ADJUSTMENT`, `TRANSFER_OUT`, `TRANSFER_IN`, `PROD_CONSUME`, `PROD_OUTPUT`, `OPENING`.

See `DATA-MODEL.md §4` for full attribute tables.

## 4. Key business flows

**4.1 Inbound move (GRN posting).** `procurement` posts a GRN line and emits `GrnPosted.v1`. The stock module's listener (or a direct in-tx call from `app`) inserts a `stock_move` with `direction=IN`, `move_type=GRN`, signed-positive `qty`, supplied `cost_amount`; updates `item_branch_balance.qty_on_hand += qty`; recalculates `avg_cost` via the moving-average formula in §10; refreshes `last_cost` and `last_moved_at`. All in one DB transaction with the outbox event row.

**4.2 Outbound move (sale / POS).** `sales` or `pos` calls the stock module to consume stock. The module evaluates §10's negative-stock invariant: if `qty_on_hand - requested_qty < 0` and the caller did not supply an `override_by_user_id`, the call fails with `NegativeStockBlocked.v1` emitted and a problem-detail response. On success: inserts a `stock_move` with `direction=OUT`, signed-negative `qty`, `cost_amount = balance.avg_cost` at posting time; decrements `qty_on_hand`; does **not** touch `avg_cost`.

**4.3 Stock count.** Storekeeper starts a session (US-STOCK-004): system snapshots `system_qty` per item from current balances, status moves to `IN_PROGRESS`. Counters enter `counted_qty` in waves (US-STOCK-005); manager closes (US-STOCK-006): for each line where `counted_qty - system_qty ≠ 0`, the module posts an `ADJUSTMENT` `stock_move` for the difference using current `avg_cost`. Status moves to `POSTED`. Variances above a configurable threshold per item require an explicit reason captured on the line.

**4.4 Inter-branch transfer.** Issuing branch posts `TRANSFER_OUT` immediately on issue, decrementing source `qty_on_hand` and incrementing destination `qty_in_transit`. Status moves to `IN_TRANSIT`. Receiving branch confirms (US-STOCK-008): posts `TRANSFER_IN` at destination, clears `qty_in_transit`, increments destination `qty_on_hand`. If `received_qty ≠ issued_qty`, a reason is required and a variance event is emitted for investigation.

**4.5 Adjustment with reason (US-STOCK-003).** Requires `STOCK.ADJUST` privilege. Adjustments above a configurable threshold require supervisor authorisation (short-lived token per `ARCHITECTURE.md §2.5`). Posts a single `ADJUSTMENT` move; reason stored on `stock_move.notes` and in the audit event.

**4.6 Negative-stock block + supervisor override (US-STOCK-010).** Enforced at the line level inside the stock service, called by POS and Web invoice posting. Failure offers a supervisor-PIN authorisation flow; an authorised oversell records the supervisor identity on the resulting move and emits an audit event.

## 5. Module interactions

**Depends on**
- `catalog` — for `item_id` references and item metadata (`type`, `uom`).
- `platform/company` — for `company_id` / `branch_id` scoping.
- `platform/events` — for outbox publication.

**Publishes**
- `StockMoved.v1` — every successful `stock_move` insert.
- `BalanceUpdated.v1` — every `item_branch_balance` row update.
- `StockCountClosed.v1` — when a count transitions to `POSTED`, with the list of generated adjustments.
- `TransferIssued.v1` — when a `stock_transfer` transitions to `ISSUED`.
- `TransferReceived.v1` — when it transitions to `RECEIVED`, including variance lines.
- `LowStockTriggered.v1` — when a balance update crosses below `reorder_min` (rising-edge only).
- `NegativeStockBlocked.v1` — when an outbound attempt fails the negative-stock guard with no override.

**Consumes**
- `GrnPosted.v1` (from `procurement`) → `GRN` inbound move.
- `SalesInvoicePosted.v1` (from `sales`) → `SALE` outbound move.
- `PosSaleClosed.v1` (from `pos`) → `SALE` outbound move (via sync push).
- `ProductionOutputPosted.v1` / `ProductionConsumePosted.v1` (from `production`, when added) → `PROD_OUTPUT` / `PROD_CONSUME` moves.

Each consumer handler is **idempotent**: the listener computes `(source_doc_type, source_doc_id, line_seq)` (stored as `ref_type`, `ref_id`, plus a derived sequence) and rejects duplicates. The `client_op_id` UUID from sync (per `ARCHITECTURE.md §2.9`) keys the same guarantee end-to-end.

## 6. API surface

| Verb | Path | Purpose |
|---|---|---|
| GET | `/api/stock-moves` | Read-only ledger query (filters: item, branch, date range, move_type, ref). |
| GET | `/api/stock-moves/{id}` | Single move detail. |
| GET | `/api/balances` | Read-only balance grid (filters: item, branch, below-reorder, negative). |
| GET | `/api/balances/{itemId}/{branchId}` | Single balance row (stock card header). |
| POST | `/api/adjustments` | Post a stock adjustment with reason. |
| POST | `/api/stock-counts` | Start a count session. |
| PUT  | `/api/stock-counts/{id}/lines` | Capture / update counted quantities. |
| POST | `/api/stock-counts/{id}/close` | Close session and post variance adjustments. |
| POST | `/api/stock-transfers` | Create a transfer in `DRAFT`. |
| POST | `/api/stock-transfers/{id}/issue` | Post `TRANSFER_OUT`. |
| POST | `/api/stock-transfers/{id}/receive` | Post `TRANSFER_IN`. |

All write endpoints honour the `clientOpId` idempotency contract from `ARCHITECTURE.md §2.9`.

## 7. Persistence

- Flyway migrations under `db/migration/common/`, named `V<N>__stock_<purpose>.sql` (e.g. `V12__stock_move.sql`, `V13__stock_balance.sql`, `V14__stock_count.sql`, `V15__stock_transfer.sql`). Dialect-specific scripts only if forced, per `ARCHITECTURE.md §2.3`.
- Indexes on `stock_move`: `(item_id, branch_id, at)`, `(branch_id, at)`, `(ref_type, ref_id)`. The last enables idempotency lookup by source doc.
- **Balance update strategy.** Two acceptable patterns; choose per call site:
  - *Row-level lock* (`SELECT ... FOR UPDATE` on the balance row) inside the same tx that inserts the move. Default. Safe under contention, ordering-deterministic.
  - *Upsert with version check* (`@Version` on `item_branch_balance`) for high-throughput POS sync where the balance is server-authoritative and retries are cheap.
- `stock_move` partitioning (e.g. by `at` month) is a **long-term operational concern, not MVP**. The schema must not assume partitioning exists.

## 8. User stories

**P1 — MVP critical**
- US-STOCK-001 View stock on hand for an item.
- US-STOCK-002 View stock card (movement history).
- US-STOCK-003 Adjust stock with a reason.
- US-STOCK-009 See alerts for low or negative stock.
- US-STOCK-010 Block oversell unless authorised.

**P2 — Hardening**
- US-STOCK-004 Start a stock count.
- US-STOCK-005 Capture counted quantities.
- US-STOCK-006 Close a stock count and post variances.

**P3 — Breadth**
- US-STOCK-007 Issue an inter-branch transfer.
- US-STOCK-008 Receive an inter-branch transfer.

## 9. Open questions

From `PRD.md §13` and `DATA-MODEL.md §16`, only the items that materially constrain this module:

1. **Cost method per item vs per branch** (DATA-MODEL §16.2). Schema keeps both `item.avg_cost` (global, reporting) and `item_branch_balance.avg_cost` (per-branch, margin). Confirm both are wanted; if not, drop the global field and recompute reports from balances.
2. **Negative-stock policy** (DATA-MODEL §16.4). Schema allows negative balances; rule is application-level via `STOCK.OVERSELL`. Confirm this remains policy and not a hard DB constraint.
3. **Stock-affecting performance target** (PRD §13.2). Peak concurrency at largest branch drives whether row-level locking on `item_branch_balance` is sufficient or whether high-throughput SKUs need the upsert path.
4. **Variance threshold defaults** for adjustment supervisor authorisation and stock-count line reason capture — currently "configurable", with no default. Needs business sign-off before MVP.

## 10. Implementation notes

- **Layout.** Hexagonal, per `ARCHITECTURE.md §2.2`: `api` (controllers, DTOs), `app` (transactional services, orchestration), `domain` (entities, value objects, domain events, invariants), `infra` (JPA repositories, outbox adapter).
- **Invariants** (enforced in `domain` and asserted by integration tests):
  - `stock_move` is append-only. No update, no delete. Corrections are new moves (typically `ADJUSTMENT`).
  - Every `stock_move` insert is transactionally consistent with the corresponding `item_branch_balance` update — same DB transaction, no eventual consistency.
  - Moving-average cost is recalculated **on inbound moves only**, using `new_avg = (old_qty * old_cost + recv_qty * recv_cost) / (old_qty + recv_qty)`. Guarded against `(old_qty + recv_qty) == 0`; in that case `new_avg = recv_cost`. Outbound moves consume at current `avg_cost` and leave it unchanged.
  - Negative stock is blocked unless the caller supplies an `override_by_user_id` resolved from a valid supervisor authorisation token. Override identity is persisted on the resulting move and audited.
  - `direction` must match the sign of `qty`; enforced at the domain layer, double-checked by a DB `CHECK` constraint where the dialect supports it.
- **Multi-tenant.** Every read and write carries `company_id` + `branch_id`. Repositories inherit the base interface from `platform/company` that injects the predicate automatically (`ARCHITECTURE.md §2.6`).
- **Idempotency.** `(source_doc_type, source_doc_id)` is unique across `stock_move` for sourced moves; the `clientOpId` UUID from sync push is mapped to this pair on entry. Duplicate deliveries from the event bus are absorbed silently.
- **Outbox.** All published events (`StockMoved.v1`, `BalanceUpdated.v1`, `StockCountClosed.v1`, `TransferIssued.v1`, `TransferReceived.v1`, `LowStockTriggered.v1`, `NegativeStockBlocked.v1`) are written to `domain_event` in the same DB transaction as the business write, per `ARCHITECTURE.md §2.10`. No in-memory Spring events for cross-module signalling.
- **Rebuild path.** A maintenance job can truncate `item_branch_balance` and replay `stock_move` in `(item_id, branch_id, at)` order to reconstruct the cache. This path is exercised by a nightly drift-check in non-production environments.

---

## 11. Phase 1.1 additions

See [docs/design/PHASE-1.1-ADDITIONS.md](../../../../../../../../docs/design/PHASE-1.1-ADDITIONS.md).

**New table:**
- `stock_batch` — per-branch batch row carrying `batch_no`, `manufactured_at`, `expiry_at`, `qty_received`, `qty_on_hand`, `cost`, `source_doc_type` (`GRN` / `PRODUCTION_OUTPUT` / `OPENING`), `status` (`ACTIVE` / `EXHAUSTED` / `EXPIRED` / `RECALLED`). UNIQUE `(branch_id, item_id, batch_no)`.

**`stock_move` gains:**
- `batch_id BIGINT FK → stock_batch` nullable (populated for items with `tracks_batches = true`)
- `section_id BIGINT FK → section` nullable (stamped on production / section-transfer)
- `consumption_category VARCHAR(20)` nullable — `CANTEEN` / `DISPLAY` / `SAMPLES` / `DONATION` / `MAINTENANCE` / `OTHER` (required for `INTERNAL_CONSUMPTION`)
- `authorised_by_user_id BIGINT FK` nullable
- New `move_type` values: `INTERNAL_CONSUMPTION`, `STAFF_PURCHASE`, `EMPLOYEE_GIFT`, `RESERVED` (layby reservation), `EXPIRY_WRITE_OFF`
- New `direction` value: `RESERVED` (sits alongside IN / OUT). Reserved stock is on hand but not available; `qty_reserved` tracked on `item_branch_balance`.

**`item_branch_balance` gains:**
- `qty_reserved DECIMAL(18,4)` — sum of open RESERVED moves
- Effective availability is `qty_on_hand − qty_reserved`

**FEFO consumption:**
- For items with `tracks_batches = true`, outbound moves pick the batch with the earliest non-null `expiry_at` (FIFO among same-expiry).
- Cost flows from the consumed batch, not the moving average.
- POS / sales / production-consume must call `stock` to resolve the batch at consumption time; offline POS attaches its locally-resolved `batch_id` and the server validates.

**Expired-stock workflow:**
- Scheduled job marks `ACTIVE` batches with `expiry_at < now` and emits `BatchExpired.v1`.
- Manual write-off via `stock_move` of type `EXPIRY_WRITE_OFF` (so accountants approve it).

**Internal-consumption rules:**
- Every `INTERNAL_CONSUMPTION` move requires `authorised_by_user_id` + `consumption_category` + non-empty `reason` (stored in existing `stock_move.notes`).
- Privilege `STOCK.INTERNAL_CONSUMPTION_AUTHORISE`.

**New events:**
- `BatchCreated.v1`, `BatchExpired.v1`, `BatchRecalled.v1`, `BatchExhausted.v1`
- `StockReserved.v1`, `StockReservationReleased.v1`
- `InternalConsumptionPosted.v1`

**New stories:** US-STOCK-011 .. US-STOCK-016 (see USER-STORIES.md Phase 1.1 section).
