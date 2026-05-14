# Implementation plan

End-to-end vertical slices, ordered by dependency. Each feature spans backend + web (+ POS / WMS where applicable) so that contract issues surface at integration time, not after.

## 👉 Resume here

**Last updated:** 2026-05-14 · **Branch:** `feature` · **Last commit:** `5493a0f` — F2.1 business day.

**Done in Phase 0:**
- F0.1 — first-run setup wizard (backend + web)
- F0.2 — login + JWT (backend + web)
- F0.3 — logout + refresh tokens with theft detection (backend + web)
- F0.4 backend — RBAC wired; ADMIN role + 10 permissions seeded; JWT now carries real `perms[]`
- F0.4b — `RoleAdminService`/`RoleAdminController` (role + permission + grant CRUD, gated by `IAM.MANAGE_ROLES`); web `RoleAdminComponent` + `HasPermissionDirective`; `AuthService` now decodes `perms[]` from the JWT
- F0.5 — active-branch switching: `BranchAccessGuard` + `JwtAuthenticationFilter` 403-on-denied-override; `SessionController` (`/session/branches`, `/session/active-branch`); web branch dropdown in the app shell

**Done in Phase 1:**
- F1.1 — branch + section CRUD (admin module)
- F1.2 — currency + FX rate (admin module)
- F1.3 — catalog: item CRUD + groups (item list/get/patch/archive/activate, `ItemGroup` tree CRUD + move, web catalog screens)
- F1.4 — catalog: barcodes, UoM, VAT groups (`Uom`/`VatGroup`/`ItemBarcode` entities + services + controllers, web UoM/VAT screens + barcode panel)
- F1.5 — catalog: price lists + price-change audit (`PriceList`/`PriceListItem`/`PriceChangeLog`, close+open price flow, `ItemPriceChanged.v1`, web price-list screen + per-item price history)
- F1.6 — catalog: weighed-item + batch-tracking flags (`item.is_weighed`/`weighing_unit` + `item_barcode.barcode_type` via migration `V6`; weighed↔unit + weighed-needs-PLU/embedded-weight-barcode validation; `ItemWeighingChanged`/`ItemBatchTracking*` events; web toggles + barcode-type dropdown)
- F1.7 — party: customer, supplier, employee, sales agent (`party` + `party_address` + `party_contact` + 4 role tables via `V7`; shared-party-by-TIN reuse; per-branch walk-in customer hook; `@Pii` field marker; web screens under `/party/*`)
- F1.8 — **deferred** to Phase 8 (biometric enrolment — not an MVP launch requirement)

**Done in Phase 2:**
- F2.1 — business day open/close/override (`BusinessDay` OPEN→CLOSING→CLOSED state machine + monotonic-date / single-non-closed-day invariants; `business_day_override` audit table via `V9`; `DayGuard` synchronous port; `DAY.*` permissions via `V10`; `BusinessDayController`; web `/day` dashboard)
- F2.2 — stock ledger + balances (`StockMove` append-only + `ItemBranchBalance` cache via `V11`; moving-average cost on inbound, consume-at-average on outbound; negative-stock guard + `STOCK.OVERSELL` via `V12`; posting requires an open day via `DayGuard`; `StockMoved`/`BalanceUpdated`/`LowStockTriggered.v1`; web `/stock/balances` + `/stock/card/:itemId`)

**Next slice (start here):** **F2.3** — stock counts + transfers. Phase-0 test debt still outstanding.

**Pending across all of Phase 0 (tests + docs):**
- Unit + integration tests for F0.1 / F0.2 / F0.3 — none authored yet. F0.4 has `RoleAdminServiceImplTest`; F0.5 has `BranchAccessGuardTest`. Integration/system layers still pending. See [docs/qa/](qa/).
- POS (Flutter) login wiring — deferred until F5.1 (till session work).
- "Logout everywhere" button — backend endpoint exists; UI button still missing.

**How to validate before continuing:** `cd orbix-engine-api && mvn -q clean compile` + `cd ../orbix-engine-web && npx ng build --configuration development`. Both should be silent (zero output = success in `-q` mode).



## How to use this plan

- Pick the next un-blocked feature. Don't jump ahead — dependencies are real.
- A feature is **done** when every checkbox under it ticks AND tests are green AND the DoD is met.
- One feature at a time. Resist parallelising features that touch the same module — merge conflicts swamp the speed gain.
- Cross-references: `docs/qa/modules/*.md` for the per-module test catalogue; `docs/qa/e2e-scenarios.md` for cross-module flows; `docs/design/PHASE-1.1-ADDITIONS.md` for the Phase 1.1 scope spec.

## Conventions

### Sizing
- **S** = 1-2 days
- **M** = 3-5 days
- **L** = 1-2 weeks
- **XL** = 3+ weeks (must be split before starting)

### Test layers per feature

| Layer | Tool | Scope |
|---|---|---|
| **Unit** | JUnit 5 + Mockito (backend) · Karma+Jasmine (Angular) · `flutter test` (Dart) | One class / function. Mocks for collaborators. Runs in seconds. |
| **Integration** | Spring Boot Test + Testcontainers MySQL & Postgres · `flutter integration_test` | One slice of the system with real DB / network. Runs in minutes. |
| **System (E2E)** | RestAssured + Testcontainers full stack · Playwright (web) · Flutter integration_test on emulator (POS / WMS) | One business flow end-to-end as a user would do it. Runs in tens of minutes; CI nightly. |
| **Contract** | Spring Cloud Contract (REST) · Pact (events) | Pinned event payload shapes and REST schemas. Runs in CI per commit. |

### Definition of Done (every feature)

- All backend tasks checked + ArchUnit boundary tests green.
- All web (and POS) tasks checked + lints clean.
- Unit + integration tests green; coverage ≥ 70% line for the new code.
- The specified system test (`TC-E2E-XXX`) passes.
- Domain events emitted are visible in `domain_event` and consumed idempotently.
- Audit log row exists for every state change.
- Migration applies cleanly on **both** MySQL and Postgres profiles.
- README / API docs updated if the public surface changed.

### Status legend

- `[ ]` — not started
- `[~]` — in progress
- `[x]` — done

Update the plan as you go; commit the change alongside the feature.

---

# Phase 0 — Foundation

**Goal:** Bootstrap an empty deployment and log in. Proves the architectural stack works end-to-end before we add real features.

**Modules touched:** `auth`, `admin`, `common`.

## F0.1 — First-run setup wizard

**Story:** US-COMP-001 · **Size:** M · **Status:** `[x]` (commit `a96a206` backend, `a2fdc13` web)
**Dependencies:** none.

**Backend:**
- [x] `AppUser`, `AppUserRepository`, `AuthService` + Impl (already in place).
- [x] `DevSeed` CommandLineRunner for `@Profile("local")`.
- [x] `organisation`, `company`, `branch`, `section` JPA entities under `modules/admin/domain/entity/`.
- [x] `OrganisationRepository`, `CompanyRepository`, `BranchRepository`, `SectionRepository`.
- [x] `FirstRunSetupService` + Impl + DTOs (`FirstRunRequestDto`, `FirstRunResponseDto`).
- [x] `SetupController` exposing `POST /api/v1/setup/first-run` + `GET /status`. Public endpoint.
- [x] Flyway `V3__admin_section_currency_fx.sql` adds `section`, `currency`, `fx_rate` (`till_currency` deferred to F5.1).
- [ ] Outbox events: `OrganisationCreated.v1`, `CompanyCreated.v1`, `BranchCreated.v1`, `SectionCreated.v1` — pending until `EventPublisher` is wired in the bootstrap path.

**Web:**
- [x] `/setup` route with a 4-step wizard component (org → company → first branch → admin).
- [x] `SetupService` calls `POST /api/v1/setup/first-run` + `GET /status`.
- [x] Auto-redirect to `/login` on success, with username pre-filled via router state.

**Tests:**
- **Unit (backend):** `FirstRunSetupServiceTest` — happy path; rejects when org already exists; idempotent on company code.
- **Integration:** `FirstRunIntegrationTest` (Testcontainers MySQL + Postgres) — POST `/first-run`, assert rows + events.
- **System (E2E):** `TC-E2E-001` (modified to start from empty DB) + `TC-ADMIN-001` / `TC-ADMIN-002` / `TC-ADMIN-003`.

**DoD:** A developer with an empty DB hits `/setup` in the browser, fills the form, and is redirected to `/login`. The seeded admin can log in.

---

## F0.2 — Login + JWT (cookie / Bearer)

**Story:** US-IAM-001 · **Size:** S · **Status:** `[x]` (commit `bd1617f` web wiring + ApiResponse envelope)
**Dependencies:** F0.1.

**Backend:**
- [x] `AuthServiceImpl.login` with BCrypt verify, 5-strike / 15-min lockout.
- [x] `AuthController` POST `/api/v1/auth/login`.
- [x] JWT carries `uid` / `cid` / `bid` / `perms[]` (real perms wired in F0.4).

**Web:**
- [x] `LoginComponent` posting username + password.
- [x] `AuthInterceptor` attaches `Authorization: Bearer <jwt>` to all subsequent calls.
- [x] `AuthGuard` redirects unauthenticated users to `/login`.
- [x] Token stored in `sessionStorage`.

**POS (Flutter):**
- [ ] `LoginScreen` (existing stub) wires to backend.
- [ ] `AuthRepository` with secure-storage backed JWT.
- [ ] Biometric login (post-MVP — gated by `tracks_biometrics` feature flag).

**Tests:**
- **Unit (backend):** `AuthServiceImplTest` — happy path, wrong password, locked account, lockout reset.
- **Integration:** `AuthIntegrationTest` (Testcontainers) — full /login → use token → /protected endpoint.
- **Unit (web):** `AuthInterceptorSpec`, `AuthGuardSpec`.
- **System (E2E):** `TC-AUTH-001` .. `TC-AUTH-013`; cross-module `TC-E2E-022` (lockout).

**DoD:** Web user logs in, sees the dashboard, JWT visible in Network tab as Bearer header. POS user logs in, JWT cached, used on next sync.

---

## F0.3 — Logout + refresh tokens

**Story:** US-IAM-002, US-IAM-003 · **Size:** M · **Status:** `[x]` (commit `9fcc325`)
**Dependencies:** F0.2.

**Backend:**
- [x] `RefreshToken` entity + repository (SHA-256 hashed at rest; `@ToString` excludes the hash).
- [x] `AuthServiceImpl.refresh()` — single-use rotation, theft detection revokes all user tokens.
- [x] `AuthServiceImpl.logout()` — revokes current refresh token.
- [x] `AuthController` POST `/refresh`, POST `/logout`, POST `/logout-everywhere`.

**Web:**
- [x] `AuthInterceptor` catches 401, hits `/refresh`, retries the original request (with in-flight latch).
- [x] `AuthService.logout()` calls backend then clears storage + redirects to `/login`.
- [ ] "Logout everywhere" button — backend endpoint exists, UI control missing.

**Tests:**
- **Unit:** `AuthServiceImplTest` covers rotation + theft detection.
- **Integration:** `RefreshTokenIntegrationTest` — issue, rotate, re-use rejected, theft revokes all.
- **System:** `TC-NFR-SEC-005` (refresh tokens single-use).

**DoD:** A 15-minute-old access token transparently refreshes; logout invalidates the token everywhere.

---

## F0.4 — RBAC wiring (permissions in JWT)

**Story:** US-IAM-008, US-IAM-009 · **Size:** M · **Status:** `[x]` (backend `b4b013a`; F0.4b admin UI + endpoints follow-up)
**Dependencies:** F0.2.

**Backend:**
- [x] `Role`, `Permission`, `UserRole` entities + repos (role_permission via `@ManyToMany` join table).
- [x] `RoleAdminService` + Impl + `RoleAdminController` — role CRUD, permission assignment, grant/revoke endpoints, all gated by `@PreAuthorize("hasAuthority('IAM.MANAGE_ROLES')")`. System roles are mutation-locked.
- [x] `AuthServiceImpl.login()` + `refresh()` load permissions via `PermissionResolverService.resolve(userId, companyId, branchId)`.
- [x] JWT `perms[]` populated from resolver instead of `List.of()`.
- [x] Flyway `V4` seeds 10 permissions + ADMIN role + grants all-to-ADMIN. Bootstrap (FirstRunSetupService + DevSeed) assigns ADMIN role to the admin user.

**Web:**
- [x] `RoleAdminComponent` (`/admin/roles`) — create roles, edit details, assign permissions (grouped by module), grant/revoke to users.
- [x] `HasPermissionDirective` (`*orbixHasPermission="'ITEM.CREATE'"`) hides UI per permission; `AuthService` decodes the JWT `perms[]` claim into a signal.

**Tests:**
- **Unit:** `RoleAdminServiceImplTest`, `AuthServiceImplTest` extended to load permissions.
- **Integration:** `RbacIntegrationTest` — assign role, login, verify JWT carries permissions, blocked user gets 403.
- **System:** `TC-AUTH-014`, `TC-AUTH-015`, `TC-NFR-SEC-007`.

**DoD:** A user without `ITEM.CREATE` cannot POST items; an admin with the permission can. UI elements hide accordingly.

---

## F0.5 — Active branch context switching

**Story:** US-COMP-005 · **Size:** S · **Status:** `[x]`
**Dependencies:** F0.4.

**Backend:**
- [x] `JwtAuthenticationFilter` reads `X-Branch-Id` to override JWT's `bid`; when the override differs from the token branch it runs `BranchAccessGuard` and sends 403 if denied.
- [x] `BranchAccessGuard` verifies the user holds an active `user_role` grant covering that branch (branch-specific or company-wide); throws `AccessDeniedException` otherwise.
- [x] `SessionService` + `SessionController` — `GET /api/v1/session/branches` (branches the caller can switch into) and `PUT /api/v1/session/active-branch` (persists `default_branch_id` on `app_user`, access-guarded).

**Web:**
- [x] Branch dropdown in the app shell (shown when the user has >1 accessible branch). On change it calls `PUT /session/active-branch`, persists to `localStorage` (`orbix.activeBranchId`, already read by `AuthInterceptor` as `X-Branch-Id`), then reloads. `AuthService` clears the key on login/logout.

**Tests:**
- **Unit:** `BranchAccessGuardTest` — done (allow / deny / incomplete-context).
- **Integration:** Switching branch then making a request — verify `RequestContext.branchId` matches. *(pending — Phase-0 test debt)*
- **System:** `TC-ADMIN-018`, `TC-ADMIN-019`. *(pending)*

**DoD:** A multi-branch user switches in the dropdown and reads / writes are scoped to the new branch.

---

# Phase 1 — Master data

**Goal:** Every entity that transactional modules reference must exist before transactions can post.

## F1.1 — Branch + section CRUD (admin module)

**Story:** US-COMP-004, US-ADMIN-001, US-ADMIN-002 · **Size:** M · **Status:** `[x]`
**Dependencies:** F0.4.

**Backend:**
- [x] `BranchService` + Impl + `BranchController` — list / get / `POST` / `PATCH` / `POST /{id}/deactivate`, gated by `ADMIN.MANAGE_BRANCHES`. Emits `BranchCreated/Updated/Deactivated.v1`.
- [x] `SectionService` + Impl + `SectionController` — `GET|POST /api/v1/branches/{id}/sections`, `PATCH /sections/{id}`, `POST /sections/{id}/deactivate`, gated by `ADMIN.MANAGE_SECTIONS`. Emits `SectionCreated/Updated/Deactivated.v1`.
- [x] On branch create: auto-create default `MAIN` RETAIL_FLOOR section. *(Per-branch number sequences deferred — `number_sequence` is a `common`-module concern, not yet built; walk-in customer deferred to F1.3 per plan.)*
- [x] Invariants: can't deactivate the last active RETAIL_FLOOR section in a branch; can't deactivate an already-inactive branch/section. *(Open-till / active-BOM checks are TODO markers — those entities land in F5.1 / F7.3.)*

**Web:**
- [x] `BranchAdminComponent` under `/admin/branches` — branch list + create + edit + deactivate, and per-branch section list with create / edit / deactivate. (Single cohesive component, matching the `RoleAdminComponent` precedent rather than the 3-component split originally sketched.)

**Tests:**
- **Unit:** `BranchServiceImplTest` (6), `SectionServiceImplTest` (9) — done.
- **Integration:** Branch deactivate with open till → 422. *(pending — needs the Till entity)*
- **System:** `TC-ADMIN-004` .. `TC-ADMIN-012`. *(pending)*

**DoD:** Admin creates a second branch, adds a Bakery section, sees both in the dropdown. *(Branch dropdown wired in F0.5; the new branch appears once the user has a grant covering it.)*

---

## F1.2 — Currency + FX rate

**Story:** US-ADMIN-003, US-ADMIN-004, US-ADMIN-006 · **Size:** S · **Status:** `[x]`
**Dependencies:** F1.1.

**Backend:**
- [x] `Currency`, `FxRate` entities + repos (pre-existing from V3). *(`TillCurrency` deferred to F5.1 — needs the Till entity.)*
- [x] `CurrencyService` + Impl, `FxRateService` + Impl. FX `effectiveRate(from,to,at)` does the "most recent ≤ time" lookup; `quoteRate` is append-only and rejects same-currency / non-positive / unknown-currency quotes.
- [x] Controllers: `CurrencyController` (`GET`, `POST`, `POST /{code}/enable|disable`, gated `ADMIN.MANAGE_CURRENCIES`); `FxRateController` (`GET`, `POST`, `GET /effective`, gated `ADMIN.MANAGE_FX`). *(`PUT /api/v1/tills/{id}/currencies` deferred to F5.1.)*

**Web:**
- [x] `/admin/currencies` — currency table with enable/disable + add-currency form.
- [x] `/admin/fx-rates` — quote form + rate-history table.

**Tests:**
- **Unit:** `FxRateServiceImplTest` (7) — done (most-recent lookup, same-currency, rate ≤ 0, unknown currency).
- **Integration:** Quote rate, look up at various timestamps. *(pending)*
- **System:** `TC-ADMIN-013` .. `TC-ADMIN-016`. *(pending)*

**DoD:** Admin enables USD, quotes today's rate. *(Till-accepts-USD half deferred with `TillCurrency` to F5.1.)*

---

## F1.3 — Catalog: item CRUD + groups

**Story:** US-CAT-001, US-CAT-002, US-CAT-004 · **Size:** M · **Status:** `[x]`
**Dependencies:** F0.4.

**Backend:**
- [x] `Item` entity + `ItemRepository` + `ItemServiceImpl.create()` + `ItemController` (POST).
- [x] `GET /api/v1/items` (paged, optional `status` filter, `PageDto<T>` envelope), `GET /{id}`, `PATCH /{id}`, `POST /{id}/archive`, `POST /{id}/activate`. Emits `ItemUpdated/Archived/Activated.v1`.
- [x] `ItemGroup` entity + repository + `ItemGroupService` tree-CRUD — `GET`, `POST /api/v1/item-groups`, `PATCH /{id}` (rename), `POST /{id}/move` (re-parents subtree, recomputes `level`, rejects cycles), `POST /{id}/archive`. Migration `V4_2` adds `item_group_seq` (mysql + postgres).
- [x] Soft-delete via `status = ARCHIVED`; archive idempotency guarded. *(Active-promotion / active-batch blocks are TODO — those entities land in F1.5 / F2.4.)*
- [ ] Meilisearch indexer that subscribes to `ItemCreated.v1` / `ItemUpdated.v1` / `BarcodeAdded.v1` — **deferred**: `platform.search` owns the indexer; catalog only emits the events (already does).

**Web:**
- [x] `/catalog/items` list + status filter + paged grid (`ItemListComponent`).
- [x] `/catalog/items/new` and `/catalog/items/:id/edit` (`ItemEditComponent`). *(UoM / VAT-group are raw id inputs pending F1.4 list endpoints.)*
- [x] `/catalog/groups` tree view + move-under-parent (`ItemGroupComponent`). *(Move via parent dropdown rather than drag-and-drop.)*

**Tests:**
- **Unit:** `ItemServiceImplTest` (10), `ItemGroupServiceImplTest` (8) — create / edit / archive / activate / list / move / cycle rejection.
- **Integration:** Create item, search Meilisearch within 1s. *(pending — needs the search module)*
- **System:** `TC-CAT-001` .. `TC-CAT-008`. *(pending)*

**DoD:** Admin creates an item via the web form, sees it in the list, archives it, filters it back. Item-group tree is editable.

---

## F1.4 — Catalog: barcodes, UoM, VAT groups

**Story:** US-CAT-003, US-COMP-006, US-COMP-007 · **Size:** M · **Status:** `[x]`
**Dependencies:** F1.3.

**Backend:**
- [x] `Uom`, `VatGroup`, `ItemBarcode` entities + repos. Migration `V4_3` adds `uom_seq` / `vat_group_seq` / `item_barcode_seq` (mysql + postgres).
- [x] CRUD endpoints — `UomController` (`/api/v1/uoms`), `VatGroupController` (`/api/v1/vat-groups`, + archive), `ItemBarcodeController` (`/api/v1/items/{id}/barcodes`, `DELETE /barcodes/{id}`). Gated by the `ITEM.*` catalog permissions.
- [x] Barcode globally unique (matches the `uk_item_barcode` DDL); VAT group enforces single company default; `BarcodeAdded.v1` emitted.
- [ ] `UomConversion` — **deferred**: no `uom_conversion` table yet (needs its own migration) and no UI in F1.4 scope; revisit alongside pack-conversion work.
- [ ] Weighed-item requires EMBEDDED_WEIGHT / PLU barcode — **deferred to F1.6**: `item.is_weighed` and `item_barcode.barcode_type` are Phase-1.1 columns not yet added.

**Web:**
- [x] `BarcodesPanelComponent` embedded in the item edit screen (add / remove rows).
- [x] `/catalog/uoms` and `/catalog/vat-groups` screens. *(Placed under `/catalog` rather than `/admin` — the backend is the catalog module; linked from the catalog landing page.)*

**Tests:**
- **Unit:** `UomServiceImplTest` (4), `VatGroupServiceImplTest` (7), `ItemBarcodeServiceImplTest` (5) — code uniqueness, single-default, barcode uniqueness, company scoping.
- **Integration:** Create item with 3 barcodes, scan each. *(pending)*
- **System:** `TC-CAT-010` .. `TC-CAT-015`. *(pending)*

**DoD:** UoM + VAT registries are editable; an item can have multiple barcodes added/removed. *(Weighed-item barcode rule lands with F1.6; UoM conversions deferred.)*

---

## F1.5 — Catalog: price lists + price-change audit

**Story:** US-CAT-007, US-CAT-008, US-CAT-014 · **Size:** M · **Status:** `[x]`
**Dependencies:** F1.3, F1.4.

**Backend:**
- [x] `PriceList`, `PriceListItem`, `PriceChangeLog` entities + repos. Migration `V5` creates the three tables; `V5_1` seeds their sequences (mysql + postgres).
- [x] `PriceListService` + Impl + `PriceListController` (`/api/v1/price-lists` CRUD + archive, `GET|PUT /{id}/items`, `GET /items/{id}/price-changes`). `setPrice` **closes** the prior open `price_list_item` row (`valid_to = effective_from − 1`) and **appends** to `price_change_log`; rejects an effective date not after the current row's start. Single company-default invariant. Gated by `ITEM.*`.
- [x] `ItemPriceChanged.v1` emitted on every price set.

**Web:**
- [x] `/catalog/price-lists` — price-list list + create/edit/archive, and a per-list current-price grid with a "set price" form (close+open). *(Form-driven rather than a CSV-style bulk grid.)*
- [x] Price-change-log read-only view per item — `PriceHistoryPanelComponent` embedded in the item edit screen.

**Tests:**
- **Unit:** `PriceListServiceImplTest` (10) — close+open flow, null old-price on first set, effective-date guard, single-default, company scoping.
- **Integration:** Multiple price changes — query log shows full history. *(pending)*
- **System:** `TC-CAT-016` .. `TC-CAT-019`. *(pending)*

**DoD:** Buyer sets a price on a list; the prior row closes and the change is logged; per-item price history shows the trail.

---

## F1.6 — Catalog: weighed-item + batch-tracking flags

**Story:** US-CAT-015, US-CAT-017 · **Size:** S · **Status:** `[x]`
**Dependencies:** F1.3.

**Backend:**
- [x] Migration `V6` adds `item.is_weighed` + `item.weighing_unit` and `item_barcode.barcode_type`. *(`item.is_batch_tracked` already existed from the V2 baseline — it is now mapped on the entity as `batchTracked`; the plan's `tracks_batches` name resolves to that column.)*
- [x] Validation in `ItemServiceImpl.updateItem`: `weighing_unit` set iff `weighed`; a weighed item needs ≥1 PLU / EMBEDDED_WEIGHT barcode. Emits `ItemWeighingChanged.v1`, `ItemBatchTrackingEnabled/Disabled.v1`.
- [ ] Un-set `batch_tracked` / archive blocked when active batches exist — **deferred to F2.4** (`stock_batch` entity doesn't exist yet; TODO markers in place).

**Web:**
- [x] Weighed + batch-tracked toggles and a weighing-unit dropdown on the item edit form; barcode-type dropdown in the barcodes panel.

**Tests:**
- **Unit:** `ItemServiceImplTest` extended (15) — weighed↔unit rule, weighed-needs-capable-barcode, weighing + batch-tracking events.
- **System:** `TC-CAT-025`, `TC-CAT-026`, `TC-CAT-027`. *(pending)*

**DoD:** A "Bananas — KG" item with a PLU barcode can be flagged `weighed = true`, `weighing_unit = KG`; flagging it weighed without such a barcode is rejected.

---

## F1.7 — Party: customer, supplier, employee, sales agent

**Story:** US-PROC-001, US-SALES-001, US-SALES-002, US-HR-001, US-HR-002 · **Size:** L · **Status:** `[x]`
**Dependencies:** F0.4.

**Backend:**
- [x] `Party`, `PartyAddress`, `PartyContact`, `Customer`, `Supplier`, `Employee`, `SalesAgent` entities (migration `V7` + `V7_1` sequences). Role tables share the party PK. `V8` seeds the 4 `PARTY.MANAGE_*` permissions.
- [x] Repos + a service per role (`CustomerService` / `SupplierService` / `EmployeeService` / `SalesAgentService`) over a shared `PartyService`; controllers gated by `PARTY.MANAGE_*`.
- [x] Shared-party logic: `PartyService.resolveOrCreate` reuses an existing company party with the same TIN rather than inserting a new row; a `GET /api/v1/parties/by-tin` lookup backs the web hint.
- [x] Walk-in `customer` per branch — `BranchServiceImpl.createBranch` now calls `CustomerService.createWalkInCustomer` (idempotent).
- [~] PII tagging — `@Pii` field marker added on party PII fields; the audit-log / event scrubbing that consumes it is **deferred** (touches shared `AuditAspect` / `EventPublisher` infra).
- [ ] `PartyAddress` / `PartyContact` CRUD endpoints — entities + tables exist; per-address/contact endpoints **deferred** (core party carries inline phone/email/address).

**Web:**
- [x] `/party/customers`, `/party/suppliers`, `/party/employees`, `/party/agents` list + create screens, sharing a `PartyDetailsFormComponent`.
- [x] On create-customer / create-supplier with an existing TIN: an inline hint shows the matching party and that the role will attach to it (form-driven rather than a modal).

**Tests:**
- **Unit:** `PartyServiceImplTest` (6), `CustomerServiceImplTest` (6), `SupplierServiceImplTest` (4) — shared-party reuse, duplicate-role rejection, walk-in idempotency, company scoping.
- **Integration:** Customer credit-limit + deactivate flow. *(pending)*
- **System:** `TC-PARTY-001` .. `TC-PARTY-018`. *(pending)*

**DoD:** Back-office user creates a B2B customer, a vendor, and an employee; a second supplier created with an existing customer's TIN attaches to that party instead of duplicating it. Walk-in customer is provisioned with every new branch.

---

## F1.8 — Party: biometric enrolment (optional MVP)

**Story:** US-IAM-011, US-IAM-012 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F1.7.

Defer to Phase 8 unless biometric-cashier-login is a launch requirement.

---

# Phase 2 — Transactional foundation

## F2.1 — Business day open / close / override

**Story:** US-DAY-001 .. US-DAY-005 · **Size:** M · **Status:** `[x]`
**Dependencies:** F1.1.

**Backend:**
- [x] `BusinessDay` (composite PK, `@IdClass`) + `BusinessDayOverride` entities. `business_day` ships in V1; `V9` adds `business_day_override`, `V9_1` its sequence, `V10` seeds `DAY.*` permissions.
- [x] `BusinessDayService` + Impl — OPEN → CLOSING → CLOSED state machine; invariants: at most one non-closed day per branch, monotonic business dates (no backdating an open). `BusinessDayController` gated by `DAY.OPEN` / `DAY.CLOSE`.
- [x] `DayGuard` synchronous port — `requireOpenDay(branchId)` for other modules' posting services to consume.
- [~] EOD pre-flight checks — `startClosing` is the hook; the delegated calls into pos / procurement / stock / production are **deferred** until those posting modules exist (TODO marker in place).
- [ ] Scheduled job (auto-warn 30h, auto-revoke override) — **deferred**: the `business_day_override` schema is an audit record (no expiry/standing window), so the plan's `OverrideExpired` model doesn't match; revisit if a standing-override model is wanted.
- [x] Events: `BusinessDayOpened.v1`, `BusinessDayClosingStarted.v1`, `BusinessDayClosed.v1`.

**Web:**
- [x] `/day` dashboard: current-day status for the active branch, open-day form, start-closing + close-day actions (gated by `DAY.*`), recent-days table. *(Override dialog deferred — overrides are written when back-dating posts, which don't exist yet.)*

**Tests:**
- **Unit:** `BusinessDayServiceImplTest` (9) — open / start-closing / close transitions, single-non-closed-day + monotonic-date guards, company scoping.
- **Integration:** Open day → fail EOD (open till) → close after till closes. *(pending — needs the till module)*
- **System:** `TC-DAY-001` .. `TC-DAY-025`; cross-module `TC-E2E-017`, `TC-E2E-018`. *(pending)*

**DoD:** Branch manager opens the day on the `/day` dashboard and ends it (start-closing → close); a second open is rejected while one is non-closed; backdated opens are rejected.

---

## F2.2 — Stock ledger + balances

**Story:** US-STOCK-001, US-STOCK-002, US-STOCK-009, US-STOCK-010 · **Size:** L · **Status:** `[x]`
**Dependencies:** F1.3 (items), F1.1 (branches).

**Backend:**
- [x] `StockMove` (append-only, immutable) + `ItemBranchBalance` (composite-PK cache) entities + repos. Migration `V11` + `V11_1` sequence. Append-only is enforced at the app layer (no update path) — a portable DB-level UPDATE-reject trigger was skipped.
- [x] `StockMoveService.post` updates the balance in the same transaction; moving-average cost on inbound, consume-at-`avg_cost` on outbound. Posting requires the branch's open business day (`DayGuard`).
- [x] Negative-stock guard with `STOCK.OVERSELL` override (`V12` seeds the permission). *(`NegativeStockBlocked.v1` not emitted — a blocking throw rolls back the outbox row; needs a separate-tx emit, deferred.)*
- [x] Events: `StockMoved.v1`, `BalanceUpdated.v1`, `LowStockTriggered.v1`.
- [x] `GET /api/v1/stock-moves` (paged, branch filter), `GET /api/v1/balances`, `GET /api/v1/stock-card`. No POST endpoint — moves are posted by the modules that own the causing document (and F2.5 adjustments).

**Web:**
- [x] `/stock/balances` grid (active branch, low-stock rows highlighted).
- [x] `/stock/card/:itemId` movement ledger per item.

**Tests:**
- **Unit:** `StockMoveServiceImplTest` (8) — moving-average math, oversell block + override, open-day requirement, same-tx event emission.
- **Integration:** Replay stock_move to rebuild balance — match. *(pending)*
- **System:** `TC-STOCK-001` .. `TC-STOCK-004`, `TC-STOCK-012`, `TC-STOCK-013`; `TC-E2E-021`. *(pending)*

**DoD:** A posted move updates `item_branch_balance` atomically (moving-average maintained); the per-item stock card is queryable; oversell is blocked without `STOCK.OVERSELL`.

---

## F2.3 — Stock counts + transfers

**Story:** US-STOCK-004 .. US-STOCK-008 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F2.2.

**Backend:**
- [ ] `StockCount`, `StockCountLine`, `StockTransfer`, `StockTransferLine`.
- [ ] Count lifecycle: DRAFT → IN_PROGRESS → CLOSED → POSTED.
- [ ] Transfer lifecycle: DRAFT → ISSUED (in-transit) → RECEIVED → CLOSED.

**Web:**
- [ ] `/stock/counts` and `/stock/transfers` flows.

**Tests:**
- **Unit:** Variance posting on count close; transfer in-transit accounting.
- **System:** `TC-STOCK-005` .. `TC-STOCK-011`; `TC-E2E-020`.

**DoD:** Storekeeper runs a cycle count; variances post as adjustment moves. Inter-branch transfer flows end-to-end.

---

## F2.4 — Batch tracking + FEFO consumption

**Story:** US-STOCK-011, US-STOCK-012, US-STOCK-013, US-STOCK-014 · **Size:** L · **Status:** `[ ]`
**Dependencies:** F2.2, F1.6.

**Backend:**
- [ ] `StockBatch` entity, `StockBatchRepository`, `StockBatchService` + Impl.
- [ ] On GRN of batch-tracked items: create `stock_batch` row.
- [ ] On consumption: FEFO picker — earliest-expiry ACTIVE batch wins.
- [ ] Scheduled expiry-flag job; `EXPIRY_WRITE_OFF` move type; `BatchRecalled.v1`.

**Web:**
- [ ] `/stock/batches` filter view + expiring-soon report.

**Tests:**
- **Unit:** FEFO picker; cost flows from chosen batch.
- **Integration:** Mixed-batch consumption; expiry job marks batches.
- **System:** `TC-STOCK-014` .. `TC-STOCK-019`; `TC-E2E-009`, `TC-E2E-010`.

**DoD:** Batch-tracked item's stock card shows per-batch breakdown; POS pulls earliest expiry first.

---

## F2.5 — Stock adjustments + internal consumption

**Story:** US-STOCK-003, US-STOCK-015, US-STOCK-016 · **Size:** S · **Status:** `[ ]`
**Dependencies:** F2.2.

**Backend:**
- [ ] `POST /api/v1/adjustments` + supervisor-threshold rule.
- [ ] `POST /api/v1/internal-consumption` with `consumption_category` + `authorised_by_user_id`.
- [ ] Section-tagged moves for section-to-section transfer.

**Web:**
- [ ] `/stock/adjust` form (manager).
- [ ] `/stock/internal-consumption` form.

**Tests:**
- **System:** `TC-STOCK-007`, `TC-STOCK-008`, `TC-STOCK-023`, `TC-STOCK-024`, `TC-STOCK-025`.

**DoD:** Staff canteen draws stock; report shows consumption by category.

---

# Phase 3 — Inbound (Procurement)

## F3.1 — LPO lifecycle

**Story:** US-PROC-002, US-PROC-003, US-PROC-011, US-PROC-012 · **Size:** L · **Status:** `[ ]`
**Dependencies:** F1.7 (suppliers), F1.3 (items), F2.1 (day).

**Backend:**
- [ ] `LpoOrder`, `LpoOrderLine` entities.
- [ ] `LpoOrderService` + Impl. State machine DRAFT → PENDING_APPROVAL → APPROVED → CANCELLED.
- [ ] Auto-approval below configured threshold; approval flow above.
- [ ] PDF / email export via `LpoOrderRenderer` + email subscriber on `LpoOrderApproved.v1`.

**Web:**
- [ ] `/procurement/lpos` list + new / view.
- [ ] PDF preview, "send to supplier" action.

**Tests:**
- **Unit:** State transitions, threshold rule.
- **System:** `TC-PROC-004` .. `TC-PROC-009`.

**DoD:** Merchandiser creates an LPO, sends for approval, manager approves, PDF emails to supplier.

---

## F3.2 — GRN posting (+ batch capture)

**Story:** US-PROC-004, US-PROC-005, US-STOCK-011 · **Size:** L · **Status:** `[ ]`
**Dependencies:** F3.1, F2.2, F2.4.

**Backend:**
- [ ] `Grn`, `GrnLine` entities (line carries `batch_no`, `expiry_at` for batch-tracked items).
- [ ] `GrnService` + Impl. Validates against LPO line quantities; over-receipt rejected.
- [ ] Direct GRN under `GRN.DIRECT` permission.
- [ ] `GrnPosted.v1` → stock module writes `stock_move` rows (and `stock_batch` rows for batch-tracked items).

**Web:**
- [ ] `/procurement/grns` flow; "Receive against LPO" workflow.

**Tests:**
- **Integration:** GRN post → stock_batch + stock_move + avg_cost recomputed.
- **System:** `TC-PROC-010` .. `TC-PROC-015`; `TC-E2E-002`, `TC-E2E-009`.

**DoD:** Storekeeper receives goods against LPO; stock balance updates; batch-tracked items get batch rows.

---

## F3.3 — Supplier invoice match (3-way)

**Story:** US-PROC-006 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F3.2.

**Backend:**
- [ ] `SupplierInvoice`, `SupplierInvoiceGrn` entities.
- [ ] `SupplierInvoiceService` + Impl. Match invoice to one or more GRNs; tolerance check; `SupplierInvoiceMatched.v1`.

**Web:**
- [ ] `/procurement/invoices` match grid (drag GRN lines).

**Tests:**
- **System:** `TC-PROC-017` .. `TC-PROC-021`.

**DoD:** Accountant matches an invoice covering 2 GRNs; supplier debt opens correctly.

---

## F3.4 — Supplier payment

**Story:** US-PROC-007 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F3.3, F6.1 (cash entries).

**Backend:**
- [ ] `SupplierPayment`, `SupplierPaymentAllocation` entities (lives in `cash` module).
- [ ] `SupplierPaymentService` + Impl.

**Tests:** `TC-PROC-022` .. `TC-PROC-025`; `TC-E2E-002`.

---

## F3.5 — Vendor return + credit note

**Story:** US-PROC-008, US-PROC-009 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F3.2, F3.3.

Defer to Phase 8 if not used in pilot.

---

# Phase 4 — Outbound (Back-office Sales)

## F4.1 — Sales quotation

**Story:** US-SALES-003, US-SALES-004 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F1.7 (customers), F1.5 (price lists).

Skip if pilot doesn't need quotations; jump to F4.2.

---

## F4.2 — Sales invoice posting (cash + credit)

**Story:** US-SALES-005, US-SALES-006, US-SALES-007 · **Size:** L · **Status:** `[ ]`
**Dependencies:** F1.7, F1.5, F2.2, F2.1.

**Backend:**
- [ ] `SalesInvoice`, `SalesInvoiceLine` entities.
- [ ] `SalesInvoiceService` + Impl. Credit-limit check; discount-threshold rule; `min_sell_price` rule.
- [ ] Void rule: same business day only.
- [ ] Events: `SalesInvoicePosted.v1`, `SalesInvoiceVoided.v1`.

**Web:**
- [ ] `/sales/invoices` flow; copy-from-quotation; line discounting.

**Tests:**
- **System:** `TC-SALES-001` .. `TC-SALES-013`; `TC-E2E-003`.

**DoD:** Back-office user raises a credit invoice; debt opens.

---

## F4.3 — Sales receipt + allocation

**Story:** US-SALES-008, US-SALES-009 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F4.2, F6.1.

**Backend:**
- [ ] `SalesReceipt`, `ReceiptAllocation` entities + service.
- [ ] Allocation oldest-first by default; surplus held as customer credit.

**Web:**
- [ ] `/sales/receipts` flow; allocation grid.

**Tests:** `TC-SALES-014` .. `TC-SALES-017`; `TC-E2E-003`.

---

## F4.4 — Customer return + credit note

**Story:** US-SALES-010, US-SALES-011 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F4.2.

**Tests:** `TC-SALES-018` .. `TC-SALES-021`.

---

## F4.5 — Packing list

**Story:** US-SALES-012 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F4.2.

**Tests:** `TC-SALES-022` .. `TC-SALES-024`.

---

# Phase 5 — POS (Till)

The biggest phase. Strongly recommend doing F5.1 → F5.2 → F5.4 (offline sync) as a thin slice first, then F5.3 / F5.5 .. F5.10 in any order.

## F5.1 — Till + session lifecycle

**Story:** US-POS-002, US-POS-016 · **Size:** L · **Status:** `[ ]`
**Dependencies:** F1.1, F2.1, F0.2.

**Backend:**
- [ ] `Till`, `TillSession` entities.
- [ ] `TillSessionService` + Impl. Open with float; close with declared cash + variance; supervisor PIN above threshold.
- [ ] `TillSessionOpened.v1`, `TillSessionClosed.v1`.

**Flutter POS:**
- [ ] Till-open screen, cashier+supervisor PIN flow.
- [ ] Till-close screen; declare cash; show variance.

**Tests:**
- **Unit (backend):** Variance math, lockout on second open.
- **Integration:** Open → multiple sales → close.
- **System:** `TC-POS-001` .. `TC-POS-004`, `TC-POS-020`, `TC-POS-021`.

**DoD:** Cashier signs in, opens till, signs out at end of shift.

---

## F5.2 — Basic POS sale (cash tender)

**Story:** US-POS-003, US-POS-004, US-POS-009, US-POS-010 · **Size:** L · **Status:** `[ ]`
**Dependencies:** F5.1, F2.2.

**Backend:**
- [ ] `PosSale`, `PosSaleLine`, `PosPayment` entities.
- [ ] `PosSaleService` + Impl. Validates tender sum, section stamp, day open, stock available.
- [ ] `PosSaleClosed.v1`.

**Flutter POS:**
- [ ] Barcode scan / typeahead → cart.
- [ ] Tender + cash drawer interaction.
- [ ] Thermal-printer receipt.

**Tests:**
- **System:** `TC-POS-005` .. `TC-POS-011`; `TC-E2E-001`.

**DoD:** Cashier scans 5 items, takes cash, prints receipt.

---

## F5.3 — Discounts, voids, mixed tender

**Story:** US-POS-005, US-POS-006, US-POS-009 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F5.2.

**Tests:** `TC-POS-012` .. `TC-POS-015`.

---

## F5.4 — Offline sync

**Story:** US-POS-017, US-POS-018 · **Size:** XL · **Status:** `[ ]`
**Dependencies:** F5.2.

Split into:
- **F5.4a** Local SQLite catalog + balance snapshot pull.
- **F5.4b** Outbox queue + sync push with `client_op_id` idempotency.
- **F5.4c** Conflict resolution + re-order handling.

**Backend:**
- [ ] `POST /api/v1/sync/push` endpoint accepting batched operations.
- [ ] Idempotency on `client_op_id` (UNIQUE on `pos_sale`).

**Flutter POS:**
- [ ] Local DB (Drift): items, prices, recent sales, outbox.
- [ ] Sync poller, retry, backoff.
- [ ] Conflict UI when server rejects.

**Tests:**
- **Integration:** Push duplicate → no second row.
- **System:** `TC-POS-022` .. `TC-POS-025`; `TC-E2E-004`.

**DoD:** POS sells offline for 30 minutes, then catches up the moment network returns.

---

## F5.5 — Refund at till

**Story:** US-POS-019, US-POS-020 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F5.2, F6.1.

**Backend:**
- [ ] `pos_sale.kind = REFUND` flow; threshold rule + supervisor override.
- [ ] Cash refund posts `cash_entry` OUT.

**Tests:** `TC-POS-026` .. `TC-POS-030`; `TC-E2E-005`.

---

## F5.6 — FX tender

**Story:** US-POS-021 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F5.2, F1.2 (currencies).

**Backend:**
- [ ] `pos_payment.tender_currency / tender_amount / fx_rate_snapshot`.
- [ ] Validation: till accepts currency; FX rate exists; functional total == sum(tender × rate).

**Flutter POS:**
- [ ] Tender screen accepts multiple currencies.

**Tests:** `TC-POS-031` .. `TC-POS-035`; `TC-E2E-006`, `TC-E2E-007`.

---

## F5.7 — Gift card tender (depends on F7.1)

Skip until F7.1 lands.

---

## F5.8 — Weighed items + barcode parser

**Story:** US-CAT-016, US-POS-003 (extended) · **Size:** M · **Status:** `[ ]`
**Dependencies:** F1.6, F5.2.

**Flutter POS:**
- [ ] `EmbeddedWeightDecoder` parses EAN-13 starting with `2`.
- [ ] Scale integration via platform channel (USB / serial).

**Tests:** `TC-CAT-013`; `TC-POS-039`; `TC-E2E-008`.

---

## F5.9 — Cash pickup + petty cash

**Story:** US-POS-013, US-POS-014 · **Size:** S · **Status:** `[ ]`
**Dependencies:** F5.1, F6.1.

**Tests:** `TC-POS-016` .. `TC-POS-018`.

---

## F5.10 — X / Z reports

**Story:** US-POS-015, US-POS-016 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F5.2, F5.9.

PDF generation, object-store upload on Z.

**Tests:** `TC-POS-019`, `TC-POS-020`; `TC-NFR-PERF-005`.

---

# Phase 6 — Money ledger (cash module)

## F6.1 — Cash entries + cash book (single currency)

**Story:** scattered across procurement, sales, pos · **Size:** L · **Status:** `[ ]`
**Dependencies:** F2.1.

**Backend:**
- [ ] `CashEntry`, `CashBook` entities; append-only ledger.
- [ ] Event consumers: `TillSessionOpened.v1`, `CashPickupRecorded.v1`, `PettyCashPaid.v1`, `TillSessionClosed.v1`, `SalesReceiptCaptured.v1`, `SupplierPaymentRecorded.v1`, `BusinessDayClosed.v1`.
- [ ] Idempotent on `(source_doc_type, source_doc_id, direction)`.

**Web:**
- [ ] `/cash/ledger`, `/cash/cash-book`.

**Tests:** `TC-CASH-001` .. `TC-CASH-016`.

---

## F6.2 — Multi-currency cash book

**Story:** US-DAY-006 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F6.1, F5.6.

**Backend:**
- [ ] Composite PK extension; per-currency variance.

**Tests:** `TC-CASH-017` .. `TC-CASH-019`; `TC-E2E-007`.

---

## F6.3 — End-of-day banking + supervisor adjustment

**Story:** US-DAY-002 (banking side) · **Size:** S · **Status:** `[ ]`
**Dependencies:** F6.1, F2.1.

**Tests:** `TC-CASH-012` .. `TC-CASH-014`.

---

# Phase 7 — Phase 1.1 extensions

## F7.1 — Gift cards

**Story:** US-GC-001 .. US-GC-008 · **Size:** L · **Status:** `[ ]`
**Dependencies:** F5.2, F6.1.

**Backend:**
- [ ] `GiftCard`, `GiftCardTxn` entities.
- [ ] `GiftCardService` + Impl: issue, redeem, refund, freeze, unfreeze, expire.
- [ ] Scheduled expiry job.

**Web:**
- [ ] `/gift-cards` admin issuance + balance lookup.

**Flutter POS:**
- [ ] Issue-card flow.
- [ ] Redeem-card tender option (in F5.7).

**Tests:** `TC-GC-001` .. `TC-GC-031`; `TC-E2E-015`, `TC-E2E-016`.

---

## F7.2 — Layby + pre-order

**Story:** US-ORD-001 .. US-ORD-009 · **Size:** L · **Status:** `[ ]`
**Dependencies:** F2.2 (stock reservation), F6.1.

**Backend:**
- [ ] `CustomerOrder`, `CustomerOrderLine`, `CustomerOrderPayment` entities.
- [ ] State machine + reservation events.
- [ ] Pre-order triggers `production_batch` via `production` consumer.
- [ ] Expiry job.

**Web:**
- [ ] `/orders` flow.

**Flutter POS:**
- [ ] Layby collection at till — scan order number.

**Tests:** `TC-ORD-001` .. `TC-ORD-024`; `TC-E2E-013`, `TC-E2E-014`.

---

## F7.3 — Production: BOM + batch lifecycle

**Story:** US-PROD-001 .. US-PROD-010 · **Size:** XL · **Status:** `[ ]`
**Dependencies:** F1.3, F2.2, F1.1 (sections).

Split into:
- **F7.3a** BOM + sub-recipes.
- **F7.3b** Plan + start + record output (PROD_CONSUME / PROD_OUTPUT moves).
- **F7.3c** Lifecycle states + wastage.

**Tests:** `TC-PROD-001` .. `TC-PROD-028`; `TC-E2E-011`, `TC-E2E-012`.

---

## F7.4 — Production wastage + conversions

**Story:** US-PROD-007, US-PROD-009 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F7.3.

**Tests:** `TC-PROD-012` .. `TC-PROD-020`.

---

## F7.5 — EOD orchestration (gates + auto-roll + Z-report)

**Story:** US-DAY-002 · **Size:** L · **Status:** `[ ]`
**Dependencies:** F2.1, F5.10, F6.3.

**Tests:** `TC-DAY-006` .. `TC-DAY-010`; `TC-E2E-017`.

---

# Phase 8 — Reporting + hardening

## F8.1 — Stock reporting (card, fast/slow, negative)
**Stories:** US-RPT-004, US-RPT-005, US-RPT-006 · **Size:** M · **Status:** `[ ]`

## F8.2 — Sales reporting (daily, Z-history, margin)
**Stories:** US-RPT-001, US-RPT-002, US-RPT-003 · **Size:** M · **Status:** `[ ]`

## F8.3 — Section P&L
**Story:** US-RPT-011 · **Size:** M · **Status:** `[ ]`

## F8.4 — Production yield + wastage
**Story:** US-RPT-012 · **Size:** S · **Status:** `[ ]`

## F8.5 — Gift card outstanding liability
**Story:** US-RPT-013 · **Size:** S · **Status:** `[ ]`

## F8.6 — Layby ageing
**Story:** US-RPT-014 · **Size:** S · **Status:** `[ ]`

## F8.7 — Customer / supplier statements
**Story:** US-RPT-007 · **Size:** M · **Status:** `[ ]`

## F8.8 — VAT return export
**Story:** US-NFR-COMP-001 · **Size:** M · **Status:** `[ ]`

## F8.9 — Biometric enrolment + login (defer)
**Story:** US-IAM-011, US-IAM-012 · **Size:** M · **Status:** `[ ]`

## F8.10 — WMS module (separate plan; out of current scope)

---

# Cross-cutting work (parallel to phases)

| Feature | Where | Notes |
|---|---|---|
| **Outbox dispatcher hardening** | `common.OutboxDispatcher` | Already scaffolded. Verify dead-letter behaviour under load. Tests: `TC-COMMON-010` .. `TC-COMMON-015`. |
| **Audit chain implementation** | `common.AuditLogWriterImpl` | TODO marker exists. Hash-chained write + verifier job. Tests: `TC-COMMON-004`, `TC-COMMON-005`, `TC-NFR-SEC-008`, `TC-NFR-SEC-009`. |
| **CI matrix: MySQL + Postgres** | `.github/workflows/` | Run every PR. Enforces DB-agnostic correctness. |
| **Migration smoke test** | `TC-E2E-032` | Spins both stacks weekly; diffs sample queries. |
| **Performance baseline** | `TC-NFR-PERF-001` .. `005` | After F5.2 lands, capture p95 baseline; track regressions. |
| **Security audit** | `TC-NFR-SEC-*` | Before pilot launch. |

---

# Recommended ordering (the actual week-by-week)

Treating each `L` as ~10 working days, an `M` as ~4, an `S` as ~1.5 — and one developer working solo:

| Week(s) | Slice |
|---|---|
| 1 | **Thin foundation slice:** F0.1 + F0.2 web wiring (login + admin can boot up an empty DB). |
| 2 | F0.3 + F0.4 + F0.5 (refresh, RBAC, branch switch). |
| 3-4 | F1.1 + F1.2 (branches, sections, currency). |
| 5-6 | F1.3 + F1.4 + F1.5 + F1.6 (catalog full). |
| 7-8 | F1.7 (party). |
| 9 | F2.1 (business day). |
| 10-11 | F2.2 + F2.5 (stock ledger + adjustments). |
| 12 | F2.3 (counts + transfers — defer if time pressure). |
| 13-14 | F2.4 (batches + FEFO). |
| 15-17 | F3.1 + F3.2 (LPO + GRN — biggest inbound feature). |
| 18-19 | F3.3 + F3.4 (invoice match + payment). |
| 20-21 | F4.2 + F4.3 (sales invoice + receipt). |
| 22-23 | F5.1 + F5.2 (till + basic sale — Flutter heavy). |
| 24-25 | F5.4 (offline sync — big risk; allocate buffer). |
| 26 | F5.3 + F5.9 + F5.10 (discounts, pickup, Z). |
| 27 | F5.5 (refund). |
| 28 | F5.6 + F6.2 (FX tender + multi-currency book). |
| 29-30 | F6.1 + F6.3 (cash module wiring — runs alongside earlier work since other modules emit events from week 14 onward). |
| 31-32 | F7.1 + F5.7 (gift cards). |
| 33-34 | F7.2 (layby / pre-order). |
| 35-38 | F7.3 (production). |
| 39 | F7.5 (EOD orchestration). |
| 40 | F8.1 + F8.2 (essential reports). |
| 41+ | F8.3 onwards as needed. |

**Pilot launch target:** end of Week 32 (gift cards + everything below it for one branch). Production extension (F7.3) can join during pilot stabilisation.

---

*This plan is the source of truth for delivery order. Update statuses as features close; raise blockers immediately when discovered.*
