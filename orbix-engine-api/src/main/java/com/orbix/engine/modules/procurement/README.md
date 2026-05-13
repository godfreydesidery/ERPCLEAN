# Procurement module

## 1. Purpose

The procurement module owns the **inbound goods lifecycle**: the documents and state transitions that take an intent-to-buy through to a posted receipt and a settled supplier payable. Its canonical flow is:

`purchase_quotation` → `lpo_order` → `grn` → `supplier_invoice` → supplier payment

It also covers the reverse path — `vendor_return` → `vendor_credit_note` — and the matching/allocation that keeps the supplier sub-ledger consistent.

Procurement is the **command side** of the inbound flow. It does not own stock balances, supplier cash movements, or supplier ageing reports; it produces the events those modules consume.

## 2. Scope

In scope:
- Purchase quotation (RFQ) issuing, multi-supplier comparison, and conversion to LPO.
- LPO lifecycle: draft, approval (threshold-driven), partial/full receipt, cancellation.
- GRN posting against an LPO or — under permission — directly.
- Three-way match: LPO ↔ GRN ↔ `supplier_invoice` via the `supplier_invoice_grn` junction.
- Vendor returns and the credit notes they trigger; allocation of credit notes against open `supplier_invoice` rows.
- Approval state machine and immutability of posted documents.

Out of scope (owned elsewhere):
- Supplier party data, contact details, payment terms, credit limit — **party module**.
- Stock-on-hand writes, `stock_move` rows, average-cost recomputation on GRN post — **stock module** (consumes `GrnPosted.v1`).
- Supplier cash movement, bank/till postings, multi-tender supplier payments — **cash module** (consumes `SupplierPaymentRecorded.v1`; emits the actual `cash_movement`).
- Supplier ageing buckets, payables ledger reports, statement generation — **reporting module** (later phase).
- Catalogue items, UoMs, VAT groups — **catalog module**.

## 3. Domain model

Tables owned by this module (see DATA-MODEL.md §5 for full column definitions):

| Aggregate | Tables | Notes |
|---|---|---|
| Quotation | `purchase_quotation`, `purchase_quotation_line` | RFQ root. Status: `DRAFT`, `SENT`, `RECEIVED`, `CONVERTED`, `EXPIRED`, `CANCELLED`. |
| LPO | `lpo_order`, `lpo_order_line` | Authoritative purchase order. `received_qty` aggregated from GRN lines. Status: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED`. |
| GRN | `grn`, `grn_line` | Receiving event. `lpo_order_id` NULL = direct GRN. Status: `DRAFT`, `POSTED`, `CANCELLED`. |
| Supplier invoice | `supplier_invoice`, `supplier_invoice_grn` | Junction is many-to-many (an invoice can cover several GRNs; a GRN can be split across invoices). Status: `DRAFT`, `POSTED`, `PARTIALLY_PAID`, `PAID`, `CANCELLED`. |
| Vendor return | `vendor_return`, `vendor_return_line` | Reverse stock event. Reason: `DAMAGED`, `EXPIRED`, `WRONG_ITEM`, `OVERSUPPLY`, `OTHER`. |
| Credit note | `vendor_credit_note` | Supplier's confirmation of payable reduction; allocated against open `supplier_invoice` rows via `allocated_amount`. |

The supplier sub-ledger (`debt_entry` rows for the supplier side) is **not** in this package — it lives with the debt module. Procurement raises the events that open and close those entries.

## 4. Key business flows

These are the canonical happy-paths the module must support. PRD §6.2 (LPO → GRN → Stock & Payable) is the controlling reference.

### 4.1 Quotation → multi-supplier compare → LPO
1. Merchandiser drafts a `purchase_quotation` per supplier for the same item set.
2. System sends/exports each quotation (status: `SENT`).
3. Quotes return; merchandiser records prices (status: `RECEIVED`) and compares side-by-side.
4. Best quote is **converted** to an `lpo_order` (quotation status → `CONVERTED`).

### 4.2 LPO approval
1. LPO submitted from `DRAFT` → `PENDING_APPROVAL`.
2. Auto-approval if `total_amount` ≤ configured branch threshold; otherwise requires `LPO.APPROVE` permission.
3. On approval: `status` → `APPROVED`, `approved_by` + `approved_at` set, document becomes immutable except for cancellation.
4. Rejection requires a reason; LPO returns to `DRAFT` for edit or is `CANCELLED`.

### 4.3 GRN against LPO (partial or full) — PRD §6.2 canonical flow
1. Storekeeper opens GRN against an `APPROVED` (or `PARTIALLY_RECEIVED`) LPO.
2. Each `grn_line` defaults to `lpo_order_line.ordered_qty − received_qty`; partial values allowed; over-receipt rejected.
3. On post:
   - GRN `status` → `POSTED`, `posted_at` / `posted_by` set.
   - `lpo_order_line.received_qty` incremented; LPO status moves to `PARTIALLY_RECEIVED` or `RECEIVED`.
   - `GrnPosted.v1` emitted → **stock module** writes `stock_move` rows, recomputes `item_branch_balance.avg_cost` and `last_cost`.
   - `GrnPosted.v1` also opens a GRN-bounded supplier `debt_entry` (consumed by debt module).

### 4.4 Direct GRN (no LPO)
- Allowed only with `GRN.DIRECT` permission (supervisor-level). `grn.lpo_order_id` is NULL.
- Same stock and payable consequences; rejected if business day is closed (see day module).

### 4.5 Supplier invoice match (three-way)
1. Accountant creates `supplier_invoice` and links one-or-more GRNs through `supplier_invoice_grn(amount)`.
2. System warns if `total_amount` differs from sum of covered GRN totals beyond configurable tolerance.
3. On post: GRN-bounded supplier debt closes; invoice-bounded supplier debt opens with `due_date` derived from supplier `payment_terms_days`. Emits `SupplierInvoiceMatched.v1`.

### 4.6 Pay supplier
- Records a supplier payment intent and allocates against one or more open `supplier_invoice` rows; updates `paid_amount` and `status` (`PARTIALLY_PAID` / `PAID`).
- Emits `SupplierPaymentRecorded.v1` → **cash module** writes the cash-side `cash_movement` and (if used) `till_session` impact.

### 4.7 Vendor return → credit note → allocation
1. `vendor_return` posted against an `original_grn_id` (or direct); emits a reverse stock event (consumed by stock module).
2. Supplier issues a `vendor_credit_note`; recorded against the return (`vendor_return.status` → `CREDITED`).
3. Credit note `allocated_amount` is incremented as it is applied to open `supplier_invoice` rows; emits `VendorCreditIssued.v1`.

### 4.8 Cancel
- LPO cancellation: only if no posted GRN draws against it. Status → `CANCELLED`; document immutable thereafter.
- Posted GRN cancellation (`GRN.CANCEL` + reason): issues a **compensating** GRN event (reverse `stock_move`, reverse `debt_entry`) — the original row is never deleted (US-PROC-012).

## 5. Module interactions

**Depends on:**
- `party` — `supplier_id`, supplier payment terms.
- `catalog` — `item_id`, `uom_id`, `vat_group_id`.
- `day` — posting (`GRN.POST`, `supplier_invoice` post, supplier payment) is rejected if the target `business_date` is not in an open business day for the branch.
- `platform` — sequence service, outbox, audit, approvals.

**Publishes (domain events, all v1, written to outbox in the same transaction):**
- `PurchaseQuotationRaised.v1`
- `LpoOrderApproved.v1`
- `GrnPosted.v1` (carries `grn_id`, branch, lines with item/qty/unit_cost — consumed by stock and debt)
- `SupplierInvoiceMatched.v1`
- `SupplierPaymentRecorded.v1` (consumed by cash and debt)
- `VendorCreditIssued.v1`

**Consumes:** none in MVP (procurement is a source module). It does subscribe to nothing for its own state transitions.

## 6. API surface

REST endpoints (all under `/api`, JWT-secured, permission-checked):

| Resource | Endpoints |
|---|---|
| Purchase quotations | `GET/POST /api/purchase-quotations`, `GET/PUT /api/purchase-quotations/{id}`, `POST /api/purchase-quotations/{id}/send`, `POST /api/purchase-quotations/{id}/convert-to-lpo` |
| LPO orders | `GET/POST /api/lpo-orders`, `GET/PUT /api/lpo-orders/{id}`, `POST /api/lpo-orders/{id}/submit`, `POST /api/lpo-orders/{id}/approve`, `POST /api/lpo-orders/{id}/reject`, `POST /api/lpo-orders/{id}/cancel`, `GET /api/lpo-orders/{id}/pdf` |
| GRNs | `GET/POST /api/grns`, `GET/PUT /api/grns/{id}`, `POST /api/grns/{id}/post`, `POST /api/grns/{id}/cancel` |
| Supplier invoices | `GET/POST /api/supplier-invoices`, `POST /api/supplier-invoices/{id}/match`, `POST /api/supplier-invoices/{id}/post`, `POST /api/supplier-invoices/{id}/cancel` |
| Vendor returns | `GET/POST /api/vendor-returns`, `POST /api/vendor-returns/{id}/post`, `POST /api/vendor-returns/{id}/cancel` |
| Vendor credit notes | `GET/POST /api/vendor-credit-notes`, `POST /api/vendor-credit-notes/{id}/allocate` |

Approval-related endpoints (`/submit`, `/approve`, `/reject`) delegate to the platform approval state machine; they do not contain procurement-specific logic beyond mapping permissions.

## 7. Persistence

- Migrations live in `orbix-engine-api/src/main/resources/db/migration/common/` as `V<N>__procurement_<purpose>.sql`. Examples: `V0050__procurement_lpo.sql`, `V0051__procurement_grn.sql`, `V0052__procurement_supplier_invoice.sql`.
- Per-branch document numbers (LPO, GRN, supplier_invoice internal `number`, vendor_return, vendor_credit_note) are allocated through the platform sequence service. Pattern: `LPO-<BRANCH_CODE>-000123` (e.g. `LPO-DSM-000124` per DATA-MODEL.md §5.3). Gaps on rollback are accepted (DATA-MODEL.md §16 item 5).
- `UNIQUE(company_id, branch_id, number)` per document table.
- All amounts `DECIMAL(18,4)`; all FKs hard, no orphan rows tolerated.

## 8. User stories

P1 (MVP):
- **US-PROC-002** — Raise an LPO.
- **US-PROC-003** — Approve or reject an LPO.
- **US-PROC-004** — Receive against an LPO (GRN).
- **US-PROC-006** — Match a supplier invoice to one or more GRNs.
- **US-PROC-007** — Pay a supplier.
- **US-PROC-011** — Print or email an LPO.
- **US-PROC-012** — Cancel an LPO or GRN.

P2:
- **US-PROC-005** — Direct GRN (no LPO).
- **US-PROC-008** — Return goods to a vendor.
- **US-PROC-009** — Receive and allocate a vendor credit note.
- **US-PROC-010** — Multi-supplier RFQ (purchase_quotation).

(Supplier party creation `US-PROC-001` is owned by the party module.)

## 9. Open questions

From PRD §13 and DATA-MODEL.md §16, the items that affect procurement design:

- **Party reuse** (DM §16.1) — can a single `party_id` back both a `customer` and a `supplier`? Affects supplier lookup and de-duplication on supplier creation.
- **Cost on GRN post** (DM §16.2) — per-branch `avg_cost` is kept; confirm we never re-cost retroactively when a later GRN lands at a different unit_cost (we don't — only forward-looking moving average).
- **LPO auto-approval threshold** (PRD §13) — single global, per-company, or per-branch? MVP assumes per-company; confirm.
- **Three-way match tolerance** (US-PROC-006) — percentage, absolute, or both? Configuration location?
- **Direct GRN policy** — should this be allowed on closed business days under a higher permission, or never?
- **Number sequence gaps** (DM §16.5) — accepted for procurement documents; document this for auditors.

## 10. Implementation notes

**Architectural style.** Hexagonal: domain aggregates (`LpoOrder`, `Grn`, `SupplierInvoice`, `VendorReturn`) hold invariants and emit domain events; JPA adapters under `infra/persistence`; REST adapters under `api/rest`. No JPA annotations on aggregate roots.

**Invariants (enforced in domain, not at SQL):**
- `lpo_order_line.received_qty` is monotonically non-decreasing and never exceeds `ordered_qty`.
- GRN against an `APPROVED` or `PARTIALLY_RECEIVED` LPO only; GRN against a `CANCELLED` LPO is rejected.
- `(supplier_invoice_id, grn_id)` is unique in `supplier_invoice_grn`.
- A `POSTED` GRN is immutable — corrections happen through cancellation (compensating event) or `vendor_return`, never in-place edits.
- `supplier_invoice.paid_amount` ≤ `total_amount`.
- `vendor_credit_note.allocated_amount` ≤ `total_amount`.

**Multi-tenant scoping.** Every row carries `company_id` and `branch_id`. Repositories filter by the request's `company_id` via the platform `TenantContext`. Suppliers are company-scoped; cross-company supplier reuse is out of scope for MVP.

**Approval state machine** (LPO and supplier invoice):
`DRAFT` → `SUBMITTED` (a.k.a. `PENDING_APPROVAL` for LPO) → `APPROVED` | `REJECTED` → `CANCELLED`. Transitions go through the platform approval service so permission checks, audit, and reason capture are uniform.

**Idempotency.** All `POST` and state-transition endpoints accept an `Idempotency-Key` header; duplicates within the configured window return the original response.

**Outbox.** Every domain event is appended to the platform outbox **in the same transaction** as the state change. Asynchronous dispatch is the platform's responsibility; this module never publishes directly.

**Posting day check.** Before any `*.post` operation, the module asks the day module whether the target `business_date` for the branch is open; if not, the call is rejected with `BUSINESS_DAY_CLOSED`.
