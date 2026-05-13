# Day module

## 1. Purpose

Owns the **business date** per branch and acts as the system-wide **posting guard**. Every transactional module (sales, pos, stock, procurement, cash, wms, production, debt) consults `day` before writing a dated posting; once a `business_day` is `CLOSED`, postings against that date are rejected unless a live supervisor override covers them. Day also orchestrates the end-of-day flow — it does not generate the artefacts itself, it gates and sequences them.

## 2. Scope

In scope:
- Opening and closing the business day per branch.
- End-of-day pre-flight checks (orchestration only).
- Supervisor override to back-date into a closed day, with expiry window.
- Cross-branch business-day status view.
- Emitting lifecycle events that downstream modules subscribe to.

Out of scope (owned by other modules, day only gates / sequences them):
- Z-report assembly and printing → `pos`.
- Cash-up, float reconciliation, banking entries → `cash`.
- Stock-count posting and variance adjustments → `stock`.
- Supplier-invoice and GRN posting → `procurement`.
- Production batch closure → `production`.

Day orchestrates; downstream modules execute.

## 3. Domain model

Two tables, both small. See DATA-MODEL.md §11 for full attribute lists.

- **`business_day`** — composite PK `(branch_id, business_date)`. Columns: `status` (`OPEN | CLOSING | CLOSED`), `opened_at`, `opened_by`, `closed_at`, `closed_by`, `eod_report_object_key`. **Invariant: at most one row per branch with `status = OPEN`.**
- **`business_day_override`** — `id`, `branch_id`, `target_business_date`, `entity_type`, `entity_id`, `reason`, `authorised_by`, `at`. One row per back-dated posting; reports highlight entities that carry an override marker.

Status transitions: `OPEN → CLOSING → CLOSED`. `CLOSING` is the brief window during which pre-flight checks run and orchestration events fire; no new postings accepted while in `CLOSING`.

## 4. Key business flows

**Open day (US-DAY-001).** Manager opens the next day for a branch. If no `OPEN` row exists, system creates one with `business_date = previous.business_date + 1` (or today, on first ever open). Auto-roll on EOD success creates the next day's record automatically; explicit open is only needed after a skip or initial setup. Emits `BusinessDayOpened.v1`.

**End-of-day per branch (US-DAY-002, PRD §6.6).**
1. Manager invokes EOD; row moves to `CLOSING`.
2. Pre-flight gates (all must pass, or EOD aborts with a list of offending documents):
   - all `TillSession` for the branch on `business_date` are `CLOSED` (consumes `TillSessionClosed.v1`);
   - no unposted GRNs for the branch (consumes `GrnPosted.v1` counter);
   - no orphan production batches;
   - all WMS sales sheets submitted (approval not required).
3. Emit `BusinessDayClosingStarted.v1` — `pos` builds Z-summary PDF, `cash` finalises cash entries, `reporting` snapshots dashboards. Day waits for ack events or a timeout.
4. On all-ack: persist `eod_report_object_key`, set `status = CLOSED`, `closed_at`, `closed_by`, emit `BusinessDayClosed.v1`, auto-create next day's `OPEN` row.

**Override (US-DAY-003).** Supervisor with permission `BUSINESS_DAY.OVERRIDE` requests permission to post into a closed `(branch_id, target_business_date)`. Requires supervisor PIN (verified via `platform.security`) and a written reason. Day issues a time-bounded grant (default 24h, configurable per company); each posting made under the grant writes a `business_day_override` row tagged with the affected `entity_type` / `entity_id`. Expired grants auto-revoke (`BusinessDayOverrideExpired.v1`).

**Auto-warn before expiry (US-DAY-005).** Scheduled job checks `OPEN` business days whose `opened_at` exceeds the configured threshold (e.g. 30h) and notifies the branch manager. Pure read-side; no state change.

**Cross-branch status (US-DAY-004).** HQ persona reads `/api/business-days/status` for a roll-up of `OPEN | CLOSING | CLOSED` per branch with the oldest open date highlighted.

## 5. Module interactions

**Depends on:**
- `platform.security` — supervisor PIN check, permission `BUSINESS_DAY.OVERRIDE`.
- `platform.audit` — every open / close / override is auditable.
- `platform.company` — `branch.timezone` drives date resolution.
- `platform.events` — transactional outbox.

**Publishes:**
- `BusinessDayOpened.v1` — `{branch_id, business_date, opened_by, opened_at}`.
- `BusinessDayClosingStarted.v1` — triggers downstream EOD work.
- `BusinessDayClosed.v1` — `{branch_id, business_date, eod_report_object_key}`.
- `BusinessDayOverrideOpened.v1` — `{override_id, branch_id, target_business_date, authorised_by, expires_at}`.
- `BusinessDayOverrideExpired.v1`.

**Consumes (as gates / counters during EOD pre-flight):**
- `TillSessionClosed.v1` from `pos`.
- `GrnPosted.v1` from `procurement` (pending-count gate).
- `StockCountClosed.v1` from `stock`.
- `ProductionBatchClosed.v1` from `production`.
- `SalesSheetSubmitted.v1` from `wms`.

**Provides to other modules** (synchronous in-process port): `DayGuard.checkPostingAllowed(branch_id, business_date) → ALLOW | DENY(reason) | ALLOW_VIA_OVERRIDE(override_id)`. Every aggregate that holds a `posting_date` calls this in its application service before commit; the returned `override_id` (if any) is stamped on the resulting `business_day_override` row.

## 6. API surface

REST under `/api/business-days`:

- `POST   /api/business-days` — open a day for `{branch_id}`. Idempotent on `(branch_id, business_date)`.
- `POST   /api/business-days/{branch_id}/{business_date}:end` — invoke end-of-day; returns pre-flight failures if any.
- `GET    /api/business-days/{branch_id}/{business_date}` — single-day status with EOD report link.
- `GET    /api/business-days/status` — cross-branch summary (HQ persona).
- `POST   /api/business-days/{branch_id}/{business_date}/override` — request override grant; body `{reason}`; supervisor PIN in header. Returns `{override_id, expires_at}`.
- `GET    /api/business-days/{branch_id}/{business_date}/override` — list overrides used against this day.

All write endpoints emit through the outbox in the same transaction.

## 7. Persistence

- Flyway scripts live under `orbix-engine-api/src/main/resources/db/migration/common/` as `V<N>__day_<purpose>.sql`. DB-specific scripts only if a dialect difference is unavoidable.
- All date columns are SQL `DATE` (DB-agnostic); timestamps are `TIMESTAMP`. No DB-specific date functions.
- `status` is `VARCHAR(32)` string-mapped (`@Enumerated(EnumType.STRING)`) — never an ordinal.
- Unique partial-index simulation: a single `UNIQUE` index on `(branch_id, status)` is **not** safe because `CLOSED` repeats. Enforce the "at most one OPEN per branch" invariant via a unique index on `(branch_id)` filtered to `status = 'OPEN'` where the dialect supports it, otherwise via a check-constraint trigger; the application service additionally guards with `SELECT … FOR UPDATE` on the branch row.
- Append-only audit: closing a day never deletes the row; corrections happen via override, never by re-opening.

## 8. User stories

**P1**
- US-DAY-001 — Open the business day at a branch.
- US-DAY-002 — End-of-day per branch (pre-flight + Z-summary + auto-roll).

**P2**
- US-DAY-003 — Override and post into a closed business day.
- US-DAY-004 — View business-day status across branches.
- US-DAY-005 — Auto-warn before a business day expires.

See USER-STORIES.md Epic 11.

## 9. Open questions

Tracked in PRD §13 and DATA-MODEL.md §16:

1. **Time-zone diversity across branches.** `business_day.date` is anchored in `branch.timezone`; do we permit branches in different zones inside one company, and if so does HQ roll-up display each branch in its local date or normalise? Confirm before MVP cut — drives the cross-branch status view.
2. **Override max window.** PRD allows supervisor override but does not fix the expiry. Default 24h, configurable per company — confirm cap (e.g. hard ceiling 72h) and whether HQ can extend.
3. **Skip-day policy.** If a branch forgets to open a day, does the system auto-open intermediate empty days on next open, or refuse and require manual intervention? Currently leaning auto-open with `opened_by = SYSTEM`.
4. **EOD ack timeout.** What happens if `pos` / `cash` / `reporting` do not ack `BusinessDayClosingStarted.v1` within the window? Proposed: revert to `OPEN`, surface the offending listener, alert.

## 10. Implementation notes

**Layering.** `day` is a **light module** (ARCHITECTURE §2.2): service + repository layout, not full hexagonal. Three packages: `api` (controllers + DTOs), `app` (services + outbound ports), `infra` (JPA repos). No `domain` package — the aggregate is thin.

**Invariants (enforced in the application service, asserted in tests):**
1. At most one `business_day` per branch in `status = OPEN` at any time.
2. `business_date` is monotonically non-decreasing per branch.
3. Any posting carrying `posting_date = d` into branch `b` is rejected if `business_day(b, d).status = CLOSED` and no active override exists for `(b, d)`.
4. Override has a configurable max window (default 24h); expired overrides are auto-revoked by a scheduled job.
5. `CLOSING → CLOSED` only after all subscribed downstream listeners ack or the configured ack-timeout elapses.

**Multi-tenant.** Every row carries `company_id` (via `branch_id`'s FK) and `branch_id`. **Business day is per-branch, not per-company** — branches close independently. HQ roll-up is a read-side projection only.

**Time-zone.** `business_day.business_date` is stored as a date in `branch.timezone`. The API boundary accepts and emits ISO-8601; conversion between branch-local date and UTC timestamp happens in `app`, never in `infra` and never in the client. `opened_at` / `closed_at` are UTC `TIMESTAMP`.

**Idempotency.** `POST /api/business-days` is idempotent on `(branch_id, business_date)` — replays return the existing row. EOD invocation is idempotent while `status = CLOSING`. Override creation is idempotent on a client-supplied `Idempotency-Key` header.

**Outbox.** All event emissions (`BusinessDayOpened.v1`, `BusinessDayClosingStarted.v1`, `BusinessDayClosed.v1`, `BusinessDayOverrideOpened.v1`) write to the transactional outbox in the same DB transaction as the state change — never after commit, never via direct broker call from the service.

**Testing.** ArchUnit blocks other modules from reaching into `day.infra`. Integration tests cover: open/close happy path, EOD with one failing gate, override grant + use + expiry, monotonic-date invariant under concurrent open attempts.
