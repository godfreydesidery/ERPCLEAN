# TC-SALES — Back-Office Sales: Invoice, Receipt, Return

**Module:** sales  
**Stories:** US-SALES-001 through US-SALES-013  
**API base:** `http://localhost:8081/api/v1`

---

### TC-SALES-001 — Raise a sales invoice; stock and debt posted on approval

| Field | Value |
|-------|-------|
| **ID** | TC-SALES-001 |
| **Title** | POST /sales-invoices with credit customer; on post: stock move + debt_entry created |
| **Area** | sales |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-005 |
| **Preconditions** | 1. Business day OPEN. 2. Customer with credit terms exists. 3. Item in stock (qty_on_hand >= 2). 4. Price list assigned to customer. |
| **Steps** | 1. `POST /api/v1/sales-invoices` body: `{"customerId":<id>,"lines":[{"itemId":<id>,"qty":2,"unitPrice":1200,"discountPct":0}],"paymentTerms":"CREDIT"}`. 2. `POST /api/v1/sales-invoices/uid/<uid>/post`. 3. Check `stock_move`. 4. Check `debt_entry`. |
| **Expected Result** | Step 2: HTTP 200; `data.status = "POSTED"`. Step 3: SALE stock_move qty=-2. Step 4: `debt_entry` created for customer, amount=2400, due_date computed from terms. Domain event `SalesInvoicePosted.v1` in outbox. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-SALES-005 AC |

---

### TC-SALES-002 — Credit limit check blocks invoice above limit

| Field | Value |
|-------|-------|
| **ID** | TC-SALES-002 |
| **Title** | Invoice that exceeds customer credit_limit_amount is blocked without override permission |
| **Area** | sales |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-005 |
| **Preconditions** | Customer has `credit_limit_amount = 5000`. Invoice total = 6000. User does NOT have `SALES_INVOICE.OVERRIDE_CREDIT`. |
| **Steps** | 1. `POST /api/v1/sales-invoices` with total 6000. Then `POST /post`. |
| **Expected Result** | HTTP 422; error references credit limit; no stock move, no debt_entry. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | US-SALES-005 AC: "Credit-limit check blocks credit sales" |

---

### TC-SALES-003 — Void a posted sales invoice; compensating moves created

| Field | Value |
|-------|-------|
| **ID** | TC-SALES-003 |
| **Title** | Voiding a POSTED sales invoice creates compensating stock move and debt entry |
| **Area** | sales |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-007 |
| **Preconditions** | TC-SALES-001 completed; invoice in POSTED status. Logged in as manager with SALES_INVOICE.VOID. |
| **Steps** | 1. `POST /api/v1/sales-invoices/uid/<uid>/void` body: `{"reason":"Pricing error"}`. 2. Check stock_move and debt_entry for reversals. |
| **Expected Result** | HTTP 200; `data.status = "VOIDED"`; new stock_move qty=+2 (reversal); compensating debt_entry closing the original; original document preserved. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-SALES-007 AC |

---

### TC-SALES-004 — Sales receipt reduces customer debt

| Field | Value |
|-------|-------|
| **ID** | TC-SALES-004 |
| **Title** | POST /sales-receipts creates cash_entry and reduces customer outstanding debt |
| **Area** | sales |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-008 |
| **Preconditions** | Customer has open invoice with debt_entry for 2400 TZS. |
| **Steps** | 1. `POST /api/v1/sales-receipts` body: `{"customerId":<id>,"amount":2400,"method":"CASH","reference":"REC-001"}`. 2. Check `cash_entry`. 3. Check `debt_entry` status. |
| **Expected Result** | HTTP 201; `cash_entry` CASH_IN 2400 TZS on COUNTER account; customer `debt_entry` balance reduced; `data.status = "POSTED"`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-SALES-008 AC |

---

### TC-SALES-005 — Customer return with restock reverses stock and opens credit note

| Field | Value |
|-------|-------|
| **ID** | TC-SALES-005 |
| **Title** | POST /customer-returns with restock=true creates RETURN_IN stock move and credit note |
| **Area** | sales |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-010 |
| **Preconditions** | 1. Original invoice exists in POSTED status. 2. User has CUSTOMER_RETURN.POST permission. |
| **Steps** | 1. `POST /api/v1/customer-returns` body: `{"invoiceId":<id>,"lines":[{"itemId":<id>,"qty":1}],"restock":true}`. 2. Check stock_move. 3. Check for credit note or compensating debt_entry. |
| **Expected Result** | HTTP 201; stock_move type RETURN_IN qty=+1; credit note or compensating debt_entry opened for customer; original invoice shows partial return. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-SALES-010 AC |

---

### TC-SALES-006 — Sales invoice customer picker shows name, not raw id

| Field | Value |
|-------|-------|
| **ID** | TC-SALES-006 |
| **Title** | New Sales Invoice form uses customer name typeahead picker, not raw customerId field |
| **Area** | sales / web |
| **Dimension** | UX |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-005 |
| **Preconditions** | Web app running. Logged in as salesperson. |
| **Steps** | 1. Navigate to Sales > Invoices > New. 2. Inspect the Customer field. 3. Inspect Item fields on lines. |
| **Expected Result** | Customer field is a typeahead picker showing customer names. Item field on lines is a picker showing item names/codes. No raw numeric id or uid input box visible. |
| **Automatable?** | yes — Playwright e2e + axe |
| **Result/Status** | |
| **Notes/IssueRef** | CLAUDE.md no-raw-id-pickers rule — PR 54 reference |

---

### TC-SALES-007 — Discount above threshold requires supervisor for back-office invoice

| Field | Value |
|-------|-------|
| **ID** | TC-SALES-007 |
| **Title** | Line discount above configured threshold on sales invoice requires supervisor authorisation |
| **Area** | sales |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-006 |
| **Preconditions** | Discount threshold configured. User does not have DISCOUNT_APPROVE. |
| **Steps** | 1. POST invoice with line discountPct above threshold, no supervisor token. |
| **Expected Result** | HTTP 422; error references discount approval required. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | US-SALES-006 AC |

---

### TC-SALES-008 — Receipt allocation reduces invoice ageing correctly

| Field | Value |
|-------|-------|
| **ID** | TC-SALES-008 |
| **Title** | Allocating receipt to two invoices correctly zeroes first and reduces second |
| **Area** | sales |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-009 |
| **Preconditions** | Customer has two open invoices: I1=2400, I2=1800. Receipt amount=3000. |
| **Steps** | 1. POST receipt for 3000 TZS. 2. POST allocation: `[{"invoiceId":<I1_id>,"amount":2400},{"invoiceId":<I2_id>,"amount":600}]`. 3. Check I1 balance. 4. Check I2 balance. 5. Check remaining unallocated credit. |
| **Expected Result** | I1 fully settled (balance=0, status=PAID or equivalent). I2 balance = 1200 (outstanding). Unallocated credit = 0 (full 3000 allocated). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-SALES-009 AC |
