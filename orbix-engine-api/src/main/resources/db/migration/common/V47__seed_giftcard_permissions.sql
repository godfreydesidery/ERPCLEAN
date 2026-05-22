-- Gift-card module permissions (F7.1). GIFTCARD.ISSUE for cashiers issuing
-- cards; GIFTCARD.REDEEM for cashiers tendering cards (also used at refund);
-- GIFTCARD.LOOKUP for balance inquiry without a redemption; GIFTCARD.FREEZE
-- for managers reporting lost / stolen cards.

INSERT INTO permission (id, code, description, module) VALUES
    (53, 'GIFTCARD.ISSUE',  'Issue a new gift card and collect payment',  'giftcard'),
    (54, 'GIFTCARD.REDEEM', 'Redeem or refund a gift card as POS tender', 'giftcard'),
    (55, 'GIFTCARD.LOOKUP', 'Look up a gift card balance and history',    'giftcard'),
    (56, 'GIFTCARD.FREEZE', 'Freeze or unfreeze a lost / stolen card',    'giftcard');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (53, 54, 55, 56);
