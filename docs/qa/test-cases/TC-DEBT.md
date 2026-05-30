# TC-DEBT — Debt Management: Receivables and Payables

**Module:** debt  
**Stories:** US-DEBT-001 through US-DEBT-007  
**API base:** `http://localhost:8081/api/v1`

---

### TC-DEBT-001 — Customer debt position shows open invoices and ageing

| Field | Value |
|-------|-------|
| **ID** | TC-DEBT-001 |
| **Title** | GET /debt/customer shows open invoices with ageing buckets |
| **Area** | debt |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-DEBT-001 |
| **Preconditions** | Customer has 3 open invoices: one current, one 45 days overdue, one 95 days overdue. |
| **Steps** | 1. `GET /api/v1/debt/customer/<customer_uid>`. |
| **Expected Result** | Response shows: open invoice list; ageing buckets 0-30: <amount>, 31-60: <amount>, 61-90: 0, 90+: <amount>; total outstanding balance. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-DEBT-001 AC: "open invoices, allocated receipts, customer credit, ageing buckets" |

---

### TC-DEBT-002 — Debt ageing report groups correctly

| Field | Value |
|-------|-------|
| **ID** | TC-DEBT-002 |
| **Title** | Ageing report buckets (0-30/31-60/61-90/90+) correctly classify debt_entry rows |
| **Area** | debt |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-DEBT-003 |
| **Preconditions** | Multiple debt_entries at various ages. |
| **Steps** | 1. `GET /api/v1/debt/ageing?type=RECEIVABLE&asOf=2026-05-30`. 2. Verify bucket totals manually against known due dates. |
| **Expected Result** | Each debt_entry classified in exactly one bucket based on days past due relative to asOf date; no entry in two buckets; total across buckets = sum of all open debt amounts. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-DEBT-003 AC |

---

### TC-DEBT-003 — Customer statement contains invoices, receipts, and running balance

| Field | Value |
|-------|-------|
| **ID** | TC-DEBT-003 |
| **Title** | Customer statement PDF/JSON contains correct running balance sequence |
| **Area** | debt |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-DEBT-005 |
| **Preconditions** | Customer has invoice (2400), receipt (1200), net balance (1200). |
| **Steps** | 1. `GET /api/v1/debt/statement/customer/<uid>?from=2026-01-01&to=2026-05-30`. |
| **Expected Result** | Response shows: invoice entry +2400, receipt entry -1200, running balance 1200 at statement end. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-DEBT-005 AC |

---

### TC-DEBT-004 — Debt write-off requires dual approval

| Field | Value |
|-------|-------|
| **ID** | TC-DEBT-004 |
| **Title** | Writing off debt requires both supervisor and accountant approvals |
| **Area** | debt |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | US-DEBT-004 |
| **Preconditions** | Customer has uncollectable debt 5000 TZS. Users with DEBT.WRITE_OFF_SUPERVISOR and DEBT.WRITE_OFF_ACCOUNTANT exist. |
| **Steps** | 1. POST debt write-off with only one approver. 2. POST debt write-off with both approvers. |
| **Expected Result** | Step 1: HTTP 422 or 403; insufficient approvals. Step 2: HTTP 201; compensating debt_entry created; reason logged; both approver identities recorded. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-DEBT-004 AC: "Requires Supervisor + Accountant authorisations (both recorded)" |

---

### TC-DEBT-005 — Money amounts in debt entries use TZS precision (no fractional cents)

| Field | Value |
|-------|-------|
| **ID** | TC-DEBT-005 |
| **Title** | Debt entry amounts stored and returned without floating-point rounding artifacts |
| **Area** | debt |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-DEBT-001 |
| **Preconditions** | Invoice total involves multiplication (3 * 800 = 2400 TZS). |
| **Steps** | 1. Post invoice 3 units at 800 TZS. 2. Check debt_entry.amount. |
| **Expected Result** | `debt_entry.amount = 2400` exactly; no value like 2399.9999 or 2400.0001. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Tanzania currency TZS has no sub-unit; BigDecimal with scale=0 or controlled rounding expected |
