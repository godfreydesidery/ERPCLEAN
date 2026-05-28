-- Business-day module permissions. Follows the V4 convention: stable ids,
-- granted to the ADMIN role (role.id 1).
--
-- Slice D (docs/design/slice-d-day-cash.md) introduces granular read-side
-- permissions on band 92-93. The legacy coarse codes 15-17 stay seeded —
-- they remain as group-grant shortcuts on existing role assignments.

INSERT INTO permission (id, code, description, module) VALUES
    (15, 'DAY.OPEN',           'Open a branch business day',                  'day'),
    (16, 'DAY.CLOSE',          'Start closing / close a branch business day', 'day'),
    (17, 'DAY.OVERRIDE',       'Back-date a posting into a closed day',       'day'),
    (92, 'DAY.READ',           'View business-day status across branches',    'day'),
    (93, 'DAY.OVERRIDE_LIST',  'List back-dated overrides',                   'day');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (15, 16, 17, 92, 93);
