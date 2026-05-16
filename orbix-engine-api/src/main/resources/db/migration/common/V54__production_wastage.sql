-- Orbix Engine — production wastage (F7.3c). DATA-MODEL §17.11.
-- Category-tagged loss recorded against a production_batch. Wastage qty does
-- NOT enter stock — it's a side-channel record for reporting and variance
-- analysis. Mandatory reason for audit.

CREATE TABLE production_wastage (
    id                     BIGINT         NOT NULL PRIMARY KEY,
    production_batch_id    BIGINT         NOT NULL,
    item_id                BIGINT         NOT NULL,
    qty                    DECIMAL(18, 4) NOT NULL,
    uom_id                 BIGINT         NOT NULL,
    category               VARCHAR(20)    NOT NULL,
    reason                 VARCHAR(2000)  NOT NULL,
    recorded_by            BIGINT         NOT NULL,
    recorded_at            TIMESTAMP      NOT NULL,
    CONSTRAINT fk_production_wastage_batch FOREIGN KEY (production_batch_id) REFERENCES production_batch (id),
    CONSTRAINT fk_production_wastage_item  FOREIGN KEY (item_id) REFERENCES item (id),
    CONSTRAINT fk_production_wastage_uom   FOREIGN KEY (uom_id) REFERENCES uom (id)
);
CREATE INDEX ix_production_wastage_batch    ON production_wastage (production_batch_id);
CREATE INDEX ix_production_wastage_category ON production_wastage (category);
