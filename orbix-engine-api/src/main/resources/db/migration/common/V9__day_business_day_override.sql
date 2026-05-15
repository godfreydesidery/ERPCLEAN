-- Orbix Engine — business-day module. The business_day table ships in the V1
-- baseline; this adds the audit record written whenever a supervisor back-dates
-- a posting into a closed day. DATA-MODEL.md §11.2.

CREATE TABLE business_day_override (
    id                   BIGINT      NOT NULL PRIMARY KEY,
    branch_id            BIGINT      NOT NULL,
    target_business_date DATE        NOT NULL,
    entity_type          VARCHAR(40) NOT NULL,
    entity_id            BIGINT      NOT NULL,
    reason               TEXT        NOT NULL,
    authorised_by        BIGINT      NOT NULL,
    at                   TIMESTAMP   NOT NULL,
    CONSTRAINT fk_business_day_override_branch FOREIGN KEY (branch_id) REFERENCES branch (id)
);
CREATE INDEX ix_business_day_override_branch_date
    ON business_day_override (branch_id, target_business_date);
