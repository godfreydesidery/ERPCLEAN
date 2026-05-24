-- Orbix Engine — item conversion (F7.4). DATA-MODEL §9.6.
-- One-shot non-BOM transformation: bulk-flour -> packed-flour, raw-fruit ->
-- chopped-fruit, etc. Posts paired PROD_CONSUME (from_item) + PROD_OUTPUT
-- (to_item) stock_moves in one transaction. Distinct from a production_batch
-- — no plan / start phase, no consumption / output line tables, no lifecycle.
--
-- DRAFT -> POSTED -> terminal. POSTED writes the moves; CANCELLED only
-- allowed from DRAFT.

CREATE TABLE conversion (
    id                BIGINT         NOT NULL PRIMARY KEY,
    uid               CHAR(26)       NOT NULL,
    number            VARCHAR(40)    NOT NULL,
    company_id        BIGINT         NOT NULL,
    branch_id         BIGINT         NOT NULL,
    conversion_date   DATE           NOT NULL,
    from_item_id      BIGINT         NOT NULL,
    from_qty          DECIMAL(18, 4) NOT NULL,
    from_uom_id       BIGINT         NOT NULL,
    to_item_id        BIGINT         NOT NULL,
    to_qty            DECIMAL(18, 4) NOT NULL,
    to_uom_id         BIGINT         NOT NULL,
    unit_cost         DECIMAL(18, 4) NOT NULL DEFAULT 0,
    reason            VARCHAR(120),
    status            VARCHAR(32)    NOT NULL,
    posted_at         TIMESTAMP,
    posted_by         BIGINT,
    cancelled_at      TIMESTAMP,
    cancelled_by      BIGINT,
    version           INT            NOT NULL DEFAULT 0,
    created_at        TIMESTAMP      NOT NULL,
    updated_at        TIMESTAMP      NOT NULL,
    created_by        BIGINT         NOT NULL,
    updated_by        BIGINT         NOT NULL,
    CONSTRAINT uk_conversion_uid           UNIQUE (uid),
    CONSTRAINT uk_conversion_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_conversion_company   FOREIGN KEY (company_id)   REFERENCES company (id),
    CONSTRAINT fk_conversion_branch    FOREIGN KEY (branch_id)    REFERENCES branch  (id),
    CONSTRAINT fk_conversion_from_item FOREIGN KEY (from_item_id) REFERENCES item    (id),
    CONSTRAINT fk_conversion_to_item   FOREIGN KEY (to_item_id)   REFERENCES item    (id),
    CONSTRAINT fk_conversion_from_uom  FOREIGN KEY (from_uom_id)  REFERENCES uom     (id),
    CONSTRAINT fk_conversion_to_uom    FOREIGN KEY (to_uom_id)    REFERENCES uom     (id)
);
CREATE INDEX ix_conversion_company_status ON conversion (company_id, status);
CREATE INDEX ix_conversion_branch_date    ON conversion (branch_id, conversion_date);
