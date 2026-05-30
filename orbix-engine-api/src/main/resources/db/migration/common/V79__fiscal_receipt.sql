-- Orbix Engine — Tanzania VFD fiscalization scaffold (US-POS-011 / ADR-0006).
-- Adds the fiscal_receipt aggregate and denormalized mirror columns on pos_sale.
--
-- fiscal_receipt: one row per POS sale that requires fiscalization. Multi-tenant
-- (company_id + branch_id). Counters (rctnum, gc, dc, znum) are TRA-mandated
-- monotonic sequences that MUST be managed with row-lock allocation — they are
-- distinct from pos_sale.number. This scaffold persists the final counter values
-- after a successful EFDMS submission; allocation logic lives in FiscalReceiptService.
--
-- pos_sale gains three denormalized read-only mirror columns so the sync-pull
-- and reprint paths never need to cross into the fiscal module:
--   fiscal_status          — mirrors fiscal_receipt.status
--   fiscal_verification_code — mirrors fiscal_receipt.verification_code
--   fiscal_qr_payload        — mirrors fiscal_receipt.qr_payload
-- The existing fiscal_signature column is kept (it stores the device-level RSA
-- signature once the real EFDMS call lands).

-- -----------------------------------------------------------------------
-- 1. fiscal_receipt aggregate
-- -----------------------------------------------------------------------
CREATE TABLE fiscal_receipt (
    id                   BIGINT         NOT NULL PRIMARY KEY,
    uid                  CHAR(26)       NOT NULL,
    company_id           BIGINT         NOT NULL,
    branch_id            BIGINT         NOT NULL,
    pos_sale_id          BIGINT         NOT NULL,

    -- Fiscal lifecycle status.
    -- NONE        = regime is NoOp; fiscalization skipped.
    -- PENDING     = event received; EFDMS call not yet attempted.
    -- PROVISIONAL = awaiting async EFDMS confirmation (till printed provisional receipt).
    -- FISCALIZED  = EFDMS accepted; verification artefacts populated.
    -- FAILED      = max retries exhausted / DEAD_LETTERED.
    -- EXEMPT      = sale class does not require fiscalization (future: B2G, internal).
    status               VARCHAR(20)    NOT NULL DEFAULT 'PENDING',

    -- Provider that handled this receipt (e.g. "TZ_VFD", "NONE").
    provider             VARCHAR(40),

    -- STUB fields: populated by the real EFDMS response. All tagged // STUB in the entity.
    -- TRA-mandated receipt counters (per-device, never reused).
    rctnum               BIGINT,        -- RCTNUM: receipt counter (monotonic per device)
    gc                   BIGINT,        -- GC: grand cumulative counter
    dc                   BIGINT,        -- DC: daily counter
    znum                 INT,           -- ZNUM: Z-report number (day counter)

    -- Verification artefacts that must appear on the printed receipt.
    verification_code    VARCHAR(200),  -- TRA verification code
    verify_url           VARCHAR(500),  -- https://verify.tra.go.tz/... URL
    qr_payload           VARCHAR(2000), -- QR code payload (URL or encoded data)
    signature            VARCHAR(2000), -- RSA device signature of the submitted receipt

    -- Raw EFDMS response (truncated to avoid LOB issues on older MariaDB).
    efdms_response       VARCHAR(4000),

    -- Retry tracking (outbox handles retry; this is a mirror for observability).
    attempt_count        INT            NOT NULL DEFAULT 0,
    last_error           VARCHAR(1000),

    -- Timestamps.
    submitted_at         TIMESTAMP,     -- when the EFDMS POST succeeded
    created_at           TIMESTAMP      NOT NULL,
    updated_at           TIMESTAMP      NOT NULL,
    created_by           BIGINT         NOT NULL,
    updated_by           BIGINT         NOT NULL,

    CONSTRAINT uk_fiscal_receipt_uid        UNIQUE (uid),
    CONSTRAINT uk_fiscal_receipt_pos_sale   UNIQUE (pos_sale_id),
    CONSTRAINT fk_fiscal_receipt_company    FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_fiscal_receipt_branch     FOREIGN KEY (branch_id)  REFERENCES branch  (id),
    CONSTRAINT fk_fiscal_receipt_pos_sale   FOREIGN KEY (pos_sale_id) REFERENCES pos_sale (id)
);

CREATE INDEX ix_fiscal_receipt_company_status ON fiscal_receipt (company_id, status);
CREATE INDEX ix_fiscal_receipt_branch_status  ON fiscal_receipt (branch_id,  status);

-- -----------------------------------------------------------------------
-- 2. Denormalized mirror columns on pos_sale
-- -----------------------------------------------------------------------
-- fiscal_status: mirrors fiscal_receipt.status; NULL = no fiscalization
-- attempted yet (regime=NONE or event not yet consumed).
ALTER TABLE pos_sale ADD COLUMN fiscal_status            VARCHAR(20);
ALTER TABLE pos_sale ADD COLUMN fiscal_verification_code VARCHAR(200);
ALTER TABLE pos_sale ADD COLUMN fiscal_qr_payload        VARCHAR(2000);

CREATE INDEX ix_pos_sale_fiscal_status ON pos_sale (fiscal_status);

-- -----------------------------------------------------------------------
-- 3. Permissions
-- -----------------------------------------------------------------------
-- Permission to view fiscal receipt status (ops console, accountants).
INSERT INTO permission (id, code, description, module) VALUES
    (80, 'FISCAL.VIEW',       'View fiscal receipt status and verification artefacts', 'fiscal'),
    (81, 'FISCAL.ADMIN',      'Manage fiscal device configuration and trigger manual re-fiscalization', 'fiscal');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (80, 81);
