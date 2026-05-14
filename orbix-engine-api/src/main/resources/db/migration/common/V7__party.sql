-- Orbix Engine — party module: the unified customer / supplier / employee /
-- sales-agent person record. DATA-MODEL.md §2. One `party` row per real-world
-- entity; role tables attach role-specific terms via a shared primary key.

CREATE TABLE party (
    id               BIGINT       NOT NULL PRIMARY KEY,
    company_id       BIGINT       NOT NULL,
    code             VARCHAR(40)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    legal_name       VARCHAR(200),
    category         VARCHAR(32)  NOT NULL,
    tin              VARCHAR(40),
    vrn              VARCHAR(40),
    phone            VARCHAR(40),
    email            VARCHAR(120),
    physical_address TEXT,
    postal_address   TEXT,
    country_code     VARCHAR(2),
    notes            TEXT,
    status           VARCHAR(32)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    created_by       BIGINT       NOT NULL,
    updated_by       BIGINT       NOT NULL,
    version          INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_party_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_party_company FOREIGN KEY (company_id) REFERENCES company (id)
);
CREATE INDEX ix_party_company_tin ON party (company_id, tin);

CREATE TABLE party_address (
    id            BIGINT        NOT NULL PRIMARY KEY,
    party_id      BIGINT        NOT NULL,
    label         VARCHAR(40)   NOT NULL,
    line1         VARCHAR(200)  NOT NULL,
    line2         VARCHAR(200),
    city          VARCHAR(80),
    region        VARCHAR(80),
    country_code  VARCHAR(2),
    gps_lat       DECIMAL(10, 7),
    gps_lng       DECIMAL(10, 7),
    is_default    BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_party_address_party FOREIGN KEY (party_id) REFERENCES party (id)
);
CREATE INDEX ix_party_address_party ON party_address (party_id);

CREATE TABLE party_contact (
    id          BIGINT       NOT NULL PRIMARY KEY,
    party_id    BIGINT       NOT NULL,
    name        VARCHAR(120) NOT NULL,
    role_label  VARCHAR(80),
    phone       VARCHAR(40),
    email       VARCHAR(120),
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_party_contact_party FOREIGN KEY (party_id) REFERENCES party (id)
);
CREATE INDEX ix_party_contact_party ON party_contact (party_id);

-- Role tables: party_id is both PK and FK (shared primary key, 1:1 with party).

CREATE TABLE customer (
    party_id               BIGINT         NOT NULL PRIMARY KEY,
    credit_limit_amount    DECIMAL(18, 4) NOT NULL DEFAULT 0,
    credit_terms_days      INT            NOT NULL DEFAULT 0,
    price_list_id          BIGINT,
    default_sales_agent_id BIGINT,
    default_branch_id      BIGINT,
    is_walk_in             BOOLEAN        NOT NULL DEFAULT FALSE,
    tax_exempt             BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_customer_party FOREIGN KEY (party_id) REFERENCES party (id)
);

CREATE TABLE supplier (
    party_id              BIGINT         NOT NULL PRIMARY KEY,
    payment_terms_days    INT            NOT NULL DEFAULT 0,
    credit_limit_amount   DECIMAL(18, 4) NOT NULL DEFAULT 0,
    default_currency_code VARCHAR(3),
    bank_name             VARCHAR(120),
    bank_account_no       VARCHAR(40),
    lead_time_days        INT,
    CONSTRAINT fk_supplier_party FOREIGN KEY (party_id) REFERENCES party (id)
);

CREATE TABLE employee (
    party_id         BIGINT      NOT NULL PRIMARY KEY,
    app_user_id      BIGINT,
    employee_code    VARCHAR(40) NOT NULL,
    job_title        VARCHAR(120),
    branch_id        BIGINT      NOT NULL,
    hire_date        DATE,
    termination_date DATE,
    CONSTRAINT fk_employee_party  FOREIGN KEY (party_id)  REFERENCES party (id),
    CONSTRAINT fk_employee_branch FOREIGN KEY (branch_id) REFERENCES branch (id)
);

CREATE TABLE sales_agent (
    party_id        BIGINT         NOT NULL PRIMARY KEY,
    app_user_id     BIGINT,
    agent_code      VARCHAR(40)    NOT NULL,
    route_code      VARCHAR(40),
    commission_rate DECIMAL(10, 4),
    branch_id       BIGINT         NOT NULL,
    CONSTRAINT fk_sales_agent_party  FOREIGN KEY (party_id)  REFERENCES party (id),
    CONSTRAINT fk_sales_agent_branch FOREIGN KEY (branch_id) REFERENCES branch (id)
);
