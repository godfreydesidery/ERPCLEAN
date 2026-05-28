-- Slice G.2 — debt write-off requests. Single table covers AR + AP via
-- targetKind discriminator. PENDING_APPROVAL → POSTED or REJECTED.
CREATE TABLE debt_write_off (
    id                    BIGINT        NOT NULL,
    uid                   CHAR(26)      NOT NULL,
    company_id            BIGINT        NOT NULL,
    branch_id             BIGINT        NOT NULL,
    target_kind           VARCHAR(32)   NOT NULL,           -- CUSTOMER_INVOICE | SUPPLIER_INVOICE
    target_invoice_id     BIGINT        NOT NULL,
    target_invoice_uid    CHAR(26)      NOT NULL,
    amount                DECIMAL(19,4) NOT NULL,
    currency_code         VARCHAR(3)    NOT NULL,
    reason                VARCHAR(2000) NOT NULL,
    status                VARCHAR(32)   NOT NULL,           -- PENDING_APPROVAL | POSTED | REJECTED
    requested_by_user_id  BIGINT        NOT NULL,
    requested_at          TIMESTAMP     NOT NULL,
    approved_by_user_id   BIGINT,
    approved_at           TIMESTAMP,
    posted_at             TIMESTAMP,
    rejected_at           TIMESTAMP,
    reason_for_reject     VARCHAR(2000),
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            TIMESTAMP     NOT NULL,
    updated_at            TIMESTAMP     NOT NULL,
    CONSTRAINT pk_debt_write_off     PRIMARY KEY (id),
    CONSTRAINT uk_debt_write_off_uid UNIQUE (uid)
);

CREATE INDEX ix_debt_write_off_company_status
    ON debt_write_off (company_id, status);
CREATE INDEX ix_debt_write_off_branch_requested
    ON debt_write_off (company_id, branch_id, requested_at DESC);
CREATE INDEX ix_debt_write_off_target
    ON debt_write_off (target_kind, target_invoice_id);

CREATE SEQUENCE debt_write_off_seq START WITH 1000 INCREMENT BY 50;
