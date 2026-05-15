-- Orbix Engine — customer returns + credit notes (F4.4). DATA-MODEL.md §6.7-§6.9.
-- DRAFT → POSTED writes the RETURN_IN (restock) or DAMAGE (non-restock) stock
-- moves; POSTED → CREDITED issues a customer_credit_note. Credit-note
-- allocation to open invoices comes with a later slice.

CREATE TABLE customer_return (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    number              VARCHAR(40)    NOT NULL,
    company_id          BIGINT         NOT NULL,
    branch_id           BIGINT         NOT NULL,
    customer_id         BIGINT         NOT NULL,
    original_invoice_id BIGINT,
    return_date         DATE           NOT NULL,
    reason              VARCHAR(20)    NOT NULL,
    total_amount        DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status              VARCHAR(32)    NOT NULL,
    restock             BOOLEAN        NOT NULL DEFAULT TRUE,
    posted_at           TIMESTAMP,
    posted_by           BIGINT,
    notes               VARCHAR(2000),
    version             INT            NOT NULL DEFAULT 0,
    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP      NOT NULL,
    created_by          BIGINT         NOT NULL,
    updated_by          BIGINT         NOT NULL,
    CONSTRAINT uk_customer_return_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_customer_return_company  FOREIGN KEY (company_id)          REFERENCES company       (id),
    CONSTRAINT fk_customer_return_branch   FOREIGN KEY (branch_id)           REFERENCES branch        (id),
    CONSTRAINT fk_customer_return_customer FOREIGN KEY (customer_id)         REFERENCES customer      (party_id),
    CONSTRAINT fk_customer_return_invoice  FOREIGN KEY (original_invoice_id) REFERENCES sales_invoice (id)
);
CREATE INDEX ix_customer_return_company_status ON customer_return (company_id, status);
CREATE INDEX ix_customer_return_customer       ON customer_return (customer_id);

CREATE TABLE customer_return_line (
    id                    BIGINT         NOT NULL PRIMARY KEY,
    customer_return_id    BIGINT         NOT NULL,
    line_no               INT            NOT NULL,
    item_id               BIGINT         NOT NULL,
    uom_id                BIGINT         NOT NULL,
    returned_qty          DECIMAL(18, 4) NOT NULL,
    unit_price            DECIMAL(18, 4) NOT NULL,
    vat_group_id          BIGINT         NOT NULL,
    tax_amount            DECIMAL(18, 4) NOT NULL DEFAULT 0,
    line_total            DECIMAL(18, 4) NOT NULL DEFAULT 0,
    original_line_id      BIGINT,
    CONSTRAINT uk_customer_return_line_no UNIQUE (customer_return_id, line_no),
    CONSTRAINT fk_customer_return_line_return FOREIGN KEY (customer_return_id) REFERENCES customer_return (id),
    CONSTRAINT fk_customer_return_line_item   FOREIGN KEY (item_id)            REFERENCES item            (id),
    CONSTRAINT fk_customer_return_line_uom    FOREIGN KEY (uom_id)             REFERENCES uom             (id),
    CONSTRAINT fk_customer_return_line_vat    FOREIGN KEY (vat_group_id)       REFERENCES vat_group       (id)
);
CREATE INDEX ix_customer_return_line_return ON customer_return_line (customer_return_id);

CREATE TABLE customer_credit_note (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    number              VARCHAR(40)    NOT NULL,
    company_id          BIGINT         NOT NULL,
    branch_id           BIGINT         NOT NULL,
    customer_id         BIGINT         NOT NULL,
    customer_return_id  BIGINT,
    cn_date             DATE           NOT NULL,
    currency_code       VARCHAR(3)     NOT NULL,
    total_amount        DECIMAL(18, 4) NOT NULL,
    allocated_amount    DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status              VARCHAR(32)    NOT NULL,
    notes               VARCHAR(2000),
    version             INT            NOT NULL DEFAULT 0,
    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP      NOT NULL,
    created_by          BIGINT         NOT NULL,
    updated_by          BIGINT         NOT NULL,
    CONSTRAINT uk_customer_credit_note_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_customer_credit_note_company  FOREIGN KEY (company_id)         REFERENCES company         (id),
    CONSTRAINT fk_customer_credit_note_branch   FOREIGN KEY (branch_id)          REFERENCES branch          (id),
    CONSTRAINT fk_customer_credit_note_customer FOREIGN KEY (customer_id)        REFERENCES customer        (party_id),
    CONSTRAINT fk_customer_credit_note_return   FOREIGN KEY (customer_return_id) REFERENCES customer_return (id),
    CONSTRAINT fk_customer_credit_note_currency FOREIGN KEY (currency_code)      REFERENCES currency        (code)
);
CREATE INDEX ix_customer_credit_note_customer ON customer_credit_note (customer_id);
