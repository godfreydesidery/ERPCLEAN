# Auth — test plan

Module-level tests for login, lockout, JWT issuance, refresh, RBAC, dev seed. See [e2e-scenarios.md](../e2e-scenarios.md) for cross-module flows (E15 covers auth interactions).

## Happy paths

### TC-AUTH-001 — Login with valid credentials [P1]
**Type:** Functional · **Stories:** US-IAM-001

**Preconditions:** User `admin` exists, status ACTIVE, password `orbix`, default_company_id and default_branch_id set.

**Steps:**
1. POST /api/v1/auth/login `{ username: "admin", password: "orbix" }`.

**Expected:**
- 200; response has `accessToken`, `tokenType: "Bearer"`, `expiresInSeconds`, `user`.
- `app_user.last_login_at` set to now.
- `app_user.failed_login_count = 0`.
- JWT decodes to: `iss = orbix-engine`, `uid = admin.id`, `cid = admin.defaultCompanyId`, `bid = admin.defaultBranchId`, `privs = []` (until RBAC wired).

### TC-AUTH-002 — DevSeed creates admin on empty DB [P1]
**Type:** Functional · **Stories:** —

**Preconditions:** `app_user` table is empty. Profile `local` active.

**Steps:**
1. Start the application.

**Expected:**
- Logs print the dev credentials banner.
- `organisation`, `company`, `branch`, `app_user (admin)` rows created exactly once.
- Restart on a non-empty DB skips seeding (logged "admin already present").

### TC-AUTH-003 — Decoded JWT carries branch override [P1]
**Type:** Functional · **Stories:** US-COMP-005

**Steps:**
1. Login → JWT has `bid = X`.
2. Send a request with header `X-Branch-Id: Y` (Y ≠ X; user has access to Y).

**Expected:** `RequestContext.branchId = Y` for the request. Audit log on any write records Y, not X.

## Negative paths

### TC-AUTH-004 — Wrong password [P1]
**Steps:** POST login with `{ username: "admin", password: "wrong" }`.
**Expected:** 401 `{ "error": "invalid_credentials" }`. `failed_login_count` incremented. No JWT issued.

### TC-AUTH-005 — Unknown username [P1]
**Steps:** POST login with `{ username: "ghost", password: "anything" }`.
**Expected:** 401 `{ "error": "invalid_credentials" }`. Same response shape as wrong-password (no enumeration leak).

### TC-AUTH-006 — Lockout after 5 failed attempts [P1]
**Stories:** US-IAM-001
**Steps:** 5 wrong-password attempts.
**Expected:** After 5th: `app_user.locked_until = now + 15m`. Subsequent attempts (even with right password) return 401 within the window.

### TC-AUTH-007 — Lockout auto-clears after window + successful login [P1]
**Steps:** From locked state, wait 15 minutes, login with correct password.
**Expected:** 200; `failed_login_count = 0`, `locked_until = null`.

### TC-AUTH-008 — Inactive user cannot log in [P1]
**Steps:** Set `app_user.status = INACTIVE`, then POST login with correct password.
**Expected:** 401 `invalid_credentials`. `canLogIn()` returns false regardless of password.

### TC-AUTH-009 — Bean-validation rejects empty username/password [P1]
**Steps:** POST login with `{ username: "", password: "" }`.
**Expected:** 400 with field-level error per `@NotBlank` constraint.

## JWT lifecycle

### TC-AUTH-010 — JWT expires after access-ttl [P1]
**Stories:** US-IAM-001 AC
**Steps:**
1. Issue a token. Wait `access-ttl + 1s` (test-only short TTL).
2. Use the token.

**Expected:** 401 `Invalid token`.

### TC-AUTH-011 — JWT signature validated on every request [P1]
**Type:** Security
**Steps:** Tamper with a token's payload, then call any privileged endpoint.
**Expected:** 401. Audit log records the failed attempt.

### TC-AUTH-012 — Missing Authorization header [P1]
**Steps:** Call a privileged endpoint without `Authorization`.
**Expected:** 401 or 403 depending on Spring's filter chain (current config: 401).

### TC-AUTH-013 — Malformed Bearer token [P1]
**Steps:** `Authorization: Bearer not.a.jwt`.
**Expected:** 401 `Invalid token`.

## RBAC (when wired)

### TC-AUTH-014 — Privileges loaded from user_role / role_privilege at login [P1]
**Stories:** US-IAM-008, US-IAM-009
**Preconditions:** User has roles granting `ITEM.CREATE` and `STOCK.OVERSELL`.
**Steps:** Login; decode JWT.
**Expected:** `privs` contains both privilege codes; nothing extra.

### TC-AUTH-015 — @PreAuthorize blocks unprivileged calls [P1]
**Steps:** As a user without `ITEM.CREATE`, POST /api/v1/items.
**Expected:** 403.

### TC-AUTH-016 — Supervisor PIN flow issues short-lived authorisation [P2]
**Stories:** US-IAM-010
**Steps:** POST /api/v1/auth/supervisor-pin with cashier JWT + supervisor PIN; receive an `oversell_token`; use it on a sale.
**Expected:** Token expires within configured window (e.g. 5 minutes); single-use; tied to the cashier's session.

## Edge

### TC-AUTH-017 — Same user, multiple devices [P2]
**Steps:** Login from device A, get JWT A. Login from device B, get JWT B.
**Expected:** Both tokens valid until their `exp`. Deactivating the user invalidates both immediately (filter checks `app_user.status`).

### TC-AUTH-018 — Concurrent failed-login races [P2]
**Steps:** 5 wrong-password attempts concurrently.
**Expected:** `failed_login_count` reaches exactly 5 (atomically); only one lockout transition fires.

### TC-AUTH-019 — Username case sensitivity [P2]
**Steps:** Login with `Admin` vs `admin`.
**Expected:** **Convention:** usernames are case-insensitive (stored lowercase). Both succeed.

### TC-AUTH-020 — Password change clears refresh tokens [P2] *(when refresh wired)*
**Steps:** Change password. Re-use an old refresh token.
**Expected:** 401. All existing refresh tokens for the user are revoked on password change.
