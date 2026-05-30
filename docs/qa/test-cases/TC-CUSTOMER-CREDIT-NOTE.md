# TC-CUSTOMER-CREDIT-NOTE — Customer Credit Note: Issue and Apply

**Module:** sales (customer-credit-note sub-area)
**Stories:** US-SALES-010, US-SALES-011
**API base:** `http://localhost:8081/api/v1`
**Permission gate:** `SALES.MANAGE_RETURN` (class-level on `CustomerReturnController`)

---

## Setup pattern (reused across cases)

The full setup chain is: create customer return (DRAFT) → post return → issue credit note → apply credit note to invoice.
All test codes MUST be unique per run — prefix with `CCN` + short entropy string (e.g. `CCN-A1`, `CCN-A2`).
Do NOT close the shared business day or archive seeded catalog/customer.

```bash
# Authenticate
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"rootadmin","password":"SKp315goPN8Nb0yJtMCCD7cm"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"

# Resolve seeded item id (e.g. COKE500) and invoice id
ITEM_ID=$(curl -s "http://localhost:8081/api/v1/items?q=COKE500" -H "$AUTH" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['content'][0]['id'])")
```

---

### TC-CUSTOMER-CREDIT-NOTE-001 — Full happy path: return → credit note → apply to invoice

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-001 |
| **Title** | Return posted + credit note issued + applied to open invoice reduces invoice outstanding and flips CN status to FULLY_ALLOCATED |
| **Area** | customer-credit-note |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-010, US-SALES-011 |
| **Preconditions** | 1. Business day OPEN for branch 1. 2. Seeded customer CUST0001 exists (id resolvable via `GET /api/v1/customers`). 3. A POSTED sales invoice exists for CUST0001 with `status=POSTED` and `paidAmount=0` (create one if needed via `POST /api/v1/sales-invoices` + post). 4. Seeded item COKE500 exists. 5. Admin token held. |
| **Steps** | 1. Resolve invoice uid and id: `GET /api/v1/sales-invoices?customerId=<customerId>` — pick one with `status=POSTED`; note `uid`, `id`, and `totalAmount`. 2. Create customer return draft: `POST /api/v1/customer-returns` body `{"number":"CCN-A1-RET","branchId":1,"customerId":<customerId>,"originalInvoiceId":<invoiceId>,"returnDate":"2026-05-30","reason":"WRONG_ITEM","restock":true,"lines":[{"itemId":<itemId>,"returnedQty":1,"unitPrice":1000}]}` — expect HTTP 201, `data.status="DRAFT"`. 3. Post the return: `POST /api/v1/customer-returns/uid/<returnUid>/post` — expect HTTP 200, `data.status="POSTED"`. 4. Issue credit note: `POST /api/v1/customer-returns/uid/<returnUid>/issue-credit-note` body `{"number":"CCN-A1-CN","notes":"Wrong item return"}` — expect HTTP 200, note `data.uid` as `<cnUid>` and `data.totalAmount` as `<cnTotal>`. 5. Apply credit note in full: `POST /api/v1/customer-credit-notes/uid/<cnUid>/apply` body `{"salesInvoiceUid":"<invoiceUid>","amount":<cnTotal>}` — expect HTTP 200. 6. Verify invoice outstanding: `GET /api/v1/sales-invoices/uid/<invoiceUid>` — check `paidAmount` increased by `<cnTotal>` and `status` is `PAID` (if fully covered) or `PARTIALLY_PAID`. 7. Verify CN status: from apply response `data.status="FULLY_ALLOCATED"`, `data.availableAmount=0`, `data.allocations` list length=1 with correct `salesInvoiceNumber`. |
| **Expected Result** | HTTP 200 on apply; `data.status="FULLY_ALLOCATED"`; `data.allocatedAmount=<cnTotal>`; `data.availableAmount=0`; `data.allocations[0].amount=<cnTotal>`; `data.allocations[0].salesInvoiceNumber` matches the target invoice number. Invoice `paidAmount` increased by `<cnTotal>`. Outbox row `CustomerCreditNoteApplied.v1` written in same TX. No cash movement. |
| **Automatable?** | yes — integration test (`CustomerReturnServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | P0 because this is the primary AR-reduction path. Missing outbox row = data corruption. |

---

### TC-CUSTOMER-CREDIT-NOTE-002 — Partial application leaves CN PARTIALLY_ALLOCATED and invoice balance correct

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-002 |
| **Title** | Applying half the credit note balance sets status=PARTIALLY_ALLOCATED and availableAmount equals remainder |
| **Area** | customer-credit-note |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-011 |
| **Preconditions** | 1. Business day OPEN. 2. Credit note exists with `totalAmount=2000`, `status=POSTED` (from a return of quantity=2 at unit price 1000). 3. Open invoice for same customer with `totalAmount>=2000`. |
| **Steps** | 1. `POST /api/v1/customer-credit-notes/uid/<cnUid>/apply` body `{"salesInvoiceUid":"<invoiceUid>","amount":1000}` — partial apply. 2. Capture response. 3. `GET /api/v1/customer-credit-notes` — find the CN and inspect fields. 4. `GET /api/v1/sales-invoices/uid/<invoiceUid>` — inspect `paidAmount`. |
| **Expected Result** | Step 1: HTTP 200; `data.status="PARTIALLY_ALLOCATED"`; `data.allocatedAmount=1000`; `data.availableAmount=1000`; `data.allocations` length=1. Step 3: same CN shows `availableAmount=1000`. Step 4: invoice `paidAmount` increased by 1000; `status="PARTIALLY_PAID"`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Also verifies that partial state is stable and allows a second apply call. |

---

### TC-CUSTOMER-CREDIT-NOTE-003 — Apply amount exceeds credit note available balance is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-003 |
| **Title** | POST apply with amount > availableAmount returns HTTP 400 and no allocation is persisted |
| **Area** | customer-credit-note |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-011 |
| **Preconditions** | 1. Credit note with `totalAmount=500`, `allocatedAmount=0`, `status=POSTED`. 2. Open invoice for same customer with `totalAmount>=1000`. |
| **Steps** | 1. `POST /api/v1/customer-credit-notes/uid/<cnUid>/apply` body `{"salesInvoiceUid":"<invoiceUid>","amount":501}`. 2. Attempt to apply 501 against a 500 note. |
| **Expected Result** | HTTP 400 (or 422); `errors` or `message` contains "exceeds available credit"; no `customer_credit_note_allocation` row created; `cn.allocatedAmount` unchanged at 0; invoice `paidAmount` unchanged. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard in `CustomerReturnServiceImpl.applyToInvoice` line ~304. P0: over-apply corrupts customer outstanding. |

---

### TC-CUSTOMER-CREDIT-NOTE-004 — Apply amount exceeds invoice outstanding balance is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-004 |
| **Title** | POST apply with amount > invoice outstanding returns HTTP 400 and no allocation is persisted |
| **Area** | customer-credit-note |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-011 |
| **Preconditions** | 1. Credit note with `totalAmount=5000`, `status=POSTED`. 2. Open invoice for same customer with `totalAmount=800`, `paidAmount=300` (outstanding=500). |
| **Steps** | 1. `POST /api/v1/customer-credit-notes/uid/<cnUid>/apply` body `{"salesInvoiceUid":"<invoiceUid>","amount":600}`. |
| **Expected Result** | HTTP 400 (or 422); message contains "exceeds invoice outstanding"; no allocation row; invoice `paidAmount` still 300; CN `allocatedAmount` unchanged. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard at `CustomerReturnServiceImpl.applyToInvoice` line ~329. P0: over-crediting the invoice creates negative outstanding. |

---

### TC-CUSTOMER-CREDIT-NOTE-005 — Apply against invoice belonging to a different customer is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-005 |
| **Title** | POST apply with a salesInvoiceUid that belongs to a different customer returns HTTP 400 |
| **Area** | customer-credit-note |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-011 |
| **Preconditions** | 1. Credit note issued for customer A (CUST0001). 2. A POSTED invoice exists for customer B (a different customer — create one or locate one). |
| **Steps** | 1. `POST /api/v1/customer-credit-notes/uid/<cnUidForCustA>/apply` body `{"salesInvoiceUid":"<invoiceUidForCustB>","amount":100}`. |
| **Expected Result** | HTTP 400 (or 422); message contains "different customer"; no allocation row; both CN and invoice state unchanged. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard at `CustomerReturnServiceImpl.applyToInvoice` line ~317. Cross-customer allocation would corrupt two customers' AR simultaneously. |

---

### TC-CUSTOMER-CREDIT-NOTE-006 — Apply to a DRAFT or VOIDED invoice is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-006 |
| **Title** | POST apply against an invoice not in POSTED or PARTIALLY_PAID status is rejected with HTTP 400 |
| **Area** | customer-credit-note |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-011 |
| **Preconditions** | 1. Credit note with `totalAmount=500`, `status=POSTED`. 2. An invoice for the same customer in `DRAFT` status (not yet posted). |
| **Steps** | 1. `POST /api/v1/customer-credit-notes/uid/<cnUid>/apply` body `{"salesInvoiceUid":"<draftInvoiceUid>","amount":200}`. |
| **Expected Result** | HTTP 400 (or 422); message contains the invoice status and "only POSTED or PARTIALLY_PAID invoices can receive"; no allocation row created. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard at `CustomerReturnServiceImpl.applyToInvoice` line ~321. Also applies if status is PAID, VOIDED, CANCELLED. |

---

### TC-CUSTOMER-CREDIT-NOTE-007 — Apply to a FULLY_ALLOCATED credit note is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-007 |
| **Title** | POST apply against a FULLY_ALLOCATED credit note returns HTTP 400 (double-apply rejection) |
| **Area** | customer-credit-note |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-011 |
| **Preconditions** | 1. Credit note with `totalAmount=1000`, previously fully applied (status=FULLY_ALLOCATED, allocatedAmount=1000). 2. A second open invoice for the same customer. |
| **Steps** | 1. `POST /api/v1/customer-credit-notes/uid/<cnUid>/apply` body `{"salesInvoiceUid":"<secondInvoiceUid>","amount":1}`. |
| **Expected Result** | HTTP 400 (or 422); message includes "FULLY_ALLOCATED" or "only POSTED or PARTIALLY_ALLOCATED notes can be applied"; no allocation row added; second invoice `paidAmount` unchanged. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard at `CustomerReturnServiceImpl.applyToInvoice` line ~295. Prevents double-spending a depleted credit note. |

---

### TC-CUSTOMER-CREDIT-NOTE-008 — Zero and negative amount payloads rejected by bean validation

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-008 |
| **Title** | POST apply with amount=0 or amount=-1 returns HTTP 400 from bean validation before service logic |
| **Area** | customer-credit-note |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-011 |
| **Preconditions** | Any POSTED credit note and any open invoice for the same customer. |
| **Steps** | 1. `POST /api/v1/customer-credit-notes/uid/<cnUid>/apply` body `{"salesInvoiceUid":"<invoiceUid>","amount":0}`. 2. Repeat with `"amount":-50`. 3. Repeat with no `amount` field (null). |
| **Expected Result** | All three calls: HTTP 400; `errors` array contains a constraint-violation message referencing `amount` (`@DecimalMin("0.01")`). No allocation rows created. |
| **Automatable?** | yes — unit test (MockMvc bean-validation layer) |
| **Result/Status** | |
| **Notes/IssueRef** | `ApplyCreditNoteRequestDto` carries `@NotNull @DecimalMin("0.01")` on `amount`. |

---

### TC-CUSTOMER-CREDIT-NOTE-009 — Unauthenticated request is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-009 |
| **Title** | POST apply without Authorization header returns HTTP 401 |
| **Area** | customer-credit-note |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-011 |
| **Preconditions** | Any valid credit note uid. |
| **Steps** | 1. `POST /api/v1/customer-credit-notes/uid/<anyValidCnUid>/apply` with no `Authorization` header and body `{"salesInvoiceUid":"<anyUid>","amount":100}`. |
| **Expected Result** | HTTP 401; no allocation row created. |
| **Automatable?** | yes — integration test (Spring Security filter) |
| **Result/Status** | |
| **Notes/IssueRef** | Also verify `GET /api/v1/customer-credit-notes` and `POST /customer-returns/uid/{uid}/issue-credit-note` return 401 without token. |

---

### TC-CUSTOMER-CREDIT-NOTE-010 — User without SALES.MANAGE_RETURN is forbidden

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-010 |
| **Title** | POST apply with a token that lacks SALES.MANAGE_RETURN returns HTTP 403 |
| **Area** | customer-credit-note |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-011 |
| **Preconditions** | A second user exists with a role that does NOT include `SALES.MANAGE_RETURN` (e.g. a read-only viewer or POS cashier). Use cashier credentials: `cashier` / `Cashier#2026`. |
| **Steps** | 1. Obtain cashier token: `POST /api/v1/auth/login` with `{"username":"cashier","password":"Cashier#2026"}`. 2. `POST /api/v1/customer-credit-notes/uid/<anyCnUid>/apply` using cashier token, body `{"salesInvoiceUid":"<anyUid>","amount":100}`. |
| **Expected Result** | HTTP 403; no allocation row created. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `@PreAuthorize("hasAuthority('SALES.MANAGE_RETURN')")` is class-level on `CustomerReturnController`. |

---

### TC-CUSTOMER-CREDIT-NOTE-011 — Cross-tenant credit note uid rejected (tenant isolation)

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-011 |
| **Title** | POST apply using a credit note uid from a different company returns 404 (not 400 or 500) |
| **Area** | customer-credit-note |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-011 |
| **Preconditions** | Requires two companies in the QA container. If only one company exists, mark BLOCKED(needs-exclusive-state). Otherwise: obtain a credit note uid that belongs to company B while authenticated as company A admin. |
| **Steps** | 1. Authenticate as company A admin. 2. `POST /api/v1/customer-credit-notes/uid/<cnUidFromCompanyB>/apply` body `{"salesInvoiceUid":"<anyUid>","amount":100}`. |
| **Expected Result** | HTTP 404; `message` contains "Credit note not found" (no cross-tenant uid leakage in error text); no allocation row created; company B's CN is unaffected. |
| **Automatable?** | yes — integration test (Testcontainers, two company seed) |
| **Result/Status** | BLOCKED(needs-exclusive-state) |
| **Notes/IssueRef** | `requireCreditNoteByUid` enforces `companyId` match and throws `NoSuchElementException` which maps to 404. |

---

### TC-CUSTOMER-CREDIT-NOTE-012 — Invoice outstanding and debt ageing bucket reflect allocation

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-012 |
| **Title** | After credit note applied, debt ageing report shows reduced outstanding in the correct bucket |
| **Area** | customer-credit-note |
| **Dimension** | INT |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-011, US-DEBT-003 |
| **Preconditions** | 1. Customer has one invoice with `totalAmount=3000`, `dueDate` at least 40 days in the past (falls in 31-60 bucket). 2. Credit note with `totalAmount=1000`, `status=POSTED`, for same customer. |
| **Steps** | 1. `GET /api/v1/debt/ageing?type=RECEIVABLE` before apply — record `d31_60` bucket value for the customer. 2. `POST /api/v1/customer-credit-notes/uid/<cnUid>/apply` body `{"salesInvoiceUid":"<invoiceUid>","amount":1000}`. 3. `GET /api/v1/debt/ageing?type=RECEIVABLE` after apply — compare bucket values. 4. `GET /api/v1/debt/statement/uid/<customerUid>` — inspect `totalOutstanding` and `openInvoices[0].amountDue`. |
| **Expected Result** | Step 3: customer `d31_60` bucket value decreases by 1000 (from 3000 to 2000). `totalOutstanding` for customer decreases by 1000. Step 4: `totalOutstanding=2000`; `openInvoices[0].amountDue=2000` (outstanding = totalAmount - paidAmount = 3000 - 1000). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Ageing is computed from `invoice.paidAmount` in-memory at query time (no separate debt_entry for CN allocations). Verify the read model is not stale-cached. |

---

### TC-CUSTOMER-CREDIT-NOTE-013 — Credit note number uniqueness per branch enforced

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-013 |
| **Title** | Issuing two credit notes with the same number in the same branch returns HTTP 400 on the second |
| **Area** | customer-credit-note |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-010 |
| **Preconditions** | 1. Two POSTED customer returns exist for the same branch (returns must be separate so each can issue a CN). 2. First CN with number `CCN-DUP-01` already issued. |
| **Steps** | 1. `POST /api/v1/customer-returns/uid/<return1Uid>/issue-credit-note` body `{"number":"CCN-DUP-01","notes":"first"}` — expect HTTP 200. 2. `POST /api/v1/customer-returns/uid/<return2Uid>/issue-credit-note` body `{"number":"CCN-DUP-01","notes":"second attempt"}`. |
| **Expected Result** | Step 2: HTTP 400 (or 422); `message` contains "Credit-note number already exists for this branch"; no second `customer_credit_note` row created; DB has exactly one row with `number=CCN-DUP-01` for that branch. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Uniqueness enforced at service layer (`creditNotes.existsByBranchIdAndNumber`) and DB constraint `uk_customer_credit_note_branch_number`. |

---

### TC-CUSTOMER-CREDIT-NOTE-014 — Multiple partial applies accumulate correctly and final apply closes CN

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-014 |
| **Title** | Three sequential partial applies sum to totalAmount; final apply flips status to FULLY_ALLOCATED atomically |
| **Area** | customer-credit-note |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-011 |
| **Preconditions** | 1. Credit note with `totalAmount=3000`, `status=POSTED`. 2. Three open invoices for same customer each with outstanding >= 1000. |
| **Steps** | 1. Apply 1000 to invoice 1: `POST /api/v1/customer-credit-notes/uid/<cnUid>/apply` body `{"salesInvoiceUid":"<inv1Uid>","amount":1000}` — response `status=PARTIALLY_ALLOCATED`, `allocatedAmount=1000`, `availableAmount=2000`. 2. Apply 1000 to invoice 2 — response `status=PARTIALLY_ALLOCATED`, `allocatedAmount=2000`, `availableAmount=1000`. 3. Apply 1000 to invoice 3 — response `status=FULLY_ALLOCATED`, `allocatedAmount=3000`, `availableAmount=0`. 4. Confirm `data.allocations` length=3, each with distinct `salesInvoiceNumber`. |
| **Expected Result** | After step 3: `status=FULLY_ALLOCATED`; `allocatedAmount=3000`; `availableAmount=0`; `allocations` array contains 3 rows ordered by `allocatedAt` ascending; three outbox events `CustomerCreditNoteApplied.v1` written (one per apply). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Tests that `@Version` optimistic-lock does not interfere with sequential calls and that each apply increments `allocatedAmount` additively. |

---

### TC-CUSTOMER-CREDIT-NOTE-015 — GET /customer-credit-notes detail endpoint not exposed (gap note)

| Field | Value |
|-------|-------|
| **ID** | TC-CUSTOMER-CREDIT-NOTE-015 |
| **Title** | No GET /customer-credit-notes/uid/{uid} endpoint exists — single-CN detail is not reachable via the API |
| **Area** | customer-credit-note |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | US-SALES-011 |
| **Preconditions** | Any valid credit note uid. |
| **Steps** | 1. `GET /api/v1/customer-credit-notes/uid/<cnUid>` with admin auth. |
| **Expected Result** | HTTP 404 (no route matched). The service has `getCreditNote(String uid)` implemented but `CustomerReturnController` does not expose a `@GetMapping("/customer-credit-notes/uid/{uid}")` handler. The apply response returns the hydrated DTO as a workaround. |
| **Automatable?** | yes — simple HTTP probe |
| **Result/Status** | |
| **Notes/IssueRef** | Implementation gap: `CustomerReturnService.getCreditNote` exists but has no controller mapping. File as a P2 feature-gap issue. Web UI cannot independently refresh a single CN without re-calling apply or listing all. concurrencyRisk=possible (gap may be intentional design — confirm with product). |
