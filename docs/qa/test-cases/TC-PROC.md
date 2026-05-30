# TC-PROC — Procurement: LPO, GRN, Supplier Payment

**Module:** procurement  
**Stories:** US-PROC-001 through US-PROC-012  
**API base:** `http://localhost:8081/api/v1`

---

### TC-PROC-001 — Create supplier and raise LPO

| Field | Value |
|-------|-------|
| **ID** | TC-PROC-001 |
| **Title** | Create supplier (party), raise LPO with lines; LPO starts in DRAFT status |
| **Area** | procurement |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-001 US-PROC-002 |
| **Preconditions** | 1. Business day OPEN. 2. Logged in as merchandiser with LPO.CREATE permission. 3. Item COKE500 has supplier mapping. |
| **Steps** | 1. `POST /api/v1/suppliers` body: `{"name":"Test Supplier","code":"SUP001"}`. 2. `POST /api/v1/lpo-orders` body: `{"supplierId":<id>,"lines":[{"itemId":<coke_id>,"qty":100,"unitCost":800}]}`. 3. `GET /api/v1/lpo-orders/uid/<uid>`. |
| **Expected Result** | Step 1: HTTP 201 supplier. Step 2: HTTP 201 LPO in DRAFT status; `data.totalAmount = 80000`; subtotal, tax, total computed. Step 3: confirms DRAFT. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-PROC-002 AC: "Initial status: DRAFT" |

---

### TC-PROC-002 — Submit and approve LPO below threshold auto-approves

| Field | Value |
|-------|-------|
| **ID** | TC-PROC-002 |
| **Title** | LPO below configured approval threshold auto-approves on submit |
| **Area** | procurement |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-003 |
| **Preconditions** | Auto-approve threshold configured at 500000 TZS. LPO total = 80000 TZS (below threshold). |
| **Steps** | 1. `POST /api/v1/lpo-orders/uid/<uid>/submit`. |
| **Expected Result** | HTTP 200; `data.status = "APPROVED"` (auto-approved). |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | US-PROC-003 AC: "LPOs below the configured threshold auto-approve" |

---

### TC-PROC-003 — Receive against LPO (GRN); stock updated; supplier debt requires explicit SupplierInvoice

| Field | Value |
|-------|-------|
| **ID** | TC-PROC-003 |
| **Title** | Post GRN against approved LPO: stock_move GRN type created, avg_cost updated; supplier debt requires a subsequent SupplierInvoice |
| **Area** | procurement |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-004 |
| **Preconditions** | LPO in APPROVED status with 100 units COKE500 at 800/unit. |
| **Steps** | 1. `POST /api/v1/grns` body: `{"lpoId":<id>,"lines":[{"lpoLineId":<id>,"receivedQty":100,"unitCost":800}]}`. 2. `POST /api/v1/grns/uid/<uid>/post`. 3. Check `stock_move`. 4. Check `item_branch_balance.avg_cost`. 5. `GET /api/v1/debt/statement/uid/<supplier_uid>` — confirm no debt created yet. 6. `POST /api/v1/supplier-invoices` with GRN reference. 7. `POST /api/v1/supplier-invoices/uid/<uid>/post`. 8. Re-check supplier debt. |
| **Expected Result** | Step 2: HTTP 200, `data.status = "POSTED"`. Step 3: GRN `stock_move` qty=+100, moveType=GRN. Step 4: `avg_cost` updated using moving average formula (e.g. 100 units at 800 = 800 avg). Step 5: `totalOutstanding = 0` — no debt entry created by GRN post alone. Step 7: HTTP 200, SupplierInvoice status = POSTED. Step 8: `totalOutstanding = 94400` (80000 subtotal + 18% VAT). LPO status = RECEIVED. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | ISSUE-PROC-004: corrected step 5 expected result. GRN post does NOT auto-create a supplier debt entry. The implemented 3-way-match flow is: GRN (stock receipt) → SupplierInvoice (creates AP debt) → SupplierPayment (settles debt). `GrnPosted.v1` outbox event exists but no `DomainEventHandler` is registered for it — auto-invoice-from-GRN is not implemented. Step 5 now verifies debt is zero after GRN; steps 6-8 document the required SupplierInvoice path. US-PROC-004 AC. |

---

### TC-PROC-004 — Partial GRN leaves LPO in PARTIALLY_RECEIVED

| Field | Value |
|-------|-------|
| **ID** | TC-PROC-004 |
| **Title** | Receiving 60 of 100 ordered units sets LPO status to PARTIALLY_RECEIVED |
| **Area** | procurement |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-004 |
| **Preconditions** | LPO in APPROVED status with 100 units COKE500. |
| **Steps** | 1. POST GRN with receivedQty=60. POST /post. 2. Check LPO status. |
| **Expected Result** | LPO `data.status = "PARTIALLY_RECEIVED"`. A subsequent GRN for remaining 40 would set to RECEIVED. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | US-PROC-004 AC |

---

### TC-PROC-005 — Cancel posted GRN issues compensating stock move

| Field | Value |
|-------|-------|
| **ID** | TC-PROC-005 |
| **Title** | Cancelling a posted GRN posts compensating stock_move and reverses debt_entry |
| **Area** | procurement |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-012 |
| **Preconditions** | GRN in POSTED status. User has GRN.CANCEL permission. |
| **Steps** | 1. `POST /api/v1/grns/uid/<uid>/cancel` body: `{"reason":"Wrong items received"}`. 2. Check stock_move. 3. Check debt_entry. |
| **Expected Result** | GRN status = CANCELLED; compensating stock_move qty=-100 (RETURN or GRN_CANCEL type); debt_entry reversal; original documents preserved (never deleted). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-PROC-012 AC: "Cancelling a posted GRN issues compensating stock_move and debt_entry rows (never deletes)" |

---

### TC-PROC-006 — Supplier payment allocates against debt entries

| Field | Value |
|-------|-------|
| **ID** | TC-PROC-006 |
| **Title** | POST supplier payment reduces supplier debt balance by the allocated amount |
| **Area** | procurement |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-007 |
| **Preconditions** | Supplier has debt_entry of 80000 TZS. |
| **Steps** | 1. `POST /api/v1/supplier-payments` body: `{"supplierId":<id>,"amount":80000,"method":"BANK_TRANSFER","reference":"PAY-001"}`. 2. Check supplier outstanding debt. |
| **Expected Result** | HTTP 201; `cash_entry` CASH_OUT 80000 from BANK account; supplier debt_entry balance = 0 (or SETTLED). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-PROC-007 |

---

### TC-PROC-007 — Procurement forms use name pickers, not raw ids

| Field | Value |
|-------|-------|
| **ID** | TC-PROC-007 |
| **Title** | LPO form shows supplier name picker and item name picker; no raw ids |
| **Area** | procurement / web |
| **Dimension** | UX |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-002 |
| **Preconditions** | Web app running. Logged in as merchandiser. |
| **Steps** | 1. Navigate to Procurement > LPO > New. 2. Inspect Supplier field, Item fields on lines. 3. Navigate to GRN > New — inspect LPO reference field. |
| **Expected Result** | All reference fields show name/code pickers. No raw `supplierId`, `lpoId`, `itemId` numeric inputs visible to user. |
| **Automatable?** | yes — Playwright e2e + axe |
| **Result/Status** | |
| **Notes/IssueRef** | CLAUDE.md no-raw-id convention |
