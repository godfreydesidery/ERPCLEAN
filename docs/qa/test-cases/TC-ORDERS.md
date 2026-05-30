# TC-ORDERS — Layby / Customer Orders

**Module:** orders  
**Stories:** (layby / pre-order lifecycle)  
**API base:** `http://localhost:8081/api/v1`

---

### TC-ORDERS-001 — Create layby with deposit payment

| Field | Value |
|-------|-------|
| **ID** | TC-ORDERS-001 |
| **Title** | POST /customer-orders creates layby; deposit payment recorded; status OPEN |
| **Area** | orders |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | (layby lifecycle) |
| **Preconditions** | Business day OPEN. Customer exists. Items in catalog. |
| **Steps** | 1. `POST /api/v1/customer-orders` body: `{"customerId":<id>,"lines":[{"itemId":<id>,"qty":1,"unitPrice":5000}],"depositAmount":2000}`. |
| **Expected Result** | HTTP 201; `data.status = "OPEN"`, `data.depositAmount = 2000`, `data.balance = 3000`; deposit recorded as payment. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-ORDERS-002 — Complete layby when fully paid; converts to invoice

| Field | Value |
|-------|-------|
| **ID** | TC-ORDERS-002 |
| **Title** | Paying remaining balance on layby closes the layby and creates a sales invoice |
| **Area** | orders |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | (layby lifecycle) |
| **Preconditions** | Layby with remaining balance 3000. |
| **Steps** | 1. `POST /api/v1/customer-orders/uid/<uid>/pay` body: `{"amount":3000,"method":"CASH"}`. |
| **Expected Result** | Layby status = COMPLETED; sales invoice created and posted; stock decremented. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-ORDERS-003 — Cancel layby refunds deposit

| Field | Value |
|-------|-------|
| **ID** | TC-ORDERS-003 |
| **Title** | Cancelling a layby triggers deposit refund process; status = CANCELLED |
| **Area** | orders |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | (layby lifecycle) |
| **Preconditions** | Layby with deposit 2000. |
| **Steps** | 1. `POST /api/v1/customer-orders/uid/<uid>/cancel` body: `{"reason":"Customer no longer wants"}`. |
| **Expected Result** | Status = CANCELLED; deposit refund recorded (cash_entry or customer credit); items NOT allocated to stock (were reserved, not deducted). |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-ORDERS-004 — Layby ageing report shows overdue layby items

| Field | Value |
|-------|-------|
| **ID** | TC-ORDERS-004 |
| **Title** | Layby ageing report lists laybys with outstanding balance and overdue date |
| **Area** | orders / reports |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | (layby lifecycle) |
| **Preconditions** | Layby 45 days old with remaining balance. |
| **Steps** | 1. `GET /api/v1/reports/layby-ageing?branchId=1`. |
| **Expected Result** | HTTP 200; layby appears with correct days open and remaining balance; overdue flag set if past due date. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `LaybyAgeingReportController` exists in API |
