# Giftcard module

## 1. Purpose

`giftcard` owns the **gift-card lifecycle and redemption ledger**. A gift card is a stored-value voucher with a unique code, an initial value, a running balance, and a status. It's loaded at issue, redeemed at the till (as a tender method), and tracked in an append-only `gift_card_txn` ledger.

Gift card balance is a **liability**, not cash. It lives in its own ledger, separate from `cash_book`. When a customer redeems a gift card, the till is paid (a `pos_payment` with `method = GIFT_CARD`) but no `cash_entry` is created — instead a `gift_card_txn` of kind `REDEEM` debits the card balance.

## 2. Scope

In scope:
- `gift_card` master: code, initial value, current balance, status, expiry.
- `gift_card_txn` append-only ledger: `LOAD`, `REDEEM`, `REFUND`, `EXPIRE`.
- Code generation (configurable: random N-digit numeric, alphanumeric, or branch-prefixed).
- Issue flow: cashier scans / generates code, charges customer for the load value, prints card.
- Redemption flow at POS: scan code, check balance + status + expiry, debit and pay the sale.
- Refund flow: a refunded gift-card-tendered sale credits the card (gift_card_txn kind `REFUND`).
- Freeze / unfreeze (lost / stolen reporting).
- Auto-expire scheduled job (writes `EXPIRE` txn, sets status `EXPIRED`).

Out of scope:
- Customer linkage — gift cards are bearer instruments by default; customer association is optional metadata, not required.
- Loyalty points — separate domain (`party` or future `loyalty` module).
- Discount voucher engine — that's a `promotion` concern.
- Cash side of issue (the cash collected when loading a card) — that's `cash` (`cash_entry` `IN` on `TILL` paired with the `LOAD` txn here).

## 3. Domain model

| Table | Purpose |
|---|---|
| `gift_card` | Bearer voucher. UNIQUE `code`. Status: `ACTIVE`, `FULLY_REDEEMED`, `EXPIRED`, `FROZEN`, `REFUNDED`. |
| `gift_card_txn` | Append-only ledger row. Kind: `LOAD`, `REDEEM`, `REFUND`, `EXPIRE`. |

See [DATA-MODEL.md §12.1-12.2](../../../../../../../../DATA-MODEL.md) (Phase 1.1 additions).

## 4. Key business flows

1. **Issue card.** Cashier opens "Issue gift card" at POS. Picks load amount; system generates a unique `code` (or accepts a scanned blank card's pre-printed code). Cashier collects payment (cash / card / mobile money). Three rows post in one transaction:
   - `gift_card` row (`status = ACTIVE`, `current_balance = initial_value`).
   - `gift_card_txn` row (`kind = LOAD`, `amount = initial_value`, `balance_after = initial_value`).
   - `cash_entry` row in `cash` module (paired with the load payment).
   Emits `GiftCardIssued.v1`.
2. **Redeem at POS.** Customer presents card. Cashier scans `code`. Backend validates: status = `ACTIVE`, not expired, balance > 0. POS treats the gift card as a tender method (`pos_payment.method = GIFT_CARD`), up to the card balance or sale total (whichever is smaller). On posting:
   - `gift_card_txn` row (`kind = REDEEM`, `amount = redeemed_value`, `balance_after = balance − redeemed_value`).
   - Card status flips to `FULLY_REDEEMED` if balance hits 0.
   - No `cash_entry` (gift card is liability, not cash).
   Emits `GiftCardRedeemed.v1`.
3. **Refund a gift-card-tendered sale.** Customer returns a sale paid (fully or partly) with gift card. The card is credited back, not the cash drawer. Posts `gift_card_txn` of kind `REFUND`; card balance increases; status flips back to `ACTIVE` if it was `FULLY_REDEEMED`.
4. **Freeze (lost / stolen).** Manager sets `status = FROZEN`. Card cannot be redeemed; remaining balance preserved. Can be unfrozen.
5. **Auto-expire.** Scheduled job (e.g. nightly) finds `ACTIVE` cards with `expires_at < now`. For each, posts `gift_card_txn` of kind `EXPIRE` (amount = current_balance, balance_after = 0), sets `status = EXPIRED`. The expired liability is recognised as breakage revenue (a separate accounting line — flagged for treasury, no cash entry posted).
6. **Look up card balance.** Customer or cashier can check balance by code without redeeming.

## 5. Module interactions

**Depends on:**
- `auth` — issuer / redeemer identity for ledger rows.
- `admin` — `branch_id` for issued cards.
- `common` — outbox, audit.

**Publishes events:**
- `GiftCardIssued.v1`
- `GiftCardRedeemed.v1` (consumed by `pos` reconciliation, `reporting` for redemption rate)
- `GiftCardRefunded.v1`
- `GiftCardFrozen.v1` / `GiftCardUnfrozen.v1`
- `GiftCardExpired.v1`

**Consumes events:**
- `PosSaleVoided.v1` (from `pos`) — if the voided sale was tendered with a gift card, credit the card back.

## 6. API surface

| Resource | Endpoints |
|---|---|
| `/api/v1/gift-cards` | `POST` (issue), `GET` (search by code / status) |
| `/api/v1/gift-cards/{code}` | `GET` (balance + history) |
| `/api/v1/gift-cards/{code}/redeem` | `POST` — invoked by POS during tender |
| `/api/v1/gift-cards/{code}/refund` | `POST` — invoked on sale void / customer return |
| `/api/v1/gift-cards/{code}/freeze` | `POST` |
| `/api/v1/gift-cards/{code}/unfreeze` | `POST` |
| `/api/v1/gift-cards/{code}/transactions` | `GET` — ledger view |

POS clients call the redeem endpoint during the tender step. The endpoint is idempotent on `clientOpId` so a network blip during checkout doesn't double-charge the card.

## 7. Persistence

- Flyway: `V5__giftcard.sql` under `common/`.
- Sequences: `gift_card_seq`, `gift_card_txn_seq` (in `V1_3__phase11_sequences.sql`).
- Unique index on `gift_card.code`.
- Index on `(status, expires_at)` for the expiry job.
- Money columns `DECIMAL(18,4)`.

## 8. User stories

**P1:**
- US-GC-001 — Issue a gift card at POS
- US-GC-002 — Look up gift card balance
- US-GC-003 — Redeem a gift card as POS tender
- US-GC-005 — Freeze a lost or stolen gift card

**P2:**
- US-GC-004 — Refund a gift-card-tendered sale (credits the card)
- US-GC-006 — Unfreeze a gift card
- US-GC-007 — Auto-expire gift cards past their expiry date
- US-GC-008 — Gift card redemption rate report

## 9. Open questions

- **Bearer vs personalised.** Default is bearer (anyone with the code can redeem). Should we support personalisation (linked to a `customer.id`, requires ID at redemption)? Out for MVP.
- **Multi-currency gift cards.** Issue / redeem in functional currency only for MVP. Foreign-currency gift cards out of scope.
- **Reload (top-up an existing card).** Allowed or not? Default: not — cards are single-use stored value. Re-loadable would need an extra flag on `gift_card`.
- **Cross-company redemption** under one organisation. Default: gift cards are scoped to a single `company_id`; cross-company redemption rejected.
- **Breakage revenue posting.** When `EXPIRE` fires, the unredeemed balance is "breakage". Should this auto-post to a revenue account? Currently emits an event for treasury to handle; no auto-GL.

## 10. Implementation notes

- **Layered shape:** `domain/{entity,dto,enums,event}/`, `service/`, `repository/`.
- **Lombok:** `@Data` + `@NoArgsConstructor(PROTECTED)` + `@EqualsAndHashCode(of = "id")` on entities. `@ToString(exclude = {"code"})` on `gift_card` — codes are bearer secrets; do not log.
- **Invariants:**
  - `gift_card_txn` is append-only.
  - `current_balance = initial_value + sum(LOAD) − sum(REDEEM) + sum(REFUND) − sum(EXPIRE)`. Continuously valid.
  - `LOAD` is only allowed once (at issue); subsequent loads are rejected for MVP.
  - `REDEEM` rejected if card status ≠ `ACTIVE` or `current_balance < amount` or `expires_at < now`.
  - `FROZEN` blocks both `REDEEM` and `REFUND` until unfrozen.
- **Code generation.** Random 12-digit numeric by default. Collision check before commit (UNIQUE constraint as safety net). Card-creation API accepts a pre-printed code from physical-card inventory.
- **Multi-tenancy:** `company_id` scoping; gift cards do not cross companies.
- **Idempotency:** every write accepts `Idempotency-Key`. POS redemption is keyed by `(pos_sale_id, gift_card_id)` so the same redemption can't double-debit.
- **Outbox:** all events emitted in the same transaction.
- **Security:** card codes are bearer secrets. Logs and audit payloads redact full codes; show only last 4 digits (`****1234`). Audit log retains hash of code for forensic lookup, not plaintext.
