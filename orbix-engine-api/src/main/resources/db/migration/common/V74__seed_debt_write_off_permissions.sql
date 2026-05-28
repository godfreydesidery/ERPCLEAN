-- Slice G.2 — write-off permission band 134-135. Grants both codes to
-- role 1 (ADMIN). The accountant persona is widened via the FE seed
-- bootstrap (same pattern as V70 which seeds DEBT.* band 130-133).
--
-- Dual-approval is enforced by requesterUserId != approverUserId at the
-- service layer — both perms live on the same role for Tanzania-first
-- small-team orgs. Future role split is a one-migration refactor.

INSERT INTO permission (id, code, description, module) VALUES
    (134, 'DEBT.WRITE_OFF.REQUEST', 'Submit a debt write-off request (AR or AP)',           'debt'),
    (135, 'DEBT.WRITE_OFF.APPROVE', 'Approve or reject a pending debt write-off request',   'debt');

-- Grant to ADMIN role (id = 1).
INSERT INTO role_permission (role_id, permission_id)
SELECT 1, p.id FROM permission p WHERE p.id IN (134, 135);
