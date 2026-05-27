-- Party-module permissions (mirrors com.orbix.engine.modules.iam.domain.enums.Permissions).
-- Fine-grained per-action permissions, one set per party type. Follows the
-- hardening Definition of Done — same shape as the catalog UoM / PriceList
-- migrations. permission_seq was bumped past 100 in V4_1, so these fixed
-- ids (11..22) cannot collide with JPA-allocated ids.

INSERT INTO permission (id, code, description, module) VALUES
    (11, 'CUSTOMER.CREATE',     'Create customers',                            'party'),
    (12, 'CUSTOMER.UPDATE',     'Edit customers and their underlying party',   'party'),
    (13, 'CUSTOMER.ARCHIVE',    'Archive / restore customers',                 'party'),
    (14, 'SUPPLIER.CREATE',     'Create suppliers',                            'party'),
    (15, 'SUPPLIER.UPDATE',     'Edit suppliers and their underlying party',   'party'),
    (16, 'SUPPLIER.ARCHIVE',    'Archive / restore suppliers',                 'party'),
    (17, 'EMPLOYEE.CREATE',     'Create employees',                            'party'),
    (18, 'EMPLOYEE.UPDATE',     'Edit employees and their underlying party',   'party'),
    (19, 'EMPLOYEE.ARCHIVE',    'Archive / restore employees',                 'party'),
    (20, 'SALES_AGENT.CREATE',  'Create sales agents',                         'party'),
    (21, 'SALES_AGENT.UPDATE',  'Edit sales agents and their underlying party','party'),
    (22, 'SALES_AGENT.ARCHIVE', 'Archive / restore sales agents',              'party');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id BETWEEN 11 AND 22;
