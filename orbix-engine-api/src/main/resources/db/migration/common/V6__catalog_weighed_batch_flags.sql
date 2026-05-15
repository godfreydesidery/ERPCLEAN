-- Phase 1.1: weighed-item flags + barcode typing. Catalog README §11.
-- item.is_batch_tracked already exists from the V2 baseline; only the
-- weighed-item columns and item_barcode.barcode_type are new here.

ALTER TABLE item ADD COLUMN is_weighed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE item ADD COLUMN weighing_unit VARCHAR(10);

ALTER TABLE item_barcode ADD COLUMN barcode_type VARCHAR(20) NOT NULL DEFAULT 'EAN13';
