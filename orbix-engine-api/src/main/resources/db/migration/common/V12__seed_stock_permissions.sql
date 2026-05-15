-- Stock-module permissions. Follows the V4 convention: stable ids, granted to
-- the ADMIN role (role.id 1).

INSERT INTO permission (id, code, description, module) VALUES
    (18, 'STOCK.OVERSELL', 'Post a stock move that drives quantity negative', 'stock');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 18;
