-- Customer-order module permissions (F7.2). ORDER.MANAGE for back-office /
-- cashier flows (create, reserve, pay, cancel). ORDER.COLLECT for the till
-- collection action — split because a POS device should be able to collect
-- without holding the broader MANAGE grant. ORDER.READ for read-only lookups
-- (the customer-service desk).

INSERT INTO permission (id, code, description, module) VALUES
    (57, 'ORDER.MANAGE',  'Create / edit / cancel layby + pre-order, accept payments', 'orders'),
    (58, 'ORDER.COLLECT', 'Collect a layby / pre-order at the till',                   'orders'),
    (59, 'ORDER.READ',    'Look up customer orders + payment history',                 'orders');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (57, 58, 59);
