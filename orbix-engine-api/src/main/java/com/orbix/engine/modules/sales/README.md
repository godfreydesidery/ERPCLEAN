# Sales module

## 1. Purpose

The `sales` module owns the back-office sales surface: the lifecycle that runs from a written price offer (`sales_quotation`) through the legal sales document (`sales_invoice`), the money-in document (`sales_receipt`), and the link between them (`receipt_allocation`). It also owns the reverse side — `customer_return`, `customer_credit_note` — and the delivery side — `packing_list`. It is the canonical place where credit sales, B2B counter sales, deliveries against invoices, and back-office posting live. PRD §5.6 and §6.5 are the authoritative business statements.

## 2. Scope

In scope: quotation lifecycle, invoice raise/post/void, receipt capture, allocation of a receipt across one or many invoices, customer return → credit note → allocation, packing list build and dispatch, reprint with copy-number audit, credit-limit enforcement at invoice posting.

Out of scope:
- Cashier-till sales — owned by `pos` (a sibling of `sales_invoice`, not a subset; see DATA-MODEL §7).
- Customer party data — name, TIN, addresses, credit limit, default agent, default price list live in `party`.
- Stock outbound bookkeeping — `stock_move` rows are written by the `stock` module which consumes `SalesInvoicePosted.v1` / `CustomerReturnPosted.v1` events.
- Customer-debt ageing reports — open balances are reconstructable from `sales_invoice.paid_amount` plus `receipt_allocation`, but the ageing report itself is a future module not in the minimum set.
- Field sales / route-to-market — depends on `wms`, not in minimum set.

## 3. Domain model

Aggregates and tables owned by this module (DATA-MODEL §6):

- `sales_quotation` / `sales_quotation_line` — quote with `valid_until`, `price_list_id` frozen at issue, `status` in `{DRAFT, SENT, ACCEPTED, EXPIRED, CONVERTED, CANCELLED}`, `converted_to_invoice_id` back-link on conversion.
- `sales_invoice` / `sales_invoice_line` — legal sales document. `payment_terms` in `{CASH, CREDIT}`, `status` in `{DRAFT, POSTED, PARTIALLY_PAID, PAID, CANCELLED, VOIDED}`, `paid_amount` driven by allocations, `posted_at` / `posted_by` / `voided_at` / `voided_by` / `void_reason` audit fields, per-line `cost_amount` snapped at post for margin reporting, optional `promotion_id`.
- `sales_receipt` — money in, with `method` in `{CASH, CARD, BANK_TRANSFER, MOBILE_MONEY, CHEQUE, STORE_CREDIT}`, `total_amount`, `allocated_amount`, `unallocated_amount`.
- `receipt_allocation` — N:M link of a receipt to invoices it pays, with `amount`, `allocated_at`, `allocated_by`.
- `customer_return` / `customer_return_line` — `reason` in `{DAMAGED, EXPIRED, WRONG_ITEM, BUYER_REMORSE, OTHER}`, `restock` flag drives `RETURN_IN` vs `DAMAGE` stock move.
- `customer_credit_note` — issued from a return or as goodwill/correction, allocates against open invoices or held as customer credit.
- `packing_list` / `packing_list_line` — physical delivery against a `sales_invoice`, possibly in multiple trips; `status` in `{DRAFT, DISPATCHED, DELIVERED, CANCELLED}`.

## 4. Key business flows

1. **Quotation → invoice.** Draft `sales_quotation`, send, on `ACCEPTED` convert in one click — lines copy across, `converted_to_invoice_id` is set, quotation moves to `CONVERTED` (US-SALES-003, -004).
2. **Raise sales invoice.** Customer's `price_list_id` resolves unit prices; `payment_terms` toggles CASH vs CREDIT. On post: stock decrements (via stock module consuming the event), debt opens (credit) or cash entry opens (cash), `SalesInvoicePosted.v1` emitted (US-SALES-005, PRD §6.5).
3. **Apply discount.** Line- or header-level. Above a configured percent threshold supervisor authorisation is required; unit price after discount cannot drop below `item.min_sell_price` without override (US-SALES-006).
4. **Void posted invoice.** Manager action; reason mandatory; compensating `stock_move` and `debt_entry` / `cash_entry` rows are written by downstream modules consuming `SalesInvoiceVoided.v1`; original is preserved; status moves to `VOIDED`; must be same business day (the `day` module rejects void on a closed day) (US-SALES-007).
5. **Capture receipt + allocate.** Capture `sales_receipt` (method, reference, amount). Allocation screen lists customer's open invoices oldest-first, user enters per-invoice amounts, sum must be ≤ receipt total, remainder held as customer credit (US-SALES-008, -009, PRD §6.5 step 4).
6. **Customer return → credit note → allocate.** Select original invoice where known; lines default from it. `restock=true` → `RETURN_IN`; `restock=false` → `DAMAGE`. `customer_credit_note` opens; credit can be applied to open invoices (US-SALES-010, -011).
7. **Packing list build + dispatch.** Pack against a posted invoice in one or many trips; cumulative `packing_list_line.qty` across packing lists must not exceed the corresponding `sales_invoice_line.qty`. Dispatch transitions `DRAFT → DISPATCHED` and emits `PackingListDispatched.v1` (US-SALES-012).
8. **Reprint with audit.** Each reprint stamps a monotonically increasing copy number ("Copy 2 of original") and writes an audit row (US-SALES-013).

## 5. Module interactions

Depends on:
- `party` — customer master, credit limit, default agent, default price list.
- `catalog` — items, UoM, VAT groups, price lists, min sell price.
- `stock` — read-only balance check for availability hints (writes happen via event consumption in stock).
- `day` — posting requires an open business day for the branch.

Publishes (domain events, versioned, in same transaction via outbox):

| Event | Payload keys | Emitted from |
|---|---|---|
| `SalesInvoiceCreated.v1` | `salesInvoiceId`, `number`, `customerId`, `totalAmount`, `paymentTerms` | `SalesInvoiceServiceImpl#createDraft` |
| `SalesInvoicePosted.v1` | `salesInvoiceId`, `number`, `customerId`, `branchId`, `totalAmount`, `paymentTerms`, `currencyCode`, `lines[]` (Slice C — per-line breakdown with `salesInvoiceLineId`/`itemId`/`uomId`/`qty`/`unitPrice`/`vatGroupId`/`lineTotal`/`batchId`), `creditOverride` + `creditOverrideBy` + `creditOverrideReason` (when exercised) | `SalesInvoiceServiceImpl#post` |
| `SalesInvoiceVoided.v1` | `salesInvoiceId`, `number`, `customerId`, `totalAmount`, `reason`, `compensating`, `priorStatus`, `voidedBy`, `voidedAt` | `SalesInvoiceServiceImpl#voidInvoice` |
| `SalesInvoiceCancelled.v1` | `salesInvoiceId`, `number` | `SalesInvoiceServiceImpl#cancel` |
| `SalesInvoiceReprinted.v1` | `salesInvoiceId`, `number`, `reason` (enum name), `notes`, `reprintedBy`, `reprintedAt`, `reprintCount` | `SalesInvoiceServiceImpl#reprint` |
| `SalesReceiptCreated.v1` | `salesReceiptId`, `number`, `customerId`, `totalAmount`, `allocations` (count) | `SalesReceiptServiceImpl#createDraft` |
| `SalesReceiptPosted.v1` | `salesReceiptId`, `number`, `customerId`, `branchId`, `totalAmount`, `method`, `currencyCode` | `SalesReceiptServiceImpl#post` |
| `SalesReceiptCancelled.v1` | `salesReceiptId`, `number` | `SalesReceiptServiceImpl#cancel` |

Synchronous callers (named exemptions per ADR-0004):
- `SalesInvoiceServiceImpl#post` / `#voidInvoice` → `stock.StockMoveService`,
  `stock.StockBatchService` (FEFO drain + outbound `stock_move`, compensating
  inbound on void).
- `SalesReceiptServiceImpl#post` → `cash.CashLedgerService` (cash `IN` entry
  for CASH / MOBILE_MONEY / BANK / CHEQUE; absent for CARD / STORE_CREDIT).

Consumers (informational): `stock` writes `stock_move`; `cash` writes `cash_entry`; reporting builds margin and ageing.

## 6. API surface

- `/api/sales-quotations` — CRUD, send, convert.
- `/api/sales-invoices` — draft, post, void (manager), reprint.
- `/api/sales-receipts` — capture, post.
- `/api/receipt-allocations` — list customer's open invoices, allocate.
- `/api/customer-returns` — record return, post.
- `/api/customer-credit-notes` — issue, allocate to invoices.
- `/api/packing-lists` — build, dispatch, mark delivered.

All endpoints honour `Idempotency-Key` for state-changing calls. Responses carry the canonical document number (`SI-<branch>-<seq>`, `RCT-<branch>-<seq>`, etc.).

## 7. Persistence

Flyway migrations live under `orbix-engine-api/src/main/resources/db/migration/common/` named `V<N>__sales_<purpose>.sql` (e.g. `V0210__sales_invoice.sql`, `V0211__sales_receipt.sql`). DB-agnostic SQL; no vendor-specific types beyond what the abstraction layer supports.

Per-branch number sequences: `INV-BR1-000001`, `RCT-BR1-000001`, `QT-BR1-000001`, `PKL-BR1-000001`, `CN-BR1-000001`. Gaps on rollback are acceptable (DATA-MODEL §16.5). Money columns are `DECIMAL(18,4)`; quantities `DECIMAL(18,4)`.

## 8. User stories

P1 (MVP-blocking):
- US-SALES-001 Create a customer (delegates to party).
- US-SALES-002 Edit a customer.
- US-SALES-005 Raise a sales invoice.
- US-SALES-006 Apply a discount (line or header).
- US-SALES-007 Void a posted sales invoice.
- US-SALES-008 Capture a sales receipt.
- US-SALES-009 Allocate a receipt across invoices.
- US-SALES-013 Reprint a sales document with audit.

P2:
- US-SALES-003 Raise a sales quotation.
- US-SALES-004 Convert a quotation to an invoice.
- US-SALES-010 Process a customer return.
- US-SALES-011 Apply a customer credit note to an invoice.
- US-SALES-012 Build and dispatch a packing list.

## 9. Open questions

From DATA-MODEL §16 and PRD §13, the items that affect this module:
- §16.1 Should `customer` and `supplier` share a `party_id`? Affects how this module joins to `party`.
- §16.3 Walk-in customer at POS — synthetic `WALK-IN-BR-...` `customer` per branch; sales links to it, but the policy lives in the `pos` module.
- §16.5 Number sequence gaps on rollback — confirm gaps acceptable for `SI-`, `RCT-`, etc.
- §16.8 Sales agent commission storage — currently a flat rate on `sales_agent`; tier-/item-based rules would need a new `commission_rule` table consumed here.

Returns-at-POS policy: intersects sales (it issues a `customer_credit_note`) but the trigger and UX live in `pos`.

## 10. Implementation notes

Hexagonal layering: `domain/` (aggregates, value objects, invariants), `application/` (use cases, command handlers), `adapter/in/web/` (REST controllers), `adapter/out/persistence/` (JPA), `adapter/out/event/` (outbox publisher). No JPA annotations on domain types.

Invariants enforced inside aggregates:
- A posted `sales_invoice` is immutable; only `void` is allowed, and only on the same business day it was posted.
- Reprint counts are strictly monotonic per document (`sales_invoice.reprint_count`); each reprint emits `SalesInvoiceReprinted.v1` with the chosen `ReprintReason` (`DUPLICATE`/`REISSUE_TO_CUSTOMER`/`INTERNAL_FILE`/`OTHER`) and optional free-text notes (≤500 chars).
- `SUM(receipt_allocation.amount) ≤ sales_receipt.total_amount` at all times; surplus is held as customer credit.
- Credit-limit check on credit-invoice posting is enforced in this module (not in `party`): `customer.open_debt + invoice.total_amount ≤ customer.credit_limit_amount`.
- Per-line discount cannot push unit price below `item.min_sell_price` without override.
- Cumulative `packing_list_line.qty` across all non-cancelled packing lists for a `sales_invoice_line` must not exceed that line's `qty`.

### Credit-limit override (Slice C)

The credit-limit gate runs at POST time on CREDIT-terms invoices. When the
caller holds `SALES_INVOICE.OVERRIDE_CREDIT` and the post body carries a
non-blank `overrideReason`, the post proceeds and stamps three columns on
`sales_invoice`: `credit_override=true`, `credit_override_by=<actorId>`,
`credit_override_reason=<reason>`. The same triplet is mirrored on the
`SalesInvoicePosted.v1` payload so the deferred debt module can audit the
override without re-reading the row. Draft creation always enforces the
limit unconditionally; the override branch is post-time only. The
zero-credit-limit branch ("customer has no credit configured") is NOT
overridable per the locked Slice C decision.

### Reprint audit (Slice C)

`POST /api/v1/sales-invoices/uid/{uid}/reprint` increments
`sales_invoice.reprint_count` and emits `SalesInvoiceReprinted.v1` carrying
the `ReprintReason` enum + optional notes + the actor id + reprintCount.
Pure audit — no state mutation beyond the counter. Permission:
`SALES_INVOICE.REPRINT`. Eligible source statuses are POSTED / PARTIALLY_PAID
/ PAID / VOIDED — DRAFT / CANCELLED reprints are rejected with
`IllegalStateException` (400 via `GlobalExceptionHandler`).

Multi-tenant: every row carries `company_id` and `branch_id` (`sales_invoice_line`
and `receipt_allocation` inherit the tenant from their parent header — see
GAP 1.A). `customer` is company-scoped (DATA-MODEL §6 + party module). All
queries filter by tenant via a request-scoped interceptor.

Idempotency: state-changing endpoints accept `Idempotency-Key`; the application layer caches the outcome for 24h keyed by `(tenant, key)`.

Outbox: domain events written to `outbox` in the same DB transaction as the aggregate change; a separate publisher relays them. `stock` consumes invoice/return events to write `stock_move` rows — this module does not touch `stock_move` directly.
