-- Orbix Engine — supplier invoices + 3-way match against GRNs (F3.3).
-- DATA-MODEL.md §5.7, §5.8. DRAFT → POSTED (the matched invoice opens a
-- payable). PARTIALLY_PAID / PAID transitions land with F3.4. DRAFT → CANCELLED
-- and POSTED → CANCELLED both allowed; cancellation does NOT auto-reverse
-- allocations, the operator must adjust the GRN side separately.

CREATE TABLE supplier_invoice (
    id                       BIGINT         NOT NULL PRIMARY KEY,
    number                   VARCHAR(40)    NOT NULL,
    supplier_invoice_no      VARCHAR(80)    NOT NULL,
    company_id               BIGINT         NOT NULL,
    branch_id                BIGINT         NOT NULL,
    supplier_id              BIGINT         NOT NULL,
    invoice_date             DATE           NOT NULL,
    due_date                 DATE           NOT NULL,
    currency_code            VARCHAR(3)     NOT NULL,
    subtotal_amount          DECIMAL(18, 4) NOT NULL DEFAULT 0,
    tax_amount               DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount             DECIMAL(18, 4) NOT NULL DEFAULT 0,
    paid_amount              DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status                   VARCHAR(32)    NOT NULL,
    posted_at                TIMESTAMP,
    posted_by                BIGINT,
    notes                    VARCHAR(2000),
    version                  INT            NOT NULL DEFAULT 0,
    created_at               TIMESTAMP      NOT NULL,
    updated_at               TIMESTAMP      NOT NULL,
    created_by               BIGINT         NOT NULL,
    updated_by               BIGINT         NOT NULL,
    CONSTRAINT uk_supplier_invoice_branch_number UNIQUE (branch_id, number),
    CONSTRAINT uk_supplier_invoice_supplier_no   UNIQUE (supplier_id, supplier_invoice_no),
    CONSTRAINT fk_supplier_invoice_company  FOREIGN KEY (company_id)    REFERENCES company  (id),
    CONSTRAINT fk_supplier_invoice_branch   FOREIGN KEY (branch_id)     REFERENCES branch   (id),
    CONSTRAINT fk_supplier_invoice_supplier FOREIGN KEY (supplier_id)   REFERENCES supplier (party_id),
    CONSTRAINT fk_supplier_invoice_currency FOREIGN KEY (currency_code) REFERENCES currency (code)
);
CREATE INDEX ix_supplier_invoice_company_status ON supplier_invoice (company_id, status);
CREATE INDEX ix_supplier_invoice_supplier       ON supplier_invoice (supplier_id);

CREATE TABLE supplier_invoice_grn (
    supplier_invoice_id BIGINT         NOT NULL,
    grn_id              BIGINT         NOT NULL,
    amount              DECIMAL(18, 4) NOT NULL,
    PRIMARY KEY (supplier_invoice_id, grn_id),
    CONSTRAINT fk_supplier_invoice_grn_inv FOREIGN KEY (supplier_invoice_id) REFERENCES supplier_invoice (id),
    CONSTRAINT fk_supplier_invoice_grn_grn FOREIGN KEY (grn_id)              REFERENCES grn              (id)
);
CREATE INDEX ix_supplier_invoice_grn_grn ON supplier_invoice_grn (grn_id);
