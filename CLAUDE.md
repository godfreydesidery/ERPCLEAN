# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository shape

Orbix Engine is a clean-build ERP rewrite of the legacy `ERP-master` codebase. It's a polyrepo-in-monorepo: five sibling applications all prefixed `orbix-engine-*` plus shared docs.

| Folder | Stack | Role |
|---|---|---|
| `orbix-engine-api/` | Spring Boot 3.3 · Java 21 · Maven · Hibernate 6 · Flyway | REST API — modular monolith, source of truth |
| `orbix-engine-web/` | Angular 17 standalone components · Bootstrap 5 | Back-office Web ERP |
| `orbix-engine-pos/` | Flutter Desktop (Windows) · Drift/SQLite | Offline-first POS for tills |
| `orbix-engine-wms/` | Flutter Android · Drift/SQLite | Field-sales mobile app |
| `orbix-engine-contracts/` | OpenAPI 3.1 + generated TS/Dart clients | Single source of truth for the API contract |
| `orbix-engine-infra/` | Docker Compose + IaC | Deployment templates |

Java root package: `com.orbix.engine`.

The four documents at repo root — `PRD.md`, `ARCHITECTURE.md`, `DATA-MODEL.md`, `USER-STORIES.md` — are the authoritative design specs. `ARCHITECTURE.md` and `DATA-MODEL.md` are large; load specific sections as needed rather than reading whole.

## Common commands

### Default: start local in QA parity (use this when running the app)

The remote QA box runs a single self-contained image (API + MariaDB + Redis + Angular bundle) built from `orbix-engine-infra/qa/Dockerfile` and bootstrapped from `orbix.env`. **Local runs should mirror it** so we exercise the same image, profile (`mysql,qa`), and env-driven bootstrap as production-shaped QA. Use the dev-mode commands further down only when you need fast iteration (hot reload).

```powershell
# Build once per code change to the API or web (re-runs npm install + maven package inside the image)
docker build -f orbix-engine-infra/qa/Dockerfile -t orbix:qa .

# Start (idempotent — removes any stale container of the same name first)
docker rm -f orbix 2>$null
docker volume create orbix-data-local | Out-Null
docker run -d --name orbix --restart unless-stopped `
  -p 8081:8081 `
  -v orbix-data-local:/var/lib/mysql `
  --env-file orbix-engine-infra/qa/orbix.env `
  orbix:qa

# Watch boot
docker logs -f orbix
```

- URL: `http://localhost:8081/` (both Angular bundle and `/api/v1`)
- Health: `http://localhost:8081/actuator/health` → `{"status":"UP"}` once Flyway + Tomcat are up (~30–60 s first run)
- Login: `rootadmin` / `ORBIX_BOOTSTRAP_ADMIN_PASSWORD` from `orbix-engine-infra/qa/orbix.env` (gitignored; see `CREDENTIALS.local.md` in the same folder for the current value)
- **Wipe to start with fresh data**:
  ```powershell
  docker rm -f orbix; docker volume rm orbix-data-local
  # then re-run the `docker run` above — Flyway re-runs from scratch and env bootstrap re-creates org/company/branch/rootadmin
  ```
- The local `docker-compose.yml` (MariaDB :3307, Postgres :5432, Redis :6379, Meili :7700, MinIO :9000–9001, phpMyAdmin :8090) is **only** for dev-mode iteration below; leave it down when running the QA-parity container.

### Dev mode (only when you need hot reload)

```powershell
docker compose up -d                                  # MariaDB on :3307, Postgres :5432, Redis :6379, Meilisearch :7700, MinIO :9000-9001, phpMyAdmin :8090
docker compose down
```
Data persists under `./infra/local-data/` (gitignored). MariaDB (not vanilla MySQL) is used because Flyway migrations rely on native `CREATE SEQUENCE`. Dev mode uses the `local` profile, which seeds an `admin`/`orbix` user (see [DevSeed.java](orbix-engine-api/src/main/java/com/orbix/engine/modules/iam/service/DevSeed.java)) — that account does **not** exist in the QA-parity container.

#### Backend (`orbix-engine-api/`)
```powershell
mvn spring-boot:run                                                  # default profile: local,mysql — http://localhost:8081
mvn spring-boot:run -Dspring-boot.run.profiles=local,postgres        # switch DB engine
mvn test                                                             # full test suite (unit + ArchUnit + smoke)
mvn test -Dtest=ItemServiceImplTest                                  # single test class
mvn test -Dtest=ItemServiceImplTest#createItem_persistsAndReturnsDto # single test method
mvn -pl orbix-engine-api spring-boot:run                             # if invoked from repo root
```
Swagger UI: `http://localhost:8081/swagger-ui.html`. No `mvnw` wrapper — use the system `mvn`.

#### Frontend (`orbix-engine-web/`)
```powershell
npm install
npm start             # ng serve on :4200; expects API on http://localhost:8081/api/v1
npm run build         # production build
npm test              # Karma unit tests
npm run e2e           # Playwright + axe-core accessibility
```

### POS / WMS (Flutter)
```powershell
flutter pub get
dart run build_runner build --delete-conflicting-outputs   # regenerates database.g.dart + freezed files; required after schema/DTO edits
flutter run -d windows         # POS
flutter run -d <android-id>    # WMS
```

## Backend architecture (the parts that aren't obvious from one file)

### Actual package layout vs. the docs
`orbix-engine-api/README.md` and `ARCHITECTURE.md §2.2` describe a hexagonal `api/app/domain/infra` layout. **The implemented code uses a flatter layout** — when adding code, follow the implementation, not the prose:

```
com.orbix.engine
├── api/                         FLAT — all REST controllers, one file per resource
└── modules/
    ├── common/                  cross-cutting platform (audit, response envelope, ...)
    ├── auth/                    JWT issuance, security filter, login
    ├── iam/                     users, roles, permissions
    └── <business module>/       sales, catalog, stock, procurement, pos, production, ...
        ├── domain/
        │   ├── entity/          JPA @Entity classes
        │   ├── dto/             request/response DTOs — every class ends with `Dto`
        │   ├── enums/
        │   └── event/           domain events
        ├── service/             interface `XxxService` + class `XxxServiceImpl` pair
        ├── repository/          Spring Data JPA repositories
        └── package-info.java
```

`ModuleBoundaryTest` (ArchUnit, in `src/test/java/com/orbix/engine/architecture/`) enforces:
- Controllers in `com.orbix.engine.api..` may not touch repositories.
- Modules may only depend on other modules via `..domain.dto..` / `..domain.enums..`, plus `common`/`auth`/`iam` infrastructure.
- Layer order: controller → service → repository → domain.

If you add a new cross-module dependency that breaks the rule, fix the design — don't relax the rule.

### Naming and code-shape conventions (project-wide, enforced socially)
- **DTOs end with `Dto`** — every class in `domain/dto/`, including nested records (e.g. `SalesInvoiceDto.LineDto`).
- **Application + helper services split into `interface Xxx` + `class XxxImpl`.** Aspects, config classes, filters, and Spring `@Component` infrastructure stay concrete. Test classes target `XxxImplTest`.
- **Lombok on new entities and DTOs** (`@Getter`/`@Setter`/`@Builder`/`@NoArgsConstructor`/`@AllArgsConstructor` as needed); prefer Java `record`s for immutable DTOs. Lombok is excluded from the Spring Boot fat jar.
- **API response envelope** — every REST response is wrapped in `ApiResponse<T>` with `status`/`statusCode`/`responseCode`/`message`/`errors[]`/`data`. Wrapping is automatic on the backend; the Angular HTTP layer unwraps so feature code sees the raw `T`. Don't wrap manually in controllers, and don't expect a wrapped shape in frontend services.
- **RBAC unit is "permission", not "privilege".** Entity `Permission`, table `permission`, JWT claim `perms`. Use `@PreAuthorize("hasPermission(...)")`-style checks consistent with the rest of the codebase — never invent a parallel "privilege" concept.
- **`uid` is the URL identifier; `id` stays in the body, serialised as a string.** Every entity that crosses an external boundary extends `com.orbix.engine.modules.common.domain.entity.UidEntity` (mapped superclass that auto-assigns a Crockford ULID at `@PrePersist`). URLs MUST address entities by `uid`; response and request DTOs carry both `id` (numeric handle, for body-level joins like `SalesInvoiceLine.itemId`) and `uid` (canonical external identifier). Per **JSON:API** discipline, every Long field whose name is `id` or ends in `Id` serialises as a JSON string on the wire — this is global, applied automatically by `IdLongAsStringSerializerModifier` (registered in `JacksonConfig`). Java types stay `Long`; Jackson coerces back from `"42"` to `42L` on deserialisation. Genuine numerics (decimals, prices, counts, version) are untouched. Patterns to follow on every uid-exposing entity:
  - Migration: `uid CHAR(26) NOT NULL` + `CONSTRAINT uk_<table>_uid UNIQUE (uid)`.
  - Entity: `extends UidEntity`; mark `@UniqueConstraint(name = "uk_<table>_uid", columnNames = {"uid"})` on `@Table`; keep your own `@Id` (each table has its own sequence).
  - Response DTO: include both `id` and `uid`. No annotation needed — the global modifier handles serialisation.
  - Request DTO: numeric FK fields stay `Long` — Jackson default deserialisation accepts both `42` and `"42"`, so clients can send strings.
  - Repository: `Optional<X> findByUid(String uid)`.
  - Service: external entry-point methods take `String uid` (`getXByUid`, `updateXByUid`, `archiveXByUid`, …). Internal joins on `Long id` stay in repositories — keep both `requireXByUid(String)` and `requireXById(Long)` helpers when an aggregate also accepts cross-aggregate id lookups (e.g. setting a price for an item id from the request body).
  - Controller: URL shape is `/api/v1/<resource>/uid/{uid}` — the literal `uid` segment makes it unambiguous vs. code or id lookups. Validate with `@ValidUlid` (from `modules.common.validation`).
  - Tests: bypass `@PrePersist` by setting via reflection — `ReflectionTestUtils.setField(entity, "uid", UidGenerator.next())`. Pin the wire shape with a small JSON test alongside the DTO (see `ItemResponseDtoJsonTest`).
  - Angular: `id` typed as `string` on the model (since Long-id fields stringify); use uid for navigation/URLs, id for body-level joins. Edit pages track the entity's `uid` (e.g. `editingUid: signal<string | null>`).
  - Migration of an existing entity touches the entity, DTO, repository, service interface + impl, controller, tests, plus every Angular route / service / component that addressed it by id in URLs. Roll out one aggregate at a time. Catalog (`Item`, `ItemGroup`, `Uom`, `VatGroup`, `ItemBarcode`, `PriceList`) is the reference cohort.

### Persistence policy (DB-agnostic by design — ARCHITECTURE.md §2.3)
The same build must run on MySQL 8 (currently MariaDB 11 locally) **and** PostgreSQL 15+. Concrete rules:
- JPQL or `CriteriaBuilder` only. Native queries are banned unless wrapped behind a dialect-resolver adapter.
- No vendor-only features at the app layer (no Postgres `JSONB` operators, no MySQL `FULLTEXT`, no listen/notify, no Oracle hints). Search lives in Meilisearch.
- IDs are `BIGINT` via Hibernate `SEQUENCE` strategy (table fallback for MySQL), or `BINARY(16)` UUIDs where externally referenced. Never `IDENTITY`.
- Flyway scripts default to `db/migration/common/`. Dialect-specific scripts go in `db/migration/mysql/` or `db/migration/postgres/` only when unavoidable, and need a `// DIALECT-SPECIFIC:` reason comment.
- `ddl-auto=validate` always. Never `update`.

### Auth + multi-tenancy
- JWT (access 15m, refresh 30d single-use/rotated — see `orbix.jwt.*` in `application.yml`). Local dev uses an ephemeral in-memory key; production loads RS256 from a secret store.
- Every transactional table carries `company_id` + `branch_id`. A `RequestContext` filter sets current user/company/branch from the JWT + a branch-override header; repository base interfaces inject the predicate automatically.

### Cross-module communication
Domain events via a **transactional outbox** (`domain_event` table written in the same TX as the business write, polled and dispatched by a Spring scheduled job). Use it for cross-module side effects — don't reach into another module's service or repository. Spring `ApplicationEventPublisher` directly is not the pattern here because it loses events on crash.

### Configuration knobs worth knowing
`application.yml` exposes a long list of `orbix.*` thresholds (POS variance, sales discount approval, LPO auto-approval, FEFO expiry cron, gift-card expiry cron, invoice match tolerance, etc.). When implementing a feature that gates on a magnitude, check whether the threshold already exists there before adding a new constant.

## Frontend (`orbix-engine-web`)

- Angular 17 **standalone components** — no NgModules; bootstrap providers in `app.config.ts`, routes (lazy-loaded per feature) in `app.routes.ts`.
- Feature folders under `src/app/features/` (`catalog`, `sales`, `procurement`, `stock`, `production`, `debt`, `reports`, `admin`, ...). Shared shell in `layout/`, auth/error infra in `core/`.
- The HTTP interceptor unwraps `ApiResponse<T>` before it reaches services, and attaches the JWT + branch header.
- Bootstrap 5 + Angular Material for UI primitives. Accessibility (WCAG AA) is a CI gate via axe-core in Playwright.

## Repository conventions

- **Trunk-based development**; feature branches ≤ 2 days, work-in-progress hidden behind feature flags on `main`.
- **Conventional Commits** for commit messages.
- **One logical change per PR**, mandatory review, green CI required.
- Branch / PR / commit references include the user-story ID from `USER-STORIES.md` (`US-POS-014`, `US-PROC-002`).
- Non-trivial architectural decisions get an ADR in `docs/decisions/` (template at `0000-adr-template.md`; see `0001-modular-monolith.md` for an example).
- `.env.example` documents required vars; copy to `.env` (gitignored) for local overrides. **Never commit secrets** — `.env`, `*.key`, `*.pem`, `*.pfx`, `*.p12` are gitignored.
- Legacy reference code at `d:\My_Works\ERP\ERP-master` (outside this repo). Use only for business-process reference, never copy code in — this is a clean rewrite.
