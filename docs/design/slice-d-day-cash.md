# Slice D — Day + Cash spine hardening (design note)

Companion to [ADR 0002](../decisions/0002-uid-on-composite-key-aggregates.md).
Captures the slice-specific decisions engineering needs before starting.
Not load-bearing enough for an ADR each; flag anything that grows up to one.

## Permission map (band 92–105)

Existing coarse codes `CASH.READ` (48), `CASH.ADJUST` (49), `CASH.BANKING`
(50), `DAY.OPEN` (15), `DAY.CLOSE` (16), `DAY.OVERRIDE` (17) **stay seeded
and stay granted to ADMIN**. They remain as group-grant shortcuts for legacy
role assignments. Controllers move off them onto the granular codes below.

| ID | Code | Description | Controller(s) |
|---|---|---|---|
| 92 | `DAY.READ` | List / read business days | `BusinessDayController` GET |
| 93 | `DAY.START_CLOSING` | OPEN → CLOSING transition | `BusinessDayController` POST `…/start-closing` |
| 94 | `DAY.END` | Run EOD (`startClosing+close+auto-roll`) | `BusinessDayController` POST `…/end` |
| 95 | `DAY.OVERRIDE_VOID` | Void a `BusinessDayOverride` before its closed-day post | `BusinessDayOverrideController` POST `…/archive` |
| 96 | `CASH_BOOK.READ` | Read cash-book balances + variance views | `CashLedgerController` GET `/cash-books*` |
| 97 | `CASH_ENTRY.READ` | Read raw ledger rows | `CashLedgerController` GET `/cash-entries*` |
| 98 | `CASH_ADJUSTMENT.POST` | Post a supervisor cash adjustment | `CashAdjustmentController` POST |
| 99 | `CASH_ADJUSTMENT.REVERSE` | Reverse (archive) a posted adjustment | `CashAdjustmentController` POST `…/uid/{uid}/archive` |
| 100 | `CASH_ADJUSTMENT.READ` | Read adjustments | `CashAdjustmentController` GET |
| 101 | `BANK_DEPOSIT.POST` | Record EOD bank deposit | `BankDepositController` POST |
| 102 | `BANK_DEPOSIT.REVERSE` | Reverse a posted bank deposit | `BankDepositController` POST `…/uid/{uid}/archive` |
| 103 | `BANK_DEPOSIT.READ` | Read deposits | `BankDepositController` GET |
| 104 | `CASH_PICKUP.READ` | Read cash pickups (write stays on `POS.CASH_PICKUP` = 51) | `CashPickupController` GET |
| 105 | `PETTY_CASH.READ` | Read petty-cash payouts (write stays on `POS.PETTY_CASH` = 52) | `PettyCashController` GET |

14 new codes, band 92–105 used in full. Seed in **V10** (day codes 92–95 +
existing 15–17), **V41** (cash codes 96–103 + existing 48–50), **V43**
(pickup/petty read codes 104–105 + existing 51–52) — i.e. edit the existing
seed migrations in place (pre-stable schema policy). Grant all 14 to ADMIN
(role.id 1) via the same `SELECT 1, p.id FROM permission` pattern.

`@PreAuthorize` on controllers switches to the granular code. The class-level
`@PreAuthorize("hasAuthority('CASH.ADJUST')")` on `CashAdjustmentController`
goes away in favour of per-method annotations.

## Outbox event catalogue (Slice D)

All emitted via the existing `EventPublisher.publish(type, aggregate, key,
payload)` API. Key follows the pattern already in `BusinessDayServiceImpl`
(composite for day, surrogate id for cash docs). Payload keys: stable
identifiers, business data, no transient state (no `Instant.now()`, no
caller IP, no DTO-level computed fields).

| Event type | Emitter | Aggregate | Payload keys |
|---|---|---|---|
| `BusinessDayOpened.v1` | `BusinessDayServiceImpl.openDay` (+ auto-roll) | `BusinessDay` | `uid`, `branchId`, `businessDate`, `openedBy` (+ `autoRolledFrom` when auto-roll) |
| `BusinessDayClosed.v1` | `BusinessDayServiceImpl.closeDay` / `endDay` | `BusinessDay` | `uid`, `branchId`, `businessDate`, `closedBy`, `eodReportObjectKey` |
| `BusinessDayOverridden.v1` | New `BusinessDayOverrideServiceImpl.post` (formalises the today-implicit override write) | `BusinessDayOverride` | `overrideUid`, `branchId`, `targetBusinessDate`, `entityType`, `entityId`, `reason`, `authorisedBy` |
| `CashAdjustmentPosted.v1` | `CashAdjustmentServiceImpl.post` | `CashAdjustment` | `cashAdjustmentUid`, `cashAdjustmentId`, `branchId`, `businessDate`, `account`, `direction`, `amount`, `currencyCode` |
| `CashAdjustmentReversed.v1` | `CashAdjustmentServiceImpl.archiveByUid` | `CashAdjustment` | `cashAdjustmentUid`, `cashAdjustmentId`, `reversingCashEntryId`, `reversedBy`, `reason` |
| `BankDepositPosted.v1` | `BankDepositServiceImpl.post` | `BankDeposit` | `bankDepositUid`, `bankDepositId`, `branchId`, `businessDate`, `amount`, `currencyCode`, `reference` |
| `BankDepositReversed.v1` | `BankDepositServiceImpl.archiveByUid` | `BankDeposit` | `bankDepositUid`, `bankDepositId`, `reversingCashEntryIds` (the two), `reversedBy`, `reason` |

`BusinessDayClosingStarted.v1` is already emitted today; left as-is, not
listed above. Known subscribers in this slice: **none yet** — these are
audit-trail signals plus future hooks (reporting, alerts). That's fine, the
outbox is write-only at the emit point.

## Reversal pattern (cash archive = compensating entry)

CashEntry is append-only. Archiving a `CashAdjustment` or `BankDeposit`
posts a new compensating `CashEntry` in the same transaction; the audit-doc
gets a `reversed_at`, `reversed_by`, `reversed_by_entry_id` (single FK for
adjustment, JSON array or a join table for deposit because it pairs two
entries) and refuses double-reversal.

### Schema additions (edit V45 in place)

```sql
ALTER TABLE cash_adjustment
    ADD COLUMN reversed_at         TIMESTAMP,
    ADD COLUMN reversed_by         BIGINT,
    ADD COLUMN reversed_by_entry_id BIGINT,
    ADD CONSTRAINT fk_cash_adj_reversal_entry
        FOREIGN KEY (reversed_by_entry_id) REFERENCES cash_entry (id);

ALTER TABLE bank_deposit
    ADD COLUMN reversed_at              TIMESTAMP,
    ADD COLUMN reversed_by              BIGINT,
    ADD COLUMN reversed_by_out_entry_id BIGINT,  -- BANK -> compensating OUT
    ADD COLUMN reversed_by_in_entry_id  BIGINT,  -- CASH_BOX -> compensating IN
    ADD CONSTRAINT fk_bank_dep_reversal_out FOREIGN KEY (reversed_by_out_entry_id) REFERENCES cash_entry (id),
    ADD CONSTRAINT fk_bank_dep_reversal_in  FOREIGN KEY (reversed_by_in_entry_id)  REFERENCES cash_entry (id);
```

### Service shape (`archiveCashAdjustmentByUid(uid, reason)`)

Single `@Transactional`:

1. `requireByUid(uid)` — load original; throw 404 if not found, 403 if
   cross-branch.
2. Refuse if `reversedAt != null` (idempotent: same exception, never a
   double-reversal).
3. Build compensating `CashEntry` with:
   - `direction` = opposite of original
   - `amount` / `tenderAmount` / `fxRateSnapshot` / `currencyCode` / `account`
     copied from original
   - `glCategory` = same as original (so the projection cancels cleanly)
   - **`refType` = `CashRefType.CASH_ADJUSTMENT_REVERSAL` (new constant)**,
     `refId` = original adjustment id — keeps the `(ref_type, ref_id,
     direction)` idempotency UNIQUE happy and makes the reversal easy to find
     in queries.
   - `notes` = `"REVERSAL: " + reason`, `actorId` = caller.
4. `cashLedger.postEntry(...)` (the existing path that upserts `cash_book`).
5. Stamp `reversed_at = now`, `reversed_by = actor`,
   `reversed_by_entry_id = newEntry.id` on the original adjustment.
6. Emit `CashAdjustmentReversed.v1`.

### Service shape (`archiveBankDepositByUid(uid, reason)`)

Same pattern, but posts **two** compensating entries (mirror of the
original IN-BANK + OUT-CASH_BOX pair), using
`CashRefType.BANK_DEPOSIT_REVERSAL`. Stamp both new entry ids on the
audit-doc.

### Why a new `*_REVERSAL` ref_type and not just direction-flip on the same `ref_type`

`bank_deposit` posts both an IN and an OUT under the same `ref_type` +
`ref_id`. A direction-flip reversal would collide with the original on the
`(ref_type, ref_id, direction)` UNIQUE. New ref_type sidesteps this and
keeps the cause-chain trivially queryable.

## CashEntry immutability

CashEntry gets `uid` + a JSON-shape pin test, **and nothing else**. No
archive, no activate, no `Inactive` pill. The reversal pattern above is the
only way to undo a posting, and it lives on the audit-doc, not on the
entry. The append-only invariant (DATA-MODEL.md §10.2) is preserved.

## Day aggregate hardening scope

Endpoints to harden on `BusinessDayController`:

- `GET /api/v1/business-days?branchId=` — list (gated `DAY.READ`)
- `GET /api/v1/business-days/current?branchId=` — current OPEN/CLOSING day
- `POST /api/v1/business-days` — open day (existing `DAY.OPEN`)
- `POST /api/v1/business-days/uid/{uid}/start-closing` — gated
  `DAY.START_CLOSING`
- `POST /api/v1/business-days/uid/{uid}/close` — gated `DAY.CLOSE`
- `POST /api/v1/business-days/uid/{uid}/end` — gated `DAY.END`
- `GET  /api/v1/business-days/uid/{uid}/blockers` — gated `DAY.END` or
  `DAY.START_CLOSING`

Note URLs change from `…/{date}` to `…/uid/{uid}` — see ADR 0002. Legacy
date paths can be retired (no external producer holds them yet) or kept as a
404 redirect; engineering's call but cleanest is to retire.

**`BusinessDayOverride` lifecycle** is new wire surface:

- Today the override row is written implicitly inside whichever back-dating
  service needs it. Slice D formalises a `BusinessDayOverrideController` +
  service that:
  - `POST /api/v1/business-day-overrides` — creates the override row
    (called by back-dating producers via cross-module service call).
  - `POST /api/v1/business-day-overrides/uid/{uid}/archive` — voids the
    override **before** the back-dated post lands. Gated
    `DAY.OVERRIDE_VOID`. After the back-dated post has succeeded, the
    override is immutable.
- DTO: `BusinessDayOverrideDto` with `uid`, `id`, branch, target date,
  entity type/id, reason, authorisedBy, `archivedAt`/`archivedBy`.

## Files engineering will touch

### Backend

- `db/migration/common/V1__platform_baseline.sql` — add `uid CHAR(26) NOT
  NULL` + `uk_business_day_uid` on `business_day`.
- `db/migration/common/V9__day_business_day_override.sql` — add `uid` on
  `business_day_override`, plus `archived_at` + `archived_by` columns.
- `db/migration/common/V40__cash_ledger.sql` — add `uid` on `cash_entry`
  and `cash_book` (composite-PK case from ADR 0002).
- `db/migration/common/V42__pos_cash_pickup_petty_cash.sql` — add `uid` on
  `cash_pickup` and `petty_cash`.
- `db/migration/common/V45__cash_adjustment_bank_deposit.sql` — add `uid` on
  `cash_adjustment` and `bank_deposit`, plus the reversal columns spelled
  out above.
- `db/migration/common/V10__seed_day_permissions.sql` — add codes 92–95.
- `db/migration/common/V41__seed_cash_permissions.sql` — add codes 96–103.
- `db/migration/common/V43__seed_pos_pickup_petty_permissions.sql` — add
  codes 104–105.
- Java: every entity in `modules/day/domain/entity/` and
  `modules/cash/domain/entity/` extends `UidEntity`; DTOs gain `uid`;
  repositories gain `findByUid`; services gain `*ByUid` external entry
  points and the two reversal methods; controllers switch to `/uid/{uid}`
  routes and granular `@PreAuthorize`.
- New module wiring: `BusinessDayOverrideService` + `…Impl` + controller,
  `CashRefType.CASH_ADJUSTMENT_REVERSAL` + `BANK_DEPOSIT_REVERSAL`
  constants.
- Tests: per-aggregate `*JsonTest` to pin the wire shape (uid + composite
  components for day/cashbook), service-level tests for both reversal
  paths (happy path + double-reversal refusal + cross-branch refusal).

### Web (`orbix-engine-web`)

- Retrofit `features/day/` to navigate by uid; remove any
  `branchId+date`-keyed URL.
- New `features/cash/` covering cash-book read, cash-entry browse,
  cash-adjustment post + reverse, bank-deposit post + reverse. Use the
  hardened catalog feature as the structural reference.
- Drop the `Inactive` pill from screens where it doesn't apply
  (cash-entry list — append-only).

## Live-testing handoff to qa-engineer

Per the live-testing-first policy, a Playwright spec is the release signal
for this slice. Cover these flows; each runs against a real backend with
seeded fixtures, and axe-core runs on every page visited.

1. **Open day → record an adjustment → close day**. Operator opens today's
   business day, posts a `CASH_ADJUSTMENT` IN on `CASH_BOX`, sees the
   cash-book closing balance move by exactly the amount, runs end-of-day,
   day closes cleanly with no blocker pop-up.
2. **Reverse an adjustment**. Same operator (or a `CASH_ADJUSTMENT.REVERSE`
   holder) hits the row's archive control, reason dialog, confirm; UI shows
   the original as "Reversed" with a link to the reversing entry, and the
   cash-book balance returns to its pre-adjustment value. The reversing
   entry is visible in the entries list as a separate row.
3. **End-of-day bank deposit**. With a cash-box balance > 0, operator
   records a `BANK_DEPOSIT` for the full amount; UI shows two paired
   ledger rows (OUT CASH_BOX, IN BANK), cash-book CASH_BOX closing = 0,
   BANK closing = deposit amount. Then reverse the deposit; balances flip
   back.
4. **No `Inactive` pill on cash-entry / cash-book views**. Explicit
   regression: ensure the immutable views don't render archive lifecycle
   chrome.
5. **uid in URL on every navigation**. Spec asserts the URL pattern on the
   detail pages of day, adjustment, and deposit.

Axe-core must run clean on the main views (`/day`, `/cash/adjustments`,
`/cash/bank-deposits`, `/cash/entries`). Any violation blocks the slice.

## Open questions

None currently; the `*_REVERSAL` ref_type choice was the only ambiguity and
is resolved above. Surface to architect if reversal preconditions need to
expand (e.g. "cannot reverse after day close" — current scope says any
posted adjustment can be reversed until its day is CLOSED; engineering
should confirm against `DayGuard` and raise if there's friction).
