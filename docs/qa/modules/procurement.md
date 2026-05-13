# Procurement — test plan

Module tests for the inbound goods lifecycle: quotation → LPO → GRN → supplier invoice → supplier payment, plus vendor return / credit note.

## Quotation

### TC-PROC-001 — Raise quotation [P2]
**Stories:** US-PROC-010
**Steps:** POST /api/v1/purchase-quotations with supplier + lines.
**Expected:** 201; status `DRAFT`; `PurchaseQuotationRaised.v1`.

### TC-PROC-002 — Send to supplier [P2]
**Steps:** POST /api/v1/purchase-quotations/{id}/send.
**Expected:** status `SENT`; PDF email triggered (event).

### TC-PROC-003 — Convert quotation to LPO [P2]
**Steps:** POST /api/v1/purchase-quotations/{id}/convert-to-lpo.
**Expected:** New `lpo_order` row with lines copied; quotation `status = CONVERTED`.

## LPO

### TC-PROC-004 — Raise LPO [P1]
**Stories:** US-PROC-002
**Steps:** POST /api/v1/lpo-orders with supplier + items + qty + unit_cost.
**Expected:** 201; status `DRAFT`; per-branch number `LPO-BR1-...`.

### TC-PROC-005 — Submit + approve LPO [P1]
**Stories:** US-PROC-003
**Steps:** POST /submit then /approve.
**Expected:** `PENDING_APPROVAL` → `APPROVED`; `approved_by` + `approved_at`; `LpoOrderApproved.v1`.

### TC-PROC-006 — Auto-approval below threshold [P1]
**Steps:** LPO with `total_amount` below configured threshold. POST /submit.
**Expected:** `PENDING_APPROVAL` skipped; auto `APPROVED`.

### TC-PROC-007 — Reject LPO with reason [P1]
**Steps:** POST /reject `{ reason: "Supplier price too high" }`.
**Expected:** Returns to `DRAFT`; reason audited.

### TC-PROC-008 — Cancel LPO [P1]
**Stories:** US-PROC-012
**Steps:** POST /cancel.
**Expected:** `CANCELLED`; subsequent GRN against this LPO rejected.

### TC-PROC-009 — Edit posted LPO blocked [P1]
**Steps:** PATCH on `APPROVED` LPO.
**Expected:** 422; cancel + new LPO required.

## GRN

### TC-PROC-010 — GRN against LPO (full) [P1]
**Stories:** US-PROC-004
**Steps:** Post GRN with line qty = ordered qty.
**Expected:** GRN `POSTED`; LPO line `received_qty` updated; LPO status `RECEIVED`; `GrnPosted.v1`; stock module decrements / increments via consumer.

### TC-PROC-011 — GRN against LPO (partial) [P1]
**Steps:** GRN qty < ordered qty.
**Expected:** LPO `PARTIALLY_RECEIVED`; remaining qty receivable in subsequent GRNs.

### TC-PROC-012 — Over-receipt rejected [P1]
**Steps:** GRN qty > ordered − received.
**Expected:** 422.

### TC-PROC-013 — Direct GRN (no LPO) [P1]
**Stories:** US-PROC-005
**Steps:** Post GRN with `lpo_order_id = null` using `GRN.DIRECT` privilege.
**Expected:** 201; without privilege → 403.

### TC-PROC-014 — Cancel posted GRN (compensating) [P1]
**Stories:** US-PROC-012
**Steps:** POST /cancel with reason. Privilege `GRN.CANCEL` required.
**Expected:** Compensating events emitted (stock + debt reverse); original GRN row immutable.

### TC-PROC-015 — GRN with batch_no + expiry_at (Phase 1.1) [P1]
**Stories:** US-STOCK-011
**Steps:** Item has `tracks_batches = true`. POST GRN with `batch_no` + `expiry_at` on each line.
**Expected:** `stock_batch` row created via stock consumer; GRN line carries the values.

### TC-PROC-016 — Batch-tracked item without batch_no [P1]
**Steps:** GRN line for batch-tracked item missing `batch_no`.
**Expected:** 422 `BATCH_NO_REQUIRED`.

## Supplier invoice match

### TC-PROC-017 — Match invoice to single GRN [P1]
**Stories:** US-PROC-006
**Steps:** POST supplier_invoice with `supplier_invoice_grn[]` linking one GRN.
**Expected:** Invoice `MATCHED`; tolerance check against GRN total.

### TC-PROC-018 — Match invoice to multiple GRNs [P1]
**Steps:** One supplier invoice covering 3 GRNs.
**Expected:** `supplier_invoice_grn` has 3 rows; sum of allocated amounts = invoice total.

### TC-PROC-019 — Tolerance warning [P2]
**Steps:** invoice.total_amount differs from sum(GRN totals) by 1% (above tolerance).
**Expected:** Warning surfaced; user accepts → invoice posted with variance note.

### TC-PROC-020 — Same GRN matched twice [P1]
**Steps:** Two supplier invoices try to claim the same GRN.
**Expected:** 409. UNIQUE (supplier_invoice_id, grn_id).

### TC-PROC-021 — Post invoice opens supplier debt [P1]
**Steps:** POST /api/v1/supplier-invoices/{id}/post.
**Expected:** Invoice `POSTED`; supplier debt row with `due_date = posted_at + supplier.payment_terms_days`.

## Payment

### TC-PROC-022 — Pay supplier (cash) [P1]
**Stories:** US-PROC-007
**Steps:** POST /api/v1/supplier-payments method = CASH, allocate to invoice.
**Expected:** `SupplierPaymentRecorded.v1` + `SupplierPaymentAllocated.v1`; cash module writes `cash_entry` OUT; invoice `paid_amount` updated.

### TC-PROC-023 — Pay supplier (bank) [P1]
**Steps:** method = BANK_TRANSFER.
**Expected:** `cash_entry` OUT on BANK account.

### TC-PROC-024 — Partial payment [P1]
**Steps:** Payment amount < invoice balance.
**Expected:** Invoice `PARTIALLY_PAID`; remaining `due_amount = total − paid`.

### TC-PROC-025 — Over-allocation rejected [P1]
**Steps:** Allocate amount > invoice balance.
**Expected:** 422.

## Vendor return

### TC-PROC-026 — Vendor return [P2]
**Stories:** US-PROC-008
**Steps:** POST /api/v1/vendor-returns referencing original GRN.
**Expected:** Reverse stock event; supplier debt reduced; `vendor_return.status = POSTED`.

### TC-PROC-027 — Vendor credit note [P2]
**Stories:** US-PROC-009
**Steps:** POST credit note against return; allocate to open invoices.
**Expected:** `VendorCreditIssued.v1`; invoice `paid_amount` reduced; supplier ledger updated.

## Day-gating

### TC-PROC-028 — Post on closed business day blocked [P1]
**Steps:** Branch's business_day is `CLOSED`. POST /supplier-invoices/{id}/post.
**Expected:** 422 `BUSINESS_DAY_CLOSED`.

### TC-PROC-029 — Post on overridden closed day [P2]
**Steps:** Supervisor opens an override window. Post invoice with backdate.
**Expected:** Succeeds; `business_day_override` row tagged with the supplier_invoice id.

## Per-branch sequencing

### TC-PROC-030 — Document numbers per branch [P1]
**Steps:** Create 3 LPOs in branch BR-1 and 3 in BR-2.
**Expected:** `LPO-BR1-000001..3` and `LPO-BR2-000001..3` independently.

## Edge

### TC-PROC-031 — Concurrent GRN against same LPO [P2]
**Steps:** Two parallel GRNs both consuming remaining ordered qty.
**Expected:** Exactly one wins; other gets 422 (over-receipt).

### TC-PROC-032 — Idempotency on GRN post [P1]
**Steps:** Retry POST /post with same Idempotency-Key.
**Expected:** Same response; no duplicate stock_move.
