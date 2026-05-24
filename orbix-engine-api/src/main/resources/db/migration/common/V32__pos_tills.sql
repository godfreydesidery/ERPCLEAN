-- Orbix Engine — POS tills + till sessions (F5.1). DATA-MODEL.md §7.1, §7.2.
-- Till: a physical workstation. TillSession: a cashier shift on that till —
-- OPEN → CLOSED → RECONCILED. At most one OPEN session per till.

CREATE TABLE till (
    id                       BIGINT      NOT NULL PRIMARY KEY,
    uid                      CHAR(26)    NOT NULL,
    company_id               BIGINT      NOT NULL,
    branch_id                BIGINT      NOT NULL,
    code                     VARCHAR(20) NOT NULL,
    name                     VARCHAR(80) NOT NULL,
    install_id               VARCHAR(80),
    default_price_list_id    BIGINT      NOT NULL,
    status                   VARCHAR(32) NOT NULL,
    version                  INT         NOT NULL DEFAULT 0,
    created_at               TIMESTAMP   NOT NULL,
    updated_at               TIMESTAMP   NOT NULL,
    created_by               BIGINT      NOT NULL,
    updated_by               BIGINT      NOT NULL,
    CONSTRAINT uk_till_uid         UNIQUE (uid),
    CONSTRAINT uk_till_branch_code UNIQUE (branch_id, code),
    CONSTRAINT fk_till_company    FOREIGN KEY (company_id)            REFERENCES company    (id),
    CONSTRAINT fk_till_branch     FOREIGN KEY (branch_id)             REFERENCES branch     (id),
    CONSTRAINT fk_till_price_list FOREIGN KEY (default_price_list_id) REFERENCES price_list (id)
);
CREATE INDEX ix_till_branch ON till (branch_id);

CREATE TABLE till_session (
    id                       BIGINT         NOT NULL PRIMARY KEY,
    uid                      CHAR(26)       NOT NULL,
    till_id                  BIGINT         NOT NULL,
    branch_id                BIGINT         NOT NULL,
    company_id               BIGINT         NOT NULL,
    business_date            DATE           NOT NULL,
    opened_by                BIGINT         NOT NULL,
    opened_at                TIMESTAMP      NOT NULL,
    opening_float_amount     DECIMAL(18, 4) NOT NULL,
    closed_by                BIGINT,
    closed_at                TIMESTAMP,
    expected_cash_amount     DECIMAL(18, 4),
    declared_cash_amount     DECIMAL(18, 4),
    variance_amount          DECIMAL(18, 4),
    supervisor_id            BIGINT,
    status                   VARCHAR(32)    NOT NULL,
    z_report_object_key      VARCHAR(200),
    notes                    VARCHAR(2000),
    version                  INT            NOT NULL DEFAULT 0,
    CONSTRAINT uk_till_session_uid     UNIQUE (uid),
    CONSTRAINT fk_till_session_till    FOREIGN KEY (till_id)    REFERENCES till    (id),
    CONSTRAINT fk_till_session_branch  FOREIGN KEY (branch_id)  REFERENCES branch  (id),
    CONSTRAINT fk_till_session_company FOREIGN KEY (company_id) REFERENCES company (id)
);
CREATE INDEX ix_till_session_till_status   ON till_session (till_id, status);
CREATE INDEX ix_till_session_branch_status ON till_session (branch_id, status);
CREATE INDEX ix_till_session_company       ON till_session (company_id);
