-- Orbix Engine — cash pickup + petty cash (F5.9). DATA-MODEL.md §7.6, §7.7.
-- Mid-shift cash leaves the drawer: either to the back-office cash box
-- (pickup, paired CASH-OUT/CASH_BOX-IN) or out of the system entirely
-- (petty cash, single OUT-TILL). Both require a supervisor authoriser.

CREATE TABLE cash_pickup (
    id                BIGINT         NOT NULL PRIMARY KEY,
    uid               CHAR(26)       NOT NULL,
    till_session_id   BIGINT         NOT NULL,
    company_id        BIGINT         NOT NULL,
    branch_id         BIGINT         NOT NULL,
    business_date     DATE           NOT NULL,
    amount            DECIMAL(18, 4) NOT NULL,
    at                TIMESTAMP      NOT NULL,
    picked_up_by      BIGINT         NOT NULL,
    authorised_by     BIGINT         NOT NULL,
    note              VARCHAR(200),
    created_at        TIMESTAMP      NOT NULL,
    CONSTRAINT uk_cash_pickup_uid UNIQUE (uid),
    CONSTRAINT fk_cash_pickup_session FOREIGN KEY (till_session_id) REFERENCES till_session (id),
    CONSTRAINT fk_cash_pickup_company FOREIGN KEY (company_id)      REFERENCES company      (id),
    CONSTRAINT fk_cash_pickup_branch  FOREIGN KEY (branch_id)       REFERENCES branch       (id)
);
CREATE INDEX ix_cash_pickup_session ON cash_pickup (till_session_id);
CREATE INDEX ix_cash_pickup_branch_date ON cash_pickup (branch_id, business_date);

CREATE TABLE petty_cash (
    id                       BIGINT         NOT NULL PRIMARY KEY,
    uid                      CHAR(26)       NOT NULL,
    till_session_id          BIGINT,
    company_id               BIGINT         NOT NULL,
    branch_id                BIGINT         NOT NULL,
    business_date            DATE           NOT NULL,
    amount                   DECIMAL(18, 4) NOT NULL,
    at                       TIMESTAMP      NOT NULL,
    category                 VARCHAR(40)    NOT NULL,
    paid_to                  VARCHAR(120),
    paid_by                  BIGINT         NOT NULL,
    authorised_by            BIGINT         NOT NULL,
    description              VARCHAR(2000),
    receipt_attachment_key   VARCHAR(200),
    created_at               TIMESTAMP      NOT NULL,
    CONSTRAINT uk_petty_cash_uid UNIQUE (uid),
    CONSTRAINT fk_petty_cash_session FOREIGN KEY (till_session_id) REFERENCES till_session (id),
    CONSTRAINT fk_petty_cash_company FOREIGN KEY (company_id)      REFERENCES company      (id),
    CONSTRAINT fk_petty_cash_branch  FOREIGN KEY (branch_id)       REFERENCES branch       (id)
);
CREATE INDEX ix_petty_cash_session ON petty_cash (till_session_id);
CREATE INDEX ix_petty_cash_branch_date ON petty_cash (branch_id, business_date);
CREATE INDEX ix_petty_cash_category   ON petty_cash (category);
