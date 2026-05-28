-- Slice H.1 — PROCUREMENT.MANAGE_RETURN permission (id 136).
-- G.2 took 134-135. H.1 consumes one new id.
-- Granted to ADMIN role (id = 1). Persona widening for procurement-officer
-- + accountant handled via FE seed bootstrap (same pattern as V70 / V74).

INSERT INTO permission (id, code, description, module) VALUES
    (136, 'PROCUREMENT.MANAGE_RETURN',
     'Create, post, cancel, issue, and apply vendor credit notes',
     'procurement');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 136;
