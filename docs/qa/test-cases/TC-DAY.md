# TC-DAY — Business Day Management

**Module:** day  
**Stories:** US-DAY-001 through US-DAY-005  
**API base:** `http://localhost:8081/api/v1`

---

### TC-DAY-001 — Open business day for a branch

| Field | Value |
|-------|-------|
| **ID** | TC-DAY-001 |
| **Title** | POST /business-days opens day; status=OPEN; audit row written |
| **Area** | day |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-DAY-001 |
| **Preconditions** | 1. Branch 1 has no OPEN day for today. 2. Logged in as rootadmin. |
| **Steps** | 1. `POST /api/v1/business-days?branchId=1` body: `{"businessDate":"2026-05-30"}`. 2. `GET /api/v1/business-days?branchId=1&status=OPEN`. |
| **Expected Result** | Step 1: HTTP 201; `data.status = "OPEN"`, `data.branchId = 1`, `data.businessDate = "2026-05-30"`, `data.openedBy` = rootadmin's userId. Domain event `BusinessDayOpened.v1` emitted to outbox. Step 2: returns the day. |
| **Automatable?** | yes — unit test (`BusinessDayServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-DAY-002 — Second open day for same branch-date rejected

| Field | Value |
|-------|-------|
| **ID** | TC-DAY-002 |
| **Title** | Opening a second OPEN day for the same branch returns 409 |
| **Area** | day |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-DAY-001 |
| **Preconditions** | Branch 1 already has OPEN day for today (TC-DAY-001). |
| **Steps** | 1. `POST /api/v1/business-days?branchId=1` body: `{"businessDate":"2026-05-30"}`. |
| **Expected Result** | HTTP 409 or 422; error references existing open day. Only one OPEN day per branch enforced. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | US-DAY-001 AC: "At most one OPEN business day exists per branch" |

---

### TC-DAY-003 — Close business day; pre-flight checks enforced

| Field | Value |
|-------|-------|
| **ID** | TC-DAY-003 |
| **Title** | Close business day fails if unclosed till sessions exist |
| **Area** | day |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-DAY-002 |
| **Preconditions** | 1. OPEN day for branch 1. 2. At least one OPEN till session on this day. |
| **Steps** | 1. `POST /api/v1/business-days/uid/<uid>/close`. |
| **Expected Result** | HTTP 422; error body lists the open till sessions as blocking; status remains OPEN. |
| **Automatable?** | yes — unit test with mocked EodGuard |
| **Result/Status** | |
| **Notes/IssueRef** | US-DAY-002 AC: "Pre-flight checks: all till sessions closed" |

---

### TC-DAY-004 — Close business day succeeds after all sessions closed

| Field | Value |
|-------|-------|
| **ID** | TC-DAY-004 |
| **Title** | Business day closes successfully when all pre-flight checks pass |
| **Area** | day |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-DAY-002 |
| **Preconditions** | 1. OPEN day. 2. All till sessions CLOSED. 3. No open GRNs or production batches. |
| **Steps** | 1. `POST /api/v1/business-days/uid/<uid>/close`. 2. `GET /api/v1/business-days/uid/<uid>`. |
| **Expected Result** | HTTP 200; `data.status = "CLOSED"`, `data.closedAt` populated. Domain event `BusinessDayClosed.v1` emitted. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-DAY-005 — POS sale blocked when business day is closed

| Field | Value |
|-------|-------|
| **ID** | TC-DAY-005 |
| **Title** | Posting a POS sale on a branch with CLOSED business day returns 422 |
| **Area** | day |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-DAY-001 US-POS-002 |
| **Preconditions** | Branch has NO open business day. |
| **Steps** | 1. Attempt `POST /api/v1/pos-sales` (or open till session) for branch 1 with closed day. |
| **Expected Result** | HTTP 422; error code `BUSINESS_DAY_CLOSED`; no sale posted. |
| **Automatable?** | yes — unit test (DayGuard integration) |
| **Result/Status** | |
| **Notes/IssueRef** | DayGuard enforces this across modules |

---

### TC-DAY-006 — Sales invoice blocked when business day is closed

| Field | Value |
|-------|-------|
| **ID** | TC-DAY-006 |
| **Title** | POST /sales-invoices fails with BUSINESS_DAY_CLOSED when day is not OPEN |
| **Area** | day / sales |
| **Dimension** | INT |
| **Priority** | P0 |
| **Linked US-*** | US-DAY-001 US-SALES-005 |
| **Preconditions** | Branch 1 has CLOSED day. |
| **Steps** | 1. `POST /api/v1/sales-invoices` with valid payload and `X-Branch-Id: 1`. |
| **Expected Result** | HTTP 422; error references business day gating. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-DAY-007 — listDays returns days in descending date order

| Field | Value |
|-------|-------|
| **ID** | TC-DAY-007 |
| **Title** | GET /business-days returns days newest-first |
| **Area** | day |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-DAY-004 |
| **Preconditions** | Multiple business days exist for branch 1. |
| **Steps** | 1. `GET /api/v1/business-days?branchId=1`. |
| **Expected Result** | List is ordered by `businessDate` descending. `data[0].businessDate >= data[1].businessDate`. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | `BusinessDayServiceImpl.listDays` uses `findByBranchIdOrderByBusinessDateDesc` |
