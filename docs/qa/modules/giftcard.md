# Giftcard — test plan

Issue, redeem, refund, freeze, expire. Bearer instrument. Liability ledger separate from cash_book.

## Issue

### TC-GC-001 — Issue a gift card [P1]
**Stories:** US-GC-001
**Steps:** POST /api/v1/gift-cards `{ initial_value: 100000, expires_at? }` with cash payment.
**Expected:** 201; gift_card status ACTIVE, balance = initial_value; gift_card_txn LOAD row; cash_entry IN on TILL; `GiftCardIssued.v1` + paired `CashEntryPosted.v1`.

### TC-GC-002 — Unique code generated [P1]
**Steps:** Issue many cards.
**Expected:** No code collisions; UNIQUE constraint as safety net.

### TC-GC-003 — Provided code accepted [P2]
**Steps:** Issue with body-provided code (pre-printed card stock).
**Expected:** 201; uses supplied code.

### TC-GC-004 — Re-issue with same code [P1]
**Steps:** Two POSTs with same code.
**Expected:** Second 409.

### TC-GC-005 — Initial value > 0 [P1]
**Steps:** initial_value = 0 or negative.
**Expected:** 422.

## Balance lookup

### TC-GC-006 — Get card balance [P1]
**Stories:** US-GC-002
**Steps:** GET /gift-cards/{code}.
**Expected:** 200; balance, status, expires_at; full code NOT echoed back to non-issuer (or shown as ****1234).

### TC-GC-007 — Get balance on frozen card [P2]
**Steps:** Card FROZEN.
**Expected:** 200; shows status; cannot be redeemed.

### TC-GC-008 — Get balance on expired card [P2]
**Steps:** Card EXPIRED.
**Expected:** 200; balance = 0 (after expire ledger entry); status EXPIRED.

## Redeem

### TC-GC-009 — Redeem partial [P1]
**Stories:** US-GC-003
**Steps:** POST /gift-cards/{code}/redeem `{ amount: 35000, ref: pos_sale.id }`.
**Expected:** gift_card_txn REDEEM row; balance −= 35000; status stays ACTIVE.

### TC-GC-010 — Redeem to zero → FULLY_REDEEMED [P1]
**Steps:** Redeem amount = current_balance.
**Expected:** Balance = 0; status FULLY_REDEEMED.

### TC-GC-011 — Insufficient balance [P1]
**Steps:** Redeem amount > balance.
**Expected:** 422 `INSUFFICIENT_BALANCE`.

### TC-GC-012 — Frozen card cannot be redeemed [P1]
**Steps:** Card FROZEN.
**Expected:** 422 `CARD_FROZEN`.

### TC-GC-013 — Expired card cannot be redeemed [P1]
**Steps:** Card EXPIRED (expires_at < now).
**Expected:** 422 `CARD_EXPIRED`.

### TC-GC-014 — Wrong company_id [P1]
**Steps:** Redeem a card issued by company 1 from a till in company 2 (within same organisation).
**Expected:** 422 `CROSS_COMPANY_REDEMPTION_DISALLOWED`.

### TC-GC-015 — Concurrent redemption races [P1]
**Steps:** Two parallel /redeem calls on the same card, each for half the balance.
**Expected:** Both succeed in serialised order; balance = 0; two gift_card_txn rows.

### TC-GC-016 — Concurrent over-redemption [P1]
**Steps:** Two parallel /redeem with amounts each > half balance.
**Expected:** One succeeds; one 422 (balance already reduced).

### TC-GC-017 — Idempotent redeem [P1]
**Steps:** Replay /redeem with same Idempotency-Key.
**Expected:** Same response; one txn row; balance only debited once.

## Refund

### TC-GC-018 — Refund a redeemed sale [P2]
**Stories:** US-GC-004
**Steps:** POS refunds sale that used GC. POST /gift-cards/{code}/refund `{ amount: 35000, ref: pos_sale.id }`.
**Expected:** REFUND txn row; balance += 35000; status flips to ACTIVE if was FULLY_REDEEMED; `GiftCardRefunded.v1`.

### TC-GC-019 — Refund > prior REDEEM total [P2]
**Steps:** Refund more than ever redeemed.
**Expected:** 422.

## Freeze / unfreeze

### TC-GC-020 — Freeze a card [P1]
**Stories:** US-GC-005
**Steps:** POST /gift-cards/{code}/freeze.
**Expected:** Status FROZEN; redeems blocked; `GiftCardFrozen.v1`.

### TC-GC-021 — Unfreeze [P2]
**Stories:** US-GC-006
**Steps:** POST /gift-cards/{code}/unfreeze.
**Expected:** Status ACTIVE; `GiftCardUnfrozen.v1`.

### TC-GC-022 — Freeze already-FULLY_REDEEMED card [P2]
**Steps:** Card balance 0. POST freeze.
**Expected:** Allowed (preserves zero balance from future refund); no surprise behaviour.

## Expiry

### TC-GC-023 — Auto-expire job [P2]
**Stories:** US-GC-007
**Steps:** Card with expires_at < now, status ACTIVE. Scheduled job runs.
**Expected:** Status EXPIRED; EXPIRE txn row (amount = balance_before, balance_after = 0); `GiftCardExpired.v1`.

### TC-GC-024 — Already-expired but ACTIVE on lookup [P2]
**Steps:** expires_at < now but job hasn't run yet. Try to redeem.
**Expected:** Redeem rejected (validate expires_at on every redeem).

## Ledger consistency

### TC-GC-025 — Balance equation invariant [P1]
**Type:** Edge
**Steps:** Sum gift_card_txn for a card.
**Expected:** `current_balance = initial_value + sum(LOAD) − sum(REDEEM) + sum(REFUND) − sum(EXPIRE)`.

### TC-GC-026 — Ledger append-only [P1]
**Steps:** Attempt UPDATE on gift_card_txn.
**Expected:** No app endpoint; DB-level enforcement preferred.

## Security

### TC-GC-027 — Code not logged in plaintext [P1]
**Steps:** Issue card; review logs.
**Expected:** Code redacted (****1234) everywhere except DB row + initial response to issuer.

### TC-GC-028 — Audit log shows last 4 + hash [P1]
**Steps:** Inspect audit row.
**Expected:** No full code; last 4 digits + sha256(code) for forensic lookup.

### TC-GC-029 — Privilege checks [P1]
**Steps:** Issue / Redeem / Freeze without respective privilege.
**Expected:** 403.

## Reports

### TC-GC-030 — Outstanding liability report [P2]
**Stories:** US-RPT-013
**Steps:** GET /reports/giftcard-liability.
**Expected:** Sum of current_balance for ACTIVE + FROZEN cards.

### TC-GC-031 — Redemption rate [P2]
**Stories:** US-GC-008
**Steps:** GET /reports/giftcard-redemption-rate.
**Expected:** Issued total / redeemed total over period.
