-- Orbix Engine — stock counts + inter-branch transfers. DATA-MODEL.md §4.3–4.6.
-- On count post, non-zero variances become ADJUSTMENT stock moves; transfers
-- post TRANSFER_OUT at issue and TRANSFER_IN at receipt.

CREATE TABLE stock_count (
    id          BIGINT      NOT NULL PRIMARY KEY,
    uid         CHAR(26)    NOT NULL,
    number      VARCHAR(40) NOT NULL,
    branch_id   BIGINT      NOT NULL,
    company_id  BIGINT      NOT NULL,
    count_date  DATE        NOT NULL,
    type        VARCHAR(20) NOT NULL,
    status      VARCHAR(32) NOT NULL,
    started_by  BIGINT      NOT NULL,
    closed_by   BIGINT,
    posted_at   TIMESTAMP,
    CONSTRAINT uk_stock_count_uid           UNIQUE (uid),
    CONSTRAINT uk_stock_count_branch_number UNIQUE (branch_id, number),
    CONSTRAINT fk_stock_count_branch  FOREIGN KEY (branch_id)  REFERENCES branch (id),
    CONSTRAINT fk_stock_count_company FOREIGN KEY (company_id) REFERENCES company (id)
);

CREATE TABLE stock_count_line (
    id             BIGINT         NOT NULL PRIMARY KEY,
    stock_count_id BIGINT         NOT NULL,
    item_id        BIGINT         NOT NULL,
    system_qty     DECIMAL(18, 4) NOT NULL,
    counted_qty    DECIMAL(18, 4),
    variance_qty   DECIMAL(18, 4),
    note           VARCHAR(200),
    CONSTRAINT fk_stock_count_line_count FOREIGN KEY (stock_count_id) REFERENCES stock_count (id),
    CONSTRAINT fk_stock_count_line_item  FOREIGN KEY (item_id)        REFERENCES item (id)
);
CREATE INDEX ix_stock_count_line_count ON stock_count_line (stock_count_id);

CREATE TABLE stock_transfer (
    id             BIGINT      NOT NULL PRIMARY KEY,
    uid            CHAR(26)    NOT NULL,
    number         VARCHAR(40) NOT NULL,
    company_id     BIGINT      NOT NULL,
    from_branch_id BIGINT      NOT NULL,
    to_branch_id   BIGINT      NOT NULL,
    issued_at      TIMESTAMP,
    received_at    TIMESTAMP,
    status         VARCHAR(32) NOT NULL,
    CONSTRAINT uk_stock_transfer_uid            UNIQUE (uid),
    CONSTRAINT uk_stock_transfer_company_number UNIQUE (company_id, number),
    CONSTRAINT fk_stock_transfer_company FOREIGN KEY (company_id)     REFERENCES company (id),
    CONSTRAINT fk_stock_transfer_from    FOREIGN KEY (from_branch_id) REFERENCES branch (id),
    CONSTRAINT fk_stock_transfer_to      FOREIGN KEY (to_branch_id)   REFERENCES branch (id)
);

CREATE TABLE stock_transfer_line (
    id                BIGINT         NOT NULL PRIMARY KEY,
    stock_transfer_id BIGINT         NOT NULL,
    item_id           BIGINT         NOT NULL,
    issued_qty        DECIMAL(18, 4) NOT NULL,
    received_qty      DECIMAL(18, 4),
    cost_amount       DECIMAL(18, 4) NOT NULL,
    CONSTRAINT fk_stock_transfer_line_transfer FOREIGN KEY (stock_transfer_id) REFERENCES stock_transfer (id),
    CONSTRAINT fk_stock_transfer_line_item     FOREIGN KEY (item_id)           REFERENCES item (id)
);
CREATE INDEX ix_stock_transfer_line_transfer ON stock_transfer_line (stock_transfer_id);
