-- Bump native sequences past the IDs we hard-coded in V4.
ALTER SEQUENCE permission_seq RESTART WITH 100;
ALTER SEQUENCE role_seq       RESTART WITH 100;
