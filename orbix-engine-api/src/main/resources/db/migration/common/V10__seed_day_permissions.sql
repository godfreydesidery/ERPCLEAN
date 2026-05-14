-- Business-day module permissions. Follows the V4 convention: stable ids,
-- granted to the ADMIN role (role.id 1).

INSERT INTO permission (id, code, description, module) VALUES
    (15, 'DAY.OPEN',     'Open a branch business day',                 'day'),
    (16, 'DAY.CLOSE',    'Start closing / close a branch business day', 'day'),
    (17, 'DAY.OVERRIDE', 'Back-date a posting into a closed day',       'day');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (15, 16, 17);
