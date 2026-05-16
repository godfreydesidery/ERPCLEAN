-- Bump sequences past the IDs we hard-coded in V4 so subsequent
-- JPA inserts allocate fresh IDs without collisions.
ALTER SEQUENCE permission_seq RESTART WITH 100;
ALTER SEQUENCE role_seq       RESTART WITH 100;
