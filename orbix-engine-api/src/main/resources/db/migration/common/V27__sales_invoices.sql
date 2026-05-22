-- Orbix Engine — back-office sales invoices (F4.2). DATA-MODEL.md §6.3, §6.4.
-- DRAFT → POSTED writes stock_move outbound rows + opens debt (for CREDIT) or
-- a cash entry (for CASH; mirrored by F6.1's subscriber). VOIDED is a same-day
-- reversal; CANCELLED is for DRAFTs that never posted. PARTIALLY_PAID / PAID
-- flips come from F4.3 sales-receipt allocations.

CREATE TABLE sales_invoice (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    number              VARCHAR(40)    NOT NULL,
    company_id          BIGINT         NOT NULL,
    branch_id           BIGINT         NOT NULL,
    customer_id         BIGINT         NOT NULL,
    sales_agent_id      BIGINT,
    invoice_date        DATE           NOT NULL,
    due_date            DATE,
    payment_terms       VARCHAR(20)    NOT NULL,
    currency_code       VARCHAR(3)     NOT NULL,
    price_list_id       BIGINT         NOT NULL,
    subtotal_amount     DECIMAL(18, 4) NOT NULL DEFAULT 0,
    discount_amount     DECIMAL(18, 4) NOT NULL DEFAULT 0,
    tax_amount          DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount        DECIMAL(18, 4) NOT NULL DEFAULT 0,
    paid_amount         DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status              VARCHAR(32)    NOT NULL,
    posted_at           TIMESTAMP,
    posted_by           BIGINT,
    posted_business_date DATE,
    voided_at           TIMESTAMP,
    voided_by           BIGINT,
    void_reason         VARCHAR(200),
    reference           VARCHAR(80),
    notes               VARCHAR(2000),
    version             INT            NOT NULL DEFAULT 0,
    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP      NOT NULL,
    created_by          BIGINT         NOT NULL,
    updated_by          BIGINT         NOT NULL,
    CONSTRAINT uk_sales_invoice_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_sales_invoice_company    FOREIGN KEY (company_id)    REFERENCES company    (id),
    CONSTRAINT fk_sales_invoice_branch     FOREIGN KEY (branch_id)     REFERENCES branch     (id),
    CONSTRAINT fk_sales_invoice_customer   FOREIGN KEY (customer_id)   REFERENCES customer   (party_id),
    CONSTRAINT fk_sales_invoice_currency   FOREIGN KEY (currency_code) REFERENCES currency   (code),
    CONSTRAINT fk_sales_invoice_price_list FOREIGN KEY (price_list_id) REFERENCES price_list (id)
);
CREATE INDEX ix_sales_invoice_company_status ON sales_invoice (company_id, status);
CREATE INDEX ix_sales_invoice_customer       ON sales_invoice (customer_id);
CREATE INDEX ix_sales_invoice_branch_date    ON sales_invoice (branch_id, invoice_date);

CREATE TABLE sales_invoice_line (
    id                 BIGINT         NOT NULL PRIMARY KEY,
    sales_invoice_id   BIGINT         NOT NULL,
    line_no            INT            NOT NULL,
    item_id            BIGINT         NOT NULL,
    uom_id             BIGINT         NOT NULL,
    qty                DECIMAL(18, 4) NOT NULL,
    unit_price         DECIMAL(18, 4) NOT NULL,
    discount_pct       DECIMAL(10, 4) NOT NULL DEFAULT 0,
    discount_amount    DECIMAL(18, 4) NOT NULL DEFAULT 0,
    vat_group_id       BIGINT         NOT NULL,
    tax_amount         DECIMAL(18, 4) NOT NULL DEFAULT 0,
    line_total         DECIMAL(18, 4) NOT NULL DEFAULT 0,
    cost_amount        DECIMAL(18, 4) NOT NULL DEFAULT 0,
    promotion_id       BIGINT,
    CONSTRAINT uk_sales_invoice_line_no UNIQUE (sales_invoice_id, line_no),
    CONSTRAINT fk_sales_invoice_line_invoice FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoice (id),
    CONSTRAINT fk_sales_invoice_line_item    FOREIGN KEY (item_id)          REFERENCES item          (id),
    CONSTRAINT fk_sales_invoice_line_uom     FOREIGN KEY (uom_id)           REFERENCES uom           (id),
    CONSTRAINT fk_sales_invoice_line_vat     FOREIGN KEY (vat_group_id)     REFERENCES vat_group     (id)
);
CREATE INDEX ix_sales_invoice_line_invoice ON sales_invoice_line (sales_invoice_id);
