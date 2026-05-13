# Orders — test plan

Layby + pre-order lifecycle. Stock reservation, instalment payments, production-tied collection, expiry.

## Create

### TC-ORD-001 — Create layby [P1]
**Stories:** US-ORD-001
**Steps:** POST /api/v1/orders `{ type: LAYBY, customer_id, lines, deposit_required_amount, reserved_until }`.
**Expected:** 201; status DRAFT or DEPOSIT_PAID (if deposit captured atomically); per-branch number ORD-BR1-...

### TC-ORD-002 — Create pre-order [P1]
**Stories:** US-ORD-004
**Steps:** POST type = PRE_ORDER with section_id (BAKERY).
**Expected:** 201; section captured.

### TC-ORD-003 — Layby without deposit_required_amount [P1]
**Steps:** Submit without deposit_required.
**Expected:** 422 (configurable default applied if blank).

### TC-ORD-004 — Items below available stock at reserve time [P1]
**Steps:** Order requires 5 units; available (qty_on_hand − qty_reserved) = 3.
**Expected:** 422 `INSUFFICIENT_AVAILABILITY`.

## Reservation

### TC-ORD-005 — Reserve flips status to RESERVED [P1]
**Steps:** POST /orders/{id}/reserve.
**Expected:** Stock module consumes event; writes RESERVED move; `item_branch_balance.qty_reserved += line qty`.

### TC-ORD-006 — Pre-order skips stock reservation [P1]
**Steps:** Pre-order is made-to-order; reserve called.
**Expected:** No RESERVED stock_move (production produces fresh); pre-order moves to RESERVED status without stock side-effect.

## Payments

### TC-ORD-007 — Pay deposit [P1]
**Steps:** POST /orders/{id}/payments `{ amount, method: CASH }`.
**Expected:** customer_order_payment row; cash module writes IN entry; if amount ≥ deposit_required → status DEPOSIT_PAID; `OrderDepositPaid.v1`.

### TC-ORD-008 — Pay instalment [P1]
**Stories:** US-ORD-002
**Steps:** Subsequent payments.
**Expected:** PARTIALLY_PAID; balance_due decreases.

### TC-ORD-009 — Pay final balance [P1]
**Steps:** Payment makes balance_due = 0.
**Expected:** Status READY (layby) or stays as-is awaiting production output (pre-order).

### TC-ORD-010 — Overpayment rejected [P1]
**Steps:** Total payments > total_amount.
**Expected:** 422.

### TC-ORD-011 — Payment via gift card [P2]
**Steps:** Pay deposit with GIFT_CARD method.
**Expected:** customer_order_payment.method = GIFT_CARD; gift_card_txn REDEEM row; no cash entry.

## Pre-order production trigger

### TC-ORD-012 — Deposit paid triggers production batch [P1]
**Steps:** Pre-order moves to DEPOSIT_PAID; production module consumes `OrderDepositPaid.v1`.
**Expected:** production_batch in PLANNED state references the order via metadata.

### TC-ORD-013 — Production output advances order to READY [P1]
**Steps:** Bakery posts output for the order's BOM.
**Expected:** Orders module consumes `ProductionOutputPosted.v1` (matched on production_batch metadata); order status = READY.

## Collection

### TC-ORD-014 — Collect layby [P1]
**Stories:** US-ORD-003
**Steps:** Customer at till; POS calls /orders/{id}/collect.
**Expected:** Stock RESERVED moves reversed; SALE moves posted; pos_sale created referencing order; status COLLECTED; `OrderCollected.v1`.

### TC-ORD-015 — Cannot collect with balance due [P1]
**Steps:** Try to collect with balance_due > 0.
**Expected:** 422 — must pay first (or at-till final payment captured in same flow).

## Cancel + expire

### TC-ORD-016 — Cancel order [P1]
**Stories:** US-ORD-005
**Steps:** POST /orders/{id}/cancel.
**Expected:** Reservation released; deposit refunded per company policy; `OrderCancelled.v1`.

### TC-ORD-017 — Cancel refund policy [P2]
**Steps:** Company config: forfeit if cancelled past 7 days from create.
**Expected:** Refund amount per policy; cash module receives refund instruction.

### TC-ORD-018 — Expire abandoned order [P1]
**Stories:** US-ORD-006
**Steps:** Scheduled job runs after `reserved_until < now` with no recent payment.
**Expected:** Status EXPIRED; reservation released; deposit per policy (default: forfeit); `OrderExpired.v1`.

### TC-ORD-019 — Notify before expiry [P2]
**Stories:** US-ORD-008
**Steps:** Scheduled job runs 24h before `reserved_until`.
**Expected:** SMS / email notification to customer.

## Lookup + receipts

### TC-ORD-020 — List customer's open orders [P2]
**Stories:** US-ORD-007
**Steps:** GET /orders?customerId=... &status=OPEN.
**Expected:** Active orders (not COLLECTED / CANCELLED / EXPIRED).

### TC-ORD-021 — Order receipt [P2]
**Stories:** US-ORD-009
**Steps:** GET /orders/{id}/receipt — PDF / print / SMS.
**Expected:** Document with order details, deposit, balance, expected collection date.

## Edge

### TC-ORD-022 — Concurrent reservation on low stock [P2]
**Steps:** 2 orders try to reserve last unit.
**Expected:** First wins; second 422.

### TC-ORD-023 — Edit lines after RESERVED blocked [P1]
**Steps:** PATCH lines on RESERVED order.
**Expected:** 422 — lines editable only in DRAFT.

### TC-ORD-024 — Idempotent payment post [P1]
**Steps:** Replay payment with same Idempotency-Key.
**Expected:** Same response; one customer_order_payment row; one cash_entry.
