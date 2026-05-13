# POS — test plan

Till sessions, sales, mixed-tender payments, refunds, gift-card tender, FX tender, weighed items, batch FEFO, offline sync. The most-touched module.

## Till + session

### TC-POS-001 — Open till session [P1]
**Stories:** US-POS-002
**Steps:** POST /api/v1/till-sessions with cashier + supervisor + opening_float.
**Expected:** 201; status `OPEN`; `TillSessionOpened.v1`; cash module writes opening-float `cash_entry`.

### TC-POS-002 — One open session per till [P1]
**Steps:** Try to open second session on a till with OPEN one.
**Expected:** 409.

### TC-POS-003 — Open session on closed business day [P1]
**Steps:** Branch business_day CLOSED.
**Expected:** 422 `BUSINESS_DAY_CLOSED`.

### TC-POS-004 — Section required on till [P1]
**Steps:** Create till without section_id.
**Expected:** 422.

## Sale basics

### TC-POS-005 — Scan barcode → add line [P1]
**Stories:** US-POS-003
**Steps:** Scan EAN13 on existing item. POS posts sale line.
**Expected:** Line with `item_id` resolved; unit_price from price_list; tax computed.

### TC-POS-006 — Typeahead lookup [P1]
**Stories:** US-POS-004
**Steps:** Search "milk" via Meilisearch endpoint.
**Expected:** Top matches within 200ms.

### TC-POS-007 — Section stamped on sale [P1]
**Stories:** US-POS-024
**Steps:** Post sale on till in BAKERY section.
**Expected:** `pos_sale.section_id = BAKERY`; lines inherit.

### TC-POS-008 — Cash-only mixed sale [P1]
**Stories:** US-POS-009
**Steps:** 2 lines, single CASH payment, total 5,000 UGX.
**Expected:** 201; `PosSaleClosed.v1`; stock decrement; cash IN entry.

### TC-POS-009 — Mixed tender [P1]
**Steps:** Cart 50,000 = 30,000 cash + 20,000 card.
**Expected:** Sum of payments = total; two `pos_payment` rows; only cash hits cash_entry; card goes through external processor (out of test scope).

### TC-POS-010 — Tender mismatch [P1]
**Steps:** Payments sum ≠ sale total.
**Expected:** 422 `PAYMENT_MISMATCH`.

### TC-POS-011 — Change calculation [P1]
**Steps:** Cart 5,000; tender CASH 10,000.
**Expected:** `pos_sale.change_amount = 5,000`; cash receipt shows.

## Discounts + voids

### TC-POS-012 — Line discount within threshold [P1]
**Stories:** US-POS-005
**Steps:** 5% off (threshold 10%).
**Expected:** Posted.

### TC-POS-013 — Line discount above threshold needs supervisor [P1]
**Steps:** 20% off without supervisor token.
**Expected:** 422; with token, 201.

### TC-POS-014 — Void line in cart [P1]
**Stories:** US-POS-006
**Steps:** Within open session, void a line before tender.
**Expected:** Line removed from cart (client-side); never reaches backend.

### TC-POS-015 — Post-close sale void [P1]
**Steps:** After tender, supervisor voids the entire sale.
**Expected:** `PosSaleVoided.v1`; stock reversed; cash reversed.

## Cash pickup / petty cash

### TC-POS-016 — Cash pickup mid-shift [P1]
**Stories:** US-POS-013
**Steps:** POST /cash-pickups with supervisor token.
**Expected:** Move TILL → SAFE; `CashPickupRecorded.v1`; cash module writes paired entries.

### TC-POS-017 — Cash pickup exceeds drawer cash [P1]
**Steps:** Pickup amount > current cash balance.
**Expected:** 422.

### TC-POS-018 — Petty cash payout [P1]
**Stories:** US-POS-014
**Steps:** POST /petty-cash with category + amount + reason.
**Expected:** `PettyCashPaid.v1`; cash entry OUT on TILL.

## X / Z reports

### TC-POS-019 — X-report mid-shift [P2]
**Stories:** US-POS-015
**Steps:** GET /reports/x-report?tillSessionId=...
**Expected:** Snapshot of cash sales, mixed tenders, voids, expected vs declared. No state change.

### TC-POS-020 — Close till session [P1]
**Stories:** US-POS-016
**Steps:** POST /close with declared cash.
**Expected:** Z-report PDF; `TillSessionClosed.v1`; variance computed; status `CLOSED`.

### TC-POS-021 — Variance above threshold [P1]
**Steps:** Variance > threshold without supervisor PIN.
**Expected:** 422; with PIN, close succeeds with supervisor stamp.

## Offline sync

### TC-POS-022 — Sell offline (5 sales) [P1]
**Stories:** US-POS-017
**Steps:** Disable network. Cashier rings 5 sales. Each gets client_op_id + TILL-namespaced receipt number.
**Expected:** All print locally; nothing posted server-side yet.

### TC-POS-023 — Sync queued ops on reconnect [P1]
**Stories:** US-POS-018
**Steps:** POST /sync/push with 5 ops in order.
**Expected:** 5 ackedClientOpIds; idempotent on replay.

### TC-POS-024 — Out-of-order op [P2]
**Steps:** Push op with `sale_at` < previously-acked op on same session.
**Expected:** 409 conflict; client re-orders + retries.

### TC-POS-025 — Op for closed session [P2]
**Steps:** Push op for a session that's already CLOSED on server.
**Expected:** 422.

## Phase 1.1 — Refund at till

### TC-POS-026 — Same-day refund within threshold [P1]
**Stories:** US-POS-019
**Steps:** Refund a same-day sale, receipt scanned, total below threshold.
**Expected:** 201 new sale with `kind = REFUND`; cash OUT entry; stock IN move.

### TC-POS-027 — Same-day refund above threshold requires supervisor [P1]
**Stories:** US-POS-020
**Steps:** Refund total above threshold, no supervisor token.
**Expected:** 403; with token, 201.

### TC-POS-028 — Refund without receipt [P1]
**Steps:** Same-day sale, no receipt scanned, cashier alone.
**Expected:** 403; manager PIN required.

### TC-POS-029 — Refund of past business day blocked [P1]
**Steps:** Refund yesterday's sale.
**Expected:** 422 `BUSINESS_DAY_CLOSED_FOR_TILL_REFUND`; back-office customer_return required.

### TC-POS-030 — Negative payment for refund [P1]
**Steps:** Refund body: `pos_payment { method: CASH, amount: -7000 }`.
**Expected:** Cash entry OUT on TILL; ref_type `PosRefund`.

## Phase 1.1 — FX tender

### TC-POS-031 — Tender in functional currency [P1]
**Steps:** UGX-only payment.
**Expected:** tender_currency = UGX, fx_rate_snapshot = 1, amount = tender_amount.

### TC-POS-032 — Tender in foreign currency [P1]
**Stories:** US-POS-021
**Steps:** Pay USD 5 for 19,000 UGX cart with current rate 3,800.
**Expected:** pos_payment row: tender_currency=USD, tender_amount=5, fx_rate_snapshot=3800, amount=19000.

### TC-POS-033 — Foreign currency not accepted on this till [P1]
**Steps:** Till does not have USD in `till_currency`. Pay USD.
**Expected:** 422 `CURRENCY_NOT_ACCEPTED_BY_TILL`.

### TC-POS-034 — No FX rate quoted for tender date [P1]
**Steps:** Pay USD when no UGX→USD rate has been quoted.
**Expected:** 422 `NO_FX_RATE_AVAILABLE`.

### TC-POS-035 — fx_rate_snapshot mismatch with tender × rate [P1]
**Steps:** Submit fx_rate_snapshot that doesn't match the lookup.
**Expected:** 422 — server overrides with current rate (or 400 demanding consistency).

## Phase 1.1 — Gift card tender

### TC-POS-036 — Pay with gift card [P1]
**Stories:** US-POS-022
**Steps:** Add gift card method, amount up to card balance.
**Expected:** `gift_card_txn` REDEEM row; no cash_entry; balance decremented.

### TC-POS-037 — Gift card insufficient balance [P1]
**Steps:** Redeem amount > balance.
**Expected:** 422; partial redemption allowed only if remaining cart pays other tenders.

### TC-POS-038 — Refund a gift-card-tendered sale [P2]
**Stories:** US-GC-004
**Steps:** Refund same-day sale that used GC.
**Expected:** Card balance credited back; `gift_card_txn REFUND`.

## Phase 1.1 — Weighed items + batches

### TC-POS-039 — Embedded-weight barcode at scan [P1]
**Stories:** US-CAT-016
**Steps:** Scan 2-prefix EAN-13 with PLU + weight.
**Expected:** Client parses, posts qty in KG; backend trusts; line_total = unit_price × decoded weight.

### TC-POS-040 — FEFO batch consumption [P1]
**Stories:** US-STOCK-012
**Steps:** Item with 3 ACTIVE batches; sell qty triggering split.
**Expected:** Client resolves earliest-expiry batch; backend validates; `pos_sale_line.batch_id` populated.

### TC-POS-041 — No active batch for batch-tracked item [P1]
**Steps:** Sell batch-tracked item with no ACTIVE batches.
**Expected:** 422 `NO_BATCH_AVAILABLE`.

## Phase 1.1 — Staff purchase

### TC-POS-042 — Apply staff price tier on badge scan [P2]
**Stories:** US-POS-023
**Steps:** Cashier scans employee badge; cart re-priced to staff_price_list.
**Expected:** Each line uses staff price; `pos_sale.is_staff_purchase = true`; stock_move move_type `STAFF_PURCHASE`.

## Layby collection at POS

### TC-POS-043 — Collect a paid-up layby [P1]
**Stories:** US-ORD-003
**Steps:** Cashier scans order number; POS creates pos_sale from order lines; reservation flips.
**Expected:** Sale posted; receipt references order_no.

## Negative + edge

### TC-POS-044 — Idempotent sale post [P1]
**Steps:** Push same client_op_id twice.
**Expected:** Same response; one server row.

### TC-POS-045 — Concurrent void on same sale [P2]
**Steps:** Two clients try to void at once.
**Expected:** One wins; other 409.

### TC-POS-046 — Sale with item not in client's catalog snapshot [P2]
**Steps:** Catalog has new item not yet synced to client. Cashier scans.
**Expected:** Client tries server lookup; if offline, line entry blocked.
