-- Production-wastage permission (F7.3c). Distinct from PROD.MANAGE_BATCH
-- because some chains require a senior signoff on wastage (donations, write-
-- offs) while the batch operator can still plan / start / post output.

INSERT INTO permission (id, code, description, module) VALUES
    (64, 'PROD.RECORD_WASTAGE', 'Record category-tagged wastage against a production batch', 'production');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 64;
