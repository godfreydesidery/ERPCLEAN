-- Sales invoice + receipt + return + packing-list permissions (F4.x).

INSERT INTO permission (id, code, description, module) VALUES
    (31, 'SALES.MANAGE_INVOICE',  'Create, post, void and cancel sales invoices', 'sales'),
    (32, 'SALES.DISCOUNT_APPROVE','Authorise a discount above the configured threshold', 'sales'),
    (33, 'SALES.MANAGE_RECEIPT',  'Create, post and cancel sales receipts',      'sales'),
    (34, 'SALES.MANAGE_RETURN',   'Create, post and cancel customer returns + credit notes', 'sales'),
    (35, 'SALES.MANAGE_PACKING',  'Create and close packing lists',              'sales');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (31, 32, 33, 34, 35);
