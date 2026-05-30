# TC-GIFTCARD — Gift Card Lifecycle

**Module:** giftcard  
**Stories:** US-POS-009 (tender extension)  
**API base:** `http://localhost:8081/api/v1`

---

### TC-GIFTCARD-001 — Issue gift card; balance set; card is ACTIVE

| Field | Value |
|-------|-------|
| **ID** | TC-GIFTCARD-001 |
| **Title** | POST /gift-cards issues card with specified amount; status=ACTIVE |
| **Area** | giftcard |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | Admin logged in. |
| **Steps** | 1. `POST /api/v1/gift-cards` body: `{"amount":10000,"currencyCode":"TZS","expiresAt":"2027-01-01"}`. |
| **Expected Result** | HTTP 201; `data.status = "ACTIVE"`, `data.balance = 10000`, `data.code` is non-null (card code). |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-GIFTCARD-002 — Redeem gift card reduces balance; partial redemption allowed

| Field | Value |
|-------|-------|
| **ID** | TC-GIFTCARD-002 |
| **Title** | Redeem 6000 TZS from 10000 balance; remaining balance = 4000 |
| **Area** | giftcard |
| **Dimension** | FUNC |
| **Priority** | P1 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | Gift card active with balance 10000. |
| **Steps** | 1. `POST /api/v1/gift-cards/<code>/redeem` body: `{"amount":6000,"posSessionId":<id>}`. 2. GET gift card — check balance. |
| **Expected Result** | HTTP 200; `data.balance = 4000`; transaction logged. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-GIFTCARD-003 — Redeem more than balance is rejected

| Field | Value |
|-------|-------|
| **ID** | TC-GIFTCARD-003 |
| **Title** | Redeeming amount > card balance returns 422 |
| **Area** | giftcard |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | Gift card balance = 4000. |
| **Steps** | 1. POST /redeem amount=5000. |
| **Expected Result** | HTTP 422; error references insufficient balance; card balance unchanged. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-GIFTCARD-004 — Expired gift card cannot be redeemed

| Field | Value |
|-------|-------|
| **ID** | TC-GIFTCARD-004 |
| **Title** | Gift card past expiresAt date is rejected on redemption |
| **Area** | giftcard |
| **Dimension** | NEG |
| **Priority** | P1 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | Gift card with expiresAt in the past (e.g. 2025-01-01). |
| **Steps** | 1. Attempt to redeem expired card. |
| **Expected Result** | HTTP 422; error references card expiry; status may be EXPIRED. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |

---

### TC-GIFTCARD-005 — Refund to gift card increases balance

| Field | Value |
|-------|-------|
| **ID** | TC-GIFTCARD-005 |
| **Title** | POST /gift-cards/<code>/refund increases card balance |
| **Area** | giftcard |
| **Dimension** | FUNC |
| **Priority** | P2 |
| **Linked US-*** | US-POS-009 |
| **Preconditions** | Gift card active with balance 4000. |
| **Steps** | 1. `POST /api/v1/gift-cards/<code>/refund` body: `{"amount":2000}`. 2. GET card balance. |
| **Expected Result** | Balance = 6000; refund transaction logged. |
| **Automatable?** | yes — unit test |
| **Result/Status** | |
| **Notes/IssueRef** | |
