-- Orbix Engine — supplier payments + per-invoice allocations (F3.4). DATA-MODEL.md §10.4, §10.5.
-- DRAFT → POSTED writes the allocations to supplier_invoice.paid_amount and
-- flips invoices to PARTIALLY_PAID / PAID. The cash-side mirror (cash_entry
-- OUT, cash_book delta) is owned by F6.1 and will subscribe to
-- SupplierPaymentPosted.v1.

CREATE TABLE supplier_payment (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    number              VARCHAR(40)    NOT NULL,
    company_id          BIGINT         NOT NULL,
    branch_id           BIGINT         NOT NULL,
    supplier_id         BIGINT         NOT NULL,
    payment_date        DATE           NOT NULL,
    method              VARCHAR(20)    NOT NULL,
    reference           VARCHAR(80),
    currency_code       VARCHAR(3)     NOT NULL,
    total_amount        DECIMAL(18, 4) NOT NULL,
    allocated_amount    DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status              VARCHAR(32)    NOT NULL,
    posted_at           TIMESTAMP,
    posted_by           BIGINT,
    notes               VARCHAR(2000),
    version             INT            NOT NULL DEFAULT 0,
    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP      NOT NULL,
    created_by          BIGINT         NOT NULL,
    updated_by          BIGINT         NOT NULL,
    CONSTRAINT uk_supplier_payment_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_supplier_payment_company  FOREIGN KEY (company_id)    REFERENCES company  (id),
    CONSTRAINT fk_supplier_payment_branch   FOREIGN KEY (branch_id)     REFERENCES branch   (id),
    CONSTRAINT fk_supplier_payment_supplier FOREIGN KEY (supplier_id)   REFERENCES supplier (party_id),
    CONSTRAINT fk_supplier_payment_currency FOREIGN KEY (currency_code) REFERENCES currency (code)
);
CREATE INDEX ix_supplier_payment_company_status ON supplier_payment (company_id, status);
CREATE INDEX ix_supplier_payment_supplier       ON supplier_payment (supplier_id);

CREATE TABLE supplier_payment_allocation (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    supplier_payment_id BIGINT         NOT NULL,
    supplier_invoice_id BIGINT         NOT NULL,
    amount              DECIMAL(18, 4) NOT NULL,
    CONSTRAINT uk_supplier_payment_alloc UNIQUE (supplier_payment_id, supplier_invoice_id),
    CONSTRAINT fk_supplier_payment_alloc_payment FOREIGN KEY (supplier_payment_id) REFERENCES supplier_payment (id),
    CONSTRAINT fk_supplier_payment_alloc_invoice FOREIGN KEY (supplier_invoice_id) REFERENCES supplier_invoice (id)
);
CREATE INDEX ix_supplier_payment_alloc_payment ON supplier_payment_allocation (supplier_payment_id);
CREATE INDEX ix_supplier_payment_alloc_invoice ON supplier_payment_allocation (supplier_invoice_id);
