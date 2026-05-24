-- Orbix Engine — batch tracking + FEFO consumption (F2.4). DATA-MODEL.md §17.5.
-- stock_batch is a per-(branch, item) batch row; on GRN of a batch-tracked item
-- a row is created carrying manufacture / expiry / received qty. Drains via FEFO
-- consumption (earliest expiry wins). stock_move gains a nullable batch_id so a
-- move row can attribute itself to a specific batch.

CREATE TABLE stock_batch (
    id              BIGINT         NOT NULL PRIMARY KEY,
    uid             CHAR(26)       NOT NULL,
    item_id         BIGINT         NOT NULL,
    branch_id       BIGINT         NOT NULL,
    company_id      BIGINT         NOT NULL,
    batch_no        VARCHAR(40)    NOT NULL,
    manufactured_at DATE,
    expiry_at       DATE,
    qty_received    DECIMAL(18, 4) NOT NULL,
    qty_on_hand     DECIMAL(18, 4) NOT NULL,
    cost            DECIMAL(18, 4) NOT NULL,
    source_doc_type VARCHAR(40)    NOT NULL,
    source_doc_id   BIGINT         NOT NULL,
    status          VARCHAR(32)    NOT NULL,
    version         INT            NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL,
    updated_at      TIMESTAMP      NOT NULL,
    created_by      BIGINT         NOT NULL,
    updated_by      BIGINT         NOT NULL,
    CONSTRAINT uk_stock_batch_uid           UNIQUE (uid),
    CONSTRAINT uk_stock_batch_branch_item_no UNIQUE (branch_id, item_id, batch_no),
    CONSTRAINT fk_stock_batch_item    FOREIGN KEY (item_id)    REFERENCES item (id),
    CONSTRAINT fk_stock_batch_branch  FOREIGN KEY (branch_id)  REFERENCES branch (id),
    CONSTRAINT fk_stock_batch_company FOREIGN KEY (company_id) REFERENCES company (id)
);
CREATE INDEX ix_stock_batch_branch_item_expiry ON stock_batch (branch_id, item_id, expiry_at);
CREATE INDEX ix_stock_batch_status_expiry      ON stock_batch (status, expiry_at);

ALTER TABLE stock_move ADD COLUMN batch_id BIGINT;
ALTER TABLE stock_move ADD CONSTRAINT fk_stock_move_batch
    FOREIGN KEY (batch_id) REFERENCES stock_batch (id);
CREATE INDEX ix_stock_move_batch ON stock_move (batch_id);
