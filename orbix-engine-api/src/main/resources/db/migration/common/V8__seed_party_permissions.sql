-- Party-module permissions (mirrors com.orbix.engine.modules.iam.domain.enums.Permissions).
-- Fine-grained per-action permissions, one set per party type. Follows the
-- hardening Definition of Done — same shape as the catalog UoM / PriceList
-- migrations. permission_seq is bumped past 100 in V4_1, so fixed ids in
-- [1, 99] are safe from JPA-allocated collisions. Band 80..91 is picked
-- to sit above the current high-water mark in other seed migrations
-- (V10 / V12 / V14 / V16 / V18 use 15..22; later modules climb to 74).

INSERT INTO permission (id, code, description, module) VALUES
    (80, 'CUSTOMER.CREATE',     'Create customers',                            'party'),
    (81, 'CUSTOMER.UPDATE',     'Edit customers and their underlying party',   'party'),
    (82, 'CUSTOMER.ARCHIVE',    'Archive / restore customers',                 'party'),
    (83, 'SUPPLIER.CREATE',     'Create suppliers',                            'party'),
    (84, 'SUPPLIER.UPDATE',     'Edit suppliers and their underlying party',   'party'),
    (85, 'SUPPLIER.ARCHIVE',    'Archive / restore suppliers',                 'party'),
    (86, 'EMPLOYEE.CREATE',     'Create employees',                            'party'),
    (87, 'EMPLOYEE.UPDATE',     'Edit employees and their underlying party',   'party'),
    (88, 'EMPLOYEE.ARCHIVE',    'Archive / restore employees',                 'party'),
    (89, 'SALES_AGENT.CREATE',  'Create sales agents',                         'party'),
    (90, 'SALES_AGENT.UPDATE',  'Edit sales agents and their underlying party','party'),
    (91, 'SALES_AGENT.ARCHIVE', 'Archive / restore sales agents',              'party');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id BETWEEN 80 AND 91;
