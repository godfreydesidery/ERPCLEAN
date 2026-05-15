-- Stock-batch permissions (F2.4). Follows the V4 convention.

INSERT INTO permission (id, code, description, module) VALUES
    (21, 'STOCK.BATCH', 'Read stock batches and recall an active batch', 'stock');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 21;
