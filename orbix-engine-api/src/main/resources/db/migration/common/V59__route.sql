-- Orbix Engine — delivery-route master. A `route` is a sales territory
-- (e.g. CENTRAL, EAST-A) that field-sales agents work and that daily
-- van loads (sales_list, WMS) anchor to. Company-scoped master data,
-- analogous to branch / section.
--
-- Replaces the free-text `sales_agent.route_code` column with a FK to
-- this master so reports group cleanly without de-duping typos.

CREATE TABLE route (
    id          BIGINT       NOT NULL PRIMARY KEY,
    uid         CHAR(26)     NOT NULL,
    company_id  BIGINT       NOT NULL,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    status      VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    created_by  BIGINT       NOT NULL,
    updated_by  BIGINT       NOT NULL,
    version     INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_route_uid          UNIQUE (uid),
    CONSTRAINT uk_route_company_code UNIQUE (company_id, code),
    CONSTRAINT fk_route_company FOREIGN KEY (company_id) REFERENCES company (id)
);
CREATE INDEX ix_route_company_status ON route (company_id, status);

-- Swap sales_agent.route_code (VARCHAR free-text) for route_id (FK).
-- Clean-build rewrite: pre-existing route_code values are dropped, not
-- migrated. Re-seed via the routes admin page after this migration.
ALTER TABLE sales_agent DROP COLUMN route_code;
ALTER TABLE sales_agent ADD COLUMN route_id BIGINT;
ALTER TABLE sales_agent ADD CONSTRAINT fk_sales_agent_route
    FOREIGN KEY (route_id) REFERENCES route (id);
