-- Orbix Engine — production: BOM + BOM line (F7.3a). DATA-MODEL §9.1 / §9.2
-- + Phase 1.1 additions (parent_bom_id, section_id).
--
-- A BOM is a versioned recipe — one output_item × output_qty per execution.
-- Multiple versions of the same recipe coexist via `version` UNIQUE-per-item;
-- bom.status drives DRAFT (editable) -> ACTIVE (immutable, schedulable) -> RETIRED.
--
-- Sub-recipes: a bom_line can reference EITHER a raw material (input_item_id)
-- OR another BOM (sub_bom_id) — the planner explodes sub-BOMs at plan time
-- in F7.3b. Cycle detection is enforced by the service before activation.
--
-- bom.parent_bom_id is the legacy hierarchical link (per DATA-MODEL §17.12) and
-- is mostly null for top-level recipes; the operational sub-recipe link is
-- bom_line.sub_bom_id.

CREATE TABLE bom (
    id                    BIGINT         NOT NULL PRIMARY KEY,
    company_id            BIGINT         NOT NULL,
    section_id            BIGINT         NOT NULL,
    parent_bom_id         BIGINT,
    output_item_id        BIGINT         NOT NULL,
    output_qty            DECIMAL(18, 4) NOT NULL,
    output_uom_id         BIGINT         NOT NULL,
    version               INT            NOT NULL,
    valid_from            DATE           NOT NULL,
    valid_to              DATE,
    standard_yield_pct    DECIMAL(10, 4) NOT NULL DEFAULT 100,
    status                VARCHAR(32)    NOT NULL,
    notes                 VARCHAR(2000),
    version_no            INT            NOT NULL DEFAULT 0,
    created_at            TIMESTAMP      NOT NULL,
    updated_at            TIMESTAMP      NOT NULL,
    created_by            BIGINT         NOT NULL,
    updated_by            BIGINT         NOT NULL,
    CONSTRAINT uk_bom_output_version UNIQUE (output_item_id, version),
    CONSTRAINT fk_bom_company        FOREIGN KEY (company_id)     REFERENCES company  (id),
    CONSTRAINT fk_bom_section        FOREIGN KEY (section_id)     REFERENCES section  (id),
    CONSTRAINT fk_bom_parent         FOREIGN KEY (parent_bom_id)  REFERENCES bom      (id),
    CONSTRAINT fk_bom_output_item    FOREIGN KEY (output_item_id) REFERENCES item     (id),
    CONSTRAINT fk_bom_output_uom     FOREIGN KEY (output_uom_id)  REFERENCES uom      (id)
);
CREATE INDEX ix_bom_company_status ON bom (company_id, status);
CREATE INDEX ix_bom_section        ON bom (section_id);
CREATE INDEX ix_bom_output_item    ON bom (output_item_id);

CREATE TABLE bom_line (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    bom_id              BIGINT         NOT NULL,
    line_no             INT            NOT NULL,
    input_item_id       BIGINT,
    sub_bom_id          BIGINT,
    qty                 DECIMAL(18, 4) NOT NULL,
    uom_id              BIGINT         NOT NULL,
    wastage_pct         DECIMAL(10, 4) NOT NULL DEFAULT 0,
    notes               VARCHAR(200),
    CONSTRAINT uk_bom_line_no UNIQUE (bom_id, line_no),
    CONSTRAINT fk_bom_line_bom        FOREIGN KEY (bom_id)        REFERENCES bom  (id),
    CONSTRAINT fk_bom_line_input      FOREIGN KEY (input_item_id) REFERENCES item (id),
    CONSTRAINT fk_bom_line_sub_bom    FOREIGN KEY (sub_bom_id)    REFERENCES bom  (id),
    CONSTRAINT fk_bom_line_uom        FOREIGN KEY (uom_id)        REFERENCES uom  (id)
);
CREATE INDEX ix_bom_line_bom     ON bom_line (bom_id);
CREATE INDEX ix_bom_line_input   ON bom_line (input_item_id);
CREATE INDEX ix_bom_line_sub_bom ON bom_line (sub_bom_id);
