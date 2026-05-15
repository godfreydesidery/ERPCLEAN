-- Orbix Engine — POS FX tender (F5.6). DATA-MODEL.md §17.4 + §17.12.
-- A till is configured with a set of foreign currencies it accepts; the
-- company's functional currency is implicit. Each pos_payment row now carries
-- the original tender amount + currency + snapshot FX rate, with `amount`
-- re-interpreted as the functional-currency-converted value.

-- Foreign currencies a till is allowed to accept.
CREATE TABLE till_currency (
    till_id        BIGINT      NOT NULL,
    currency_code  VARCHAR(3)  NOT NULL,
    created_at     TIMESTAMP   NOT NULL,
    created_by     BIGINT      NOT NULL,
    CONSTRAINT pk_till_currency PRIMARY KEY (till_id, currency_code),
    CONSTRAINT fk_till_currency_till     FOREIGN KEY (till_id)       REFERENCES till     (id),
    CONSTRAINT fk_till_currency_currency FOREIGN KEY (currency_code) REFERENCES currency (code)
);
CREATE INDEX ix_till_currency_currency ON till_currency (currency_code);

-- pos_payment gains FX-tender columns. Pre-existing rows (dev DBs only — this
-- is a clean-build project) are backfilled to functional currency at rate 1.
ALTER TABLE pos_payment ADD COLUMN tender_currency  VARCHAR(3);
ALTER TABLE pos_payment ADD COLUMN tender_amount    DECIMAL(18, 4);
ALTER TABLE pos_payment ADD COLUMN fx_rate_snapshot DECIMAL(20, 8);

UPDATE pos_payment SET tender_amount = amount        WHERE tender_amount    IS NULL;
UPDATE pos_payment SET fx_rate_snapshot = 1          WHERE fx_rate_snapshot IS NULL;
UPDATE pos_payment SET tender_currency = (
    SELECT c.currency_code FROM pos_sale ps
    JOIN company c ON c.id = ps.company_id
    WHERE ps.id = pos_payment.pos_sale_id
) WHERE tender_currency IS NULL;

ALTER TABLE pos_payment ADD CONSTRAINT fk_pos_payment_currency
    FOREIGN KEY (tender_currency) REFERENCES currency (code);
CREATE INDEX ix_pos_payment_tender_currency ON pos_payment (tender_currency);

-- F5.6 permission: admin endpoint to maintain a till's allowed FX currencies.
INSERT INTO permission (id, code, description, module) VALUES
    (47, 'POS.TILL_CURRENCY_MANAGE', 'Add or remove allowed FX tender currencies for a till', 'pos');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 47;
