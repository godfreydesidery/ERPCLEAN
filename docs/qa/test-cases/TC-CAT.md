# TC-CAT — Catalog: Items, Prices, Barcodes, Groups

**Module:** catalog  
**Stories:** US-CAT-001 through US-CAT-014  
**API base:** `http://localhost:8081/api/v1`

---

### TC-CAT-001 — Create an item with required fields

| Field | Value |
|-------|-------|
| **ID** | TC-CAT-001 |
| **Title** | POST /items with all required fields creates item; UID assigned |
| **Area** | catalog |
| **Dimension** | FUNC |
| **Priority** | P0 |
| **Linked US-*** | US-CAT-001 |
| **Preconditions** | 1. Item group exists (e.g. Beverages). 2. UoM EA (id=1) exists. 3. VAT group STD18 exists. 4. Logged in as rootadmin. |
| **Steps** | 1. `POST /api/v1/items` body: `{"code":"TESTITEM01","name":"Test Item","type":"SELLABLE","itemGroupId":<grp_id>,"uomId":1,"vatGroupId":<vat_id>}`. 2. `GET /api/v1/items/uid/<returned_uid>`. 3. Inspect `id` field in response (must be string per global Jackson modifier). |
| **Expected Result** | HTTP 201; `data.uid` is 26-char ULID; `data.id` is a string (e.g. `"42"`); `data.code = "TESTITEM01"`; `data.isTracked = true` (default). |
| **Automatable?** | yes — unit test (`ItemServiceImplTest`) + JSON wire test (`ItemResponseDtoJsonTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | id serialised as string via `IdLongAsStringSerializerModifier` (CLAUDE.md convention) |

---

### TC-CAT-002 — Item code must be unique per company

| Field | Value |
|-------|-------|
| **ID** | TC-CAT-002 |
| **Title** | Creating a second item with the same code in the same company returns 409 |
| **Area** | catalog |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-CAT-001 |
| **Preconditions** | Item TESTITEM01 created in TC-CAT-001. |
| **Steps** | 1. `POST /api/v1/items` with `code: "TESTITEM01"`, same company context. |
| **Expected Result** | HTTP 409 or 422; error references duplicate code. Original item not affected. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | US-CAT-001 AC: "code (unique per company)" |

---

### TC-CAT-003 — Create item missing required field returns 400

| Field | Value |
|-------|-------|
| **ID** | TC-CAT-003 |
| **Title** | POST /items without required field (e.g. uomId) returns 400 validation error |
| **Area** | catalog |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-CAT-001 |
| **Preconditions** | Logged in. |
| **Steps** | 1. `POST /api/v1/items` body omitting `uomId`. 2. `POST /api/v1/items` body omitting `name`. 3. `POST /api/v1/items` body with `type: "INVALID_ENUM"`. |
| **Expected Result** | Step 1: HTTP 400; error mentions uomId. Step 2: HTTP 400; error mentions name. Step 3: HTTP 400; error mentions invalid enum value (NOTE: if this currently returns 500, this is a known bug — file as TC-CAT-003-BUG with severity Major). |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Invalid enum returning 500 instead of 400 is a known pattern per project conventions |

---

### TC-CAT-004 — Add barcode to item; barcode is unique per company

| Field | Value |
|-------|-------|
| **ID** | TC-CAT-004 |
| **Title** | POST /item-barcodes adds barcode to item; duplicate barcode across items rejected |
| **Area** | catalog |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-CAT-003 |
| **Preconditions** | Item COKE500 exists. |
| **Steps** | 1. `POST /api/v1/item-barcodes` body: `{"itemId":<coke_id>,"barcode":"5000112637922","uomId":1,"packQty":1}`. 2. Same barcode for a different item. |
| **Expected Result** | Step 1: HTTP 201. Step 2: HTTP 409; barcode uniqueness violation per company. |
| **Automatable?** | yes — unit test (`ItemBarcodeServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | US-CAT-003 AC: "Barcode is unique per company" |

---

### TC-CAT-005 — Price list item set; previous price closed with valid_to

| Field | Value |
|-------|-------|
| **ID** | TC-CAT-005 |
| **Title** | Setting new price on price list closes old price_list_item; price_change_log row created |
| **Area** | catalog |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-CAT-007 US-CAT-014 |
| **Preconditions** | RETAIL price list exists. COKE500 has price 1200 TZS. |
| **Steps** | 1. `PUT /api/v1/price-lists/uid/<pl_uid>/items` body: `{"itemId":<coke_id>,"uomId":1,"price":1300,"effectiveFrom":"2026-06-01","reason":"Price increase"}`. 2. Query `price_list_item` for COKE500 — check valid_to on old row. 3. Query `price_change_log`. |
| **Expected Result** | Old price_list_item has `valid_to = 2026-05-31` (day before effective); new row has `price = 1300`, `valid_from = 2026-06-01`. `price_change_log` row with old_price=1200, new_price=1300, changed_by, reason. |
| **Automatable?** | yes — unit test (`PriceListServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | US-CAT-007 AC |

---

### TC-CAT-006 — Item id field serializes as string on wire

| Field | Value |
|-------|-------|
| **ID** | TC-CAT-006 |
| **Title** | GET /items response: id field is JSON string, not number |
| **Area** | catalog |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-CAT-001 |
| **Preconditions** | At least one item exists. |
| **Steps** | 1. `GET /api/v1/items` — inspect raw JSON response body. 2. Parse `data[0].id` — check JSON token type. |
| **Expected Result** | `data[0].id` is a JSON string (`"1"`, not `1`). `data[0].uid` is a 26-char string. `data[0].vatGroupId` (reference id field) is also a string. Numeric fields (price, qty) remain numbers. |
| **Automatable?** | yes — JSON unit test (`ItemResponseDtoJsonTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | Global `IdLongAsStringSerializerModifier` in JacksonConfig |

---

### TC-CAT-007 — Item group hierarchy — child group cannot exceed max depth

| Field | Value |
|-------|-------|
| **ID** | TC-CAT-007 |
| **Title** | Item group nesting respects hierarchy depth constraints |
| **Area** | catalog |
| **Dimension** | NEG |
| **Priority** | P2 |
| **Linked US-*** | US-CAT-004 |
| **Preconditions** | Item group hierarchy has 4 levels (Dept > Class > SubClass > Category per PRD). |
| **Steps** | 1. Create groups at 4 levels deep. 2. Attempt to create a 5th level group. |
| **Expected Result** | If depth limit is enforced: HTTP 422. If not enforced (check implementation), document as observed behavior. |
| **Automatable?** | yes — unit test (`ItemGroupServiceImplTest`) |
| **Result/Status** | |
| **Notes/IssueRef** | PRD §5.3: "Four-level grouping hierarchy" — verify implementation enforces this |

---

### TC-CAT-008 — Item group with items cannot be deleted

| Field | Value |
|-------|-------|
| **ID** | TC-CAT-008 |
| **Title** | DELETE on item group that has items returns 422 |
| **Area** | catalog |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-CAT-004 |
| **Preconditions** | Item group BVGS has item COKE500. |
| **Steps** | 1. `DELETE /api/v1/item-groups/uid/<bvgs_uid>`. |
| **Expected Result** | HTTP 422; error "group has items — archive instead of delete". Group not deleted. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | US-CAT-004 AC: "A group with items cannot be deleted; it can be archived" |

---

### TC-CAT-009 — Catalog pickers show name, not raw id

| Field | Value |
|-------|-------|
| **ID** | TC-CAT-009 |
| **Title** | All catalog reference fields in web forms show name pickers, not raw id/uid inputs |
| **Area** | catalog / web |
| **Dimension** | UX |
| **Priority** | P0 |
| **Linked US-*** | US-CAT-001 |
| **Preconditions** | Web app running at http://localhost:8081/. Logged in. |
| **Steps** | 1. Navigate to Catalog > Items > New Item. 2. Inspect Item Group field, UoM field, VAT Group field. 3. Inspect Price List selection in price form. |
| **Expected Result** | All reference fields show typeahead/dropdown showing entity names. No raw numeric id or ULID input fields visible. (Per feedback-no-raw-id-entries convention.) |
| **Automatable?** | yes — Playwright e2e + axe-core |
| **Result/Status** | |
| **Notes/IssueRef** | CLAUDE.md: "users never type raw ids or uids; every reference field is a picker" |

---

### TC-CAT-010 — Item list endpoint is paginated

| Field | Value |
|-------|-------|
| **ID** | TC-CAT-010 |
| **Title** | GET /items with pageSize param returns paged result with totalCount |
| **Area** | catalog |
| **Dimension** | PERF |
| **Priority** | P1 |
| **Linked US-*** | US-CAT-005 |
| **Preconditions** | More than 10 items exist. |
| **Steps** | 1. `GET /api/v1/items?pageSize=5&page=0`. 2. `GET /api/v1/items?pageSize=5&page=1`. 3. Check for overlapping items. |
| **Expected Result** | Each page returns 5 items; `data.totalCount` consistent; no item appears in both pages; `data.currentPage` and `data.totalPages` populated. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-CAT-011 — VAT group rate precision maintained as BigDecimal

| Field | Value |
|-------|-------|
| **ID** | TC-CAT-011 |
| **Title** | VAT rate stored and returned as exact decimal; no floating-point rounding |
| **Area** | catalog |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-CAT-001 US-COMP-006 |
| **Preconditions** | VAT group STD18 with rate 0.18 exists. |
| **Steps** | 1. `GET /api/v1/vat-groups` — inspect rate field raw JSON. 2. Compute 1200 * 0.18 = 216 exactly. |
| **Expected Result** | Rate returned as `0.18` exactly (not `0.17999999999999999`). Tax computation on a 1200 TZS item at 18%: tax = 183.05... (for tax-inclusive) or 216.00 (for tax-exclusive); no floating-point artefact. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Java BigDecimal used throughout; MONEY_SCALE=4 in PosSaleServiceImpl |
