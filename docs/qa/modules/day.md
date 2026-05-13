# Day — test plan

Business-day open / close / override + EOD orchestration.

## Open

### TC-DAY-001 — Open business day [P1]
**Stories:** US-DAY-001
**Steps:** POST /api/v1/business-days `{ branchId, businessDate }`.
**Expected:** 201; one row in `business_day` with status OPEN, opened_at, opened_by; `BusinessDayOpened.v1`.

### TC-DAY-002 — At most one OPEN per branch [P1]
**Steps:** Try to open another day on same branch.
**Expected:** 409 `BUSINESS_DAY_ALREADY_OPEN`.

### TC-DAY-003 — Monotonic business_date [P1]
**Steps:** Open business_date earlier than previous closed.
**Expected:** 422 `BUSINESS_DATE_BACKWARD`.

### TC-DAY-004 — Auto-roll on EOD success [P1]
**Steps:** EOD completes successfully.
**Expected:** Next day auto-created with status OPEN; opened_by SYSTEM.

### TC-DAY-005 — Skip-day on missed open [P2]
**Steps:** Branch hasn't opened for 2 days; manager opens today.
**Expected:** Behaviour per policy (currently: auto-open intermediate empty days marked opened_by SYSTEM).

## End-of-day

### TC-DAY-006 — Happy-path close [P1]
**Stories:** US-DAY-002
**Steps:** All tills closed, no pending GRNs, no open production. POST :end.
**Expected:** status CLOSING → emit `BusinessDayClosingStarted.v1` → wait for ack → CLOSED. Next day auto-opened.

### TC-DAY-007 — Open till blocks close [P1]
**Steps:** One till still OPEN.
**Expected:** 422 with list of offending tills.

### TC-DAY-008 — Unposted GRN blocks close [P1]
**Steps:** DRAFT GRN exists for today.
**Expected:** 422.

### TC-DAY-009 — Open production batch blocks close [P2]
**Steps:** Production batch in `IN_PROGRESS` or any OUTPUT_* state.
**Expected:** 422 with batch id.

### TC-DAY-010 — Ack timeout reverts to OPEN [P2]
**Steps:** A consumer fails to ack within window.
**Expected:** Day moves back to OPEN; alert raised; report identifies the failing consumer.

## Override

### TC-DAY-011 — Open override [P2]
**Stories:** US-DAY-003
**Steps:** Supervisor POST override with reason + PIN.
**Expected:** `BusinessDayOverrideOpened.v1`; grant has expires_at = now + window.

### TC-DAY-012 — Post into closed day within override [P2]
**Steps:** Procurement posts supplier_invoice with `posting_date = yesterday` while override active.
**Expected:** 201; `business_day_override` row tagged with the entity.

### TC-DAY-013 — Override expiry [P2]
**Steps:** Wait window+1m. Try another posting.
**Expected:** 422; scheduled job emits `BusinessDayOverrideExpired.v1`.

### TC-DAY-014 — Multiple overrides for same day [P2]
**Steps:** Open second override while first is active.
**Expected:** Allowed; each posting tagged with its override_id.

## Cross-branch + status

### TC-DAY-015 — Cross-branch status [P2]
**Stories:** US-DAY-004
**Steps:** GET /business-days/status.
**Expected:** Per-branch summary; oldest open date highlighted.

### TC-DAY-016 — Auto-warn before expiry [P2]
**Stories:** US-DAY-005
**Steps:** Day opened 30h ago. Scheduled job runs.
**Expected:** Notification emitted to branch manager.

## DayGuard (port consumed by other modules)

### TC-DAY-017 — checkPostingAllowed returns ALLOW for open day [P1]
**Steps:** Module X calls DayGuard for current open day.
**Expected:** ALLOW.

### TC-DAY-018 — checkPostingAllowed returns DENY for closed day [P1]
**Steps:** Date d's business_day = CLOSED, no override.
**Expected:** DENY with reason.

### TC-DAY-019 — checkPostingAllowed returns ALLOW_VIA_OVERRIDE [P2]
**Steps:** Active override covers (branch, date).
**Expected:** ALLOW_VIA_OVERRIDE(override_id); module stamps the entity.

## Time-zone

### TC-DAY-020 — business_date in branch.timezone [P1]
**Steps:** Branch in Africa/Kampala. Open day Mon 23:55 local.
**Expected:** business_date = Monday (local). server timestamps UTC.

### TC-DAY-021 — Rollover at midnight [P1]
**Steps:** Sale at 00:00:05 local on a closing branch.
**Expected:** Sale tagged with NEW business_date if old day already CLOSED; rejected if old day CLOSING.

### TC-DAY-022 — Branches in different time zones [P2]
**Steps:** Branch A in UG (UTC+3), branch B in Saudi (UTC+3). Same calendar date; different open/close times.
**Expected:** Independent business_days; cross-branch report uses each branch's local date.

## Edge

### TC-DAY-023 — Concurrent open attempts [P2]
**Steps:** Two clients open same branch + date.
**Expected:** Exactly one wins; other 409.

### TC-DAY-024 — Closed day immutable [P1]
**Steps:** Attempt to reopen CLOSED day.
**Expected:** 422; corrections go via override only.

### TC-DAY-025 — Idempotent EOD invocation [P1]
**Steps:** POST :end twice within CLOSING window.
**Expected:** Second is no-op; same response.
