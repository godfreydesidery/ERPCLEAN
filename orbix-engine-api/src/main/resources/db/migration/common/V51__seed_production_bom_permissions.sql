-- Production-BOM permissions (F7.3a). PROD.MANAGE_BOM for chefs / production
-- managers who author recipes; PROD.READ_BOM for cashiers / floor staff who
-- need to inspect a recipe (cost transparency, allergens, etc.).

INSERT INTO permission (id, code, description, module) VALUES
    (60, 'PROD.MANAGE_BOM', 'Create / edit / activate / retire / version a BOM', 'production'),
    (61, 'PROD.READ_BOM',   'View BOMs and their line composition',              'production');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (60, 61);
