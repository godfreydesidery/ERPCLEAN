-- Orbix Engine — catalog price lists + price-change audit.
-- DATA-MODEL.md §3.8–3.10. Catalog README §4 (price-list maintenance).

CREATE TABLE price_list (
    id            BIGINT       NOT NULL PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    code          VARCHAR(40)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    currency_code VARCHAR(3)   NOT NULL,
    valid_from    DATE         NOT NULL,
    valid_to      DATE,
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    tax_inclusive BOOLEAN      NOT NULL DEFAULT FALSE,
    status        VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    created_by    BIGINT       NOT NULL,
    updated_by    BIGINT       NOT NULL,
    version       INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_price_list_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_price_list_company FOREIGN KEY (company_id) REFERENCES company (id)
);

-- A price for one item in one list and UoM. Closed/open by valid_from / valid_to:
-- setting a new price closes the prior row (valid_to = effective_from - 1) and
-- inserts a fresh row. valid_to NULL = the currently-effective price.
CREATE TABLE price_list_item (
    id            BIGINT         NOT NULL PRIMARY KEY,
    price_list_id BIGINT         NOT NULL,
    item_id       BIGINT         NOT NULL,
    uom_id        BIGINT         NOT NULL,
    price         DECIMAL(18, 4) NOT NULL,
    valid_from    DATE           NOT NULL,
    valid_to      DATE,
    CONSTRAINT fk_price_list_item_list FOREIGN KEY (price_list_id) REFERENCES price_list (id),
    CONSTRAINT fk_price_list_item_item FOREIGN KEY (item_id)       REFERENCES item (id),
    CONSTRAINT fk_price_list_item_uom  FOREIGN KEY (uom_id)        REFERENCES uom (id)
);
CREATE INDEX ix_price_list_item_lookup ON price_list_item (price_list_id, item_id, uom_id, valid_to);

-- Append-only audit of every price change.
CREATE TABLE price_change_log (
    id                 BIGINT         NOT NULL PRIMARY KEY,
    price_list_item_id BIGINT         NOT NULL,
    old_price          DECIMAL(18, 4),
    new_price          DECIMAL(18, 4) NOT NULL,
    effective_from     DATE           NOT NULL,
    changed_at         TIMESTAMP      NOT NULL,
    changed_by         BIGINT         NOT NULL,
    reason             TEXT,
    CONSTRAINT fk_price_change_log_pli FOREIGN KEY (price_list_item_id) REFERENCES price_list_item (id)
);
CREATE INDEX ix_price_change_log_pli ON price_change_log (price_list_item_id);
