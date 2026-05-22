-- Route admin permission. permission_seq was bumped past 100 in V4_1,
-- so the hard-coded id 67 cannot collide with JPA-allocated ids.

INSERT INTO permission (id, code, description, module) VALUES
    (67, 'ADMIN.MANAGE_ROUTES', 'Create / edit / deactivate delivery routes', 'admin');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 67;
