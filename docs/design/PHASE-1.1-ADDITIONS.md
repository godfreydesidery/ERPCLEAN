# Phase 1.1 — Scope additions (supermarket + sections + production + FX + batches + gift cards + layby + refund-at-till)

This is the **authoritative spec** for the Phase 1.1 doc + code sweep. Every agent and engineer updating PRD / DATA-MODEL / USER-STORIES / ARCHITECTURE / module READMEs treats this file as the source of truth.

## Decisions locked

| # | Topic | Decision |
|---|---|---|
| 1 | Masters location | New `modules/admin/` owns branch, section, currency, fx_rate |
| 2 | Gift cards | New `modules/giftcard/` (separate liability ledger from cash_book) |
| 3 | Layby + pre-orders | New `modules/orders/` (separate aggregate from sales_invoice) |
| 4 | Production | Re-added as `modules/production/` (was dropped from minimum set) |
| 5 | Section dimension | REQUIRED on `pos_sale`, `pos_sale_line`, `till`, `bom`, `production_batch`. OPTIONAL on `stock_move` (only stamped on production / section-transfer). NOT on `sales_invoice` / `supplier_invoice` |

Module count: **14** (auth, common, admin, party, catalog, stock, procurement, sales, pos, cash, day, production, orders, giftcard).

## New tables

### `section` (admin module)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | section_seq |
| `branch_id` | BIGINT FK → branch | |
| `code` | VARCHAR(20) | UNIQUE(branch_id, code) |
| `name` | VARCHAR(80) | |
| `type` | VARCHAR(20) | `RETAIL_FLOOR`, `BAKERY`, `BUTCHERY`, `DELI`, `FRESH`, `DAIRY`, `DRY_GOODS`, `HOUSEHOLD`, `ELECTRONICS`, `OTHER` |
| `manager_user_id` | BIGINT FK → app_user | nullable |
| `status` | VARCHAR(32) | `ACTIVE`, `INACTIVE` |
| audit cols + version | | |

### `currency` (admin module)
| Column | Type | Notes |
|---|---|---|
| `code` | VARCHAR(3) PK | ISO 4217 (UGX, USD, EUR…) |
| `name` | VARCHAR(60) | |
| `symbol` | VARCHAR(8) | `UGX`, `$`, `€` |
| `minor_unit_digits` | INT | 0 for UGX/JPY, 2 for USD/EUR |
| `status` | VARCHAR(32) | `ACTIVE`, `INACTIVE` |

### `fx_rate` (admin module)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | fx_rate_seq |
| `from_currency` | VARCHAR(3) FK | |
| `to_currency` | VARCHAR(3) FK | |
| `rate` | DECIMAL(20,8) | `qty_in_to = qty_in_from × rate` |
| `effective_at` | TIMESTAMP | most recent ≤ sale time wins |
| `created_by` | BIGINT | |
| `created_at` | TIMESTAMP | |

### `stock_batch` (stock module)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | stock_batch_seq |
| `item_id` | BIGINT FK | |
| `branch_id` | BIGINT FK | |
| `batch_no` | VARCHAR(40) | UNIQUE(branch_id, item_id, batch_no) |
| `manufactured_at` | DATE | nullable |
| `expiry_at` | DATE | nullable but typical |
| `qty_received` | DECIMAL(18,4) | immutable once recorded |
| `qty_on_hand` | DECIMAL(18,4) | drains on consumption |
| `cost` | DECIMAL(18,4) | unit cost at receipt |
| `source_doc_type` | VARCHAR(40) | `GRN`, `PRODUCTION_OUTPUT`, `OPENING` |
| `source_doc_id` | BIGINT | |
| `status` | VARCHAR(32) | `ACTIVE`, `EXHAUSTED`, `EXPIRED`, `RECALLED` |
| audit cols + version | | |

### `gift_card` (giftcard module)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | gift_card_seq |
| `code` | VARCHAR(40) UNIQUE | printed on card |
| `initial_value` | DECIMAL(18,4) | |
| `current_balance` | DECIMAL(18,4) | derived from gift_card_txn |
| `status` | VARCHAR(32) | `ACTIVE`, `FULLY_REDEEMED`, `EXPIRED`, `FROZEN`, `REFUNDED` |
| `issued_at` | TIMESTAMP | |
| `expires_at` | TIMESTAMP | nullable |
| `issued_by_branch_id` | BIGINT FK | |
| `issued_by_user_id` | BIGINT FK | |
| `company_id` | BIGINT FK | |
| audit cols + version | | |

### `gift_card_txn` (giftcard module)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `gift_card_id` | BIGINT FK | |
| `kind` | VARCHAR(20) | `LOAD`, `REDEEM`, `REFUND`, `EXPIRE` |
| `amount` | DECIMAL(18,4) | always positive |
| `balance_after` | DECIMAL(18,4) | snapshot post-tx |
| `ref_doc_type` | VARCHAR(40) | `POS_SALE`, `ADMIN_ADJUSTMENT`, … |
| `ref_doc_id` | BIGINT | |
| `occurred_at` | TIMESTAMP | |
| `by_user_id` | BIGINT FK | |

### `customer_order` (orders module)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | customer_order_seq |
| `company_id` | BIGINT FK | |
| `branch_id` | BIGINT FK | |
| `section_id` | BIGINT FK | nullable; populated for production-tied orders |
| `number` | VARCHAR(40) | per-branch sequence, e.g. `ORD-BR1-000123` |
| `customer_id` | BIGINT FK | |
| `type` | VARCHAR(20) | `LAYBY`, `PRE_ORDER` |
| `status` | VARCHAR(32) | `DRAFT`, `RESERVED`, `DEPOSIT_PAID`, `PARTIALLY_PAID`, `READY`, `COLLECTED`, `CANCELLED`, `EXPIRED` |
| `reserved_until` | TIMESTAMP | nullable |
| `deposit_required_amount` | DECIMAL(18,4) | |
| `deposit_paid_amount` | DECIMAL(18,4) | |
| `total_amount` | DECIMAL(18,4) | |
| `balance_due` | DECIMAL(18,4) | |
| `notes` | TEXT | |
| audit cols + version | | |

### `customer_order_line` (orders module)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `customer_order_id` | BIGINT FK | |
| `item_id` | BIGINT FK | |
| `qty` | DECIMAL(18,4) | |
| `unit_price` | DECIMAL(18,4) | snapshot at order time |
| `discount_amount` | DECIMAL(18,4) | |
| `line_total` | DECIMAL(18,4) | |

### `customer_order_payment` (orders module)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `customer_order_id` | BIGINT FK | |
| `amount` | DECIMAL(18,4) | |
| `method` | VARCHAR(20) | `CASH`, `CARD`, `BANK_TRANSFER`, `MOBILE_MONEY`, `CHEQUE`, `GIFT_CARD` |
| `ref_cash_entry_id` | BIGINT FK | links the cash side |
| `occurred_at` | TIMESTAMP | |

### `production_wastage` (production module)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `production_batch_id` | BIGINT FK | |
| `item_id` | BIGINT FK | |
| `qty` | DECIMAL(18,4) | |
| `category` | VARCHAR(20) | `BURNT`, `EXPIRED`, `DROPPED`, `SAMPLED`, `DONATED`, `OTHER` |
| `reason` | TEXT | non-empty |
| `recorded_by` | BIGINT | |
| `recorded_at` | TIMESTAMP | |

### `till_currency` (admin module — accepted foreign currencies per till)
| Column | Type | Notes |
|---|---|---|
| `till_id` | BIGINT FK | composite PK |
| `currency_code` | VARCHAR(3) FK | composite PK |

## Existing table changes

### `item`
- `+ is_weighed BOOLEAN NOT NULL DEFAULT FALSE`
- `+ weighing_unit VARCHAR(10)` — `KG`, `G`, `L`, `ML`. Null if `is_weighed = false`.
- `+ tracks_batches BOOLEAN NOT NULL DEFAULT FALSE`

### `item_barcode`
- `+ barcode_type VARCHAR(20) NOT NULL` — `UPC`, `EAN13`, `EAN8`, `PLU`, `EMBEDDED_WEIGHT`, `EMBEDDED_PRICE`

### `stock_move`
- `+ batch_id BIGINT FK → stock_batch` nullable
- `+ section_id BIGINT FK → section` nullable (stamped on production/section-transfer only)
- `+ consumption_category VARCHAR(20)` — `CANTEEN`, `DISPLAY`, `SAMPLES`, `DONATION`, `MAINTENANCE`, `OTHER`. Required when move_type is `INTERNAL_CONSUMPTION`.
- `+ authorised_by_user_id BIGINT FK` — required for internal-consumption / oversell / adjustment-above-threshold
- New `move_type` values: `INTERNAL_CONSUMPTION`, `STAFF_PURCHASE`, `EMPLOYEE_GIFT`

### `bom`
- `+ parent_bom_id BIGINT FK → bom` nullable (sub-recipes)
- `+ section_id BIGINT FK → section` required

### `production_batch`
- `+ section_id BIGINT FK → section` required
- `+ lifecycle_state VARCHAR(32)` — `PLANNED`, `IN_PROGRESS`, `OUTPUT_HOT_DISPLAY`, `OUTPUT_COLD_DISPLAY`, `OUTPUT_DISCOUNTED`, `OUTPUT_DONATED`, `OUTPUT_WRITE_OFF`, `CLOSED`

### `production_output`
- `+ is_pack_by_weight BOOLEAN NOT NULL DEFAULT FALSE`
- `+ batch_id BIGINT FK → stock_batch` — production output is itself a batch row

### `grn_line`
- `+ batch_no VARCHAR(40)` nullable
- `+ expiry_at DATE` nullable

### `pos_sale`
- `+ section_id BIGINT FK → section` **REQUIRED**
- `+ kind VARCHAR(20)` — `SALE`, `REFUND`, `NO_SALE`. Default `SALE`.
- `+ refunded_from_sale_id BIGINT FK → pos_sale` nullable (only set on refund-kind rows)

### `pos_sale_line`
- `+ section_id BIGINT FK → section` **REQUIRED** (usually equals parent `pos_sale.section_id`)
- `+ batch_id BIGINT FK → stock_batch` nullable (FEFO at scan for batch-tracked items)

### `pos_payment`
- `+ tender_currency VARCHAR(3) FK → currency` **REQUIRED** (default = company functional currency)
- `+ tender_amount DECIMAL(18,4)` — value in tender currency
- `+ fx_rate_snapshot DECIMAL(20,8)` — rate used to back-convert
- Existing `amount` column re-interpreted as **functional-currency-converted amount**. Document this in DATA-MODEL.

### `cash_book`
- PK changes from `(branch_id, account, business_date)` to **`(branch_id, account, currency_code, business_date)`**
- `+ currency_code VARCHAR(3) FK → currency` (part of PK)

### `cash_entry`
- `+ currency_code VARCHAR(3) FK → currency` — required
- `+ fx_rate_snapshot DECIMAL(20,8)` — required for non-functional currency, else 1

### `till`
- `+ section_id BIGINT FK → section` **REQUIRED**

### `employee`
- `+ default_section_id BIGINT FK → section` nullable
- `+ staff_price_list_id BIGINT FK → price_list` nullable — when set, POS auto-applies staff prices on badge scan

## Policy rules

### Refund at till
- **Same-day + receipt scanned**: cashier may refund up to threshold (company-configurable, default UGX 50,000). Above threshold → manager PIN.
- **Same-day, no receipt**: manager PIN always.
- **Past business day**: must go via back-office `customer_return` flow; till refund rejected.
- Refund creates a new `pos_sale` row with `kind = REFUND`, negative-qty lines, negative-amount `pos_payment` rows. Cash refund posts a `cash_entry` `OUT` on `TILL`.

### Foreign currency at till
- Functional currency stays single per company.
- Foreign tender allowed only at `pos_payment` step.
- Backend stores `tender_amount` (tender currency), `fx_rate_snapshot`, and `amount` (functional currency).
- `till.accepted_currencies` defined via `till_currency` join table.
- Close-till variance computed **per currency**.

### Weighed items + barcode parsing
- EAN-13 starting with `2` = embedded data. Convention (Type-2 GS1):
  - PLU = bytes 2..7 (5 digits, item identifier)
  - Weight or price = bytes 8..12 (5 digits)
  - Check digit = byte 13
- POS client decodes the embedded weight; backend trusts the client.
- Item with `is_weighed = true` carries `weighing_unit`; POS multiplies decoded weight by per-unit price.

### Section dimension
- REQUIRED on: `pos_sale`, `pos_sale_line`, `till`, `bom`, `production_batch`
- OPTIONAL (stamped only on production / section-transfer): `stock_move`
- NOT on: `sales_invoice`, `supplier_invoice` (HQ-level)

### Expiry / batch
- Item-level opt-in via `item.tracks_batches`.
- GRN captures `batch_no` + `expiry_at` per line for batch-tracked items.
- POS / sales consumption uses **FEFO** (first-expired-first-out).
- Expired stock auto-flagged by scheduled job; report drives manual write-off via `stock_move` of type `EXPIRY_WRITE_OFF` (treated as a damage variant).

### Internal consumption
- New `stock_move.move_type` values: `INTERNAL_CONSUMPTION`, `STAFF_PURCHASE`, `EMPLOYEE_GIFT`.
- All require `authorised_by_user_id`, `consumption_category`, and a non-empty reason on the move.
- `STAFF_PURCHASE` is a normal sale at staff price list (recorded via `pos_sale`, identified by `employee.staff_price_list_id`); the `stock_move` is just a regular `SALE` move tagged with `is_staff_purchase` (or via the cash side).

## New permissions

- `POS.REFUND` — cashier-level same-day refund within threshold
- `POS.REFUND_OVERRIDE_THRESHOLD` — manager override
- `POS.FX_TENDER` — cashier may accept foreign currency tender
- `GIFTCARD.ISSUE` / `GIFTCARD.REDEEM` / `GIFTCARD.FREEZE`
- `ORDER.CREATE_LAYBY` / `ORDER.RESERVE_STOCK` / `ORDER.CANCEL_RESERVED`
- `STOCK.INTERNAL_CONSUMPTION_AUTHORISE`
- `PRODUCTION.RECORD_WASTAGE` / `PRODUCTION.MARK_LIFECYCLE_STATE`
- `ADMIN.MANAGE_BRANCHES` / `ADMIN.MANAGE_SECTIONS` / `ADMIN.MANAGE_CURRENCIES` / `ADMIN.MANAGE_FX`

## New sequences (Flyway)

Add to `db/migration/mysql/V1_3__phase11_sequences.sql` and `db/migration/postgres/V1_3__phase11_sequences.sql`:
- `section_seq`, `fx_rate_seq`, `stock_batch_seq`, `gift_card_seq`, `gift_card_txn_seq`, `customer_order_seq`, `customer_order_line_seq`, `customer_order_payment_seq`, `production_batch_seq`, `production_consumption_seq`, `production_output_seq`, `production_wastage_seq`, `bom_seq`, `bom_line_seq`

## Implementation order (for code commits — docs first, code after)

1. `section` master + section CRUD
2. `currency` + `fx_rate` masters
3. Weighed items + barcode parser
4. `stock_batch` + FEFO consumption
5. Production module re-add + sub-recipes + wastage
6. Gift card module
7. Orders module (layby/pre-order)
8. Internal consumption + staff purchase
9. Refund at till
10. Foreign-currency tender
