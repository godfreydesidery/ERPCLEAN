-- Orbix Engine — customer orders (layby + pre-order) (F7.2). DATA-MODEL §17.8 / §17.9 / §17.10.
-- Distinct from sales_invoice: ownership doesn't transfer until COLLECTED.
-- Stock reservation for LAYBY is tracked via item_branch_balance.qty_reserved
-- and audited via stock_move rows of move_type = RESERVED (see StockMoveType
-- additions in F2.5 / Phase 1.1).
--
-- Lifecycle: DRAFT -> RESERVED -> DEPOSIT_PAID -> PARTIALLY_PAID -> READY -> COLLECTED
-- with branches to CANCELLED (any pre-COLLECTED) / EXPIRED (post-reserved_until job).
--
-- Payments are an append-only ledger; each row writes a paired cash_entry via
-- CashLedgerService (CASH / BANK_TRANSFER / MOBILE_MONEY / CHEQUE) or, for the
-- GIFT_CARD method, a gift_card_txn via GiftCardService.

CREATE TABLE customer_order (
    id                       BIGINT         NOT NULL PRIMARY KEY,
    uid                      CHAR(26)       NOT NULL,
    number                   VARCHAR(40)    NOT NULL,
    company_id               BIGINT         NOT NULL,
    branch_id                BIGINT         NOT NULL,
    section_id               BIGINT,
    customer_id              BIGINT         NOT NULL,
    type                     VARCHAR(20)    NOT NULL,
    status                   VARCHAR(32)    NOT NULL,
    currency_code            VARCHAR(3)     NOT NULL,
    reserved_until           TIMESTAMP,
    deposit_required_amount  DECIMAL(18, 4) NOT NULL DEFAULT 0,
    deposit_paid_amount      DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_amount             DECIMAL(18, 4) NOT NULL DEFAULT 0,
    paid_amount              DECIMAL(18, 4) NOT NULL DEFAULT 0,
    balance_due              DECIMAL(18, 4) NOT NULL DEFAULT 0,
    refunded_amount          DECIMAL(18, 4) NOT NULL DEFAULT 0,
    forfeited_amount         DECIMAL(18, 4) NOT NULL DEFAULT 0,
    reserved_at              TIMESTAMP,
    reserved_by              BIGINT,
    collected_at             TIMESTAMP,
    collected_by             BIGINT,
    cancelled_at             TIMESTAMP,
    cancelled_by             BIGINT,
    cancel_reason            VARCHAR(200),
    expired_at               TIMESTAMP,
    notes                    VARCHAR(2000),
    version                  INT            NOT NULL DEFAULT 0,
    created_at               TIMESTAMP      NOT NULL,
    updated_at               TIMESTAMP      NOT NULL,
    created_by               BIGINT         NOT NULL,
    updated_by               BIGINT         NOT NULL,
    CONSTRAINT uk_customer_order_uid           UNIQUE (uid),
    CONSTRAINT uk_customer_order_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_customer_order_company  FOREIGN KEY (company_id)    REFERENCES company  (id),
    CONSTRAINT fk_customer_order_branch   FOREIGN KEY (branch_id)     REFERENCES branch   (id),
    CONSTRAINT fk_customer_order_section  FOREIGN KEY (section_id)    REFERENCES section  (id),
    CONSTRAINT fk_customer_order_customer FOREIGN KEY (customer_id)   REFERENCES customer (party_id),
    CONSTRAINT fk_customer_order_currency FOREIGN KEY (currency_code) REFERENCES currency (code)
);
CREATE INDEX ix_customer_order_company_status   ON customer_order (company_id, status);
CREATE INDEX ix_customer_order_customer         ON customer_order (customer_id);
CREATE INDEX ix_customer_order_status_reserved  ON customer_order (status, reserved_until);

CREATE TABLE customer_order_line (
    id                     BIGINT         NOT NULL PRIMARY KEY,
    customer_order_id      BIGINT         NOT NULL,
    line_no                INT            NOT NULL,
    item_id                BIGINT         NOT NULL,
    uom_id                 BIGINT         NOT NULL,
    qty                    DECIMAL(18, 4) NOT NULL,
    unit_price             DECIMAL(18, 4) NOT NULL,
    discount_amount        DECIMAL(18, 4) NOT NULL DEFAULT 0,
    line_total             DECIMAL(18, 4) NOT NULL DEFAULT 0,
    notes                  VARCHAR(200),
    CONSTRAINT uk_customer_order_line_no UNIQUE (customer_order_id, line_no),
    CONSTRAINT fk_customer_order_line_order FOREIGN KEY (customer_order_id) REFERENCES customer_order (id),
    CONSTRAINT fk_customer_order_line_item  FOREIGN KEY (item_id)           REFERENCES item           (id),
    CONSTRAINT fk_customer_order_line_uom   FOREIGN KEY (uom_id)            REFERENCES uom            (id)
);
CREATE INDEX ix_customer_order_line_order ON customer_order_line (customer_order_id);

CREATE TABLE customer_order_payment (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    customer_order_id   BIGINT         NOT NULL,
    amount              DECIMAL(18, 4) NOT NULL,
    method              VARCHAR(20)    NOT NULL,
    direction           VARCHAR(8)     NOT NULL, -- IN (deposit/instalment) or OUT (refund on cancel)
    reference           VARCHAR(80),
    notes               VARCHAR(200),
    occurred_at         TIMESTAMP      NOT NULL,
    by_user_id          BIGINT         NOT NULL,
    ref_cash_entry_id   BIGINT,
    ref_giftcard_txn_id BIGINT,
    idempotency_key     VARCHAR(80),
    CONSTRAINT fk_customer_order_payment_order FOREIGN KEY (customer_order_id) REFERENCES customer_order (id),
    CONSTRAINT uk_customer_order_payment_idempotency UNIQUE (customer_order_id, idempotency_key)
);
CREATE INDEX ix_customer_order_payment_order ON customer_order_payment (customer_order_id);
