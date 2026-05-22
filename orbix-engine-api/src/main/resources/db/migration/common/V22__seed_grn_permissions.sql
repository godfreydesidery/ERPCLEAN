-- GRN permissions (F3.2).

INSERT INTO permission (id, code, description, module) VALUES
    (27, 'GRN.POST',   'Create and post a Goods Received Note',     'procurement'),
    (28, 'GRN.DIRECT', 'Post a direct GRN without referencing an LPO', 'procurement');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (27, 28);
