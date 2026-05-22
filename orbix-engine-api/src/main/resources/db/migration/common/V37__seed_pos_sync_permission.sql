-- POS offline sync permission (F5.4). Held by the till's install identity so a
-- cashier device can pull catalog snapshots + push queued sales.

INSERT INTO permission (id, code, description, module) VALUES
    (44, 'POS.SYNC', 'Push queued POS sales + pull catalog/balance snapshots from a till', 'pos');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 44;
