-- Phase 1.1 admin masters: section, currency, fx_rate, till_currency.
-- See docs/design/PHASE-1.1-ADDITIONS.md and DATA-MODEL.md §17.

CREATE TABLE section (
    id              BIGINT      NOT NULL PRIMARY KEY,
    uid             CHAR(26)    NOT NULL,
    branch_id       BIGINT      NOT NULL,
    code            VARCHAR(20) NOT NULL,
    name            VARCHAR(80) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    manager_user_id BIGINT,
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL,
    created_by      BIGINT      NOT NULL,
    updated_by      BIGINT      NOT NULL,
    version         INT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_section_uid         UNIQUE (uid),
    CONSTRAINT uk_section_branch_code UNIQUE (branch_id, code),
    CONSTRAINT fk_section_branch FOREIGN KEY (branch_id) REFERENCES branch (id)
);
CREATE INDEX ix_section_branch_status ON section (branch_id, status);

CREATE TABLE currency (
    code              VARCHAR(3) NOT NULL PRIMARY KEY,
    name              VARCHAR(60) NOT NULL,
    symbol            VARCHAR(8),
    minor_unit_digits INT         NOT NULL DEFAULT 2,
    status            VARCHAR(32) NOT NULL
);

CREATE TABLE fx_rate (
    id            BIGINT         NOT NULL PRIMARY KEY,
    from_currency VARCHAR(3)     NOT NULL,
    to_currency   VARCHAR(3)     NOT NULL,
    rate          DECIMAL(20, 8) NOT NULL,
    effective_at  TIMESTAMP      NOT NULL,
    created_by    BIGINT         NOT NULL,
    created_at    TIMESTAMP      NOT NULL,
    CONSTRAINT fk_fx_rate_from FOREIGN KEY (from_currency) REFERENCES currency (code),
    CONSTRAINT fk_fx_rate_to   FOREIGN KEY (to_currency)   REFERENCES currency (code)
);
CREATE INDEX ix_fx_rate_pair_effective ON fx_rate (from_currency, to_currency, effective_at);

-- till_currency join table — created when till entity is added in Phase 5.
