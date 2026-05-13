# Cash — test plan

Cash ledger, supplier payments, multi-currency cash_book, refund cash side, day-end banking.

## cash_entry

### TC-CASH-001 — Append-only enforcement [P1]
**Steps:** Attempt UPDATE on cash_entry.
**Expected:** App has no endpoint; DB CHECK or trigger rejects.

### TC-CASH-002 — Idempotent on (source_doc_type, source_doc_id, direction) [P1]
**Steps:** Same event consumed twice.
**Expected:** Second is no-op; UNIQUE constraint prevents double.

## Opening float

### TC-CASH-003 — Opening float on till session open [P1]
**Steps:** POS opens till with float 100,000 UGX.
**Expected:** Cash module consumes `TillSessionOpened.v1`; writes IN entry on TILL (and offsetting OUT on CASH_BOX if floats are tracked back).

## Pickup / petty cash

### TC-CASH-004 — Cash pickup pairs OUT-TILL + IN-CASH_BOX [P1]
**Steps:** POS emits `CashPickupRecorded.v1` for 50,000.
**Expected:** Two cash_entry rows in same tx; same `ref_id`, `ref_type = CashPickup`.

### TC-CASH-005 — Petty cash payout single OUT [P1]
**Steps:** POS emits `PettyCashPaid.v1` for 20,000 category FUEL.
**Expected:** Single OUT on TILL; `gl_category = PETTY`; no paired IN.

## Close till

### TC-CASH-006 — Close till with no variance [P1]
**Steps:** Declared = expected.
**Expected:** No variance entry.

### TC-CASH-007 — Close till with variance [P1]
**Steps:** Declared > expected by 5,000.
**Expected:** Variance entry; ref_type `CashVariance`; `gl_category = VARIANCE`.

### TC-CASH-008 — Per-currency variance (Phase 1.1) [P1]
**Stories:** US-DAY-006
**Steps:** Till took UGX + USD; declared per currency; UGX matches, USD short.
**Expected:** Variance entries split per currency in cash_book.

## Supplier payments

### TC-CASH-009 — Record supplier payment [P1]
**Stories:** US-PROC-007
**Steps:** POST /supplier-payments method=BANK_TRANSFER, allocate to 1 invoice.
**Expected:** `SupplierPaymentRecorded.v1` + `SupplierPaymentAllocated.v1`; cash entry OUT on BANK.

### TC-CASH-010 — Partial allocation across multiple invoices [P1]
**Steps:** One payment, three allocations.
**Expected:** Sum of allocations = payment amount; each invoice updated.

### TC-CASH-011 — Over-allocation rejected [P1]
**Steps:** Allocations > payment.amount.
**Expected:** 422.

## Banking

### TC-CASH-012 — End-of-day banking [P1]
**Stories:** US-DAY-002
**Steps:** Day module emits `BusinessDayClosed.v1`; manager records deposit.
**Expected:** Paired entries OUT CASH_BOX + IN BANK; ref_type `BankDeposit`.

## Supervisor adjustment

### TC-CASH-013 — Adjust with reason [P1]
**Steps:** POST /cash-adjustments with reason + supervisor token.
**Expected:** Single entry; audit captured.

### TC-CASH-014 — Adjustment without supervisor [P1]
**Steps:** Without permission `CASH.ADJUST`.
**Expected:** 403.

## cash_book projection

### TC-CASH-015 — Balance equation invariant [P1]
**Steps:** Read cash_book row.
**Expected:** `closing_amount = opening_amount + in_amount − out_amount`.

### TC-CASH-016 — Rebuild from cash_entry [P1]
**Type:** Edge
**Steps:** Drop a cash_book row. Run rebuild job for that location+date.
**Expected:** Row recomputed from sum of cash_entry; matches authoritative ledger.

## Phase 1.1 — multi-currency book

### TC-CASH-017 — Composite PK extension [P1]
**Steps:** Same branch + account + business_date, two currencies.
**Expected:** Two cash_book rows differing only by currency_code; both maintained.

### TC-CASH-018 — Foreign-currency entry stores tender + functional [P1]
**Steps:** POS USD tender 5 at rate 3,800.
**Expected:** cash_entry: currency_code = USD, amount (functional) = 19,000, fx_rate_snapshot = 3,800. tender_amount stored either on entry or on the originating pos_payment.

### TC-CASH-019 — Functional currency entries have fx_rate_snapshot = 1 [P1]
**Steps:** UGX-only entry.
**Expected:** fx_rate_snapshot = 1; amount stored once.

## Phase 1.1 — refund cash side

### TC-CASH-020 — Refund OUT on TILL [P1]
**Steps:** POS emits `PosSaleRefunded.v1` with method CASH and amount 7,000.
**Expected:** OUT entry on TILL; `ref_type = PosRefund`; `gl_category = CASH_REFUND`.

### TC-CASH-021 — Refund of mixed-tender sale [P2]
**Steps:** Original sale paid 5,000 cash + 2,000 card. Refund.
**Expected:** OUT cash entry for 5,000 only; card refund out-of-scope for cash module.

## Phase 1.1 — orders cash side

### TC-CASH-022 — Layby deposit IN [P1]
**Steps:** Orders emits `OrderDepositPaid.v1` for 75,000 cash.
**Expected:** IN entry on TILL; `ref_type = CustomerOrder`.

### TC-CASH-023 — Layby cancellation refund [P2]
**Steps:** Orders emits `OrderCancelled.v1` with refund.
**Expected:** OUT entry per refund policy; `ref_type = CustomerOrder`.

## Phase 1.1 — gift card cash side

### TC-CASH-024 — Gift card issue IN [P1]
**Steps:** GC module emits `GiftCardIssued.v1` for 100,000 cash.
**Expected:** Single cash entry IN on TILL; `ref_type = GiftCardIssue`; `gl_category = GIFT_CARD_ISSUE_PROCEEDS`.

### TC-CASH-025 — Gift card REDEEM does NOT create cash_entry [P1]
**Steps:** GC redeem event consumed.
**Expected:** NO cash_entry posted (liability ledger). Cash module ignores `GiftCardRedeemed.v1`.

## Day-gating

### TC-CASH-026 — Cash entries blocked on closed day [P1]
**Steps:** Branch business_day CLOSED. Direct POST to /cash-adjustments.
**Expected:** 422 unless override active.

## Edge

### TC-CASH-027 — Concurrent updates to same cash_book row [P2]
**Steps:** Two consumers post cash_entry for same (branch, account, currency, date).
**Expected:** Both update cash_book atomically (row-level lock or upsert); no lost-update.

### TC-CASH-028 — gl_category enum coverage [P1]
**Steps:** Each entry has gl_category in the allowed enum.
**Expected:** Values: CASH, BANK, PETTY, VARIANCE, SUPPLIER_SETTLEMENT, RECEIPT, TILL_FLOAT, CASH_REFUND, GIFT_CARD_ISSUE_PROCEEDS, FX_VARIANCE, ORDER_DEPOSIT (per Phase 1.1).
