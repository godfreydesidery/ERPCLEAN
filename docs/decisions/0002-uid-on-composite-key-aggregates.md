# 0002 — `uid` on composite-PK aggregates (Path A)

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-05-27 |
| Deciders | Godfrey |

## Context

The Orbix identity discipline (CLAUDE.md, ADR 0001's invariants) requires every
externally exposed entity to extend `UidEntity` and carry a Crockford ULID `uid`
as the canonical external handle. URLs address resources by `uid`; numeric `id`
stays in the body for joins. Catalog (`Item`, `ItemGroup`, `Uom`, `VatGroup`,
`ItemBarcode`, `PriceList`) is the reference cohort.

Slice D ("Harden the Day + Cash spine") brings the next set of aggregates under
the hardening checklist:

- `BusinessDay` — composite PK `(branch_id, business_date)` via `@IdClass`.
- `CashBook` — composite PK `(branch_id, account, currency_code, business_date)`
  via `@EmbeddedId`. The composite is load-bearing: the four columns *are* the
  semantic identity of the daily bucket, the projection upserts on them, and
  US-DAY-006 (per-currency variance) reads them directly.
- `CashEntry`, `CashAdjustment`, `BankDeposit`, `CashPickup`, `PettyCash`,
  `BusinessDayOverride` — surrogate-Long PK, easy retrofit.

The forces:

1. **Uid is mandatory on the wire.** The checklist will not pass until
   `BusinessDay` URLs are uid-addressable.
2. **The composite is the natural domain key.** It encodes the multi-tenancy
   predicate (`branch_id`) and the time-bucket (`business_date`,
   `currency_code`, `account`). Producers across modules already hold these
   components — POS opens its till session against `(branchId, businessDate)`,
   the EOD guard chain reads `requireOpenDayFor(branchId, today)`. None of them
   carry a surrogate id today, and none need a uid to do their job.
3. **Cross-aggregate FKs land on the composite, not a surrogate.** `cash_entry`
   joins `(branch_id, business_date)` against `business_day` and
   `(branch_id, account, currency_code, business_date)` against `cash_book`.
   Re-keying would either drop the FK to lookup-by-composite (loses referential
   integrity) or duplicate every composite column **plus** the surrogate (write
   amplification + denormalisation).
4. **No external system references these aggregates by uid yet.** The POS,
   WMS, web, contracts — all address day/cashbook by composite or implicit
   "current day for this branch". A uid is needed for the URL, not for joins.

## Decision

**Path A.** Composite-PK aggregates add `uid CHAR(26) NOT NULL` as an
*external* handle alongside the existing composite. The composite stays the
primary key in the database and the join key for every cross-aggregate FK;
`uid` is what crosses the wire (URLs and `uid` field on response DTOs).

Concretely:

- **URL shape**: `/api/v1/business-days/uid/{uid}` and
  `/api/v1/cash-books/uid/{uid}`. The literal `uid` segment makes it
  unambiguous (CLAUDE.md uid convention). Listing endpoints stay
  `?branchId=&businessDate=` query-keyed — they're enumeration views, not
  resource addresses.
- **Internal service helpers**: methods that already accept the composite keep
  doing so (`requireOpenDayFor(branchId, date)`, `cashLedger.post(... branchId,
  businessDate, account, direction ...)`). They never need uid. Only
  external-entry-point methods take `String uid` (`getDayByUid`,
  `archiveBankDepositByUid`, etc.) and resolve to the entity in one step.
- **Cross-module FKs stay composite.** `cash_entry.branch_id +
  cash_entry.business_date` continues to be the way the cash ledger refers to a
  day; no `business_day_uid` column is added.

## Consequences

### Migration template for composite-PK aggregates

```sql
ALTER TABLE business_day
    ADD COLUMN uid CHAR(26) NOT NULL,
    ADD CONSTRAINT uk_business_day_uid UNIQUE (uid);
```

The entity extends `UidEntity` **even though `@Id` is the composite**. `@IdClass`
and `@EmbeddedId` are compatible with a `MappedSuperclass` adding a non-key
column; the `@PrePersist` hook on `UidEntity` still fires. The uid column is
unique (one ULID per row), nullable false, immutable post-insert — same shape
as on surrogate-PK aggregates.

Existing rows in dev DBs are unaffected because Slice D edits V1/V9/V40 in
place and the DB is recreated (per the project's ephemeral-migrations policy).

### JSON wire shape

Response DTOs include both:

```json
{
  "uid": "01HW4Z9CPSYNTH3YPK5XKQ9V0M",
  "branchId": "42",
  "businessDate": "2026-05-27",
  "status": "OPEN",
  ...
}
```

`branchId` serialises as a string under the global
`IdLongAsStringSerializerModifier` (CLAUDE.md uid convention). `businessDate`
is ISO-8601 as today. No `id` field on these aggregates — the composite *is*
the identity, and we expose its components instead of a synthetic numeric id.

### Repositories

Add `Optional<X> findByUid(String uid)` alongside `findById(compositeKey)`.
The uid index is the secondary lookup path; the composite is still the primary
fetch path for posting flows.

### Tests

Pre-set the uid via reflection on fixtures, same pattern as Item:

```java
ReflectionTestUtils.setField(day, "uid", UidGenerator.next());
```

Pin the wire shape with a small Jackson test alongside each response DTO
(`BusinessDayDtoJsonTest`, `CashBookDtoJsonTest`).

### What gets harder

- DTO construction is one column wider. Trivial.
- ArchUnit and the controller-may-not-touch-repository rule are unaffected;
  the uid lookup goes through the service layer the same way `findById`
  does.

### What gets easier

- Aggregating client-side URLs (e.g. an audit log linking back to the day a
  posting affected) becomes `/api/v1/business-days/uid/{uid}` without
  serialising a composite tuple onto the URL — uniform with every other
  hardened aggregate.
- External producers that want to cite a specific day in their own audit
  trail get a stable single-token reference.

## Alternatives considered

### Path B — Re-key to a surrogate Long PK

Replace the composite with a generated `id BIGINT` and demote
`(branch_id, business_date)` to a UNIQUE constraint; cross-aggregate FKs land
on `business_day_id` instead of the composite.

Rejected because:

1. **Blast radius on `cash_entry`.** Every existing producer (POS, sales,
   procurement, day-close orchestration) calls `cashLedger.post(...,
   businessDate, ...)` with the composite components in hand. Switching to a
   surrogate FK requires either a denormalised `business_day_id` lookup on
   every call site (extra read per post — measurable on the hot POS path) or
   passing `business_day_id` everywhere, which means each module's producer
   needs a back-reference it doesn't have today.
2. **`cash_book` PK includes `currency_code`** since Phase 1.1 §202 — it's
   not just a composite of natural keys but an *intentional partitioning* of
   the projection. A surrogate id would obscure that the row's identity *is*
   "this branch's CASH_BOX for TZS on 2026-05-27".
3. **No cross-aggregate uid join is requested.** Path B's upside (a single
   `business_day_uid` foreign-key column elsewhere) has no consumer — uids are
   exposed at the boundary, not used inside the monolith for joins.
4. **Reversibility.** Path A → Path B is one further migration if a future
   need arises. Path B → Path A is a re-key, considerably more disruptive.

### Path C — Surface the composite directly in URLs (no uid)

`/api/v1/business-days/branch/{branchId}/date/{date}`. Rejected: violates the
project-wide identity invariant (URL = uid), and would have to be reversed
the first time we want to link a day from an external system (audit export,
notification, mobile bookmark).

## References

- [CLAUDE.md uid convention](../../CLAUDE.md) — `uid` URL shape, dual `id`/`uid`
  pattern, migration template.
- [DATA-MODEL.md §11.1, §10.2, §10.3](../../DATA-MODEL.md) — `business_day`,
  `cash_entry`, `cash_book`.
- [Phase 1.1 §202](../decisions/0001-modular-monolith.md) — `cash_book` PK
  extension to per-currency.
- [docs/design/slice-d-day-cash.md](../design/slice-d-day-cash.md) — the
  slice-specific migration / permission / event plan that applies this ADR.
