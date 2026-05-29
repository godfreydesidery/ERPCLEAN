# Slice M — debt/reports e2e drift triage

**Status:** RESOLVED 2026-05-29 — debt.spec.ts 42/0, reports.spec.ts 11/0 against `orbix:qa`.
**Owner:** desideryg
**Branch:** `chore/slice-m-debt-reports-e2e-drift`

## Why this exists

While verifying Slice L (statement-fixture seeding, see
[`slice-k-reports-statements-plan.md`](slice-k-reports-statements-plan.md) for the
statement scenarios), running the broader e2e suite surfaced **pre-existing red**
in `debt.spec.ts` and `reports.spec.ts` that is unrelated to Slice L's diff.
Confirmed by running `debt.spec.ts` as a whole file in isolation:
**15 failed / 27 passed**. None of the failures touch the three files Slice L
changed (`statements.spec.ts`, `test-users.ts`, `procurement.service.ts`); the
debt failures are sales-clerk / procurement-officer / accountant-debt personas,
and no `procurement.spec.ts` test failed.

This drift was filed separately rather than folded into Slice L to keep that PR
to one logical change.

## Failure class 1 — stale `test.fail` markers (≈13 tests)

Gates that were written as `test.fail` (expecting a not-yet-implemented 403)
now return the correct status, so the `test.fail` wrapper inverts to a hard
failure ("Expected to fail, but passed"). The fix is to drop the `test.fail`
wrapper — the behaviour is correct; only the bookkeeping is stale. Same cleanup
Slice K/L did for the statement scenarios.

Known offenders (line numbers as of `origin/main` at filing):

| Spec | Lines | Gate now passing |
|---|---|---|
| `debt.spec.ts` Slice G.1 — sales-clerk | 1160, 1173, 1204, 1217 | `DEBT.READ` 403 on supplier-aging / supplier-dunning |
| `debt.spec.ts` Slice G.2 — sales-clerk | 1832, 1842, 1859, 1875 | `DEBT.READ` 403 on write-offs read/approve/reject |
| `debt.spec.ts` Slice G.2 — procurement-officer | 1935, 1945, 1961, 1977 | `DEBT.READ` 403 on write-offs |
| `reports.spec.ts` Slice J — cashier gate | 715 | stock-report permission-required state |

> Re-confirm the exact set when picking this up — line numbers drift. Grep for
> `test.fail` in both specs and run each; any that reports "Expected to fail, but
> passed" is a candidate to unwrap.

## Failure class 2 — debt happy-path setup not populating refs (≈3 tests)

`debt.spec.ts:518 / 535 / 553` (aging-bucket header cells, dunning queue row,
customer drill-down) all fail with:

```
Error: Debt refs missing — setup must run first.
Got: {"uomId":"53","itemGroupId":"3","vatGroupId":"3","itemId":"3",
      "priceListId":"3","customerName":"QA Cust Debt E2EDBT",
      "customerId":"5","customerUid":"01KSSWT8CK0Y0FYR885R377QVA"}
```

The shared refs object is only **partially** populated — `customerId`/`customerUid`
are set but the downstream ids (posted sales invoice etc.) the guard requires are
not. That points at the `debt.spec.ts` setup test throwing partway through, so
the three dependent happy-paths short-circuit on the guard. Likely a single root
failure cascading to three.

**Action:** run the debt setup test in isolation with `--reporter=line`, capture
where it throws (probable suspects: invoice post, allocation, or a business-day
precondition), fix the setup, re-confirm the three happy-paths render.

## Acceptance

- [x] `reports.spec.ts` runs clean as a standalone file (0 failed) — 11 passed, 2026-05-29.
- [x] No `test.fail` remains on a gate that currently passes — verified 2026-05-29.
- [x] `debt.spec.ts` runs clean as a standalone file (0 failed) — 42 passed, 2026-05-29.
- [ ] Full `npm run e2e` green against the local QA container — not yet run end-to-end (deferred).

## Resolution

Two test-only fixes (debt.spec.ts) + one minimal FE wiring fix closed the gate.

**1. `test.fail` cleanup (18 markers unwrapped).** Stale markers on gates/features
that have since landed (Slice G.1/G.2 DEBT.READ + write-off 403s, credit-limit
adjust, reports Slice I/J export + permission states). Each was confirmed
"Expected to fail, but passed" before unwrapping; ~20 markers on genuinely
unimplemented backend were left as `test.fail`. One more (`2.E` drill-down
panels) surfaced and was unwrapped only after the setup fix below made its refs
resolvable.

**2. `loadRefsFromDb()` recovery gap (failure class 2).** The setup test populates
`invoice45Id`/`invoice5Id` in-memory, but a recycled Playwright worker starts
with an empty `refs`, and `loadRefsFromDb()` did not recover the two invoice ids
— so `requireRefs()` threw "Debt refs missing". Fix: recover both invoices from
the DB by number suffix (`-AGE45` / `-AGE05`) joined to the debt customer,
`status='POSTED'`, most recent.

**3. Credit-limit accumulation trap (surfaced after #2).** The debt customer is
reused across runs on the persistent volume and its invoices are never paid down,
while the `2.G` adjust test pins the credit limit back to 250k each run. Once
accumulated debt crossed 250k the post-time credit gate blocked setup's invoices
(`Customer credit limit exceeded: current debt 247800, limit 250000`). Fix:
setup now raises the limit to 1e9 (within `@Digits(integer=14)`, no false-match
on the `2.G` `%250000%` event assertion) before posting.

**4. Debt-bucket testids (the one FE change).** `debt.spec.ts` asserts
`data-testid="debt-bucket-current|30|60|90|over-90"`. The five bucket-total cells
*were* rendering (labels + amounts) in `debt.component.ts` but carried no
`data-testid` — the contract attributes the spec header (lines 44–48) reserved
for the frontend to wire. Added the five attributes. Backend `GET /api/v1/debt/aging`
already returns the correct `totals` shape (`debt.spec.ts:708`). This was the
only non-test change; container image rebuilt + restarted to verify.

> Note: `statements.spec.ts` shows one "Expected to fail, but passed" on this
> branch — a divergence artifact, not a regression. This branch is cut from
> `origin/main` (pre-Slice-L); Slice L already flips that marker to `test` and is
> 9/9 green on its own branch. Slice M does not touch `statements.spec.ts`.
