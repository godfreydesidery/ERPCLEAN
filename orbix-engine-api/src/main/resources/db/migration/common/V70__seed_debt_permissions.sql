-- Slice G — debt module hardening. Adds the DEBT.* permission namespace
-- (4 codes) and grants them all to role 1 (ADMIN). Other personas are
-- widened via the FE seed bootstrap (accountant gains DEBT.* per the
-- slice-g plan §8).
--
-- Permission ids 130-133 sit in the next free band above the Slice C
-- high-water mark (125, SALES.REPORT.AR_SUMMARY). 126-129 left free for
-- in-flight slice fix-ups. permission_seq was bumped past 100 in V4_1,
-- so hard-coded ids cannot collide with JPA-allocated ids.

INSERT INTO permission (id, code, description, module) VALUES
    (130, 'DEBT.READ',                'Read the AR aging buckets, dunning queue and customer debt position', 'debt'),
    (131, 'DEBT.NOTE.CREATE',         'Append a chase note against a customer',            'debt'),
    (132, 'DEBT.NOTE.ARCHIVE',        'Archive (soft-delete) a chase note',                'debt'),
    (133, 'DEBT.CREDIT_LIMIT.UPDATE', 'Adjust a customer credit limit from the debt surface', 'debt');

INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (130, 131, 132, 133);
