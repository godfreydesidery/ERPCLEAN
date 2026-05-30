# TC-RBAC — Role / Permission Administration & Enforcement Test Cases

**Module:** iam (RoleAdminController) + auth (sessions / logout-everywhere) + common.security (RequestContext, BranchAccessGuard, TokenGuard)
**Stories:** US-IAM-003, US-IAM-008, US-IAM-009
**API base:** `http://localhost:8081/api/v1`
**Priority rationale:** RBAC is a security boundary — a role that over-grants, a branch-scoped grant that leaks cross-branch, a revoke that doesn't bite, or a logout-everywhere that leaves a live token = pilot-blocking. Most cases here are **P0**.

---

## Schema reminder

| ID | Title | Area | Dim | Pri | US-* | Preconditions | Steps | Expected Result | Auto? | Result | Notes |
|----|-------|------|-----|-----|------|---------------|-------|-----------------|-------|--------|-------|

---

## Grounding notes (read before executing)

- **Every** `/roles`, `/roles/uid/{uid}/**`, `/grants/**`, and `/permissions` endpoint is gated by the **class-level** `@PreAuthorize("hasAuthority('IAM.MANAGE_ROLES')")` on `RoleAdminController`. There is no per-method relaxation. A caller without `IAM.MANAGE_ROLES` gets **403** on all of them.
- Permission seed (`V4__seed_permissions_and_admin_role.sql`): `IAM.MANAGE_USERS`=id 1, `IAM.MANAGE_ROLES`=id 2, `IAM.VIEW_AUDIT`=id 3, `ADMIN.MANAGE_BRANCHES`=id 4 … `ITEM.CREATE`=id 8, `ITEM.UPDATE`=id 9, `ITEM.ARCHIVE`=id 10. Resolve the live id set with `GET /api/v1/permissions` before building payloads — later Flyway seeds (V8…V80) add more.
- Error mapping (`GlobalExceptionHandler`): bean-validation on `@RequestBody` → **422 `VALIDATION_FAILED`**; bad `@ValidUlid` path var → **400 `BAD_REQUEST`**; `IllegalArgumentException` (dup code, invalid perm id, system-role mutation, branch-not-in-company, already-granted, delete-with-grants) → **400 `BAD_REQUEST`**; `NoSuchElementException` (role/grant/user missing) → **404 `NOT_FOUND`**; `AccessDeniedException` (rootadmin guard / branch guard) → **403 `FORBIDDEN`**.
- Roles/grants are addressed by **uid** (Crockford ULID). `id` appears in response bodies and serialises as a JSON string (JSON:API discipline).
- **Interference discipline:** create roles with unique codes (prefix `RBAC` + entropy, e.g. `RBAC_QA_7F3K`). Grant only to throwaway test users you created. Never touch the `ADMIN` system role, `rootadmin`, the seeded `cashier`, the shared business day, or seeded catalog/customer.

---

## TC-RBAC-001 — Create a custom role, assign a single permission, verify the role grants ONLY that permission

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-001 |
| **Title** | Custom role with exactly one permission grants that permission and nothing else |
| **Area** | iam |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-008 |
| **Endpoint** | `POST /roles`, `PUT /roles/uid/{uid}/permissions`, `POST /roles/uid/{uid}/grants` |
| **Preconditions** | 1. Logged in as `rootadmin` (has `IAM.MANAGE_ROLES` + `IAM.MANAGE_USERS`). 2. A throwaway user `rbac_u1_<entropy>` created via `POST /users` with a known password (branch HQ id=1). 3. `GET /permissions` resolved — note id of `ITEM.CREATE` (seed id 8). |
| **Steps** | 1. `POST /roles` body `{"code":"RBAC_ITEMC_<entropy>","name":"RBAC item-create only","description":"qa"}` → capture role `uid`. 2. `PUT /roles/uid/{uid}/permissions` body `{"permissionIds":["8"]}` (ITEM.CREATE only). 3. `POST /roles/uid/{uid}/grants` body `{"username":"rbac_u1_<entropy>","branchId":1}`. 4. Login as `rbac_u1_<entropy>` → get accessToken. Decode JWT. 5. With that token: `POST /api/v1/items` (valid item, unique code) and `POST /api/v1/customers` (valid party). |
| **Expected Result** | Step 1: 201, role detail returned with empty `permissions`. Step 2: 200, `permissions` contains exactly `[{code:"ITEM.CREATE"...}]`. Step 3: 201, grant returned with `branchId:"1"`. Step 4: JWT `perms` claim = exactly `["ITEM.CREATE"]` — no other codes. Step 5: `POST /items` → 201; `POST /customers` → **403** (no `CUSTOMER.CREATE`). Proves least-privilege: the role grants only what was assigned. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Core least-privilege assertion. concurrencyRisk=none (own user/role). |

---

## TC-RBAC-002 — List permissions returns the seeded catalogue

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-002 |
| **Title** | GET /permissions returns the full seeded permission set, sorted by module then code |
| **Area** | iam |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-008 |
| **Endpoint** | `GET /permissions` |
| **Preconditions** | Logged in as `rootadmin`. |
| **Steps** | 1. `GET /api/v1/permissions`. |
| **Expected Result** | 200; array contains the canonical codes from `Permissions.java` incl. `IAM.MANAGE_USERS`, `IAM.MANAGE_ROLES`, `IAM.VIEW_AUDIT`, `ITEM.CREATE`; each item has `id` (string), `code`, `description`, `module`; ordered by `module` then `code`. Every `code` shown is one the `ADMIN` system role holds (parity check vs `role_permission`). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Picker source for role-edit screen. |

---

## TC-RBAC-003 — Reassigning permissions REPLACES the full set (PUT semantics, not merge)

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-003 |
| **Title** | PUT /permissions replaces the role's entire permission set |
| **Area** | iam |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-008 |
| **Endpoint** | `PUT /roles/uid/{uid}/permissions` |
| **Preconditions** | A custom role `RBAC_REPL_<entropy>` exists with permissions `[ITEM.CREATE id8, ITEM.UPDATE id9]`. |
| **Steps** | 1. `PUT /roles/uid/{uid}/permissions` body `{"permissionIds":["10"]}` (ITEM.ARCHIVE only). 2. `GET /roles/uid/{uid}`. |
| **Expected Result** | Step 1: 200. Step 2: `permissions` = exactly `[ITEM.ARCHIVE]` — the previous `ITEM.CREATE`/`ITEM.UPDATE` are gone (replace, not append). `role_permission` rows for the dropped perms are deleted. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `replacePermissions` is full-set replace — guards against accidental privilege accrual. |

---

## TC-RBAC-004 — Empty permission set is valid (revoke all permissions from a role)

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-004 |
| **Title** | PUT with empty permissionIds strips all permissions; role grants nothing |
| **Area** | iam |
| **Dimension** | DATA |
| **Priority** | P2 |
| **Linked US-*** | US-IAM-008 |
| **Endpoint** | `PUT /roles/uid/{uid}/permissions` |
| **Preconditions** | A custom role `RBAC_EMPTY_<entropy>` with at least one permission, granted to throwaway user `rbac_u4_<entropy>`. |
| **Steps** | 1. `PUT /roles/uid/{uid}/permissions` body `{"permissionIds":[]}`. 2. `GET /roles/uid/{uid}`. 3. Re-login as `rbac_u4_<entropy>`; decode JWT. |
| **Expected Result** | Step 1: 200 (empty list is `@NotNull` but may be empty). Step 2: `permissions` = `[]`. Step 3: JWT `perms` claim empty for that role's contribution — user can authenticate but is authorized for nothing gated. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `@NotNull` rejects missing field but allows empty list. |

---

## TC-RBAC-005 — Caller WITHOUT IAM.MANAGE_ROLES is denied every role-admin endpoint (403)

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-005 |
| **Title** | A user lacking IAM.MANAGE_ROLES gets 403 on all /roles, /permissions, /grants endpoints |
| **Area** | iam |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-008, US-IAM-009 |
| **Endpoint** | all of `RoleAdminController` |
| **Preconditions** | 1. Login as seeded `cashier` (role POS_CASHIER, no `IAM.MANAGE_ROLES`). |
| **Steps** | 1. `GET /api/v1/permissions`. 2. `GET /api/v1/roles`. 3. `POST /api/v1/roles` body `{"code":"RBAC_HACK_<entropy>","name":"x"}`. 4. `PUT /api/v1/roles/uid/01ARZ3NDEKTSV4RRFFQ69G5FAV/permissions` body `{"permissionIds":["1"]}` (ADMIN role uid). 5. `POST /api/v1/roles/uid/<any>/grants` body `{"username":"cashier","branchId":1}`. |
| **Expected Result** | All steps: **403 `FORBIDDEN`**, `ApiResponse` envelope `message:"Access denied"`. No role created, no permission changed. Self-escalation via the role API is impossible without the gate permission. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Privilege-escalation guard. Critical if any returns 2xx. |

---

## TC-RBAC-006 — Branch-scoped grant: user can act in their branch, denied outside it (multi-tenant isolation)

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-006 |
| **Title** | User granted a role scoped to branch 1 is rejected (403) when overriding X-Branch-Id to a branch with no grant |
| **Area** | iam / common.security |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-009 |
| **Endpoint** | `POST /roles/uid/{uid}/grants` + `JwtAuthenticationFilter` / `BranchAccessGuard` |
| **Preconditions** | 1. Two branches in the company: HQ id=1, and a second branch id=2 (resolve via `GET /api/v1/branches`; if only HQ exists, mark BLOCKED(needs-second-branch)). 2. A custom role `RBAC_BR_<entropy>` with `ITEM.CREATE`. 3. Throwaway user `rbac_b6_<entropy>` whose default branch is 1. 4. Grant the role to that user with `branchId:1` (branch-scoped, NOT company-wide). 5. Login as that user → token (JWT `branchId`=1). |
| **Steps** | 1. `POST /api/v1/items` with header `X-Branch-Id: 1` (matches grant). 2. `POST /api/v1/items` with header `X-Branch-Id: 2` (no grant for branch 2). |
| **Expected Result** | Step 1: 201 (within granted branch). Step 2: **403 `FORBIDDEN`** — `BranchAccessGuard.verify` finds no active grant covering branch 2; message references "no role grant for branch 2". The override is rejected in the JWT filter before the controller runs. Proves tenant isolation actually bites. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Hard multi-tenant boundary. concurrencyRisk=none. If only one branch exists: BLOCKED(needs-second-branch). |

---

## TC-RBAC-007 — Branch-scoped grant does NOT confer permissions when acting in a different branch

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-007 |
| **Title** | Permission resolution excludes branch-1-scoped grants when the request targets branch 2 |
| **Area** | iam / common.security |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-009 |
| **Endpoint** | `findActivePermissionCodes` via login token mint + branch override |
| **Preconditions** | 1. Branches 1 and 2 exist. 2. User `rbac_b7_<entropy>` has TWO grants: role A (`ITEM.CREATE`) scoped to branch 1, and role B (`STOCK.COUNT`) company-wide (branchId null). 3. Login token issued at default branch 1. |
| **Steps** | 1. With `X-Branch-Id: 2`: `POST /api/v1/items` (needs ITEM.CREATE, only granted for branch 1). 2. With `X-Branch-Id: 2`: call a STOCK.COUNT-gated endpoint (company-wide grant). |
| **Expected Result** | Step 1: **403** — branch-1-scoped ITEM.CREATE does not apply in branch 2 (token still carries the login-time perms, but a fresh login while bound to branch 2 would resolve perms excluding the branch-1 grant; if testing token-level, refresh after switching). Step 2: succeeds — the company-wide grant covers every branch. Documents the per-branch scoping of `findActivePermissionCodes` (`ur.branchId is null or ur.branchId = :branchId`). |
| **Automatable?** | yes — integration test on `PermissionResolverServiceImpl` |
| **Result/Status** | |
| **Notes/IssueRef** | Note design: access-token perms are computed at login per default branch; this case is cleanest tested at the resolver level. Cross-ref TC-AUTH-011 (stale-perms window). |

---

## TC-RBAC-008 — Revoking a grant invalidates the user's live tokens immediately

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-008 |
| **Title** | DELETE /grants/uid/{grantUid} revokes the grant AND forces the user's existing access token to fail |
| **Area** | iam / common.security |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-009 |
| **Endpoint** | `DELETE /grants/uid/{grantUid}` + `TokenGuard.invalidateUserTokens` |
| **Preconditions** | 1. Custom role `RBAC_REVK_<entropy>` with `ITEM.CREATE`, granted to throwaway user `rbac_r8_<entropy>`. 2. Login as that user → accessTokenA (within 15-min TTL). 3. Confirm `POST /items` with accessTokenA → 201. 4. As rootadmin, `GET /roles/uid/{uid}/grants` → capture the grant `uid`. |
| **Steps** | 1. As rootadmin: `DELETE /api/v1/grants/uid/{grantUid}`. 2. Immediately re-use accessTokenA: `POST /api/v1/items` (unique code). 3. The user logs in afresh; retry `POST /items`. |
| **Expected Result** | Step 1: 204. Step 2: **401** ("Session revoked") — `invalidateUserTokens` sets an invalidation cutoff `>=` the token's `iat`, so the still-unexpired token is rejected on its next call (not left valid until 15-min expiry). Step 3: **403** (the re-minted token no longer carries `ITEM.CREATE`). Revocation takes effect now, not at TTL. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Immediate revocation is the whole point of `TokenGuard`. concurrencyRisk=none. |

---

## TC-RBAC-009 — Cannot delete a role that still has active grants

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-009 |
| **Title** | DELETE /roles/uid/{uid} is rejected (400) while the role has any non-revoked grant |
| **Area** | iam |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-008 |
| **Endpoint** | `DELETE /roles/uid/{uid}` |
| **Preconditions** | Custom role `RBAC_DEL_<entropy>` granted to throwaway user `rbac_d9_<entropy>` (active grant exists). |
| **Steps** | 1. `DELETE /api/v1/roles/uid/{uid}`. 2. Revoke the grant (`DELETE /grants/uid/{grantUid}`). 3. `DELETE /api/v1/roles/uid/{uid}` again. |
| **Expected Result** | Step 1: **400 `BAD_REQUEST`** message "Role still has active grants — revoke them first"; role NOT deleted. Step 3: 204 — deletion succeeds once grants are revoked. Prevents orphaning a granted role. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Referential-integrity guard in `deleteRoleByUid`. |

---

## TC-RBAC-010 — System role (ADMIN) cannot be modified, repermissioned, or deleted

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-010 |
| **Title** | Mutating the ADMIN system role is rejected (400) on update, set-permissions, and delete |
| **Area** | iam |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-008 |
| **Endpoint** | `PATCH /roles/uid/{uid}`, `PUT /roles/uid/{uid}/permissions`, `DELETE /roles/uid/{uid}` |
| **Preconditions** | Logged in as `rootadmin`. ADMIN role uid is the seeded `01ARZ3NDEKTSV4RRFFQ69G5FAV` (confirm via `GET /roles`). |
| **Steps** | 1. `PATCH /roles/uid/01ARZ3NDEKTSV4RRFFQ69G5FAV` body `{"name":"hacked"}`. 2. `PUT /roles/uid/01ARZ3NDEKTSV4RRFFQ69G5FAV/permissions` body `{"permissionIds":["8"]}` (would strip ADMIN to one perm). 3. `DELETE /roles/uid/01ARZ3NDEKTSV4RRFFQ69G5FAV`. |
| **Expected Result** | All three: **400 `BAD_REQUEST`** "System role 'ADMIN' cannot be modified". ADMIN keeps its full permission set; no admin lock-out possible by stripping/deleting the system role. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `requireMutableRoleByUid` blocks `isSystem` roles. Lock-out prevention. concurrencyRisk=none (shared ADMIN role is read-only; no state changes). |

---

## TC-RBAC-011 — rootadmin account cannot be granted extra roles or stripped of its grant

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-011 |
| **Title** | Granting a role to rootadmin, and revoking rootadmin's grant, are both rejected (403) |
| **Area** | iam |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-009 |
| **Endpoint** | `POST /roles/uid/{uid}/grants`, `DELETE /grants/uid/{grantUid}` |
| **Preconditions** | Custom role `RBAC_ROOT_<entropy>` exists. Logged in as `rootadmin`. |
| **Steps** | 1. `POST /roles/uid/{uid}/grants` body `{"username":"rootadmin","branchId":1}`. 2. (If a rootadmin grant uid is discoverable) attempt `DELETE /grants/uid/{rootadminGrantUid}`. |
| **Expected Result** | Step 1: **403 `FORBIDDEN`** "The rootadmin account is protected and cannot be granted roles" (`RootAdminGuard.assertMutable`). Step 2: **403** "...cannot be stripped of its roles". The break-glass account's company-wide ADMIN access can't be altered or removed via the API. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Protects the break-glass account from privilege tampering. |

---

## TC-RBAC-012 — Set-permissions rejects an unknown permission id (no partial apply)

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-012 |
| **Title** | PUT /permissions with an invalid permission id is rejected 400 and changes nothing |
| **Area** | iam |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-008 |
| **Endpoint** | `PUT /roles/uid/{uid}/permissions` |
| **Preconditions** | Custom role `RBAC_BADP_<entropy>` with permission `[ITEM.CREATE id8]`. |
| **Steps** | 1. `PUT /roles/uid/{uid}/permissions` body `{"permissionIds":["8","999999"]}` (999999 does not exist). 2. `GET /roles/uid/{uid}`. |
| **Expected Result** | Step 1: **400 `BAD_REQUEST`** "One or more permission ids are invalid". Step 2: `permissions` unchanged = `[ITEM.CREATE]` — the whole PUT is rejected atomically, no partial grant of the valid id. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `resolved.size() != distinct(ids).size()` check; transactional rollback. |

---

## TC-RBAC-013 — Duplicate role code rejected; grant to non-existent user 404; grant to branch outside company 400

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-013 |
| **Title** | Role-admin input validation: duplicate code (400), unknown grantee (404), foreign branch (400) |
| **Area** | iam |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-008, US-IAM-009 |
| **Endpoint** | `POST /roles`, `POST /roles/uid/{uid}/grants` |
| **Preconditions** | A role with code `RBAC_DUP_<entropy>` already created. A custom role `RBAC_GV_<entropy>` exists for the grant attempts. |
| **Steps** | 1. `POST /roles` body `{"code":"rbac_dup_<entropy>","name":"dup"}` (same code, lower-case — codes are upper-cased before the uniqueness check). 2. `POST /roles/uid/{rbac_gv_uid}/grants` body `{"username":"definitely_no_such_user","branchId":1}`. 3. `POST /roles/uid/{rbac_gv_uid}/grants` body `{"username":"rootadmin","branchId":999999}` — but use a real test user, not rootadmin, with branch 999999. |
| **Expected Result** | Step 1: **400 `BAD_REQUEST`** "Role code already exists: RBAC_DUP_<entropy>" (case-insensitive collision). Step 2: **404 `NOT_FOUND`** "User not found: definitely_no_such_user". Step 3: **400 `BAD_REQUEST`** "Branch 999999 is not in your company" — a bad branchId is rejected cleanly, not as a 500 FK error. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Branch-scope validation is defense-in-depth against FK-500 leakage. |

---

## TC-RBAC-014 — Duplicate grant in the same scope is rejected (idempotent guard)

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-014 |
| **Title** | Granting the same role to the same user+branch twice is rejected 400 |
| **Area** | iam |
| **Dimension** | DATA |
| **Priority** | P2 |
| **Linked US-*** | US-IAM-009 |
| **Endpoint** | `POST /roles/uid/{uid}/grants` |
| **Preconditions** | Custom role `RBAC_DG_<entropy>`; throwaway user `rbac_d14_<entropy>`. |
| **Steps** | 1. `POST /roles/uid/{uid}/grants` body `{"username":"rbac_d14_<entropy>","branchId":1}`. 2. Repeat the exact same POST. 3. `POST /roles/uid/{uid}/grants` body `{"username":"rbac_d14_<entropy>","branchId":null}` (company-wide — different scope). |
| **Expected Result** | Step 1: 201. Step 2: **400 `BAD_REQUEST`** "User already has role '...' in this scope" — no duplicate `user_role` row. Step 3: 201 — same role at a different scope (company-wide vs branch 1) is a distinct, allowed grant. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Scope = (roleId, branchId). Prevents duplicate active grants. |

---

## TC-RBAC-015 — Bad-shape requests: malformed uid (400), missing required body field (422)

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-015 |
| **Title** | Invalid ULID path → 400; missing NotBlank/NotNull body field → 422 |
| **Area** | iam |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-IAM-008 |
| **Endpoint** | `GET /roles/uid/{uid}`, `POST /roles`, `PUT /roles/uid/{uid}/permissions` |
| **Preconditions** | Logged in as `rootadmin`. A valid custom role uid in hand for the PUT case. |
| **Steps** | 1. `GET /api/v1/roles/uid/not-a-ulid`. 2. `POST /api/v1/roles` body `{"name":"no code"}` (missing `code`, which is `@NotBlank`). 3. `PUT /api/v1/roles/uid/{validUid}/permissions` body `{}` (missing `permissionIds`, which is `@NotNull`). |
| **Expected Result** | Step 1: **400 `BAD_REQUEST`** "Invalid request parameter" (ValidUlid constraint violation). Step 2: **422 `VALIDATION_FAILED`** with field error on `code`. Step 3: **422 `VALIDATION_FAILED`** with field error on `permissionIds`. No internal exception text leaks into `errors[]`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Path-var vs body-validation status split per `GlobalExceptionHandler`. |

---

## TC-RBAC-016 — logout-everywhere revokes all sessions and invalidates every live access token

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-016 |
| **Title** | POST /auth/logout-everywhere kills all refresh + access tokens for the caller; /sessions returns none |
| **Area** | auth |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-003 |
| **Endpoint** | `GET /auth/sessions`, `POST /auth/logout-everywhere` |
| **Preconditions** | A throwaway user `rbac_le16_<entropy>` (any role). Log in TWICE from two clients (distinct `X-Client-Version`) → sessions S1 (accessA/refreshA) and S2 (accessB/refreshB). |
| **Steps** | 1. With accessA: `GET /api/v1/auth/sessions` → expect ≥2 active sessions listed (metadata only, no raw token). 2. With accessA: `POST /api/v1/auth/logout-everywhere`. 3. Re-use accessB (the OTHER session's still-unexpired token): `GET /api/v1/items`. 4. Attempt `POST /api/v1/auth/refresh` body `{"refreshToken":"<refreshB>"}`. 5. With accessA: `GET /api/v1/auth/sessions`. |
| **Expected Result** | Step 1: 200, list of `SessionDto{id,clientInstallId,issuedAt,expiresAt}` — raw token never exposed. Step 2: 204. Step 3: **401** "Session revoked" — `invalidateUserTokens` cutoff invalidates ALL access tokens for the user, including the other client's. Step 4: **401** — refreshB revoked. Step 5: 200 with an empty list (all sessions gone). Audit row `LOGOUT_EVERYWHERE`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-IAM-003 "log out everywhere". concurrencyRisk=none (own user). |

---

## TC-RBAC-017 — sessions / logout-everywhere are scoped to the caller (cannot reach another user)

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-017 |
| **Title** | /auth/sessions and /auth/logout-everywhere operate only on the authenticated principal |
| **Area** | auth |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-003 |
| **Endpoint** | `GET /auth/sessions`, `POST /auth/logout-everywhere` |
| **Preconditions** | Two throwaway users `rbac_a17_<entropy>` and `rbac_b17_<entropy>`, each logged in (token A, token B). |
| **Steps** | 1. With token A: `POST /api/v1/auth/logout-everywhere`. 2. With token B: `GET /api/v1/items` and `GET /api/v1/auth/sessions`. 3. Unauthenticated (no Authorization header): `GET /api/v1/auth/sessions` and `POST /api/v1/auth/logout-everywhere`. |
| **Expected Result** | Step 1: 204 (kills user A's sessions only). Step 2: token B still works (200) and user B still has its session — there is no body/param to target another user; the endpoint derives `userId` solely from the SecurityContext principal, so A cannot log B out. Step 3: **401** for both (no principal → endpoint returns UNAUTHORIZED). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | No user-id parameter exists on these endpoints — principal-only by design. Cross-user logout is `IAM.MANAGE_USERS`-gated `POST /users/uid/{uid}/force-logout` instead. |

---

## TC-RBAC-018 — Self-service password change requires the correct current password and rotates the hash

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-018 |
| **Title** | POST /users/me/change-password rejects wrong current password; succeeds with correct one |
| **Area** | iam |
| **Dimension** | SEC |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-008 |
| **Endpoint** | `POST /users/me/change-password` |
| **Preconditions** | Throwaway user `rbac_pw18_<entropy>` with known password `OldPass#2026`. Logged in as that user. |
| **Steps** | 1. `POST /api/v1/users/me/change-password` body `{"currentPassword":"WRONG","newPassword":"NewPass#2026X"}`. 2. Same with `{"currentPassword":"OldPass#2026","newPassword":"short"}` (8 chars < min 10). 3. Same with `{"currentPassword":"OldPass#2026","newPassword":"NewPass#2026X"}`. 4. Re-login with the OLD password, then the NEW password. |
| **Expected Result** | Step 1: 4xx (current-password mismatch — no change). Step 2: **422 `VALIDATION_FAILED`** on `newPassword` (min 10). Step 3: 204 — password rotated. Step 4: old password → 401; new password → 200. This endpoint is NOT gated by `IAM.MANAGE_USERS` (any authenticated user changes their own). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Self-service is open to all authenticated; admin reset is `IAM.MANAGE_USERS`-gated (`POST /users/uid/{uid}/reset-password`). |

---

## TC-RBAC-019 — Admin password reset is IAM.MANAGE_USERS-gated and protected against targeting rootadmin

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-019 |
| **Title** | POST /users/uid/{uid}/reset-password requires IAM.MANAGE_USERS; rootadmin cannot be reset via this path |
| **Area** | iam |
| **Dimension** | SEC |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-008 |
| **Endpoint** | `POST /users/uid/{uid}/reset-password` |
| **Preconditions** | 1. A non-admin user `rbac_np19_<entropy>` without `IAM.MANAGE_USERS`, logged in (tokenNP). 2. A throwaway user `rbac_t19_<entropy>` (uid T). 3. rootadmin uid known (resolve via `GET /users?q=rootadmin` as admin). |
| **Steps** | 1. With tokenNP: `POST /api/v1/users/uid/{T}/reset-password` body `{"mustChangePassword":true}`. 2. As rootadmin: `POST /api/v1/users/uid/{T}/reset-password` body `{"mustChangePassword":true}`. 3. As rootadmin: `POST /api/v1/users/uid/{rootadminUid}/reset-password` body `{"mustChangePassword":true}`. |
| **Expected Result** | Step 1: **403 `FORBIDDEN`** (no `IAM.MANAGE_USERS`). Step 2: 200 — returns a one-time temp password; `mustChangePassword=true` set. Step 3: **403 `FORBIDDEN`** — `RootAdminGuard` blocks resetting rootadmin via the user API (rootadmin password is env/token-gated only, see `POST /setup/reset-rootadmin-password`). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | rootadmin credential is managed only via bootstrap env + token-gated setup endpoint, never the user-admin reset. |

---

## TC-RBAC-020 — Stale-perms window: removing a permission from a role does not retroactively invalidate a live token, but a refresh does

| Field | Value |
|-------|-------|
| **ID** | TC-RBAC-020 |
| **Title** | Permission removed from a role takes effect on next token mint (≤15m window), not retroactively on a live token |
| **Area** | iam / auth |
| **Dimension** | SEC |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-008 |
| **Endpoint** | `PUT /roles/uid/{uid}/permissions` + `POST /auth/refresh` |
| **Preconditions** | Custom role `RBAC_STALE_<entropy>` with `ITEM.CREATE`, granted to throwaway user `rbac_s20_<entropy>`. Login → accessTokenA (carries `ITEM.CREATE`). |
| **Steps** | 1. As rootadmin: `PUT /roles/uid/{uid}/permissions` body `{"permissionIds":[]}` (drop ITEM.CREATE from the role — note: this is a perm change, NOT a grant revoke, so it does NOT trip `invalidateUserTokens`). 2. Re-use accessTokenA: `POST /api/v1/items`. 3. `POST /api/v1/auth/refresh` with refreshTokenA → accessTokenB. 4. Re-use accessTokenB: `POST /api/v1/items`. |
| **Expected Result** | Step 2: **201** — the still-valid token carries the stale `ITEM.CREATE` claim (max stale window = 15-min access TTL; perm edits do not force re-mint, unlike a grant revoke per TC-RBAC-008). Step 4: **403** — refreshed token no longer carries `ITEM.CREATE`. Documents the intentional difference: permission-set edits propagate on next mint; grant revokes propagate immediately. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Contrast with TC-RBAC-008 (revoke = immediate). If product requires immediate effect on perm edits too, that's an enhancement, not a bug here. |

---

## Coverage summary

| Dimension | Cases |
|-----------|-------|
| FUNC | TC-RBAC-001, 002 |
| NEG | TC-RBAC-009, 012, 013, 015 |
| DATA | TC-RBAC-003, 004, 014 |
| SEC | TC-RBAC-005, 006, 007, 008, 010, 011, 016, 017, 018, 019, 020 |

| Priority | Cases |
|----------|-------|
| P0 | TC-RBAC-001, 005, 006, 007, 008, 010, 011, 016, 017 |
| P1 | TC-RBAC-002, 003, 009, 012, 013, 018, 019, 020 |
| P2 | TC-RBAC-004, 014, 015 |

**Security boundaries asserted:** least-privilege (a role grants only assigned perms — 001), gate enforcement / no self-escalation (005), branch-scoped tenant isolation actively attempted (006, 007), immediate revocation (008), system-role + rootadmin tamper-proofing (010, 011), session/logout-everywhere scoping (016, 017), password-change/reset gating + rootadmin protection (018, 019), stale-perms window (020).
