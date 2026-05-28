# Slice C — Harden Sales (invoice + receipt + credit-limit + dashboard AR)

| Field | Value |
|---|---|
| Branch | `harden/sales-invoice-receipt` |
| Prereqs | Slice A (Party) merged · Slice D (Day/Cash) merged · Slice B (LPO + GRN) merged |
| Owner | PM coordinating; backend + frontend + qa + architect |
| Date | 2026-05-27 |

Current state confirmed by code-read (not just docs): `SalesInvoiceServiceImpl` already
posts stock_move synchronously, emits `SalesInvoicePosted.v1`, and has a credit-limit
check that hard-throws. `SalesReceiptServiceImpl` already calls `cashLedger.post(...)`
directly. Sales uses `extends UidEntity` everywhere. The gaps are not "build sales" —
they are checklist conformance, the missing `OVERRIDE_CREDIT` permission, the absent
`debt` module, no e2e gate, and the architecture decision that sanctions Sales' two
synchronous-TX writes (stock + cash).

---

## 1. Scope

**In**
- `SalesInvoice` + `SalesInvoiceLine` hardened to checklist bar (uid URLs already present — wire-shape pin, JSON test, ArchUnit, e2e).
- `SalesReceipt` + `ReceiptAllocation` hardened to the same bar.
- Customer price-list resolution at line creation (consumes Slice A `Customer.priceListId`).
- `SALES_INVOICE.OVERRIDE_CREDIT` permission: seed it, wire it into `checkCreditLimit`, gate POST.
- Void with compensating `stock_move` + (if posted-receipt-exists guard already in place) reject.
- Reprint audit endpoint (`POST /sales-invoices/uid/{uid}/reprint` writing an `audit_entry`).
- Dashboard AR tiles flipped from stubs to live: `openInvoiceCount`, `arOutstanding`, `overdueInvoiceCount`.
- ADR-0004 codifying Sales' synchronous-TX writes (stock_move on post, cash_entry on receipt post). Extends ADR-0003 precedent.

**Out**
- US-SALES-003/004 (quotations), US-SALES-010 (customer returns — already partially built, separate hardening slice), US-SALES-011 (credit-note apply), US-SALES-012 (packing lists).
- Standalone `debt` module — see §5 below.
- US-DEBT-003 ageing report (defer; tile values from sales queries are enough for Slice C).

**Prerequisites confirmed**
- Slice A merged at `d631f2e` lineage (Party present; `Customer.creditLimitAmount` + `priceListId` exist).
- Slice D merged (cash + day spine; `CashLedgerService.post` exists; receipts already use it).
- Slice B merged (procurement + GRN; ADR-0003 sets the synchronous-TX precedent).

## 2. Permission band

Current high-water: 113 (`GRN.CANCEL`, Slice B). Pick band **120–127** (skip 114–119
for in-flight slices). One-line collision check:
```powershell
Select-String -Path 'orbix-engine-api/src/main/resources/db/migration/common/V*permission*.sql' -Pattern '^\s*\(1[12][0-9],'
```

Proposed ids:
- 120 `SALES_INVOICE.OVERRIDE_CREDIT` — bypass credit-limit on POST
- 121 `SALES_INVOICE.REPRINT` — reprint a posted invoice (audited)
- 122 `SALES_INVOICE.READ` — list/get without write
- 123 `SALES_RECEIPT.REPRINT`
- 124 `SALES_RECEIPT.READ`
- 125 `SALES.REPORT.AR_SUMMARY` — backs dashboard AR tiles
(126–127 reserved for slice fix-ups.)

## 3. Outbox event catalogue

All events already emitted today are kept; net-new in **bold**.

| Event | Payload keys | Known subscribers |
|---|---|---|
| `SalesInvoiceCreated.v1` | id, number, customerId, totalAmount, paymentTerms | reporting |
| `SalesInvoicePosted.v1` | id, number, customerId, branchId, totalAmount, paymentTerms, currencyCode | reporting; future debt-opener |
| `SalesInvoiceVoided.v1` | id, number, customerId, totalAmount, reason | reporting; future debt-reverser |
| `SalesInvoiceCancelled.v1` | id, number | reporting |
| `SalesReceiptCreated.v1` | id, number, customerId, totalAmount, allocations | reporting |
| `SalesReceiptPosted.v1` | id, number, customerId, branchId, totalAmount, method, currencyCode | reporting; future debt-reducer |
| `SalesReceiptCancelled.v1` | id, number, reason | reporting |
| **`SalesInvoiceReprinted.v1`** | id, number, actorId, reprintReason | audit only |
| **`SalesReceiptReprinted.v1`** | id, number, actorId, reprintReason | audit only |

No `debt_entry` events — Slice C defers the standalone debt module (§5).

## 4. Posting-engine question

**Recommendation: option (b) — separate engines per channel, with shared helpers extracted into `..common.posting..`.**

Evidence from the code: `PosSaleServiceImpl` and `SalesInvoiceServiceImpl` already
diverge on the rules that matter — POS settles cash mid-transaction with multiple
tenders, runs an EOD guard, draws on a `TillSession`; Sales emits a credit-limit
gate and depends on a `PaymentTerms` enum POS does not have. Both call into
`StockMoveService` + `StockBatchService` already (same exemption seam as ADR-0003).
A "shared `PostingService`" extraction (option a) would have to thread both
ruleset shapes through one signature and would be a 3–4 day refactor for cosmetic
unity. Option (c) — POS calls Sales as the canonical engine — inverts the
dependency direction (POS is the higher-traffic, offline-tolerant path; making it
depend on Sales is wrong for the long-term contract test boundary).

The shared bits worth extracting now (≤ half-day): `StockMoveType.SALE` posting
helper, FEFO drain wrapper, and the `accountFor(method)` style enum→CashAccount
mapping. They sit in `modules.common.posting` and stay stateless.

**Consequence for ADR-0004**: this slice MUST land it. The exemption inventory now
covers two synchronous TX writes from Sales (stock_move on invoice post, cash_entry
on receipt post). Without ADR-0004 the `ModuleBoundaryTest` whitelist can't add the
named exemption and the slice ships under the wrong rule.

## 5. Three-way match / debt module

`com.orbix.engine.modules.debt` is **still absent** today — verified by directory
listing. Outstanding-debt math is done inline in `SalesInvoiceRepository.sumOutstandingDebt(partyId)`,
which works from the invoice/receipt tables directly. **Recommendation: do NOT create
the debt module in Slice C.** Reasons:
1. The dashboard AR tiles only need three aggregates (count of open invoices, sum
   of outstanding, count of overdue). All three are answerable from existing tables
   with one read endpoint (`/api/v1/sales/reports/ar-summary?branchId=…`).
2. Slice B deferred opening supplier `debt_entry` on GRN post on the same logic
   ("event already published; consumer can land later"). Symmetric on the customer
   side here.
3. Creating a parallel `debt_entry` ledger and back-filling existing invoices is a
   2-day side quest that doesn't unblock anything Slice C must ship.

**When the debt module lands**: it consumes `SalesInvoicePosted.v1`, `GrnPosted.v1`,
`SalesReceiptPosted.v1`, `SalesInvoiceVoided.v1` and writes ledger rows. That is a
clean Slice F/G — write it up after Slice C closes.

## 6. Credit-limit gate

Confirmed contract (block on POST, not on draft):
- **Where**: `SalesInvoiceServiceImpl#checkCreditLimit` (currently throws unconditionally — needs the permission off-ramp).
- **Rule**: if `currentDebt + invoiceTotal > customer.creditLimit`, AND caller does NOT hold `SALES_INVOICE.OVERRIDE_CREDIT`, **block** with HTTP 400 + the existing diagnostic message. Invoice stays in `DRAFT` so the user can either reduce the amount or escalate to an overrider.
- **If overrider posts**: log to `audit_entry` with the override reason (mandatory field on the post request when override is exercised — controller branch). Event `SalesInvoicePosted.v1` payload gains `creditOverrideBy: <actorId>` when used.
- **Block-vs-warn**: block. The user is told the breach amount and the limit; UI then surfaces "Request override" if their session has the perm.
- **Zero-limit customers**: existing throw stays — CREDIT terms not allowed without a configured limit. No override path (force the user to set a limit explicitly).

## 7. Dashboard AR tiles

From `dashboard.component.ts:354-356` + `dashboard.service.ts:50-63`:
- **Open invoices** (`openInvoices` signal, `openInvoiceCount()` service method, currently `of(SAMPLE)`).
- **AR outstanding** (`arOutstanding`, `arOutstanding()`).
- **Overdue invoices** alert tile (`overdueInvoices`, `overdueInvoiceCount()`).

All three sourced from a single new endpoint `GET /api/v1/sales/reports/ar-summary?branchId={id}` returning:
```json
{ "openCount": 12, "outstanding": 4250000.00, "overdueCount": 3, "currencyCode": "TZS" }
```
Flip `DASHBOARD_LIVE.{openInvoiceCount, arOutstanding, overdueInvoiceCount}` to `true`.

## 8. Task list (TDD-style — QA first, ADR second, backend third)

| # | Owner | Deliverable | Acceptance signal |
|---|---|---|---|
| 1 | **qa-engineer** | Failing Playwright spec `sales.spec.ts` — sales-rep persona creates draft invoice, posts it, captures a receipt against it; supervisor exercises credit-override path; AR-summary endpoint asserts. ~8 scenarios, all expected-fail at start. Persona harness already provides `sales-rep`; bootstrap will need a new `sales-supervisor` persona declaring `SALES_INVOICE.OVERRIDE_CREDIT`. | Spec runs, ≥6 expected-fails on `main`. Land before backend work. |
| 2 | **solutions-architect** | ADR-0004 — Sales-invoice → stock_move and Sales-receipt → cash_entry are permitted synchronous in-transaction dependencies (extends ADR-0003). Names the exemptions for `ModuleBoundaryTest`. Includes the §4 recommendation (separate engines, shared `..common.posting..` helpers). | ADR file committed; backend can start. |
| 3 | **backend-engineer** | Gap-close pass on `sales/`: (a) add perms 120–125 to a new `V69__seed_sales_hardening_permissions.sql`; (b) wire `SALES_INVOICE.OVERRIDE_CREDIT` into `checkCreditLimit` (extra param `boolean overrideAllowed = permissions.resolve(actorId, …).contains(...)`); (c) add `creditOverrideBy` + `creditOverrideReason` columns (edit existing `V<N>__sales*` per ephemeral-migrations rule); (d) add reprint endpoints + events; (e) `GET /sales/reports/ar-summary` controller + repo query. JSON wire-shape pin for `SalesInvoiceDto` and `SalesReceiptDto`. | `mvn -pl orbix-engine-api test` green; ArchUnit green with new named exemptions. |
| 4 | **backend-engineer** | Price-list resolution at line creation — when `request.unitPrice` is null on `CreateSalesInvoiceRequestDto.Line`, resolve from `customer.priceListId` via the existing `PriceListService`. If the customer has no price list and no unitPrice supplied, reject 400. Tests on both branches. | Service unit test passes; e2e scenario 4 green. |
| 5 | **frontend-engineer** | Flip dashboard AR tiles to the live endpoint (`DASHBOARD_LIVE.*` to true, replace `of(SAMPLE)` with real HTTP). Add Sales UI: override-reason modal when a credit-limit error returns and session holds the perm; reprint button on posted invoices/receipts; receipt-allocation screen polished to checklist § 8 (loading/empty/error/populated states). Uid routing already in place — verify it stayed correct. | `npm test` + `npm run e2e` green for `sales` and `dashboard` features. |
| 6 | **backend-engineer + frontend-engineer** | Hardening-checklist sweep on sales (sections 1–9): wire-shape JSON tests for both response DTOs, ArchUnit named-exemption update, README "Published events" table updated, controller URLs already `/uid/{uid}` so just verify. | All 10 checklist boxes ticked in PR body. |
| 7 | **qa-engineer (final QA gate)** | Re-run `sales.spec.ts` (expected-fails removed), full e2e suite, QA-image rebuild from `orbix-engine-infra/qa/Dockerfile`, smoke-test against the QA container with **all 6 personas** (sales-rep happy path, supervisor override path, accountant AR-summary read, store-manager blocked, cashier blocked, procurement irrelevant). | All scenarios green; QA report attached to PR. |
| 8 | **pm (this agent)** | PR merge → sync `main` locally (`feedback-sync-main-after-pr`). Update `USER-STORIES.md` to mark US-SALES-005/007/008/009 + US-DEBT-001 (AR position view) as Hardened. Open Slice F (debt-module) draft. | Main green; status report appended. |

## 9. Open questions for Godfrey

1. **Posting-engine architecture** — endorse the §4 (b) recommendation? Or push for (a) shared engine and accept the bigger refactor? Decision blocks ADR-0004 and so the backend start.
2. **Reprint-reason: free text or enum?** — affects `SalesInvoiceReprinted.v1` payload shape and the override modal UX. Recommendation: enum (`DUPLICATE`, `REISSUE_TO_CUSTOMER`, `INTERNAL_FILE`, `OTHER`) + optional notes field; finalises events table.
3. **`SALES.REPORT.AR_SUMMARY` permission gate** — does the AR-summary tile feed need its own permission, or piggyback on `SALES_INVOICE.READ`? Affects which personas can render the dashboard tiles without errors.

## 10. Need ADR?

**Yes — ADR-0004**, title:
> `0004-sales-posting-synchronous-tx-and-engine-split.md`

It covers two decisions: (1) which Sales→{Stock, Cash} calls are sanctioned synchronous-TX exemptions (extends ADR-0003), and (2) the Sales-vs-POS posting-engine split rule (the §4 recommendation). One ADR is right here because both decisions arise from the same architectural concern: cross-module side-effect policy for the sales hot path.

---

**Need decision on:** the three open questions in §9 (posting-engine recommendation, reprint-reason shape, AR-summary perm gating). Once those land, the slice is **ready for engineering handoff** — start with task 1 (qa-engineer writes the failing spec).
