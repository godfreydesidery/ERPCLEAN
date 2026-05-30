# TC-DEBT — Debt Management: Receivables and Payables

**Module:** debt  
**Stories:** US-DEBT-001 through US-DEBT-007  
**API base:** `http://localhost:8081/api/v1`

---

### TC-DEBT-001 — Customer debt position shows open invoices and ageing

| Field | Value |
|-------|-------|
| **ID** | TC-DEBT-001 |
| **Title** | GET /debt/statement/uid/{customerUid} shows open invoices, recent receipts, and outstanding balance |
| **Area** | debt |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-DEBT-001 |
| **Preconditions** | Customer has 3 open invoices: one current, one 45 days overdue, one 95 days overdue. |
| **Steps** | 1. `GET /api/v1/debt/statement/uid/<customer_uid>` with admin auth. Alternatively `GET /api/v1/debt/statement?customerUid=<uid>`. |
| **Expected Result** | HTTP 200; `data.openInvoices` contains the 3 open invoices with `daysOverdue` correctly computed; `data.totalOutstanding` = sum of open invoice amounts; `data.recentReceipts` list available. For company-wide ageing buckets use `GET /api/v1/debt/aging?asOf=<date>`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | ISSUE-DEBT-TC-URL-01: corrected URL from `GET /api/v1/debt/customer/<uid>` (endpoint does not exist, returns 500 NoResourceFoundException) to `GET /api/v1/debt/statement/uid/<uid>` (actual endpoint in `DebtController.java:83`). US-DEBT-001 AC: "open invoices, allocated receipts, customer credit, ageing buckets". |

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

### TC-DEBT-003 — Customer statement contains invoices, receipts, and total outstanding

| Field | Value |
|-------|-------|
| **ID** | TC-DEBT-003 |
| **Title** | Customer statement JSON contains open invoices, recent receipts, and total outstanding balance |
| **Area** | debt |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-DEBT-005 |
| **Preconditions** | Customer has an invoice (2400 TZS), a receipt allocated against it (1200 TZS), net balance (1200 TZS). |
| **Steps** | 1. `GET /api/v1/debt/statement/uid/<customer_uid>`. |
| **Expected Result** | HTTP 200; `data.openInvoices` contains the partially-paid invoice with `amountDue = 1200`; `data.recentReceipts` contains the receipt of 1200; `data.totalOutstanding = 1200`. Note: the current `CustomerStatementDto` presents invoices and receipts as two separate lists, not a chronologically merged ledger with per-line running balance. `data.runningBalance` does not exist as a field. The US-DEBT-005 AC requirement for "running balance sequence" is a known gap (ISSUE-DEBT-STMT-BALANCE-01); a merged `List<LedgerRow>` with cumulative balance column is not yet implemented and requires product sign-off to add. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | ISSUE-DEBT-STMT-BALANCE-01: corrected URL from `GET /api/v1/debt/statement/customer/<uid>` (wrong path) to `GET /api/v1/debt/statement/uid/<uid>`. Removed expectation of per-line running balance field — `CustomerStatementDto` returns two separate arrays (`openInvoices`, `recentReceipts`) plus a scalar `totalOutstanding`. A merged chronological ledger with running balance is a requirement gap tracked as ISSUE-DEBT-STMT-BALANCE-01. US-DEBT-005 AC. |

---

### TC-DEBT-004 — Debt write-off requires dual-control approval

| Field | Value |
|-------|-------|
| **ID** | TC-DEBT-004 |
| **Title** | Writing off debt above the threshold requires requester and approver to be different users |
| **Area** | debt |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | US-DEBT-004 |
| **Preconditions** | Customer has uncollectable debt 5000 TZS. `orbix.debt.write-off.dual-approval-threshold` configured (default 0, meaning all write-offs above 0 require separate approver). Two users both holding `DEBT.WRITE_OFF.REQUEST` and `DEBT.WRITE_OFF.APPROVE` exist. |
| **Steps** | 1. User A: `POST /api/v1/debt/write-offs` body `{"targetInvoiceUid":"<uid>","targetKind":"RECEIVABLE","amount":5000,"reason":"Uncollectable"}` — creates write-off in `PENDING_APPROVAL`. 2. User A again: `POST /api/v1/debt/write-offs/uid/<uid>/approve` — same user attempts approval. 3. User B: `POST /api/v1/debt/write-offs/uid/<uid>/approve` — different user approves. |
| **Expected Result** | Step 1: HTTP 201; `data.status = "PENDING_APPROVAL"`, `data.requestedByUserId` = User A. Step 2: HTTP 409 or 422; "approver must differ from requester" (dual-control guard). Step 3: HTTP 200; `data.status = "POSTED"`, `data.approvedByUserId` = User B; compensating debt_entry created; reason logged. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | ISSUE-DEBT-WRITEOFF-DUAL-01: corrected from dual-role model (DEBT.WRITE_OFF_SUPERVISOR + DEBT.WRITE_OFF_ACCOUNTANT, two separate named role-holders) to the implemented single-permission dual-control model. The implementation uses a single permission `DEBT.WRITE_OFF.APPROVE` with a threshold-gated requester-ne-approver check (`DebtWriteOffServiceImpl.java:135`). Permissions `DEBT.WRITE_OFF_SUPERVISOR` and `DEBT.WRITE_OFF_ACCOUNTANT` do not exist in the codebase. Endpoint: `POST /api/v1/debt/write-offs` (create) and `POST /api/v1/debt/write-offs/uid/{uid}/approve` (approve). Product decision pending on whether to implement the original two-role flow. |

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
