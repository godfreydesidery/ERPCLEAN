-- Procurement-LPO permissions (F3.1).

INSERT INTO permission (id, code, description, module) VALUES
    (25, 'PROCUREMENT.MANAGE_LPO',  'Create, edit, submit, and cancel LPOs', 'procurement'),
    (26, 'PROCUREMENT.APPROVE_LPO', 'Approve a submitted LPO',               'procurement');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (25, 26);
