-- F5.9 permissions: record a cash pickup or pay petty cash from the till.
-- Both require a supervisor authoriser at the service layer; the controller
-- only checks that the caller has the per-action permission.
--
-- Slice D — read endpoints for both aggregates split out onto granular
-- codes on band 100-101. Writes stay on the existing 51/52 codes; reads
-- accept either the existing write code or the new read code via
-- hasAnyAuthority on the controller (so read-only role assignments are now
-- possible without granting the write).

INSERT INTO permission (id, code, description, module) VALUES
    (51,  'POS.CASH_PICKUP',      'Record a mid-shift cash pickup from the till', 'pos'),
    (52,  'POS.PETTY_CASH',       'Record a petty-cash payout from the till',     'pos'),
    (100, 'POS.CASH_PICKUP.READ', 'List cash pickups for a till session',         'pos'),
    (101, 'POS.PETTY_CASH.READ',  'List petty-cash payouts for a till session',   'pos');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (51, 52, 100, 101);
