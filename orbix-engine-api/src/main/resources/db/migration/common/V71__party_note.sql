-- Slice G — chase-note schema. One table for every kind of party note:
-- customer-AR chase, supplier-AP chase (reserved Slice G.1), and a
-- generic kind. Lives in modules/party because the data crosses
-- customer + supplier symmetry; ADR-0005 ratifies the "no new
-- modules/debt" call.
-- DATA-MODEL.md §2.x (party).

CREATE TABLE party_note (
    id          BIGINT        NOT NULL PRIMARY KEY,
    uid         CHAR(26)      NOT NULL,
    company_id  BIGINT        NOT NULL,
    party_id    BIGINT        NOT NULL,
    kind        VARCHAR(20)   NOT NULL,    -- AR_CHASE | AP_CHASE | GENERAL
    body        VARCHAR(1000) NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | ARCHIVED
    archived_at TIMESTAMP,
    archived_by BIGINT,
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP     NOT NULL,
    created_by  BIGINT        NOT NULL,
    updated_by  BIGINT        NOT NULL,
    version     INT           NOT NULL DEFAULT 0,
    CONSTRAINT uk_party_note_uid     UNIQUE (uid),
    CONSTRAINT fk_party_note_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_party_note_party   FOREIGN KEY (party_id)   REFERENCES party   (id)
);

-- Customer drill-down activity log: notes for a party, newest first.
CREATE INDEX ix_party_note_party_created ON party_note (party_id, created_at);

-- Future chase-activity roll-up by company + kind + lifecycle.
CREATE INDEX ix_party_note_company_kind   ON party_note (company_id, kind, status);
