# UID + JSON:API rollout — progress + remaining work

Resume point. Last commit on `feature` branch: `3b0b550` (party uid migration).
See [CLAUDE.md](CLAUDE.md) for the canonical per-aggregate migration pattern.

## Status

**Foundation (done):**
- `modules/common/domain/entity/UidEntity` — mapped superclass, auto-assigns ULID at `@PrePersist`
- `modules/common/util/UidGenerator` + `@ValidUlid`
- `modules/common/service/IdLongAsStringSerializerModifier` + `JacksonConfig` — globally stringifies every Long field whose name is `id` or ends in `Id` / `By`
- `ItemResponseDtoJsonTest` pins the wire shape

**Migrated entities (13):**

| Module | Entities |
|---|---|
| Catalog (6) | `Item`, `ItemGroup`, `Uom`, `VatGroup`, `ItemBarcode`, `PriceList` |
| Admin (3) | `Branch`, `Section`, `Route` |
| Party (1 + 4 inherit) | `Party` — Customer/Supplier/Employee/SalesAgent share its uid via the role-table PK |

**Tests green for migrated cohort:** 48 catalog + admin + party + JSON + ArchUnit tests pass.

## Remaining externally-exposed entities (~20)

In the recommended order. Each follows the same template — see [CLAUDE.md](CLAUDE.md) "External entity identity is `uid` (ULID)..." section.

### 1. IAM (3) — smallest, contained; finishes the audit-ref story
- [ ] `AppUser` — referenced everywhere as audit actor; JWT `sub` claim
- [ ] `Role`
- [ ] `Permission` — global lookup

**Watch out for:** JWT claim layout; `@PreAuthorize` sites; tests in `UserAdminControllerTest` and `RoleAdminControllerTest`.

### 2. POS (3)
- [ ] `Till`
- [ ] `TillSession`
- [ ] `PosSale` — append-only

### 3. Stock transactional (3)
- [ ] `StockBatch`
- [ ] `StockCount`
- [ ] `StockTransfer`

### 4. Procurement (3) — headers; *Line tables stay internal
- [ ] `LpoOrder`
- [ ] `Grn`
- [ ] `SupplierInvoice`

### 5. Sales (5) — headers; *Line tables stay internal
- [ ] `SalesInvoice`
- [ ] `SalesReceipt`
- [ ] `CustomerReturn`
- [ ] `CustomerCreditNote`
- [ ] `PackingList`

### 6. Cash (3)
- [ ] `SupplierPayment`
- [ ] `BankDeposit`
- [ ] `CashAdjustment`

### 7. Orders (1)
- [ ] `CustomerOrder` — header; lines / payments stay internal

### 8. Production (3)
- [ ] `Bom`
- [ ] `ProductionBatch`
- [ ] `Conversion`

### 9. Giftcard (1)
- [ ] `GiftCard` — optional (often addressed by card-code, may not need uid)

## Intentionally skipped (no migration)

- `Currency` — `code` is its natural external key
- `Company`, `Organisation` — no controllers (set during first-run only)
- `FxRate` — append-only, addressed by `(from, to, at)`
- All `*Line` / `*Allocation` / junction tables (internal, accessed via parent uid)
- Infra: `DomainEvent`, `RefreshToken`, `PartyCodeSequence`, `ItemBranchBalance`, `StockMove`, `BusinessDayOverride`, `PartyContact`, `PartyAddress`, `PriceListItem`, `PriceChangeLog`

## Per-entity checklist (canonical)

For each entity in the remaining list:

1. **Entity** — `extends UidEntity`, add `@UniqueConstraint(name = "uk_<table>_uid", columnNames = {"uid"})` on `@Table`, switch `@EqualsAndHashCode(of = "id", callSuper = false)`.
2. **Migration** — add `uid CHAR(26) NOT NULL` + `CONSTRAINT uk_<table>_uid UNIQUE (uid)` to the existing CREATE TABLE in its V*.sql file (user recreates DB; no Java backfill needed).
3. **Repository** — add `Optional<X> findByUid(String uid)`.
4. **Service interface** — rename external entry-points to `*ByUid(String uid)`. Keep `*ById(Long)` helpers where cross-aggregate id refs come from request bodies.
5. **Service impl** — add `requireXByUid(String)` helper; emit events with `xUid` instead of numeric ids; extract repeated string literals as `private static final String X_UID_KEY = "xUid";` to satisfy SonarLint.
6. **Controller** — `@RequestMapping` unchanged; route shapes flip to `/api/v1/<resource>/uid/{uid}`; `@PathVariable @ValidUlid String uid`; `@Validated` on the class; `Created` Location header at `/uid/{response.uid()}`.
7. **DTO** — response carries both `id` and `uid` (no per-field annotation needed — global modifier handles it).
8. **Tests** — set uid via `ReflectionTestUtils.setField(entity, "uid", UidGenerator.next())`; mock `findByUid` in fixtures.
9. **Angular** — model gains `uid: string`; service URLs flip to `/uid/...`; components track by uid, pass `entity.uid` to delete/update calls.

## Other follow-ups (not strictly uid)

- [ ] **Pre-existing test failures**: 74F + 65E `branchScope is null` errors in sales / stock / procurement / iam tests — from the recent `iam: branch-scope ...` commits (8e53a1c, f64e0e3, 4878946). These are unrelated to uid work but block any "all tests green" goal. Worth a separate diagnostic pass.
- [ ] **POS / WMS Flutter apps** — consume the API; route + DTO changes haven't propagated. POS is currently all mocks (orbix-engine-pos prototype) so this is deferred until real backend wiring.
- [ ] **OpenAPI contract** at [`orbix-engine-contracts/openapi/orbix-engine.yaml`](orbix-engine-contracts/openapi/orbix-engine.yaml) — regenerate after the rollout settles, for any external consumers.
- [ ] **Angular admin route params** — `:id` segments in admin route definitions may still exist alongside the new `/uid/{uid}` API paths. Quick `grep "':id'"` under `src/app/features/admin` to confirm.

## How to resume

1. `git log --oneline -5` to confirm last state.
2. Pick the next module from the **Remaining** list (recommended: IAM next).
3. Use the canonical checklist above; commit at module-cohort granularity (one commit per module, like the previous PRs).
4. After each module: `mvn -Dtest=<Module>ServiceImplTest,ModuleBoundaryTest test` for the green-test sanity check.

## Session record — how we got here

This was a long working session that started in the POS prototype and ended deep in
the backend uid rollout. The journey is worth keeping because several decisions
were revisited and the final shape is the third or fourth version of itself.

### Part 1 — Flutter POS prototype polish

Before the uid work, the session refined the POS app (orbix-engine-pos):

- Supermarket spreadsheet table:
  - Added a separate **Scan code** column for the EAN-13 barcode alongside the
    existing **Code** column (item code). Lookup now tries barcode first, then
    falls back to item code.
  - Combo dropdown under the name column searches barcode + code + name.
  - Hidden scan code from the dropdown suggestions (per user preference — code
    + name + price is enough there).
- Right pane narrowed from 420 → 260 px in supermarket mode (numpad is small),
  other modes keep 420.
- Pay cash / Other / Void buttons stacked vertically directly under the numpad,
  in the same 240 px column. Pay-cash on top, Void at the bottom (furthest from
  the primary to reduce fat-finger risk).

Then completed three pages that were referenced but unreachable:

- `/payment` → fleshed out as a paper-tape **receipt preview** with Reprint /
  Email / Open-drawer / Next-sale actions. Replaces the old completion
  AlertDialog. Pay-cash and Pay-other now route here.
- `/held` → **held-carts drawer**. Hold parks the active cart as `Hxxx` with
  optional note; recall swaps it back into the active cart (with a
  confirm-discard when there's an active cart).
- `/refund` → **refund/return picker**. Search past sales, tick lines + qty,
  capture reason, supervisor PIN (mock `1234`) authorises.

Other completions:
- Login biometric button → mock fingerprint dialog (auto-success after 900 ms).
- Settings Test / Push-now buttons → SnackBar feedback (no longer no-ops).
- Per-line **discount dialog** (% button on each cart line, preset chips,
  warning at ≥15%).

POS supporting infra in `lib/features/_demo/mocks.dart`:
- `HeldCart` + `HeldCartsNotifier`
- `CompletedSale` + `lastSaleProvider` + `recordSale()` helper
- `PastSale` + `mockPastSales` for the refund picker

Commits: `e607007`, `e2da853`, `cdd1ae7`, `b801a1a` (POS prototype era).

### Part 2 — UID + JSON:API rollout

User stated the principle: *"all entities exposed to users and other systems
should have a unique uid (ULID), used in URLs as `/.../uid/{entityUid}/...`"*.

#### Decisions taken (in order)

1. **URL shape**: literal `uid/{uid}` segment, not bare `{uid}` (matches the
   user's example exactly; unambiguous vs. code or numeric-id lookups).
2. **Rollout**: foundation + Item pilot first, then per-aggregate rolloutin follow-up commits. Not a single big-bang refactor.
3. **DTO id treatment** — *this one was revisited twice*:
   - First decision: strip `id` from response DTOs, expose `uid` only. This
     immediately broke `SalesInvoiceLine.itemId: Long` because the dropdown
     had no numeric id to feed back. **Reverted**.
   - Second decision: keep `id` in body, `uid` in URL. Working but inconsistent
     (`"id": "42"` next to `"companyId": 2`).
   - **Final decision**: install a global Jackson `BeanSerializerModifier` that
     stringifies every Long field whose name is `id`, ends in `Id`, or ends in
     `By` (audit-actor user-ids). Per JSON:API discipline; cross-cutting; new
     DTOs get it for free.
4. **Migrations strategy**: user is in dev, will recreate DB. So we edit V*.sql
   in place instead of writing a backfill migration.
5. **Currency stays addressed by `code`** (its natural key), not uid. Same for
   junction tables, line/allocation tables, and infra entities.

#### Cohorts rolled out in order

- **Foundation** (commit `e607007`): `UidEntity` mapped superclass,
  `UidGenerator` (wraps `f4b6a3:ulid-creator`), `@ValidUlid` validator, plus the
  Item pilot. `id` was originally stripped from the DTO.
- **Item refinement** (commit `989a079`): put `id` back in DTOs after
  realising sales-invoice line creation needed it.
- **JSON:API string ids on Item only** (commit `dab2fdb`): `@JsonSerialize(using = ToStringSerializer.class)` on `ItemResponseDto.id`.
- **Catalog completion + global modifier** (commit `1873bd2`): the rest of
  catalog (ItemGroup, Uom, VatGroup, ItemBarcode, PriceList) migrated, and the
  global modifier replaces the per-field annotation. Per-entity annotation
  dropped.
- **Admin + `*By` extension** (commit `56194c6`): Branch / Section / Route +
  the modifier now also stringifies `*By` fields (createdBy, postedBy, etc.).
  Triggered a wide Angular cascade — `*Id: number` → `*Id: string` across 60+
  files in catalog / party / sales / procurement / stock / day / admin.
- **Party** (commit `3b0b550`): Party + role-tables (Customer/Supplier/Employee/SalesAgent
  share Party's PK so they inherit its uid; no separate uid on the role rows).
- **This checkpoint** (commit `40a3e69`): created [UID-MIGRATION-PROGRESS.md](UID-MIGRATION-PROGRESS.md).

#### Important rules / preferences established this session

- **Update migrations in place** (user recreates DB) — don't write Java
  Flyway backfills.
- **Skip pre-existing test failures** — 74F + 65E `branchScope is null`
  errors in sales / stock / procurement / iam tests come from earlier `iam:
  branch-scope ...` commits (8e53a1c, f64e0e3, 4878946). Not in scope for the
  uid work but should be addressed separately.
- **Test fixtures bypass `@PrePersist`** — use
  `ReflectionTestUtils.setField(entity, "uid", UidGenerator.next())`.
- **Lombok already excluded from the fat jar**; use it on entities + DTOs.
- **Per-aggregate commits**, one module per commit, sized like the catalog or
  admin commits.

#### Sample wire shape (current, after the global modifier)

```json
GET /api/v1/items/uid/01HZ8X7M3K9PJK2D7Q5BCN8W4F
{
  "status": true,
  "statusCode": 200,
  "responseCode": "OK",
  "message": "Item retrieved",
  "errors": [],
  "data": {
    "id": "42",
    "uid": "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
    "companyId": "2",
    "code": "BR-001",
    "name": "White bread loaf 600g",
    "itemGroupId": "10",
    "uomId": "20",
    "vatGroupId": "30",
    "avgCost": 2300.0000,
    "lastCost": 2400.0000,
    "status": "ACTIVE"
  }
}
```

All id-like fields stringified, decimals stay numeric, enums stay strings.
This is the shape every migrated module emits.

#### Files that are likely to surprise on resume

- `IdLongAsStringSerializerModifier` matches by **field name**, so any new Long
  field named `notById` or similar would accidentally stringify. Watch for false
  positives if a new field happens to end in `By`.
- `BranchService.requireInCompanyByUid(String)` exists alongside the old
  `requireInCompany(Long)` — both are kept on purpose: cross-aggregate callers
  inside the monolith still pass numeric ids from FKs.
- The `partyService.deactivate(Long)` / `activate(Long)` methods stay as `Long`
  on purpose — the role services resolve the uid → party first, then pass
  `party.getId()`.
- `Currency`, `FxRate`, `Company`, `Organisation` are *intentionally not
  migrated*. Skipping them isn't a TODO — they have natural keys or no
  controllers.
- The `Set<number>` → `Set<string>` and `Record<number, ...>` → `Record<string, ...>`
  changes in role-admin / grns / packing-lists components were part of the
  cascade. If something looks like a typed lie there, double-check the type
  matches the wire value (string).
- The role-grant "company-wide" sentinel switched from numeric `-1` to empty
  string `""` for the new string id space.
