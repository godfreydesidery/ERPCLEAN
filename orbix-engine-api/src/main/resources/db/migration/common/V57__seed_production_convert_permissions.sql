-- Production conversion + variance report permissions (F7.4). PROD.CONVERT
-- for the production manager / kitchen lead who runs ad-hoc transforms;
-- PROD.READ_REPORT for finance / floor managers consuming the variance dash.

INSERT INTO permission (id, code, description, module) VALUES
    (65, 'PROD.CONVERT',     'Post a non-BOM item conversion (paired consume + output)', 'production'),
    (66, 'PROD.READ_REPORT', 'View production variance + wastage reports',                'production');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (65, 66);
