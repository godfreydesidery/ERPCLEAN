# End-to-end scenarios

Cross-module business flows. Every test here touches at least two modules and asserts the event-driven side effects (outbox row + consumer effects) are correct.

For test format and conventions see [README.md](README.md).

---

## E1. First-run setup → first sale

### TC-E2E-001 — Bootstrap an empty deployment to first POS sale [P1]
**Modules:** admin → party → catalog → auth → day → pos → stock → cash
**Stories:** US-COMP-001, US-IAM-001, US-CAT-001, US-DAY-001, US-POS-002, US-POS-003, US-POS-009

**Preconditions:**
- Empty database; only Flyway baseline + sequences are applied.
- An OS-level admin can reach the API.

**Steps:**
1. `POST /api/v1/setup/first-run` — body: organisation name, company code + name + currency (`UGX`), first branch (code, name, timezone), admin user (username `admin`, password).
2. Login as `admin` via `POST /api/v1/auth/login`. Receive JWT.
3. Confirm the default section "Main Floor" was created by listing `GET /api/v1/branches/{id}/sections`.
4. `POST /api/v1/currencies` enable `USD`.
5. `POST /api/v1/fx-rates` quote `UGX → USD` at the day's rate.
6. `POST /api/v1/items` — create item `MILK-1L` (`type = SELLABLE`, `is_weighed = false`, `tracks_batches = false`), price 3,500 UGX.
7. `POST /api/v1/parties` create a walk-in customer auto-created on company create — verify it exists.
8. `POST /api/v1/business-days` open the business day for the branch.
9. `POST /api/v1/tills` create till `TILL-1` in section "Main Floor".
10. `POST /api/v1/till-sessions` open with float 100,000 UGX.
11. `POST /api/v1/pos-sales` — single line `MILK-1L × 2`, single cash tender 7,000 UGX.

**Expected:**
- Step 1: 201; events `OrganisationCreated.v1`, `CompanyCreated.v1`, `BranchCreated.v1`, `SectionCreated.v1`, `PartyCreated.v1` (walk-in) in outbox.
- Step 2: 200 with access token, `user.defaultCompanyId` and `defaultBranchId` populated.
- Step 6: `ItemCreated.v1` emitted; Meilisearch index updated within 1s.
- Step 10: `TillSessionOpened.v1`; opening-float `cash_entry` written by `cash` consumer.
- Step 11: `PosSaleClosed.v1`; `stock_move` row decrements MILK-1L on-hand by 2; `cash_entry` IN on TILL for 7,000 UGX.
- All idempotency keys honoured; replaying step 11 returns the same `pos_sale.id`.

**Negative variants:**
- Step 11 with sale total ≠ sum of tenders → 422.
- Step 10 with another OPEN session on the same till → 409.

---

## E2. Procurement → stock → margin

### TC-E2E-002 — LPO → GRN → supplier invoice → supplier payment [P1]
**Modules:** procurement → stock → cash → party
**Stories:** US-PROC-002, US-PROC-003, US-PROC-004, US-PROC-006, US-PROC-007 (PRD §6.2)

**Preconditions:**
- Bootstrapped deployment.
- Supplier `ACME-DAIRY` exists.
- Items `MILK-1L` and `YOGURT-500ML` exist.
- Business day OPEN.

**Steps:**
1. `POST /api/v1/lpo-orders` — supplier `ACME-DAIRY`, lines `MILK-1L × 100 @ 2,500`, `YOGURT-500ML × 50 @ 1,800`.
2. `POST /api/v1/lpo-orders/{id}/submit` then `/approve` (manager).
3. `POST /api/v1/grns` referencing LPO, full receipt.
4. `POST /api/v1/grns/{id}/post`.
5. `POST /api/v1/supplier-invoices` matching the GRN, total `(100×2,500) + (50×1,800) = 340,000`.
6. `POST /api/v1/supplier-invoices/{id}/post`.
7. `POST /api/v1/supplier-payments` `total = 340,000`, method `BANK_TRANSFER`, allocate fully to the invoice.

**Expected:**
- LPO transitions `DRAFT → SUBMITTED → APPROVED`.
- After step 4: `GrnPosted.v1` emitted; stock module writes `stock_move` rows (`+100` MILK-1L, `+50` YOGURT-500ML); `item_branch_balance.avg_cost` recomputed via moving average; supplier `debt_entry` row opened.
- After step 6: `SupplierInvoiceMatched.v1`; `supplier_invoice_grn` row links invoice to GRN; first debt entry closes, invoice-bounded debt opens with `due_date`.
- After step 7: `SupplierPaymentRecorded.v1` + `SupplierPaymentAllocated.v1`; `cash_entry` OUT on BANK; supplier_invoice `status = PAID`.

**Negative variants:**
- GRN line qty > LPO line ordered_qty → 422.
- GRN against a CANCELLED LPO → 422.
- Supplier_invoice matched twice to the same GRN → 409.

---

## E3. Credit sale → receipt → allocation

### TC-E2E-003 — Customer credit invoice → partial receipt → allocation [P1]
**Modules:** sales → stock → cash → party → day
**Stories:** US-SALES-005, US-SALES-008, US-SALES-009 (PRD §6.5)

**Preconditions:**
- Customer `KAMPALA-RETAIL` exists with `credit_limit = 5,000,000`.
- Item `MILK-1L` has `qty_on_hand ≥ 200`, `qty_reserved = 0`.
- Business day OPEN.

**Steps:**
1. `POST /api/v1/sales-invoices` — customer `KAMPALA-RETAIL`, lines `MILK-1L × 100 @ 3,500`, payment_terms `CREDIT`.
2. `POST /api/v1/sales-invoices/{id}/post`.
3. `POST /api/v1/sales-receipts` — customer pays 200,000 cash.
4. `POST /api/v1/receipt-allocations` — allocate 200,000 to invoice.

**Expected:**
- Step 2: `SalesInvoicePosted.v1`; stock_move OUT 100 MILK-1L; `cost_amount` snapped at post; customer debt opens at 350,000.
- Step 3: `SalesReceiptCaptured.v1`; `cash_entry` IN on TILL or BANK.
- Step 4: `ReceiptAllocated.v1`; invoice `paid_amount = 200,000`, `status = PARTIALLY_PAID`, remaining debt 150,000.

**Negative variants:**
- Step 1 when `customer.open_debt + invoice.total > credit_limit` and caller lacks `SALES_INVOICE.OVERRIDE_CREDIT` → 422.
- Step 4 with `amount > receipt.total_amount` → 422.

---

## E4. POS happy path (offline + sync)

### TC-E2E-004 — Open till → 5 sales offline → reconnect → sync [P1]
**Modules:** pos → catalog → stock → cash → day
**Stories:** US-POS-002, US-POS-003, US-POS-009, US-POS-017, US-POS-018

**Preconditions:**
- Flutter POS client paired with branch, has a recent catalog snapshot.
- Items `MILK-1L`, `BREAD-LOAF` cached locally.
- Business day OPEN on the branch.

**Steps:**
1. POS client opens a `till_session` with float 50,000 UGX while ONLINE.
2. Disable network. Cashier rings 5 cash sales (varying baskets totalling 35,000 UGX).
3. Each sale prints a receipt locally with `client_op_id` and a TILL-namespaced number.
4. Re-enable network. Client invokes `POST /api/v1/sync/push` with the 5 queued ops.
5. Verify each op's `client_op_id` appears in the response `ackedClientOpIds`.

**Expected:**
- 5 `pos_sale` rows on the server, each with its `client_op_id` set.
- 5 `PosSaleClosed.v1` events emitted; stock module decrements balances; cash module writes 5 `cash_entry` IN rows on TILL.
- Replay of the same push body returns the same response; no duplicate sales or stock moves.
- No `cash_entry` for the disconnected period until the sync; sync inserts them all atomically (per-sale tx).

**Negative variants:**
- Out-of-order timestamps on the same till session → 409 with a list of conflicts; client re-orders and retries.
- A sale referencing a closed `till_session` → 422.
- A sale dated to a closed `business_day` → 422.

---

## E5. Refund-at-till

### TC-E2E-005 — Same-day cash refund within threshold [P1]
**Modules:** pos → cash → stock
**Stories:** US-POS-019

**Preconditions:**
- Earlier today, `pos_sale` `S` exists: 2 × `MILK-1L`, paid 7,000 cash, NOT yet refunded.
- Threshold configured at 50,000 UGX.
- Till session OPEN.

**Steps:**
1. Cashier scans receipt code for sale `S`.
2. `POST /api/v1/pos-sales` with `kind = REFUND`, `refunded_from_sale_id = S.id`, lines `MILK-1L × -2`, `pos_payment` `method = CASH`, `amount = -7,000`.

**Expected:**
- 201; new `pos_sale` row with `kind = REFUND`.
- `PosSaleRefunded.v1` emitted.
- `stock_move` IN of `MILK-1L × 2` (reverses the original SALE move).
- `cash_entry` OUT on TILL for 7,000 UGX; new `ref_type = PosRefund`.
- Original sale row immutable; status unchanged.

**Negative variants:**
- Refund of a sale from a previous business day → 422 with `BUSINESS_DAY_CLOSED_FOR_TILL_REFUND` (must use back-office customer return).
- Refund total > threshold without `POS.REFUND_OVERRIDE_THRESHOLD` permission → 403.
- Refund without scanning receipt → require manager PIN; without it → 403.

---

## E6. Foreign-currency tender

### TC-E2E-006 — Mixed tender: UGX + USD [P1]
**Modules:** pos → admin (fx_rate) → cash
**Stories:** US-POS-021, US-ADMIN-004

**Preconditions:**
- Functional currency `UGX`.
- `USD` enabled; today's `UGX → USD` rate quoted at 3,800.
- Till `TILL-1` has `USD` in `till_currency`.
- Cart total: 38,000 UGX.

**Steps:**
1. `POST /api/v1/pos-sales` with two payments:
   - `{ method: CASH, tender_currency: UGX, tender_amount: 19,000, amount: 19,000, fx_rate_snapshot: 1 }`
   - `{ method: CASH, tender_currency: USD, tender_amount: 5, amount: 19,000, fx_rate_snapshot: 3,800 }`

**Expected:**
- 201; sale total 38,000 UGX; tenders sum to functional total.
- `PosFxTenderUsed.v1` emitted.
- Cash module writes TWO `cash_entry` IN rows on TILL: one for UGX 19,000 (currency = UGX, fx = 1), one for USD 5 (currency = USD, fx = 3,800).
- `cash_book` rows for `(branch, TILL, UGX, today)` and `(branch, TILL, USD, today)` each updated separately.

**Negative variants:**
- USD payment when till does not accept USD → 422.
- `tender_amount × fx_rate_snapshot` ≠ `amount` (within rounding tolerance) → 422.

### TC-E2E-007 — Close till with FX variance [P1]
**Modules:** pos → cash
**Stories:** US-POS-016, US-DAY-006

**Preconditions:**
- Till session has float 100,000 UGX + 20 USD; sold 38,000 UGX + 5 USD (per TC-E2E-006); no pickup; no petty cash.

**Steps:**
1. `POST /api/v1/till-sessions/{id}/close` — declared cash UGX 138,000, USD 25.

**Expected:**
- 200; `TillSessionClosed.v1` carries variance per currency.
- Expected UGX = 100,000 + 19,000 = 119,000. Declared = 138,000 → variance +19,000.
- Expected USD = 20 + 5 = 25. Declared = 25 → variance 0.
- Wait — original example total was 38,000 UGX cart paid with 19,000 UGX + USD 5 equivalent; recompute: expected UGX 119,000 (declared 138,000 → variance +19,000), expected USD 25 (declared 25 → variance 0). Per-currency rows in `cash_book` updated.
- Above variance threshold → require supervisor PIN; without it → 403.

---

## E7. Weighed items + barcode parse

### TC-E2E-008 — Scale-printed barcode at POS [P1]
**Modules:** catalog → pos → stock
**Stories:** US-CAT-015, US-CAT-016, US-POS-003

**Preconditions:**
- Item `TOMATO-LOOSE` with `is_weighed = true`, `weighing_unit = KG`, unit_price = 4,500 / KG, barcode of `barcode_type = EMBEDDED_WEIGHT` with PLU `12345`.
- In-store scale weighs 0.420 KG, prints EAN-13 `2` + `12345` + `00420` + check digit.

**Steps:**
1. POS client scans the printed barcode.
2. Parser decodes: PLU `12345` (matches `TOMATO-LOOSE`), weight `0.420 KG`.
3. POS client posts `pos_sale_line { item_id: ..., qty: 0.420, unit_price: 4,500, line_total: 1,890 }`.

**Expected:**
- Backend accepts the line (PLU resolves to the item; qty is non-integer; line_total = 4,500 × 0.420).
- `stock_move` OUT for 0.420 KG of `TOMATO-LOOSE`.
- Receipt prints qty as `0.420 KG @ UGX 4,500`.

**Negative variants:**
- PLU does not resolve → 422 with `UNKNOWN_PLU`.
- Weight is zero → 422.

---

## E8. Batch tracking / FEFO

### TC-E2E-009 — GRN captures batch + FEFO at POS [P1]
**Modules:** procurement → stock → pos
**Stories:** US-STOCK-011, US-STOCK-012

**Preconditions:**
- Item `YOGURT-500ML` with `tracks_batches = true`.
- Existing stock_batch A: `batch_no = Y2026-03`, `expiry_at = 2026-06-30`, `qty_on_hand = 30`.
- Existing stock_batch B: `batch_no = Y2026-02`, `expiry_at = 2026-05-30`, `qty_on_hand = 20`.

**Steps:**
1. GRN posts 50 new `YOGURT-500ML` with `batch_no = Y2026-04`, `expiry_at = 2026-08-31`.
2. POS sale rings 25 `YOGURT-500ML`.

**Expected:**
- After GRN: new stock_batch C `qty_on_hand = 50, status = ACTIVE`. `BatchCreated.v1` emitted.
- POS sale picks batches FEFO order: batch B (20, earliest expiry) is consumed first → `qty_on_hand = 0, status = EXHAUSTED`; then batch A (5 more) → A `qty_on_hand = 25`.
- 2 `pos_sale_line` rows possible (one per batch consumed), or 1 line with 2 stock_move rows — implementation choice, both acceptable.
- `BatchExhausted.v1` emitted for batch B.

**Negative variants:**
- Item with `tracks_batches = true` and no ACTIVE batches → 422 `NO_BATCH_AVAILABLE`.
- A batch with `expiry_at < today` is skipped; if it's the only stock and no override, → 422 with `STOCK_AVAILABLE_BUT_EXPIRED`.

### TC-E2E-010 — Expired batch write-off [P2]
**Modules:** stock (scheduled job) → procurement (audit)
**Stories:** US-STOCK-013

**Preconditions:**
- Stock_batch with `expiry_at = yesterday`, `status = ACTIVE`, `qty_on_hand = 10`.

**Steps:**
1. Scheduled job runs at 02:00.
2. Manager opens expiring-soon report and posts an `EXPIRY_WRITE_OFF` `stock_move` for the 10 units.

**Expected:**
- After job: batch `status = EXPIRED`; event `BatchExpired.v1`.
- After write-off: `stock_move` OUT of 10 units, batch `qty_on_hand = 0`.
- Reporting picks up the wastage cost.

---

## E9. Production (bakery batch)

### TC-E2E-011 — Plan → start → record output → consume materials [P1]
**Modules:** production → catalog → stock
**Stories:** US-PROD-003, US-PROD-004, US-PROD-005, US-PROD-013

**Preconditions:**
- Section `BAKERY` exists on branch.
- BOM `BREAD-WHITE-LOAF`: 0.5 KG `FLOUR`, 0.01 KG `YEAST`, 0.015 KG `SALT`. Yield 1 loaf.
- Materials in stock: FLOUR 100 KG, YEAST 5 KG, SALT 10 KG (all in BAKERY section).

**Steps:**
1. `POST /api/v1/production-batches` — bom = `BREAD-WHITE-LOAF`, target_qty = 50, section = `BAKERY`.
2. `POST /api/v1/production-batches/{id}/start`.
3. `POST /api/v1/production-batches/{id}/post-output` — actual consumption: 26 KG flour, 0.55 KG yeast, 0.8 KG salt; actual output: 50 loaves.

**Expected:**
- Step 1: batch `lifecycle_state = PLANNED`; materials reserved (`stock_move RESERVED`).
- Step 2: batch `IN_PROGRESS`; reservation flips to `PROD_CONSUME` moves; events `ProductionBatchStarted.v1`, `ProductionConsumePosted.v1`.
- Step 3: batch transitions to `OUTPUT_HOT_DISPLAY`; new `stock_batch` for bread loaves with `manufactured_at = now`; `stock_move PROD_OUTPUT` IN 50 loaves; `ProductionOutputPosted.v1`.
- Yield variance: 0 (actuals = planned, except slight flour over-use). Variance report picks up the deltas.

**Negative variants:**
- Materials insufficient at plan time → 422.
- Output qty > 2× planned without override → 422.

### TC-E2E-012 — Wastage recorded + lifecycle to write-off [P1]
**Modules:** production → stock
**Stories:** US-PROD-009, US-PROD-010

**Preconditions:**
- Batch from TC-E2E-011 is in `OUTPUT_COLD_DISPLAY` with 8 loaves remaining; 42 sold.

**Steps:**
1. End of day: cashier records 3 loaves with category `DONATED` (food rescue).
2. Operator transitions remaining 5 loaves to `OUTPUT_WRITE_OFF` (oversupply).

**Expected:**
- Step 1: `production_wastage` row with `category = DONATED`, `qty = 3`. Stock decrement by 3 via compensating `stock_move` of type `EXPIRY_WRITE_OFF` (or a new `WASTAGE_WRITE_OFF` type — implementation decides).
- Step 2: batch `lifecycle_state = OUTPUT_WRITE_OFF`; stock decrement by 5; `ProductionLifecycleAdvanced.v1`.

---

## E10. Layby + pre-order

### TC-E2E-013 — Create layby, pay instalments, collect [P1]
**Modules:** orders → stock → cash → pos
**Stories:** US-ORD-001, US-ORD-002, US-ORD-003

**Preconditions:**
- Customer `JANE-MUSOKE` exists.
- Item `BLENDER-XL` has `qty_on_hand = 5`, `qty_reserved = 0`.

**Steps:**
1. `POST /api/v1/orders` — type `LAYBY`, customer, line `BLENDER-XL × 1 @ 250,000`, deposit_required 30% = 75,000.
2. `POST /api/v1/orders/{id}/payments` — 75,000 cash.
3. Two weeks later, `POST /api/v1/orders/{id}/payments` — 100,000 cash.
4. A week later, `POST /api/v1/orders/{id}/payments` — 75,000 cash (final).
5. `POST /api/v1/orders/{id}/collect` — invoked when customer arrives at till.

**Expected:**
- Step 1: order `status = DEPOSIT_PAID` (when deposit ≥ required_deposit at create); stock reserved (`stock_move RESERVED`); item_branch_balance `qty_reserved += 1`.
- Step 2-4: `customer_order_payment` rows; `cash_entry` IN; status `DEPOSIT_PAID → PARTIALLY_PAID → READY`.
- Step 5: `OrderCollected.v1` consumed by pos → creates a `pos_sale` referencing the order; stock RESERVED move reversed; SALE move posted.

**Negative variants:**
- Reservation released on `OrderCancelled.v1`; deposit refund per policy.
- Order past `reserved_until` while still unpaid → scheduled job marks `EXPIRED`, releases reservation.

### TC-E2E-014 — Pre-order triggers production batch [P1]
**Modules:** orders → production → cash
**Stories:** US-ORD-004

**Preconditions:**
- Customer requests a custom birthday cake for next Saturday.
- BOM `BIRTHDAY-CAKE-CUSTOM` exists in section `BAKERY`.

**Steps:**
1. `POST /api/v1/orders` — type `PRE_ORDER`, lines reference the custom cake item, deposit 50%.
2. `POST /api/v1/orders/{id}/payments` — deposit paid.
3. Production module consumes `OrderDepositPaid.v1` and creates `production_batch` in `PLANNED` state.
4. On Saturday, baker starts and posts output.
5. `ProductionOutputPosted.v1` is consumed by orders; order moves to `READY`.
6. Customer collects → `POST /api/v1/orders/{id}/collect`; final payment captured.

**Expected:**
- Sequence completes without errors; reservation NOT made on items (production made-to-order); production_batch correctly section-tagged.

---

## E11. Gift cards

### TC-E2E-015 — Issue → redeem partial → balance remains [P1]
**Modules:** giftcard → pos → cash
**Stories:** US-GC-001, US-GC-003

**Preconditions:**
- Customer wants a 100,000 UGX gift card; pays cash.

**Steps:**
1. `POST /api/v1/gift-cards` — initial_value = 100,000. Cash payment captured at the till.
2. Later: customer presents code at POS; cart total 35,000 UGX.
3. `POST /api/v1/gift-cards/{code}/redeem` — amount 35,000, ref = pos_sale.
4. POS posts the sale with `pos_payment { method: GIFT_CARD, amount: 35,000, tender_currency: UGX }`.

**Expected:**
- Step 1: `gift_card` row `ACTIVE` with balance 100,000; `gift_card_txn` `LOAD` row; `cash_entry` IN on TILL (paired); events `GiftCardIssued.v1` + `CashEntryPosted.v1`.
- Step 3: `gift_card_txn` `REDEEM` row; balance = 65,000; status still `ACTIVE`.
- Step 4: `pos_sale` posted with gift-card tender; NO `cash_entry` for the gift-card tender (liability ledger only).

**Negative variants:**
- Redeem amount > balance → 422.
- Redeem on a frozen card → 422.
- Redeem on an expired card → 422.
- Redeem on a card with `company_id` ≠ current request → 422 (gift cards do not cross companies).

### TC-E2E-016 — Refund a gift-card-tendered sale credits the card [P2]
**Modules:** pos → giftcard
**Stories:** US-GC-004

**Preconditions:**
- Sale `S` was paid fully by gift card `G` (35,000 UGX). Card balance 65,000.

**Steps:**
1. Customer returns `S` same-day with receipt.
2. POS posts a refund-kind `pos_sale` referencing `S`, payment `method = GIFT_CARD`, `amount = -35,000`.

**Expected:**
- Card `G` balance = 100,000 (credited back).
- `gift_card_txn` `REFUND` row.
- No `cash_entry`.

---

## E12. Day-end orchestration

### TC-E2E-017 — End of day per branch [P1]
**Modules:** day → pos → cash → stock → procurement → wms
**Stories:** US-DAY-002 (PRD §6.6)

**Preconditions:**
- Branch has 3 tills, all of today's sessions CLOSED.
- No unposted GRNs, no open production batches.

**Steps:**
1. `POST /api/v1/business-days/{branchId}/{date}:end`.

**Expected:**
- Pre-flight gates pass.
- `business_day.status = CLOSING`.
- Event `BusinessDayClosingStarted.v1` consumed by pos, cash, reporting.
- pos builds Z-report PDF and uploads to object storage.
- cash finalises banking entry (today's safe → BANK if scheduled).
- After ack: `business_day.status = CLOSED`, `BusinessDayClosed.v1`, next day auto-opened.

**Negative variants:**
- Open till_session detected → 422 with list of offending tills; status stays `OPEN`.
- One consumer fails to ack within timeout → revert to `OPEN` with an error report.

### TC-E2E-018 — Supervisor override into closed day [P2]
**Modules:** day → procurement
**Stories:** US-DAY-003

**Preconditions:**
- Yesterday's business_day is `CLOSED`. A late supplier invoice needs to be matched against a GRN dated yesterday.

**Steps:**
1. Supervisor `POST /api/v1/business-days/{branchId}/{date}/override` with reason and supervisor PIN.
2. Within the granted window (default 24h), `POST /api/v1/supplier-invoices/{id}/post` for an invoice with `posting_date = yesterday`.
3. After window expires, `cash` job marks override `EXPIRED`.

**Expected:**
- Override grants succeeded; `BusinessDayOverrideOpened.v1` emitted.
- Posting into closed day succeeds and writes a `business_day_override` row tagged with the supplier_invoice id + reason.
- Reports highlight overridden entries.

---

## E13. Multi-branch + section reporting

### TC-E2E-019 — Section-level P&L for bakery [P1]
**Modules:** admin → pos → production → reporting
**Stories:** US-RPT-011

**Preconditions:**
- Branch has sections `RETAIL_FLOOR`, `BAKERY`, `BUTCHERY`.
- Today: BAKERY produced 100 loaves (BOM cost 80,000 UGX); sold 80 loaves at 1,500 each (POS pos_sales tagged with `section = BAKERY`).

**Steps:**
1. `GET /api/v1/reports/section-pnl?branchId=...&date=today`.

**Expected:**
- Bakery row: revenue 120,000, cost 64,000 (80 × 800), margin 56,000.
- Inventory-on-hand at bakery: 20 loaves × 800 = 16,000 (remains for tomorrow or wastage).
- Other sections show their own revenue (0 if no sale tagged there).

### TC-E2E-020 — Inter-branch stock transfer [P2]
**Modules:** stock → admin → cash (no cash impact)
**Stories:** US-STOCK-007, US-STOCK-008

**Preconditions:**
- Branch A has `MILK-1L × 100`; Branch B has `MILK-1L × 5`.

**Steps:**
1. Branch A `POST /api/v1/stock-transfers` — issue 30 to Branch B.
2. Branch A `POST /api/v1/stock-transfers/{id}/issue` — `TRANSFER_OUT` posted.
3. Branch B `POST /api/v1/stock-transfers/{id}/receive` — `TRANSFER_IN` posted.

**Expected:**
- After issue: Branch A `qty_on_hand` 70, Branch B `qty_in_transit` 30.
- After receive: Branch A unchanged, Branch B `qty_on_hand` 35, `qty_in_transit` 0.
- Events `TransferIssued.v1` and `TransferReceived.v1`.
- Variance (e.g. receive only 28) → flagged on the transfer row + reason required.

---

## E14. Negative-stock + supervisor override

### TC-E2E-021 — Oversell blocked then authorised [P1]
**Modules:** pos → stock → auth
**Stories:** US-STOCK-010, US-POS-005

**Preconditions:**
- Item `MILK-1L` `qty_on_hand = 1` at the till's branch.
- Cart wants `MILK-1L × 3`. Cashier lacks `STOCK.OVERSELL`.

**Steps:**
1. POST pos_sale → backend rejects with `NEGATIVE_STOCK_BLOCKED`.
2. Manager presents PIN; backend issues a short-lived `oversell_token` for this sale.
3. POST pos_sale with `oversell_token` → succeeds.

**Expected:**
- Step 1: 422 `NegativeStockBlocked.v1` emitted for telemetry; sale not posted.
- Step 3: `stock_move.authorised_by_user_id` set to the supervisor; sale posted; balance −2.
- Audit log captures both the block and the override.

---

## E15. Authentication + RBAC

### TC-E2E-022 — Lockout after 5 failed logins; reset after success [P1]
**Modules:** auth
**Stories:** US-IAM-001

**Steps:**
1. POST /api/v1/auth/login with wrong password 5 times in a row for user `cashier1`.
2. POST /api/v1/auth/login with correct password.
3. Wait 15 minutes (lockout window), retry with correct password.

**Expected:**
- After step 1: `app_user.failed_login_count = 5`, `locked_until = now + 15m`. Any login attempt (right or wrong) within the window returns 401 `invalid_credentials` (without revealing the lockout).
- After 15m: `failed_login_count` cleared on successful login; `last_login_at` updated.

### TC-E2E-023 — JWT carries branch + permissions; revoked on deactivate [P1]
**Modules:** auth → party
**Stories:** US-IAM-007

**Steps:**
1. User `staff1` logs in; receives JWT.
2. Use JWT for a permissiond call → succeeds.
3. Admin deactivates `staff1`.
4. Reuse the JWT (still inside its 15-min TTL).

**Expected:**
- Step 4: backend rejects the call (deactivated user). Specifically, the request filter checks `app_user.status` after JWT parse and rejects if not ACTIVE.

---

## E16. Edge cases

### TC-E2E-024 — Concurrent pos_sale posts to the same till [P2]
**Type:** Edge / Concurrency
**Modules:** pos
**Steps:**
1. Two clients (e.g., main app + manager dashboard) attempt to close the same till session simultaneously.

**Expected:**
- Exactly one wins; the other gets 409 with a conflict body.
- Final `till_session.status = CLOSED` with a single Z-report PDF.

### TC-E2E-025 — Sync with clock skew (client ahead by 10 min) [P2]
**Type:** Edge / Time
**Modules:** pos → day
**Steps:**
1. POS client clock is 10 minutes ahead of server.
2. Client posts sale dated 10 minutes in the future.

**Expected:**
- Server accepts (POS times are advisory; `business_day` is gated by the SERVER's current date, not the client's).
- `pos_sale.sale_at` keeps the client value; `server_at` is the server now.
- No business_day rollover until the server's clock crosses midnight.

### TC-E2E-026 — Sale at exact business-day rollover [P2]
**Type:** Edge / Time
**Modules:** day → pos
**Steps:**
1. Branch close-of-business time is 23:59. Sale at 23:59:30 lands in OLD day; sale at 00:00:30 lands in NEW day.

**Expected:**
- Sales tagged with the correct `business_date`.
- If old day is being closed at the same moment as new sale arrives, sale rejected with `BUSINESS_DAY_CLOSING_IN_PROGRESS`.

---

## E17. Security

### TC-E2E-027 — PII redaction in audit log + events [P1]
**Modules:** common (audit) → party
**Stories:** US-IAM-013

**Steps:**
1. Create a customer with phone +256-700-000-000 and email `jane@example.com`.
2. Read `audit_log` row and the `PartyCreated.v1` event payload.

**Expected:**
- `audit_log.after_json` redacts the `@PII`-tagged fields (phone, email show as `****`).
- The event payload sent to EXTERNAL webhook subscribers is similarly redacted.
- The internal in-process consumer sees full PII (it needs it).

### TC-E2E-028 — Cross-tenant read blocked [P1]
**Modules:** common (request context) → catalog
**Stories:** US-IAM-009 (effectively)

**Steps:**
1. User A's JWT has `company_id = 1`.
2. A requests `GET /api/v1/items/{id}` where the item belongs to `company_id = 2`.

**Expected:**
- 404 (treat as not-found to avoid leaking existence).
- Audit log records the attempt.

### TC-E2E-029 — Audit log hash chain integrity [P2]
**Modules:** common
**Stories:** US-IAM-014

**Steps:**
1. Capture audit_log row N.
2. Manually attempt to UPDATE the `before_json` or `row_hash` of row N (simulate tampering).
3. Run the audit-chain verification job.

**Expected:**
- Verification job fails on row N+1 (its `prev_hash` no longer matches row N's `row_hash`).
- Alert raised; report identifies the broken row.

---

## E18. Performance smoke (not load testing yet)

### TC-E2E-030 — 100 concurrent POS sales at one branch [P2]
**Type:** Performance smoke
**Modules:** pos → stock → cash
**Steps:**
1. 100 parallel POST /api/v1/pos-sales requests, each different cart, same till session.

**Expected:**
- p95 latency < 500ms.
- No 5xx; no DB deadlocks.
- Final `item_branch_balance.qty_on_hand` matches the sum of decrements (no lost-update).
- Cash book balance matches sum of cash tenders.

### TC-E2E-031 — Outbox dispatcher keeps up under burst [P2]
**Type:** Performance smoke
**Modules:** common (outbox)
**Steps:**
1. Generate 10,000 `domain_event` PENDING rows in 1 minute.
2. Watch the dispatcher run.

**Expected:**
- Dispatcher reaches 0 PENDING within 5 minutes.
- No event stuck on retry without exceeding `max_attempts`.
- No webhook subscriber receives duplicates beyond at-least-once expectation.

---

## E19. Migration smoke

### TC-E2E-032 — Run identical seed scenario on MySQL and PostgreSQL [P1]
**Type:** Functional / DB-agnostic
**Stories:** PRD §11

**Steps:**
1. Start two stacks: one on MySQL 8, one on PostgreSQL 15.
2. Run the bootstrap + first-sale scenario (TC-E2E-001) against each.

**Expected:**
- Both succeed.
- Schemas after Flyway migration are equivalent (modulo dialect-specific sequence emulation).
- All events emitted identically.
- Sample queries (item lookup, stock card, daily summary) return matching shapes / counts.

---

## E20. WMS / Mobile (deferred — outside MVP module set)

WMS flows (`US-WMS-*`) are out of the current minimum module set. When `modules/wms` is re-added, the following scenarios should be authored:
- Field-sale capture, sync queue depth, daily sales-sheet settlement with variance, approval, mileage / fuel expense capture.
- Reserved here as a placeholder; not in current QA scope.

---

*Index of all E2E scenarios. See per-module test plans in [modules/](modules/) for unit-level functional and edge tests.*
