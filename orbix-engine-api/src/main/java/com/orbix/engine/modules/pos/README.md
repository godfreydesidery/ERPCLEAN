# POS module

## 1. Purpose

`pos` is the backend module that the Flutter desktop POS application (`orbix-engine-pos`) talks to. The Flutter client is offline-first: it sells from local SQLite, queues every till operation in a local outbox, and pushes batches to this module when the network returns. The backend's job is to accept those finalised operations, validate them against server-authoritative state (business day open, till session open, totals consistent), persist them durably, allocate server identities, and emit domain events so other modules (stock, cash, debt) can post their side of the transaction.

`pos_sale` is the till receipt and is a sibling of `sales_invoice`, not a subset — it carries simpler shape and offline-origin semantics (client-allocated number, `client_op_id`, `sale_at` vs `server_at`). Both shapes feed the same downstream postings.

## 2. Scope

In scope: `till`, `till_session`, `pos_sale`, `pos_sale_line`, `pos_payment`, `cash_pickup`, `petty_cash`, the batch sync-push endpoint, X-report and Z-report generation, and the till-namespaced receipt sequence.

Out of scope (deliberately delegated):

- Cashier identity, password login, supervisor PIN issue and verification — `platform.security` (US-IAM-001, US-IAM-010 issue the short-lived supervisor authorisation token that the cashier presents on void / discount / cash pickup / petty cash).
- Item lookup, barcode resolution, price-list pricing, active promotions — `catalog` (read-only consumer; POS sync relays the cached snapshot to the Flutter client).
- Customer at till (walk-in or named) — `party` (read-only; POS references `customer_id`, including the synthetic `WALK-IN-BR-*` party per branch).
- Stock balance writes and pre-sale on-hand check — `stock` (read-only check; balance writes happen in `stock` as a consumer of `PosSaleClosed.v1`).
- Cash banking, bank deposit posting, GL classification of petty-cash categories — `cash` (consumes `CashPickupRecorded.v1` and `PettyCashPaid.v1`).
- Fiscal printer hardware drivers — client-side concern; backend only stores the returned signature token on `pos_sale.fiscal_signature`.
- Offline outbox transport, retry, backoff — Flutter client owns the wire. The backend only sees finalised operations with a `client_op_id` and processes them idempotently.

## 3. Domain model

- `till` — physical workstation + cash drawer + printer at a branch. Carries `install_id`, `default_price_list_id`, `status`. Unique `code` per branch (e.g. `TILL-1`).
- `till_session` — a cashier's shift on a till. Bounds float, sales, pickups, petty cash, and the close. Holds `business_date`, `opening_float_amount`, computed `expected_cash_amount`, `declared_cash_amount`, `variance_amount`, `z_report_object_key`. Status `OPEN`, `CLOSED`, `RECONCILED`.
- `pos_sale` — a single till transaction. Client-namespaced `number` (`TILL-3-20260513-00027`), `client_op_id` UUID for idempotency, dual timestamps `sale_at` (client) and `server_at`. Status `POSTED` or `VOIDED` — never `DRAFT` (the client commits locally before push).
- `pos_sale_line` — mirrors `sales_invoice_line` shape (item, pack qty, unit price, discount, tax, line total).
- `pos_payment` — mixed tender. Method ∈ `CASH`, `CARD`, `MOBILE_MONEY`, `VOUCHER`, `STORE_CREDIT`. Carries `reference`, `terminal_id`, `last4`. PAN is never stored.
- `cash_pickup` — mid-shift cash removed to the safe. Supervisor-authorised; decrements expected drawer cash.
- `petty_cash` — small payouts from the drawer (transport, office, maintenance, other). May be tied to a `till_session` or paid from the main cash book (`till_session_id` nullable).

See DATA-MODEL.md §7 for full attribute tables.

## 4. Key business flows

The canonical PRD §6.1 flow "Open Till → Cashier Sale → Close Till" is the spine of this module:

1. **Open till session** — cashier presents credentials, picks an assigned `till`, declares opening float. Backend rejects if the branch's `business_day` is not `OPEN`, or if another `OPEN` `till_session` already exists for that till (US-POS-002). Emits `TillSessionOpened.v1`.
2. **Add line** — scan barcode or typeahead, resolve to `item` + `pack_qty`, append or increment a cart line. Cart lives entirely client-side until tender. The backend only sees the finalised line set when the sale arrives. Lines are idempotent via the parent sale's `client_op_id`.
3. **Mixed-tender payment** — cash + card + mobile money + voucher + store credit in one sale (US-POS-009). Backend validates `sum(pos_payment.amount) == pos_sale.total_amount`; change is `tendered − total` when positive.
4. **Hold / recall cart** — entirely client-state. The server only ever sees a closed sale; held carts never reach the backend.
5. **Void line** — within the open `till_session`, after partial tender, requires supervisor authorisation. A voided sale (US-POS post-close correction) emits `PosSaleVoided.v1`; downstream modules reverse their postings idempotently.
6. **Cash pickup** — supervisor records mid-shift drawer withdrawal to safe; emits `CashPickupRecorded.v1`.
7. **Petty cash payment** — supervisor records a payout with category and optional receipt attachment; emits `PettyCashPaid.v1`.
8. **X-report** — mid-shift snapshot. Read-only; does not change `till_session` state.
9. **Z-report / close session** — cashier counts cash, declares `declared_cash_amount`. Backend computes `expected_cash_amount = float + cash sales + cash receipts − pickups − petty cash`, derives `variance_amount = declared − expected`, requires supervisor authorisation if variance exceeds threshold (US-POS-016), generates the Z-report PDF to object storage, and sets `status = CLOSED`. Emits `TillSessionClosed.v1` carrying the variance.

## 5. Module interactions

Depends on:

- `party` — cashier user, supervisor user, customer-at-till (including the synthetic walk-in party).
- `catalog` — item, pack, price list, active promotion (read-only).
- `stock` — pre-sale on-hand check (read-only).
- `day` — sale must reference a `till_session` whose `business_date` matches the branch's `OPEN` `business_day`.
- `platform.security` — JWT, supervisor PIN, short-lived supervisor authorisation token.
- `platform.audit` — every till write emits an audit event via the audit aspect.
- `platform.events` — transactional outbox for all published events below.

Publishes (versioned, via transactional outbox in the same DB transaction as the business write):

- `TillSessionOpened.v1`
- `PosSaleClosed.v1` — consumed by `stock` (decrement on-hand) and `debt` (allocate against store credit / voucher tenders); cash tender contributes to drawer expected.
- `PosSaleVoided.v1` — reverse postings.
- `CashPickupRecorded.v1` — consumed by `cash` (drawer → safe transfer).
- `PettyCashPaid.v1` — consumed by `cash` (drawer → expense category).
- `TillSessionClosed.v1` — carries `expected_cash_amount`, `declared_cash_amount`, `variance_amount`; consumed by `cash` (drawer → cash book) and `day` (precondition for end-of-day branch close).

Consumes: catalog item, price-list, and active-promotion changes. The server caches the resolved snapshot and relays it to clients on the next sync pull (delta or full).

## 6. API surface

Base path `/api/v1`. All writes accept a `clientOpId` and are idempotent.

- `GET|POST /api/tills` — till master CRUD (admin).
- `GET|POST /api/till-sessions`, `POST /api/till-sessions/{id}/close` — open and close.
- `GET|POST /api/pos-sales`, `POST /api/pos-sales/{id}/void` — sale create and post-close void.
- `POST /api/pos-payments` — typically embedded in the sale create payload; standalone endpoint for reconciliation tools only.
- `POST /api/cash-pickups`.
- `POST /api/petty-cash`.
- `GET /api/reports/x-report?tillSessionId=...` — mid-shift, read-only.
- `GET /api/reports/z-report?tillSessionId=...` — generated at close, served from object storage thereafter.
- `POST /api/v1/sync/push` — the batch-submit endpoint from `ARCHITECTURE.md` §2.9. Body `{ clientId, lastAckId, ops: [SyncOp...] }`; response `{ ackedClientOpIds, conflicts, serverOps }`. The Flutter outbox pushes till-session, sale, payment, pickup, petty-cash, and close ops through here in FIFO order.

## 7. Persistence

Flyway scripts live under `db/migration/common/` as `V<N>__pos_<purpose>.sql` (e.g. `V020__pos_till.sql`, `V021__pos_till_session.sql`, `V022__pos_sale.sql`, `V023__pos_payment.sql`, `V024__pos_cash_pickup.sql`, `V025__pos_petty_cash.sql`). No dialect-specific scripts at MVP; standard column types only (`BIGINT`, `VARCHAR`, `TIMESTAMP`, `DECIMAL(18,4)`, `DATE`, `TEXT`).

Receipt numbers are per-till sequences namespaced by date — `TILL-{tillId}-{yyyymmdd}-{nnnnn}` — allocated by the client (offline-safe; no server round-trip required). Uniqueness is enforced at the DB by a unique index on `pos_sale.number` and on `pos_sale.client_op_id`. All money columns are `DECIMAL(18,4)`; variance is signed.

## 8. User stories

P1 (MVP):

- **US-POS-001** Log in to POS (password) — `platform.security` boundary; pos module is the consumer.
- **US-POS-002** Open a till session.
- **US-POS-003** Add an item by barcode.
- **US-POS-004** Add an item by typeahead.
- **US-POS-005** Apply line or header discount (supervisor PIN above threshold).
- **US-POS-006** Void a line in the cart.
- **US-POS-007** Hold the current cart (client-only).
- **US-POS-008** Recall a held cart (client-only).
- **US-POS-009** Mixed-tender payment.
- **US-POS-010** Print a receipt (client hardware; backend tolerates printer-offline).
- **US-POS-012** Reprint the last receipt.
- **US-POS-013** Record a cash pickup.
- **US-POS-014** Pay petty cash from the till.
- **US-POS-016** Close a till session.
- **US-POS-017** Sell while offline.
- **US-POS-018** Sync queued operations on reconnect.

P2:

- **US-POS-011** Fiscal printer signature.
- **US-POS-015** Mid-shift X-report.

## 9. Open questions

PRD §13 and DATA-MODEL.md §16 POS-affecting items pending domain-owner confirmation:

- **Returns at POS policy** — whether a `pos_sale` can be reversed at the till (cash refund from drawer) or only via a back-office customer return / credit note. Affects whether this module owns a return endpoint or defers to `sales`.
- **Fiscal-printer integration scope** — which regions are in MVP, which deployment-config flag drives driver selection, and whether a fiscal-driver failure blocks the sale or only flags it (US-POS-011 AC is region-dependent).
- **In-app card processing** — default is tokenised via certified terminals (PCI-reduced scope per PRD §11). If a deployment opts in to in-app card capture, what additional schema and controls are required on `pos_payment`.
- **Walk-in customer modelling** — confirmed approach is one synthetic `customer` per branch (`WALK-IN-BR-*`); pos depends on `party` providing it.
- **Negative-stock policy at till** — application-level via `STOCK.OVERSELL` permission; pos must surface the block or override consistently.

## 10. Implementation notes

**Layering.** Hexagonal (`api`, `app`, `domain`, `infra`) per ARCHITECTURE.md §2.2 — pos is a core module.

**Invariants** (enforced in `domain` and by DB constraints):

- At most one `OPEN` `till_session` per `till` at any time (unique partial index on `(till_id) WHERE status = 'OPEN'`).
- A `pos_sale` references a `till_session` that is `OPEN` and whose `business_date` matches the branch's currently-open `business_day`.
- `sum(pos_payment.amount) == pos_sale.total_amount` for every posted sale; `change_amount = max(0, tendered − total)`.
- `cash_pickup.amount` ≤ current drawer cash balance for the session (float + cash sales − prior pickups − petty cash paid in cash).
- A `CLOSED` `till_session` is immutable; subsequent corrections issue compensating events, never UPDATE.
- `pos_payment.last4` may be stored; the PAN never is. Card tenders without a `reference` (approval code) are rejected.

**Multi-tenant.** Every row carries `company_id` + `branch_id`; `pos_sale` and dependents additionally carry `till_id` (via the `till_session`). The `RequestContext` filter from `platform` injects the company/branch predicate; the till is resolved from the `install_id` claim on the POS client's JWT or the `X-Till-Id` header.

**Idempotency.** Every write (sale, payment, pickup, petty cash, close) carries a `client_op_id` (UUID v7). The backend persists it on the row and a unique index makes replay a no-op — the second arrival of the same `client_op_id` returns the original server response. Receipt numbers are namespaced per till so two offline tills never collide, even mid-partition.

**Outbox events in same transaction.** Per ARCHITECTURE.md §2.10, `events.publish()` inserts into `domain_event` in the same DB transaction as the business write. Consumers — `stock` for on-hand decrement, `cash` for drawer-to-cash-book transfer on close, `debt` for store-credit and voucher tender allocation — are idempotent and key off the source aggregate (`pos_sale_id`, `cash_pickup_id`, `petty_cash_id`, `till_session_id`).

**Sync protocol.** The server accepts batched ops with monotonic client timestamps. Within a single `till_session`, ops must arrive in `sale_at` order; an out-of-order op on a session is rejected with a conflict response and the client is expected to re-order and replay. Across sessions, ops are independent. The push endpoint returns the list of acknowledged `clientOpId`s and any conflicts; server-authoritative state for the till (current session, item-price snapshot) flows back on the next pull. Conflict policy per ARCHITECTURE.md §2.9: sales never conflict (client-namespaced numbers), stock is server-authoritative, master-data edits resolve last-write-wins per field with audit.

---

## 11. Phase 1.1 additions

See [docs/design/PHASE-1.1-ADDITIONS.md](../../../../../../../../docs/design/PHASE-1.1-ADDITIONS.md).

### Section dimension (REQUIRED)

- `till.section_id`, `pos_sale.section_id`, `pos_sale_line.section_id` all REQUIRED.
- A till is assigned to one section. Every sale from that till is stamped with the till's section.
- Sections must exist before tills can be created — `admin` module owns the master.

### Refund at till

- `pos_sale.kind` ∈ `SALE` / `REFUND` / `NO_SALE`.
- `pos_sale.refunded_from_sale_id` — self-FK on refund-kind rows.
- A refund row has negative-qty `pos_sale_line` rows and negative-amount `pos_payment` rows.
- Policy (locked): same-day + receipt → cashier up to threshold (company-config, default UGX 50,000); above → manager PIN. Same-day no receipt → manager PIN always. Past business day → refuse; route to back-office `customer_return`.
- Permissions: `POS.REFUND` (cashier), `POS.REFUND_OVERRIDE_THRESHOLD` (manager).
- Cash refund posts a `cash_entry` OUT on TILL via the `cash` module consumer.

### Foreign currency at till

- `pos_payment` gains `tender_currency` (REQUIRED), `tender_amount` (DECIMAL — in tender currency), `fx_rate_snapshot` (DECIMAL(20,8)).
- Existing `amount` column is re-interpreted as the **functional-currency-converted amount**.
- A till declares accepted foreign currencies via `till_currency` (admin module).
- POS validates: payment currency ∈ functional ∪ `till_currency` set; FX rate fetched from `fx_rate` (most recent ≤ `sale_at`).
- Close-till variance computed **per currency**.
- Permission: `POS.FX_TENDER`.

### Gift card tender

- New `pos_payment.method` value: `GIFT_CARD`.
- POS calls `giftcard` redeem endpoint with `gift_card.code`, the requested amount, and the `pos_sale.id`.
- Redeem creates a `gift_card_txn` of kind `REDEEM`. No `cash_entry` is posted (gift card is liability, not cash).
- Voiding a gift-card-tendered sale → `giftcard.GiftCardRefunded.v1` event credits the card back.

### Weighed items / embedded barcodes

- POS client decodes EAN-13 embedded-weight barcodes locally: leading digit `2`, PLU bytes 2..7, weight bytes 8..12, check byte 13.
- Decoded line carries `qty` in the item's `weighing_unit` (KG / G / L / ML). Backend trusts client.
- `pos_sale_line.qty` accepts DECIMAL(18,4); pricing is `unit_price × qty`.

### FEFO batch consumption

- For items with `tracks_batches = true`, POS must resolve a `stock_batch.id` per line at scan time.
- Offline client picks the earliest-expiry ACTIVE batch from its local snapshot; backend validates on sync and may correct (issues a compensating move + flag) if a more recent batch landed at HQ.
- `pos_sale_line.batch_id` populated for batch-tracked lines.

### Staff price tier on employee badge

- Cashier scans employee badge → POS resolves `employee.staff_price_list_id` and applies that price list to all items in the cart.
- `pos_sale.is_staff_purchase = true` (or via a flag on the cashier_id metadata).
- The resulting `stock_move` carries `move_type = STAFF_PURCHASE` for reporting.

### Layby / pre-order collection

- `orders` module emits `OrderCollected.v1` → POS consumes and creates a `pos_sale` from the collected order's lines; stock module flips the RESERVED move to a SALE move.
- POS receipt references the order number (`ORD-BR1-...`).

### New events emitted

- `PosSaleRefunded.v1`, `PosRefundOverrideApplied.v1`
- `PosFxTenderUsed.v1`
- `PosGiftCardRedeemed.v1` (relay of `giftcard.GiftCardRedeemed.v1`)
- `PosStaffPurchasePosted.v1`
