-- Orbix Engine — cash module foundation (F6.1). DATA-MODEL.md §10.2, §10.3.
-- `cash_entry` is the append-only ledger; `cash_book` is the per-branch /
-- per-account / per-business-date opening/in/out/closing projection.

CREATE TABLE cash_entry (
    id               BIGINT         NOT NULL PRIMARY KEY,
    at               TIMESTAMP      NOT NULL,
    company_id       BIGINT         NOT NULL,
    branch_id        BIGINT         NOT NULL,
    business_date    DATE           NOT NULL,
    account          VARCHAR(40)    NOT NULL,
    direction        VARCHAR(10)    NOT NULL,
    amount           DECIMAL(18, 4) NOT NULL,
    currency_code    VARCHAR(3)     NOT NULL,
    ref_type         VARCHAR(40)    NOT NULL,
    ref_id           BIGINT         NOT NULL,
    gl_category      VARCHAR(40)    NOT NULL,
    notes            VARCHAR(2000),
    actor_id         BIGINT         NOT NULL,
    created_at       TIMESTAMP      NOT NULL,
    -- (ref_type, ref_id, direction) is the idempotency key: a replayed
    -- producer call resolves to the same triple and the constraint kicks in.
    CONSTRAINT uk_cash_entry_ref UNIQUE (ref_type, ref_id, direction),
    CONSTRAINT fk_cash_entry_company  FOREIGN KEY (company_id)    REFERENCES company  (id),
    CONSTRAINT fk_cash_entry_branch   FOREIGN KEY (branch_id)     REFERENCES branch   (id),
    CONSTRAINT fk_cash_entry_currency FOREIGN KEY (currency_code) REFERENCES currency (code)
);
CREATE INDEX ix_cash_entry_branch_date_account ON cash_entry (branch_id, business_date, account);
CREATE INDEX ix_cash_entry_company             ON cash_entry (company_id);
CREATE INDEX ix_cash_entry_ref                 ON cash_entry (ref_type, ref_id);

CREATE TABLE cash_book (
    branch_id        BIGINT         NOT NULL,
    account          VARCHAR(40)    NOT NULL,
    business_date    DATE           NOT NULL,
    company_id       BIGINT         NOT NULL,
    currency_code    VARCHAR(3)     NOT NULL,
    opening_amount   DECIMAL(18, 4) NOT NULL DEFAULT 0,
    in_amount        DECIMAL(18, 4) NOT NULL DEFAULT 0,
    out_amount       DECIMAL(18, 4) NOT NULL DEFAULT 0,
    closing_amount   DECIMAL(18, 4) NOT NULL DEFAULT 0,
    version          INT            NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP      NOT NULL,
    CONSTRAINT pk_cash_book PRIMARY KEY (branch_id, account, business_date),
    CONSTRAINT fk_cash_book_company  FOREIGN KEY (company_id)    REFERENCES company  (id),
    CONSTRAINT fk_cash_book_branch   FOREIGN KEY (branch_id)     REFERENCES branch   (id),
    CONSTRAINT fk_cash_book_currency FOREIGN KEY (currency_code) REFERENCES currency (code)
);
CREATE INDEX ix_cash_book_company_date ON cash_book (company_id, business_date);
