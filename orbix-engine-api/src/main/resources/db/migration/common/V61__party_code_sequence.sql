-- Auto-generated party codes per company + prefix. One row per
-- (company_id, prefix); read+increment under optimistic lock. Used
-- by the role-creation forms to suggest the next free code; the
-- user can still type their own (legacy imports, meaningful codes).
--
-- Numbers may skip on abandoned forms — by design; gap-free numbering
-- would need synchronous allocation at submit time and is not worth it.

CREATE TABLE party_code_sequence (
    id            BIGINT      NOT NULL PRIMARY KEY,
    company_id    BIGINT      NOT NULL,
    prefix        VARCHAR(10) NOT NULL,
    current_value BIGINT      NOT NULL DEFAULT 0,
    version       INT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_party_code_seq_company_prefix UNIQUE (company_id, prefix),
    CONSTRAINT fk_party_code_seq_company FOREIGN KEY (company_id) REFERENCES company (id)
);
