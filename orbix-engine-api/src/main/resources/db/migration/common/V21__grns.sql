-- Orbix Engine — GRN (goods received note) + batch capture (F3.2).
-- DATA-MODEL.md §5.5, §5.6. DRAFT → POSTED writes stock_move rows (and
-- stock_batch rows for batch-tracked items) atomically with the GRN flip;
-- POSTED is terminal. DRAFT → CANCELLED is also allowed.

CREATE TABLE grn (
    id                       BIGINT         NOT NULL PRIMARY KEY,
    uid                      CHAR(26)       NOT NULL,
    number                   VARCHAR(40)    NOT NULL,
    company_id               BIGINT         NOT NULL,
    branch_id                BIGINT         NOT NULL,
    supplier_id              BIGINT         NOT NULL,
    lpo_order_id             BIGINT,
    received_date            DATE           NOT NULL,
    supplier_delivery_note   VARCHAR(80),
    subtotal_amount          DECIMAL(18, 4) NOT NULL DEFAULT 0,
    tax_amount               DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount             DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status                   VARCHAR(32)    NOT NULL,
    posted_at                TIMESTAMP,
    posted_by                BIGINT,
    notes                    VARCHAR(2000),
    version                  INT            NOT NULL DEFAULT 0,
    created_at               TIMESTAMP      NOT NULL,
    updated_at               TIMESTAMP      NOT NULL,
    created_by               BIGINT         NOT NULL,
    updated_by               BIGINT         NOT NULL,
    CONSTRAINT uk_grn_uid           UNIQUE (uid),
    CONSTRAINT uk_grn_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_grn_company  FOREIGN KEY (company_id)   REFERENCES company (id),
    CONSTRAINT fk_grn_branch   FOREIGN KEY (branch_id)    REFERENCES branch (id),
    CONSTRAINT fk_grn_supplier FOREIGN KEY (supplier_id)  REFERENCES supplier (party_id),
    CONSTRAINT fk_grn_lpo      FOREIGN KEY (lpo_order_id) REFERENCES lpo_order (id)
);
CREATE INDEX ix_grn_company_status ON grn (company_id, status);
CREATE INDEX ix_grn_branch_status  ON grn (branch_id,  status);
CREATE INDEX ix_grn_lpo            ON grn (lpo_order_id);

CREATE TABLE grn_line (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    grn_id              BIGINT         NOT NULL,
    lpo_order_line_id   BIGINT,
    item_id             BIGINT         NOT NULL,
    uom_id              BIGINT         NOT NULL,
    received_qty        DECIMAL(18, 4) NOT NULL,
    unit_cost           DECIMAL(18, 4) NOT NULL,
    vat_group_id        BIGINT         NOT NULL,
    line_total          DECIMAL(18, 4) NOT NULL,
    batch_no            VARCHAR(40),
    expiry_date         DATE,
    CONSTRAINT fk_grn_line_grn      FOREIGN KEY (grn_id)            REFERENCES grn (id),
    CONSTRAINT fk_grn_line_lpo_line FOREIGN KEY (lpo_order_line_id) REFERENCES lpo_order_line (id),
    CONSTRAINT fk_grn_line_item     FOREIGN KEY (item_id)           REFERENCES item (id),
    CONSTRAINT fk_grn_line_uom      FOREIGN KEY (uom_id)            REFERENCES uom (id),
    CONSTRAINT fk_grn_line_vat      FOREIGN KEY (vat_group_id)      REFERENCES vat_group (id)
);
CREATE INDEX ix_grn_line_grn ON grn_line (grn_id);
