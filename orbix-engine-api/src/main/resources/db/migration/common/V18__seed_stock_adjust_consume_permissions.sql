-- Stock adjustment + internal-consumption permissions (F2.5).

INSERT INTO permission (id, code, description, module) VALUES
    (22, 'STOCK.ADJUST',               'Post a manual stock adjustment', 'stock'),
    (23, 'STOCK.ADJUST_APPROVE',       'Authorise an above-threshold stock adjustment', 'stock'),
    (24, 'STOCK.INTERNAL_CONSUMPTION', 'Post an internal-consumption (canteen / display / samples) write-off', 'stock');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (22, 23, 24);
