-- Orbix Engine — stock_move gains section / consumption / authoriser tags (F2.5).
-- DATA-MODEL.md §17.12. All columns are nullable; consumption_category becomes
-- required at the app layer for INTERNAL_CONSUMPTION moves, and
-- authorised_by_user_id is required for internal-consumption / oversell /
-- above-threshold adjustment.

ALTER TABLE stock_move ADD COLUMN section_id              BIGINT;
ALTER TABLE stock_move ADD COLUMN consumption_category    VARCHAR(20);
ALTER TABLE stock_move ADD COLUMN authorised_by_user_id   BIGINT;

ALTER TABLE stock_move ADD CONSTRAINT fk_stock_move_section
    FOREIGN KEY (section_id) REFERENCES section (id);
ALTER TABLE stock_move ADD CONSTRAINT fk_stock_move_authoriser
    FOREIGN KEY (authorised_by_user_id) REFERENCES app_user (id);

CREATE INDEX ix_stock_move_section              ON stock_move (section_id);
CREATE INDEX ix_stock_move_consumption_category ON stock_move (consumption_category);
