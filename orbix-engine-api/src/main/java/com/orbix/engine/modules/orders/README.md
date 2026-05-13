# Orders module

## 1. Purpose

`orders` owns **layby and pre-order** customer orders — agreements where the customer reserves goods now and pays / collects later. It is distinct from `sales_invoice` (no debt opens; ownership doesn't transfer until collection) and from `pos_sale` (no till receipt yet).

Two flavours, one aggregate:
- **Layby** — instalment purchase: customer pays a deposit, item is reserved (out of available stock), customer pays in instalments, takes possession on full payment.
- **Pre-order** — production-tied: custom cake, party platter, large fruit order. Customer pays a deposit, production module schedules the work, customer collects on the agreed date.

## 2. Scope

In scope:
- `customer_order` aggregate (header + lines + payment ledger).
- Lifecycle: `DRAFT` → `RESERVED` → `DEPOSIT_PAID` → `PARTIALLY_PAID` → `READY` → `COLLECTED`. Branches to `CANCELLED` / `EXPIRED`.
- Stock reservation when status becomes `RESERVED` (issues a `stock_move` of type `RESERVED` against the relevant branch).
- Deposit + instalment receipts (`customer_order_payment`) that book a `cash_entry` via the `cash` module.
- Auto-expiry of `RESERVED` / `DEPOSIT_PAID` orders past `reserved_until` (configurable per type, default 30 days).
- Conversion to `pos_sale` (cash collection at till) or to `sales_invoice` (credit) on collection.
- Optional production trigger for `PRE_ORDER` type (creates a `production_batch` once deposit is paid).

Out of scope:
- Stock ledger writes — `stock` owns `stock_move`; orders emits events.
- Cash side of payments — `cash` writes `cash_entry`; orders emits events.
- Customer master — `party`.
- Production batch lifecycle — `production`.

## 3. Domain model

| Table | Purpose |
|---|---|
| `customer_order` | Header: type (LAYBY / PRE_ORDER), customer, status, deposit required, deposit paid, total, balance, reserved_until. Per-branch number (`ORD-BR1-000123`). Optional `section_id` for production-tied pre-orders. |
| `customer_order_line` | Item, qty, unit_price snapshot, discount, line total. |
| `customer_order_payment` | Deposit + instalment receipts. Method, amount, FK to `cash_entry` (cash-side bookkeeping). |

See [DATA-MODEL.md §13.1-13.3](../../../../../../../../DATA-MODEL.md) (Phase 1.1 additions).

## 4. Key business flows

1. **Create layby (US-ORD-001).** Customer agrees to buy 3 items totalling 200,000 UGX. Cashier or back-office user creates the order: lines, total, `deposit_required_amount` (configurable %, default 30%), `reserved_until` (e.g. +60 days). Customer pays deposit → `customer_order_payment` row; cash module writes `cash_entry`; status moves `DRAFT` → `DEPOSIT_PAID`. Stock module reserves the qty via `stock_move` of type `RESERVED`. Emits `LaybyCreated.v1`.
2. **Pay an instalment (US-ORD-002).** Customer returns with a payment. `customer_order_payment` row added; `balance_due` decreases; status updates to `PARTIALLY_PAID`.
3. **Collect on completion (US-ORD-003).** When `balance_due = 0` (or customer pays the balance at collection), order moves to `READY`. Cashier scans the order at the till → POS creates a `pos_sale` against the order (gathered from `customer_order_line` rows), reservation flips to a regular `SALE` `stock_move`, status moves to `COLLECTED`. Emits `LaybyCollected.v1`.
4. **Create pre-order (US-ORD-004).** Customer orders a custom cake for next Saturday. Same `customer_order` lifecycle, type = `PRE_ORDER`. On `DEPOSIT_PAID`, production module is triggered (`ProductionRequested.v1` consumed by `production` which creates a `PLANNED` `production_batch`). When production posts output, order status moves to `READY`.
5. **Cancel (US-ORD-005).** Cashier / customer cancels. Reservation released (compensating `stock_move`). Deposit refunded per policy (full / partial / non-refundable — company config). Status `CANCELLED`. Emits `OrderCancelled.v1`.
6. **Expire (US-ORD-006).** Scheduled job finds orders past `reserved_until` still in `RESERVED` / `DEPOSIT_PAID` / `PARTIALLY_PAID`. Per policy: notify customer first, then move to `EXPIRED`, release reservation, retain deposit as forfeit (configurable). Emits `OrderExpired.v1`.

## 5. Module interactions

**Depends on:**
- `party` — `customer_id`.
- `catalog` — `item_id` references and price-list resolution.
- `stock` — read-only availability check at order time; emits `stock_move` reservation via event.
- `cash` — `customer_order_payment` triggers `cash_entry` rows.
- `production` — pre-orders trigger production batches.
- `admin` — `branch_id`, `section_id`.
- `day` — order operations must be in an open business day.
- `common` — outbox, audit.

**Publishes events:**
- `LaybyCreated.v1` / `PreOrderCreated.v1`
- `OrderDepositPaid.v1` (consumed by `cash`, `production`)
- `OrderInstallmentPaid.v1` (consumed by `cash`)
- `OrderReady.v1`
- `OrderCollected.v1` (consumed by `pos` to create the `pos_sale`, by `stock` to flip reservation → sale)
- `OrderCancelled.v1` (consumed by `stock` to release reservation, by `cash` to refund deposit per policy)
- `OrderExpired.v1`
- `ProductionRequested.v1` (for `PRE_ORDER` type only; consumed by `production`)

**Consumes events:**
- `ProductionOutputPosted.v1` (from `production`) — if the batch was tied to a pre-order, advance the order to `READY`.

## 6. API surface

| Resource | Endpoints |
|---|---|
| `/api/v1/orders` | `GET` (filter by status / type / customer), `POST` (create) |
| `/api/v1/orders/{id}` | `GET`, `PATCH` (edit lines while `DRAFT` only) |
| `/api/v1/orders/{id}/reserve` | `POST` — moves `DRAFT` → `RESERVED`; emits stock reservation |
| `/api/v1/orders/{id}/payments` | `POST` — record a deposit / instalment / final payment |
| `/api/v1/orders/{id}/cancel` | `POST` — releases reservation; refund per policy |
| `/api/v1/orders/{id}/collect` | `POST` — triggered from POS at collection time |

## 7. Persistence

- Flyway: `V6__orders.sql` under `common/`.
- Sequences: `customer_order_seq`, `customer_order_line_seq`, `customer_order_payment_seq` (in `V1_3__phase11_sequences.sql`).
- Per-branch number sequence for `customer_order.number` (`ORD-BR1-...`).
- Money columns `DECIMAL(18,4)`.

## 8. User stories

**P1:**
- US-ORD-001 — Create a layby
- US-ORD-002 — Pay an instalment toward a layby
- US-ORD-003 — Collect a fully-paid layby
- US-ORD-004 — Create a pre-order (production-tied)
- US-ORD-005 — Cancel an order with deposit-refund policy applied
- US-ORD-006 — Auto-expire abandoned orders

**P2:**
- US-ORD-007 — List a customer's open orders
- US-ORD-008 — Notify customer before expiry
- US-ORD-009 — Order receipt (printable / SMS / email)

## 9. Open questions

- **Deposit refund policy** on cancel / expire: full / partial / forfeit. Currently company-config; default = full refund on cancel within 7 days, forfeit on expire. Confirm.
- **Reservation lock granularity.** Reservation lives in `stock` as a `stock_move` with direction = RESERVED. If multiple orders compete for the same low stock, what's the conflict policy? FIFO at order creation time.
- **Partial-pickup.** Can a customer collect 2 of 3 line items? Default: no, all-or-nothing per order. Splitting into two orders is the workaround.
- **Customer credit-limit interaction.** If the layby balance counts as debt (it doesn't by default — no `sales_invoice` is posted until collection), should it affect credit limit? Default: no.

## 10. Implementation notes

- **Layered shape:** `domain/{entity,dto,enums,event}/`, `service/`, `repository/`.
- **Lombok:** `@Data` + `@NoArgsConstructor(PROTECTED)` + `@EqualsAndHashCode(of = "id")` on entities. Business methods (`addPayment`, `cancel`, `expire`, `collect`) coexist with generated setters.
- **Invariants:**
  - `sum(customer_order_payment.amount) ≤ customer_order.total_amount`.
  - Status transitions are forward-only except `CANCELLED` / `EXPIRED` from any pre-`COLLECTED` state.
  - `customer_order_line` qty cannot exceed available-minus-already-reserved at reserve time.
  - `deposit_paid_amount ≥ deposit_required_amount` is the gate to move into `DEPOSIT_PAID`.
  - `customer_order` cannot reference a `section_id` if not present on the order's `branch_id`.
- **Multi-tenancy:** `company_id` + `branch_id` on every order. Customer can have orders in multiple branches.
- **Idempotency:** every write accepts `Idempotency-Key`. Payment posts are critical — duplicate prevention prevents double-charging the cash drawer.
- **Outbox:** all events emitted in the same transaction. Stock-reservation event and cash-entry event are consumed by their respective modules idempotently on `(order_id, payment_id)`.
- **Cleanup job:** scheduled task runs daily, looks for orders past `reserved_until`, emits expiry event.
