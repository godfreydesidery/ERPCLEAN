# orbix-engine-api

Spring Boot 3.3 · Java 21 · Maven · Hibernate 6 · Flyway · MySQL 8 / PostgreSQL 15.

The REST API. A modular monolith — package boundaries enforced by ArchUnit
(see `src/test/java/com/orbix/engine/architecture/ModuleBoundaryTest.java`).

## Run

```bash
# default profile composition: local + mysql
./mvnw spring-boot:run

# against PostgreSQL
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,postgres
```

API on http://localhost:8081. OpenAPI UI on http://localhost:8081/swagger-ui.html.

## Tests

```bash
./mvnw test
```

Includes:
- ArchUnit boundary tests (module isolation, hexagonal layering)
- Smoke tests (app starts, /ping responds)

## Module structure

Mirrors [ARCHITECTURE.md §2.1](../ARCHITECTURE.md):

```
com.orbix.engine
├── platform/    cross-cutting: security, audit, events, flags, company, search
├── party/       parties: customer, supplier, employee, agent
├── catalog/     items, groups, pricing, VAT, UoM, promotions
├── stock/       balances, moves, counts, transfers
├── procurement/ quotation, LPO, GRN, vendor returns/credits
├── sales/       quotation, invoice, receipt, allocation, returns
├── pos/         till session, sale, payment, fiscal
├── wms/         sales list, sales sheet, expense, route
├── production/  BOM, batch, conversion
├── debt/        receivable / payable entries, allocation
├── cash/        cash book, cash entry, supplier payment
├── day/         business day open/close
├── hr/          employee, shift, biometric
├── reporting/   report definitions, runs
└── integration/ fiscal printer, webhooks, accounting export
```

Each core module follows hexagonal layout (`api/app/domain/infra`).
Light modules use service + repository directly.
