# TC-ADMIN — Admin: Company, Branch, Setup, Settings

**Module:** admin  
**Stories:** US-COMP-001 through US-COMP-009  
**API base:** `http://localhost:8081/api/v1`

---

### TC-ADMIN-001 — Fresh-volume container boots clean; Flyway migrations pass; health UP

| Field | Value |
|-------|-------|
| **ID** | TC-ADMIN-001 |
| **Title** | Fresh-volume QA container starts; Flyway runs all migrations; /actuator/health returns UP |
| **Area** | admin / platform |
| **Dimension** | RELI |
| **Priority** | P0 |
| **Linked US-*** | US-PLAT-001 |
| **Preconditions** | Docker available. Image `orbix:qa` built. |
| **Steps** | 1. `docker rm -f orbix; docker volume rm orbix-data-local`. 2. `docker volume create orbix-data-local`. 3. `docker run -d --name orbix -p 8081:8081 -v orbix-data-local:/var/lib/mysql --env-file orbix-engine-infra/qa/orbix.env orbix:qa`. 4. Wait 60 seconds. 5. `curl http://localhost:8081/actuator/health`. 6. `curl http://localhost:8081/api/v1/auth/login -d '{"username":"rootadmin","password":"<from orbix.env>"}'`. |
| **Expected Result** | Step 5: `{"status":"UP"}`. Step 6: HTTP 200 with accessToken; rootadmin user created by bootstrap. No Flyway errors in `docker logs orbix`. Validate ddl-auto=validate passes (no schema drift). |
| **Automatable?** | partial — shell script; requires Docker. `HealthSmokeTest` in backend (currently needs test DB infra — tracked debt). |
| **Result/Status** | |
| **Notes/IssueRef** | Boot-safety is P0. `HealthSmokeTest` is the backend equivalent — currently tracked as test debt (1 error in suite as of 2026-05-24). |

---

### TC-ADMIN-002 — Company profile update is audited

| Field | Value |
|-------|-------|
| **ID** | TC-ADMIN-002 |
| **Title** | Updating company TIN/VRN is audited; before/after values in audit_log |
| **Area** | admin |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-COMP-002 |
| **Preconditions** | Company exists. Admin logged in. |
| **Steps** | 1. `PUT /api/v1/companies/uid/<uid>` body: `{"tin":"123456789","vrn":"VRN-NEW-001"}`. 2. `GET /api/v1/audit?entityType=Company&entityId=<id>&action=UPDATE`. |
| **Expected Result** | HTTP 200 on update. Audit row with action=UPDATE, before-JSON contains old TIN, after-JSON contains new TIN. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-COMP-002 AC: "All edits are audited" |

---

### TC-ADMIN-003 — Branch default flag mutual-exclusion enforced

| Field | Value |
|-------|-------|
| **ID** | TC-ADMIN-003 |
| **Title** | Setting branch B as default clears is_default on previous default branch A |
| **Area** | admin |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-COMP-004 |
| **Preconditions** | Branch A has `is_default=true`. Branch B exists. |
| **Steps** | 1. `PUT /api/v1/branches/uid/<B_uid>` body includes `isDefault: true`. 2. `GET /api/v1/branches/uid/<A_uid>`. |
| **Expected Result** | Branch B: `isDefault=true`. Branch A: `isDefault=false`. No two branches have `isDefault=true` in the same company. |
| **Automatable?** | yes — unit test (`BranchServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | US-COMP-004 AC: "Setting is_default = true automatically clears the flag on the previous default branch" |

---

### TC-ADMIN-004 — Settings endpoint returns current system configuration

| Field | Value |
|-------|-------|
| **ID** | TC-ADMIN-004 |
| **Title** | GET /settings returns current orbix.* thresholds relevant to UI configuration |
| **Area** | admin |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-PLAT-001 |
| **Preconditions** | Admin logged in. |
| **Steps** | 1. `GET /api/v1/settings`. |
| **Expected Result** | HTTP 200; response contains at minimum: discount threshold, cash variance threshold, fiscal regime; values match `application.yml` or `app_setting` table overrides. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `SettingsController` + `app_setting` table (V64 migration) |

---

### TC-ADMIN-005 — Number sequences are per branch and per doc type

| Field | Value |
|-------|-------|
| **ID** | TC-ADMIN-005 |
| **Title** | LPO numbers from branch 1 and branch 2 are independent sequences |
| **Area** | admin |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-COMP-008 |
| **Preconditions** | Number sequences configured for LPO in both branches. |
| **Steps** | 1. Create LPO for branch 1 — note number. 2. Create LPO for branch 2 — note number. |
| **Expected Result** | Branch 1 LPO numbers follow Branch 1 sequence (e.g. LPO-BR1-000001). Branch 2 LPO numbers follow Branch 2 sequence (LPO-BR2-000001). Numbers do not collide. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-COMP-008 AC: "Per (company, branch, doc_type)" |
