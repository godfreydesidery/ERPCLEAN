-- Orbix Engine — business-day module. The business_day table ships in the V1
-- baseline; this adds the audit record written whenever a supervisor back-dates
-- a posting into a closed day. DATA-MODEL.md §11.2.
--
-- Slice D — overrides get an archive lifecycle (void before the back-dated
-- post lands). `archived_at` / `archived_by` are nullable; once stamped the
-- override is treated as void and cannot be archived twice.

CREATE TABLE business_day_override (
    id                   BIGINT      NOT NULL PRIMARY KEY,
    uid                  CHAR(26)    NOT NULL,
    branch_id            BIGINT      NOT NULL,
    target_business_date DATE        NOT NULL,
    entity_type          VARCHAR(40) NOT NULL,
    entity_id            BIGINT      NOT NULL,
    reason               TEXT        NOT NULL,
    authorised_by        BIGINT      NOT NULL,
    at                   TIMESTAMP   NOT NULL,
    archived_at          TIMESTAMP,
    archived_by          BIGINT,
    CONSTRAINT fk_business_day_override_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT uk_business_day_override_uid UNIQUE (uid)
);
CREATE INDEX ix_business_day_override_branch_date
    ON business_day_override (branch_id, target_business_date);
