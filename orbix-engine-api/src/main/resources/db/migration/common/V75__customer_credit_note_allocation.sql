-- Slice H — credit-note → invoice allocations. Mirror of receipt_allocation.
-- Many allocations per credit note (partial applies); many allocations per
-- invoice (multiple credits against one invoice).
CREATE TABLE customer_credit_note_allocation (
    id                       BIGINT         NOT NULL,
    customer_credit_note_id  BIGINT         NOT NULL,
    sales_invoice_id         BIGINT         NOT NULL,
    amount                   DECIMAL(18, 4) NOT NULL,
    allocated_at             TIMESTAMP      NOT NULL,
    allocated_by             BIGINT         NOT NULL,
    CONSTRAINT pk_customer_credit_note_allocation  PRIMARY KEY (id),
    CONSTRAINT fk_ccna_credit_note  FOREIGN KEY (customer_credit_note_id) REFERENCES customer_credit_note (id),
    CONSTRAINT fk_ccna_invoice      FOREIGN KEY (sales_invoice_id)        REFERENCES sales_invoice        (id)
);

CREATE INDEX ix_ccna_credit_note ON customer_credit_note_allocation (customer_credit_note_id);
CREATE INDEX ix_ccna_invoice     ON customer_credit_note_allocation (sales_invoice_id);

CREATE SEQUENCE customer_credit_note_allocation_seq START WITH 1000 INCREMENT BY 50;
