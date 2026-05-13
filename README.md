# Orbix ERP — Clean Build

Next-generation ERP system. Replaces the legacy `ERP-master` codebase with a clean architecture.

## Documents

| Document | Purpose |
|---|---|
| [PRD.md](PRD.md) | Product requirements — what, why, who, when |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Technical design — modules, stack, sync, security |
| [DATA-MODEL.md](DATA-MODEL.md) | Entities, attributes, datatypes, examples |
| [USER-STORIES.md](USER-STORIES.md) | Actionable backlog (159 stories, 16 epics) |
| [docs/decisions/](docs/decisions/) | Architecture Decision Records (ADRs) |

## Applications

All applications share the `orbix-engine-` brand prefix.

| Directory | Stack | Purpose |
|---|---|---|
| [orbix-engine-api/](orbix-engine-api/) | Spring Boot 3.3 · Java 21 · Maven · Hibernate 6 · Flyway | REST API, modular monolith, the single source of truth |
| [orbix-engine-web/](orbix-engine-web/) | Angular 17 · TypeScript · Bootstrap 5 | Back-office Web ERP for all configuration, reporting, finance |
| [orbix-engine-pos/](orbix-engine-pos/) | Flutter Desktop · Drift (SQLite) | Offline-first Point of Sale for Windows tills |
| [orbix-engine-wms/](orbix-engine-wms/) | Flutter Android · Drift (SQLite) | Mobile field-sales app |
| [orbix-engine-contracts/](orbix-engine-contracts/) | OpenAPI 3.1 spec | Single source of truth for the API contract; clients generated from this |
| [orbix-engine-infra/](orbix-engine-infra/) | Docker Compose + IaC | Local development + deployment infrastructure |

Java root package: `com.orbix.engine`.

## Quickstart (local development)

### 1. Start infrastructure
```bash
docker compose up -d
```
Brings up: MySQL 8 (default DB), PostgreSQL 15 (DB-agnostic test), Redis 7, Meilisearch, MinIO.

### 2. Run the API
```bash
cd orbix-engine-api
./mvnw spring-boot:run
```
API on `http://localhost:8081`. OpenAPI UI on `http://localhost:8081/swagger-ui.html`.

### 3. Run the Web ERP
```bash
cd orbix-engine-web
npm install
npm start
```
On `http://localhost:4200`.

### 4. Run the POS (Flutter Desktop)
```bash
cd orbix-engine-pos
flutter pub get
flutter run -d windows
```
Requires Flutter SDK installed.

### 5. Run the WMS (Flutter Mobile)
```bash
cd orbix-engine-wms
flutter pub get
flutter run -d <android-device>
```

## Repository conventions

- **Branching:** trunk-based; feature branches ≤ 2 days, behind feature flags for incomplete work.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/).
- **PRs:** one logical change per PR; mandatory code review; green CI required.
- **ADRs:** every non-trivial architecture decision is recorded in `docs/decisions/` (see [the template](docs/decisions/0000-adr-template.md)).
- **Issue tracking:** user-story IDs from [USER-STORIES.md](USER-STORIES.md) (`US-POS-014`, `US-PROC-002`) appear in branch names, commit messages, and PR titles.
