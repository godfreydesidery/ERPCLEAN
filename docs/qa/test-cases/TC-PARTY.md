# TC-PARTY — Party: Customer, Supplier, Employee

**Module:** party  
**Stories:** US-SALES-001, US-PROC-001, US-HR-001, US-HR-002  
**API base:** `http://localhost:8081/api/v1`

---

### TC-PARTY-001 — Create customer with required fields

| Field | Value |
|-------|-------|
| **ID** | TC-PARTY-001 |
| **Title** | POST /customers creates party+customer; uid assigned; response id is string |
| **Area** | party |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-001 |
| **Preconditions** | Logged in. No existing customer with same name. |
| **Steps** | 1. `POST /api/v1/customers` body: `{"name":"Test Customer","category":"RETAIL","phone":"0712345678"}`. 2. Inspect response `id` and `uid` fields. |
| **Expected Result** | HTTP 201; `data.uid` is 26-char ULID; `data.id` is a JSON string. `data.name = "Test Customer"`. Party row + Customer row created. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-PARTY-002 — Customer assigned to price list via picker

| Field | Value |
|-------|-------|
| **ID** | TC-PARTY-002 |
| **Title** | Web customer form shows price list name picker, not raw priceListId |
| **Area** | party / web |
| **Dimension** | UX |
| **Priority** | P0 |
| **Linked US-*** | US-CAT-008 |
| **Preconditions** | Web app running. Logged in. At least one price list exists. |
| **Steps** | 1. Navigate to Party > Customers > New. 2. Inspect Price List field. |
| **Expected Result** | Price List field is a dropdown/typeahead showing price list names, not a raw id input. |
| **Automatable?** | yes — Playwright e2e + axe |
| **Result/Status** | |
| **Notes/IssueRef** | No-raw-id rule |

---

### TC-PARTY-003 — Customer credit limit enforced across branches

| Field | Value |
|-------|-------|
| **ID** | TC-PARTY-003 |
| **Title** | Credit limit on customer applies across all branches in the company |
| **Area** | party / sales |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-005 |
| **Preconditions** | Customer credit_limit=5000. Branch 1 has invoice 3000 outstanding. |
| **Steps** | 1. Try to post invoice for 3000 from branch 2 (total would be 6000). |
| **Expected Result** | Blocked with credit limit error referencing total exposure, not just branch-2 exposure. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Multi-tenant: credit limit is company-scoped, not branch-scoped |

---

### TC-PARTY-004 — Customer form page passes axe-core WCAG AA check

| Field | Value |
|-------|-------|
| **ID** | TC-PARTY-004 |
| **Title** | Customer create/edit page has zero axe-core violations |
| **Area** | party / web |
| **Dimension** | UX |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-001 |
| **Preconditions** | Web app running at http://localhost:8081/. Logged in. |
| **Steps** | 1. Navigate to Party > Customers > New. 2. Run axe-core via Playwright `checkA11y()`. |
| **Expected Result** | 0 violations at WCAG AA level. |
| **Automatable?** | yes — Playwright + axe-core |
| **Result/Status** | |
| **Notes/IssueRef** | WCAG AA is a CI gate per CLAUDE.md |

---

### TC-PARTY-005 — Supplier created with party record

| Field | Value |
|-------|-------|
| **ID** | TC-PARTY-005 |
| **Title** | POST /suppliers creates party+supplier; code is unique per company |
| **Area** | party |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-001 |
| **Preconditions** | Logged in as merchandiser. |
| **Steps** | 1. `POST /api/v1/suppliers` body: `{"name":"ABC Distributors","code":"ABC001"}`. 2. Create another with same code. |
| **Expected Result** | Step 1: HTTP 201. Step 2: HTTP 409 — code uniqueness per company. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |
