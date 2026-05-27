# Hardening Definition of Done

**What this is.** The canonical, acceptance-testable checklist for promoting a ported-but-immature module to the same bar as the **catalog cohort** (`Item`, `ItemGroup`, `Uom`, `VatGroup`, `ItemBarcode`, `PriceList`). Source of truth for what a "Slice X — Harden Y" PR must contain.

**When to follow it.** Whenever a `harden/<module>-<aggregate>` branch is opened. The PR body should paste the relevant sections below verbatim and tick each box. Reference precedent: `harden/catalog-uom` (commit `6f3105e`) and `harden/catalog-price-list` (commits `e2a0954`, `470ad1f`, merged at `fe49912`).

**Ownership.** Sections 1–7 + 9 are backend (orbix-engine-api). Section 8 is web (orbix-engine-web). Section 10 is whoever ran the QA-image smoke. Sections cite `CLAUDE.md` rather than restating them.

---

## 1. Data layer (Flyway + entity mapping)

- [ ] **Migration touches the existing baseline, not a new file**, unless the schema is already stable. Pre-stability hardening edits the existing `V<N>__<module>_<purpose>.sql` and rebuilds the DB (per CLAUDE.md memory: "ephemeral migrations").
- [ ] If a follow-up migration is genuinely needed (post-stability or seed-only), it lives in `db/migration/common/` and uses the next free `V<N>__...` number (see `V66__catalog_price_list_hardening.sql`, `V67__seed_price_permissions.sql` for the shape).
- [ ] **Dialect-specific scripts** only when unavoidable, under `db/migration/mysql/` or `db/migration/postgres/`, with a `-- DIALECT-SPECIFIC: <reason>` comment.
- [ ] **No native SQL features** banned by CLAUDE.md "Persistence policy" (no JSONB ops, no FULLTEXT, no IDENTITY, no listen/notify, no Oracle hints). JPQL or CriteriaBuilder only.
- [ ] **Table carries `uid CHAR(26) NOT NULL` + `CONSTRAINT uk_<table>_uid UNIQUE (uid)`** (CLAUDE.md "uid is the URL identifier"). Example:
  ```sql
  uid CHAR(26) NOT NULL,
  CONSTRAINT uk_<table>_uid UNIQUE (uid),
  ```
- [ ] **Multi-tenant tables carry `company_id BIGINT NOT NULL`** (and `branch_id` where applicable) with FK to `company`. Globally-shared reference tables (e.g. `uom`) are explicitly justified in the module README — they are the exception, not the rule.
- [ ] **`@Id BIGINT` via `SEQUENCE` strategy, one sequence per table**, `allocationSize = 50`. Dialect companion migration bumps the sequence past any hardcoded seed ids (see `V4_1` precedent in `V4__seed_permissions_and_admin_role.sql`).
- [ ] **FK on every cross-table reference**, with explicit name `fk_<child>_<parent>` and a deliberate `ON DELETE` policy (default `NO ACTION` — soft-delete via `status`, never hard-delete).
- [ ] **Indexes** on every column listed in a `findBy…` repository method plus every FK; effective-date / window tables have a composite `(parent_id, child_id, …, valid_from)` index (see `ix_price_list_item_effective`).
- [ ] **Decimal precision** matches DATA-MODEL.md: money `DECIMAL(18,4)`, conversion factors `DECIMAL(18,8)`, rates `DECIMAL(10,4)` fraction. Counts and quantities `DECIMAL(18,4)`.
- [ ] **Audit columns present and `NOT NULL`**: `created_at`, `updated_at`, `created_by`, `updated_by`, `version INT NOT NULL DEFAULT 0` (for `@Version`).
- [ ] Entity **extends `UidEntity`**, has its own `@Id`, and declares the `uk_<table>_uid` constraint in `@Table(uniqueConstraints = …)` (see `Uom.java`, `PriceList.java` for the pattern). Lombok `@Data` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + `@EqualsAndHashCode(of = "id", callSuper = false)`.
- [ ] `ddl-auto=validate` still passes after the migration runs (verified by app boot during section 10).

## 2. Domain layer (DTOs + enums)

- [ ] **Every class in `domain/dto/` ends with `Dto`**, including nested records (e.g. `SalesInvoiceDto.LineDto`). Request DTOs are named `<Verb><Aggregate>RequestDto` (see `CreateUomRequestDto`, `UpdatePriceListRequestDto`).
- [ ] **Immutable DTOs are Java `record`s**; mutable entities use Lombok. No `@Data` on DTOs.
- [ ] **Response DTOs include both `id` (Long) and `uid` (String)**. No annotation needed — `IdLongAsStringSerializerModifier` stringifies on the wire (see `ItemResponseDto`).
- [ ] **Bean validation on every request DTO field**: `@NotBlank`, `@NotNull`, `@Size`, `@Min`, `@Pattern`, `@PositiveOrZero` as appropriate. Money fields use `@DecimalMin`/`@Digits`.
- [ ] **Enums live in `domain/enums/`**, never inline. `@Enumerated(EnumType.STRING)` everywhere — never `ORDINAL`.

## 3. Service layer

- [ ] **Interface `XxxService` + concrete `XxxServiceImpl`** pair (CLAUDE.md "service interface + impl"). Aspects/configs/filters stay concrete.
- [ ] `XxxServiceImpl` is `@Service @RequiredArgsConstructor` with `final` collaborators.
- [ ] **Every public method has a transactional boundary**: `@Transactional(readOnly = true)` for reads, `@Transactional` for writes. No transactional plumbing in controllers.
- [ ] **Write methods carry `@Auditable(action = "...", entityType = "...")`** (see `UomServiceImpl`).
- [ ] **External entry points take `String uid`**: `getXByUid`, `updateXByUid`, `archiveXByUid`, `activateXByUid`. Internal joins on `Long id` stay in the service; expose `requireXByUid(String)` + `requireXById(Long)` private helpers when both lookups are needed (see `PriceListServiceImpl.requireItemByUid` / `requireItemById`).
- [ ] **Tenant predicate enforced in service**: every `requireXByUid` re-checks `!Objects.equals(entity.getCompanyId(), context.companyId())` and throws `NoSuchElementException` on mismatch (see `PriceListServiceImpl#requirePriceListByUid`). Global tables (e.g. `uom`) skip this check, with the module README explicitly stating why.
- [ ] **Lifecycle is `ACTIVE` ↔ `ARCHIVED` via dedicated methods** (`archiveXByUid` / `activateXByUid`). Re-archiving an `ARCHIVED` entity or re-activating an `ACTIVE` one throws — see `UomServiceImpl#archiveUomByUid`.
- [ ] **Cross-module side effects emitted via outbox**, never via `ApplicationEventPublisher` or direct service calls into another module: `events.publish("<Aggregate><Action>.v<N>", "<Aggregate>", String.valueOf(id), Map.of(...))` (see `PriceListServiceImpl` price-change events).
- [ ] **No business logic in private static helpers without a clear seam** — keep validation/approval rules as named private methods on the service so tests can reach them.

## 4. Repository layer

- [ ] **Spring Data JPA only**, `extends JpaRepository<X, Long>`. No `@Query(nativeQuery = true)` — wrap behind a dialect adapter if truly unavoidable, with an ADR.
- [ ] **`Optional<X> findByUid(String uid)`** on every uid-exposing entity.
- [ ] **Existence / lookup methods named after the columns**: `existsByCompanyIdAndCode`, `findByCompanyIdAndIsDefaultTrue`, etc. (see `UomRepository`, `PriceListRepository`).
- [ ] No repository accessed from a controller — `ModuleBoundaryTest#controllers_do_not_use_repositories` enforces.
- [ ] No cross-module repository or entity reach-through — `ModuleBoundaryTest#modules_only_depend_on_published_dtos_or_infrastructure` enforces.

## 5. REST layer (controller)

- [ ] **URL shape `/api/v1/<resource>/uid/{uid}`** for uid lookups. Literal `uid` segment so it cannot be confused with code or numeric id.
- [ ] **All `{uid}` path variables annotated `@ValidUlid`**: `@PathVariable @ValidUlid String uid`.
- [ ] **All write endpoints carry `@PreAuthorize("hasAuthority('<MODULE>.<ACTION>')")`** — never invent a parallel "privilege" concept (CLAUDE.md "permission, not privilege").
- [ ] **Request bodies validated with `@Valid`** and the class is `@Validated` at the type level.
- [ ] **No manual `ApiResponse` wrapping in controllers** — the envelope is auto-applied. Return raw DTOs / `ResponseEntity<DTO>`.
- [ ] **`POST` returns `ResponseEntity.created(URI.create("/api/v1/<resource>/uid/" + dto.uid()))`** with the canonical Location header.
- [ ] **State-transition endpoints are `POST .../uid/{uid}/{action}`** returning **`200 OK` with the updated DTO** in the response body (see `GrnController#post`, `GrnController#cancel`, `BankDepositController#archive`, `BomController#activate`). Callers refresh from the response without a follow-up `GET`. *Exception: bulk operations (≥ N rows) may return `204` to avoid serialising large arrays — case-by-case in the endpoint review.*
- [ ] Controller lives flat in `com.orbix.engine.api.<Resource>Controller`, not in a module subpackage.

## 6. Permissions

- [ ] **Seed migration follows the `V<N>__seed_<feature>_permissions.sql` pattern** (template: `V8__seed_party_permissions.sql`, `V65__seed_uom_permission.sql`, `V67__seed_price_permissions.sql`). Inserts both `permission` rows AND grants them to role 1 (ADMIN):
  ```sql
  INSERT INTO permission (id, code, description, module) VALUES
      (<id>, '<MODULE>.<ACTION>', '<description>', '<module>');

  INSERT INTO role_permission (role_id, permission_id)
  SELECT 1, p.id FROM permission p WHERE p.id IN (<ids>);
  ```
- [ ] **Permission ids are hard-coded** in a range that cannot collide with `permission_seq` (bumped past 100 in `V4_1`) **and** does not collide with any other seed migration's band. Verify with `grep -EHn "^\s*\([0-9]+, '" orbix-engine-api/src/main/resources/db/migration/common/V*permission*.sql` before picking the range — pick a free block above the current high-water mark.
- [ ] **Constants added to `com.orbix.engine.modules.iam.domain.enums.Permissions`** in the right module section (see how catalog adds `UOM_MANAGE`, `PRICE_LIST_*`, `PRICE_SET`, `PRICE_APPROVE`).
- [ ] **Permission codes follow `MODULE.ACTION`** convention, all-caps, underscores in action.

## 7. Tests

- [ ] **`XxxServiceImplTest`** under `src/test/java/.../service/`, `@ExtendWith(MockitoExtension.class)`, mocking the repository + `RequestContext`. Covers happy path, validation errors, lifecycle transitions, tenant-isolation, and every approval / threshold branch.
- [ ] **`@PrePersist` bypassed via reflection** in fixtures — never call `entityManager`:
  ```java
  ReflectionTestUtils.setField(entity, "uid", UidGenerator.next());
  ```
- [ ] **JSON wire-shape pin** for every response DTO that crosses an external boundary, modelled on `ItemResponseDtoJsonTest`: registers `IdLongAsStringSerializerModifier` locally, asserts `id` and every `…Id` Long stringifies, and that genuine numerics stay numeric. One test per major response shape.
- [ ] **`ModuleBoundaryTest` is still green** after the change (run `mvn test -Dtest=ModuleBoundaryTest`). If a new cross-module dependency forced a relaxation, that is an ADR-level change — escalate, don't relax the rule.
- [ ] **Full test suite passes**: `mvn -pl orbix-engine-api test` (use system `mvn`, no wrapper — CLAUDE.md memory).
- [ ] No mocks of `EventPublisher` that hide event emission — at least one assertion that the expected outbox event was published with the right type and payload keys.

## 8. Web (Angular)

- [ ] **Model declares `id: string` and `uid: string`** (see `catalog.models.ts` — `Item`). Every `…Id` field on the model is also `string` because Long-id fields stringify on the wire.
- [ ] **Routing and navigation use `uid`**, never `id`. Edit pages track the entity via `editingUid: signal<string | null>`.
- [ ] **Service calls the new uid endpoints**: `GET /<resource>/uid/{uid}`, `PATCH /<resource>/uid/{uid}`, `POST /<resource>/uid/{uid}/archive`.
- [ ] **Component renders all four states**: loading skeleton, empty, error (dismissable alert), populated. Verify by toggling network state in DevTools.
- [ ] **Standalone component**, no NgModules. Lazy-loaded route registered in the feature `*.routes.ts`.
- [ ] **`npm test`** passes for the feature. **`npm run e2e`** (axe-core inside Playwright) green on the new pages — accessibility is a CI gate.

## 9. Cross-module events

- [ ] Each outbox event has a **versioned type name**: `<Aggregate><Action>.v1` (precedent: `ItemPriceChanged.v1`, `ItemPriceDiscontinued.v1`, `BarcodeAdded.v1`).
- [ ] **Event documented in the module README** ("Published events" table, see `catalog/README.md §5`). New event semantics are an ADR-level change — escalate.
- [ ] Payload is a `Map<String, Object>` of stable keys (entity ids, business data needed by subscribers). No entity references, no transient session state.
- [ ] Emission is in the same `@Transactional` write that produced the side effect — outbox row commits or rolls back with the business row.

## 10. Verification (QA-image smoke)

Per CLAUDE.md "Default: start local in QA parity":

- [ ] **Rebuild the QA image** after the change: `docker build -f orbix-engine-infra/qa/Dockerfile -t orbix:qa .`
- [ ] **Wipe the volume and restart** so Flyway re-runs cleanly: `docker rm -f orbix; docker volume rm orbix-data-local; docker run …` (full command in CLAUDE.md).
- [ ] **Wait for `/actuator/health` to return `{"status":"UP"}`** (~30–60 s first boot). This proves `ddl-auto=validate` is happy with the new schema.
- [ ] **Log in as `rootadmin`** at `http://localhost:8081/` and exercise the four flows: list, create, edit, archive (and activate-back for archive). All four states (loading / empty / error / populated) seen, either via screenshots in the PR body or noted as "verified manually" with timestamps.
- [ ] Any new event emitted is visible in the `domain_event` table after the corresponding write.

---

## What this checklist is NOT

This checklist is **not a substitute for an ADR**. It encodes the implementation pattern; it does not decide the design. The following changes go to `solutions-architect` first, via a draft ADR in `docs/decisions/`, before any code is written:

- Changing **module boundaries** (a new cross-module dependency that needs `ModuleBoundaryTest` adjusted; merging or splitting aggregates).
- Introducing a **new outbox event** with semantics other modules will subscribe to (versioning, payload schema, retention).
- Changing the **identity pattern** (anything other than `Long id` + `String uid` per the precedent — e.g. composite keys, natural keys, UUIDs).
- Introducing **dialect-specific SQL** or a native query.
- Adding a **new top-level permission namespace** (a `MODULE.*` prefix that doesn't yet exist).
- Anything touching **auth, tenancy, or the audit log shape**.

When in doubt, ship a smaller slice and ask. A hardening PR that passes all 10 sections is mergeable; one that drifts the architecture is not, no matter how green the tests.
