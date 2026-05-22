-- Orbix Engine — gift card master + append-only txn ledger (F7.1).
-- DATA-MODEL §17.6 / §17.7. Card balance is a LIABILITY, not cash — it lives
-- in this ledger, not in cash_book. Cash side of issuance lands as a regular
-- cash_entry via CashLedgerService (ref_type=GiftCardIssue,
-- gl_category=GIFT_CARD_ISSUE_PROCEEDS); redemption does NOT post a
-- cash_entry (it's a liability transfer, not a cash movement).

CREATE TABLE gift_card (
    id                     BIGINT         NOT NULL PRIMARY KEY,
    code                   VARCHAR(40)    NOT NULL,
    company_id             BIGINT         NOT NULL,
    issued_by_branch_id    BIGINT         NOT NULL,
    issued_by_user_id      BIGINT         NOT NULL,
    initial_value          DECIMAL(18, 4) NOT NULL,
    current_balance        DECIMAL(18, 4) NOT NULL,
    currency_code          VARCHAR(3)     NOT NULL,
    status                 VARCHAR(32)    NOT NULL,
    issued_at              TIMESTAMP      NOT NULL,
    expires_at             TIMESTAMP,
    version                INT            NOT NULL DEFAULT 0,
    created_at             TIMESTAMP      NOT NULL,
    updated_at             TIMESTAMP      NOT NULL,
    created_by             BIGINT         NOT NULL,
    updated_by             BIGINT         NOT NULL,
    CONSTRAINT uk_gift_card_code     UNIQUE (code),
    CONSTRAINT fk_gift_card_company  FOREIGN KEY (company_id)    REFERENCES company  (id),
    CONSTRAINT fk_gift_card_branch   FOREIGN KEY (issued_by_branch_id) REFERENCES branch (id),
    CONSTRAINT fk_gift_card_currency FOREIGN KEY (currency_code) REFERENCES currency (code)
);
CREATE INDEX ix_gift_card_status_expires ON gift_card (status, expires_at);
CREATE INDEX ix_gift_card_company         ON gift_card (company_id);

CREATE TABLE gift_card_txn (
    id                BIGINT         NOT NULL PRIMARY KEY,
    gift_card_id      BIGINT         NOT NULL,
    kind              VARCHAR(20)    NOT NULL,
    amount            DECIMAL(18, 4) NOT NULL,
    balance_after     DECIMAL(18, 4) NOT NULL,
    ref_doc_type      VARCHAR(40),
    ref_doc_id        BIGINT,
    occurred_at       TIMESTAMP      NOT NULL,
    by_user_id        BIGINT         NOT NULL,
    created_at        TIMESTAMP      NOT NULL,
    CONSTRAINT fk_gift_card_txn_card FOREIGN KEY (gift_card_id) REFERENCES gift_card (id),
    CONSTRAINT uk_gift_card_txn_ref  UNIQUE (gift_card_id, ref_doc_type, ref_doc_id, kind)
);
CREATE INDEX ix_gift_card_txn_card_at ON gift_card_txn (gift_card_id, occurred_at);
