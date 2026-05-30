# TC-VENDOR-RETURN — Vendor Returns and Vendor Credit Notes

**Module:** procurement  
**Stories:** US-PROC-008, US-PROC-009  
**API base:** `http://localhost:8081/api/v1`  
**Permission gate:** `PROCUREMENT.MANAGE_RETURN` (class-level `@PreAuthorize` on all endpoints)

> **Setup note.** Every case that exercises stock moves or credit-note apply requires a POSTED GRN and a POSTED SupplierInvoice. Use the seeded supplier from `seed-dev-data.local.sh`, or create a fresh one with a unique code (`VR-SUP-<entropy>`). Use unique `number` fields (prefix `VRN-<entropy>`) to avoid the branch-scoped unique constraint.

---

### TC-VENDOR-RETURN-001 — Create DRAFT vendor return with restock=true

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-001 |
| **Title** | POST /vendor-returns creates a DRAFT vendor return; totalAmount rolls up from lines with VAT |
| **Area** | vendor-return |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | 1. Logged in as rootadmin (has `PROCUREMENT.MANAGE_RETURN`). 2. A posted GRN exists for supplier S1 with item COKE500 (resolve itemId via `GET /api/v1/items?q=COKE500`). 3. Branch HQ id=1. |
| **Steps** | 1. Resolve item: `GET /api/v1/items?q=COKE500` — note `data.content[0].id` as `<itemId>`, `data.content[0].vatGroupId` as `<vatGroupId>`. 2. `POST /api/v1/vendor-returns` body: `{"number":"VRN-001-A","branchId":1,"supplierId":<supplierId>,"returnDate":"2026-05-30","reason":"DAMAGED","restock":true,"lines":[{"itemId":<itemId>,"returnedQty":5,"unitPrice":800,"vatGroupId":<vatGroupId>}]}`. 3. `GET /api/v1/vendor-returns/uid/<uid>`. |
| **Expected Result** | Step 2: HTTP 201; `data.status = "DRAFT"`; `data.restock = true`; `data.totalAmount` = 5 × 800 × (1 + vatRate) scaled to 4 dp (e.g. for 18% VAT: 5 × 800 = 4000 net + 720 tax = 4720.0000); `data.lines` has 1 entry with `lineTotal = 4720.0000`, `taxAmount = 720.0000`. Step 3: GET 200, same shape. |
| **Automatable?** | yes — integration test (Testcontainers) |
| **Result/Status** | |
| **Notes/IssueRef** | All Long `id` / `*Id` fields serialize as JSON strings per JacksonConfig. |

---

### TC-VENDOR-RETURN-002 — Post vendor return (restock=true) emits RETURN_OUT stock move

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-002 |
| **Title** | POST /vendor-returns/uid/{uid}/post transitions status to POSTED and posts a RETURN_OUT stock move that decrements on-hand |
| **Area** | vendor-return |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | 1. Vendor return VRN-002-A exists in DRAFT, branchId=1, restock=true, 5 units COKE500 at 800. 2. Stock on-hand for COKE500 at branch 1 is >= 5 (satisfied after the seeded GRN). |
| **Steps** | 1. Note stock balance: `GET /api/v1/stock/balance?itemId=<itemId>&branchId=1` — capture `onHand` as `<before>`. 2. `POST /api/v1/vendor-returns/uid/<uid>/post`. 3. Re-read balance: `GET /api/v1/stock/balance?itemId=<itemId>&branchId=1`. 4. Query stock moves: `GET /api/v1/stock/moves?referenceType=VendorReturn&referenceId=<returnId>`. |
| **Expected Result** | Step 2: HTTP 200; `data.status = "POSTED"`; `data.postedAt` is non-null. Step 3: `onHand = <before> - 5`. Step 4: one move with `moveType = "RETURN_OUT"`, `qty = -5` (negative, indicating deduction), `referenceType = "VendorReturn"`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Service code: `StockMoveType.RETURN_OUT` when `restock=true`; qty negated before posting. |

---

### TC-VENDOR-RETURN-003 — Post vendor return (restock=false) emits DAMAGE stock move

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-003 |
| **Title** | POST /vendor-returns/uid/{uid}/post with restock=false posts a DAMAGE stock move (write-off, not RETURN_OUT) |
| **Area** | vendor-return |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | 1. Vendor return VRN-003-A in DRAFT, restock=false, 3 units COKE500 at 800. 2. Stock on-hand >= 3. |
| **Steps** | 1. `POST /api/v1/vendor-returns/uid/<uid>/post`. 2. `GET /api/v1/stock/moves?referenceType=VendorReturn&referenceId=<returnId>`. |
| **Expected Result** | Step 1: HTTP 200; status = "POSTED". Step 2: move `moveType = "DAMAGE"`, `qty = -3`; no RETURN_OUT move generated. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Business rule: damaged goods were never in sellable stock — write-off path, not supplier-return path. |

---

### TC-VENDOR-RETURN-004 — Issue credit note from POSTED return; return transitions to CREDITED

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-004 |
| **Title** | POST /vendor-returns/uid/{uid}/issue-credit-note creates VendorCreditNote in POSTED status with totalAmount matching the return |
| **Area** | vendor-return |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-008 US-PROC-009 |
| **Preconditions** | 1. Vendor return VRN-004-A in POSTED status, totalAmount = 4720.0000 TZS. |
| **Steps** | 1. `POST /api/v1/vendor-returns/uid/<uid>/issue-credit-note` body: `{"number":"VCN-004-A","notes":"damaged batch credit"}`. 2. Note returned `data.uid` as `<cnUid>`. 3. `GET /api/v1/vendor-returns/uid/<returnUid>`. 4. `GET /api/v1/vendor-credit-notes`. |
| **Expected Result** | Step 1: HTTP 200; `data.status = "POSTED"`; `data.totalAmount = 4720.0000`; `data.allocatedAmount = 0`; `data.availableAmount = 4720.0000`; `data.vendorReturnId` = returnId; `data.supplierId` = supplierId; `data.currencyCode = "TZS"`. Step 3: vendor return `status = "CREDITED"`. Step 4: new credit note appears in list. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Currency falls back to TZS when no supplier invoice is linked; when linked it inherits the invoice currency. |

---

### TC-VENDOR-RETURN-005 — Apply credit note to open supplier invoice; invoice outstanding decreases

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-005 |
| **Title** | POST /vendor-credit-notes/uid/{uid}/apply reduces invoice outstanding and sets credit-note status to FULLY_ALLOCATED when amount equals available balance |
| **Area** | vendor-return |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-009 |
| **Preconditions** | 1. VendorCreditNote VCN-005-A in POSTED status, totalAmount=4720, allocatedAmount=0. 2. SupplierInvoice SI-005-A in POSTED status for the SAME supplier, totalAmount >= 4720, paidAmount=0. Resolve `<siUid>` via `GET /api/v1/supplier-invoices/uid/<siUid>`. |
| **Steps** | 1. Note invoice outstanding: `GET /api/v1/supplier-invoices/uid/<siUid>` — record `outstandingAmount` as `<before>`. 2. `POST /api/v1/vendor-credit-notes/uid/<cnUid>/apply` body: `{"supplierInvoiceUid":"<siUid>","amount":4720.00}`. 3. `GET /api/v1/supplier-invoices/uid/<siUid>`. |
| **Expected Result** | Step 2: HTTP 200; `data.status = "FULLY_ALLOCATED"`; `data.allocatedAmount = 4720`; `data.availableAmount = 0`; `data.allocations` array has 1 entry with `supplierInvoiceId` matching SI-005-A and `amount = 4720`. Step 3: `paidAmount` increased by 4720; `status` = "PAID" (if total settled) or "PARTIALLY_PAID". |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | ADR-0004 sync-TX exemption #23: credit-note allocation + invoice paidAmount update in same DB transaction. |

---

### TC-VENDOR-RETURN-006 — Partial application leaves credit note in PARTIALLY_ALLOCATED

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-006 |
| **Title** | Apply credit note for less than its available balance transitions status to PARTIALLY_ALLOCATED and leaves remaining credit |
| **Area** | vendor-return |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-009 |
| **Preconditions** | 1. VendorCreditNote VCN-006-A in POSTED status, totalAmount=4720, allocatedAmount=0. 2. SupplierInvoice SI-006-A in POSTED status for same supplier, totalAmount >= 4720. |
| **Steps** | 1. `POST /api/v1/vendor-credit-notes/uid/<cnUid>/apply` body: `{"supplierInvoiceUid":"<siUid>","amount":2000.00}`. 2. `POST /api/v1/vendor-credit-notes/uid/<cnUid>/apply` body: `{"supplierInvoiceUid":"<siUid>","amount":2720.00}`. |
| **Expected Result** | Step 1: HTTP 200; `data.status = "PARTIALLY_ALLOCATED"`; `data.allocatedAmount = 2000`; `data.availableAmount = 2720`. Step 2: HTTP 200; `data.status = "FULLY_ALLOCATED"`; `data.allocatedAmount = 4720`; `data.availableAmount = 0`; `data.allocations` has 2 entries. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Second apply hits PARTIALLY_ALLOCATED → FULLY_ALLOCATED transition. |

---

### TC-VENDOR-RETURN-007 — Cancel DRAFT vendor return; stock move NOT created

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-007 |
| **Title** | POST /vendor-returns/uid/{uid}/cancel on a DRAFT return transitions to CANCELLED and does not post any stock move |
| **Area** | vendor-return |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | 1. Vendor return VRN-007-A in DRAFT status, 5 units COKE500. 2. Note on-hand balance before. |
| **Steps** | 1. `POST /api/v1/vendor-returns/uid/<uid>/cancel`. 2. `GET /api/v1/stock/moves?referenceType=VendorReturn&referenceId=<returnId>`. 3. `GET /api/v1/stock/balance?itemId=<itemId>&branchId=1`. |
| **Expected Result** | Step 1: HTTP 200; `data.status = "CANCELLED"`. Step 2: empty list (no stock moves generated). Step 3: on-hand unchanged from pre-cancel balance. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `cancel()` only allowed from DRAFT — see entity state-machine. |

---

### TC-VENDOR-RETURN-008 — Cannot post a CANCELLED return

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-008 |
| **Title** | POST /post on a CANCELLED vendor return is rejected with 409/4xx (IllegalStateException) |
| **Area** | vendor-return |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | Vendor return VRN-008-A in CANCELLED status. |
| **Steps** | 1. `POST /api/v1/vendor-returns/uid/<uid>/post`. |
| **Expected Result** | HTTP 4xx (expect 409 or 400); `data` null or absent; response body contains error message indicating status is CANCELLED not DRAFT. No stock move created. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `requireStatus(DRAFT)` throws `IllegalStateException` — map to 409 in GlobalExceptionHandler. Verify handler maps correctly. |

---

### TC-VENDOR-RETURN-009 — Cannot issue credit note from a DRAFT return (must post first)

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-009 |
| **Title** | POST /issue-credit-note on a DRAFT vendor return is rejected; credit note requires POSTED status |
| **Area** | vendor-return |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | Vendor return VRN-009-A in DRAFT status. |
| **Steps** | 1. `POST /api/v1/vendor-returns/uid/<uid>/issue-credit-note` body: `{"number":"VCN-009-A"}`. |
| **Expected Result** | HTTP 4xx; error mentions return is DRAFT not POSTED. No `vendor_credit_note` row created. |
| **Automatable?** | yes — unit test (VendorReturnServiceImplTest) |
| **Result/Status** | |
| **Notes/IssueRef** | `markCredited()` calls `requireStatus(POSTED)` internally. |

---

### TC-VENDOR-RETURN-010 — Cannot apply credit note for amount exceeding available balance

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-010 |
| **Title** | POST /apply with amount > availableAmount returns 400 and does not mutate credit note or invoice |
| **Area** | vendor-return |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-009 |
| **Preconditions** | VendorCreditNote VCN-010-A in POSTED status, totalAmount=4720, allocatedAmount=0 (available=4720). SupplierInvoice SI-010-A posted for same supplier, outstanding >= 5000. |
| **Steps** | 1. `POST /api/v1/vendor-credit-notes/uid/<cnUid>/apply` body: `{"supplierInvoiceUid":"<siUid>","amount":5000.00}`. 2. `GET /api/v1/vendor-credit-notes` — check VCN-010-A. 3. `GET /api/v1/supplier-invoices/uid/<siUid>`. |
| **Expected Result** | Step 1: HTTP 400; error: "Apply amount 5000 exceeds available credit 4720". Step 2: `allocatedAmount` still 0, status still "POSTED". Step 3: invoice `paidAmount` unchanged. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard in `applyToInvoice`: `req.amount().compareTo(available) > 0`. |

---

### TC-VENDOR-RETURN-011 — Cannot apply credit note for amount exceeding invoice outstanding

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-011 |
| **Title** | POST /apply with amount > invoice outstanding returns 400 even when credit note has sufficient balance |
| **Area** | vendor-return |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-009 |
| **Preconditions** | VendorCreditNote VCN-011-A POSTED, totalAmount=10000. SupplierInvoice SI-011-A POSTED, totalAmount=2000, paidAmount=0 (outstanding=2000). |
| **Steps** | 1. `POST /api/v1/vendor-credit-notes/uid/<cnUid>/apply` body: `{"supplierInvoiceUid":"<siUid>","amount":3000.00}`. |
| **Expected Result** | HTTP 400; error: "Apply amount 3000 exceeds invoice outstanding 2000". Credit note allocatedAmount unchanged. Invoice paidAmount unchanged. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Second over-limit guard in `applyToInvoice`: `req.amount().compareTo(invoiceOutstanding) > 0`. |

---

### TC-VENDOR-RETURN-012 — Cannot apply credit note to invoice belonging to different supplier

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-012 |
| **Title** | POST /apply with a supplier invoice from a different supplier is rejected with 400 |
| **Area** | vendor-return |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-009 |
| **Preconditions** | 1. VendorCreditNote VCN-012-A for supplier S1 (POSTED). 2. SupplierInvoice SI-012-A in POSTED status for supplier S2 (different supplierId). |
| **Steps** | 1. `POST /api/v1/vendor-credit-notes/uid/<cnUid>/apply` body: `{"supplierInvoiceUid":"<siS2Uid>","amount":100.00}`. |
| **Expected Result** | HTTP 400; error: "Supplier invoice belongs to a different supplier than the credit note". No allocation row created. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard: `invoice.getSupplierId()` vs `cn.getSupplierId()`. |

---

### TC-VENDOR-RETURN-013 — Duplicate vendor return number within branch is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-013 |
| **Title** | POST /vendor-returns with a number already used by the same branch returns 400 |
| **Area** | vendor-return |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | Vendor return with number "VRN-DUP-001" already exists for branchId=1. |
| **Steps** | 1. `POST /api/v1/vendor-returns` body: same payload as the first, number="VRN-DUP-001", branchId=1. |
| **Expected Result** | HTTP 400; error: "Vendor return number already exists for this branch: VRN-DUP-001". |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Unique constraint `uk_vendor_return_branch_number(branch_id, number)`. Number is trimmed and upper-cased before the check. |

---

### TC-VENDOR-RETURN-014 — Batch-tracked item blocks post

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-014 |
| **Title** | POST /post with a batch-tracked item on a line is rejected with 400 (Slice H.1 limitation) |
| **Area** | vendor-return |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | 1. An item with `batchTracked=true` exists (create via catalog if not seeded). 2. Vendor return VRN-014-A in DRAFT with that item on a line. |
| **Steps** | 1. `POST /api/v1/vendor-returns/uid/<uid>/post`. |
| **Expected Result** | HTTP 400; error contains "batch-tracked items" and "Slice H.1". No stock move created. VendorReturn status remains DRAFT. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Explicit limitation in `VendorReturnServiceImpl.post()`: batch-routed returns deferred to a later slice. |

---

### TC-VENDOR-RETURN-015 — POST /vendor-returns without permission returns 403

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-015 |
| **Title** | Any /vendor-returns or /vendor-credit-notes endpoint returns 403 for a user without PROCUREMENT.MANAGE_RETURN |
| **Area** | vendor-return |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | 1. User `vr-noauth-<entropy>` exists with a role that has NO `PROCUREMENT.MANAGE_RETURN` permission (e.g. POS_CASHIER). Token acquired for that user. |
| **Steps** | 1. `POST /api/v1/auth/login` body: `{"username":"cashier","password":"Cashier#2026"}` — capture token. 2. `GET /api/v1/vendor-returns` with `Authorization: Bearer <token>`. 3. `POST /api/v1/vendor-returns` with that token, valid body. 4. `GET /api/v1/vendor-credit-notes` with that token. 5. `POST /api/v1/vendor-credit-notes/uid/01AAAAAAAAAAAAAAAAAAAAAAAAA/apply` with that token, valid body. |
| **Expected Result** | Steps 2–5: all return HTTP 403. No vendor return or credit note created. Response body contains `status: "FORBIDDEN"` or equivalent error structure. |
| **Automatable?** | yes — integration test (Spring Security test slice) |
| **Result/Status** | |
| **Notes/IssueRef** | Class-level `@PreAuthorize("hasAuthority('PROCUREMENT.MANAGE_RETURN')")`. The seeded `cashier` user has `POS_CASHIER` role which must NOT carry this permission. |

---

### TC-VENDOR-RETURN-016 — Unauthenticated request returns 401

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-016 |
| **Title** | Requests to /vendor-returns with no Authorization header return 401 |
| **Area** | vendor-return |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | None. |
| **Steps** | 1. `GET /api/v1/vendor-returns` — no Authorization header. 2. `POST /api/v1/vendor-returns` — no Authorization header. |
| **Expected Result** | Both return HTTP 401. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Standard JWT filter chain. |

---

### TC-VENDOR-RETURN-017 — Cross-tenant isolation: cannot access another company's vendor return

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-017 |
| **Title** | GET /vendor-returns/uid/{uid} for a return belonging to company C2 returns 404 when called as company C1 user |
| **Area** | vendor-return |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | Two companies C1, C2 with distinct rootadmin users. Vendor return VR-C2 created under C2. |
| **Steps** | 1. Authenticate as C1 rootadmin. 2. `GET /api/v1/vendor-returns/uid/<vr-c2-uid>`. |
| **Expected Result** | HTTP 404 (not 403 — uid must not leak existence across tenant boundary). Response: "Vendor return not found: <uid>". |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `requireReturnByUid` checks `ret.getCompanyId() == context.companyId()` and throws `NoSuchElementException` (404) rather than 403 to avoid tenant discovery. Same pattern for credit note apply cross-tenant. |
| **concurrencyRisk** | possible — shares live QA container; must use isolated company if testing on shared instance |

---

### TC-VENDOR-RETURN-018 — Credit note number uniqueness within branch enforced

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-018 |
| **Title** | POST /issue-credit-note with a number already used by another credit note in the same branch returns 400 |
| **Area** | vendor-return |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | Credit note with number "VCN-DUP-001" already exists for branchId=1. A second POSTED vendor return VRN-018-B exists for the same branch. |
| **Steps** | 1. `POST /api/v1/vendor-returns/uid/<vrnB-uid>/issue-credit-note` body: `{"number":"VCN-DUP-001"}`. |
| **Expected Result** | HTTP 400; error: "Credit-note number already exists for this branch: VCN-DUP-001". |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `creditNotes.existsByBranchIdAndNumber(branchId, number)` guard in `issueCreditNote`. Number trimmed + upper-cased. |

---

### TC-VENDOR-RETURN-019 — totalAmount calculation precision (multi-line, tax-inclusive VAT)

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-019 |
| **Title** | totalAmount and taxAmount on a multi-line vendor return are computed to 4 decimal places using HALF_UP rounding |
| **Area** | vendor-return |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | Logged in as rootadmin. STD18 VAT group (18% rate) and EXEMPT (0%) seeded. Two items available: COKE500 (STD18) and MATCHES (EXEMPT). |
| **Steps** | 1. `POST /api/v1/vendor-returns` body: `{"number":"VRN-019-PREC","branchId":1,"supplierId":<id>,"returnDate":"2026-05-30","reason":"WRONG_ITEM","restock":true,"lines":[{"itemId":<cokeId>,"returnedQty":3,"unitPrice":850,"vatGroupId":<std18Id>},{"itemId":<matchesId>,"returnedQty":7,"unitPrice":150,"vatGroupId":<exemptId>}]}`. 2. Read `data.lines[0]` and `data.lines[1]`. |
| **Expected Result** | Line 0 (COKE500): net = 3 × 850 = 2550.0000; taxAmount = 2550 × 0.18 = 459.0000; lineTotal = 3009.0000. Line 1 (MATCHES, 0% VAT): net = 7 × 150 = 1050.0000; taxAmount = 0.0000; lineTotal = 1050.0000. `data.totalAmount = 4059.0000`. All values serialized as JSON numbers (not strings — these are decimals, not id/uid fields). |
| **Automatable?** | yes — unit test (VendorReturnServiceImplTest) + JSON shape pinned in VendorReturnDtoJsonTest |
| **Result/Status** | |
| **Notes/IssueRef** | MONEY_SCALE = 4, RoundingMode.HALF_UP in saveLinesAndRollUp. |

---

### TC-VENDOR-RETURN-020 — Apply to CANCELLED supplier invoice is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-020 |
| **Title** | POST /apply against a CANCELLED or PAID supplier invoice returns 400 |
| **Area** | vendor-return |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-009 |
| **Preconditions** | 1. VendorCreditNote VCN-020-A POSTED with available balance. 2. SupplierInvoice SI-020-CANCELLED in CANCELLED status for same supplier. |
| **Steps** | 1. `POST /api/v1/vendor-credit-notes/uid/<cnUid>/apply` body: `{"supplierInvoiceUid":"<cancelledSiUid>","amount":100.00}`. |
| **Expected Result** | HTTP 400; error: "Supplier invoice ... is CANCELLED; only POSTED or PARTIALLY_PAID invoices can receive a credit-note apply". Credit note unchanged. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard: `invoice.getStatus() != POSTED && invoice.getStatus() != PARTIALLY_PAID`. Same check blocks DRAFT invoices. |

---

### TC-VENDOR-RETURN-021 — Cannot apply a FULLY_ALLOCATED credit note

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-021 |
| **Title** | POST /apply on a FULLY_ALLOCATED credit note returns 400 |
| **Area** | vendor-return |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-009 |
| **Preconditions** | VendorCreditNote VCN-021-A in FULLY_ALLOCATED status (already fully applied). SupplierInvoice SI-021-A POSTED for same supplier. |
| **Steps** | 1. `POST /api/v1/vendor-credit-notes/uid/<cnUid>/apply` body: `{"supplierInvoiceUid":"<siUid>","amount":0.01}`. |
| **Expected Result** | HTTP 400 or 409; error: "Credit note ... is FULLY_ALLOCATED; only POSTED or PARTIALLY_ALLOCATED notes can be applied". No allocation row created. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard: `cn.getStatus() != POSTED && cn.getStatus() != PARTIALLY_ALLOCATED`. |

---

### TC-VENDOR-RETURN-022 — Vendor return linked to supplier invoice validates supplier consistency

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-022 |
| **Title** | POST /vendor-returns with originalSupplierInvoiceId belonging to a different supplier returns 400 |
| **Area** | vendor-return |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | SupplierInvoice SI-022-A belongs to supplier S1. Request uses supplierId=S2 (different supplier). |
| **Steps** | 1. `POST /api/v1/vendor-returns` body: `{"number":"VRN-022-A","branchId":1,"supplierId":<s2Id>,"originalSupplierInvoiceId":<s1InvoiceId>,"returnDate":"2026-05-30","reason":"OTHER","restock":true,"lines":[{"itemId":<itemId>,"returnedQty":1,"unitPrice":100}]}`. |
| **Expected Result** | HTTP 400; error: "Original invoice belongs to a different supplier". |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Guard in `createDraft`: `invoice.getSupplierId()` vs `request.supplierId()`. |

---

### TC-VENDOR-RETURN-023 — Vendor return web form uses name pickers; no raw ids

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-023 |
| **Title** | Vendor return creation form and credit note apply form show name/code pickers; no raw supplierId, itemId, vendorReturnId, or supplierInvoiceUid inputs |
| **Area** | vendor-return / web |
| **Dimension** | UX |
| **Priority** | P0 |
| **Linked US-*** | US-PROC-008 US-PROC-009 |
| **Preconditions** | Web app at http://localhost:8081/ logged in as rootadmin. |
| **Steps** | 1. Navigate to Procurement > Vendor Returns > New. 2. Inspect Supplier field. 3. Inspect item fields on each line. 4. Navigate to apply credit note flow — inspect Supplier Invoice reference field. 5. Run axe-core on both pages. |
| **Expected Result** | All reference fields show name/code pickers (typeahead or modal). No numeric `id` inputs visible. axe-core: 0 violations (WCAG AA). |
| **Automatable?** | yes — Playwright e2e + axe |
| **Result/Status** | |
| **Notes/IssueRef** | CLAUDE.md no-raw-id convention (feedback-no-raw-id-uid-entries). |

---

### TC-VENDOR-RETURN-024 — List endpoint is paginated and scoped to branch

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-024 |
| **Title** | GET /vendor-returns with branchId filter returns only returns for that branch; without filter returns all for the company |
| **Area** | vendor-return |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | At least 2 vendor returns exist for branch 1; 0 or more for other branches. |
| **Steps** | 1. `GET /api/v1/vendor-returns?branchId=1&page=0&size=5`. 2. `GET /api/v1/vendor-returns?page=0&size=50`. |
| **Expected Result** | Step 1: `data.content` contains only returns with `branchId=1`; `data.totalElements >= 2`; `data.pageSize = 5`. Step 2: returns for all branches of the company. Response shape includes `totalElements`, `totalPages`, `pageNumber`, `pageSize`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Controller uses `PageRequest.of(page, size)`; service delegates to `BranchScope.requireReadable`. |

---

### TC-VENDOR-RETURN-025 — Invalid ULID in path returns 400 validation error

| Field | Value |
|-------|-------|
| **ID** | TC-VENDOR-RETURN-025 |
| **Title** | GET /vendor-returns/uid/{uid} with a malformed UID (not a valid ULID) returns 400 before hitting the service |
| **Area** | vendor-return |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-PROC-008 |
| **Preconditions** | Authenticated as rootadmin. |
| **Steps** | 1. `GET /api/v1/vendor-returns/uid/not-a-ulid`. 2. `POST /api/v1/vendor-credit-notes/uid/!!!bad!!!/apply` body: `{"supplierInvoiceUid":"01AAAAAAAAAAAAAAAAAAAAAAAAA","amount":1.00}`. |
| **Expected Result** | Both return HTTP 400; validation error referencing the `@ValidUlid` constraint. No DB query executed. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `@ValidUlid` on all `@PathVariable` uid parameters in VendorReturnController. |
