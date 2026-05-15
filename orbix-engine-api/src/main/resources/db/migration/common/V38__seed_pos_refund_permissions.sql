-- POS refund permissions (F5.5).

INSERT INTO permission (id, code, description, module) VALUES
    (45, 'POS.REFUND_POST',    'Post a refund against a POSTED POS sale',                 'pos'),
    (46, 'POS.REFUND_APPROVE', 'Authorise an above-threshold refund total at the till',   'pos');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (45, 46);
