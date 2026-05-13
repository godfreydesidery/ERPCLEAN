# Catalog module

## 1. Purpose

The catalog module owns the **item master** and everything that hangs off it: groups, units, tax classification, prices, barcodes, supplier mappings, and time-bounded promotions. It is the single most-queried surface in the system — POS, WMS, web ERP, procurement, sales, stock and production all start with an `item`.

This module is the **canonical hexagonal example** for the codebase. Its layout (`api` / `app` / `domain` / `infra`) is the template every other core module follows.

The defining catalog decision: the legacy `Product` / `Material` split is gone. There is **one `item` aggregate**, with a `type` discriminator (`SELLABLE`, `CONSUMABLE`, `BOTH`, `SERVICE`) and an optional BOM reference. An item can be sold, consumed in production, or both — without duplicating data.

## 2. Scope

In scope:
- Item master CRUD, archival, search.
- Item-group hierarchy (self-referencing tree, any depth — `level` is a denormalised reporting hint).
- UoM registry and per-item pack conversions.
- VAT groups (rate, validity).
- Price lists, price-list-items, append-only `price_change_log`.
- Promotions and their item eligibility.
- Barcodes (multi per item, with pack UoM + qty).
- Item-supplier mappings (supplier code, last buy price, preferred flag).
- Bulk CSV import (background job).

Out of scope (owned elsewhere):
- **Cost calculation** (moving average, last cost) — `stock` module updates `item.avg_cost` / `last_cost` snapshots on GRN postings; catalog only stores the cached values.
- **BOM definition** — `production` module owns BOM rows; not in the current minimum set.
- **Customer price-tier assignment** — `party` module stores `customer.price_list_id`; catalog only defines the price lists.
- **Reorder min/max per branch** — `stock` module owns `item_branch_balance` (catalog provides the item; per-branch policy is stock's).
- **Search index implementation** — `platform.search` owns the Meilisearch indexer; catalog only emits the events it subscribes to.

## 3. Domain model

| Table | Role |
|---|---|
| `item_group` | Self-referencing tree (`parent_id` nullable; `level` 1–5: Department → Class → SubClass → Category → SubCategory). Replaces the legacy four parallel hierarchy tables. |
| `item` | The aggregate root. `code` unique per company; carries type, group, UoM, VAT group, cached costs, status. |
| `item_barcode` | One row per scannable barcode. `barcode` unique per company; each row declares its pack UoM and `pack_qty`. |
| `item_supplier` | M:N between item and supplier with `supplier_item_code`, `last_buy_price`, `last_buy_date`, `is_preferred`. |
| `uom` | Global unit registry (`EA`, `KG`, `L`, `CASE_24`...) with a `dimension` (`COUNT`, `WEIGHT`, `VOLUME`, `LENGTH`) and `is_base` flag. |
| `uom_conversion` | Conversion factor `qty_to = qty_from * factor`. Item-specific when packs differ per item; `item_id` NULL = global dimensional. |
| `vat_group` | Tax classification per company: `code`, `rate` (decimal fraction), `valid_from`, `is_default`. |
| `price_list` | Named price book: `RETAIL`, `WHOLESALE`, `AGENT`, `STAFF`. Carries `currency_code`, validity, `tax_inclusive`. |
| `price_list_item` | A price for one item in one list and UoM. Closed/open by `valid_from` / `valid_to`. |
| `price_change_log` | Append-only audit of every price change: `old_price`, `new_price`, `effective_from`, `changed_by`, `reason`. |
| `promotion` | Time-bounded offer: type ∈ {`PERCENT_OFF`, `AMOUNT_OFF`, `BOGO`, `BUNDLE`, `STEP_QTY`}, `params_json`, `starts_at` / `ends_at`, `priority`, `is_stackable`. |
| `promotion_item` | Composite-PK eligibility table. Empty set = all items. |

Aggregate roots: `Item`, `ItemGroup`, `PriceList`, `Promotion`, `VatGroup`, `Uom`. Other tables are entities or value objects inside those aggregates.

See DATA-MODEL.md §3 for full attribute lists.

## 4. Key business flows

- **Create / edit item** — validate `code` unique per company, group/UoM/VAT references exist, default `is_tracked = true`. Emit `ItemCreated.v1`. Renames and attribute edits emit `ItemUpdated.v1`; price-affecting edits trigger reindex.
- **Bulk CSV import** — upload kicked off as a background job. Row-by-row validation produces a preview (`create` / `update` / `fail`). Apply phase runs in chunks; failures roll back the chunk with per-row errors. Result is emailed.
- **Item-group hierarchy maintenance** — move a node and its descendants follow. Groups with items cannot be hard-deleted; archive instead. `level` is recomputed on move.
- **Price-list maintenance** — setting a new price closes the previous `price_list_item` with `valid_to = effective_from - 1` and opens a new row. **Every change appends to `price_change_log`** — never updates it. Emits `ItemPriceChanged.v1`.
- **Promotion activation** — scheduled by `starts_at`. POS / sales pick the highest-`priority` active promotion at line evaluation; `is_stackable` controls combination. Emits `PromotionScheduled.v1` on creation and `PromotionActivated.v1` when it crosses `starts_at`.
- **Barcode add** — validate uniqueness per company, set pack UoM + `pack_qty`, emit `BarcodeAdded.v1` so POS offline catalogues pick it up on next pull.
- **Archive item** — soft state transition (`status = ARCHIVED`). Existing transactions retain the FK; new lines are blocked at the app layer.

## 5. Module interactions

**Depends on:**
- `platform.company` — `company_id` scope on every master row.
- `platform.audit` — change tracking.
- `platform.events` — outbox publication.
- `party` — `default_supplier_id` and `item_supplier.supplier_id` reference `supplier.party_id`.

**Published events** (versioned, dispatched via transactional outbox — see ARCHITECTURE.md §2.10):

| Event | Triggered by | Primary subscribers |
|---|---|---|
| `ItemCreated.v1` | New item row | Meilisearch indexer, offline POS / WMS catalogue sync |
| `ItemUpdated.v1` | Attribute edit | Meilisearch indexer, POS / WMS sync |
| `ItemArchived.v1` / `ItemActivated.v1` | Status transition | Meilisearch indexer, POS / WMS sync |
| `ItemPriceChanged.v1` | `price_list_item` close+open | Meilisearch indexer, POS / WMS sync, reporting |
| `PromotionScheduled.v1` | Promotion created/updated | POS / WMS sync, reporting |
| `PromotionActivated.v1` | `starts_at` crossed | POS / WMS sync |
| `BarcodeAdded.v1` | New `item_barcode` | Meilisearch indexer, POS / WMS sync |

**Consumes events:**
- `GrnPosted` (from `procurement`) — refresh cached `last_cost`, `avg_cost` on `item` (the authoritative computation lives in `stock`; catalog only stores the snapshot).
- `PartyArchived` (from `party`) — null out `default_supplier_id` on affected items.

## 6. API surface

Base path `/api/v1` (per ARCHITECTURE.md §2.8). All endpoints are company-scoped via `RequestContext`.

| Resource | Endpoints |
|---|---|
| `/api/items` | `GET` (paged, filtered), `GET /{id}`, `POST`, `PUT /{id}`, `POST /{id}/archive`, `POST /{id}/activate` |
| `/api/items/bulk-import` | `POST` (CSV upload → returns job id), `GET /{jobId}` (status + per-row results) |
| `/api/item-groups` | `GET` (tree or flat), `POST`, `PUT /{id}`, `POST /{id}/move`, `POST /{id}/archive` |
| `/api/barcodes` | `GET`, `POST`, `DELETE /{id}` (scoped to an item) |
| `/api/uoms` | `GET`, `POST`, `PUT /{id}` |
| `/api/uom-conversions` | `GET`, `POST`, `DELETE /{id}` |
| `/api/vat-groups` | `GET`, `POST`, `PUT /{id}` |
| `/api/price-lists` | `GET`, `GET /{id}`, `POST`, `PUT /{id}` |
| `/api/price-lists/{id}/items` | `GET`, `PUT` (set/replace price → closes prior row + appends to `price_change_log`) |
| `/api/price-change-log` | `GET` (filtered by item / list / date range) — read-only |
| `/api/promotions` | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `POST /{id}/cancel` |
| `/api/promotions/{id}/items` | `GET`, `PUT` (replace eligibility set) |
| `/api/item-suppliers` | `GET`, `POST`, `PUT /{id}`, `DELETE /{id}` |

Typeahead / search is **not served from this module**. POS, WMS and web hit `/api/v1/search/items` on `platform.search` (Meilisearch-backed). The catalog module's role is to emit events that keep the index fresh.

All write endpoints accept a `clientOpId` (UUID v7) header for idempotency on retry — see §2.9 of ARCHITECTURE.md.

## 7. Persistence

- Baseline DDL: `db/migration/common/V2__catalog.sql` (companion to `V1__platform_baseline.sql`).
- Future migrations: `db/migration/common/V<N>__catalog_<purpose>.sql`. Dialect-specific scripts go to `mysql/` or `postgres/` only when unavoidable, with a `// DIALECT-SPECIFIC:` reason comment.
- IDs: `BIGINT` via Hibernate `SEQUENCE` strategy with table fallback on MySQL (see `Item.java` — `@SequenceGenerator(name = "item_seq", sequenceName = "item_seq", allocationSize = 50)`). Never `IDENTITY`.
- Decimal precision: prices and costs are `DECIMAL(18,4)` (four-decimal money scale, per ARCHITECTURE.md §2.8). Conversion factors are `DECIMAL(18,8)`. VAT rates are `DECIMAL(10,4)` stored as a fraction (`0.18` = 18%).
- Optimistic locking: `@Version` on every aggregate root.
- Soft-deletable masters use `status` (`ACTIVE` / `INACTIVE` / `ARCHIVED`); no hard deletes.
- Append-only: `price_change_log` — never updated, never deleted.
- Uniqueness: `uk_item_company_code (company_id, code)`, `uk_item_barcode_company_barcode (company_id, barcode)`, `uk_item_group_company_code (company_id, code)`, `uk_uom_code (code)`, `uk_vat_group_company_code (company_id, code)`, `uk_price_list_company_code (company_id, code)`, `uk_promotion_company_code (company_id, code)`.

## 8. User stories

**P1 (MVP):**
- US-CAT-001 — Create an item
- US-CAT-002 — Edit an item
- US-CAT-003 — Add a barcode to an item
- US-CAT-004 — Manage the item-group hierarchy
- US-CAT-005 — Search for an item (Meilisearch — implementation in `platform.search`, events sourced here)
- US-CAT-006 — Maintain an item-supplier mapping
- US-CAT-007 — Create and maintain a price list
- US-CAT-008 — Set a customer's price tier (party-side; catalog provides the list)
- US-CAT-011 — Configure reorder points per item per branch (stock-side; item must exist here first)
- US-CAT-013 — Manage offline catalogue on POS / WMS (driven by events emitted here)
- US-CAT-014 — Audit price changes over time

**P2:**
- US-CAT-009 — Bulk-edit items in a grid
- US-CAT-010 — Bulk-import items from CSV
- US-CAT-012 — Create a promotion

## 9. Open questions

- **PRD.md §13 Q6 — Pricing tiers, how many price lists per item in practice?** Three (retail / wholesale / agent) covers the legacy model. Schema supports N; confirm before locking UI defaults. Adding a fourth (`STAFF`) is data-only, no code change.
- **DATA-MODEL.md §16 — Promotion engine scope.** Current schema supports `PERCENT_OFF`, `AMOUNT_OFF`, `BOGO`, `BUNDLE`, `STEP_QTY` with `priority` and `is_stackable`. Evaluation rules at POS line level need a written specification — especially stacking precedence and interaction with tax-inclusive price lists. Not blocking schema; blocking POS implementation.
- Cost-method scope (DATA-MODEL.md §16.2): both global `item.avg_cost` and per-branch `item_branch_balance.avg_cost` are kept; confirm both are wanted before stock module hardens.
- Default supplier override behaviour: should setting `item_supplier.is_preferred = true` also overwrite `item.default_supplier_id`? Currently independent.

## 10. Implementation notes

**Hexagonal layout (already laid out — use as template):**

```
catalog/
├── api/                  — REST controllers + request/response DTOs
│   ├── ItemController.java
│   └── dto/
├── app/                  — application services, @Transactional, orchestration
│   └── ItemService.java
├── domain/               — entities, value objects, domain events
│   └── Item.java
└── infra/                — JPA repositories, adapters
    └── ItemRepository.java
```

Controllers never touch repositories. Repositories never return entities outside `app`. ArchUnit enforces both.

**Invariants (enforced in domain / DB):**
- `item.code` unique per `company_id`.
- `item_barcode.barcode` unique per `company_id` (effectively global once we treat the same company across branches as one barcode namespace).
- `vat_group.rate >= 0`.
- `uom_conversion.factor > 0`.
- `promotion.starts_at < promotion.ends_at`.
- `price_list_item` rows for the same `(price_list_id, item_id, uom_id)` may not overlap in `[valid_from, valid_to]` — the close+open flow guarantees this.
- An item cannot be archived if there is an active promotion targeting it; archive the promotion first or let it expire.

**Multi-tenant:** every master row carries `company_id`. The `RequestContext` filter injects the predicate on read repositories. There is no group-wide catalog at MVP — UoM is the one global table.

**Idempotency:** every write endpoint accepts a `clientOpId` (UUID v7). The app layer rejects duplicates by `clientOpId` within a 24-hour window. Critical for the CSV import job and for POS / WMS retrying device-originated edits.

**Outbox emits drive Meilisearch + offline POS:** the catalog module does **not** call Meilisearch or push to clients directly. It writes a `domain_event` row in the same transaction as the business write; `platform.search` and the sync poller subscribe. Per US-CAT-001 AC the index is fresh within 1 second of commit.

**Typeahead via Meilisearch, not the DB:** there is no `LIKE '%...%'` query in this module. The relational DB is for transactional truth; search is delegated. This is also what keeps the persistence layer DB-agnostic (no MySQL `FULLTEXT`, no Postgres `to_tsvector`).

**Existing canonical implementation:** see `domain/Item.java` — the JPA mapping there (sequence generator, `@Version`, `Status` enum, multi-tenant `company_id`, `createdAt` / `updatedAt` / `createdBy` / `updatedBy` audit columns) is the template every other aggregate in the codebase should mirror.

---

## 11. Phase 1.1 additions

See [docs/design/PHASE-1.1-ADDITIONS.md](../../../../../../../../docs/design/PHASE-1.1-ADDITIONS.md).

**`item` gains:**
- `is_weighed BOOLEAN` — sold by weight at the till
- `weighing_unit VARCHAR(10)` — `KG` / `G` / `L` / `ML`, nullable
- `tracks_batches BOOLEAN` — opts the item into `stock_batch` tracking with manufactured / expiry dates

**`item_barcode` gains:**
- `barcode_type VARCHAR(20)` — `UPC`, `EAN13`, `EAN8`, `PLU`, `EMBEDDED_WEIGHT`, `EMBEDDED_PRICE`. Embedded-weight EAN-13 has leading digit `2`, PLU bytes 2..7, weight bytes 8..12, check digit 13.

**New invariants:**
- `weighing_unit` non-null iff `is_weighed = true`.
- Weighed items must have at least one barcode of type `EMBEDDED_WEIGHT` or `PLU`.
- Items with `tracks_batches = true` cannot be archived while any `stock_batch.status = ACTIVE` references them.

**New events:**
- `ItemWeighingChanged.v1`, `ItemBatchTrackingEnabled.v1`, `ItemBatchTrackingDisabled.v1`.

**New user stories:** US-CAT-015 (weighed flag), US-CAT-016 (EAN-13 embedded-weight parsing — POS-side), US-CAT-017 (batch-tracking flag), US-CAT-018 (bulk-edit weighed / batch flags).

**Catalog is NOT the owner of:** `stock_batch` rows (those live in `stock`), `production_wastage` (lives in `production`), `currency` / `fx_rate` (lives in `admin`).
