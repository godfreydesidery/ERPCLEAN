# Non-functional test cases

Security, performance, accessibility, compliance, observability. These are orthogonal to the functional E2E scenarios and apply system-wide.

## Security

### TC-NFR-SEC-001 — Password hashing uses BCrypt cost ≥ 12 [P1]
**Steps:**
1. Inspect the configured `BCryptPasswordEncoder` bean's `strength` (via Spring context or directly via reflection in a test).
2. Create a user; read `app_user.password_hash`.

**Expected:**
- Strength ≥ 12.
- Hash starts with `$2a$12$` or higher cost factor.

### TC-NFR-SEC-002 — JWT TTL matches configuration [P1]
**Steps:**
1. Login. Decode the returned JWT.

**Expected:**
- `exp - iat = orbix.jwt.access-ttl` (default 900s).
- `iss = orbix-engine`.
- Algorithm: `HS256` in local profile, `RS256` in prod profile.

### TC-NFR-SEC-003 — Tokens reject altered signature [P1]
**Steps:**
1. Decode a valid JWT, change one character in the payload, re-encode.
2. Use the tampered token.

**Expected:** 401 `Invalid token`.

### TC-NFR-SEC-004 — Tokens reject expired exp claim [P1]
**Steps:**
1. Generate a JWT with `exp = now - 1s` (test-only utility).
2. Use it.

**Expected:** 401.

### TC-NFR-SEC-005 — Refresh tokens are single-use [P1] *(when refresh implemented)*
**Steps:**
1. Login → get refresh token A.
2. Use A to get a new access token + refresh token B.
3. Use A again.

**Expected:** Step 3 returns 401. A is revoked the moment it's used; reusing it signals theft.

### TC-NFR-SEC-006 — CORS rejects disallowed origins [P1]
**Steps:**
1. Send a CORS preflight from `https://evil.example.com`.

**Expected:** 403 (or no `Access-Control-Allow-Origin` returned). Localhost + configured production origin only.

### TC-NFR-SEC-007 — Method-level @PreAuthorize blocks unprivileged access [P1]
**Steps:**
1. Login as a user without `ITEM.CREATE`.
2. POST /api/v1/items.

**Expected:** 403.

### TC-NFR-SEC-008 — Audit log writes are append-only [P1]
**Steps:**
1. Insert an audit_log row.
2. Attempt UPDATE.

**Expected:** DB-level enforcement (CHECK constraint or trigger) rejects the UPDATE; app layer has no endpoint that issues UPDATE on audit_log.

### TC-NFR-SEC-009 — Audit chain detects gaps [P1]
**Steps:**
1. Generate 100 audit_log rows.
2. Manually DELETE row 50.
3. Run verification.

**Expected:** Verifier flags row 51 as broken-chain.

### TC-NFR-SEC-010 — PII fields scrubbed from logs [P1]
**Steps:**
1. Trigger an exception while creating a customer (e.g., violate a constraint after PII is in memory).
2. Inspect logs.

**Expected:** No phone / email / address / TIN / VRN / password / token appears in plaintext. Standard SLF4J pattern uses the PII-aware redactor for `@PII`-marked fields.

### TC-NFR-SEC-011 — Gift card codes redacted in logs / audit / events [P1]
**Steps:**
1. Issue a gift card; trigger an exception.

**Expected:** Code never appears in plaintext anywhere except the database row + the response to the issuer. Audit log shows last 4 + hash.

### TC-NFR-SEC-012 — Biometric template ciphertext never logged [P1]
**Steps:**
1. Enrol a biometric. Inspect all log streams.

**Expected:** `template_ciphertext` never appears. Only the enrolment id + method + actor.

### TC-NFR-SEC-013 — SQL-injection-safe parameters [P1]
**Steps:**
1. Attempt to register a customer with `name = "Robert'); DROP TABLE party; --"`.

**Expected:** Customer created with that exact string as name; no SQL executed; party table intact.

### TC-NFR-SEC-014 — Rate limits on login [P2]
**Steps:**
1. 100 login attempts in 10 seconds from one IP.

**Expected:** After threshold, 429 `Too Many Requests`. Account lockout (per TC-E2E-022) is independent of rate-limit.

### TC-NFR-SEC-015 — Idempotency keys are scoped per tenant [P1]
**Steps:**
1. User A (company 1) creates a sales invoice with `Idempotency-Key = abc-123`.
2. User B (company 2) POSTs to the same endpoint with the same key.

**Expected:** Both succeed (keys are scoped per `(tenant, key)`). No cross-tenant collision.

### TC-NFR-SEC-016 — Subject access export covers all PII [P1]
**Stories:** US-PLAT-007
**Steps:**
1. Create a customer with addresses, contacts, biometric (linked through employee).
2. Request SAR export for the party.

**Expected:** Export bundles all PII fields, references to related transactional rows (invoices, receipts, etc.) without their detailed PII unless the requester is also that customer.

### TC-NFR-SEC-017 — Anonymisation deletes / hashes party PII [P2]
**Stories:** US-PLAT-008
**Steps:**
1. Anonymise a party.
2. Read the party row + all child PII fields.

**Expected:** Name → `(anonymised)`, phone / email / address fields nulled or hash-substituted; transactional history (invoices, receipts) retains `party_id` but reads show the anonymised marker.

---

## Performance

### TC-NFR-PERF-001 — POS sale post p95 < 500ms [P1]
**Conditions:** 50 concurrent POS clients, balanced catalog cache, mid-spec hardware spec'd in PRD §13.2.
**Expected:** p95 < 500ms over 5 minutes; p99 < 1s.

### TC-NFR-PERF-002 — Item typeahead p95 < 200ms [P1]
**Conditions:** 50 concurrent typeahead requests, Meilisearch warm, 50,000-item index.
**Expected:** p95 < 200ms.

### TC-NFR-PERF-003 — GRN posting (50 lines) p95 < 1s [P2]
**Steps:** Post a GRN with 50 batch-tracked lines including stock_batch creation.
**Expected:** p95 < 1s.

### TC-NFR-PERF-004 — Outbox dispatch lag < 5s under steady load [P2]
**Conditions:** 1,000 events / minute steady state.
**Expected:** `now - max(occurred_at where status=PENDING)` stays < 5s.

### TC-NFR-PERF-005 — Daily Z-report generation < 30s per branch [P2]
**Steps:** Run Z-report for a branch with 10 tills × 200 sales each.
**Expected:** Completes within 30s; PDF rendered; persisted to object store.

### TC-NFR-PERF-006 — Stock card report for a hot SKU [P2]
**Steps:** Query 6 months of `stock_move` for an item with ~500 moves / month.
**Expected:** First page (50 rows) < 300ms; pagination consistent.

### TC-NFR-PERF-007 — Concurrent transfers across branches [P2]
**Steps:** 20 simultaneous inter-branch transfers on different SKUs.
**Expected:** No deadlocks; consistent balances; events emitted in order per branch.

---

## Reliability / Resilience

### TC-NFR-REL-001 — Outbox poller restarts gracefully [P1]
**Steps:**
1. Kill the application mid-dispatch (with PENDING + IN_FLIGHT events).
2. Restart.

**Expected:** No event is lost; in-flight events are reset to PENDING and re-dispatched (idempotent consumers absorb the duplicate).

### TC-NFR-REL-002 — Webhook subscriber failure does not block in-process consumers [P1]
**Steps:**
1. Webhook subscriber A returns 500 for an event.
2. Subscriber B (also subscribed) returns 200.

**Expected:** B receives the event; A is retried per backoff; eventually A is dead-lettered after `max_attempts`.

### TC-NFR-REL-003 — DB connection lost mid-transaction [P2]
**Steps:**
1. Sever the DB connection during a multi-step service call.

**Expected:** Transaction rollback; no partial state; client gets 503 with retry-after; on retry the operation is idempotent.

### TC-NFR-REL-004 — Disk-full on object storage (Z-report) [P2]
**Steps:** Fill object store; trigger Z-report generation.
**Expected:** Generation succeeds in-memory; storage failure surfaces as a metric + alert; report retried later; business_day NOT closed until report persisted.

### TC-NFR-REL-005 — Clock-skew tolerance for JWT (±30s) [P2]
**Steps:** Generate JWT with `iat = now + 25s`.
**Expected:** Accepted (within skew tolerance). At `iat = now + 60s` → rejected.

---

## Observability

### TC-NFR-OBS-001 — Health endpoint reflects DB + Redis + Meilisearch [P1]
**Steps:** `GET /actuator/health`.
**Expected:** 200 with sub-status per dependency. Kill one dependency → that sub-status DOWN; overall status DOWN.

### TC-NFR-OBS-002 — Prometheus metrics exported [P1]
**Steps:** `GET /actuator/prometheus`.
**Expected:** Metrics include `http_server_requests_*`, `jvm_*`, custom `orbix_pos_sale_posted_total`, `orbix_outbox_pending_*`, `orbix_outbox_dispatch_duration_seconds`.

### TC-NFR-OBS-003 — Sync queue depth metric per device [P1]
**Stories:** US-PLAT-012
**Expected:** Per-device gauge `orbix_sync_queue_depth{client_id, install_id}` updates on every push.

### TC-NFR-OBS-004 — Slow query logging [P2]
**Steps:** Run a query that exceeds 1s.
**Expected:** Logged at WARN with the SQL (parameters redacted), plan hash, duration.

---

## Compliance

### TC-NFR-COMP-001 — VAT report aggregates by VAT group + period [P1]
**Steps:** Generate VAT return for a period; reconcile manually against `pos_sale_line.vat_amount`.
**Expected:** Per-VAT-group totals match within rounding (configurable precision).

### TC-NFR-COMP-002 — Fiscal printer signature stored [P2]
**Stories:** US-POS-011 *(region-dependent)*
**Expected:** `pos_payment.fiscal_signature` populated when the fiscal printer is configured and signing is enabled.

### TC-NFR-COMP-003 — Data residency: no cross-region replication when configured [P2]
**Steps:** Deploy with `orbix.data-residency.region = UG`; observe network egress.
**Expected:** All data-at-rest in UG region; no cross-border replication; configured webhook subscribers outside UG are rejected by config validation.

### TC-NFR-COMP-004 — Number-sequence gaps acceptable but reported [P2]
**Stories:** US-PLAT-009
**Steps:** Roll back a transaction that allocated `LPO-BR1-000123`. The next allocation is `000124`.
**Expected:** Gap exists; gap-report endpoint surfaces it; auditors see a reason.

---

## Accessibility (UI surfaces)

Targeted at orbix-engine-web; POS / WMS Flutter apps have separate criteria.

### TC-NFR-A11Y-001 — Keyboard-only navigation [P2]
**Expected:** All P1 screens are operable with keyboard alone; tab order follows visual order; no focus traps.

### TC-NFR-A11Y-002 — Screen-reader labels [P2]
**Expected:** All form fields have associated `<label>`; all images have `alt`; all dynamic regions use `aria-live` appropriately.

### TC-NFR-A11Y-003 — Colour contrast WCAG AA [P2]
**Expected:** Text + interactive elements meet 4.5:1; large text 3:1.

### TC-NFR-A11Y-004 — Numeric input does not lose precision [P1]
**Steps:** Enter `0.420 KG` in a weighed-item line; submit; verify backend stores `DECIMAL(18,4)` without rounding to 0.42 or 0.4200.
**Expected:** Exact value preserved.

---

## Backups

### TC-NFR-BAK-001 — Daily backup runs and restores [P1]
**Steps:**
1. Take backup at T0.
2. Make changes between T0 and T1.
3. Restore from T0 in an isolated environment.
**Expected:** All T0 data present; T0→T1 changes absent; Flyway schema_version table consistent.

### TC-NFR-BAK-002 — Point-in-time recovery within RPO [P2]
**Conditions:** RPO 15 minutes per PRD §7.6.
**Steps:** PITR to T0 + 5 minutes.
**Expected:** Subset of T0→T1 changes that were committed by T0+5m is present; rest absent.

---

## Idempotency (cross-cutting)

### TC-NFR-IDEM-001 — Identical Idempotency-Key returns same response [P1]
**Steps:**
1. POST /api/v1/sales-invoices with key `K1` → returns response R1.
2. POST same body with key `K1`.
**Expected:** R1 returned; no second invoice created; no second domain_event row.

### TC-NFR-IDEM-002 — Same key + DIFFERENT body returns conflict [P1]
**Steps:**
1. POST with key `K1` body B1 → R1.
2. POST with key `K1` body B2.
**Expected:** 409 `IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_BODY`.

### TC-NFR-IDEM-003 — Idempotency key TTL is 24h [P2]
**Steps:** POST with key `K1`. Wait 25h. POST same body with `K1`.
**Expected:** Treated as a NEW request (cache expired); new invoice / response.

---

## DB-agnostic correctness

### TC-NFR-DB-001 — Same Flyway migrations apply cleanly to MySQL + Postgres [P1]
**Expected:** Both `mvn -P mysql verify` and `mvn -P postgres verify` succeed.

### TC-NFR-DB-002 — Sequence emulation parity [P1]
**Expected:** Sequence values monotonically increase on both engines; allocation-size of 50 honoured.

### TC-NFR-DB-003 — Timestamp / Decimal precision parity [P1]
**Expected:** TIMESTAMP and DECIMAL(18,4) round-trip identically.

### TC-NFR-DB-004 — No vendor SQL [P1]
**Expected:** Grep audit shows no `LIMIT` in non-standard form, no `JSONB`, no MySQL `FULLTEXT`, no Postgres `to_tsvector` in production code.

---

*See [e2e-scenarios.md](e2e-scenarios.md) for functional + cross-module flows.*
