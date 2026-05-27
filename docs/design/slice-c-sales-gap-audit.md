# Slice C — Sales Hardening Gap Audit

Section-by-section diff of the sales aggregates against
`docs/conventions/hardening-checklist.md`. Backend-engineer uses this to
drive implementation; PM uses it to scope. Mirrors
`docs/design/slice-b-procurement-gap-audit.md`.

## Aggregates audited

- `SalesInvoice`, `SalesInvoiceLine`, `SalesReceipt`, `ReceiptAllocation`.
- Source: `orbix-engine-api/src/main/java/com/orbix/engine/modules/sales/`.
- Migrations: `V27__sales_invoices.sql`, `V28__seed_sales_permissions.sql`,
  `V29__sales_receipts.sql`.
- Out of scope: `CustomerReturn`, `CustomerCreditNote`, `PackingList`,
  `SalesQuotation`.
- Cross-cutting ADR: ADR-0004 codifies the sync-TX exemptions consumed here
  (sales→stock, sales→cash).

## Section 1 — Data layer

- [x] Pre-stability migrations edit `V27` / `V29` in place.
- [x] No native SQL; portable DDL (`V27`, `V29`).
- [x] `uid CHAR(26) NOT NULL` + `uk_sales_invoice_uid` / `uk_sales_receipt_uid`
  on both headers (`V27:9,39`; `V29:8,29`). Lines + allocations are internal
  child tables, no `uid` — correct.
- [x] Multi-tenant: `company_id` + `branch_id` on both headers with FKs
  (`V27:11-12,41-42`; `V29:11-12,31-32`).
- [x] `@Id BIGINT` SEQUENCE, `allocationSize = 50` on all four entities.
- [x] FKs on every cross-table ref, default `NO ACTION`.
- [x] Audit columns + `@Version` on header rows (`V27:34-38`, `V29:24-28`).
- [x] Decimal precision per checklist: money `DECIMAL(18,4)`, rate
  `DECIMAL(10,4)` on `discount_pct`.
- [ ] **GAP 1.A**: Lines + allocations have no `company_id` / `branch_id`
  (tenant-inherited from parent). README §10 (`README.md:119`) claims
  "every row carries `company_id` and `branch_id`" — backend-engineer:
  update README to document the inheritance. No schema change.
- [ ] **GAP 1.B**: AR-summary tile workload needs two composite indexes
  on `sales_invoice`: `(company_id, branch_id, status)` for open-count,
  `(company_id, branch_id, due_date, status)` for overdue-count. Today
  only `(company_id, status)` exists (`V27:47`). Add in the V27 baseline.
- [ ] **GAP 1.C**: `sales_invoice` lacks `credit_override_by BIGINT` /
  `credit_override_reason VARCHAR(500)` columns. Required by the locked
  override decision (GAP 3.A / §6). Add nullable columns in V27.

## Section 2 — Domain layer (DTOs + enums)

- [x] All DTOs end with `Dto`; nested records named `Line` / `Allocation`
  per catalog precedent.
- [x] DTOs are `record`s, immutable (`SalesInvoiceDto.java:13`,
  `SalesReceiptDto.java:13`); entities use Lombok `@Data`.
- [x] Response DTOs carry `id` (Long) + `uid` (String). JSON pin tests
  `SalesInvoiceDtoJsonTest` + `SalesReceiptDtoJsonTest` exist.
- [x] Enums in `domain/enums/`, `@Enumerated(EnumType.STRING)` everywhere.
- [ ] **GAP 2.A**: Bean validation is **thin** on monetary and string
  fields. `CreateSalesInvoiceRequestDto`: `number` `@NotBlank` lacks
  `@Size(max=40)`; `currencyCode` lacks `@Size(min=3,max=3)` /
  `@Pattern("[A-Z]{3}")`; `notes` lacks `@Size(max=2000)`; `reference`
  lacks `@Size(max=80)`. `Line.qty` + `unitPrice` lack
  `@Digits(integer=14, fraction=4)`; `discountPct` unannotated (needs
  `@DecimalMin(0)` / `@DecimalMax(100)`). Same shape on
  `CreateSalesReceiptRequestDto`. `VoidSalesInvoiceRequestDto.reason`
  lacks `@Size(max=200)` despite the DB col being `VARCHAR(200)`
  (`V27:31`). Backend-engineer: add the constraints across all three
  request DTOs and the nested records.
- [ ] **GAP 2.B**: Slice C adds `ReprintSalesInvoiceRequestDto`,
  `ReprintSalesReceiptRequestDto`, and the enum
  `ReprintReason { DUPLICATE, REISSUE_TO_CUSTOMER, INTERNAL_FILE, OTHER }`
  in `domain/enums/`. Each record carries
  `@NotNull ReprintReason reason` + `@Size(max=500) String notes`. Per
  the locked Slice C decision.
- [ ] **GAP 2.C**: New `ArSummaryDto` — see §8 for the field list.

## Section 3 — Service layer

- [x] `*Service` + `*ServiceImpl` pairs on both aggregates; impls
  `@Service @RequiredArgsConstructor` with `final` collaborators.
- [x] Every public method has `@Transactional`; writes carry `@Auditable`.
- [x] External entry points take `String uid`;
  `requireInvoiceByUid` / `requireReceiptByUid` enforce the tenant
  predicate (`SalesInvoiceServiceImpl.java:356-364`,
  `SalesReceiptServiceImpl.java:235-243`).
- [x] Cross-module sync calls are within ADR-0004's named-exemption set
  (sales→stock, sales→cash) and use interface seams only
  (`stockMoveService`, `stockBatchService`, `cashLedger`).
- [ ] **GAP 3.A — load-bearing**: `checkCreditLimit`
  (`SalesInvoiceServiceImpl.java:336-354`) throws **unconditionally** on
  any limit breach. README §10 (`README.md:115`) documents an override
  path: "...unless caller holds `SALES_INVOICE.OVERRIDE_CREDIT`". But the
  permission does not exist and the service has no off-ramp.
  Backend-engineer: (a) seed the permission (§6); (b) thread an
  `overrideAllowed` boolean through `checkCreditLimit` derived from
  `permissions.resolve(actorId, companyId, branchId).contains(SALES_INVOICE_OVERRIDE_CREDIT)`;
  (c) when override is exercised populate the new override columns
  (GAP 1.C) and include them on the `SalesInvoicePosted.v1` payload
  (§9.A); (d) override does NOT apply on the `limit.signum() == 0`
  (no-credit-limit) branch — that error stays unconditional per the
  locked decision.
- [ ] **GAP 3.B**: No `reprintInvoice` / `reprintReceipt` methods. Each
  looks up the entity, verifies reprint-eligible status (POSTED /
  PARTIALLY_PAID / PAID / VOIDED for invoices; POSTED for receipts),
  emits the new event (§9.B/C), returns the DTO. No state mutation —
  pure audit. `@Auditable(action="REPRINT")`.
- [ ] **GAP 3.C**: No `arSummary(Long branchId)` method. Land on the
  existing `SalesReportService` (avoid premature module growth). Returns
  `ArSummaryDto`. See §8.A for the signature; §4.A for the repo queries.
- [ ] **GAP 3.D**: `createDraft` does not consult the customer's
  `price_list_id` to default `unitPrice` when null on the request.
  Backend-engineer: when `request.lines[].unitPrice` is null, resolve
  via `PriceListService` keyed by `customer.priceListId` (or the
  invoice-level `priceListId` request field). Reject with 400 if no
  list applies and no `unitPrice` is supplied. Tests both branches.

## Section 4 — Repository layer

- [x] All repos extend `JpaRepository<X, Long>`; no native queries. The
  `@Query` blocks in `SalesInvoiceRepository` and `SalesReceiptRepository`
  are portable JPQL.
- [x] `findByUid` on both header repos (`SalesInvoiceRepository:19`,
  `SalesReceiptRepository:19`).
- [x] Column-named lookups (`existsByBranchIdAndNumber`,
  `findByCompanyIdAndBranchIdOrderByIdDesc`).
- [x] No controller→repository reach (`ModuleBoundaryTest` green).
- [ ] **GAP 4.A**: AR-summary needs three new JPQL methods on
  `SalesInvoiceRepository`: `sumOutstandingForBranch(companyId, branchId)`,
  `countOpenForBranch(companyId, branchId)`,
  `countOverdueForBranch(companyId, branchId, today)`. Each accepts
  null `branchId` for company-wide fall-back (mirrors `findPostedOnDate`
  shape). Backed by the new indexes from GAP 1.B.

## Section 5 — REST layer

- [x] URL shape `/api/v1/sales-invoices/uid/{uid}` and
  `/api/v1/sales-receipts/uid/{uid}` — literal `uid` segment present.
- [x] All `{uid}` annotated `@ValidUlid`.
- [x] Writes gated by `@PreAuthorize` (`SALES.MANAGE_INVOICE` /
  `SALES.MANAGE_RECEIPT` class-level).
- [x] `@Validated` on type, `@Valid` on bodies; no manual `ApiResponse`
  wrapping; `POST` returns `ResponseEntity.created(...)` on both.
- [x] **State-transitions already return 200 + DTO** per the updated
  checklist §5 (`SalesInvoiceController.java:53,58,64`,
  `SalesReceiptController.java:48,53`). No flip needed — sales is
  already conformant with the Slice B precedent.
- [x] Controllers in `com.orbix.engine.api` (flat).
- [ ] **GAP 5.A**: New endpoints per Slice C:
  - `POST /sales-invoices/uid/{uid}/reprint` —
    `@PreAuthorize("hasAuthority('SALES_INVOICE.REPRINT')")`, body
    `ReprintSalesInvoiceRequestDto`, returns `SalesInvoiceDto`.
  - `POST /sales-receipts/uid/{uid}/reprint` —
    `@PreAuthorize("hasAuthority('SALES_RECEIPT.REPRINT')")`, body
    `ReprintSalesReceiptRequestDto`, returns `SalesReceiptDto`.
  - `GET /sales/reports/ar-summary?branchId={id}` —
    `@PreAuthorize("hasAuthority('SALES.REPORT.AR_SUMMARY')")`, returns
    `ArSummaryDto`. Add to existing `SalesReportController`.
- [ ] **GAP 5.B**: `POST .../uid/{uid}/post` takes no body today
  (`SalesInvoiceController.java:53-56`). Add an optional
  `PostSalesInvoiceRequestDto(@Size(max=500) String creditOverrideReason)`
  — null in the normal case; service validates that when the user holds
  the override perm AND the projection breaches the limit,
  `creditOverrideReason` must be present.

## Section 6 — Permissions

- [x] Existing seed migration `V28__seed_sales_permissions.sql` follows
  the pattern; ids 31-35 stable; granted to role 1.
- [x] Codes follow `MODULE.ACTION` (`SALES.MANAGE_INVOICE`,
  `SALES.MANAGE_RECEIPT`, `SALES.DISCOUNT_APPROVE`, etc.).
- [ ] **GAP 6.A**: `Permissions.java` has only sales-agent constants
  (lines 51-53), no constants for the five existing sales permissions
  or the new ones. Same shape as Slice B GAP 6.A. Add a `// ---- sales ----`
  section with constants for the five existing + six new permissions.
- [ ] **GAP 6.B**: New migration
  `V<next>__seed_sales_hardening_permissions.sql` for the six locked ids
  120-125: `120 SALES_INVOICE.OVERRIDE_CREDIT`,
  `121 SALES_INVOICE.REPRINT`, `122 SALES_INVOICE.READ`,
  `123 SALES_RECEIPT.REPRINT`, `124 SALES_RECEIPT.READ`,
  `125 SALES.REPORT.AR_SUMMARY`. Grant all to role 1. Collision check
  at PR time (current high-water 113 via V68 — band clear).

## Section 7 — Tests

- [x] `SalesInvoiceServiceImplTest`, `SalesReceiptServiceImplTest`,
  `SalesInvoiceDtoJsonTest`, `SalesReceiptDtoJsonTest` exist. Repository
  + `RequestContext` mocked; `@PrePersist` bypassed via reflection.
- [x] Outbox emission asserted in existing tests.
- [ ] **GAP 7.A**: No test covers the credit-limit override path (path
  doesn't exist yet). After GAP 3.A lands, add: (i) no-override-perm +
  breach → reject; (ii) override-perm + breach + reason → posts,
  payload carries `creditOverrideBy` + `creditOverrideReason`;
  (iii) override-perm + breach + null reason → 400; (iv) override-perm
  + zero-limit customer → still rejected.
- [ ] **GAP 7.B**: No reprint test. After GAP 3.B lands, assert both
  events emit with reason enum + notes payload and that ineligible
  statuses reject.
- [ ] **GAP 7.C**: No AR-summary test. Extend `SalesReportServiceImplTest`
  (or add) to mock the three repo queries and pin the response shape.
- [ ] **GAP 7.D**: Extend the JSON pin tests for the new override fields
  on `SalesInvoiceDto` and pin the `ArSummaryDto` wire shape.
- [ ] **GAP 7.E**: `ModuleBoundaryTest` extension after ADR-0004 lands —
  named-exemption entries for `sales` → `stock` and `sales` → `cash`.
  Tests stay green; today's broad `..service..` allowance is what's
  permitting them. ArchUnit rework is in scope for the slice.

## Section 8 — Web (Angular) — LONG

The sales feature exists under `orbix-engine-web/src/app/features/sales/`
(`invoices.component.ts`, `receipts.component.ts`, plus
`sales.models.ts` and `sales.service.ts`). Models + service use `string`
uid throughout — uid-routing already in place.

- [x] Models declare `id: string` and `uid: string`
  (`sales.models.ts:24-28,100-103`). Every `…Id` field on the model is
  `string`.
- [x] Service calls uid endpoints (`sales.service.ts:32-77`):
  `GET /sales-invoices/uid/{uid}`, `POST /sales-invoices/uid/{uid}/post`,
  `POST /sales-invoices/uid/{uid}/void`,
  `POST /sales-invoices/uid/{uid}/cancel`, symmetric for receipts.
- [x] Standalone components, lazy-loaded per `sales.routes.ts`.
- [ ] **GAP 8.A — AR-summary tile feed (load-bearing)**. Dashboard
  tiles are stubbed at `dashboard.service.ts:50-63`
  (`openInvoiceCount() → of(SAMPLE...)`, `arOutstanding()`,
  `overdueInvoiceCount()`). Component signal names are `openInvoices`,
  `arOutstanding`, `overdueInvoices` (`dashboard.component.ts:354-356`)
  — verified. Flip `DASHBOARD_LIVE.openInvoiceCount`,
  `DASHBOARD_LIVE.arOutstanding`, `DASHBOARD_LIVE.overdueInvoiceCount`
  (`dashboard.service.ts:72-79`) to `true` and replace the three
  `of(SAMPLE.*)` calls with one HTTP call to
  `GET /api/v1/sales/reports/ar-summary?branchId={id}`. Recommend an
  internal `arSummary(branchId)` helper on `DashboardService` doing
  one HTTP and exposing three derived observables to avoid three
  duplicated requests. Response shape (`ArSummaryDto`):
  ```json
  { "arOutstanding": 4250000.0,
    "overdueInvoices": 3,
    "openInvoices": 12,
    "currencyCode": "TZS" }
  ```
  Field names match the dashboard signals exactly — minimises mapping
  layer. `currencyCode` is included so the tile renders without a
  second config call.
- [ ] **GAP 8.B — Override-reason modal**. When `POST .../uid/{uid}/post`
  returns 400 with the credit-limit-breach error and the session perms
  include `SALES_INVOICE.OVERRIDE_CREDIT`, surface a modal capturing
  `creditOverrideReason` and resubmit with the body. Without the perm,
  show the original error + "Contact a supervisor" guidance. Lives in
  `invoices.component.ts`.
- [ ] **GAP 8.C — Reprint button + reason picker**. Posted invoices /
  receipts grow a "Reprint" action opening a modal with the
  `ReprintReason` enum as a radio group + optional notes textarea.
  Submit POSTs to the reprint endpoint and refreshes the row.
- [ ] **GAP 8.D — Sales models additions**: `sales.models.ts` adds
  `ReprintReason`, `ReprintSalesInvoiceRequest`,
  `ReprintSalesReceiptRequest`, and `creditOverrideBy: string | null`
  + `creditOverrideReason: string | null` on `SalesInvoice`. Add
  `ArSummary` to dashboard models or local to `dashboard.service.ts`.
- [ ] **GAP 8.E — Four-state rendering** (loading / empty / error /
  populated) on all new screens. Checklist §8 gate.
- [ ] **GAP 8.F — Tests**: `npm test` for sales + dashboard;
  `npm run e2e` (axe-core inside Playwright) green. The qa-engineer's
  `sales.spec.ts` covers the cross-component flow (sales-rep +
  sales-supervisor personas).

## Section 9 — Cross-module events — LONG

Net-new events in **bold**. Three new event types, one payload widening.

| Event | Payload keys | Status |
|---|---|---|
| `SalesInvoiceCreated.v1` | id, number, customerId, totalAmount, paymentTerms | unchanged |
| `SalesInvoicePosted.v1` | id, number, customerId, branchId, totalAmount, paymentTerms, currencyCode | **widening — 9.A** |
| `SalesInvoiceVoided.v1` | id, number, customerId, totalAmount, reason | unchanged |
| `SalesInvoiceCancelled.v1` | id, number | unchanged |
| `SalesReceiptCreated.v1` | id, number, customerId, totalAmount, allocations | unchanged |
| `SalesReceiptPosted.v1` | id, number, customerId, branchId, totalAmount, method, currencyCode | unchanged |
| `SalesReceiptCancelled.v1` | id, number | unchanged |
| **`SalesInvoiceReprinted.v1`** | id, number, actorId, reprintReason, notes | new — 9.B |
| **`SalesReceiptReprinted.v1`** | id, number, actorId, reprintReason, notes | new — 9.C |

- [x] Versioned `<Aggregate><Action>.v1`; payload `Map<String,Object>`
  with stable keys; emitted inside the `@Transactional` write.
- [ ] **GAP 9.A — `SalesInvoicePosted.v1` payload widening**. Slice B
  precedent (GrnPosted.v1) widened to carry per-line breakdown for the
  debt-opener. **Recommendation: widen symmetrically here.** When the
  standalone `debt` module lands (deferred to Slice F per the plan) it
  needs per-line `itemId`, `qty`, `unitPrice`, `vatGroupId`, `lineTotal`
  to build the customer sub-ledger. The service already has the lines
  in hand (`SalesInvoiceServiceImpl.java:128`); adding the array now
  costs nothing and avoids a future `.v2` bump. Add
  `lines: List<Map<String,Object>>` plus the override fields on the
  header (`creditOverrideBy`, `creditOverrideReason`) when exercised.
- [ ] **GAP 9.B — `SalesInvoiceReprinted.v1`**. Payload
  `salesInvoiceId, number, actorId, reprintReason, notes`. `reprintReason`
  is the enum name string. Emitted from
  `SalesInvoiceServiceImpl#reprint(uid, ReprintSalesInvoiceRequestDto)`.
  No state mutation; pure audit. The `@Auditable(action="REPRINT")`
  aspect writes `audit_entry` separately.
- [ ] **GAP 9.C — `SalesReceiptReprinted.v1`**. Symmetric to 9.B.
  Payload `salesReceiptId, number, actorId, reprintReason, notes`.
- [ ] **GAP 9.D — README §5 update**. `modules/sales/README.md §5`
  lists `SalesReceiptCaptured.v1` + `ReceiptAllocated.v1` (events that
  don't exist) and omits the actually-emitted `SalesInvoiceCreated.v1`,
  `SalesInvoiceCancelled.v1`, `SalesReceiptCreated.v1`,
  `SalesReceiptPosted.v1`, `SalesReceiptCancelled.v1`. Backend-engineer:
  replace the bullet list with a table of (event, type, payload keys,
  emitted-from method) matching the Slice B / catalog precedent. Add a
  "Synchronous callers" sub-section citing ADR-0004 (sales→stock,
  sales→cash named exemptions).

## Section 10 — Verification (QA-image smoke)

Owned by qa-engineer. Backend should expect to run the QA-image rebuild
+ wipe + smoke flow on:

- Invoice list → create draft (CASH + CREDIT) → post → void (same-day)
  → reprint each reason. Verify `domain_event` rows with the widened
  `lines[]` on `SalesInvoicePosted.v1`.
- Credit-limit gate: sales-rep blocked on breach; sales-supervisor
  (holds override perm) posts with a reason; payload carries
  `creditOverrideBy` + `creditOverrideReason`.
- Receipt list → create → post (one each method); verify `cash_entry`
  exists for CASH / MOBILE_MONEY / BANK_TRANSFER / CHEQUE and is absent
  for CARD / STORE_CREDIT (the `accountFor(method)` branch).
- Dashboard tiles render real values from
  `GET /sales/reports/ar-summary?branchId=...`; signals `openInvoices`,
  `arOutstanding`, `overdueInvoices` populated.
- Accountant persona reads AR-summary; persona without the perm gets 403.

---

## Cross-cutting (summary for backend-engineer)

- **Credit-limit override** (GAP 3.A + 1.C + 6.B + 5.B + 7.A): the
  load-bearing Slice C gap. Touches schema, service, controller, perms,
  Permissions.java, tests, outbox payload.
- **Outbox payload widening on `SalesInvoicePosted.v1`** (GAP 9.A): add
  `lines[]` now so the deferred debt module doesn't force a `.v2` later.
- **Permissions band 120-125** per the locked plan; high-water 113
  (V68). Add the missing `// ---- sales ----` constants section in
  `Permissions.java` for existing + new perms in the same change.
- **AR-summary indexes** (GAP 1.B + 4.A): add the two composite indexes
  before the repo methods ship.
- **Reprint** (GAP 3.B + 5.A + 2.B + 9.B + 9.C): enum + optional notes
  per the locked decision.

## Open questions

None. The three open questions from `slice-c-sales-plan.md §9` are
resolved per Godfrey's locked decisions:

1. Posting-engine: option (b) — separate engines, shared
   `..common.posting..` helpers. Codified in ADR-0004 §4.
2. Reprint-reason: enum + optional notes. Codified in §2.B + §9.B/C.
3. AR-summary: own permission `SALES.REPORT.AR_SUMMARY`. Codified in
   §6.B + §5.A.

Backend-engineer can start after the qa-engineer's failing
`sales.spec.ts` lands.

## What's intentionally NOT in scope (Slice C)

- `CustomerReturn`, `CustomerCreditNote`, `PackingList`,
  `SalesQuotation` — separate slices.
- Standalone `debt` module — Slice F. AR-summary tile values come from
  existing tables (Section 4 GAP 4.A); no `debt_entry` ledger required.
- US-DEBT-003 ageing report — Slice F.
- `SalesReportServiceImpl` → `pos.TillReportService` refactor
  (ADR-0004 #19) and the parallel cross-module repository reaches —
  future `reporting` module slice.
