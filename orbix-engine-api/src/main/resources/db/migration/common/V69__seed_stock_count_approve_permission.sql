-- Slice E1 stock-spine hardening — add the STOCK.COUNT_APPROVE permission used
-- by the count-post dual-control gate when the total variance value exceeds
-- the configured monetary threshold. Mirror of STOCK.ADJUST_APPROVE (V18 id 23).
-- Permission id 123 sits in the free band above V67/V68 (110-122).

INSERT INTO permission (id, code, description, module) VALUES
    (123, 'STOCK.COUNT_APPROVE', 'Authorise an above-threshold stock-count post (dual control)', 'stock');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (123);
