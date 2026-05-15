-- POS till + session permissions (F5.1).

INSERT INTO permission (id, code, description, module) VALUES
    (36, 'POS.MANAGE_TILL',           'Create, update, and deactivate POS tills', 'pos'),
    (37, 'POS.SESSION_OPEN',          'Open a till session as a cashier',          'pos'),
    (38, 'POS.SESSION_CLOSE',         'Close a till session and declare cash',     'pos'),
    (39, 'POS.SESSION_RECONCILE',     'Reconcile a closed till session',           'pos'),
    (40, 'POS.SESSION_VARIANCE_APPROVE', 'Authorise an above-threshold till variance', 'pos');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (36, 37, 38, 39, 40);
