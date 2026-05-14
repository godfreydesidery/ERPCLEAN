-- Party-module permissions (mirrors com.orbix.engine.modules.iam.domain.enums.Permissions).
-- Follows the V4 convention: stable ids, granted to the ADMIN role (role.id 1).
-- permission_seq was bumped past 100 in V4_1, so these fixed ids do not collide.

INSERT INTO permission (id, code, description, module) VALUES
    (11, 'PARTY.MANAGE_CUSTOMERS', 'Create / edit / deactivate customers',     'party'),
    (12, 'PARTY.MANAGE_SUPPLIERS', 'Create / edit / deactivate suppliers',     'party'),
    (13, 'PARTY.MANAGE_EMPLOYEES', 'Create / edit / deactivate employees',     'party'),
    (14, 'PARTY.MANAGE_AGENTS',    'Create / edit / deactivate sales agents',  'party');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (11, 12, 13, 14);
