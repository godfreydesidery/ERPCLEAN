-- Sales invoice + receipt + return + packing-list permissions (F4.x).
-- Slice C hardening adds fine-grained codes 120-122 alongside the coarse
-- SALES.MANAGE_* codes (collision-checked clean against V*.sql).

INSERT INTO permission (id, code, description, module) VALUES
    (31,  'SALES.MANAGE_INVOICE',         'Create, post, void and cancel sales invoices', 'sales'),
    (32,  'SALES.DISCOUNT_APPROVE',       'Authorise a discount above the configured threshold', 'sales'),
    (33,  'SALES.MANAGE_RECEIPT',         'Create, post and cancel sales receipts',      'sales'),
    (34,  'SALES.MANAGE_RETURN',          'Create, post and cancel customer returns + credit notes', 'sales'),
    (35,  'SALES.MANAGE_PACKING',         'Create and close packing lists',              'sales'),
    (120, 'SALES_INVOICE.OVERRIDE_CREDIT','Post a sales invoice that exceeds the customer credit limit (requires reason)', 'sales'),
    (121, 'SALES.REPORT.AR_SUMMARY',      'Read the AR summary endpoint for dashboard tiles', 'sales'),
    (122, 'SALES_INVOICE.REPRINT',        'Re-print a posted invoice (recorded in audit)', 'sales');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (31, 32, 33, 34, 35, 120, 121, 122);
