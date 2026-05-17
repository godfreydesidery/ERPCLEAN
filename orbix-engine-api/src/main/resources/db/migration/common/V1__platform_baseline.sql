-- Orbix Engine — platform baseline.
-- DB-agnostic. Runs on MySQL 8 and PostgreSQL 15 unchanged.

-- Organisation / Company / Branch (DATA-MODEL.md §1.1-1.3)
CREATE TABLE organisation (
    id              BIGINT      NOT NULL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    legal_name      VARCHAR(200),
    currency_code   VARCHAR(3)   NOT NULL,
    country_code    VARCHAR(2)   NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    created_by      BIGINT       NOT NULL,
    updated_by      BIGINT       NOT NULL,
    version         INT          NOT NULL DEFAULT 0
);

CREATE TABLE company (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    organisation_id         BIGINT       NOT NULL,
    code                    VARCHAR(20)  NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    legal_name              VARCHAR(200),
    tin                     VARCHAR(40),
    vrn                     VARCHAR(40),
    physical_address        TEXT,
    postal_address          TEXT,
    phone                   VARCHAR(40),
    email                   VARCHAR(120),
    website                 VARCHAR(200),
    currency_code           VARCHAR(3)   NOT NULL,
    country_code            VARCHAR(2)   NOT NULL,
    time_zone               VARCHAR(64)  NOT NULL,
    logo_object_key         VARCHAR(200),
    default_invoice_note    TEXT,
    default_quotation_note  TEXT,
    status                  VARCHAR(32)  NOT NULL,
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP    NOT NULL,
    created_by              BIGINT       NOT NULL,
    updated_by              BIGINT       NOT NULL,
    version                 INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_company_org_code UNIQUE (organisation_id, code),
    CONSTRAINT fk_company_org FOREIGN KEY (organisation_id) REFERENCES organisation (id)
);

CREATE TABLE branch (
    id                BIGINT       NOT NULL PRIMARY KEY,
    uid               CHAR(26)     NOT NULL,
    company_id        BIGINT       NOT NULL,
    code              VARCHAR(20)  NOT NULL,
    name              VARCHAR(120) NOT NULL,
    type              VARCHAR(32)  NOT NULL,
    physical_address  TEXT,
    phone             VARCHAR(40),
    time_zone         VARCHAR(64)  NOT NULL,
    is_default        BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(32)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    created_by        BIGINT       NOT NULL,
    updated_by        BIGINT       NOT NULL,
    version           INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_branch_uid          UNIQUE (uid),
    CONSTRAINT uk_branch_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_branch_company FOREIGN KEY (company_id) REFERENCES company (id)
);

-- Identity (DATA-MODEL.md §1.4-1.8)
CREATE TABLE app_user (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    username            VARCHAR(80)  NOT NULL,
    password_hash       VARCHAR(120) NOT NULL,
    display_name        VARCHAR(120) NOT NULL,
    email               VARCHAR(120),
    email_verified_at   TIMESTAMP,
    phone               VARCHAR(40),
    default_company_id  BIGINT,
    default_branch_id   BIGINT,
    failed_login_count  INT          NOT NULL DEFAULT 0,
    locked_until        TIMESTAMP,
    last_login_at       TIMESTAMP,
    status              VARCHAR(32)  NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    created_by          BIGINT       NOT NULL,
    updated_by          BIGINT       NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

CREATE TABLE role (
    id          BIGINT       NOT NULL PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    status      VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    created_by  BIGINT       NOT NULL,
    updated_by  BIGINT       NOT NULL,
    version     INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_role_code UNIQUE (code)
);

CREATE TABLE permission (
    id          BIGINT       NOT NULL PRIMARY KEY,
    code        VARCHAR(80)  NOT NULL,
    description TEXT         NOT NULL,
    module      VARCHAR(40)  NOT NULL,
    CONSTRAINT uk_permission_code UNIQUE (code)
);

CREATE TABLE role_permission (
    role_id      BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role      FOREIGN KEY (role_id)      REFERENCES role (id),
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permission (id)
);

CREATE TABLE user_role (
    id          BIGINT      NOT NULL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    role_id     BIGINT      NOT NULL,
    company_id  BIGINT      NOT NULL,
    branch_id   BIGINT,
    granted_at  TIMESTAMP   NOT NULL,
    granted_by  BIGINT      NOT NULL,
    revoked_at  TIMESTAMP,
    CONSTRAINT fk_user_role_user    FOREIGN KEY (user_id)    REFERENCES app_user (id),
    CONSTRAINT fk_user_role_role    FOREIGN KEY (role_id)    REFERENCES role (id),
    CONSTRAINT fk_user_role_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_user_role_branch  FOREIGN KEY (branch_id)  REFERENCES branch (id)
);

-- Audit log (DATA-MODEL.md §1.9). Append-only; hash-chained.
CREATE TABLE audit_log (
    id           BIGINT       NOT NULL PRIMARY KEY,
    at           TIMESTAMP    NOT NULL,
    actor_id     BIGINT       NOT NULL,
    action       VARCHAR(40)  NOT NULL,
    entity_type  VARCHAR(80)  NOT NULL,
    entity_id    VARCHAR(80)  NOT NULL,
    company_id   BIGINT,
    branch_id    BIGINT,
    before_json  TEXT,
    after_json   TEXT,
    meta_json    TEXT,
    prev_hash    VARCHAR(64)  NOT NULL,
    row_hash     VARCHAR(64)  NOT NULL
);
CREATE INDEX ix_audit_entity   ON audit_log (entity_type, entity_id, at);
CREATE INDEX ix_audit_actor    ON audit_log (actor_id, at);
CREATE INDEX ix_audit_at       ON audit_log (at);

-- Number sequences (DATA-MODEL.md §1.10)
CREATE TABLE number_sequence (
    company_id    BIGINT      NOT NULL,
    branch_id     BIGINT      NOT NULL,
    doc_type      VARCHAR(40) NOT NULL,
    prefix        VARCHAR(20) NOT NULL,
    current_value BIGINT      NOT NULL,
    pad_width     INT         NOT NULL,
    PRIMARY KEY (company_id, branch_id, doc_type)
);

-- Domain events (outbox — DATA-MODEL.md §1.11, ARCHITECTURE.md §2.10)
CREATE TABLE domain_event (
    id              BIGINT       NOT NULL PRIMARY KEY,
    type            VARCHAR(120) NOT NULL,
    aggregate_type  VARCHAR(80)  NOT NULL,
    aggregate_id    VARCHAR(80)  NOT NULL,
    payload_json    TEXT         NOT NULL,
    occurred_at     TIMESTAMP    NOT NULL,
    company_id      BIGINT,
    branch_id       BIGINT,
    actor_id        BIGINT,
    status          VARCHAR(32)  NOT NULL,
    attempt_count   INT          NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP,
    last_error      TEXT,
    dispatched_at   TIMESTAMP
);
CREATE INDEX ix_domain_event_status_occurred ON domain_event (status, occurred_at);

-- Sync idempotency for offline clients (DATA-MODEL.md §1.15)
CREATE TABLE client_op (
    client_op_id          VARCHAR(40) NOT NULL PRIMARY KEY,
    client_type           VARCHAR(20) NOT NULL,
    client_install_id     VARCHAR(80) NOT NULL,
    op_type               VARCHAR(80) NOT NULL,
    server_resource_type  VARCHAR(80),
    server_resource_id    BIGINT,
    status                VARCHAR(32) NOT NULL,
    received_at           TIMESTAMP   NOT NULL,
    applied_at            TIMESTAMP,
    error_json            TEXT
);

-- Refresh tokens (DATA-MODEL.md §1.16)
CREATE TABLE refresh_token (
    id                 BIGINT       NOT NULL PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    token_hash         VARCHAR(120) NOT NULL,
    issued_at          TIMESTAMP    NOT NULL,
    expires_at         TIMESTAMP    NOT NULL,
    client_install_id  VARCHAR(80),
    revoked_at         TIMESTAMP,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

-- Feature flags (DATA-MODEL.md §1.13-1.14)
CREATE TABLE feature_flag (
    id            BIGINT      NOT NULL PRIMARY KEY,
    code          VARCHAR(80) NOT NULL,
    type          VARCHAR(32) NOT NULL,
    description   TEXT        NOT NULL,
    default_value BOOLEAN     NOT NULL,
    expires_at    DATE,
    status        VARCHAR(32) NOT NULL,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL,
    created_by    BIGINT      NOT NULL,
    updated_by    BIGINT      NOT NULL,
    version       INT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_feature_flag_code UNIQUE (code)
);

CREATE TABLE feature_flag_override (
    id         BIGINT      NOT NULL PRIMARY KEY,
    flag_code  VARCHAR(80) NOT NULL,
    scope      VARCHAR(20) NOT NULL,
    scope_id   BIGINT,
    enabled    BOOLEAN     NOT NULL,
    note       TEXT,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL,
    created_by BIGINT      NOT NULL,
    updated_by BIGINT      NOT NULL,
    version    INT         NOT NULL DEFAULT 0
);

-- Business day (DATA-MODEL.md §11.1)
CREATE TABLE business_day (
    branch_id              BIGINT      NOT NULL,
    business_date          DATE        NOT NULL,
    status                 VARCHAR(32) NOT NULL,
    opened_at              TIMESTAMP   NOT NULL,
    opened_by              BIGINT      NOT NULL,
    closed_at              TIMESTAMP,
    closed_by              BIGINT,
    eod_report_object_key  VARCHAR(200),
    PRIMARY KEY (branch_id, business_date)
);

-- Hibernate-managed shared sequences (DB-agnostic via the SEQUENCE strategy)
-- MySQL emulates sequences via a table; Postgres uses native sequences.
-- Flyway dialect-specific scripts (mysql/, postgres/) provide the actual generators.
