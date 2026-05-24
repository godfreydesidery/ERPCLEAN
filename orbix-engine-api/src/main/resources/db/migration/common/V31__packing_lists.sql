-- Orbix Engine — packing lists (F4.5). DATA-MODEL.md §6.10/§6.11.
-- DRAFT → DISPATCHED → DELIVERED → terminal; DRAFT → CANCELLED. Stock is
-- decremented by the parent sales_invoice on post (F4.2); the packing list is
-- a delivery-tracking document on top of that.

CREATE TABLE packing_list (
    id                BIGINT         NOT NULL PRIMARY KEY,
    uid               CHAR(26)       NOT NULL,
    number            VARCHAR(40)    NOT NULL,
    company_id        BIGINT         NOT NULL,
    branch_id         BIGINT         NOT NULL,
    sales_invoice_id  BIGINT         NOT NULL,
    dispatch_date     DATE           NOT NULL,
    driver_name       VARCHAR(120),
    vehicle_no        VARCHAR(40),
    status            VARCHAR(32)    NOT NULL,
    delivered_at      TIMESTAMP,
    delivered_by      BIGINT,
    notes             VARCHAR(2000),
    version           INT            NOT NULL DEFAULT 0,
    created_at        TIMESTAMP      NOT NULL,
    updated_at        TIMESTAMP      NOT NULL,
    created_by        BIGINT         NOT NULL,
    updated_by        BIGINT         NOT NULL,
    CONSTRAINT uk_packing_list_uid           UNIQUE (uid),
    CONSTRAINT uk_packing_list_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_packing_list_company FOREIGN KEY (company_id)       REFERENCES company       (id),
    CONSTRAINT fk_packing_list_branch  FOREIGN KEY (branch_id)        REFERENCES branch        (id),
    CONSTRAINT fk_packing_list_invoice FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoice (id)
);
CREATE INDEX ix_packing_list_invoice ON packing_list (sales_invoice_id);
CREATE INDEX ix_packing_list_company_status ON packing_list (company_id, status);

CREATE TABLE packing_list_line (
    id                    BIGINT         NOT NULL PRIMARY KEY,
    packing_list_id       BIGINT         NOT NULL,
    sales_invoice_line_id BIGINT         NOT NULL,
    qty                   DECIMAL(18, 4) NOT NULL,
    CONSTRAINT fk_packing_list_line_pl   FOREIGN KEY (packing_list_id)       REFERENCES packing_list      (id),
    CONSTRAINT fk_packing_list_line_line FOREIGN KEY (sales_invoice_line_id) REFERENCES sales_invoice_line(id)
);
CREATE INDEX ix_packing_list_line_pl ON packing_list_line (packing_list_id);
