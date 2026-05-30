# Orbix Engine — Master QA Test Plan

**Version:** 1.0.0  
**Date:** 2026-05-30  
**Branch:** docs/qa-test-catalog  
**Author:** QA Agent (Claude Sonnet 4.6)

---

## 1. Scope

This catalog covers every testable surface of the Orbix Engine system as of branch `main` @ commit 3c65e66:

| Surface | Stack | Test levels |
|---------|-------|-------------|
| Backend API (`orbix-engine-api/`) | Spring Boot 3.3 / Java 21 / MariaDB 11 | Unit (JUnit 5), ArchUnit, integration (Testcontainers), smoke |
| Web ERP (`orbix-engine-web/`) | Angular 17 standalone / Bootstrap 5 | Karma unit, Playwright e2e + axe-core |
| Desktop POS (`orbix-engine-pos/`) | Flutter Desktop (Windows) / SQLite | Flutter unit + widget + integration |
| Mobile WMS (`orbix-engine-wms/`) | Flutter Android / SQLite | Flutter unit + widget + integration |

**Modules in scope:**
`auth` / `iam` / `admin` / `catalog` / `party` / `procurement` / `stock` / `sales` / `pos` / `day` / `cash` / `fiscal` / `giftcard` / `orders` / `production` / `debt` / `reports`

**Out of scope for this catalog version:** AI/LLM features (US-AI-*), WMS field-sales full cycle (stubbed server-side — `FIELD_SALE` op rejected), HR payroll calculations, e-commerce.

---

## 2. Test Dimensions

Every area is tested across eight dimensions. Not all cells are equally weighted; the priority matrix in §5 makes gaps explicit.

| Code | Dimension | Description |
|------|-----------|-------------|
| `FUNC` | Functional / Acceptance | Happy-path per US-* acceptance criterion |
| `NEG` | Negative / Boundary / Validation | Bad input, wrong enums, missing fields, limits, concurrency |
| `INT` | Integration / End-to-End | Cross-module flows, outbox events, round-trips |
| `SEC` | Security | AuthN, AuthZ/RBAC, multi-tenant isolation, injection safety |
| `PERF` | Performance / Load | Latency, throughput, pagination, bulk ops |
| `UX` | UX / Accessibility | WCAG AA, keyboard nav, empty states, picker rule |
| `DATA` | Data Integrity / Persistence | DB-agnostic behaviour, idempotency, money precision, change_seq |
| `RELI` | Reliability / Resilience | Offline, reconnect, outbox retry, boot-safety |

---

## 3. Test Case Schema

Every test case in `docs/qa/test-cases/` uses this consistent schema:

```
| Field | Content |
|-------|---------|
| ID | TC-<AREA>-<NNN> |
| Title | One-line imperative description |
| Area | Module name |
| Dimension | One of: FUNC NEG INT SEC PERF UX DATA RELI |
| Priority | P0 Pilot-blocking / P1 MVP / P2 Hardening / P3 Breadth |
| Linked US-* | Story ID(s) from USER-STORIES.md |
| Preconditions | Numbered list — state required before step 1 |
| Steps | Numbered, concrete — exact endpoint / screen / payload |
| Expected Result | Observable outcome(s), including DB state and events |
| Automatable? | yes (unit/integration/e2e/flutter) / no (manual) / partial |
| Result/Status | PASS / FAIL / BLOCKED / SKIP — filled by executor |
| Notes/IssueRef | Filled by executor — link to filed issue if FAIL |
```

**Priority scale:**
- **P0** — Pilot-blocking. A P0 failure = no launch. Covers: POS sync loop, auth, multi-tenancy isolation, money correctness, boot-safety, business-day gating.
- **P1** — MVP. Must pass before pilot branch goes live.
- **P2** — Hardening. Must pass before production roll-out.
- **P3** — Breadth. Must pass for the feature to be considered complete.

**Severity scale for filed issues:**
- **Blocker** — Prevents core flow (sale, sync, auth). P0 test failure = Blocker.
- **Critical** — Data corruption, security bypass, money miscalculation, multi-tenant leak.
- **Major** — Feature incorrect but workaround exists; accessibility regression on main nav.
- **Minor** — UI cosmetic, wrong label, non-critical error message.
- **Trivial** — Typo, extra whitespace, cosmetic only.

---

## 4. How an Executing Agent Uses This Catalog

### 4.1 Run Protocol

1. **Select cases** by area and priority. For a pilot-gate run, execute all P0 then P1 cases. For a regression run after a change, execute cases linked to the changed area + all P0 cases.

2. **Authenticate** against the live QA container at `http://localhost:8081/`:
   ```bash
   # Admin credentials
   ADMIN=rootadmin
   ADMIN_PASS=SKp315goPN8Nb0yJtMCCD7cm
   
   # POS cashier (seeded by seed-dev-data.local.sh)
   CASHIER=cashier
   CASHIER_PASS="Cashier#2026"
   BRANCH_HQ_ID=1
   
   TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"rootadmin","password":"SKp315goPN8Nb0yJtMCCD7cm"}' \
     | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
   AUTH="Authorization: Bearer $TOKEN"
   ```

3. **Reset state** when a test requires clean data:
   ```powershell
   # Full volume wipe (triggers Flyway re-run + bootstrap)
   docker rm -f orbix; docker volume rm orbix-data-local
   docker volume create orbix-data-local | Out-Null
   docker run -d --name orbix --restart unless-stopped `
     -p 8081:8081 -v orbix-data-local:/var/lib/mysql `
     --env-file orbix-engine-infra/qa/orbix.env orbix:qa
   # Wait for health
   # Then re-seed:
   bash orbix-engine-infra/qa/seed-dev-data.local.sh
   ```
   For tests that only need a clean business day, close and reopen rather than wiping.

4. **Execute each step** exactly as written. For API tests, use `curl` with the exact payload. For UI tests, drive Playwright against `http://localhost:8081/`. For unit/integration tests, use `mvn test -Dtest=<ClassName>#<method>`.

5. **Record results** in the Result/Status and Notes/IssueRef columns of each case.

6. **For each FAIL, file a structured issue** (see §4.2).

7. **Re-test** after a fix by running the failed case + its dependencies.

### 4.2 Issue Record Format

```
Title: [TC-<ID>] <one-line description of the failure>
Environment:
  - Commit: <git log --oneline -1>
  - Container: orbix:qa built <date>
  - Profile: mysql,qa
  - Instance: http://localhost:8081

Steps to Reproduce:
  1. <exact step>
  2. <exact step>
  3. <exact step>

Expected: <from test case>
Actual: <what was observed — include HTTP status, response body, DB state>
Severity: Blocker / Critical / Major / Minor / Trivial
Linked TC: TC-<ID>
Linked US-*: US-XXX-NNN
Suspected Root Cause: <module, file, method if known>
Proposed Resolution: <specific fix or investigation path>
```

### 4.3 Release Gate Sign-Off

Gate is **GREEN** when all of the following hold:
- [ ] All P0 cases: PASS
- [ ] All P1 cases: PASS (or SKIP with documented reason + filed issue)
- [ ] `axe-core` gate: 0 violations on all web pages in the Playwright suite
- [ ] `mvn test`: 0 failures, 0 errors (the 1 known error in `HealthSmokeTest` has an open tracked issue)
- [ ] Manual walk-through of the POS sell → sync → reconcile loop on QA container: verified
- [ ] No open Blocker or Critical issues

Gate is **RED** if any P0 case FAILs, or if there is an open Blocker/Critical issue without an accepted workaround.

---

## 5. Coverage Matrix

`+` = well-covered (5+ cases), `~` = representative (2–4 cases), `-` = thin (0–1 case), `*` = intentionally out of scope

| Area | FUNC | NEG | INT | SEC | PERF | UX | DATA | RELI |
|------|------|-----|-----|-----|------|----|------|------|
| Auth/IAM | + | + | ~ | + | ~ | ~ | ~ | ~ |
| Admin/Setup | + | ~ | ~ | ~ | - | ~ | ~ | - |
| Catalog | + | + | ~ | ~ | ~ | + | + | - |
| Party | + | ~ | ~ | ~ | - | + | ~ | - |
| Procurement | + | ~ | + | ~ | ~ | ~ | ~ | - |
| Stock | + | ~ | + | ~ | ~ | ~ | + | ~ |
| Sales | + | + | + | ~ | ~ | ~ | + | ~ |
| POS Core | + | + | + | + | ~ | ~ | + | + |
| POS Sync | + | + | + | + | + | ~ | + | + |
| Day/Business Day | + | + | ~ | ~ | - | ~ | ~ | ~ |
| Cash/Payments | + | ~ | + | ~ | ~ | ~ | + | ~ |
| Fiscal | + | ~ | + | ~ | - | - | ~ | ~ |
| Gift Card | + | ~ | ~ | ~ | - | ~ | ~ | - |
| Orders (Layby) | ~ | ~ | ~ | - | - | ~ | ~ | - |
| Production | ~ | ~ | ~ | - | - | ~ | ~ | - |
| Debt | + | ~ | ~ | ~ | ~ | ~ | + | - |
| Reports | ~ | ~ | ~ | ~ | ~ | ~ | - | - |
| Non-Functional | - | - | - | + | + | + | + | + |

**Known thin cells (executor expects failures or gaps):**
- **WMS/Field Sales:** `FIELD_SALE` op is server-side stubbed (returns REJECTED). All WMS sync cases are blocked.
- **Reports/PERF:** No load-test suite exists; cases are manual timing probes.
- **Biometric (US-IAM-011/012):** Not implemented in the QA image; all cases will be BLOCKED.
- **HealthSmokeTest/RELI:** Boot-safety test requires Testcontainers which is not wired up yet; case TC-RELI-001 will be BLOCKED until infra is ready.
- **Fiscal TZ_VFD:** EFDMS client is a real HTTP call; in QA container regime=NONE by default, so EFDMS-dependent cases require a test driver stub.

---

## 6. File Index

```
docs/qa/
├── README.md                        ← this file (master plan)
└── test-cases/
    ├── TC-AUTH.md                   ← Authentication & IAM
    ├── TC-ADMIN.md                  ← Admin: company, branch, setup
    ├── TC-CAT.md                    ← Catalog: items, prices, barcodes
    ├── TC-PARTY.md                  ← Party: customer, supplier
    ├── TC-PROC.md                   ← Procurement: LPO, GRN, payment
    ├── TC-STOCK.md                  ← Stock: moves, balances, counts
    ├── TC-SALES.md                  ← Sales: invoice, receipt, return
    ├── TC-POS-CORE.md               ← POS: till, session, sale, tender
    ├── TC-POS-SYNC.md               ← POS: offline sync push/pull
    ├── TC-DAY.md                    ← Business day open/close/gate
    ├── TC-CASH.md                   ← Cash ledger, bank deposit
    ├── TC-FISCAL.md                 ← Fiscal: regime, outbox, status
    ├── TC-GIFTCARD.md               ← Gift card lifecycle
    ├── TC-ORDERS.md                 ← Layby / customer orders
    ├── TC-PROD.md                   ← Production batch, BOM, wastage
    ├── TC-DEBT.md                   ← Debt management, ageing
    ├── TC-REPORTS.md                ← Reports and exports
    └── TC-NFR.md                    ← Non-functional: SEC, PERF, UX, RELI
```

---

## 7. Seeded Test Data Reference

The QA container at `http://localhost:8081/` (after running `seed-dev-data.local.sh`) has:

| Entity | Value | Notes |
|--------|-------|-------|
| Admin user | `rootadmin` / `SKp315goPN8Nb0yJtMCCD7cm` | Full permissions |
| POS cashier | `cashier` / `Cashier#2026` | Role POS_CASHIER, branch HQ id=1 |
| Branch | HQ, id=1 | Default branch |
| Business day | Today's date, OPEN | Required for POS sessions |
| VAT groups | STD18 (18%), EXEMPT (0%) | Tanzania rates |
| Price list | RETAIL, TZS, tax-inclusive | Default |
| Items | COKE500, AZAMMILK1L, BREAD, SUGAR1KG, RICE1KG, OIL1L, SOAP, UNGA2KG, MATCHES, SALT500G | Prices in TZS |
| UoMs (Flyway-seeded) | EA=1, KG=4, L=6 | Standard units |

To reference a seeded item in test payloads, first resolve its `id`/`uid` by calling `GET /api/v1/items?q=COKE500`.
