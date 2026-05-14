-- Stock count / transfer permissions. Follows the V4 convention.

INSERT INTO permission (id, code, description, module) VALUES
    (19, 'STOCK.COUNT',    'Run and post physical stock counts', 'stock'),
    (20, 'STOCK.TRANSFER', 'Issue and receive inter-branch transfers', 'stock');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (19, 20);
