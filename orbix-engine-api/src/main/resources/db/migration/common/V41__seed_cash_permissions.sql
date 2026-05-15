-- Cash module direct-write permissions (F6.1). Read endpoints, supervisor
-- adjustments, and end-of-day banking deposits. The bulk of writes are
-- event-driven (from pos / sales / procurement / day) and don't need a
-- dedicated permission — they inherit authorisation from the source flow.

INSERT INTO permission (id, code, description, module) VALUES
    (48, 'CASH.READ',    'Read cash ledger entries and cash-book balances', 'cash'),
    (49, 'CASH.ADJUST',  'Post supervisor cash adjustments',                'cash'),
    (50, 'CASH.BANKING', 'Record end-of-day CASH_BOX → BANK deposits',      'cash');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (48, 49, 50);
