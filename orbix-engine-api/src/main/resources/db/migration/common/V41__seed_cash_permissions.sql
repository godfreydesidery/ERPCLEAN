-- Cash module direct-write permissions (F6.1). Read endpoints, supervisor
-- adjustments, and end-of-day banking deposits. The bulk of writes are
-- event-driven (from pos / sales / procurement / day) and don't need a
-- dedicated permission — they inherit authorisation from the source flow.
--
-- Slice D (docs/design/slice-d-day-cash.md) splits the coarse codes 48-50
-- into granular per-aggregate codes on band 94-99. The legacy coarse codes
-- stay seeded and granted so existing role assignments keep working as
-- group-grants; controllers move to the granular codes (reads accept the
-- coarse code via hasAnyAuthority for backward compatibility, writes
-- require the granular code).

INSERT INTO permission (id, code, description, module) VALUES
    (48, 'CASH.READ',    'Read cash ledger entries and cash-book balances', 'cash'),
    (49, 'CASH.ADJUST',  'Post supervisor cash adjustments',                'cash'),
    (50, 'CASH.BANKING', 'Record end-of-day CASH_BOX → BANK deposits',      'cash'),
    (94, 'CASH.ENTRY.READ',          'Read the append-only cash ledger',          'cash'),
    (95, 'CASH.BOOK.READ',           'Read cash-book balances',                   'cash'),
    (96, 'CASH.ADJUSTMENT.POST',     'Post a supervisor cash adjustment',         'cash'),
    (97, 'CASH.ADJUSTMENT.ARCHIVE',  'Reverse a posted cash adjustment',          'cash'),
    (98, 'CASH.BANK_DEPOSIT.POST',   'Record an EOD bank deposit',                'cash'),
    (99, 'CASH.BANK_DEPOSIT.ARCHIVE','Reverse a bank deposit',                    'cash');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (48, 49, 50, 94, 95, 96, 97, 98, 99);
