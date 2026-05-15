-- Supplier-invoice permissions (F3.3).

INSERT INTO permission (id, code, description, module) VALUES
    (29, 'PROCUREMENT.MANAGE_INVOICE', 'Create, match, post, and cancel supplier invoices', 'procurement');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 29;
