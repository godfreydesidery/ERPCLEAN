-- Orbix Engine — production batch + consumption + output (F7.3b).
-- DATA-MODEL §9.3 / §9.4 / §9.5 + Phase 1.1 additions (section_id +
-- lifecycle_state on batch; is_pack_by_weight + batch_id on output).
--
-- Lifecycle for F7.3b: status goes PLANNED -> IN_PROGRESS -> COMPLETED
-- with a CANCELLED branch from PLANNED. lifecycle_state mirrors status
-- (PLANNED / IN_PROGRESS / OUTPUT_HOT_DISPLAY on completion); the
-- HOT->COLD->DISCOUNTED->DONATED/WRITE_OFF->CLOSED transitions land in F7.3c.
--
-- Plan time:
--   * BOM (and any sub-BOMs) is exploded into flat material requirements,
--     scaled by planned_qty / bom.output_qty and bumped by per-line wastage_pct.
--   * Materials are reserved via StockReservationService (qty_reserved bumped;
--     qty_on_hand untouched). production_consumption.planned_qty rows are
--     written; actual_qty filled on start.
--
-- Start: reservations are released and PROD_CONSUME outbound stock_moves are
-- posted at the moving-average cost. actual_qty is set to planned for now;
-- F7.3c will allow operator override at post-output time.
--
-- Post-output: per output item creates a PROD_OUTPUT inbound stock_move at
-- a unit_cost derived from sum(consumption_cost) ÷ sum(output_qty). For
-- batch-tracked items a stock_batch row is created first and stamped on the
-- move; per-line manufactured_at / expiry_at / batch_no / is_pack_by_weight
-- captured.

CREATE TABLE production_batch (
    id                BIGINT         NOT NULL PRIMARY KEY,
    number            VARCHAR(40)    NOT NULL,
    company_id        BIGINT         NOT NULL,
    branch_id         BIGINT         NOT NULL,
    section_id        BIGINT         NOT NULL,
    bom_id            BIGINT,
    output_item_id    BIGINT         NOT NULL,
    planned_qty       DECIMAL(18, 4) NOT NULL,
    actual_qty        DECIMAL(18, 4),
    reject_qty        DECIMAL(18, 4),
    status            VARCHAR(32)    NOT NULL,
    lifecycle_state   VARCHAR(32)    NOT NULL,
    planned_at        TIMESTAMP      NOT NULL,
    started_at        TIMESTAMP,
    started_by        BIGINT,
    completed_at      TIMESTAMP,
    completed_by      BIGINT,
    cancelled_at      TIMESTAMP,
    cancelled_by      BIGINT,
    notes             VARCHAR(2000),
    version           INT            NOT NULL DEFAULT 0,
    created_at        TIMESTAMP      NOT NULL,
    updated_at        TIMESTAMP      NOT NULL,
    created_by        BIGINT         NOT NULL,
    updated_by        BIGINT         NOT NULL,
    CONSTRAINT uk_production_batch_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_production_batch_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_production_batch_branch  FOREIGN KEY (branch_id)  REFERENCES branch  (id),
    CONSTRAINT fk_production_batch_section FOREIGN KEY (section_id) REFERENCES section (id),
    CONSTRAINT fk_production_batch_bom     FOREIGN KEY (bom_id)     REFERENCES bom     (id),
    CONSTRAINT fk_production_batch_output  FOREIGN KEY (output_item_id) REFERENCES item (id)
);
CREATE INDEX ix_production_batch_company_status ON production_batch (company_id, status);
CREATE INDEX ix_production_batch_section        ON production_batch (section_id);
CREATE INDEX ix_production_batch_branch         ON production_batch (branch_id);

CREATE TABLE production_consumption (
    id                     BIGINT         NOT NULL PRIMARY KEY,
    production_batch_id    BIGINT         NOT NULL,
    line_no                INT            NOT NULL,
    input_item_id          BIGINT         NOT NULL,
    planned_qty            DECIMAL(18, 4) NOT NULL,
    actual_qty             DECIMAL(18, 4),
    uom_id                 BIGINT         NOT NULL,
    unit_cost              DECIMAL(18, 4),
    posted_at              TIMESTAMP,
    notes                  VARCHAR(200),
    CONSTRAINT uk_production_consumption_line_no UNIQUE (production_batch_id, line_no),
    CONSTRAINT fk_production_consumption_batch FOREIGN KEY (production_batch_id) REFERENCES production_batch (id),
    CONSTRAINT fk_production_consumption_item  FOREIGN KEY (input_item_id) REFERENCES item (id),
    CONSTRAINT fk_production_consumption_uom   FOREIGN KEY (uom_id) REFERENCES uom (id)
);
CREATE INDEX ix_production_consumption_batch ON production_consumption (production_batch_id);

CREATE TABLE production_output (
    id                     BIGINT         NOT NULL PRIMARY KEY,
    production_batch_id    BIGINT         NOT NULL,
    line_no                INT            NOT NULL,
    output_item_id         BIGINT         NOT NULL,
    qty                    DECIMAL(18, 4) NOT NULL,
    uom_id                 BIGINT         NOT NULL,
    unit_cost              DECIMAL(18, 4) NOT NULL DEFAULT 0,
    is_primary             BOOLEAN        NOT NULL DEFAULT TRUE,
    is_pack_by_weight      BOOLEAN        NOT NULL DEFAULT FALSE,
    batch_id               BIGINT,
    batch_no               VARCHAR(40),
    manufactured_at        DATE,
    expiry_at              DATE,
    posted_at              TIMESTAMP      NOT NULL,
    notes                  VARCHAR(200),
    CONSTRAINT uk_production_output_line_no UNIQUE (production_batch_id, line_no),
    CONSTRAINT fk_production_output_batch    FOREIGN KEY (production_batch_id) REFERENCES production_batch (id),
    CONSTRAINT fk_production_output_item     FOREIGN KEY (output_item_id) REFERENCES item (id),
    CONSTRAINT fk_production_output_uom      FOREIGN KEY (uom_id) REFERENCES uom (id),
    CONSTRAINT fk_production_output_batch_id FOREIGN KEY (batch_id) REFERENCES stock_batch (id)
);
CREATE INDEX ix_production_output_batch ON production_output (production_batch_id);
