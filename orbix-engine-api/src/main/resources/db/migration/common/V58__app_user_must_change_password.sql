-- F0.4c — admin-issued password reset / forced-change flow.
-- When TRUE, the access-token shape carries the flag so the web app can
-- bounce the user to a "set new password" screen on first login; the
-- POST /api/v1/users/me/change-password endpoint flips it back to FALSE.
ALTER TABLE app_user
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
