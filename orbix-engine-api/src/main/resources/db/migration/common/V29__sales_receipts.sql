-- Orbix Engine — customer receipts + per-invoice allocations (F4.3). DATA-MODEL.md §6.5/§6.6.
-- DRAFT → POSTED advances sales_invoice.paid_amount and flips PARTIALLY_PAID /
-- PAID. unallocated_amount stores any cash that landed beyond the matched
-- invoices — future work routes that to customer credit.

CREATE TABLE sales_receipt (
    id                 BIGINT         NOT NULL PRIMARY KEY,
    uid                CHAR(26)       NOT NULL,
    number             VARCHAR(40)    NOT NULL,
    company_id         BIGINT         NOT NULL,
    branch_id          BIGINT         NOT NULL,
    customer_id        BIGINT         NOT NULL,
    receipt_date       DATE           NOT NULL,
    method             VARCHAR(20)    NOT NULL,
    reference          VARCHAR(80),
    currency_code      VARCHAR(3)     NOT NULL,
    total_amount       DECIMAL(18, 4) NOT NULL,
    allocated_amount   DECIMAL(18, 4) NOT NULL DEFAULT 0,
    unallocated_amount DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status             VARCHAR(32)    NOT NULL,
    posted_at          TIMESTAMP,
    posted_by          BIGINT,
    notes              VARCHAR(2000),
    version            INT            NOT NULL DEFAULT 0,
    created_at         TIMESTAMP      NOT NULL,
    updated_at         TIMESTAMP      NOT NULL,
    created_by         BIGINT         NOT NULL,
    updated_by         BIGINT         NOT NULL,
    CONSTRAINT uk_sales_receipt_uid           UNIQUE (uid),
    CONSTRAINT uk_sales_receipt_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_sales_receipt_company  FOREIGN KEY (company_id)    REFERENCES company  (id),
    CONSTRAINT fk_sales_receipt_branch   FOREIGN KEY (branch_id)     REFERENCES branch   (id),
    CONSTRAINT fk_sales_receipt_customer FOREIGN KEY (customer_id)   REFERENCES customer (party_id),
    CONSTRAINT fk_sales_receipt_currency FOREIGN KEY (currency_code) REFERENCES currency (code)
);
CREATE INDEX ix_sales_receipt_company_status ON sales_receipt (company_id, status);
CREATE INDEX ix_sales_receipt_customer       ON sales_receipt (customer_id);

CREATE TABLE receipt_allocation (
    id                 BIGINT         NOT NULL PRIMARY KEY,
    sales_receipt_id   BIGINT         NOT NULL,
    sales_invoice_id   BIGINT         NOT NULL,
    amount             DECIMAL(18, 4) NOT NULL,
    allocated_at       TIMESTAMP      NOT NULL,
    allocated_by       BIGINT         NOT NULL,
    CONSTRAINT uk_receipt_alloc UNIQUE (sales_receipt_id, sales_invoice_id),
    CONSTRAINT fk_receipt_alloc_receipt FOREIGN KEY (sales_receipt_id) REFERENCES sales_receipt (id),
    CONSTRAINT fk_receipt_alloc_invoice FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoice (id)
);
CREATE INDEX ix_receipt_alloc_receipt ON receipt_allocation (sales_receipt_id);
CREATE INDEX ix_receipt_alloc_invoice ON receipt_allocation (sales_invoice_id);
