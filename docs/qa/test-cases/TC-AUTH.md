# TC-AUTH — Authentication & IAM Test Cases

**Module:** auth + iam  
**Stories:** US-IAM-001 through US-IAM-014  
**API base:** `http://localhost:8081/api/v1`

---

## Schema reminder

| ID | Title | Area | Dim | Pri | US-* | Preconditions | Steps | Expected Result | Auto? | Result | Notes |
|----|-------|------|-----|-----|------|---------------|-------|-----------------|-------|--------|-------|

---

## TC-AUTH-001 — Login with valid credentials returns JWT pair

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-001 |
| **Title** | Login with valid credentials returns JWT access + refresh token pair |
| **Area** | auth |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-001 |
| **Preconditions** | 1. QA container running. 2. User `rootadmin` exists with correct password. |
| **Steps** | 1. `POST /api/v1/auth/login` body: `{"username":"rootadmin","password":"SKp315goPN8Nb0yJtMCCD7cm"}` 2. Decode the returned `accessToken` JWT. |
| **Expected Result** | HTTP 200; response has `data.accessToken` (non-null, valid JWT); `data.refreshToken` (non-null); JWT `exp - iat <= 900` (15 min); JWT claims contain `sub=rootadmin`, `perms` array non-empty; `audit_log` has LOGIN row with correct actor. |
| **Automatable?** | yes — JUnit integration test (`AuthServiceImplTest`) + Playwright login helper |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## TC-AUTH-002 — Login with wrong password returns generic error and increments failure count

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-002 |
| **Title** | Wrong password returns 401 with generic message; failure count increments |
| **Area** | auth |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-001 |
| **Preconditions** | 1. QA container running. 2. `rootadmin` exists, `failed_login_count = 0`. |
| **Steps** | 1. `POST /api/v1/auth/login` body: `{"username":"rootadmin","password":"WRONG"}` 2. Inspect `app_user.failed_login_count` in DB. 3. Inspect response body message. |
| **Expected Result** | HTTP 401; response message is a generic "invalid credentials" string — does NOT say "no such user"; `app_user.failed_login_count` = 1; `app_user.last_failed_login_at` updated. |
| **Automatable?** | yes — JUnit unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## TC-AUTH-003 — Non-existent username returns same error as wrong password (timing-safe)

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-003 |
| **Title** | Non-existent username returns identical error shape as wrong password |
| **Area** | auth |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-001 |
| **Preconditions** | QA container running. |
| **Steps** | 1. `POST /api/v1/auth/login` body: `{"username":"definitely_does_not_exist","password":"anything"}` 2. Compare HTTP status, body, and approximate response time (< 2x delta vs wrong password for real user). |
| **Expected Result** | HTTP 401; response body identical structure to TC-AUTH-002; no "user not found" hint; timing comparable to real-user wrong-password path (dummy bcrypt hash verification is performed — see `AuthServiceImpl.dummyHash`). |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## TC-AUTH-004 — Account locks after 5 consecutive failed logins

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-004 |
| **Title** | Account locks after 5 failed attempts; 6th attempt returns lockout message |
| **Area** | auth |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-001 |
| **Preconditions** | 1. QA container running. 2. Test user created with known password. 3. `failed_login_count = 0`. |
| **Steps** | 1. POST /auth/login with wrong password × 5. 2. POST /auth/login with CORRECT password on attempt 6. 3. Check `app_user.locked_until`. |
| **Expected Result** | Attempts 1–5: HTTP 401 generic. Attempt 6 (correct password): HTTP 401/423 with message indicating account is locked; `locked_until` is set to `now + 1 minute` (first lockout, exponential backoff base). |
| **Automatable?** | yes — unit test (`AuthServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | `AuthServiceImpl.LOCKOUT_THRESHOLD=5`, `LOCKOUT_BASE=1m` |

---

## TC-AUTH-005 — Refresh token issues new access token and rotates

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-005 |
| **Title** | Valid refresh token returns new access + new refresh; old refresh is revoked |
| **Area** | auth |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-001 US-IAM-002 |
| **Preconditions** | 1. Login → obtain refreshToken A and accessToken A. |
| **Steps** | 1. `POST /api/v1/auth/refresh` body: `{"refreshToken":"<A>"}`. 2. Use refreshToken A again. |
| **Expected Result** | Step 1: HTTP 200, new `accessToken` B, new `refreshToken` B. Step 2: HTTP 401 — old refresh token A is revoked (single-use). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-IAM-001 AC: "single-use, rotated on next use" |

---

## TC-AUTH-006 — Expired access token rejected; refresh succeeds

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-006 |
| **Title** | Expired access JWT is rejected 401; fresh token from refresh works |
| **Area** | auth |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-001 |
| **Preconditions** | Access token TTL configured to 15 min; clock or token `exp` manipulated for test. |
| **Steps** | 1. Craft a JWT with `exp = now - 1s` (test utility or by waiting > TTL). 2. `GET /api/v1/items` with that token. 3. Use valid refresh token to get new access; retry step 2. |
| **Expected Result** | Step 2: HTTP 401. Step 3 retry: HTTP 200. |
| **Automatable?** | yes — unit test on JwtServiceImpl |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## TC-AUTH-007 — Logout invalidates refresh token; access token blacklisted

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-007 |
| **Title** | POST /auth/logout removes refresh token; subsequent refresh fails |
| **Area** | auth |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-002 |
| **Preconditions** | Valid session (accessToken + refreshToken) in hand. |
| **Steps** | 1. `POST /api/v1/auth/logout` with `{"refreshToken":"<token>"}` and Bearer access token. 2. Attempt `POST /api/v1/auth/refresh` with the same refresh token. 3. Check `audit_log` for LOGOUT row. |
| **Expected Result** | Step 1: HTTP 200. Step 2: HTTP 401. Step 3: LOGOUT row present with actor, IP, timestamp. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## TC-AUTH-008 — Unauthenticated request to protected endpoint returns 401

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-008 |
| **Title** | Request without Authorization header to any protected endpoint returns 401 |
| **Area** | auth |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-001 |
| **Preconditions** | QA container running. |
| **Steps** | 1. `GET /api/v1/items` with NO Authorization header. 2. `GET /api/v1/business-days?branchId=1` with NO Authorization header. |
| **Expected Result** | Both: HTTP 401; response is `ApiResponse` envelope with `status = "error"`; no data leaked. |
| **Automatable?** | yes — unit/integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## TC-AUTH-009 — Permission-gated endpoint returns 403 for user without permission

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-009 |
| **Title** | User without ITEM.CREATE permission cannot POST /items |
| **Area** | auth / iam |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-008 US-IAM-009 |
| **Preconditions** | 1. Create a user `noperm` with role that has NO ITEM.CREATE permission. 2. Login as `noperm`; obtain token. |
| **Steps** | 1. `POST /api/v1/items` with `noperm` token, valid item payload. 2. Login as `rootadmin`; same POST. |
| **Expected Result** | Step 1: HTTP 403. Step 2: HTTP 201. |
| **Automatable?** | yes — integration test with Flyway-seeded permissions |
| **Result/Status** | |
| **Notes/IssueRef** | Permission code: `ITEM.CREATE` (seeded in Flyway migration) |

---

## TC-AUTH-010 — Role assignment scoped to branch — cross-branch access denied

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-010 |
| **Title** | User with role scoped to branch 1 cannot post to branch 2 resources |
| **Area** | iam |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-009 |
| **Preconditions** | 1. Two branches exist (HQ id=1, Branch2 id=2). 2. User `branchonly` has role scoped to branch 1 only. 3. Login as `branchonly`. |
| **Steps** | 1. `POST /api/v1/pos-sales` with header `X-Branch-Id: 2`. 2. Same request with `X-Branch-Id: 1`. |
| **Expected Result** | Step 1: HTTP 403 (branch scope mismatch). Step 2: depends on other preconditions but not a 403 from scope check. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Multi-tenant isolation — Critical if broken |

---

## TC-AUTH-011 — JWT carries correct perms claim; stale perms not honoured

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-011 |
| **Title** | JWT perms claim reflects permissions at login time; removing permission requires new token |
| **Area** | auth / iam |
| **Dimension** | SEC |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-008 |
| **Preconditions** | User `permtest` has permission P. Login → accessToken1. Remove P from user's role. |
| **Steps** | 1. Use `accessToken1` (still valid, < 15 min old) to exercise endpoint requiring P. 2. Refresh → get `accessToken2`. Use `accessToken2` for same endpoint. |
| **Expected Result** | Step 1: HTTP 200 (token carries stale perms — by design, access token TTL is 15 min). Step 2: HTTP 403 (new token no longer has P). This documents the intentional design: max stale window = access TTL. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Expected by design; document if TTL > 15 min |

---

## TC-AUTH-012 — Create user; user must change password on first login

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-012 |
| **Title** | Admin creates user; user receives must-change-password flag on first login |
| **Area** | iam |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-006 |
| **Preconditions** | Logged in as rootadmin. |
| **Steps** | 1. `POST /api/v1/users` body: `{"username":"newuser123","displayName":"New User","defaultBranchId":1,"password":"TempPass#99","mustChangePassword":true}`. 2. Login as `newuser123`. Inspect login response. |
| **Expected Result** | Step 1: HTTP 201. Step 2: HTTP 200; response contains `mustChangePassword: true` (or equivalent flag); user can obtain token but should be prompted to change password. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-IAM-006 AC |

---

## TC-AUTH-013 — Deactivated user cannot log in

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-013 |
| **Title** | Setting user status INACTIVE blocks login; existing session invalidated on next call |
| **Area** | iam |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-007 |
| **Preconditions** | 1. User `active_user` exists. 2. Login → obtain `accessToken`. |
| **Steps** | 1. Admin: `PATCH /api/v1/users/uid/{uid}/status` body `{"status":"INACTIVE"}`. 2. `POST /api/v1/auth/login` as `active_user`. 3. `GET /api/v1/items` using existing `accessToken` (within TTL). |
| **Expected Result** | Step 2: HTTP 401. Step 3: HTTP 401 (status checked on each request or token is blacklisted). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-IAM-007 AC: "immediately invalidates active sessions on next API call" |

---

## TC-AUTH-014 — Audit log records LOGIN and LOGOUT with IP and client

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-014 |
| **Title** | Login and logout events appear in audit_log with actor, IP, client metadata |
| **Area** | iam |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-001 US-IAM-002 US-IAM-013 |
| **Preconditions** | QA container running. |
| **Steps** | 1. Login as rootadmin. 2. Logout. 3. `GET /api/v1/audit?actor=rootadmin&action=LOGIN&pageSize=5`. |
| **Expected Result** | LOGIN row: `action=LOGIN`, `actor_id` = rootadmin's id, `meta` JSON contains `ip` and `client`. LOGOUT row: `action=LOGOUT`, same actor. Both rows present. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## TC-AUTH-015 — X-Branch-Id header validated against user's branch grants

| Field | Value |
|-------|-------|
| **ID** | TC-AUTH-015 |
| **Title** | Spoofed X-Branch-Id for a branch the user has no grant for is rejected 403 |
| **Area** | auth / iam |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-009 US-COMP-005 |
| **Preconditions** | 1. User `cashier` has grant only for branch id=1. 2. Login as cashier; obtain token. |
| **Steps** | 1. `GET /api/v1/sync/pull?datasets=catalog` with `X-Branch-Id: 999`. |
| **Expected Result** | HTTP 403; body message references branch access denied. Branch 999 data is not returned. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Critical security control — multi-tenant isolation |
