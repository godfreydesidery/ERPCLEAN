-- F5.9 permissions: record a cash pickup or pay petty cash from the till.
-- Both require a supervisor authoriser at the service layer; the controller
-- only checks that the caller has the per-action permission.

INSERT INTO permission (id, code, description, module) VALUES
    (51, 'POS.CASH_PICKUP', 'Record a mid-shift cash pickup from the till',     'pos'),
    (52, 'POS.PETTY_CASH',  'Record a petty-cash payout from the till',         'pos');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (51, 52);
