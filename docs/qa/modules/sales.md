# Sales — test plan

Back-office sales: quotation → invoice → receipt → allocation, customer returns, credit notes, packing lists.

## Quotation

### TC-SALES-001 — Raise quotation [P2]
**Stories:** US-SALES-003
**Steps:** POST /api/v1/sales-quotations.
**Expected:** 201; status `DRAFT`; per-branch number `QT-BR1-...`; `price_list_id` snapshot.

### TC-SALES-002 — Convert quotation to invoice [P2]
**Stories:** US-SALES-004
**Steps:** POST /convert.
**Expected:** New `sales_invoice` row with lines copied; quotation `CONVERTED`; back-link via `converted_to_invoice_id`.

## Invoice

### TC-SALES-003 — Raise CASH invoice [P1]
**Stories:** US-SALES-005
**Steps:** POST invoice with `payment_terms = CASH`.
**Expected:** Posted; cash receipt opened in same flow or separately; stock decrements via consumer.

### TC-SALES-004 — Raise CREDIT invoice [P1]
**Steps:** `payment_terms = CREDIT`.
**Expected:** Customer debt opens at full amount; `paid_amount = 0`; `status = POSTED`.

### TC-SALES-005 — Credit limit check passes [P1]
**Steps:** Customer's `open_debt + invoice.total ≤ credit_limit`.
**Expected:** 201.

### TC-SALES-006 — Credit limit check blocks [P1]
**Steps:** Combined exceeds limit. Caller lacks `SALES_INVOICE.OVERRIDE_CREDIT`.
**Expected:** 422 `CREDIT_LIMIT_EXCEEDED`.

### TC-SALES-007 — Override credit limit [P1]
**Steps:** With `SALES_INVOICE.OVERRIDE_CREDIT`.
**Expected:** 201; supervisor identity audited.

### TC-SALES-008 — Line discount within threshold [P1]
**Stories:** US-SALES-006
**Steps:** 5% line discount (below threshold).
**Expected:** Posted with discount.

### TC-SALES-009 — Line discount above threshold requires supervisor [P1]
**Steps:** 20% line discount, threshold 10%.
**Expected:** 422 / 403; with supervisor token, 201.

### TC-SALES-010 — Discount below min_sell_price [P1]
**Steps:** Discount pushes unit_price below `item.min_sell_price`.
**Expected:** 422 unless `MIN_PRICE_OVERRIDE` permission.

### TC-SALES-011 — Post invoice emits event [P1]
**Steps:** POST /post.
**Expected:** `SalesInvoicePosted.v1`; stock consumer writes OUT moves; cash consumer NO-OP for credit; cost_amount snapped for margin report.

### TC-SALES-012 — Void posted invoice (same day) [P1]
**Stories:** US-SALES-007
**Steps:** Manager POSTs /void with reason on the same business day.
**Expected:** `SalesInvoiceVoided.v1`; stock reversed; debt reversed; status `VOIDED`; copy_number bumped if reprint had occurred.

### TC-SALES-013 — Void on past business day blocked [P1]
**Steps:** Try to void yesterday's invoice.
**Expected:** 422 `BUSINESS_DAY_CLOSED`.

## Receipts

### TC-SALES-014 — Capture cash receipt [P1]
**Stories:** US-SALES-008
**Steps:** POST /api/v1/sales-receipts with method = CASH, amount.
**Expected:** 201; `SalesReceiptCaptured.v1`; cash consumer writes IN entry.

### TC-SALES-015 — Allocate receipt to single invoice [P1]
**Stories:** US-SALES-009
**Steps:** POST /receipt-allocations.
**Expected:** invoice.paid_amount += amount; status updates to PARTIALLY_PAID or PAID; `ReceiptAllocated.v1`.

### TC-SALES-016 — Allocate receipt across multiple invoices [P1]
**Steps:** Split allocation across 3 open invoices.
**Expected:** 3 `receipt_allocation` rows; sum = receipt total; remainder held as customer credit.

### TC-SALES-017 — Over-allocation rejected [P1]
**Steps:** Total allocated > receipt total.
**Expected:** 422.

## Return + credit note

### TC-SALES-018 — Customer return (restock = true) [P2]
**Stories:** US-SALES-010
**Steps:** POST /customer-returns linked to original invoice; line restock = true.
**Expected:** RETURN_IN stock move; quantity returns to balance; `CustomerReturnPosted.v1`.

### TC-SALES-019 — Customer return (restock = false) [P2]
**Steps:** Line restock = false (damaged).
**Expected:** DAMAGE move (no return to sellable stock).

### TC-SALES-020 — Issue customer credit note [P2]
**Stories:** US-SALES-011
**Steps:** POST /customer-credit-notes referencing the return.
**Expected:** Credit note opens with `allocated_amount = 0`.

### TC-SALES-021 — Allocate credit note to open invoice [P2]
**Steps:** POST /credit-notes/{id}/allocate.
**Expected:** Invoice paid_amount += allocated; credit note allocated_amount += same.

## Packing list

### TC-SALES-022 — Build packing list [P2]
**Stories:** US-SALES-012
**Steps:** POST /packing-lists against posted invoice with partial line quantities.
**Expected:** Status DRAFT; cumulative pack qty ≤ invoice line qty.

### TC-SALES-023 — Cumulative pack qty exceeds invoice line [P2]
**Steps:** Two packing lists, sum exceeds invoice line qty.
**Expected:** 422.

### TC-SALES-024 — Dispatch packing list [P2]
**Steps:** POST /packing-lists/{id}/dispatch.
**Expected:** Status DISPATCHED; `PackingListDispatched.v1`.

## Reprint

### TC-SALES-025 — Reprint with copy number [P1]
**Stories:** US-SALES-013
**Steps:** Reprint invoice twice.
**Expected:** copy_number 2 then 3 (original = 1); audit log row per reprint with `actor_id`, `at`.

## Edge

### TC-SALES-026 — Optimistic lock on invoice edit [P2]
**Steps:** Two clients fetch DRAFT invoice v=3; both PATCH.
**Expected:** First wins; second 409.

### TC-SALES-027 — Idempotency on post [P1]
**Steps:** Replay /post with same Idempotency-Key.
**Expected:** Same response; no duplicate events.

### TC-SALES-028 — Sales agent commission snapshot [P2]
**Stories:** (commission rule TBD per DM §16.8)
**Steps:** Customer has `default_sales_agent_id`. Post invoice.
**Expected:** sales_invoice carries `sales_agent_id` snapshot for commission calc later.
