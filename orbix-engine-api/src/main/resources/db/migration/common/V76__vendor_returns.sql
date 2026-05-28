-- Slice H.1 — vendor returns + credit notes (US-PROC-008 + US-PROC-009).
-- Mirror of V30 (customer_return / customer_credit_note) + V75
-- (customer_credit_note_allocation) on the AP side.
--
-- Column type note: entity-own uid columns use CHAR(26) to match UidEntity's
-- @JdbcTypeCode(SqlTypes.CHAR). Any stored reference to another entity's uid
-- (e.g. target_invoice_uid) uses VARCHAR(26) — that is the G.2 V73 lesson.

-- ── sequences ─────────────────────────────────────────────────────────────
CREATE SEQUENCE vendor_return_seq                 START WITH 1000 INCREMENT BY 50;
CREATE SEQUENCE vendor_return_line_seq            START WITH 1000 INCREMENT BY 50;
CREATE SEQUENCE vendor_credit_note_seq            START WITH 1000 INCREMENT BY 50;
CREATE SEQUENCE vendor_credit_note_allocation_seq START WITH 1000 INCREMENT BY 50;

-- ── vendor_return ─────────────────────────────────────────────────────────
CREATE TABLE vendor_return (
    id                          BIGINT         NOT NULL PRIMARY KEY,
    uid                         CHAR(26)       NOT NULL,
    number                      VARCHAR(40)    NOT NULL,
    company_id                  BIGINT         NOT NULL,
    branch_id                   BIGINT         NOT NULL,
    supplier_id                 BIGINT         NOT NULL,
    original_grn_id             BIGINT,
    original_supplier_invoice_id BIGINT,
    return_date                 DATE           NOT NULL,
    reason                      VARCHAR(20)    NOT NULL,
    total_amount                DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status                      VARCHAR(32)    NOT NULL,
    restock                     BOOLEAN        NOT NULL DEFAULT TRUE,
    posted_at                   TIMESTAMP,
    posted_by                   BIGINT,
    notes                       VARCHAR(2000),
    version                     INT            NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP      NOT NULL,
    updated_at                  TIMESTAMP      NOT NULL,
    created_by                  BIGINT         NOT NULL,
    updated_by                  BIGINT         NOT NULL,
    CONSTRAINT uk_vendor_return_uid           UNIQUE (uid),
    CONSTRAINT uk_vendor_return_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_vendor_return_company  FOREIGN KEY (company_id)                   REFERENCES company          (id),
    CONSTRAINT fk_vendor_return_branch   FOREIGN KEY (branch_id)                    REFERENCES branch           (id),
    CONSTRAINT fk_vendor_return_supplier FOREIGN KEY (supplier_id)                  REFERENCES supplier         (party_id),
    CONSTRAINT fk_vendor_return_grn      FOREIGN KEY (original_grn_id)              REFERENCES grn              (id),
    CONSTRAINT fk_vendor_return_invoice  FOREIGN KEY (original_supplier_invoice_id) REFERENCES supplier_invoice (id)
);
CREATE INDEX ix_vendor_return_company_status ON vendor_return (company_id, status);
CREATE INDEX ix_vendor_return_supplier       ON vendor_return (supplier_id);

-- ── vendor_return_line ────────────────────────────────────────────────────
CREATE TABLE vendor_return_line (
    id               BIGINT         NOT NULL PRIMARY KEY,
    vendor_return_id BIGINT         NOT NULL,
    line_no          INT            NOT NULL,
    item_id          BIGINT         NOT NULL,
    uom_id           BIGINT         NOT NULL,
    returned_qty     DECIMAL(18, 4) NOT NULL,
    unit_price       DECIMAL(18, 4) NOT NULL,
    vat_group_id     BIGINT         NOT NULL,
    tax_amount       DECIMAL(18, 4) NOT NULL DEFAULT 0,
    line_total       DECIMAL(18, 4) NOT NULL DEFAULT 0,
    original_line_id BIGINT,
    CONSTRAINT uk_vendor_return_line_no UNIQUE (vendor_return_id, line_no),
    CONSTRAINT fk_vendor_return_line_return FOREIGN KEY (vendor_return_id) REFERENCES vendor_return (id),
    CONSTRAINT fk_vendor_return_line_item   FOREIGN KEY (item_id)          REFERENCES item           (id),
    CONSTRAINT fk_vendor_return_line_uom    FOREIGN KEY (uom_id)           REFERENCES uom            (id),
    CONSTRAINT fk_vendor_return_line_vat    FOREIGN KEY (vat_group_id)     REFERENCES vat_group      (id)
);
CREATE INDEX ix_vendor_return_line_return ON vendor_return_line (vendor_return_id);

-- ── vendor_credit_note ────────────────────────────────────────────────────
CREATE TABLE vendor_credit_note (
    id               BIGINT         NOT NULL PRIMARY KEY,
    uid              CHAR(26)       NOT NULL,
    number           VARCHAR(40)    NOT NULL,
    company_id       BIGINT         NOT NULL,
    branch_id        BIGINT         NOT NULL,
    supplier_id      BIGINT         NOT NULL,
    vendor_return_id BIGINT,
    cn_date          DATE           NOT NULL,
    currency_code    VARCHAR(3)     NOT NULL,
    total_amount     DECIMAL(18, 4) NOT NULL,
    allocated_amount DECIMAL(18, 4) NOT NULL DEFAULT 0,
    status           VARCHAR(32)    NOT NULL,
    notes            VARCHAR(2000),
    version          INT            NOT NULL DEFAULT 0,
    created_at       TIMESTAMP      NOT NULL,
    updated_at       TIMESTAMP      NOT NULL,
    created_by       BIGINT         NOT NULL,
    updated_by       BIGINT         NOT NULL,
    CONSTRAINT uk_vendor_credit_note_uid           UNIQUE (uid),
    CONSTRAINT uk_vendor_credit_note_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_vendor_credit_note_company  FOREIGN KEY (company_id)       REFERENCES company        (id),
    CONSTRAINT fk_vendor_credit_note_branch   FOREIGN KEY (branch_id)        REFERENCES branch         (id),
    CONSTRAINT fk_vendor_credit_note_supplier FOREIGN KEY (supplier_id)      REFERENCES supplier       (party_id),
    CONSTRAINT fk_vendor_credit_note_return   FOREIGN KEY (vendor_return_id) REFERENCES vendor_return  (id),
    CONSTRAINT fk_vendor_credit_note_currency FOREIGN KEY (currency_code)    REFERENCES currency       (code)
);
CREATE INDEX ix_vendor_credit_note_supplier ON vendor_credit_note (supplier_id);

-- ── vendor_credit_note_allocation ─────────────────────────────────────────
CREATE TABLE vendor_credit_note_allocation (
    id                      BIGINT         NOT NULL,
    vendor_credit_note_id   BIGINT         NOT NULL,
    supplier_invoice_id     BIGINT         NOT NULL,
    amount                  DECIMAL(18, 4) NOT NULL,
    allocated_at            TIMESTAMP      NOT NULL,
    allocated_by            BIGINT         NOT NULL,
    CONSTRAINT pk_vendor_credit_note_allocation  PRIMARY KEY (id),
    CONSTRAINT fk_vcna_credit_note FOREIGN KEY (vendor_credit_note_id) REFERENCES vendor_credit_note (id),
    CONSTRAINT fk_vcna_invoice     FOREIGN KEY (supplier_invoice_id)   REFERENCES supplier_invoice   (id)
);
CREATE INDEX ix_vcna_credit_note ON vendor_credit_note_allocation (vendor_credit_note_id);
CREATE INDEX ix_vcna_invoice     ON vendor_credit_note_allocation (supplier_invoice_id);
