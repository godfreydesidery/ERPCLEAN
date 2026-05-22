-- Orbix Engine — catalog baseline.
-- DATA-MODEL.md §3.

CREATE TABLE item_group (
    id          BIGINT       NOT NULL PRIMARY KEY,
    uid         CHAR(26)     NOT NULL,
    company_id  BIGINT       NOT NULL,
    parent_id   BIGINT,
    level       INT          NOT NULL,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    created_by  BIGINT       NOT NULL,
    updated_by  BIGINT       NOT NULL,
    version     INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_item_group_uid          UNIQUE (uid),
    CONSTRAINT uk_item_group_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_item_group_parent  FOREIGN KEY (parent_id)  REFERENCES item_group (id),
    CONSTRAINT fk_item_group_company FOREIGN KEY (company_id) REFERENCES company (id)
);

CREATE TABLE uom (
    id         BIGINT       NOT NULL PRIMARY KEY,
    uid        CHAR(26)     NOT NULL,
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(80)  NOT NULL,
    dimension  VARCHAR(20)  NOT NULL,
    is_base    BOOLEAN      NOT NULL,
    CONSTRAINT uk_uom_uid  UNIQUE (uid),
    CONSTRAINT uk_uom_code UNIQUE (code)
);

CREATE TABLE vat_group (
    id          BIGINT         NOT NULL PRIMARY KEY,
    uid         CHAR(26)       NOT NULL,
    company_id  BIGINT         NOT NULL,
    code        VARCHAR(20)    NOT NULL,
    name        VARCHAR(80)    NOT NULL,
    rate        DECIMAL(10, 4) NOT NULL,
    valid_from  DATE           NOT NULL,
    is_default  BOOLEAN        NOT NULL DEFAULT FALSE,
    status      VARCHAR(32)    NOT NULL,
    created_at  TIMESTAMP      NOT NULL,
    updated_at  TIMESTAMP      NOT NULL,
    created_by  BIGINT         NOT NULL,
    updated_by  BIGINT         NOT NULL,
    version     INT            NOT NULL DEFAULT 0,
    CONSTRAINT uk_vat_group_uid          UNIQUE (uid),
    CONSTRAINT uk_vat_group_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_vat_group_company FOREIGN KEY (company_id) REFERENCES company (id)
);

CREATE TABLE item (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    uid                 CHAR(26)       NOT NULL,
    company_id          BIGINT         NOT NULL,
    code                VARCHAR(40)    NOT NULL,
    name                VARCHAR(200)   NOT NULL,
    short_name          VARCHAR(80),
    type                VARCHAR(20)    NOT NULL,
    item_group_id       BIGINT         NOT NULL,
    uom_id              BIGINT         NOT NULL,
    vat_group_id        BIGINT         NOT NULL,
    is_tracked          BOOLEAN        NOT NULL DEFAULT TRUE,
    avg_cost            DECIMAL(18, 4) NOT NULL DEFAULT 0,
    last_cost           DECIMAL(18, 4) NOT NULL DEFAULT 0,
    standard_cost       DECIMAL(18, 4),
    min_sell_price      DECIMAL(18, 4),
    default_supplier_id BIGINT,
    image_object_key    VARCHAR(200),
    is_serialised       BOOLEAN        NOT NULL DEFAULT FALSE,
    is_batch_tracked    BOOLEAN        NOT NULL DEFAULT FALSE,
    shelf_life_days     INT,
    weight_kg           DECIMAL(18, 4),
    status              VARCHAR(32)    NOT NULL,
    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP      NOT NULL,
    created_by          BIGINT         NOT NULL,
    updated_by          BIGINT         NOT NULL,
    version             INT            NOT NULL DEFAULT 0,
    CONSTRAINT uk_item_uid          UNIQUE (uid),
    CONSTRAINT uk_item_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_item_company    FOREIGN KEY (company_id)    REFERENCES company (id),
    CONSTRAINT fk_item_group      FOREIGN KEY (item_group_id) REFERENCES item_group (id),
    CONSTRAINT fk_item_uom        FOREIGN KEY (uom_id)        REFERENCES uom (id),
    CONSTRAINT fk_item_vat_group  FOREIGN KEY (vat_group_id)  REFERENCES vat_group (id)
);

CREATE TABLE item_barcode (
    id           BIGINT         NOT NULL PRIMARY KEY,
    uid          CHAR(26)       NOT NULL,
    item_id      BIGINT         NOT NULL,
    barcode      VARCHAR(40)    NOT NULL,
    pack_uom_id  BIGINT,
    pack_qty     DECIMAL(18, 4) NOT NULL DEFAULT 1,
    CONSTRAINT uk_item_barcode_uid UNIQUE (uid),
    CONSTRAINT uk_item_barcode     UNIQUE (barcode),
    CONSTRAINT fk_item_barcode_item FOREIGN KEY (item_id)     REFERENCES item (id),
    CONSTRAINT fk_item_barcode_uom  FOREIGN KEY (pack_uom_id) REFERENCES uom (id)
);
