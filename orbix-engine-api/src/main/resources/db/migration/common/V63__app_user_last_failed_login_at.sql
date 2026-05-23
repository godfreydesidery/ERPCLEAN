-- Rolling-window account lockout: records when the most recent failed login
-- happened so the failure counter can decay (see AppUser.recordFailedLogin /
-- AuthServiceImpl). Without it, stale failures accumulate forever (4 misses
-- last month + 1 today = instant lockout) and a single miss right after a
-- lockout window expires re-locks immediately.
ALTER TABLE app_user
    ADD COLUMN last_failed_login_at TIMESTAMP;
