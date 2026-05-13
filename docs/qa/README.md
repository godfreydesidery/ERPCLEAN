# QA test plans

Test cases used during development (as acceptance specs) and verification (as manual / automated checklist).

## Layout

```
docs/qa/
├── README.md            ← this file
├── e2e-scenarios.md     ← cross-module business flows (PRD §6 + Phase 1.1)
├── non-functional.md    ← security, performance, accessibility, compliance
└── modules/
    ├── admin.md         ← branch / section / currency / fx_rate
    ├── auth.md          ← login / refresh / lockout / RBAC
    ├── catalog.md       ← items, prices, promotions, barcodes, batches
    ├── cash.md          ← cash entries, supplier payments, multi-currency book
    ├── common.md        ← audit aspect, outbox, request context
    ├── day.md           ← business-day open / close / override
    ├── giftcard.md      ← issue / redeem / refund / freeze / expire
    ├── orders.md        ← layby / pre-order lifecycle
    ├── party.md         ← customer / supplier / employee / sales-agent / biometric
    ├── pos.md           ← till sessions, sales, refunds, tenders, offline sync
    ├── procurement.md   ← quotation → LPO → GRN → invoice → payment
    ├── production.md    ← BOM, batch lifecycle, wastage, conversions
    ├── sales.md         ← back-office quotation / invoice / receipt / return
    └── stock.md         ← stock_move, balances, transfers, batches, FEFO
```

## Test case format

Every test case follows this skeleton:

```
### TC-<MOD>-<NNN> — <One-line title>
**Priority:** P1 | P2 | P3
**Type:** Functional | E2E | Negative | Edge | Security | Performance
**Stories:** US-XXX-001, US-XXX-002, ...
**Preconditions:**
- <state required before the test runs>

**Steps:**
1. ...
2. ...
3. ...

**Expected:**
- <observable outcome 1>
- <observable outcome 2>
- <emitted domain event(s), if any>

**Negative variants** (optional):
- If <input X is invalid>, expect <error Y>
```

## Priorities

- **P1** — MVP-blocking. Test must pass before pilot launch.
- **P2** — Hardening. Test must pass before production roll-out.
- **P3** — Breadth. Test must pass for the feature to be considered complete.

## Test types

- **Functional** — single module / single flow happy path.
- **E2E** — spans 2+ modules, exercises the outbox + event consumers.
- **Negative** — invalid input, denied permission, conflict states.
- **Edge** — boundary values, race conditions, concurrent writes, time-zone quirks.
- **Security** — auth, authz, PII handling, audit log integrity, secret leakage.
- **Performance** — throughput, latency, p95/p99 under load.

## Conventions

- **Idempotency** — every E2E POST step asserts that a replay with the same `Idempotency-Key` returns the same response and produces no duplicate side-effects.
- **Domain events** — for every business write, the test asserts the corresponding `<Verb><Noun>.v1` event appears in `domain_event` with `status = PENDING` in the same transaction.
- **Audit log** — every state-changing test asserts a row in `audit_log` with the correct `actor_id`, `action`, `entity_type`, `entity_id`.
- **Multi-tenancy** — every test runs in a known `(company_id, branch_id)` scope and asserts cross-tenant reads return empty.
- **Cleanup** — tests own their setup (no shared fixtures across tests); teardown is implicit via DB transaction rollback in integration tests and via cleanup endpoints in E2E tests.
- **Offline scenarios** — POS tests prefixed `TC-POS-OFF-*` simulate connectivity loss between client and server.

## Running

| Layer | Tool | Where |
|---|---|---|
| Unit | JUnit 5 + Mockito | `src/test/java/` per module |
| Integration | Spring Boot Test + Testcontainers (MySQL + Postgres + Meilisearch + Redis) | `src/test/java/.../integration/` |
| E2E | RestAssured + Testcontainers full stack | `orbix-engine-api/src/test/java/.../e2e/` |
| Contract | Spring Cloud Contract (REST) + Pact (events) | `orbix-engine-contracts/` |
| UI / POS | Flutter integration tests + Playwright for web | each client repo |

ArchUnit boundary tests already live in `src/test/java/com/orbix/engine/architecture/` and are non-negotiable — they run in every CI build.

## Coverage targets

- Backend unit + integration: **≥ 70% line, ≥ 60% branch** per module (per [PRD §11](../../PRD.md)).
- Every P1 user story must have **at least one functional test** here AND **one E2E test** when the flow spans modules.
- Every domain event type must have **at least one test** that asserts its emission and **one test** that asserts its consumption (idempotently).

## How this plan evolves

- New stories → new test cases under the relevant module file.
- IDs are never reused.
- When a test moves from manual to automated, append `[AUTO]` to its title and link the test class.
- When acceptance criteria change, **bump the test ID** (don't edit silently) and link the old → new.
