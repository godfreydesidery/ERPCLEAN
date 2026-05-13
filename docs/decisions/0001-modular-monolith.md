# 0001 — Modular monolith over microservices

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-05-13 |
| Deciders | Godfrey |

## Context

The legacy system is one Spring Boot deployment with ~94 entities and ~39 controllers, mixed by concern. Two questions arose at the rewrite:

1. Should the new backend be a set of microservices (sales / catalog / stock / procurement / production…)?
2. If not, how do we avoid the legacy mess of cross-cutting reaches that made changes risky?

We have a small team (1-3 engineers initially), a single operational target per deployment, and no scale pressure that would force horizontal service split. Microservices would add deployment, observability, and cross-service consistency burden long before they paid back.

## Decision

Single deployable monolith, **but** with hard module boundaries enforced by package layout and ArchUnit tests. Each business area (sales, procurement, stock, etc.) is a top-level package under `com.orbix.engine`. Cross-module communication is by published DTOs or domain events through the transactional outbox — never by reaching into another module's `domain` or `infra` packages.

Splitting later is straightforward: a module already isolated by package and communicating by events can be lifted out into its own service when (if) scale justifies it.

## Consequences

- One image, one deploy, one DB connection pool — operational simplicity.
- Refactors that cross module boundaries are cheap: no API versioning between internal callers.
- Cross-module discipline depends on tests; ArchUnit failures must be treated as compile failures by the team, not as flaky warnings.
- We give up the ability to scale modules independently or use different languages per service. We accept that trade until a measured pressure exists.

## Alternatives considered

- **Full microservices from day one** — rejected: organisational and operational tax with zero current benefit.
- **Single monolith without enforced module boundaries** — rejected: this is exactly what the legacy system was; the rewrite must not reproduce it.
- **Separate services for POS and WMS sync from the core API** — rejected for MVP, may be revisited at scale.

## References

- [ARCHITECTURE.md §2.1, §2.2](../../ARCHITECTURE.md)
