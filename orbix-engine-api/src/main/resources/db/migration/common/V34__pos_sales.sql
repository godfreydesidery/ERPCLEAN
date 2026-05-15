-- Orbix Engine — POS sales + lines + payments (F5.2). DATA-MODEL.md §7.3/§7.4/§7.5,
-- with the Phase 1.1 additions in §17.12 (section_id REQUIRED, kind enum,
-- refunded_from_sale_id self-FK). POS sales are never DRAFT — they are
-- committed locally on the till first, then pushed to the server as POSTED.
-- Idempotency: client_op_id is a UUID v7 unique across the company.

CREATE TABLE pos_sale (
    id                       BIGINT         NOT NULL PRIMARY KEY,
    number                   VARCHAR(60)    NOT NULL,
    client_op_id             VARCHAR(40)    NOT NULL,
    till_session_id          BIGINT         NOT NULL,
    till_id                  BIGINT         NOT NULL,
    branch_id                BIGINT         NOT NULL,
    company_id               BIGINT         NOT NULL,
    section_id               BIGINT         NOT NULL,
    customer_id              BIGINT         NOT NULL,
    cashier_id               BIGINT         NOT NULL,
    supervisor_id            BIGINT,
    kind                     VARCHAR(20)    NOT NULL,
    refunded_from_sale_id    BIGINT,
    sale_at                  TIMESTAMP      NOT NULL,
    server_at                TIMESTAMP      NOT NULL,
    business_date            DATE           NOT NULL,
    subtotal_amount          DECIMAL(18, 4) NOT NULL,
    discount_amount          DECIMAL(18, 4) NOT NULL DEFAULT 0,
    tax_amount               DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount             DECIMAL(18, 4) NOT NULL,
    tendered_amount          DECIMAL(18, 4) NOT NULL,
    change_amount            DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status                   VARCHAR(32)    NOT NULL,
    voided_at                TIMESTAMP,
    voided_by                BIGINT,
    void_reason              VARCHAR(200),
    fiscal_signature         VARCHAR(200),
    notes                    VARCHAR(2000),
    version                  INT            NOT NULL DEFAULT 0,
    CONSTRAINT uk_pos_sale_company_number  UNIQUE (company_id, number),
    CONSTRAINT uk_pos_sale_client_op       UNIQUE (company_id, client_op_id),
    CONSTRAINT fk_pos_sale_company    FOREIGN KEY (company_id)            REFERENCES company      (id),
    CONSTRAINT fk_pos_sale_branch     FOREIGN KEY (branch_id)             REFERENCES branch       (id),
    CONSTRAINT fk_pos_sale_section    FOREIGN KEY (section_id)            REFERENCES section      (id),
    CONSTRAINT fk_pos_sale_session    FOREIGN KEY (till_session_id)       REFERENCES till_session (id),
    CONSTRAINT fk_pos_sale_till       FOREIGN KEY (till_id)               REFERENCES till         (id),
    CONSTRAINT fk_pos_sale_customer   FOREIGN KEY (customer_id)           REFERENCES customer     (party_id),
    CONSTRAINT fk_pos_sale_refund_src FOREIGN KEY (refunded_from_sale_id) REFERENCES pos_sale     (id)
);
CREATE INDEX ix_pos_sale_session       ON pos_sale (till_session_id);
CREATE INDEX ix_pos_sale_company_date  ON pos_sale (company_id, business_date);
CREATE INDEX ix_pos_sale_branch_date   ON pos_sale (branch_id, business_date);
CREATE INDEX ix_pos_sale_customer      ON pos_sale (customer_id);

CREATE TABLE pos_sale_line (
    id              BIGINT         NOT NULL PRIMARY KEY,
    pos_sale_id     BIGINT         NOT NULL,
    line_no         INT            NOT NULL,
    item_id         BIGINT         NOT NULL,
    uom_id          BIGINT         NOT NULL,
    qty             DECIMAL(18, 4) NOT NULL,
    unit_price      DECIMAL(18, 4) NOT NULL,
    discount_pct    DECIMAL(10, 4) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(18, 4) NOT NULL DEFAULT 0,
    vat_group_id    BIGINT         NOT NULL,
    tax_amount      DECIMAL(18, 4) NOT NULL DEFAULT 0,
    line_total      DECIMAL(18, 4) NOT NULL,
    cost_amount     DECIMAL(18, 4) NOT NULL,
    promotion_id    BIGINT,
    CONSTRAINT uk_pos_sale_line_no UNIQUE (pos_sale_id, line_no),
    CONSTRAINT fk_pos_sale_line_sale FOREIGN KEY (pos_sale_id) REFERENCES pos_sale (id),
    CONSTRAINT fk_pos_sale_line_item FOREIGN KEY (item_id)     REFERENCES item     (id),
    CONSTRAINT fk_pos_sale_line_uom  FOREIGN KEY (uom_id)      REFERENCES uom      (id),
    CONSTRAINT fk_pos_sale_line_vat  FOREIGN KEY (vat_group_id) REFERENCES vat_group (id)
);
CREATE INDEX ix_pos_sale_line_sale ON pos_sale_line (pos_sale_id);

CREATE TABLE pos_payment (
    id              BIGINT         NOT NULL PRIMARY KEY,
    pos_sale_id     BIGINT         NOT NULL,
    method          VARCHAR(20)    NOT NULL,
    amount          DECIMAL(18, 4) NOT NULL,
    reference       VARCHAR(80),
    terminal_id     VARCHAR(40),
    last4           VARCHAR(4),
    CONSTRAINT fk_pos_payment_sale FOREIGN KEY (pos_sale_id) REFERENCES pos_sale (id)
);
CREATE INDEX ix_pos_payment_sale ON pos_payment (pos_sale_id);
