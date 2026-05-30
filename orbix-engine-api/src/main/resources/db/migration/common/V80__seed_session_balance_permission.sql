-- ISSUE-CASH-001: add a granular read permission for the till-session
-- cash-balance endpoint (GET /api/v1/till-sessions/uid/{uid}/balance).
--
-- Existing till-session permissions (36-40) already cover manage/open/close/
-- reconcile/variance-approve. The new code 137 lets a read-only role query
-- the live balance without granting the write operations.
--
-- The controller also accepts POS.MANAGE_TILL / POS.SESSION_OPEN /
-- POS.SESSION_CLOSE via hasAnyAuthority so existing role assignments continue
-- to work without any migration of role_permission rows.

INSERT INTO permission (id, code, description, module) VALUES
    (137, 'POS.SESSION_BALANCE_READ',
     'Read the live cash-balance breakdown for a till session',
     'pos');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id = 137;
