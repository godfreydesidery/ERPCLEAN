-- Slice B procurement hardening — additional read + cancel permissions.
-- Ids 110-113 sit in the free band above the V43 high-water (100, 101) and
-- below the next reserved Slice E band; permission_seq was bumped past 100
-- in V4_1 so explicit ids do not collide with the sequence.

INSERT INTO permission (id, code, description, module) VALUES
    (110, 'PROCUREMENT.MANAGE_LPO.READ', 'List LPOs without edit access',                                   'procurement'),
    (111, 'PROCUREMENT.CANCEL_LPO',      'Cancel an APPROVED LPO (no posted GRN drawing against it)',      'procurement'),
    (112, 'GRN.READ',                    'Read GRN list and detail',                                        'procurement'),
    (113, 'GRN.CANCEL',                  'Cancel a POSTED GRN with compensating stock and outbox event',   'procurement');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (110, 111, 112, 113);
