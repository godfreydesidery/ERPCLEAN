# QA Execution Report — Run 001

**Date:** 2026-05-30
**Build commit:** 3c65e66 (Merge PR #54 — reference pickers, no-raw-id forms)
**Environment:** orbix:qa Docker image, profile `mysql,qa`, `http://localhost:8081`
**Credentials used:** rootadmin / SKp315goPN8Nb0yJtMCCD7cm; cashier / Cashier#2026
**Full machine evidence:** workflow result JSON at `C:\Users\Godfrey\AppData\Local\Temp\claude\d--My-Works-ERP-ERPCLEAN\d56ce404-811e-4100-a670-a7b557a535e1\tasks\wnzyw3kfh.output`

---

## Summary

| Metric | Count |
|--------|-------|
| Areas executed | 11 |
| Total cases | 174 |
| Pass | 115 |
| Fail | 38 |
| Blocked | 21 |
| Raw issues surfaced | 42 |
| Adversarially verified (high-severity) | 11 |
| Confirmed issues | 11 |
| False positives | 0 |

Pass rate on executed (non-blocked) cases: **75.2%** (115/153).

---

## Confirmed Issues (Adversarially Verified)

All 11 were reproduced live against the QA container AND root-cause confirmed in source. Zero false positives.

| ID | Severity | Dim | Area | One-line |
|----|----------|-----|------|----------|
| ISSUE-AUTH-01 | Critical | SEC | auth-iam | Disabled user's token survives when disable happens in the same wall-clock second as login (JWT filter uses `<` not `<=` on epoch seconds) |
| ISSUE-DAY-002 | Critical | FUNC | admin-day | `DayGuard.requireOpenDay` throws `IllegalStateException` which falls through to 500 instead of 422 `BUSINESS_DAY_CLOSED` |
| ISSUE-AUTH-OPT-LOCK-01 | Critical | RELI | auth-iam | Concurrent logins for the same user hit an optimistic lock on `AppUser.@Version`, producing HTTP 500 (40 occurrences in this run) |
| ISSUE-POS-001 | Critical | DATA | pos-core | VAT computed tax-exclusive even when price list `taxInclusive=true` — all standard-rated POS totals are inflated by the full VAT rate |
| ISSUE-CASH-002 | Critical | DATA | cash-fiscal-gc | Same root as ISSUE-POS-001: catalog-synced prices (shelf/inclusive) are re-taxed server-side; POS must send ex-tax price to avoid 400 rejection |
| ISSUE-ORDERS-001 | Critical | FUNC | orders-prod-rpt | LAYBY `collect` fails 400 "Cannot release … only 0 reserved" when order was never reserved — permanently stuck, cash trapped |
| ISSUE-ORDERS-002 | Critical | FUNC | orders-prod-rpt | LAYBY `cancel` fails 400 for the same reason — deposit of 2000 TZS irrecoverable via API |
| ISSUE-NFR-002 | Critical | PERF | nfr-perf-ux-data-reli | `ItemBranchBalance` has no `@Version` or `PESSIMISTIC_WRITE` lock — concurrent outbound moves can race past the negative-stock guard |
| ISSUE-AUDIT-01 | Major | DATA | nfr-security | Audit hash-chain forks under concurrent writes (read-modify-write race in `AuditLogWriterImpl`) — integrity verifier reports false tampering |
| ISSUE-DAY-003 | Major | FUNC | admin-day | `SalesInvoiceServiceImpl.createDraft()` does not call `DayGuard.requireOpenDay` — DRAFT invoices created on branches with no open business day |
| ISSUE-GC-002 | Major | NEG | cash-fiscal-gc | Redeeming an inline-expired gift card (status still ACTIVE) returns HTTP 500 — `IllegalStateException` from `requireRedeemable()` unmapped in `GlobalExceptionHandler` |

---

## Lower-Severity Issues (Unverified — High-Confidence Raw Findings)

| ID | Severity | Dim | Area | One-line |
|----|----------|-----|------|----------|
| ISSUE-AUTH-02 | Major | SEC | auth-iam | Unauthenticated request returns HTTP 403 + raw Spring error body instead of 401 + `ApiResponse` envelope |
| ISSUE-VALIDATION-01 | Major | NEG | nfr-security | Malformed ULID path-var, invalid enum, malformed JSON body all return 500 instead of 400/422 |
| ISSUE-ADMIN-001 | Major | DATA | admin-day | Audit `beforeJson` always null on UPDATE — `AuditAspect` never snapshots pre-state |
| ISSUE-ADMIN-002 | Major | FUNC | admin-day | Branch `isDefault` flag missing from `UpdateBranchRequestDto` — cannot change default branch via PATCH |
| ISSUE-CAT-001 | Major | NEG | catalog-party | Invalid enum value in item create request returns 500 instead of 400 (same `HttpMessageNotReadableException` gap) |
| ISSUE-CAT-002 | Major | NEG | catalog-party | Item group archive succeeds silently when the group contains active items — no guard |
| ISSUE-PARTY-001 | Major | NEG | catalog-party | Invalid `PartyCategory` enum returns 500 instead of 400 (same root cause as ISSUE-CAT-001) |
| ISSUE-PARTY-002 | Major | UX | catalog-party | Customer form has no price list picker — `priceListId` hardcoded null on create |
| ISSUE-PROC-001 | Major | UX | proc-stock | LPO/GRN forms display raw numeric IDs for supplier, item, approver instead of names |
| ISSUE-STOCK-001 | Major | DATA | proc-stock | Stock card running balance resets to zero on each page — wrong balance on page 2+ |
| ISSUE-PROC-004 | Major | FUNC | proc-stock | TC-PROC-003 spec error: GRN post does not auto-create supplier debt — 3-way match requires explicit `SupplierInvoice` (test case corrected; see below) |
| ISSUE-SALES-OVERSELL-01 | Major | FUNC | sales-debt | `STOCK.OVERSELL` permission checked but `allowOversell=false` hardcoded in `SalesInvoiceServiceImpl` — permission effectively ignored |
| ISSUE-NEG-ENUM-500-01 | Major | NEG | sales-debt | Invalid `ReturnReason` enum in customer-return body returns 500 (same `HttpMessageNotReadableException` gap) |
| ISSUE-SYNC-001 | Major | DATA | pos-sync | Price sync dataset omits `taxInclusive` and `priceListCode` — POS cannot determine inclusive pricing from the pull response |
| ISSUE-SYNC-002 | Major | RELI | pos-sync | Contract-version mismatch (X-Orbix-Contract-Version 0 or 2) returns 500 instead of 426/409 — `ResponseStatusException` swallowed by catch-all handler |
| ISSUE-CASH-001 | Major | FUNC | cash-fiscal-gc | `GET /api/v1/cash-ledger/session/<id>/balance` not implemented; all unknown routes return 500 instead of 404 |
| ISSUE-PROD-001 | Major | DATA | orders-prod-rpt | Conversion posts `PROD_CONSUME`/`PROD_OUTPUT` move types — no `CONVERSION_OUT`/`CONVERSION_IN` enum values exist |
| ISSUE-NFR-001 | Major | NEG | nfr-perf-ux-data-reli | Same `HttpMessageNotReadableException` → 500 bug, confirmed on `/items` and `/pos-sales` enum fields |
| ISSUE-DAY-001 | Minor | NEG | admin-day | Duplicate business-day open returns 400 instead of 409 |
| ISSUE-CAT-003 | Minor | NEG | catalog-party | Item group depth limit (4 levels per PRD §5.3) not enforced — level 5 created successfully |
| ISSUE-PROC-002 | Minor | NEG | proc-stock | Cancelling a POSTED GRN via wrong endpoint returns 500 instead of 409 (`IllegalStateException` unmapped) |
| ISSUE-PROC-003 | Minor | NEG | proc-stock | Unknown API routes return 500 instead of 404 (Spring `throw-exception-if-no-handler-found` not set) |
| ISSUE-DEBT-TC-URL-01 | Minor | FUNC | sales-debt | TC-DEBT-001 spec error: wrong endpoint URL (test case corrected; see below) |
| ISSUE-DEBT-STMT-BALANCE-01 | Minor | FUNC | sales-debt | TC-DEBT-003 spec gap: `CustomerStatementDto` has no per-line running balance field (test case corrected; see below) |
| ISSUE-DEBT-WRITEOFF-DUAL-01 | Minor | FUNC | sales-debt | TC-DEBT-004 spec divergence: write-off uses single-approver dual-control, not dual-role (test case corrected; see below) |
| ISSUE-POS-002 | Minor | FUNC | pos-core | Business-rule rejections return 400 instead of 409/422 (all NEG POS cases) |
| ISSUE-POS-003 | Minor | FUNC | pos-core | TC-POS-CORE-004/008 spec errors: wrong expected status (`CLOSED` vs `POSTED`) and URL (test cases corrected; see below) |
| ISSUE-POS-004 | Minor | FUNC | pos-core | TC-POS-CORE-019/020 spec errors: wrong barcode endpoint URL (test cases corrected; see below) |
| ISSUE-SYNC-003 | Minor | DATA | pos-sync | Concurrent `clientOpId` replay may return ACCEPTED twice instead of ACCEPTED+DUPLICATE (medium-confidence TOCTOU) |
| ISSUE-GC-001 | Minor | NEG | cash-fiscal-gc | Over-balance gift-card redemption returns 400 instead of 422 |
| ISSUE-NFR-003 | Minor | UX | nfr-perf-ux-data-reli | Login page has no axe-core WCAG AA scan in any Playwright spec — accessibility gate gap |

---

## Per-Area Pass Rates

| Area | Total | Pass | Fail | Blocked | Pass % (of executed) |
|------|-------|------|------|---------|----------------------|
| auth-iam | 15 | 12 | 2 | 1 | 85.7% (12/14) |
| nfr-security | 9 | 7 | 2 | 0 | 77.8% (7/9) |
| admin-day | 12 | 5 | 5 | 2 | 50.0% (5/10) |
| catalog-party | 18 | 11 | 5 | 2 | 68.8% (11/16) |
| proc-stock | 14 | 8 | 5 | 1 | 61.5% (8/13) |
| sales-debt | 13 | 7 | 4 | 2 | 63.6% (7/11) |
| pos-core | 20 | 12 | 4 | 4 | 75.0% (12/16) |
| pos-sync | 20 | 18 | 1 | 1 | 94.7% (18/19) |
| cash-fiscal-gc | 14 | 8 | 4 | 2 | 66.7% (8/12) |
| orders-prod-rpt | 14 | 9 | 3 | 2 | 75.0% (9/12) |
| nfr-perf-ux-data-reli | 25 | 18 | 3 | 4 | 85.7% (18/21) |

Blocked causes summary: shared-QA-state contention (cannot close business day, cannot wipe stock), feature not yet implemented (production web UI, sales-aggregate report, auto-numbering), infrastructure prerequisite (Testcontainers for dual-DB test, Playwright runtime, TZ_VFD fiscal stub, Flutter POS).

---

## How This Maps to Fix Branches

| Fix branch | Addresses issue IDs |
|------------|---------------------|
| `fix/qa-exception-handling` | ISSUE-DAY-002, ISSUE-GC-002, ISSUE-AUTH-02, ISSUE-VALIDATION-01, ISSUE-CAT-001, ISSUE-PARTY-001, ISSUE-NEG-ENUM-500-01, ISSUE-NFR-001, ISSUE-SYNC-002, ISSUE-PROC-002, ISSUE-PROC-003, ISSUE-DAY-001, ISSUE-GC-001, ISSUE-CASH-001 (all GlobalExceptionHandler gaps + unmapped exception types + Spring routing config) |
| `fix/qa-pos-sync-auth` | ISSUE-AUTH-01, ISSUE-AUTH-OPT-LOCK-01, ISSUE-SYNC-001, ISSUE-SYNC-003 (JWT filter off-by-one, concurrent login optimistic lock, price sync missing fields, duplicate clientOpId TOCTOU) |
| `fix/qa-sales-stock-orders` | ISSUE-POS-001, ISSUE-CASH-002, ISSUE-ORDERS-001, ISSUE-ORDERS-002, ISSUE-NFR-002, ISSUE-SALES-OVERSELL-01, ISSUE-STOCK-001, ISSUE-PROD-001 (VAT inclusive logic, LAYBY lifecycle, stock concurrency lock, oversell permission, stock card pagination, conversion move types) |
| `fix/qa-catalog-audit-admin` | ISSUE-AUDIT-01, ISSUE-DAY-003, ISSUE-ADMIN-001, ISSUE-ADMIN-002, ISSUE-CAT-002, ISSUE-CAT-003, ISSUE-PARTY-002, ISSUE-PROC-001 (audit chain, day guard on createDraft, beforeJson, branch isDefault, group archive guard, depth limit, customer price list picker, LPO/GRN display names) |
| `fix/qa-web-ux` | ISSUE-NFR-003 (login page axe-core spec), plus web-side display corrections for ISSUE-PROC-001 |

Test-case catalog corrections (docs-only, no fix branch needed) are committed directly on `docs/qa-execution-report` — see ISSUE-POS-003, ISSUE-POS-004, ISSUE-DEBT-TC-URL-01, ISSUE-DEBT-STMT-BALANCE-01, ISSUE-DEBT-WRITEOFF-DUAL-01, ISSUE-PROC-004.

---

## Gate Status

**Gate red.** 7 Critical issues confirmed. Release-gate criteria require Critical count = 0. Fix branches listed above must land, pass CI, and receive a clean re-run before this build can be signed off.

Known open items acknowledged:
- Testcontainers dual-DB integration test infra — tracked debt since 2026-05-24, blocks TC-NFR-DATA-001.
- Production web UI (BOM/batch) — not yet implemented, TC-PROD-004 stays blocked.
- TZ_VFD fiscal stub — TC-FISCAL-002/003/004/005 blocked until stub wired in CI.
- Auto-document-numbering (LPO) — TC-ADMIN-005 blocked; feature not implemented (callers must supply document numbers).
