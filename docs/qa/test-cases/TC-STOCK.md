# TC-STOCK — Stock Movements, Balances, Counts

**Module:** stock  
**Stories:** US-STOCK-001 through US-STOCK-010  
**API base:** `http://localhost:8081/api/v1`

---

### TC-STOCK-001 — Stock on hand reflects posted transactions

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-001 |
| **Title** | GET /stock shows correct qty_on_hand after GRN and POS sale |
| **Area** | stock |
| **Dimension** | DATA |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-001 |
| **Preconditions** | Item COKE500 has qty_on_hand=10. Open session. Business day OPEN. |
| **Steps** | 1. `GET /api/v1/stock?itemId=<id>&branchId=1` — note qty_on_hand. 2. Post POS sale of 3 units COKE500. 3. GET stock again. |
| **Expected Result** | After sale: qty_on_hand = previous - 3. No rounding or precision error. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | Stock balance derived from stock_move ledger |

---

### TC-STOCK-002 — Stock adjustment posts ADJUSTMENT move

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-002 |
| **Title** | POST /adjustments with reason creates ADJUSTMENT stock_move and updates balance |
| **Area** | stock |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-003 |
| **Preconditions** | User has STOCK.ADJUST permission. Item in stock. Business day OPEN. |
| **Steps** | 1. `POST /api/v1/adjustments` body: `{"itemId":<id>,"branchId":1,"qty":-2,"reason":"Breakage","adjType":"DAMAGE"}`. 2. `GET /api/v1/stock?itemId=<id>&branchId=1`. |
| **Expected Result** | HTTP 201; `stock_move` with type ADJUSTMENT, qty=-2; `item_branch_balance.qty_on_hand` reduced by 2. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-STOCK-003 AC: "Requires reason; requires STOCK.ADJUST permission" |

---

### TC-STOCK-003 — Large adjustment requires supervisor authorisation

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-003 |
| **Title** | Adjustment above configurable threshold blocked without supervisor token |
| **Area** | stock |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-003 |
| **Preconditions** | Adjustment threshold = 50 units. User does not have bypass permission. |
| **Steps** | 1. POST adjustment qty=-100 without supervisor token. 2. Same with supervisor token. |
| **Expected Result** | Step 1: HTTP 422; threshold exceeded. Step 2: HTTP 201; supervisor identity on adjustment. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | US-STOCK-003 AC: "large adjustments (configurable threshold) require supervisor authorisation" |

---

### TC-STOCK-004 — Stock cannot go negative without STOCK.OVERSELL

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-004 |
| **Title** | Sale of quantity exceeding qty_on_hand blocked unless STOCK.OVERSELL granted |
| **Area** | stock |
| **Dimension** | NEG |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-010 |
| **Preconditions** | Item COKE500 qty_on_hand=3. Cashier does NOT have STOCK.OVERSELL. |
| **Steps** | 1. Post POS sale of 5x COKE500. |
| **Expected Result** | HTTP 422; insufficient stock for COKE500; qty_on_hand unchanged (still 3). |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | US-STOCK-010 AC |

---

### TC-STOCK-005 — Stock card shows movement history with running balance

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-005 |
| **Title** | GET /stock-card for item+branch returns moves in date order with running balance column |
| **Area** | stock |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-STOCK-002 |
| **Preconditions** | Item COKE500 has multiple stock_move records (GRN, sale, adjustment). |
| **Steps** | 1. `GET /api/v1/stock-report/card?itemId=<id>&branchId=1&from=2026-01-01&to=2026-12-31`. 2. Inspect running balance column. |
| **Expected Result** | Moves in chronological order; each row has `runningBalance = previous_balance + qty`; final balance matches `item_branch_balance.qty_on_hand`. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | US-STOCK-002 AC: "Running balance column" |

---

### TC-STOCK-006 — Multi-tenant: stock balance scoped to branch

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-006 |
| **Title** | GET /stock with X-Branch-Id:1 returns only branch-1 balances; not branch-2 balances |
| **Area** | stock |
| **Dimension** | SEC |
| **Priority** | P0 |
| **Linked US-*** | US-STOCK-001 |
| **Preconditions** | Items have different balances at branch 1 and branch 2. |
| **Steps** | 1. GET /stock with X-Branch-Id:1. 2. GET /stock with X-Branch-Id:2. |
| **Expected Result** | Step 1 and Step 2 return different qty_on_hand values for same item; neither leaks the other's data. |
| **Automatable?** | yes — integration test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-STOCK-007 — Moving average cost updated correctly on GRN

| Field | Value |
|-------|-------|
| **ID** | TC-STOCK-007 |
| **Title** | avg_cost recalculated as moving average after new GRN at different unit cost |
| **Area** | stock |
| **Dimension** | DATA |
| **Priority** | P1 |
| **Linked US-*** | US-PROC-004 |
| **Preconditions** | COKE500: qty_on_hand=100, avg_cost=800. New GRN: 50 units at 900/unit. |
| **Steps** | 1. Post GRN: 50x COKE500 at 900. 2. `GET /api/v1/stock?itemId=<id>&branchId=1` — check avg_cost. |
| **Expected Result** | new_avg_cost = (100*800 + 50*900) / 150 = 833.33... TZS; stored to 4dp. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | Moving average formula: (existing_qty * existing_avg + new_qty * new_cost) / (existing_qty + new_qty) |
