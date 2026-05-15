-- POS sale void + discount approval permissions (F5.3).

INSERT INTO permission (id, code, description, module) VALUES
    (42, 'POS.SALE_VOID',        'Void a POSTED POS sale on the same business day', 'pos'),
    (43, 'POS.DISCOUNT_APPROVE', 'Authorise a POS-sale line discount above the threshold', 'pos');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (42, 43);
