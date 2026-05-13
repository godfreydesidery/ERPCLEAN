# Cash module

## 1. Purpose

The cash module is the **company-wide cash ledger**. Every physical-cash movement in the system — POS tender, mid-shift cash pickup, petty-cash payout, supplier cash settlement, opening till float, end-of-day banking — lands as a row in `cash_entry`. Per-location running balances are summarised in `cash_book`.

**Synthesis note.** PRD §5 does not give cash its own functional section. The cash module is **synthesised** from the cash-movement requirements scattered across:

- §5.7 / §6.1 POS (till float, mid-shift pickups, petty cash, close-till variance)
- §5.9 Debt Management (supplier payments produce a cash-out movement)
- §5.11 / §6.6 Day Management (end-of-day banking and Z-summary)
- §6.5 Sales receipts (a cash receipt against a debt produces a cash-in movement)

Those modules **own** their business documents (till session, sales receipt, supplier payment record); the cash module **owns the resulting ledger entries and the cash position**. Most writes arrive as domain events from those modules; direct API writes are limited to supervisor adjustments and the supplier-payment document itself.

## 2. Scope

### In scope
- `cash_entry` — append-only ledger of every cash movement (IN / OUT), tagged with source document.
- `cash_book` — per-branch, per-account, per-business-date opening / in / out / closing balance.
- `supplier_payment` + `supplier_payment_allocation` — the supplier-payable settlement document and its allocation across one or many supplier invoices.
- Banking / deposit movements (`CASH_BOX` → `BANK`).
- Petty-cash payouts with a GL category tag.
- Opening-float assignment when a till session opens.
- Mid-shift cash pickup (till drawer → cash box).
- End-of-day banking (cash box → bank).
- Supervisor cash adjustments with a mandatory reason.

### Out of scope
- **Customer receipt allocation to sales invoices.** The sales module owns `sales_receipt` and `receipt_allocation` (matching receipts to debt). The cash-side ledger row for that receipt **is** owned here, written off the sales receipt event.
- **POS cash declaration / variance computation.** The pos module owns `till_session.close_variance`. Cash records the resulting cash movement and the variance ledger entry.
- **Card and mobile-money tenders.** Owned by pos; they are not `cash_entry` rows. The `account` enum reserves `MOBILE_MONEY` for float-style mobile-money cash boxes that physically hold balance, not for card-rail pass-through.
- **Currency conversion / FX.** Post-MVP. `cash_entry.currency_code` is captured but only a single currency per branch is supported at launch.

## 3. Domain model

| Aggregate | Backing tables | Notes |
|---|---|---|
| `CashEntry` | `cash_entry` | DATA-MODEL §10.2. Append-only. `account` ∈ {TILL, CASH_BOX, BANK, MOBILE_MONEY}. `direction` ∈ {IN, OUT}. `amount` always positive. `ref_type` enumerates source doc kinds. |
| `CashBook` | `cash_book` | DATA-MODEL §10.3. Composite PK `(branch_id, account, business_date)`. Derived projection, rebuildable from `cash_entry`. |
| `SupplierPayment` | `supplier_payment` | DATA-MODEL §10.4. Header for an outbound payment to a supplier; carries method (CASH / BANK_TRANSFER / CHEQUE / MOBILE_MONEY), `total_amount`, `allocated_amount`. |
| `SupplierPaymentAllocation` | `supplier_payment_allocation` | DATA-MODEL §10.5. Many-to-one onto `supplier_payment`; each row settles a portion of a `supplier_invoice`. |

`cash_entry.ref_type` values currently emitted: `PosSale`, `SalesReceipt`, `CashPickup`, `PettyCash`, `SupplierPayment`, `BankDeposit`. The list is an enum-on-the-wire — additions are additive.

## 4. Key business flows

1. **Open till session — opening float.** Pos emits `TillSessionOpened.v1`. Cash writes one `cash_entry` (IN, account=TILL, ref_type=`TillFloat`) for the float amount and an offsetting OUT against the source `CASH_BOX`.
2. **Mid-shift pickup.** Pos emits `CashPickupRecorded.v1`. Cash writes paired entries: OUT on TILL, IN on CASH_BOX, both with `ref_type=CashPickup`, same `ref_id`.
3. **Petty-cash payout.** Pos emits `PettyCashPaid.v1`. Cash writes one OUT on TILL with `ref_type=PettyCash` and a GL category tag (rent, fuel, repairs…). No paired IN — petty cash leaves the cash system.
4. **Close till session.** Pos emits `TillSessionClosed.v1` carrying expected vs declared. Cash writes (a) any closing transfer TILL → CASH_BOX for the declared amount and (b) a variance entry tagged with `ref_type=CashVariance` and GL category `VARIANCE` when declared ≠ expected.
5. **Supplier payment (cash).** Direct API write to `/api/supplier-payments` (method=CASH). Cash writes OUT on CASH_BOX with `ref_type=SupplierPayment`. Allocation rows link the payment to one or many `supplier_invoice` rows; the procurement module's payable ledger consumes the resulting `SupplierPaymentAllocated.v1` events.
6. **End-of-day banking.** Day emits `BusinessDayClosed.v1`. Branch manager (or scheduled job) records the deposit: paired entries OUT on CASH_BOX, IN on BANK, `ref_type=BankDeposit`.
7. **Supervisor adjustment.** Direct API write to `/api/cash-adjustments`. Single `cash_entry`, requires supervisor role + non-empty reason; an audit row is written alongside in the same transaction.

## 5. Module interactions

**Depends on**
- `party` — `supplier_id` on supplier payments.
- `day` — every movement must fall inside an OPEN `business_day` for its branch (unless overridden via DAY override).
- `platform.audit` — every direct write and every supervisor adjustment is audited.
- `platform.events` — outbox.

**Publishes**
- `CashEntryPosted.v1` — emitted after every `cash_entry` insert.
- `SupplierPaymentRecorded.v1` — header create.
- `SupplierPaymentAllocated.v1` — per allocation row; procurement listens to credit the payable.
- `CashBookBalanceUpdated.v1` — emitted after the `cash_book` projection is updated for a business date.

**Consumes**
- `pos.TillSessionOpened.v1` → write opening-float entry.
- `pos.CashPickupRecorded.v1` → write paired pickup entries.
- `pos.PettyCashPaid.v1` → write petty-cash OUT.
- `pos.TillSessionClosed.v1` → write close-out and variance entries.
- `sales.SalesReceiptRecorded.v1` → write cash IN for the receipt portion paid in cash.
- `procurement.SupplierPaymentRecorded.v1` → if procurement triggers payment instead of cash module (alternative entry point).
- `day.BusinessDayClosed.v1` → trigger banking workflow / lock further entries to that date.

## 6. API surface

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/cash-entries` | Read-only ledger query (filter by branch, account, business date, ref_type). |
| GET | `/api/cash-entries/{id}` | Single entry. |
| GET | `/api/cash-book` | Per-branch / per-account / per-date balance projection. |
| POST | `/api/supplier-payments` | Record a supplier payment with allocations. |
| GET | `/api/supplier-payments/{id}` | Header + allocations. |
| POST | `/api/cash-adjustments` | Supervisor adjustment; produces one `cash_entry`. Requires `CASH.ADJUST`. |
| POST | `/api/bank-deposits` | Record a CASH_BOX → BANK transfer (end-of-day banking). |

The bulk of writes are **event-driven**, not REST. Direct POSTs exist only where a human document is being recorded (supplier payment, adjustment, deposit slip).

## 7. Persistence

Flyway scripts live under `db/migration/common/`:

- `V<N>__cash_entry.sql` — `cash_entry` with index on `(branch_id, business_date, account)` and unique constraint on `(ref_type, ref_id, direction)` to keep event replay idempotent.
- `V<N>__cash_book.sql` — `cash_book` composite-PK table.
- `V<N>__supplier_payment.sql` — header + status enum.
- `V<N>__supplier_payment_allocation.sql` — FK to `supplier_payment` and `supplier_invoice`.

All amounts are `DECIMAL(18,4)` per DATA-MODEL §10. `cash_book` is rebuildable: a maintenance job can drop and recompute a branch / date from `cash_entry` without data loss.

## 8. User stories

**P1 — MVP**
- US-POS-002 — Open a till session (opening-float `cash_entry` is the cash side of this).
- US-POS-013 — Record a cash pickup.
- US-POS-014 — Pay petty cash from the till.
- US-POS-016 — Close a till session (close-out and variance `cash_entry` rows).
- US-DAY-001 — Open the business day (cash entries are gated by an OPEN day).
- US-DAY-002 — End-of-day per branch (banking deposit).

**P2**
- US-POS-015 — View an X-report mid-shift (reads `cash_book` plus uncommitted till data).
- US-DAY-003 — Override and post into a closed business day (cash respects the override marker).
- US-DAY-004 — View business-day status across branches.

## 9. Open questions

- **Accounting export package** (PRD §13 Q3). Cash is the boundary between the operational system and an external GL (QuickBooks, Tally, Sage). The shape of the export — file vs API, frequency, account mapping — drives whether `cash_entry.gl_category` is sufficient or whether a fuller chart-of-accounts mapping table is needed in this module.
- **Per-branch cash accounts** (DATA-MODEL §16.6). Confirm: one `TILL` per till session, one `CASH_BOX` per branch, one `BANK` per branch. Whether multi-bank per branch is in scope changes the `account` model from an enum to a referenced entity.
- **Data residency** (PRD §13 Q11). Cash transactions are the most jurisdictionally sensitive records in the system — affects deployment topology and any cross-border consolidation in `reporting`.
- **POS cash declaration variance** authorisation threshold (US-POS-016 AC) — configuration ownership: company, branch, or till.
- **Number sequence gaps** for `supplier_payment.number` (DATA-MODEL §16.5) — confirm acceptable.

## 10. Implementation notes

- **Hexagonal layout.** `domain/`, `application/`, `adapters/inbound/web/`, `adapters/inbound/events/`, `adapters/outbound/persistence/`, `adapters/outbound/events/`. ArchUnit fences off cross-module reach per ARCHITECTURE §2.1.
- **Invariants enforced in domain**
  - `cash_entry` is append-only — no update, no delete.
  - Paired movements (pickup, banking, opening float) post both legs in the same transaction; partial posts are rejected.
  - `cash_book.closing_amount = opening_amount + in_amount − out_amount` for every row.
  - `supplier_payment.total_amount` ≥ `Σ supplier_payment_allocation.amount`; status transitions to `FULLY_ALLOCATED` only when equal.
  - Supervisor adjustment requires `actor.role` includes supervisor **and** non-empty reason; enforced in `CashAdjustmentService`, asserted in domain.
- **Multi-tenancy.** Every row is scoped by `company_id` (inherited from `branch_id` → `branch` → `company`) and `branch_id`. The cash ledger is **branch-scoped**; there is no cross-branch movement primitive in MVP — inter-branch cash transfers, if needed, are modelled as paired entries with a `BankDeposit`-style `ref_type`.
- **Idempotency.** `cash_entry` carries `(source_doc_type, source_doc_id, direction)` with a unique constraint, so replayed events (pos retry, network blip) do not double-post. Event consumers are written to do an idempotent upsert keyed on that triple.
- **Transactional outbox.** `CashEntryPosted.v1`, `SupplierPaymentRecorded.v1`, `SupplierPaymentAllocated.v1`, `CashBookBalanceUpdated.v1` are all written to the platform outbox in the **same transaction** as the entry / projection update.
- **GL classification.** Each `cash_entry` carries a `gl_category` enum: `CASH`, `BANK`, `PETTY`, `VARIANCE`, `SUPPLIER_SETTLEMENT`, `RECEIPT`, `TILL_FLOAT`. The accounting-export integration consumes this; the rest of the system ignores it.
- **`cash_book` maintenance strategy.** Updated synchronously in the same transaction as the underlying `cash_entry` (write-through). A nightly rebuild job verifies projection consistency against the ledger and emits a metric on drift.

---

## 11. Phase 1.1 additions

See [docs/design/PHASE-1.1-ADDITIONS.md](../../../../../../../../docs/design/PHASE-1.1-ADDITIONS.md).

### Multi-currency cash book

- `cash_entry` gains `currency_code VARCHAR(3) FK → currency` and `fx_rate_snapshot DECIMAL(20,8)`.
- `cash_book` composite PK changes from `(branch_id, account, business_date)` to **`(branch_id, account, currency_code, business_date)`** — one row per currency per location per day.
- Functional-currency entries carry `fx_rate_snapshot = 1`.
- Foreign-currency entries store `tender_amount` from the POS / payment source PLUS the back-converted functional amount in the existing `amount` column (so functional-currency reports keep working unchanged).
- Close-till variance is computed per currency (`pos` aggregates declared vs system per currency; cash records each variance entry separately).

### Refund-at-till cash side

- POS `pos_sale` with `kind = REFUND` paid in cash → cash module consumes the event and writes a `cash_entry` OUT on TILL with negative net (i.e. `direction = OUT`, `amount > 0`).
- `ref_type = PosRefund` (new value). `gl_category = CASH_REFUND` (new value).
- Card / mobile-money / gift-card refunds don't hit cash module (gift card → giftcard module; card / MM → external processor, our books just net out).

### Gift card liability is NOT in cash_book

- `gift_card_txn` is the gift-card ledger, owned by `modules/giftcard/`. It does not flow into `cash_book` or `cash_entry`.
- The cash side of gift card ISSUANCE (customer pays UGX 50,000 to load a card) IS a `cash_entry` IN on TILL — recorded normally — and the gift-card LOAD event posts to the gift-card ledger separately.
- Document this clearly so accountants don't double-count.

### Layby / pre-order payments

- `orders` module emits `OrderInstallmentPaid.v1` / `OrderDepositPaid.v1` with `cash_entry_id` (already booked) attached.
- No new logic in `cash` — payment is just a regular `cash_entry` with `ref_type = CustomerOrder` (new value).
- On order cancel + refund, `cash` consumes `OrderCancelled.v1` with the refund instruction and posts an OUT on TILL or BANK depending on configured refund channel.

### New ref_type values on cash_entry

- `PosRefund`, `CustomerOrder`, `GiftCardIssue` (cash IN paired with gift-card LOAD).

### New gl_category values

- `CASH_REFUND`, `GIFT_CARD_ISSUE_PROCEEDS`, `FX_VARIANCE`, `ORDER_DEPOSIT`.

### New events consumed

- `pos.PosSaleRefunded.v1` (with `method = CASH`) → write OUT entry.
- `orders.OrderDepositPaid.v1` / `OrderInstallmentPaid.v1` → already booked; idempotency only.
- `orders.OrderCancelled.v1` (with refund) → write refund OUT entry.
