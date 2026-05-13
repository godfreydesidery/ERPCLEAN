-- Seed canonical permissions (mirrors com.orbix.engine.modules.iam.domain.enums.Permissions)
-- and the system ADMIN role. The ADMIN role is granted every permission seeded here.
--
-- Add new permissions in a follow-up V<N>__seed_<feature>_permissions.sql migration
-- that also INSERTs into role_permission for ADMIN.
--
-- Permission IDs are stable: 1..N per migration. role.id 1 is ADMIN.
-- Sequences (mysql hibernate_sequence / postgres native) are bumped in dialect
-- companion migrations so subsequent JPA inserts don't collide.

INSERT INTO permission (id, code, description, module) VALUES
    (1,  'IAM.MANAGE_USERS',         'Create, edit, deactivate users',         'iam'),
    (2,  'IAM.MANAGE_ROLES',         'Create roles and grant permissions',     'iam'),
    (3,  'IAM.VIEW_AUDIT',           'Read the audit log',                     'iam'),
    (4,  'ADMIN.MANAGE_BRANCHES',    'Create / edit / deactivate branches',    'admin'),
    (5,  'ADMIN.MANAGE_SECTIONS',    'Create / edit / deactivate sections',    'admin'),
    (6,  'ADMIN.MANAGE_CURRENCIES',  'Enable / disable currencies',            'admin'),
    (7,  'ADMIN.MANAGE_FX',          'Quote FX rates',                         'admin'),
    (8,  'ITEM.CREATE',              'Create catalog items',                   'catalog'),
    (9,  'ITEM.UPDATE',              'Edit catalog items',                     'catalog'),
    (10, 'ITEM.ARCHIVE',             'Archive catalog items',                  'catalog');

INSERT INTO role (id, code, name, description, is_system, status,
                  created_at, updated_at, created_by, updated_by, version)
VALUES (1, 'ADMIN', 'System administrator',
        'Granted every permission by virtue of being the system role.',
        TRUE, 'ACTIVE',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0);

-- Grant every seeded permission to the ADMIN role.
INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p;
