-- Orbix Engine — LPO (local purchase order) lifecycle (F3.1). DATA-MODEL.md §5.3, §5.4.
-- DRAFT → PENDING_APPROVAL → APPROVED → (PARTIALLY_RECEIVED → RECEIVED via F3.2 GRN) → CANCELLED.

CREATE TABLE lpo_order (
    id                      BIGINT         NOT NULL PRIMARY KEY,
    uid                     CHAR(26)       NOT NULL,
    number                  VARCHAR(40)    NOT NULL,
    company_id              BIGINT         NOT NULL,
    branch_id               BIGINT         NOT NULL,
    supplier_id             BIGINT         NOT NULL,
    order_date              DATE           NOT NULL,
    expected_delivery_date  DATE,
    currency_code           VARCHAR(3)     NOT NULL,
    subtotal_amount         DECIMAL(18, 4) NOT NULL DEFAULT 0,
    tax_amount              DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount            DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status                  VARCHAR(32)    NOT NULL,
    approved_by             BIGINT,
    approved_at             TIMESTAMP,
    cancellation_reason     VARCHAR(500),
    notes                   VARCHAR(2000),
    version                 INT            NOT NULL DEFAULT 0,
    created_at              TIMESTAMP      NOT NULL,
    updated_at              TIMESTAMP      NOT NULL,
    created_by              BIGINT         NOT NULL,
    updated_by              BIGINT         NOT NULL,
    CONSTRAINT uk_lpo_order_uid           UNIQUE (uid),
    CONSTRAINT uk_lpo_order_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_lpo_order_company       FOREIGN KEY (company_id)    REFERENCES company (id),
    CONSTRAINT fk_lpo_order_branch        FOREIGN KEY (branch_id)     REFERENCES branch (id),
    CONSTRAINT fk_lpo_order_supplier      FOREIGN KEY (supplier_id)   REFERENCES supplier (party_id),
    CONSTRAINT fk_lpo_order_currency      FOREIGN KEY (currency_code) REFERENCES currency (code)
);
CREATE INDEX ix_lpo_order_company_status     ON lpo_order (company_id, status);
CREATE INDEX ix_lpo_order_branch_status      ON lpo_order (branch_id,  status);
CREATE INDEX ix_lpo_order_supplier           ON lpo_order (supplier_id);
-- Hot query: list LPOs for a supplier filtered by status (audit GAP 1.B).
CREATE INDEX ix_lpo_order_supplier_status    ON lpo_order (supplier_id, status);

CREATE TABLE lpo_order_line (
    id              BIGINT         NOT NULL PRIMARY KEY,
    lpo_order_id    BIGINT         NOT NULL,
    line_no         INT            NOT NULL,
    item_id         BIGINT         NOT NULL,
    uom_id          BIGINT         NOT NULL,
    ordered_qty     DECIMAL(18, 4) NOT NULL,
    received_qty    DECIMAL(18, 4) NOT NULL DEFAULT 0,
    unit_price      DECIMAL(18, 4) NOT NULL,
    vat_group_id    BIGINT         NOT NULL,
    discount_pct    DECIMAL(10, 4) NOT NULL DEFAULT 0,
    line_total      DECIMAL(18, 4) NOT NULL,
    CONSTRAINT uk_lpo_order_line_no  UNIQUE (lpo_order_id, line_no),
    CONSTRAINT fk_lpo_order_line_lpo FOREIGN KEY (lpo_order_id) REFERENCES lpo_order (id),
    CONSTRAINT fk_lpo_order_line_item FOREIGN KEY (item_id)    REFERENCES item (id),
    CONSTRAINT fk_lpo_order_line_uom  FOREIGN KEY (uom_id)     REFERENCES uom  (id),
    CONSTRAINT fk_lpo_order_line_vat  FOREIGN KEY (vat_group_id) REFERENCES vat_group (id)
);
CREATE INDEX ix_lpo_order_line_lpo ON lpo_order_line (lpo_order_id);
