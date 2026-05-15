-- Orbix Engine — supervisor cash adjustment + end-of-day banking (F6.3).
-- Two human-driven cash-ledger writes that don't have a natural source doc
-- elsewhere, so they get their own audit-doc tables. The cash entries they
-- post use the row id as their idempotency ref_id, so a replayed call
-- collides on the F6.1 (ref_type, ref_id, direction) UNIQUE.

CREATE TABLE cash_adjustment (
    id              BIGINT         NOT NULL PRIMARY KEY,
    company_id      BIGINT         NOT NULL,
    branch_id       BIGINT         NOT NULL,
    business_date   DATE           NOT NULL,
    account         VARCHAR(40)    NOT NULL,
    direction       VARCHAR(10)    NOT NULL,
    amount          DECIMAL(18, 4) NOT NULL,
    currency_code   VARCHAR(3)     NOT NULL,
    reason          VARCHAR(2000)  NOT NULL,
    at              TIMESTAMP      NOT NULL,
    posted_by       BIGINT         NOT NULL,
    created_at      TIMESTAMP      NOT NULL,
    CONSTRAINT fk_cash_adjustment_company  FOREIGN KEY (company_id)    REFERENCES company  (id),
    CONSTRAINT fk_cash_adjustment_branch   FOREIGN KEY (branch_id)     REFERENCES branch   (id),
    CONSTRAINT fk_cash_adjustment_currency FOREIGN KEY (currency_code) REFERENCES currency (code)
);
CREATE INDEX ix_cash_adjustment_branch_date ON cash_adjustment (branch_id, business_date);
CREATE INDEX ix_cash_adjustment_company     ON cash_adjustment (company_id);

CREATE TABLE bank_deposit (
    id              BIGINT         NOT NULL PRIMARY KEY,
    company_id      BIGINT         NOT NULL,
    branch_id       BIGINT         NOT NULL,
    business_date   DATE           NOT NULL,
    amount          DECIMAL(18, 4) NOT NULL,
    currency_code   VARCHAR(3)     NOT NULL,
    reference       VARCHAR(80)    NOT NULL,
    notes           VARCHAR(2000),
    at              TIMESTAMP      NOT NULL,
    posted_by       BIGINT         NOT NULL,
    created_at      TIMESTAMP      NOT NULL,
    CONSTRAINT fk_bank_deposit_company  FOREIGN KEY (company_id)    REFERENCES company  (id),
    CONSTRAINT fk_bank_deposit_branch   FOREIGN KEY (branch_id)     REFERENCES branch   (id),
    CONSTRAINT fk_bank_deposit_currency FOREIGN KEY (currency_code) REFERENCES currency (code)
);
CREATE INDEX ix_bank_deposit_branch_date ON bank_deposit (branch_id, business_date);
CREATE INDEX ix_bank_deposit_company     ON bank_deposit (company_id);
