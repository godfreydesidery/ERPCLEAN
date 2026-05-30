# TC-REPORTS — Reports and Exports

**Module:** reports  
**Stories:** US-RPT-001 through US-RPT-010  
**API base:** `http://localhost:8081/api/v1`

---

### TC-RPT-001 — Daily sales report for a branch

| Field | Value |
|-------|-------|
| **ID** | TC-RPT-001 |
| **Title** | GET /reports/sales-daily returns correct totals for the branch and date |
| **Area** | reports |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-RPT-001 |
| **Preconditions** | Today's date has at least 2 posted sales invoices totalling 5000 TZS. |
| **Steps** | 1. `GET /api/v1/reports/sales?date=2026-05-30&branchId=1`. |
| **Expected Result** | HTTP 200; `data.totalSales >= 5000`; invoice list includes today's invoices; columns: invoice no, customer, total, payment terms. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-RPT-001 AC |

---

### TC-RPT-002 — Stock card report for item + branch + date range

| Field | Value |
|-------|-------|
| **ID** | TC-RPT-002 |
| **Title** | GET /reports/stock-card returns moves filtered by date with running balance |
| **Area** | reports |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-RPT-004 |
| **Preconditions** | Item COKE500 has at least 3 stock_move records in the date range. |
| **Steps** | 1. `GET /api/v1/stock-report/card?itemId=<id>&branchId=1&from=2026-01-01&to=2026-05-30`. |
| **Expected Result** | List of moves with type, qty, running balance; running balance at end matches current qty_on_hand. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-RPT-004 AC |

---

### TC-RPT-003 — Dashboard report returns non-null summary data

| Field | Value |
|-------|-------|
| **ID** | TC-RPT-003 |
| **Title** | GET /reports/dashboard returns today's totals without 500 error |
| **Area** | reports |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-RPT-002 |
| **Preconditions** | Business day OPEN. At least one posted transaction today. |
| **Steps** | 1. `GET /api/v1/reports/dashboard?branchId=1`. |
| **Expected Result** | HTTP 200; data contains `totalSales`, `totalCash`, `openTillSessions`, `itemsLowStock`; no null pointer errors. |
| **Automatable?** | yes — integration test (`DashboardReportServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-RPT-004 — Negative stock report lists items with qty_on_hand < 0

| Field | Value |
|-------|-------|
| **ID** | TC-RPT-004 |
| **Title** | GET /reports/negative-stock returns only items with qty_on_hand < 0 at the branch |
| **Area** | reports |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-RPT-006 |
| **Preconditions** | Item X has qty_on_hand=-5 (from oversell with permission). Item Y has qty_on_hand=10. |
| **Steps** | 1. `GET /api/v1/stock-report/negative?branchId=1`. |
| **Expected Result** | List contains Item X; does not contain Item Y. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-RPT-006 AC |

---

### TC-RPT-005 — Sales aggregate report pages correctly

| Field | Value |
|-------|-------|
| **ID** | TC-RPT-005 |
| **Title** | Sales aggregate report with pageSize param returns correct paged data |
| **Area** | reports |
| **Dimension** | PERF |
| **Priority** | P2 |
| **Linked US-*** | US-RPT-001 |
| **Preconditions** | More than 50 invoice records exist. |
| **Steps** | 1. `GET /api/v1/reports/sales-aggregate?pageSize=10&page=0`. 2. GET page 1. 3. Check for overlaps. |
| **Expected Result** | 10 records per page; no invoice appears twice; `totalCount` consistent. Response time < 2 seconds. |
| **Automatable?** | yes — integration test + timing assertion |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-RPT-006 — Reports scoped to user's branch; cross-branch data not returned

| Field | Value |
|-------|-------|
| **ID** | TC-RPT-006 |
| **Title** | Report endpoints only return data for the caller's active branch |
| **Area** | reports |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-IAM-009 |
| **Preconditions** | Two branches. Branch-2 user logs in. Branch-2 has different sales from branch-1. |
| **Steps** | 1. Login as branch-2 user. 2. GET /reports/sales?date=today&branchId=1 (attempting to read branch-1 data). |
| **Expected Result** | HTTP 403 or 200 with empty data for branch 1 (scoped to user's grants). Branch-2-only data does not appear in branch-1 reports. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Multi-tenant security |
