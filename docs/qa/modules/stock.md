# Stock — test plan

Module-level tests for the append-only `stock_move` ledger, `item_branch_balance`, batches, transfers, counts, and FEFO consumption.

## Ledger fundamentals

### TC-STOCK-001 — Inbound move increments balance + recalcs avg_cost [P1]
**Steps:**
1. Initial state: `qty_on_hand = 100, avg_cost = 80`.
2. Apply inbound move: qty 50, cost 100.

**Expected:**
- `stock_move` row inserted with `direction = IN`, `move_type = GRN`, `cost_amount = 100`.
- Balance: `qty_on_hand = 150`.
- New `avg_cost = (100 × 80 + 50 × 100) / 150 = 86.67`.
- `last_cost = 100`.
- `last_moved_at = now`.
- `StockMoved.v1` + `BalanceUpdated.v1` emitted in same tx.

### TC-STOCK-002 — Outbound move decrements balance, does NOT touch avg_cost [P1]
**Steps:** Outbound qty 30 from balance above.
**Expected:** `qty_on_hand = 120`; `avg_cost = 86.67` unchanged; cost_amount on the move = current `avg_cost`.

### TC-STOCK-003 — Append-only enforcement [P1]
**Steps:** Attempt UPDATE on stock_move.
**Expected:** App layer has no endpoint; DB CHECK constraint rejects UPDATE.

### TC-STOCK-004 — Idempotent replay on same source doc [P1]
**Steps:** Consumer processes `GrnPosted.v1` twice for the same `(source_doc_type, source_doc_id, line_seq)`.
**Expected:** Second call: no-op; UNIQUE constraint prevents duplicate move.

## Stock count

### TC-STOCK-005 — Start cycle count [P2]
**Stories:** US-STOCK-004
**Steps:** POST /api/v1/stock-counts with item filter.
**Expected:** Snapshot of current `system_qty` per item; status `IN_PROGRESS`.

### TC-STOCK-006 — Close count with variance posts adjustments [P2]
**Stories:** US-STOCK-006
**Steps:** Line counted_qty differs from system_qty. POST close.
**Expected:** ADJUSTMENT `stock_move` row for the difference at current `avg_cost`; status `POSTED`; `StockCountClosed.v1`.

## Adjustment

### TC-STOCK-007 — Adjustment with reason [P1]
**Stories:** US-STOCK-003
**Steps:** POST /api/v1/adjustments `{ item, qty: -5, reason: "Damaged in transit" }`.
**Expected:** ADJUSTMENT move; `notes = reason`; audit row with same `actor_id`.

### TC-STOCK-008 — Above-threshold adjustment requires supervisor [P1]
**Steps:** Adjustment qty exceeds configured threshold without supervisor token.
**Expected:** 422 / 403; with token, succeeds and `authorised_by_user_id` is set.

## Inter-branch transfer

### TC-STOCK-009 — Issue transfer (TRANSFER_OUT + in-transit) [P1]
**Stories:** US-STOCK-007
**Steps:** Branch A issues 30 of item X to Branch B.
**Expected:** A `qty_on_hand -= 30`; B `qty_in_transit += 30`; `TransferIssued.v1`.

### TC-STOCK-010 — Receive transfer (TRANSFER_IN + clear in-transit) [P1]
**Stories:** US-STOCK-008
**Steps:** Branch B receives 30 (matches issued).
**Expected:** B `qty_on_hand += 30`; `qty_in_transit = 0`; `TransferReceived.v1`.

### TC-STOCK-011 — Receive variance (received < issued) [P1]
**Steps:** Branch B receives 28 (2 missing).
**Expected:** Reason required; transfer marked with variance; reporting picks up the loss.

## Negative-stock guard

### TC-STOCK-012 — Block oversell [P1]
**Stories:** US-STOCK-010
**Steps:** `qty_on_hand = 1`. Attempt outbound 3 without override.
**Expected:** 422; `NegativeStockBlocked.v1`; no move written.

### TC-STOCK-013 — Override oversell (supervisor) [P1]
**Steps:** Attempt outbound 3 with `override_by_user_id` set.
**Expected:** Move posted; `qty_on_hand = -2`; `stock_move.authorised_by_user_id` recorded; audit log entries for both block + override.

## Batches + FEFO (Phase 1.1)

### TC-STOCK-014 — GRN creates stock_batch row [P1]
**Stories:** US-STOCK-011
**Steps:** GRN line for batch-tracked item with batch_no + expiry_at.
**Expected:** `stock_batch` row with `source_doc_type = GRN`, `qty_received = qty_on_hand = grn qty`, `status = ACTIVE`. `BatchCreated.v1`.

### TC-STOCK-015 — UNIQUE (branch_id, item_id, batch_no) [P1]
**Steps:** Two GRNs with same batch_no for the same item + branch.
**Expected:** Second 409. (Convention: same supplier batch with multiple receipts should reuse batch_no by INCREMENTING qty_received on the existing row — confirm with business; alternative: reject.)

### TC-STOCK-016 — FEFO consumption [P1]
**Stories:** US-STOCK-012
**Steps:** Three ACTIVE batches: B1 (expiry today+10), B2 (today+5), B3 (today+30). Outbound 10 of item.
**Expected:** B2 consumed first (earliest expiry); cost taken from B2; if 10 > B2.qty_on_hand, spill into next-earliest (B1).

### TC-STOCK-017 — Expired batch flagged by job [P2]
**Stories:** US-STOCK-013
**Steps:** Batch's `expiry_at < today`. Run expiry job.
**Expected:** `status = EXPIRED`; `BatchExpired.v1`. Item's expiring-soon report includes the batch.

### TC-STOCK-018 — Manual EXPIRY_WRITE_OFF [P2]
**Steps:** Manager posts `EXPIRY_WRITE_OFF` move for the expired qty.
**Expected:** Move IN of move_type `EXPIRY_WRITE_OFF` with negative-amount? — actually DIRECTION = OUT (the qty leaves stock); `cost_amount = batch.cost`; batch `qty_on_hand → 0`.

### TC-STOCK-019 — Recall a batch [P2]
**Stories:** US-STOCK-014
**Steps:** POST /api/v1/stock-batches/{id}/recall with reason.
**Expected:** `status = RECALLED`; future consumption rejected; existing on-hand requires write-off; `BatchRecalled.v1`.

## Reservation (Phase 1.1)

### TC-STOCK-020 — Layby reservation reduces availability [P1]
**Steps:** Orders module emits reservation for 5 units. Stock consumes event.
**Expected:** Move with `direction = RESERVED`, qty = 5. `item_branch_balance.qty_reserved += 5`. Available = `qty_on_hand − qty_reserved`.

### TC-STOCK-021 — Cancel reservation releases qty [P1]
**Steps:** Orders cancelled. Stock consumes the release event.
**Expected:** Compensating RESERVED move; `qty_reserved -= 5`.

### TC-STOCK-022 — Collect reservation flips to SALE [P1]
**Steps:** Order collected. Stock consumes the collect event.
**Expected:** RESERVED move reversed; SALE move posted in same tx.

## Internal consumption (Phase 1.1)

### TC-STOCK-023 — Record internal consumption [P1]
**Stories:** US-STOCK-015
**Steps:** POST internal-consumption move with category `CANTEEN`, reason, authoriser.
**Expected:** Move `move_type = INTERNAL_CONSUMPTION`, `consumption_category = CANTEEN`, `authorised_by_user_id` set, `notes = reason`. Without all three → 422.

### TC-STOCK-024 — Category not in enum [P1]
**Steps:** category = "PERSONAL".
**Expected:** 422.

## Section-tagged moves (Phase 1.1)

### TC-STOCK-025 — Production output stamps section_id [P1]
**Steps:** Production output from BAKERY posts moves.
**Expected:** `section_id = BAKERY` on those moves.

### TC-STOCK-026 — Section-to-section transfer [P2]
**Stories:** US-STOCK-016
**Steps:** Move 10 loaves from BAKERY to RETAIL_FLOOR within the same branch.
**Expected:** Two moves in same tx; section_id on both; reporting picks up section-level movement.

## Reporting

### TC-STOCK-027 — Stock card returns ordered ledger [P1]
**Stories:** US-STOCK-002
**Steps:** GET /api/v1/stock-moves?itemId=X&branchId=Y&from=...&to=...
**Expected:** Rows in `at` order; supports pagination.

### TC-STOCK-028 — Balance rebuild matches replayed moves [P1]
**Type:** Edge
**Steps:** Truncate `item_branch_balance`. Run rebuild job. Compare to running sum of moves.
**Expected:** Identical; demonstrates the move ledger is authoritative.

## Edge

### TC-STOCK-029 — Concurrent outbound moves on the same balance [P2]
**Steps:** Two parallel outbound moves on the same `(item, branch)`.
**Expected:** Row-level lock or optimistic-version retry resolves; no lost-update; both moves recorded or one rejected with 409.
