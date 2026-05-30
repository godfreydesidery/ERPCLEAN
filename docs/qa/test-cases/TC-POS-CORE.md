# TC-POS-CORE — POS Core: Till, Session, Sale, Tender

**Module:** pos  
**Stories:** US-POS-001 through US-POS-016  
**API base:** `http://localhost:8081/api/v1`  
**Auth:** cashier / Cashier#2026, branch HQ id=1

---

## Till Management

### TC-POS-CORE-001 — Open till session with valid float

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-001 |
| **Title** | Open till session with opening float creates OPEN session and cash entry |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-POS-002 |
| **Preconditions** | 1. Business day OPEN for branch 1. 2. Till exists (seeded or created). 3. No existing OPEN session on this till. 4. Cashier logged in. |
| **Steps** | 1. `POST /api/v1/till-sessions/open` headers: `Authorization: Bearer <cashier_token>`, `X-Branch-Id: 1`. Body: `{"tillId": <till_id>, "openingFloatAmount": 50000}`. 2. `GET /api/v1/till-sessions/uid/<returned_uid>`. 3. Verify session report via `GET /api/v1/reports/x-report?tillSessionId=<id>`. |
| **Expected Result** | Step 1: HTTP 201; response `data.status = "OPEN"`, `data.openingFloatAmount = 50000`, `data.openedAt` populated. Step 2: confirms OPEN. Step 3: X-report shows float entry; CASH_IN for 50000 TZS on TILL account visible in `cashFloatTotal`. |
| **Automatable?** | yes — integration test (`TillSessionServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | ISSUE-POS-003: corrected open-session URL from `POST /api/v1/till-sessions` to `POST /api/v1/till-sessions/open`; corrected report URL from `/till-sessions/uid/<uid>/report` (does not exist) to `/reports/x-report?tillSessionId=<id>`. |

---

### TC-POS-CORE-002 — Second open session on same till blocked

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-002 |
| **Title** | Opening a second session on a till that has an OPEN session returns 400 |
| **Area** | pos |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-POS-002 |
| **Preconditions** | 1. Till already has OPEN session (TC-POS-CORE-001 passed). |
| **Steps** | 1. `POST /api/v1/till-sessions/open` same till, different cashier or same cashier. |
| **Expected Result** | HTTP 400; error body references existing open session (e.g. "Till T1 already has an OPEN session"); no new session created. Note: HTTP 409 would be semantically more correct (tracked as ISSUE-POS-002), but the current implementation returns 400 via `IllegalArgumentException`. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | ISSUE-POS-003: corrected URL to `POST /api/v1/till-sessions/open`. ISSUE-POS-002: current response is 400, not 409. |

---

### TC-POS-CORE-003 — Open session blocked when business day is CLOSED

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-003 |
| **Title** | Till session open fails with BUSINESS_DAY_CLOSED when no open business day |
| **Area** | pos |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-POS-002 US-DAY-001 |
| **Preconditions** | 1. Branch has NO open business day (day is CLOSED or does not exist for today). 2. Till exists. |
| **Steps** | 1. `POST /api/v1/till-sessions` with valid till and float. |
| **Expected Result** | HTTP 422; error code `BUSINESS_DAY_CLOSED` or equivalent; no session created. |
| **Automatable?** | yes — unit test with mocked DayGuard |
| **Result/Status** | |
| **Notes/IssueRef** | DayGuard is wired into TillSessionService |

---

## Sale — Happy Path

### TC-POS-CORE-004 — Post a single-item cash sale

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-004 |
| **Title** | Post POS sale with one item, CASH payment — stock decrements, cash entry created |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | 1. Open session exists for cashier on till. 2. Item COKE500 in stock (qty_on_hand >= 2). 3. RETAIL price list has COKE500 @ 1200 TZS, `taxInclusive=true`. |
| **Steps** | 1. Resolve item id: `GET /api/v1/items?q=COKE500` (note `data[0].id` and `data[0].uid`). 2. `POST /api/v1/pos-sales` body: `{"tillSessionId": <id>, "clientOpId": "test-op-001", "lines": [{"itemId": <id>, "qty": 2, "unitPrice": 1200, "discountPct": 0}], "payments": [{"method": "CASH", "amount": 2400}], "clientCreatedAt": "<ISO8601>"}`. 3. Query `stock_move` for item debit. 4. Query `cash_entry` for cash credit. |
| **Expected Result** | HTTP 201; `data.status = "POSTED"` (not "CLOSED" — the `PosSaleStatus` enum uses POSTED/VOIDED); `data.totalAmount = 2400`, `data.changeAmount = 0`; `stock_move` with type SALE, qty=-2 for COKE500; `cash_entry` CASH_IN 2400 TZS on TILL. Tax extracted from inclusive price: `taxAmount = round(2400 * 18/118, 4dp) = 366.1017`, `subtotalAmount = 2400 - 366.1017 = 2033.8983` (once ISSUE-POS-001 is fixed). Until ISSUE-POS-001 is fixed, the actual response inflates totals: `totalAmount=2832, taxAmount=432`. |
| **Automatable?** | yes — integration test (`PosSaleServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | ISSUE-POS-003: corrected expected `status` from `"CLOSED"` to `"POSTED"` (enum values are POSTED and VOIDED). ISSUE-POS-001: once VAT-inclusive fix lands, tender 2400 is correct; until then the API rejects 2400 with "Tender sum 2400.00 is less than total 2832.0000". id field is serialized as string per global Jackson modifier. |

---

### TC-POS-CORE-005 — Mixed-tender sale (cash + card)

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-005 |
| **Title** | POS sale with cash + card tender — two payment rows, only cash hits ledger |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | Open session exists. Item SUGAR1KG (2800 TZS) available. |
| **Steps** | 1. `POST /api/v1/pos-sales` body: total 2800 TZS, payments: `[{"method":"CASH","amount":1000},{"method":"CARD","amount":1800,"reference":"CARD-TXN-001"}]`. |
| **Expected Result** | HTTP 201; two `pos_payment` rows; `cash_entry` for 1000 TZS CASH only; CARD does not create a cash_entry; `data.changeAmount = 0`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-POS-009 AC: "Card and mobile money tenders capture a reference; PAN is never stored" |

---

### TC-POS-CORE-006 — Change computation when tender exceeds total

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-006 |
| **Title** | CASH tender > total produces correct change_amount on sale |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | Open session. Item BREAD (1000 TZS). |
| **Steps** | 1. `POST /api/v1/pos-sales` total=1000, payment `{"method":"CASH","amount":5000}`. |
| **Expected Result** | HTTP 201; `data.changeAmount = 4000`; cash_entry for 5000 CASH_IN (full tender, not net of change). |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-POS-CORE-007 — Payment sum mismatch rejected

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-007 |
| **Title** | Sale rejected when sum of payments does not equal sale total |
| **Area** | pos |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | Open session. |
| **Steps** | 1. `POST /api/v1/pos-sales` total (from lines) = 2400, payments sum = 2000. |
| **Expected Result** | HTTP 422; error code `PAYMENT_MISMATCH`; no stock move, no cash entry, no PosSale row created. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-POS-CORE-008 — Line discount within threshold posts without supervisor

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-008 |
| **Title** | Line discount at or below configured threshold posts without supervisor authorisation |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-005 |
| **Preconditions** | Discount threshold configured at 10% (default). Open session. RETAIL price list `taxInclusive=true`. |
| **Steps** | 1. `POST /api/v1/pos-sales` line: COKE500, unitPrice=1200, discountPct=5, payments CASH for the computed total (see expected result). |
| **Expected Result** | HTTP 201; line `discountPct=5`; no supervisor validation required. Once ISSUE-POS-001 is fixed: `netLine = 1200 * 0.95 = 1140`, tax extracted from inclusive price: `taxAmount = round(1140 * 18/118, 4dp) = 173.8983`, `lineTotal = 1140`. Tender = 1140. Until ISSUE-POS-001 is fixed: server adds VAT on top, `lineTotal = 1140 * 1.18 = 1345.2`; tender must be 1345.2 for the sale to be accepted. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | ISSUE-POS-003: corrected expected `line_total` — prior value of 1140 was the correct post-ISSUE-POS-001-fix value but does not match current server behaviour (1345.2). Spec is written against the correct (fixed) behaviour. ISSUE-POS-001 must land before this case passes end-to-end. |

---

### TC-POS-CORE-009 — Discount above threshold requires supervisor token

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-009 |
| **Title** | Discount > threshold without supervisor token returns 422; with token succeeds |
| **Area** | pos |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-005 US-IAM-010 |
| **Preconditions** | Discount threshold = 10%. Open session. Supervisor user with POS.DISCOUNT_APPROVE permission exists. |
| **Steps** | 1. POST sale: COKE500, discountPct=20, no supervisor token. 2. POST same sale: discountPct=20, with `supervisorToken` header containing a valid supervisor auth token. |
| **Expected Result** | Step 1: HTTP 422, error references discount threshold exceeded. Step 2: HTTP 201; line shows 20% discount; supervisor identity stored on sale. |
| **Automatable?** | yes — unit test (`PosSaleServiceImplTest`) — `DISCOUNT_APPROVE_PERMISSION = "POS.DISCOUNT_APPROVE"` |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-POS-CORE-010 — VAT correctly computed for mixed-VAT cart

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-010 |
| **Title** | Sale with both VAT-standard and VAT-exempt items computes correct tax per line |
| **Area** | pos |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | Items: COKE500 (VAT 18%, 1200 TZS inclusive), BREAD (VAT exempt, 1000 TZS). Price list tax_inclusive=true. |
| **Steps** | 1. POST sale: 1x COKE500, 1x BREAD, CASH 2200. 2. Inspect returned line DTOs for tax fields. |
| **Expected Result** | COKE500 line: `tax_amount = round(1200 * 18/118, TZS scale) = 183.05...` (or equivalent 4dp); BREAD line: `tax_amount = 0`. Total net: `1200/1.18 + 1000`. Money fields use BigDecimal with scale consistent with TZS (0 decimal cents standard, but 4dp internally as per `MONEY_SCALE = 4` in PosSaleServiceImpl). |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | `PosSaleServiceImpl.MONEY_SCALE = 4` |

---

### TC-POS-CORE-011 — Stock insufficient — oversell blocked without permission

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-011 |
| **Title** | Sale rejected when item stock is zero and STOCK.OVERSELL not granted |
| **Area** | pos |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-010 |
| **Preconditions** | 1. Item SOAP with qty_on_hand = 0. 2. Cashier does NOT have STOCK.OVERSELL. |
| **Steps** | 1. POST POS sale: 1x SOAP, CASH 700. |
| **Expected Result** | HTTP 422; error references insufficient stock for SOAP; no stock move, no cash entry. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## Cash Pickup and Petty Cash

### TC-POS-CORE-012 — Cash pickup mid-shift

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-012 |
| **Title** | Supervisor records cash pickup; expected drawer cash decrements |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-013 |
| **Preconditions** | 1. Open session with at least 20000 TZS in drawer (float + sales). 2. Supervisor token (user with POS.MANAGE_TILL). |
| **Steps** | 1. `POST /api/v1/cash-pickups` headers: supervisor auth, `X-Branch-Id: 1`. Body: `{"tillSessionId": <id>, "amount": 10000, "reference": "PICKUP-001", "authorisedBy": <supervisor_user_id>}`. 2. Verify via `GET /api/v1/reports/x-report?tillSessionId=<id>` — check `cashPickupTotal`. |
| **Expected Result** | HTTP 201; `cash_entry` OUT from TILL for 10000 TZS; `cashPickupTotal` in X-report reduced accordingly. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | ISSUE-POS-003: added required `authorisedBy` field to request body; corrected report URL from `/till-sessions/uid/<uid>/report` to `/reports/x-report?tillSessionId=<id>`. US-POS-013 AC. |

---

### TC-POS-CORE-013 — Petty cash payout

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-013 |
| **Title** | Petty cash payout records reason, reduces drawer, creates cash_entry OUT |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-014 |
| **Preconditions** | Open session. User with POS.MANAGE_TILL for authorisation. |
| **Steps** | 1. `POST /api/v1/petty-cash` body: `{"tillSessionId": <id>, "amount": 2000, "category": "OTHER", "description": "Cleaning materials", "authorisedBy": <supervisor_user_id>}`. |
| **Expected Result** | HTTP 201; `cash_entry` CASH_OUT 2000 TZS on TILL account; petty_cash row created with reason; visible in X-report `pettyCashTotal`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | ISSUE-POS-003: corrected `category` from `"SUPPLIES"` (not a valid `PettyCashCategory` enum value) to `"OTHER"`. Valid values: TRANSPORT, OFFICE, MAINTENANCE, OTHER. Added required `authorisedBy` field. |

---

## Till Close

### TC-POS-CORE-014 — Close till session with correct declared cash

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-014 |
| **Title** | Close till session; system computes expected cash; variance = 0 when declared matches |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-POS-016 |
| **Preconditions** | 1. Session with: float 50000, 1 cash sale 2400, 1 pickup 10000, 1 petty cash 500. Expected cash = 50000+2400-10000-500 = 41900. |
| **Steps** | 1. `POST /api/v1/till-sessions/uid/<uid>/close` body: `{"declaredCashAmount": 41900, "denominations": []}`. 2. `GET /api/v1/till-sessions/uid/<uid>` — check status. |
| **Expected Result** | HTTP 200; `data.status = "CLOSED"`, `data.expectedCashAmount = 41900`, `data.declaredCashAmount = 41900`, `data.variance = 0`. Z-report generated (or queued). |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-POS-016 AC |

---

### TC-POS-CORE-015 — Close with variance above threshold blocked without supervisor

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-015 |
| **Title** | Till close with variance > threshold fails without supervisor; succeeds with supervisor token |
| **Area** | pos |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-016 |
| **Preconditions** | Expected cash = 41900. Variance threshold = 500 TZS (configurable `orbix.*`). |
| **Steps** | 1. `POST /till-sessions/uid/<uid>/close` body: `{"declaredCashAmount": 40000}` (variance=1900 > threshold) — no supervisor. 2. Same with supervisor token. |
| **Expected Result** | Step 1: HTTP 422; error references variance threshold. Step 2: HTTP 200; `data.variance = -1900`; supervisor identity stamped. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-POS-CORE-016 — Void a posted POS sale reverses stock and cash

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-016 |
| **Title** | Supervisor voids completed sale; compensating stock move and cash entry created |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-006 |
| **Preconditions** | 1. POS sale in CLOSED status (from TC-POS-CORE-004). 2. Supervisor token. |
| **Steps** | 1. `POST /api/v1/pos-sales/uid/<uid>/void` body: `{"reason": "Customer changed mind", "supervisorToken": "<token>"}`. 2. Query stock_move for reversal. 3. Query cash_entry for reversal. |
| **Expected Result** | HTTP 200; `data.status = "VOIDED"`; new stock_move with type SALE_VOID, qty=+2; new cash_entry CASH_OUT for 2400 TZS; supervisor_id set on PosSale. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## Refund at Till

### TC-POS-CORE-017 — Same-day refund within threshold

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-017 |
| **Title** | Same-day POS refund creates REFUND sale, CASH_OUT entry, RETURN_IN stock move |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | 1. Sale posted today. 2. Refund threshold configured. |
| **Steps** | 1. `POST /api/v1/pos-sales/refund` body: `{"originalSaleUid": "<uid>", "lines": [{"itemId": <id>, "qty": 1}], "payments": [{"method": "CASH", "amount": -1200}]}`. |
| **Expected Result** | HTTP 201; new PosSale with `kind = REFUND`; `cash_entry` CASH_OUT 1200 TZS; `stock_move` RETURN_IN qty=+1. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-POS-CORE-018 — Refund of sale from closed business day blocked

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-018 |
| **Title** | Refund for a sale from a previous (closed) business day is blocked at till |
| **Area** | pos |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | Original sale is from yesterday's closed business day. |
| **Steps** | 1. Attempt POST /pos-sales/refund for yesterday's sale. |
| **Expected Result** | HTTP 422; error `BUSINESS_DAY_CLOSED_FOR_TILL_REFUND`; back-office customer_return required instead. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

## Barcode Resolution

### TC-POS-CORE-019 — Barcode resolves to correct item and pack quantity

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-019 |
| **Title** | GET /pos/barcode-lookup?code={barcode} resolves itemCode, barcodeType, and pack qty |
| **Area** | pos |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-003 |
| **Preconditions** | Item COKE500 has a barcode registered (e.g. EAN13 `5000112637922`). |
| **Steps** | 1. `GET /api/v1/pos/barcode-lookup?code=5000112637922` with cashier auth (`X-Branch-Id: 1`). |
| **Expected Result** | HTTP 200; `data.itemId` = COKE500's id (string); `data.itemCode = "COKE500"`; `data.qty = 1`; `data.barcodeType = "EAN13"`. Note: `ResolvedBarcodeDto` does not include a price field — price lookup requires a separate `GET /api/v1/price-entries` call with the itemId. |
| **Automatable?** | yes — unit test (`BarcodeResolverServiceImpl`) |
| **Result/Status** | |
| **Notes/IssueRef** | ISSUE-POS-004: corrected URL from `GET /api/v1/barcode-lookup/{barcode}` (path segment, returns 500) to `GET /api/v1/pos/barcode-lookup?code={barcode}` (query param, controller at `BarcodeLookupController`). Removed `data.price` from expected result — price is not in `ResolvedBarcodeDto`. |

---

### TC-POS-CORE-020 — Unknown barcode returns 404

| Field | Value |
|-------|-------|
| **ID** | TC-POS-CORE-020 |
| **Title** | Barcode lookup for non-existent barcode returns 404 |
| **Area** | pos |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-003 |
| **Preconditions** | Barcode `9999999999999` does not exist in the system. |
| **Steps** | 1. `GET /api/v1/pos/barcode-lookup?code=9999999999999` with cashier auth (`X-Branch-Id: 1`). |
| **Expected Result** | HTTP 404; response body: `{"message": "Barcode not found: 9999999999999"}`; no data field. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | ISSUE-POS-004: corrected URL from `GET /api/v1/barcode-lookup/9999999999999` (returns 500) to `GET /api/v1/pos/barcode-lookup?code=9999999999999`. US-POS-003 AC: "Unknown barcode shows a clear 'not found' toast". |
