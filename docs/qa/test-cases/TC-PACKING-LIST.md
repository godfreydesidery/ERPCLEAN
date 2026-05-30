# TC-PACKING-LIST — Packing List: Create, Dispatch, Deliver, Cancel

**Module:** sales (F4.5)  
**Stories:** US-SALES-012  
**Permission gate:** `SALES.MANAGE_PACKING` (class-level on all endpoints)  
**API base:** `http://localhost:8081/api/v1`  
**Lifecycle:** DRAFT → DISPATCHED → DELIVERED (terminal); DRAFT → CANCELLED (terminal)

**Stock note:** The packing list is a delivery-tracking document. Stock is decremented by the parent `sales_invoice` at POST time (F4.2), not by the packing list. `dispatch` and `deliver` do not themselves move stock; the test for stock-lock / stock-decrement belongs in TC-SALES-001. Cases here verify lifecycle gate enforcement, document integrity, and the outbox events.

---

## Setup helper (reusable across cases)

```bash
# 1. Obtain token
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"rootadmin","password":"SKp315goPN8Nb0yJtMCCD7cm"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"

# 2. Resolve a POSTED sales invoice id (TC-PACKING-LIST cases assume one exists;
#    create if needed via POST /sales-invoices + POST .../post)
# 3. Resolve sales_invoice_line_id from GET /sales-invoices/uid/<uid>
```

---

### TC-PACKING-LIST-001 — Create a packing list draft against a POSTED invoice

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-001 |
| **Title** | POST /packing-lists with valid POSTED invoice returns 201 DRAFT |
| **Area** | packing-list |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | 1. Business day OPEN. 2. A sales invoice in status POSTED exists for branch 1; obtain its `id` and at least one `lines[].id` (`salesInvoiceLineId`). 3. Caller has `SALES.MANAGE_PACKING`. |
| **Steps** | 1. `POST /api/v1/packing-lists` with body: `{"number":"PL-TC001-<entropy>","branchId":1,"salesInvoiceId":<invoiceId>,"dispatchDate":"2026-05-30","driverName":"John Driver","vehicleNo":"T123ABC","notes":"Test run TC-001","lines":[{"salesInvoiceLineId":<lineId>,"qty":1}]}`. 2. Note returned `uid`. 3. `GET /api/v1/packing-lists/uid/<uid>`. |
| **Expected Result** | Step 1: HTTP 201; `data.status = "DRAFT"`; `data.uid` non-null 26-char ULID; `data.number = "PL-TC001-<entropy>"` (uppercased); `data.lines` has 1 entry; `Location` header = `/api/v1/packing-lists/uid/<uid>`. Step 3: HTTP 200; matches creation payload; `id` and `salesInvoiceId` serialised as JSON strings. `PackingListCreated.v1` event written to `domain_event` outbox in same TX. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Wire-shape verified by `PackingListDtoJsonTest`; id/FK fields must be JSON strings. |

---

### TC-PACKING-LIST-002 — Happy-path: DRAFT → DISPATCHED → DELIVERED full lifecycle

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-002 |
| **Title** | Dispatch then deliver a packing list; each transition returns updated status and publishes outbox event |
| **Area** | packing-list |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | 1. TC-PACKING-LIST-001 completed; packing list `<uid>` in DRAFT. 2. Caller has `SALES.MANAGE_PACKING`. |
| **Steps** | 1. `POST /api/v1/packing-lists/uid/<uid>/dispatch`. 2. `GET /api/v1/packing-lists/uid/<uid>` — confirm status. 3. `POST /api/v1/packing-lists/uid/<uid>/deliver`. 4. `GET /api/v1/packing-lists/uid/<uid>` — confirm status. 5. Query `domain_event` table for `PackingListDispatched.v1` and `PackingListDelivered.v1`. |
| **Expected Result** | Step 1: HTTP 200; `data.status = "DISPATCHED"`. Step 2: status = DISPATCHED. Step 3: HTTP 200; `data.status = "DELIVERED"`; `data.deliveredAt` non-null; `data.deliveredBy` = current user id (as string). Step 4: status = DELIVERED; `deliveredAt` preserved. Step 5: both events present in outbox with matching `packingListId`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Terminal state — no further transitions must be possible after DELIVERED (verified by TC-PACKING-LIST-006). |

---

### TC-PACKING-LIST-003 — Cancel a DRAFT packing list

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-003 |
| **Title** | POST /cancel on a DRAFT packing list sets status CANCELLED and publishes event |
| **Area** | packing-list |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | 1. Create a fresh packing list in DRAFT (use unique number prefix `PL-TC003-<entropy>`). 2. Caller has `SALES.MANAGE_PACKING`. |
| **Steps** | 1. `POST /api/v1/packing-lists/uid/<uid>/cancel`. 2. `GET /api/v1/packing-lists/uid/<uid>`. 3. Query `domain_event` for `PackingListCancelled.v1`. |
| **Expected Result** | Step 1: HTTP 200; `data.status = "CANCELLED"`. Step 2: status = CANCELLED; `updatedAt` > `createdAt`. Step 3: event present with correct `packingListId` and `number`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-PACKING-LIST-004 — Cannot create packing list against a DRAFT invoice

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-004 |
| **Title** | POST /packing-lists with salesInvoiceId pointing to a DRAFT invoice is rejected |
| **Area** | packing-list |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | 1. A sales invoice in status DRAFT exists; obtain its `id` and a line `id`. 2. Caller has `SALES.MANAGE_PACKING`. |
| **Steps** | 1. `POST /api/v1/packing-lists` with body: `{"number":"PL-TC004-<entropy>","branchId":1,"salesInvoiceId":<draftInvoiceId>,"dispatchDate":"2026-05-30","lines":[{"salesInvoiceLineId":<lineId>,"qty":1}]}`. |
| **Expected Result** | HTTP 422 (or 400); `data` null; `errors` or `message` references "POSTED / PARTIALLY_PAID / PAID" invoice requirement; no row inserted in `packing_list`. |
| **Automatable?** | yes — unit test (service layer) + integration |
| **Result/Status** | |
| **Notes/IssueRef** | Service guard: `invoice.getStatus() != POSTED && != PARTIALLY_PAID && != PAID`. Also verify against VOIDED and CANCELLED invoices via the same path. |

---

### TC-PACKING-LIST-005 — Cannot deliver before dispatching

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-005 |
| **Title** | POST /deliver on a DRAFT packing list returns error — must dispatch first |
| **Area** | packing-list |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | 1. Packing list in DRAFT status (use unique number `PL-TC005-<entropy>`). 2. Caller has `SALES.MANAGE_PACKING`. |
| **Steps** | 1. `POST /api/v1/packing-lists/uid/<uid>/deliver` (without prior dispatch). |
| **Expected Result** | HTTP 409 (or 422); response body includes "DISPATCHED, expected DISPATCHED" or equivalent state-machine error; packing list remains in DRAFT. |
| **Automatable?** | yes — unit test (entity `markDelivered` requireStatus guard) + integration |
| **Result/Status** | |
| **Notes/IssueRef** | `PackingList.markDelivered` calls `requireStatus(DISPATCHED)` which throws `IllegalStateException`. |

---

### TC-PACKING-LIST-006 — Cannot dispatch a packing list twice

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-006 |
| **Title** | POST /dispatch on an already-DISPATCHED packing list is rejected |
| **Area** | packing-list |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | 1. Packing list in DISPATCHED status (created + dispatched via TC-PACKING-LIST-002 steps 1-2). 2. Caller has `SALES.MANAGE_PACKING`. |
| **Steps** | 1. `POST /api/v1/packing-lists/uid/<uid>/dispatch` (second call). |
| **Expected Result** | HTTP 409 (or 422); error body references "DISPATCHED, expected DRAFT" or equivalent; status remains DISPATCHED; no additional outbox event. |
| **Automatable?** | yes — unit test + integration |
| **Result/Status** | |
| **Notes/IssueRef** | `PackingList.dispatch` calls `requireStatus(DRAFT)`. |

---

### TC-PACKING-LIST-007 — Cannot cancel a DISPATCHED packing list

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-007 |
| **Title** | POST /cancel on a DISPATCHED packing list is rejected |
| **Area** | packing-list |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | 1. Packing list in DISPATCHED status. 2. Caller has `SALES.MANAGE_PACKING`. |
| **Steps** | 1. `POST /api/v1/packing-lists/uid/<uid>/cancel`. |
| **Expected Result** | HTTP 409 (or 422); error references state machine; packing list remains DISPATCHED. |
| **Automatable?** | yes — unit test + integration |
| **Result/Status** | |
| **Notes/IssueRef** | `PackingList.cancel` calls `requireStatus(DRAFT)`. Neither DISPATCHED nor DELIVERED nor CANCELLED can transition to CANCELLED. |

---

### TC-PACKING-LIST-008 — Duplicate number within the same branch is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-008 |
| **Title** | POST /packing-lists with a number already used in the same branch returns error |
| **Area** | packing-list |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | 1. A packing list with number `PL-DUP-TC008` already exists for branch 1. 2. Caller has `SALES.MANAGE_PACKING`. |
| **Steps** | 1. `POST /api/v1/packing-lists` body: `{"number":"PL-DUP-TC008","branchId":1,"salesInvoiceId":<postedInvoiceId>,"dispatchDate":"2026-05-30","lines":[{"salesInvoiceLineId":<lineId>,"qty":1}]}`. |
| **Expected Result** | HTTP 422 (or 409); `errors` or `message` references "Packing-list number already exists for this branch"; no second row in `packing_list`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | DB constraint `uk_packing_list_branch_number` plus service-layer pre-check `existsByBranchIdAndNumber`. |

---

### TC-PACKING-LIST-009 — Unauthenticated request is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-009 |
| **Title** | All /packing-lists endpoints return 401 when no JWT is supplied |
| **Area** | packing-list |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | No auth header. |
| **Steps** | 1. `GET /api/v1/packing-lists` (no auth). 2. `POST /api/v1/packing-lists` with valid body (no auth). 3. `POST /api/v1/packing-lists/uid/<any-valid-uid>/dispatch` (no auth). |
| **Expected Result** | All three: HTTP 401; no packing list data in response. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Class-level `@PreAuthorize("hasAuthority('SALES.MANAGE_PACKING')")`. |

---

### TC-PACKING-LIST-010 — User without SALES.MANAGE_PACKING is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-010 |
| **Title** | Authenticated user lacking SALES.MANAGE_PACKING receives 403 on every packing-list endpoint |
| **Area** | packing-list |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | 1. User `cashier` / `Cashier#2026` exists (POS_CASHIER role — does NOT hold `SALES.MANAGE_PACKING`). 2. A packing list `<uid>` exists. |
| **Steps** | 1. Obtain cashier JWT via `POST /api/v1/auth/login`. 2. `GET /api/v1/packing-lists` with cashier token. 3. `POST /api/v1/packing-lists` with cashier token and valid body. 4. `POST /api/v1/packing-lists/uid/<uid>/dispatch` with cashier token. |
| **Expected Result** | All three API calls: HTTP 403; no packing list data returned; packing list state unchanged. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-PACKING-LIST-011 — Cross-tenant isolation: packing list of another company is not visible

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-011 |
| **Title** | GET /packing-lists/uid/<uid> returns 404 when the packing list belongs to a different company |
| **Area** | packing-list |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | 1. Two companies exist (company A, company B). 2. A packing list created under company A with `uid = <uidA>`. 3. User belongs to company B; has `SALES.MANAGE_PACKING`. |
| **Steps** | 1. Login as company-B user. 2. `GET /api/v1/packing-lists/uid/<uidA>`. 3. `POST /api/v1/packing-lists/uid/<uidA>/dispatch`. |
| **Expected Result** | Both: HTTP 404; response body contains "Packing list not found". Company A's packing list not modified. |
| **Automatable?** | yes — integration test (requires two-company fixture) |
| **Result/Status** | |
| **Notes/IssueRef** | `requirePackingListByUid` performs `companyId` equality check and throws `NoSuchElementException` (→ 404) on mismatch. BLOCKED if single-company QA container — needs two-company fixture. concurrencyRisk=possible |

---

### TC-PACKING-LIST-012 — GET /packing-lists with branchId filter returns only that branch's lists

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-012 |
| **Title** | List endpoint filtered by branchId returns only packing lists for that branch |
| **Area** | packing-list |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | 1. At least two branches exist. 2. Packing lists created for branch 1 (numbers `PL-TC012A-<entropy>`) and branch 2 (`PL-TC012B-<entropy>`). 3. Caller has `SALES.MANAGE_PACKING` and read access to both branches. |
| **Steps** | 1. `GET /api/v1/packing-lists?branchId=1`. 2. `GET /api/v1/packing-lists?branchId=2`. 3. `GET /api/v1/packing-lists` (no filter). |
| **Expected Result** | Step 1: only branch-1 packing lists; no branch-2 entries. Step 2: only branch-2 packing lists. Step 3: lists from both branches (all visible to this user); ordered by `id DESC`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `findByCompanyIdAndBranchIdOrderByIdDesc` vs `findByCompanyIdOrderByIdDesc`. |

---

### TC-PACKING-LIST-013 — POST /packing-lists with missing required fields returns 400

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-013 |
| **Title** | Bean-validation rejects request with blank number, null branchId, null salesInvoiceId, null dispatchDate, empty lines |
| **Area** | packing-list |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | Caller has `SALES.MANAGE_PACKING`. |
| **Steps** | Send five separate requests, each omitting one required field: (a) `number` blank/absent; (b) `branchId` absent; (c) `salesInvoiceId` absent; (d) `dispatchDate` absent; (e) `lines` empty array `[]`. For (f) send a line with `qty = 0` (violates `@Positive`). |
| **Expected Result** | All six: HTTP 400; response `errors` array non-empty; no row inserted in `packing_list`. |
| **Automatable?** | yes — unit test (validation) |
| **Result/Status** | |
| **Notes/IssueRef** | `@NotBlank` on `number`, `@NotNull` on `branchId`/`salesInvoiceId`/`dispatchDate`, `@NotEmpty` on `lines`, `@Positive` on `Line.qty`. |

---

### TC-PACKING-LIST-014 — GET /packing-lists/uid/<uid> with invalid ULID returns 400

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-014 |
| **Title** | Path param that is not a valid ULID is rejected with 400 before hitting the service |
| **Area** | packing-list |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | Caller has `SALES.MANAGE_PACKING`. |
| **Steps** | 1. `GET /api/v1/packing-lists/uid/not-a-ulid`. 2. `POST /api/v1/packing-lists/uid/not-a-ulid/dispatch`. 3. `POST /api/v1/packing-lists/uid/not-a-ulid/deliver`. 4. `POST /api/v1/packing-lists/uid/not-a-ulid/cancel`. |
| **Expected Result** | All four: HTTP 400; `@ValidUlid` constraint violation message in response; no service logic executed. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | `@ValidUlid` annotation on all `{uid}` path params in `PackingListController`. |

---

### TC-PACKING-LIST-015 — Wire shape: id and FK fields serialise as JSON strings

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-015 |
| **Title** | PackingListDto response serialises id, companyId, branchId, salesInvoiceId as JSON strings; qty stays numeric |
| **Area** | packing-list |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | A DRAFT packing list with known uid exists. |
| **Steps** | 1. `GET /api/v1/packing-lists/uid/<uid>` and inspect raw JSON response. |
| **Expected Result** | `data.id` is a JSON string (e.g. `"42"`), not a number. `data.companyId`, `data.branchId`, `data.salesInvoiceId` are JSON strings. `data.lines[0].id` and `data.lines[0].salesInvoiceLineId` are JSON strings. `data.lines[0].qty` is a JSON number (BigDecimal, not an id field). `data.uid` is a 26-char string. |
| **Automatable?** | yes — unit test (`PackingListDtoJsonTest` already exists) + e2e assertion on raw response body |
| **Result/Status** | |
| **Notes/IssueRef** | Covered by existing `PackingListDtoJsonTest`; this case is the live-container confirmation. |

---

### TC-PACKING-LIST-016 — Packing list against a VOIDED invoice is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-016 |
| **Title** | POST /packing-lists with a VOIDED salesInvoiceId is rejected |
| **Area** | packing-list |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | 1. A sales invoice exists in status VOIDED. 2. Caller has `SALES.MANAGE_PACKING`. |
| **Steps** | 1. `POST /api/v1/packing-lists` with `salesInvoiceId` = id of the VOIDED invoice. |
| **Expected Result** | HTTP 422 (or 400); error message references "POSTED / PARTIALLY_PAID / PAID" requirement; no packing list row created. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Same guard as TC-PACKING-LIST-004; separate case because VOIDED is a distinct terminal state reached through a different path than DRAFT. |

---

### TC-PACKING-LIST-017 — Number is stored uppercased regardless of input case

| Field | Value |
|-------|-------|
| **ID** | TC-PACKING-LIST-017 |
| **Title** | Packing list number submitted in lowercase is stored and returned in uppercase |
| **Area** | packing-list |
| **Dimension** | DATA |
| **Priority** | P3 |
| **Linked US-*** | US-SALES-012 |
| **Preconditions** | Caller has `SALES.MANAGE_PACKING`. A POSTED invoice exists. |
| **Steps** | 1. `POST /api/v1/packing-lists` with `"number":"pl-tc017-lower"` (all lowercase). 2. `GET /api/v1/packing-lists/uid/<uid>`. |
| **Expected Result** | `data.number = "PL-TC017-LOWER"` (fully uppercased). Duplicate check also case-normalises: a second POST with `"pl-TC017-lower"` (mixed) returns 422. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | `service.createDraft` does `request.number().trim().toUpperCase()` before duplicate check and persist. |
