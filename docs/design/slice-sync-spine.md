# Slice Sync — Offline sync spine (design note)

Stories: **US-POS-017** (sell while offline), **US-POS-018** (sync queued ops on
reconnect), **US-WMS-002** (route sync), **US-WMS-005** (capture sale offline).

This is the **design gate** for the offline sync contract. Backend (Java) and
mobile (Flutter/Drift) engineers build against this independently. No backend
Java is implemented here — the current `SyncController` (41 lines) and
`outbox_dispatcher.dart` (30-line stub) are placeholders to be replaced.

Companion artefacts:
- OpenAPI additions drafted in
  [`orbix-engine-contracts/openapi/orbix-engine.yaml`](../../orbix-engine-contracts/openapi/orbix-engine.yaml)
  under tag `Sync` (paths `/api/v1/sync/*`, schemas prefixed `Sync*`).
- This note does **not** create an ADR. Two decisions in it are ADR-worthy and
  flagged inline (§2.4 the device-outbox-is-not-domain-outbox boundary; §3.3 the
  cursor strategy). Promote them if engineering pushes back.

---

## 0. Reading the ground truth first

What already exists and constrains the design (verified against current code,
not memory):

- **`PosSale` is already idempotency-ready.** `pos_sale` carries
  `client_op_id CHAR/VARCHAR(40)` with **`uk_pos_sale_client_op (company_id,
  client_op_id)`** and **`uk_pos_sale_company_number (company_id, number)`**
  (`PosSale.java:19-23,37`). It also separates **`sale_at`** (client wall-clock)
  from **`server_at`** (server receipt instant) and stamps **`business_date`**.
  The double-post defence is therefore a DB-level unique constraint, not
  application logic — this is the spine of §2.3.
- **Push is already per-item isolated.** `SyncServiceImpl.pushBatch` is
  deliberately **not** `@Transactional`; each `PosSaleService.post` runs in its
  own `REQUIRED` tx so one bad sale doesn't roll back the batch
  (`SyncServiceImpl.java:48-71`). The current `SyncPushResultDto.Item` already
  returns `{clientOpId, accepted, posSaleId, errorMessage}`
  (`SyncPushResultDto.java`). We **extend** this shape, we don't reinvent it.
- **Pull snapshots exist but are full-snapshot only.** `catalogSnapshot` and
  `balanceSnapshot` return everything for a branch every call
  (`SyncServiceImpl.java:73-132`). No cursor. §3 adds the delta/watermark layer.
- **The device outbox is NOT the domain outbox.** Server-side
  `domain_event` + `OutboxDispatcher` (`OutboxDispatcher.java`) is the
  cross-module spine and is unrelated to the client's Drift `Outbox` table
  (`database.dart:66-76`). They share the word "outbox" and the at-least-once /
  idempotent-consumer discipline, nothing else. Keep them mentally separate;
  §2.4 states the boundary explicitly.
- **Multi-tenancy is derived, never trusted from the body.** `RequestContext`
  (`RequestContext.java`) is request-scoped and populated by the JWT filter with
  `userId/companyId/branchId`. Every `company_id`/`branch_id` on a synced row is
  stamped from context, not from the client payload (§6).
- **Every response is wrapped.** `ApiResponseDto<T>`
  (`ApiResponseDto.java`) wraps automatically via `ApiResponseBodyAdvice`; the
  Dart client unwraps to `T`. The sync DTOs in this note are the **`T`**, never
  the envelope.
- **Client Drift schema today** (`database.dart`): `Outbox(clientOpId PK,
  opType, payloadJson, createdAt, lastAttemptAt, attemptCount, status)`,
  `PosSales(... synced bool)`. The contract below tells the mobile engineer
  exactly which columns to add (`serverEntityId`, `serverNumber`, `cursor`
  tables) — see §7.4.

---

## 1. Scope and shape in one paragraph

Two endpoints carry the spine: **`POST /api/v1/sync/push`** (client→server,
batched, idempotent, per-item verdicts) and **`GET /api/v1/sync/pull`**
(server→client, cursor-based deltas across reference datasets). Two supporting
endpoints: **`GET /api/v1/sync/bootstrap`** (full snapshot for a fresh/restored
device — the cursor seed) and **`POST /api/v1/sync/till-session/close`**
(reconciliation handshake). All four are gated `POS.SYNC`, derive tenancy from
the JWT, return `ApiResponse<T>`, and are versioned by a contract major carried
in a header. The till never blocks on the network: it commits locally, queues
an op, and the dispatcher drains the queue when connectivity returns.

---

## 2. Push (client → server)

### 2.1 What the client queues

The till's local `Outbox` holds heterogeneous **operations**, each with a
`opType` discriminator. The full op catalogue for Phase 1:

| `opType` | Server effect | Idempotency key | Order-sensitive? |
|---|---|---|---|
| `TILL_SESSION_OPEN` | open `till_session` | `clientOpId` | yes — must precede its sales |
| `POS_SALE` | post `pos_sale` (+ stock drain, cash entry, gift-card per ADR-0004) | `clientOpId` → `uk_pos_sale_client_op` | after its session-open |
| `POS_SALE_VOID` | void an existing `pos_sale` | `clientOpId` | after the sale it voids |
| `CASH_PICKUP` | post `cash_pickup` | `clientOpId` | within its session |
| `PETTY_CASH` | post `petty_cash` | `clientOpId` | within its session |
| `TILL_SESSION_CLOSE` | close `till_session` + reconcile (§4) | `clientOpId` | **last** for the session |

WMS adds `FIELD_SALE` (US-WMS-005) and `ROUTE_VISIT` later; they slot into the
same envelope as new `opType`s. The envelope is op-type-agnostic so adding a
type is a contract-additive change, not a new endpoint.

### 2.2 Request / response envelope

`POST /api/v1/sync/push` body (`SyncPushRequestDto`):

```jsonc
{
  "deviceId": "TILL-3",                 // logical device, audit only; tenancy from JWT
  "clientContractVersion": 1,           // also sent as header; body copy for logging
  "ops": [
    {
      "clientOpId": "01J9Z3...",        // Crockford ULID, client-generated, GLOBALLY UNIQUE per op
      "opType": "POS_SALE",
      "seq": 41,                         // monotonic per-device sequence (see 2.5)
      "occurredAt": "2026-05-30T08:14:03.221Z",  // client wall-clock (-> sale_at)
      "dependsOn": "01J9Z2...",          // clientOpId of the op that must land first; null if none
      "payload": { /* op-type-specific, e.g. PostPosSaleRequestDto shape */ }
    }
  ]
}
```

Notes:
- **`clientOpId` is the canonical idempotency key** and reuses the existing
  Crockford ULID scheme (CLAUDE.md identity discipline). The legacy
  `PostPosSaleRequestDto.clientOpId` comment says "UUID v7" — **the contract
  standardises on Crockford ULID** (26 chars, fits the existing
  `client_op_id CHAR(40)` column with room). Mobile engineer: generate with the
  same ULID lib the device already uses for local ids. ULID's lexical-sort
  property doubles as the natural `seq` tiebreak.
- `payload` for `POS_SALE` is exactly the existing `PostPosSaleRequestDto`
  (minus the redundant `clientOpId` — it moves to the envelope; keep it in the
  payload too during migration for back-compat, server reads the envelope copy).
- The whole batch is one HTTP request but **not one DB transaction** (§2.3).

Response data (`SyncPushResultDto`, the `T` inside `ApiResponse`):

```jsonc
{
  "batchAcceptedCount": 9,
  "batchRejectedCount": 1,
  "serverReceivedAt": "2026-05-30T08:14:05.880Z",  // authoritative clock, for skew est. (§5.2)
  "results": [
    {
      "clientOpId": "01J9Z3...",
      "verdict": "ACCEPTED",            // ACCEPTED | DUPLICATE | REJECTED | DEFERRED
      "serverEntityId": "100482",        // id of the created/affected row (string per JSON:API)
      "serverEntityUid": "01J9...",      // uid of the row, for navigation
      "serverNumber": "POS-3-20260530-00027",  // server-authoritative document number
      "errorCode": null,
      "errorMessage": null
    }
  ]
}
```

Verdicts:
- **`ACCEPTED`** — newly applied. Client marks the outbox row `SENT`, stamps
  `serverEntityId`/`serverNumber` on the local row, clears it from the queue.
- **`DUPLICATE`** — the `clientOpId` was already applied (retry after the
  response was lost). **Server returns the original row's ids** — functionally
  identical to ACCEPTED for the client, which marks `SENT` the same way. This is
  the double-post guarantee made observable.
- **`REJECTED`** — permanent business failure (e.g. price-list gone, item
  archived, validation). Carries `errorCode` + `errorMessage`. Client moves the
  row to a **`NEEDS_REVIEW`** local state for operator attention; it is **not**
  auto-retried. Never silently dropped.
- **`DEFERRED`** — a `dependsOn` op in the same/earlier batch has not yet
  succeeded, so this op can't be applied yet. Client keeps the row `PENDING` and
  re-pushes next cycle. Prevents a sale landing before its session-open.

### 2.3 Idempotency and the no-double-post guarantee

Three layers, defence in depth:

1. **DB unique constraint (authoritative).**
   `uk_pos_sale_client_op (company_id, client_op_id)` already exists. The server
   `INSERT` either succeeds or hits the constraint. On constraint violation the
   service catches it, loads the existing row by `(company_id, client_op_id)`,
   and returns **`DUPLICATE`** with that row's ids. **This is the guarantee.** A
   sale physically cannot double-post because the second insert cannot exist.
2. **Pre-check read (optimisation).** Before insert the service does a
   `findByCompanyIdAndClientOpId` and short-circuits to `DUPLICATE` — saves the
   constraint round-trip on the common retry path, but is *not* the correctness
   mechanism (race between two concurrent retries of the same op is closed by
   layer 1).
3. **Per-op transaction isolation.** Each op applies in its own `REQUIRED` tx
   (as `SyncServiceImpl` already does). A `DUPLICATE`/`REJECTED` op does not
   roll back its batch siblings.

Generalise the `pos_sale` pattern to **every** synced aggregate: `cash_pickup`,
`petty_cash`, `till_session`, future `field_sale` each get a
`client_op_id` column + `uk_<table>_client_op (company_id, client_op_id)`.
That is the schema rule for "this table is reachable from a device outbox".

### 2.4 Ordering and partial-batch failure

- **Ordering is expressed by `dependsOn`, enforced by `DEFERRED`.** The server
  does not assume array order. It applies ops it can, and for any op whose
  `dependsOn` is not yet ACCEPTED/DUPLICATE (in this batch or a prior one), it
  returns `DEFERRED`. The client re-sends DEFERRED ops next cycle. Within a
  batch the server may make multiple internal passes to resolve a dependency
  chain, but the simplest correct implementation is: single pass, defer
  unresolved, let the client re-push. **Recommend single-pass for v1** — the
  re-push cost is one extra round trip and the logic is trivially correct.
- **`seq`** is a monotonic per-device counter. It is **advisory** (ordering is
  `dependsOn`-driven) but lets the server log gaps and lets reconciliation (§4)
  detect "client thinks it sent op 41 but server never saw 38".
- **Partial failure is the norm, not an error.** HTTP status is `200` whenever
  the batch was *processed*, even if every op was REJECTED. The per-op verdict
  array is the real result. HTTP `4xx/5xx` is reserved for transport/auth/tenant
  failures where **nothing** was processed (§6, §5.4).

> **ADR candidate (boundary):** The device outbox is a *transport* queue, not the
> `domain_event` outbox. A synced `pos_sale` still emits its `PosSalePosted.v1`
> domain event inside its own server tx, exactly as a live (online) sale does —
> the sync path and the live path converge on the **same** `PosSaleService.post`,
> so cross-module fan-out (stock, cash, gift-card per ADR-0004) is identical.
> Sync adds no new cross-module call site and needs no `ModuleBoundaryTest`
> exemption. Promote to ADR only if engineering proposes a sync-specific service
> path that bypasses `PosSaleService.post` — that would be the wrong design and
> the ADR would reject it.

---

## 3. Pull (server → client)

### 3.1 Two modes: bootstrap and delta

- **`GET /api/v1/sync/bootstrap`** — full snapshot for a fresh, reinstalled, or
  restored device. Returns every reference dataset the till needs (catalog,
  prices, customers, barcodes, tax groups, branch config; routes for WMS) **plus
  the opening cursor** the device stores. Replaces today's two snapshot calls as
  the cold-start path. Heavy; called once per device lifecycle (and after a
  schema-version reset, §5.5).
- **`GET /api/v1/sync/pull?cursor=<token>&datasets=catalog,price,customer`** —
  incremental. Returns only rows changed since the cursor, per requested dataset,
  plus a **`nextCursor`**. This is the steady-state call (every N seconds when
  online, or on reconnect).

Both return the **same row shapes** per dataset (so the client has one
upsert path). Bootstrap is just "pull from the zero cursor with no page limit
per dataset".

### 3.2 Response shape (`SyncPullResultDto`)

```jsonc
{
  "serverTime": "2026-05-30T08:14:05.880Z",
  "nextCursor": "eyJ2IjoxLCJ0cyI6...",   // opaque token, store verbatim
  "hasMore": false,                       // true => call again immediately with nextCursor
  "datasets": {
    "catalog":  { "upserts": [ /* ItemSnapshot */ ], "deletes": ["100501"] },
    "price":    { "upserts": [ /* PriceRow */ ],      "deletes": [] },
    "customer": { "upserts": [ /* CustomerRow */ ],   "deletes": [] },
    "balance":  { "upserts": [ /* BalanceRow */ ],     "deletes": [] }
  }
}
```

- **`deletes`** carries ids of rows the client must remove (item archived,
  customer deactivated). Soft-delete on the server surfaces as a delete-or-tombstone
  to the client so its local cache stays consistent. Without this, archived items
  linger in the till forever.
- `upserts`/`deletes` are **per dataset** so a client can request a subset
  (`?datasets=catalog,price`) — WMS pulls `route` + `customer`, POS pulls
  `catalog` + `price` + `balance`. Unknown dataset names are ignored (additive
  contract evolution).
- `hasMore=true` + a server-side page cap (e.g. 2000 rows/dataset) keeps a
  long-offline reconnect (§5.1) from returning a 50 MB body. The client loops
  pull until `hasMore=false`, advancing the cursor each time.

### 3.3 Cursor / high-water-mark strategy (DB-agnostic)

> **ADR candidate (cursor strategy):** This is load-bearing and must work
> identically on MySQL 8 / MariaDB 11 **and** PostgreSQL 15 with no vendor
> features (ARCHITECTURE.md §2.3). Promote to ADR; the rest of this note assumes
> the decision below.

**Decision: monotonic `change_seq BIGINT` column, not timestamps, not CDC.**

Every reference table the till pulls gets a **`change_seq BIGINT NOT NULL`**
column, populated from a **single shared DB sequence** `sync_change_seq` on every
insert/update (set in the entity's `@PrePersist`/`@PreUpdate`, or via the
service write path). A soft-delete bumps `change_seq` too and sets a
`deleted boolean`/`status`. The cursor is then literally **the highest
`change_seq` the client has seen**, encoded in the opaque token alongside the
contract version:

```
cursor = base64({ "v": 1, "seq": 998877 })
pull   = SELECT ... WHERE change_seq > :seq ORDER BY change_seq ASC LIMIT :cap
```

Why this and not the obvious alternatives:

| Strategy | DB-agnostic? | Monotonic under concurrency? | Verdict |
|---|---|---|---|
| **`updated_at` timestamp watermark** | yes | **No** — two rows committed in the same clock tick, or a long tx that commits *after* a later-started tx, can be missed by a `> :ts` cursor (the classic "lost update on the boundary" bug). Clock resolution differs MySQL vs PG. | **Rejected** — correctness hole. |
| **Postgres logical replication / `xmin` / CDC (Debezium)** | **No** — Postgres-only, banned by §2.3. | yes | **Rejected** — vendor lock-in. |
| **Shared `change_seq` sequence** | **Yes** — `CREATE SEQUENCE` works on both (MariaDB is why dev uses MariaDB not vanilla MySQL, per CLAUDE.md). `WHERE change_seq > ?` is plain SQL. | **Yes** — a sequence is strictly increasing and assigned at write time; `> :seq` never skips or double-counts. | **Chosen.** |

Caveat the engineer must handle: a sequence value is assigned when the row is
*written* but becomes visible to readers only when its tx *commits*. A reader at
`seq=100` could miss `seq=99` if tx-99 commits after tx-100. Mitigation: pull
with a **small safety lag** — `WHERE change_seq > :seq AND change_seq <= (:maxSeq
- safety)` is *not* needed because each dataset row is written in its own short
tx (reference-data edits are not long transactions), but to be bulletproof the
server caps `nextCursor` at `min(in-flight floor)`. **Simplest robust rule for
v1:** never return a `nextCursor` higher than `(currval - 1)` is overkill;
instead, reference-data writes are short and serialised enough that
`change_seq > :seq` is safe. Document the assumption ("reference-data writes are
short transactions") and revisit only if a bulk price import shows skips. This is
exactly the kind of trade-off to capture in the ADR.

The cursor is **opaque to the client** — it stores and replays the token, never
parses it. That lets the server change the internal encoding (add per-dataset
seqs later) without a client change.

### 3.4 Per-dataset cursors (forward-compat note)

v1 uses **one** global `change_seq` shared by all datasets, so one cursor scalar
covers the lot — simplest. If a single huge dataset (catalog) later needs to
page independently of customers, the opaque token grows to a map
`{catalog: 9988, customer: 7720}` with **zero client change** (token stays
opaque). Design the token as JSON from day one so this is additive.

---

## 4. Reconciliation (till close)

`POST /api/v1/sync/till-session/close` is the handshake that closes the loop. It
is also expressible as a `TILL_SESSION_CLOSE` op in the push batch; the dedicated
endpoint exists because close needs a **richer response** than the generic op
verdict (it returns the server's view of the session for variance display).

Flow:

1. Client has drained its outbox (all sales/pickups/petty for the session are
   `SENT`). It then pushes the close op with the cashier's **declared cash**.
2. Server validates **completeness**: every `pos_sale`/`cash_pickup`/`petty_cash`
   the client claims for this `till_session_id` is present server-side. The
   client includes a **manifest** — `{posSaleCount, posSaleTotal,
   cashPickupCount, cashPickupTotal, pettyCashCount, pettyCashTotal,
   clientOpIds[]}` — so the server can assert "I received exactly what you think
   I received".
3. If the manifest **mismatches** (server is missing an op the client lists, or
   has an extra), close returns **`RECONCILE_INCOMPLETE`** with the
   `missingClientOpIds[]` / `unexpectedClientOpIds[]`. The client re-pushes the
   missing ops and retries close. Close **cannot succeed** while the manifest is
   inconsistent — this is what prevents a session closing with un-synced sales.
4. On match, server computes **`expectedCash`** from what it received
   (`opening_float + cash_sales + cash_pickups_out + petty_out`), stores
   `declared_cash`, `variance = declared - expected` (reusing
   `TillSession.close(...)`, `TillSession.java:94-107`), and returns:

```jsonc
{
  "tillSessionUid": "01J9...",
  "status": "CLOSED",
  "openingFloat": "200000.0000",
  "expectedCash": "1450000.0000",     // server-authoritative
  "declaredCash": "1448000.0000",
  "variance": "-2000.0000",
  "confirmedClientOpIds": ["01J9Z3...", "01J9Z4...", ...],  // every op now durable server-side
  "zReportObjectKey": "z/2026/05/30/till-3.pdf"  // null until EOD generates it
}
```

5. **`confirmedClientOpIds`** is the client's authority to **clear its outbox**.
   Any op in that list is durably committed server-side; the client deletes those
   `Outbox` rows and flips `PosSales.synced = true`. Ops *not* in the list stay
   queued. This is belt-and-braces over the per-op `ACCEPTED`/`DUPLICATE` verdict:
   even if a push response was lost, close re-confirms the full set.

Variance threshold gating (`orbix.pos.variance.*` in `application.yml`) is a
server-side post-close concern (supervisor approval if variance exceeds
threshold) and is **out of scope for the sync contract** — it runs the same
whether the close arrived online or via sync.

---

## 5. Failure and edge cases

### 5.1 Reconnect after a long offline period
- Push: the outbox may hold hundreds of ops. Client sends in **bounded batches**
  (recommend ≤100 ops/request) so a single failure window doesn't re-send
  thousands. Server processes each batch independently; verdicts drive retry.
- Pull: cursor delta may be large → `hasMore` paging (§3.2) caps body size; the
  client loops. If the device has been offline longer than the server's
  change-retention window for tombstones (deletes), the server returns
  **`CURSOR_EXPIRED`** (HTTP 409) and the client falls back to **bootstrap**
  (full re-sync). Set the tombstone retention generously (e.g. keep `deleted`
  rows ≥90 days); a till offline >90 days is a re-provisioning event anyway.

### 5.2 Clock skew
- The till's clock is **untrusted for ordering and business-date**. `occurredAt`
  / `sale_at` is recorded as the client *claims* it, but:
  - **Server stamps `server_at = Instant.now()`** (already done,
    `PosSale.java:138`) — the authoritative receipt time.
  - **`business_date` is server-validated** against the open `business_day` for
    the branch, not taken from client wall-clock. A sale that arrives claiming a
    closed/future business date is pinned to the correct open day or REJECTED with
    `BUSINESS_DATE_INVALID`.
  - Every push response carries **`serverReceivedAt`**; the client computes
    `skew = serverReceivedAt - localNow` and may display a "device clock is N
    minutes off" warning. The contract does not auto-correct the device clock.
- Ordering never depends on clock (§2.4 uses `dependsOn`/`seq`), so skew cannot
  reorder ops.

### 5.3 App reinstall / restore
- A reinstalled device has **no outbox and an empty cursor**. It must
  re-authenticate (JWT), then call **`/sync/bootstrap`** to repopulate reference
  data + cursor. Any sales that were committed locally but lost in the reinstall
  are **gone** unless they'd already been pushed — which is exactly why the
  dispatcher pushes aggressively (5 s interval) and why close (§4) re-confirms.
  This is an accepted data-loss boundary: an offline sale not yet pushed when the
  app's local DB is destroyed cannot be recovered. Document in US-POS-017
  acceptance as a known limitation; mitigation is frequent push + (future)
  encrypted local backup.
- A **restore from backup** replays a stale outbox: ops already applied
  server-side come back as `DUPLICATE` (harmless), genuinely-unsent ops apply.
  The `clientOpId` idempotency makes restore safe by construction.

### 5.4 Schema / contract version mismatch
- **`X-Orbix-Contract-Version: 1`** request header (also `clientContractVersion`
  in the push body for logging). Server compares against the contract majors it
  supports:
  - Client major **<** server min supported → `426 Upgrade Required`,
    `errorCode=CONTRACT_TOO_OLD`. Client must update before syncing. Block sync,
    keep selling offline.
  - Client major **>** server → `409`, `errorCode=CONTRACT_TOO_NEW` (server
    behind; rare, e.g. canary till). Client backs off.
  - Same major, client minor behind → server tolerates (additive changes only
    within a major; new fields the client ignores). This is the normal steady
    state and follows the contract's existing breaking-change rule
    (contracts/README §4 — no silent field removal without a version bump).
- The **client Drift `schemaVersion`** (`database.dart:83`) is independent of the
  contract version. A Drift migration is local-only; it does not gate sync. But a
  Drift migration that *changes how a dataset is stored* should be paired with a
  bootstrap re-pull to avoid half-migrated cache state (§5.5).

### 5.5 Forced re-sync
- Server can include **`resyncRequired: true`** in any pull/push response to tell
  the client "drop your cache and bootstrap" (e.g. price-list reassignment,
  branch reconfiguration, tombstone window exceeded). Client finishes draining
  its push outbox (never lose un-synced sales), then wipes reference caches and
  bootstraps. Outbox is **never** wiped by a resync — only reference data is.

---

## 6. Multi-tenancy

- **`company_id` and `branch_id` are derived from the JWT + branch header by the
  `RequestContext` filter, never read from the sync payload.** The push/pull DTOs
  carry **no** `companyId`/`branchId` fields the server trusts. `deviceId` is
  audit-only.
- On push, every created row (`pos_sale`, `cash_pickup`, …) is stamped with
  `context.companyId()` / `context.requireBranchId()`. A device whose JWT scopes
  it to branch B cannot post a sale into branch A — the stamp is server-side and
  unconditional.
- The idempotency constraint is **`(company_id, client_op_id)`** (already so on
  `pos_sale`). This is deliberate: a `clientOpId` is unique per device, but the
  constraint is tenant-scoped so two companies' devices can never collide and the
  unique index stays selective.
- On pull, the `change_seq` query is **always** filtered by
  `company_id = context.companyId()` (and branch for branch-scoped datasets like
  `balance`). A cursor from company A replayed under company B's JWT returns only
  company B's rows — the cursor is a scalar watermark, not a capability; tenancy
  is re-derived every call. **A cursor leaks no cross-tenant data** because the
  WHERE clause is tenant-pinned regardless of the cursor value.
- `POS.SYNC` permission gates all four endpoints (already on `SyncController`).
  Add granular reads later if needed; one permission is fine for v1.

---

## 7. Acceptance criteria and build sequence

### 7.1 Backend acceptance (US-POS-018)
1. `POST /sync/push` applies each op in its own tx; one REJECTED op does not roll
   back siblings (extends existing `SyncServiceImpl` behaviour to all op types).
2. Re-pushing an already-applied `clientOpId` returns **`DUPLICATE`** with the
   original row's `serverEntityId`/`serverNumber` — **no second row is created**
   (assert via `uk_*_client_op` + a test that pushes the same op twice).
3. `dependsOn` enforcement: a `POS_SALE` whose `TILL_SESSION_OPEN` is absent
   returns `DEFERRED`, applies on the next batch once the open lands.
4. Every `company_id`/`branch_id` is stamped from `RequestContext`, never the
   payload; a cross-branch push is impossible to construct (test with a
   branch-scoped JWT).
5. `GET /sync/pull?cursor=` returns only rows with `change_seq > cursor`, tenant-
   filtered, with a correct `nextCursor`; `hasMore` paging works; `deletes`
   surfaces archived rows. Verified identically on **MySQL and Postgres** (the
   contract test runs the pull assertion against both profiles).
6. `POST /sync/till-session/close` rejects with `RECONCILE_INCOMPLETE` when the
   manifest mismatches; on match returns server-computed `expectedCash`/`variance`
   and the full `confirmedClientOpIds`.
7. Contract-version header handling: too-old → 426, too-new → 409, same-major →
   processed.

### 7.2 Mobile acceptance (US-POS-017)
1. A sale commits to local SQLite and enqueues an `Outbox` row **with zero
   network** (airplane mode); the cashier sees no error and gets a receipt.
2. On reconnect the dispatcher drains the outbox; `ACCEPTED`/`DUPLICATE` rows are
   cleared and `PosSales.synced` flips true; `REJECTED` rows surface in a
   "needs review" list and are **not** auto-retried.
3. Till close blocks until the outbox is drained and the manifest matches; the
   variance shown is the server's `expectedCash`, not a local guess.
4. Reinstall → login → bootstrap repopulates catalog/prices and the cursor; a
   subsequent pull returns only deltas.
5. A restored stale backup re-pushes safely (all `DUPLICATE`, no double sale).

### 7.3 Contract tests that gate the slice (in `orbix-engine-contracts`)
- Spec lint passes; `Sync*` schemas and `/api/v1/sync/*` paths present and
  `bearerAuth`-secured.
- Generated Dart + TS clients compile and expose `push`, `pull`, `bootstrap`,
  `tillSessionClose`.
- A **golden-payload** test: a recorded `SyncPushRequest`/`SyncPushResult` and
  `SyncPullResult` JSON validates against the schema (pins the wire shape the way
  `ItemResponseDtoJsonTest` pins entity DTOs). Idempotency + cursor fields are
  asserted present.
- The backend contract test (ARCHITECTURE.md §9) asserts implemented endpoints
  match the spec — this is the gate that the 41-line stub controller has been
  replaced.

### 7.4 Client Drift schema deltas the mobile engineer adds
- `Outbox`: add `seq INTEGER` (per-device monotonic), `dependsOn TEXT NULL`,
  `serverEntityId TEXT NULL`, `serverNumber TEXT NULL`, extend `status` enum with
  `NEEDS_REVIEW` and `DEFERRED`. Change `clientOpId` to a ULID.
- `PosSales`: add `serverNumber TEXT NULL`, `serverEntityId TEXT NULL` (keep
  `synced`).
- New `SyncCursor(dataset TEXT PK, token TEXT)` table — stores the opaque cursor
  per dataset (one row in v1's single-cursor model; the table generalises to
  per-dataset later).
- New `Customers`, `PriceRows`, and (WMS) `Routes` tables to receive pull
  datasets the schema doesn't model yet.
- Bump Drift `schemaVersion` → 2 with a migration; pair with a one-time bootstrap.

### 7.5 Recommended build sequence
1. **Contract first.** Land the `Sync*` OpenAPI additions (drafted alongside this
   note), regenerate clients, get the golden-payload + lint tests green. This
   unblocks mobile and backend to work in parallel against a frozen shape.
2. **Backend push** — generalise `SyncServiceImpl.pushBatch` to the op envelope +
   `opType` dispatch, add `client_op_id` + `uk_*_client_op` to `cash_pickup` /
   `petty_cash` / `till_session`, wire the verdict mapping. Idempotency tests
   (criterion 7.1.2) gate this.
3. **Backend pull** — add the `sync_change_seq` sequence + `change_seq` column to
   reference tables, the cursor query, `bootstrap`, paging. Dual-DB test
   (7.1.5) gates this.
4. **Backend close/reconcile** — manifest validation + `confirmedClientOpIds`.
5. **Mobile dispatcher** — replace `outbox_dispatcher.dart` stub: drain, verdict
   handling, cursor pull loop, close handshake, Drift schema v2.
6. **WMS reuse** — add `FIELD_SALE`/`ROUTE_VISIT` op types + `route` dataset;
   no new endpoints.

Steps 2–4 are independently testable backend tasks; step 5 depends on 2–4 being
deployed to QA but its Drift + verdict-handling logic can be built against the
frozen contract (step 1) before then.

---

## 8. Open questions for architect / PM

- **Tombstone retention window** (§5.1) — proposed ≥90 days; confirm against
  storage budget and the realistic max-offline for a rural TZ till.
- **Batch size cap** (§5.1) — proposed ≤100 ops/push; tune after a load test.
- **Two ADRs to promote** — the cursor strategy (§3.3) and the "device outbox is
  not the domain outbox, sync reuses `PosSaleService.post`" boundary (§2.4). I'll
  draft both if you green-light the design.
- **WMS field-sale stock model** — US-WMS-005 sells from van stock, which is a
  different on-hand source than branch balance. Out of scope here; flag for the
  WMS slice so the `FIELD_SALE` op's stock-drain target is decided before it
  reuses this spine.
