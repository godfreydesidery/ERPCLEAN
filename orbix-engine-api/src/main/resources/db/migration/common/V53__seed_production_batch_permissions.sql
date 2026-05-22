-- Production-batch permissions (F7.3b). PROD.MANAGE_BATCH for production
-- operators (plan, start, record output, cancel); PROD.READ_BATCH for floor
-- staff / managers running variance reports.

INSERT INTO permission (id, code, description, module) VALUES
    (62, 'PROD.MANAGE_BATCH', 'Plan / start / cancel a production batch, record output', 'production'),
    (63, 'PROD.READ_BATCH',   'View production batches + consumption + output history',  'production');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (62, 63);
