# TC-CASH — Cash Ledger, Bank Deposits, Supplier Payments

**Module:** cash  
**Stories:** US-SALES-008, US-PROC-007, US-POS-013, US-POS-014  
**API base:** `http://localhost:8081/api/v1`

---

### TC-CASH-001 — Cash entry created for each POS cash sale

| Field | Value |
|-------|-------|
| **ID** | TC-CASH-001 |
| **Title** | Each POS CASH payment creates a CASH_IN entry on the TILL account |
| **Area** | cash |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | Open till session. |
| **Steps** | 1. Post POS sale 1200 TZS CASH. 2. Query `cash_entry` for this session. |
| **Expected Result** | One CASH_IN entry for 1200 TZS on account=TILL, ref_type=PosSale, ref_id=<sale_id>. No card payment creates a cash_entry. |
| **Automatable?** | yes — integration test (`CashLedgerServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-CASH-002 — Cash ledger balance ties to sum of entries

| Field | Value |
|-------|-------|
| **ID** | TC-CASH-002 |
| **Title** | Cash book balance for a session = sum of all cash_entry rows for that session |
| **Area** | cash |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-016 |
| **Preconditions** | Session with: 3 cash sales (1200 each), 1 pickup (2000), 1 petty cash (500). |
| **Steps** | 1. `GET /api/v1/cash-ledger/session/<session_id>/balance`. 2. Manually compute: 3*1200 - 2000 - 500 = 1100. |
| **Expected Result** | Response balance = 1100 TZS. No rounding discrepancy. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-CASH-003 — Bank deposit records BANK_IN entry and cash_book debit

| Field | Value |
|-------|-------|
| **ID** | TC-CASH-003 |
| **Title** | POST /bank-deposits creates BANK_IN entry; CASH account debited |
| **Area** | cash |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-008 |
| **Preconditions** | SAFE account has balance. Admin logged in. |
| **Steps** | 1. `POST /api/v1/bank-deposits` body: `{"amount":50000,"bankAccountId":<id>,"reference":"DEP-001","depositDate":"2026-05-30"}`. |
| **Expected Result** | HTTP 201; cash_entry CASH_OUT from SAFE/COUNTER; corresponding bank credit; deposit reference stored. |
| **Automatable?** | yes — integration test (`BankDepositServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-CASH-004 — Supplier payment creates CASH_OUT entry

| Field | Value |
|-------|-------|
| **ID** | TC-CASH-004 |
| **Title** | POST /supplier-payments creates CASH_OUT from BANK account |
| **Area** | cash |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-007 |
| **Preconditions** | Supplier has outstanding debt. |
| **Steps** | 1. `POST /api/v1/supplier-payments` body with bank_transfer method. |
| **Expected Result** | HTTP 201; CASH_OUT entry from BANK; `supplier_payment` row created; debt_entry reduced. |
| **Automatable?** | yes — integration test (`SupplierPaymentServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-CASH-005 — Cash adjustment requires reason and CASH.ADJUST permission

| Field | Value |
|-------|-------|
| **ID** | TC-CASH-005 |
| **Title** | POST /cash-adjustments without CASH.ADJUST permission returns 403 |
| **Area** | cash |
| **Dimension** | SEC |
| **Priority** | P1 |
| **Linked US-*** | US-IAM-009 |
| **Preconditions** | User without CASH.ADJUST permission. |
| **Steps** | 1. POST /cash-adjustments. |
| **Expected Result** | HTTP 403. With correct permission: HTTP 201. |
| **Automatable?** | yes — integration test (`CashAdjustmentServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | |
