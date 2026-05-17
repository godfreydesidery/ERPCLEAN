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
