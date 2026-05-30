# TC-PROD — Production: BOM, Batch, Wastage, Conversion

**Module:** production  
**Stories:** US-PROD-001 through US-PROD-008  
**API base:** `http://localhost:8081/api/v1`

---

### TC-PROD-001 — Create BOM with output item and inputs

| Field | Value |
|-------|-------|
| **ID** | TC-PROD-001 |
| **Title** | POST /bom creates BOM with output item and input lines |
| **Area** | production |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | US-PROD-001 |
| **Preconditions** | Output item (e.g. BREAD) exists. Input items (UNGA2KG, SUGAR1KG) exist. |
| **Steps** | 1. `POST /api/v1/bom` body: `{"outputItemId":<bread_id>,"outputQty":10,"outputUomId":1,"version":1,"validFrom":"2026-01-01","expectedYieldPct":95,"inputs":[{"itemId":<unga_id>,"qty":2,"wastage":5},{"itemId":<sugar_id>,"qty":0.5,"wastage":2}]}`. |
| **Expected Result** | HTTP 201; `data.version = 1`, `data.expectedYieldPct = 95`; input lines stored with wastage pct. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | US-PROD-001 AC |

---

### TC-PROD-002 — Production batch completion posts PROD_CONSUME and PROD_OUTPUT stock moves

| Field | Value |
|-------|-------|
| **ID** | TC-PROD-002 |
| **Title** | Completing a production batch creates PROD_CONSUME moves for inputs and PROD_OUTPUT for output |
| **Area** | production |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | US-PROD-005 |
| **Preconditions** | BOM exists. Input items in stock. Batch in IN_PROGRESS status. |
| **Steps** | 1. `POST /api/v1/production-batches/uid/<uid>/complete` body with actual quantities. 2. Check stock_move for PROD_CONSUME and PROD_OUTPUT. |
| **Expected Result** | PROD_CONSUME moves for each input (negative qty); PROD_OUTPUT move for output item (positive qty); yield_variance computed. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-PROD-005 AC |

---

### TC-PROD-003 — Item conversion creates compensating stock moves

| Field | Value |
|-------|-------|
| **ID** | TC-PROD-003 |
| **Title** | POST /conversions converts one item into another with auditable stock moves |
| **Area** | production |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | US-PROD-007 |
| **Preconditions** | Item A exists with qty_on_hand >= 10. Item B exists. Business day OPEN. |
| **Steps** | 1. `POST /api/v1/conversions` body: `{"fromItemId":<A_id>,"fromQty":10,"toItemId":<B_id>,"toQty":5,"reason":"Repack"}`. |
| **Expected Result** | HTTP 201; stock_move CONVERSION_OUT qty=-10 for Item A; stock_move CONVERSION_IN qty=+5 for Item B; balances updated. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-PROD-007: "repacks, conversions, and corrections are auditable" |

---

### TC-PROD-004 — Production forms use name pickers, not raw ids

| Field | Value |
|-------|-------|
| **ID** | TC-PROD-004 |
| **Title** | Production batch form shows item name picker for output and input items |
| **Area** | production / web |
| **Dimension** | UX |
| **Priority** | P2 |
| **Linked US-*** | US-PROD-003 |
| **Preconditions** | Web app running. Logged in. |
| **Steps** | 1. Navigate to Production > Batches > New. 2. Inspect BOM selector and item fields. |
| **Expected Result** | All item/BOM reference fields are name pickers. No raw id inputs. |
| **Automatable?** | yes — Playwright + axe |
| **Result/Status** | |
| **Notes/IssueRef** | |
