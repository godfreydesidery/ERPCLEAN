-- Supplier-payment permissions (F3.4). Lives in the cash module.

INSERT INTO permission (id, code, description, module) VALUES
    (30, 'CASH.MANAGE_SUPPLIER_PAYMENT', 'Create, allocate, post, and cancel supplier payments', 'cash');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 30;
