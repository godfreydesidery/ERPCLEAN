-- Bump emulated sequences past the IDs we hard-coded in V4 so subsequent
-- JPA inserts allocate fresh IDs without collisions.
UPDATE hibernate_sequence SET next_val = 100 WHERE sequence_name = 'permission_seq';
UPDATE hibernate_sequence SET next_val = 100 WHERE sequence_name = 'role_seq';
