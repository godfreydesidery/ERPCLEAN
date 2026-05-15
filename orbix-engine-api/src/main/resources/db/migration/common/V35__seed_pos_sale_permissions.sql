-- POS sale permission (F5.2).

INSERT INTO permission (id, code, description, module) VALUES
    (41, 'POS.SALE_POST', 'Push a cashier sale from the till to the server', 'pos');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 41;
