-- Unit-of-measure management permission. permission_seq was bumped past 100
-- in V4_1, so the hard-coded id 69 cannot collide with JPA-allocated ids.
--
-- UoM is the one global (all-tenant) catalog reference table, so it gets its
-- own permission rather than reusing the company-scoped ITEM.* perms.

INSERT INTO permission (id, code, description, module) VALUES
    (69, 'UOM.MANAGE', 'Create / edit / archive units of measure', 'catalog');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 69;
