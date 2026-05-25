-- Dedicated price-list / price-setting permissions. Pricing is sensitive
-- enough to warrant its own permissions rather than reusing the ITEM.* perms.
-- permission_seq was bumped past 100 in V4_1, so hard-coded ids 70..74 cannot
-- collide with JPA-allocated ids.

INSERT INTO permission (id, code, description, module) VALUES
    (70, 'PRICE_LIST.CREATE',  'Create price lists',                       'catalog'),
    (71, 'PRICE_LIST.UPDATE',  'Edit price lists',                         'catalog'),
    (72, 'PRICE_LIST.ARCHIVE', 'Archive / restore price lists',            'catalog'),
    (73, 'PRICE.SET',          'Set, discontinue and bulk-adjust prices',  'catalog'),
    (74, 'PRICE.APPROVE',      'Authorise price changes above threshold',  'catalog');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (70, 71, 72, 73, 74);
