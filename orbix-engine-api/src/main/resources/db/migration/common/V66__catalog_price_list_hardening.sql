-- Orbix Engine — price-list hardening.
-- 1. Quantity-break (tiered) pricing: min_qty on each price row. Base tier = 0.
-- 2. Point-in-time effective lookup index (keyed on valid_from, not valid_to).
-- DATA-MODEL.md §3.9.

ALTER TABLE price_list_item
    ADD COLUMN min_qty DECIMAL(18, 4) NOT NULL DEFAULT 0;

-- Effective-as-of resolution scans by (list, item, uom) then date window.
CREATE INDEX ix_price_list_item_effective
    ON price_list_item (price_list_id, item_id, uom_id, valid_from);
