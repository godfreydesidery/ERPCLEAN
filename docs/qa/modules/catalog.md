# Catalog — test plan

Module-level tests for items, item groups, barcodes, UoM, VAT groups, price lists, price-change log, promotions, weighed-item flags, batch-tracking flag.

## Item CRUD

### TC-CAT-001 — Create item [P1]
**Stories:** US-CAT-001
**Steps:** POST /api/v1/items `{ code: "MILK-1L", name: "Milk 1L", type: "SELLABLE", item_group_id, uom_id, vat_group_id, is_tracked: true }`.
**Expected:** 201; `ItemCreated.v1` emitted; Meilisearch indexed within 1s; `company_id` from request context.

### TC-CAT-002 — Edit item [P1]
**Stories:** US-CAT-002
**Steps:** PUT /api/v1/items/{id} rename. `ItemUpdated.v1` emitted.

### TC-CAT-003 — Duplicate code rejected per company [P1]
**Steps:** POST item with `code = MILK-1L` (already exists in this company).
**Expected:** 409.

### TC-CAT-004 — Archive item [P1]
**Steps:** POST /api/v1/items/{id}/archive.
**Expected:** `status = ARCHIVED`; `ItemArchived.v1`; new lines (POS, sales) blocked at app layer.

### TC-CAT-005 — Cannot archive item with active promotion [P1]
**Steps:** Item has an active promotion. POST archive.
**Expected:** 422; cancel / expire the promotion first.

### TC-CAT-006 — Cannot archive batch-tracked item with active batches [P1]
**Steps:** Item `tracks_batches = true`, has ACTIVE stock_batch with qty > 0. POST archive.
**Expected:** 422 `BATCHED_STOCK_ON_HAND`.

## Item groups

### TC-CAT-007 — Create item group hierarchy [P1]
**Stories:** US-CAT-004
**Steps:** Create Department → Class → SubClass → Category → SubCategory (5 levels).
**Expected:** 5 rows; each `level` field set; `parent_id` chain correct.

### TC-CAT-008 — Move a group node (descendants follow) [P2]
**Steps:** Move a Category from one Class to another.
**Expected:** Group + all SubCategory children re-parented; `level` recomputed.

### TC-CAT-009 — Group with items cannot be hard-deleted [P1]
**Steps:** DELETE on a group that has items.
**Expected:** 422 `GROUP_HAS_ITEMS`; offer archive instead.

## Barcodes

### TC-CAT-010 — Add multiple barcodes to one item [P1]
**Stories:** US-CAT-003
**Steps:** Add 3 barcodes (outer carton, inner pack, single) with different `barcode_type`.
**Expected:** 3 rows; each unique per company.

### TC-CAT-011 — Duplicate barcode per company [P1]
**Steps:** Add a barcode already used by another item in this company.
**Expected:** 409.

### TC-CAT-012 — Weighed item requires EMBEDDED_WEIGHT or PLU barcode [P1]
**Stories:** US-CAT-015
**Steps:** Create item with `is_weighed = true` but no PLU / EMBEDDED_WEIGHT barcode. Try to set `is_weighed = true` without one.
**Expected:** 422 `WEIGHED_ITEM_NEEDS_PLU_OR_EMBEDDED_BARCODE`.

### TC-CAT-013 — Embedded-weight barcode parsing [P1]
**Stories:** US-CAT-016
**Type:** Functional / Parser unit
**Steps:** Parse `2123450042005` (PLU 12345, weight 0.420 KG, check 5).
**Expected:** Returns `{ plu: "12345", quantity: 0.420 }`; rejects when leading digit ≠ 2 or check digit invalid.

## UoM + VAT

### TC-CAT-014 — Create UoM + conversion [P1]
**Steps:** Create `KG`, `G`; add conversion factor 1 KG = 1000 G.
**Expected:** Conversion applied bidirectionally; `factor > 0`.

### TC-CAT-015 — Create VAT group [P1]
**Steps:** Create `STANDARD` group with rate 0.18.
**Expected:** Rate ≥ 0; UNIQUE (company_id, code).

## Price lists

### TC-CAT-016 — Create price list [P1]
**Stories:** US-CAT-007
**Steps:** Create RETAIL list, currency UGX, tax_inclusive = true.

### TC-CAT-017 — Set item price (audited) [P1]
**Stories:** US-CAT-014
**Steps:** PUT /api/v1/price-lists/{id}/items `{ item_id, price: 3500, valid_from: today }`.
**Expected:**
- New `price_list_item` row opens.
- If a prior row existed, it's closed with `valid_to = today − 1`.
- `price_change_log` row appended (NEVER updated).
- `ItemPriceChanged.v1` emitted.

### TC-CAT-018 — Price change log is append-only [P1]
**Steps:** UPDATE / DELETE on price_change_log via direct DB.
**Expected:** App layer has no such endpoint; DB-level enforcement (CHECK / trigger) ideal.

### TC-CAT-019 — Overlapping price periods rejected [P1]
**Steps:** Set price `valid_from = today, valid_to = NULL`. Then set another with `valid_from = today + 5`.
**Expected:** Second set CLOSES the first at `today + 4` (close-open pattern). No overlap remains.

## Promotions

### TC-CAT-020 — Create promotion [P2]
**Stories:** US-CAT-012
**Steps:** Create PERCENT_OFF 10% promotion, items = `[A, B]`, dates = next week.
**Expected:** `PromotionScheduled.v1`. At `starts_at`, scheduled job emits `PromotionActivated.v1`.

### TC-CAT-021 — Promotion priority + stackability [P2]
**Steps:** Two active promotions cover item A. priority + is_stackable rules drive selection.
**Expected:** Highest priority wins by default; if both stackable, both apply (composition rule documented).

### TC-CAT-022 — Cancel promotion [P2]
**Steps:** POST /api/v1/promotions/{id}/cancel.
**Expected:** `status = CANCELLED`; not picked at POS even within date window.

## Bulk

### TC-CAT-023 — CSV import preview [P2]
**Stories:** US-CAT-010
**Steps:** POST /api/v1/items/bulk-import with CSV.
**Expected:** Async job; preview returns per-row `create` / `update` / `fail`.

### TC-CAT-024 — CSV import apply, partial chunk failure [P2]
**Steps:** Apply phase; row 50 fails validation.
**Expected:** Chunk of which row 50 is a member rolls back; other chunks commit. Final report lists 1 failure with reason.

## Phase 1.1 flags

### TC-CAT-025 — Set is_weighed + weighing_unit [P1]
**Stories:** US-CAT-015
**Steps:** PUT item with `is_weighed = true, weighing_unit = KG`.
**Expected:** Both set; `weighing_unit` required when `is_weighed = true`; rejected if missing.

### TC-CAT-026 — Set tracks_batches on an item with existing stock [P2]
**Stories:** US-CAT-017
**Steps:** Item has stock_move history but no `stock_batch` yet. Set `tracks_batches = true`.
**Expected:** Allowed; future GRNs require batch_no + expiry. Existing on-hand stock continues without batch (legacy quantity).

### TC-CAT-027 — Unset tracks_batches with active batches [P2]
**Steps:** Item has ACTIVE batches with `qty_on_hand > 0`. Set `tracks_batches = false`.
**Expected:** 422 `BATCHES_EXIST_FOR_ITEM`.

## Search

### TC-CAT-028 — Typeahead via Meilisearch [P1]
**Stories:** US-CAT-005
**Steps:** Query `/api/v1/search/items?q=mil`.
**Expected:** Returns items where `name`, `code`, or barcode matches. Hits Meilisearch, NOT the DB. Response within 200ms for 50k-item index.

### TC-CAT-029 — Reindex after item update [P1]
**Steps:** Update item name. Wait 1s.
**Expected:** Typeahead returns the new name. (Drives the index via outbox event.)

## Edge

### TC-CAT-030 — Concurrent edits to same item (optimistic lock) [P2]
**Steps:** Two clients fetch item v=5; both submit PATCH.
**Expected:** First wins; second gets 409 with current version.
