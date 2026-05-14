-- Orbix Engine — stock ledger. DATA-MODEL.md §4.
-- stock_move is the append-only source of truth; item_branch_balance is a
-- rebuildable per-(item, branch) cache. stock_move carries no updated_at /
-- version — it is immutable; append-only is enforced at the app layer
-- (no update path on the entity or service).

CREATE TABLE stock_move (
    id          BIGINT         NOT NULL PRIMARY KEY,
    at          TIMESTAMP      NOT NULL,
    item_id     BIGINT         NOT NULL,
    branch_id   BIGINT         NOT NULL,
    company_id  BIGINT         NOT NULL,
    qty         DECIMAL(18, 4) NOT NULL,
    cost_amount DECIMAL(18, 4) NOT NULL,
    direction   VARCHAR(20)    NOT NULL,
    move_type   VARCHAR(40)    NOT NULL,
    ref_type    VARCHAR(40)    NOT NULL,
    ref_id      BIGINT         NOT NULL,
    actor_id    BIGINT         NOT NULL,
    notes       VARCHAR(200),
    CONSTRAINT fk_stock_move_item    FOREIGN KEY (item_id)    REFERENCES item (id),
    CONSTRAINT fk_stock_move_branch  FOREIGN KEY (branch_id)  REFERENCES branch (id),
    CONSTRAINT fk_stock_move_company FOREIGN KEY (company_id) REFERENCES company (id)
);
CREATE INDEX ix_stock_move_item_branch_at ON stock_move (item_id, branch_id, at);
CREATE INDEX ix_stock_move_branch_at      ON stock_move (branch_id, at);
CREATE INDEX ix_stock_move_ref            ON stock_move (ref_type, ref_id);

CREATE TABLE item_branch_balance (
    item_id        BIGINT         NOT NULL,
    branch_id      BIGINT         NOT NULL,
    qty_on_hand    DECIMAL(18, 4) NOT NULL DEFAULT 0,
    qty_reserved   DECIMAL(18, 4) NOT NULL DEFAULT 0,
    qty_in_transit DECIMAL(18, 4) NOT NULL DEFAULT 0,
    avg_cost       DECIMAL(18, 4) NOT NULL DEFAULT 0,
    last_cost      DECIMAL(18, 4) NOT NULL DEFAULT 0,
    reorder_min    DECIMAL(18, 4),
    reorder_max    DECIMAL(18, 4),
    bin_location   VARCHAR(40),
    last_moved_at  TIMESTAMP,
    PRIMARY KEY (item_id, branch_id),
    CONSTRAINT fk_item_branch_balance_item   FOREIGN KEY (item_id)   REFERENCES item (id),
    CONSTRAINT fk_item_branch_balance_branch FOREIGN KEY (branch_id) REFERENCES branch (id)
);
