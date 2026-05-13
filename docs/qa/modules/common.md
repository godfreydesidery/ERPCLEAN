# Common — test plan

Cross-cutting infrastructure: audit aspect, transactional outbox + dispatcher, RequestContext, FX-rate lookup helper.

## Audit aspect

### TC-COMMON-001 — @Auditable writes a row [P1]
**Steps:** Service method annotated with `@Auditable(action="CREATE", entityType="Item")` runs.
**Expected:** audit_log row with actor_id from RequestContext, action, entity_type, entity_id, before/after JSON.

### TC-COMMON-002 — Audit row in same tx as business write [P1]
**Steps:** Service method throws after the audit write.
**Expected:** Both rolled back (no orphan audit row).

### TC-COMMON-003 — PII redaction in audit payload [P1]
**Steps:** Audit a customer create with phone + email.
**Expected:** Payload JSON shows `****` for `@PII`-tagged fields.

### TC-COMMON-004 — Hash chain links rows [P1]
**Steps:** Three consecutive audit writes.
**Expected:** Row 2's prev_hash = row 1's row_hash; row 3's prev_hash = row 2's row_hash.

### TC-COMMON-005 — Chain verification detects tamper [P1]
**Steps:** Manually UPDATE row 2's before_json. Run verifier.
**Expected:** Verifier flags row 3 as broken.

## Outbox

### TC-COMMON-006 — EventPublisher writes row in caller tx [P1]
**Steps:** Service publishes inside `@Transactional`.
**Expected:** domain_event row inserted; status PENDING; if tx rolls back, row gone too.

### TC-COMMON-007 — Outside-of-tx publish rejected [P1]
**Steps:** Call EventPublisher.publish() with no surrounding transaction.
**Expected:** Exception (`Propagation.MANDATORY`).

### TC-COMMON-008 — Payload serialised once [P1]
**Steps:** Publish event with Map payload.
**Expected:** payload_json contains the serialised JSON; no re-serialisation on dispatch.

### TC-COMMON-009 — Versioned event type [P1]
**Steps:** publish with type "ItemCreated.v1".
**Expected:** Type stored as-is; dispatcher routes by type.

## OutboxDispatcher

### TC-COMMON-010 — Dispatcher picks PENDING in occurred_at order [P1]
**Steps:** Three events in order A, B, C. Run dispatcher.
**Expected:** A, B, C in that order; each transitions PENDING → DISPATCHED.

### TC-COMMON-011 — Failure retries with backoff [P1]
**Steps:** Subscriber throws on first attempt.
**Expected:** attempt_count incremented; last_error populated; next dispatch retries.

### TC-COMMON-012 — Dead-letter after max_attempts [P1]
**Steps:** Subscriber throws on attempts 1..max_attempts.
**Expected:** Status DEAD_LETTERED; logs ERROR; alert metric incremented.

### TC-COMMON-013 — Restart after crash [P1]
**Type:** Edge
**Steps:** Kill app mid-dispatch (events in IN_FLIGHT or partially complete).
**Expected:** On restart, PENDING + stale IN_FLIGHT picked up; idempotent consumers absorb the duplicate.

### TC-COMMON-014 — Batch size honoured [P2]
**Steps:** 1000 PENDING; dispatch interval 1s; batch_size 100.
**Expected:** ~10 cycles to drain; each pulls ≤ 100 rows.

### TC-COMMON-015 — Webhook subscriber failure does not block in-process consumer [P1]
**Steps:** Two subscribers on event E. Webhook returns 500; in-process returns 200.
**Expected:** In-process succeeds; webhook retried; event marked DISPATCHED only after both succeed (or per-subscriber tracking via `event_delivery`).

## RequestContext

### TC-COMMON-016 — JWT populates RequestContext [P1]
**Steps:** Authenticated request hits a controller.
**Expected:** RequestContext.userId, companyId, branchId match JWT claims.

### TC-COMMON-017 — X-Branch-Id header overrides JWT branch [P1]
**Steps:** JWT bid = X. Header X-Branch-Id = Y (user has access to Y).
**Expected:** RequestContext.branchId = Y.

### TC-COMMON-018 — Anonymous request has no context [P1]
**Steps:** Public endpoint hit without JWT.
**Expected:** RequestContext is empty / null; service-layer calls that require companyId throw.

### TC-COMMON-019 — Context cleared after request [P1]
**Steps:** Two sequential requests A then B.
**Expected:** B's context starts empty; no leak from A.

### TC-COMMON-020 — requireBranchId() throws on missing [P1]
**Steps:** Request without branch context calls requireBranchId().
**Expected:** IllegalStateException.

## Multi-tenancy enforcement

### TC-COMMON-021 — Repository filter injects company_id [P1]
**Steps:** User on company 1 reads from a company-scoped repository.
**Expected:** Generated SQL contains `company_id = ?` predicate; cross-company rows not returned.

### TC-COMMON-022 — Direct entity load bypasses filter (test only) [P2]
**Steps:** Service queries by id only; intentional cross-tenant read in test.
**Expected:** Confirmed that bypass requires explicit unfiltered repo method (named e.g. `findByIdUnfiltered`).

## FX rate helper (Phase 1.1)

### TC-COMMON-023 — Lookup most recent rate ≤ time [P1]
**Steps:** 3 fx_rate rows at T1 < T2 < T3. Look up rate for T2.5.
**Expected:** Returns T2 rate.

### TC-COMMON-024 — No rate available [P1]
**Steps:** Look up for a tender currency with no quoted rate.
**Expected:** Exception → 422 `NO_FX_RATE_AVAILABLE`.

### TC-COMMON-025 — Same-currency rate is implicit 1 [P1]
**Steps:** Look up UGX → UGX.
**Expected:** Returns 1 (no DB row required).

## Health / Metrics

### TC-COMMON-026 — Health endpoint enumerates dependencies [P1]
**Steps:** GET /actuator/health.
**Expected:** Sub-status per dep (db, redis, meilisearch); overall DOWN if any DOWN.

### TC-COMMON-027 — Custom outbox metrics exported [P2]
**Steps:** GET /actuator/prometheus.
**Expected:** `orbix_outbox_pending` gauge, `orbix_outbox_dispatch_duration_seconds` histogram.

## Idempotency

### TC-COMMON-028 — Idempotency-Key cache works [P1]
**Steps:** Two POSTs with same key + body.
**Expected:** Second returns cached response; no second domain event.

### TC-COMMON-029 — Different body, same key [P1]
**Steps:** Same key, different body.
**Expected:** 409 `IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_BODY`.

### TC-COMMON-030 — TTL window [P2]
**Steps:** Wait 24h+1m. POST with same key.
**Expected:** Treated as new request.
