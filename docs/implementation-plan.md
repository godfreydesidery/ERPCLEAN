# Implementation plan

End-to-end vertical slices, ordered by dependency. Each feature spans backend + web (+ POS / WMS where applicable) so that contract issues surface at integration time, not after.

## 👉 Resume here

**Last updated:** 2026-05-15 · **Branch:** `feature` · **Last commit:** `c11d285` — F5.8 backend (barcode resolver + EMBEDDED_WEIGHT add-time validation). Uncommitted: F6.1 backend slice (cash ledger + cash book + producer wiring across pos/sales/procurement).

**▶ RESUME POINT:** next slice is **F5.9 — Cash pickup + petty cash** (now unblocked — F6.1 backend ships the `CashLedgerService` port + idempotency the F5.9 events will hook into). F5.7 gift-card tender still blocked on F7.1. F6.1 backend (cash_entry + cash_book + write-through projection + idempotency on `(refType, refId, direction)` + read endpoints + producer wiring in POS sale/refund/void, TillSession open/close, SalesReceipt, SupplierPayment) is done; direct supervisor-adjustment + bank-deposit endpoints deferred to a follow-on slice (need their own audit-doc tables); cash pickup / petty cash event consumers will land with F5.9; web `/cash/ledger` + `/cash/cash-book` screens deferred. Working tree dirty until the F6.1 commit.

**Progress:** ~65% of MVP slices complete (34 of 52 — Phases 0-4 + F5.1 + F5.2 + F5.3 + F5.4 backend + F5.5 backend + F5.6 backend + F5.8 backend + F6.1 backend done; F5.7 blocked, F5.9 / F5.10 / F6.2 / F6.3 next, Phase 7 extensions, Phase 8 reporting remain).

**Done in Phase 0:**
- F0.1 — first-run setup wizard (backend + web)
- F0.2 — login + JWT (backend + web)
- F0.3 — logout + refresh tokens with theft detection (backend + web)
- F0.4 backend — RBAC wired; ADMIN role + 10 permissions seeded; JWT now carries real `perms[]`
- F0.4b — `RoleAdminService`/`RoleAdminController` (role + permission + grant CRUD, gated by `IAM.MANAGE_ROLES`); web `RoleAdminComponent` + `HasPermissionDirective`; `AuthService` now decodes `perms[]` from the JWT
- F0.5 — active-branch switching: `BranchAccessGuard` + `JwtAuthenticationFilter` 403-on-denied-override; `SessionController` (`/session/branches`, `/session/active-branch`); web branch dropdown in the app shell

**Done in Phase 1:**
- F1.1 — branch + section CRUD (admin module)
- F1.2 — currency + FX rate (admin module)
- F1.3 — catalog: item CRUD + groups (item list/get/patch/archive/activate, `ItemGroup` tree CRUD + move, web catalog screens)
- F1.4 — catalog: barcodes, UoM, VAT groups (`Uom`/`VatGroup`/`ItemBarcode` entities + services + controllers, web UoM/VAT screens + barcode panel)
- F1.5 — catalog: price lists + price-change audit (`PriceList`/`PriceListItem`/`PriceChangeLog`, close+open price flow, `ItemPriceChanged.v1`, web price-list screen + per-item price history)
- F1.6 — catalog: weighed-item + batch-tracking flags (`item.is_weighed`/`weighing_unit` + `item_barcode.barcode_type` via migration `V6`; weighed↔unit + weighed-needs-PLU/embedded-weight-barcode validation; `ItemWeighingChanged`/`ItemBatchTracking*` events; web toggles + barcode-type dropdown)
- F1.7 — party: customer, supplier, employee, sales agent (`party` + `party_address` + `party_contact` + 4 role tables via `V7`; shared-party-by-TIN reuse; per-branch walk-in customer hook; `@Pii` field marker; web screens under `/party/*`)
- F1.8 — **deferred** to Phase 8 (biometric enrolment — not an MVP launch requirement)

**Done in Phase 2:**
- F2.1 — business day open/close/override (`BusinessDay` OPEN→CLOSING→CLOSED state machine + monotonic-date / single-non-closed-day invariants; `business_day_override` audit table via `V9`; `DayGuard` synchronous port; `DAY.*` permissions via `V10`; `BusinessDayController`; web `/day` dashboard)
- F2.2 — stock ledger + balances (`StockMove` append-only + `ItemBranchBalance` cache via `V11`; moving-average cost on inbound, consume-at-average on outbound; negative-stock guard + `STOCK.OVERSELL` via `V12`; posting requires an open day via `DayGuard`; `StockMoved`/`BalanceUpdated`/`LowStockTriggered.v1`; web `/stock/balances` + `/stock/card/:itemId`)
- F2.3 — stock counts + transfers (`StockCount`/`StockCountLine` DRAFT→IN_PROGRESS→CLOSED→POSTED with variance→ADJUSTMENT moves; `StockTransfer`/`StockTransferLine` DRAFT→ISSUED→RECEIVED→CLOSED with TRANSFER_OUT/IN moves; `V13`/`V14`; `STOCK.COUNT`/`STOCK.TRANSFER`; web `/stock/counts` + `/stock/transfers`)
- F2.4 — batch tracking + FEFO consumption (`StockBatch` entity per (branch, item, batch_no) with ACTIVE/EXHAUSTED/EXPIRED/RECALLED lifecycle; `StockBatchService` exposes `createBatch` for inbound flows + `drainFefo` picker + `markExpired` + `recallBatch`; daily `StockBatchExpiryJob` flags ACTIVE rows past expiry; `EXPIRY_WRITE_OFF` move type + recall writes off remaining on-hand; `stock_move.batch_id` nullable column threads through `PostStockMoveRequestDto`; `V15` + `V16` migrations; `STOCK.BATCH` permission; F1.6 archive guard now blocks archive/disable-tracking while active batches exist; web `/stock/batches` expiring-soon + all-batches modes with recall action)
- F2.5 — stock adjustments + internal consumption (`POST /api/v1/adjustments` posts ADJUSTMENT moves with a configurable monetary threshold — above threshold or for oversells an `authorisedByUserId` must hold `STOCK.ADJUST_APPROVE`; `POST /api/v1/internal-consumption` posts INTERNAL_CONSUMPTION moves with required category + section + authoriser; new `stock_move` columns `section_id` / `consumption_category` / `authorised_by_user_id` via `V17`; new move types `INTERNAL_CONSUMPTION`/`STAFF_PURCHASE`/`EMPLOYEE_GIFT`/`RESERVED`; `V18` seeds `STOCK.ADJUST` / `STOCK.ADJUST_APPROVE` / `STOCK.INTERNAL_CONSUMPTION`; web `/stock/adjust` + `/stock/internal-consumption` forms)

**Done in Phase 3:**
- F3.1 — LPO lifecycle (`LpoOrder` + `LpoOrderLine` entities; DRAFT → PENDING_APPROVAL → APPROVED state machine + DRAFT/PENDING → CANCELLED; submit auto-approves when total ≤ `orbix.procurement.lpo-auto-approval-threshold`; line totals = `ordered_qty × unit_price × (1 − discount_pct/100)`; header tax rolls up from `vat_group.rate` snapshot per line; `LpoOrderCreated/Submitted/Approved/Cancelled.v1` events; `V19` + `V19_1` + `V20` migrations; `PROCUREMENT.MANAGE_LPO` / `PROCUREMENT.APPROVE_LPO` permissions; web `/procurement/lpos` list + draft creation + state-aware action buttons. PDF rendering + email subscriber on `LpoOrderApproved.v1` deferred — the event already fires.)
- F3.2 — GRN posting + batch capture (`Grn` + `GrnLine` entities with DRAFT → POSTED terminal lifecycle + DRAFT → CANCELLED; LPO-bound flow validates each line against the parent `lpo_order_line` (item match + outstanding-qty over-receipt guard) and on post advances `received_qty` + flips LPO to PARTIALLY_RECEIVED or RECEIVED; direct GRN gated by `GRN.DIRECT`; posting routes through `StockMoveService` so DayGuard + moving-average + F2.4 batch creation are all reused — batch-tracked items create a `stock_batch` first and stamp `batch_id` on the move; `V21` + `V21_1` + `V22` migrations; `GRN.POST`/`GRN.DIRECT` permissions; `GrnCreated/Posted/Cancelled.v1` events; web `/procurement/grns` list + receive-against-LPO form with per-line outstanding + batch_no inputs + state-aware Post/Cancel.)
- F3.3 — Supplier invoice + 3-way match (`SupplierInvoice` + `SupplierInvoiceGrn` (composite-PK junction) entities with DRAFT → POSTED lifecycle; DRAFT/POSTED → CANCELLED; per-branch unique invoice number + per-supplier unique `supplier_invoice_no`; allocations validated against each referenced GRN — POSTED + same supplier + cumulative amount ≤ `grn.total_amount` (over-allocation rejected; cancelled invoices excluded); Σ allocations must match `invoice.total_amount` within `orbix.procurement.invoice-match-tolerance-pct` (defaults 0.005); due date defaults to `invoice_date + supplier.payment_terms_days`; `V23` + `V23_1` + `V24` migrations; `PROCUREMENT.MANAGE_INVOICE` permission; `SupplierInvoiceCreated/Matched/Cancelled.v1` events; web `/procurement/invoices` list + per-supplier GRN picker + live tolerance hint + state-aware Post/Cancel.)
- F3.4 — Supplier payment + invoice settlement (first floor of the cash module; `SupplierPayment` + `SupplierPaymentAllocation` entities with DRAFT → POSTED → terminal lifecycle + DRAFT → CANCELLED; per-branch unique payment number; `PaymentMethod` enum (CASH/BANK_TRANSFER/CHEQUE/MOBILE_MONEY); allocation guards — same-supplier + in-company + amount ≤ invoice outstanding + no-duplicate-invoice + Σ allocations ≤ payment total; posting requires `DayGuard.requireOpenDay`; `SupplierInvoice.applyPayment` advances `paid_amount` and flips to PARTIALLY_PAID until total → PAID; `V25` + `V25_1` + `V26` migrations seed `CASH.MANAGE_SUPPLIER_PAYMENT`; `SupplierPaymentCreated/Posted/Cancelled.v1` events for the F6.1 cash-side subscriber; web `/procurement/payments` list + supplier-scoped open-invoice picker + outstanding hint + state-aware Post/Cancel.)

**Done in Phase 4:**
- F4.1 — Sales quotation (**skipped** per plan: "Skip if pilot doesn't need quotations; jump to F4.2").
- F4.2 — Sales invoice posting (`SalesInvoice` + `SalesInvoiceLine` (DATA-MODEL §6.3/§6.4); DRAFT → POSTED → (PARTIALLY_PAID/PAID via F4.3) | VOIDED | CANCELLED; per-branch unique number; `PaymentTerms` (CASH/CREDIT); business rules — credit-limit check against `customer.creditLimitAmount` + running outstanding debt; discount-threshold via `orbix.sales.discount-threshold-pct` requires `SALES.DISCOUNT_APPROVE` authoriser; `item.minSellPrice` enforced; same-business-day-only void writes RETURN_IN compensating moves at snapped line cost — batch-tracked items rejected (use F4.4 return instead); posting routes through `StockMoveService` with DayGuard + per-batch FEFO drains for batch-tracked items; `V27` + `V27_1` + `V28` migrations seed `SALES.MANAGE_INVOICE/DISCOUNT_APPROVE/MANAGE_RECEIPT/MANAGE_RETURN/MANAGE_PACKING`; `SalesInvoiceCreated/Posted/Voided/Cancelled.v1` events; web `/sales/invoices` list + multi-line draft form + state-aware Post/Void/Cancel.)
- F4.3 — Sales receipt + allocation (`SalesReceipt` + `ReceiptAllocation` (DATA-MODEL §6.5/§6.6); DRAFT → POSTED → terminal + DRAFT → CANCELLED; `ReceiptMethod` (CASH/CARD/BANK_TRANSFER/MOBILE_MONEY/CHEQUE/STORE_CREDIT); allocation guards mirror F3.4 — same-customer, no-duplicate, amount ≤ outstanding, Σ ≤ total; tracks `unallocated_amount` for future customer-credit routing; DayGuard on post; `SalesInvoice.applyReceipt` flips PARTIALLY_PAID / PAID; `V29` + `V29_1`; `SalesReceiptCreated/Posted/Cancelled.v1` events; web `/sales/receipts` with customer-scoped open-invoice picker.)
- F4.4 — Customer returns + credit notes (`CustomerReturn` + `CustomerReturnLine` (DATA-MODEL §6.7/§6.8); DRAFT → POSTED → CREDITED + DRAFT → CANCELLED; `ReturnReason` (DAMAGED/EXPIRED/WRONG_ITEM/BUYER_REMORSE/OTHER); on post (DayGuard required) writes RETURN_IN moves when `restock=true`, DAMAGE moves otherwise — batch-tracked items rejected for now (restock-to-original-batch is a follow-on); issue-credit-note transitions POSTED → CREDITED and creates `CustomerCreditNote` (DATA-MODEL §6.9) at full return amount — allocation to open invoices is follow-on work; `V30` + `V30_1`; `CustomerReturnCreated/Posted/Cancelled.v1` + `CustomerCreditNoteIssued.v1` events; web `/sales/returns` with state-aware action buttons.)
- F4.5 — Packing lists (`PackingList` + `PackingListLine` (DATA-MODEL §6.10/§6.11); DRAFT → DISPATCHED → DELIVERED → terminal + DRAFT → CANCELLED; created against POSTED/PARTIALLY_PAID/PAID invoices; tracking-only — no stock moves (parent invoice already decremented on post); `V31` + `V31_1`; `PackingListCreated/Dispatched/Delivered/Cancelled.v1` events; web `/sales/packing-lists` with per-invoice-line tick + qty picker.)

**Done in Phase 5:**
- F5.1 — Till + till-session lifecycle (`Till` + `TillSession` entities; DATA-MODEL §7.1/§7.2; OPEN → CLOSED → RECONCILED with at-most-one-OPEN-per-till invariant; opening requires `DayGuard.requireOpenDay`; close computes `expected_cash = opening_float` + variance; variance above `orbix.pos.session-variance-threshold` (default 1000) needs a `supervisorId` holding `POS.SESSION_VARIANCE_APPROVE`; `V32` + `V32_1` + `V33` migrations seed 5 POS permissions; `TillCreated/Activated/Deactivated.v1` + `TillSessionOpened/Closed/Reconciled.v1` events; web `/admin/tills` admin screen. Flutter cashier UI deferred.)
- F5.2 — Basic POS sale (cash + mixed tender) — backend ships the full server-side contract; Flutter till app deferred. `PosSale` + `PosSaleLine` + `PosPayment` entities (DATA-MODEL §7.3-§7.5 + Phase 1.1 §17.12 additions); POS sales committed locally and pushed as POSTED (no DRAFT); idempotent on `clientOpId` per company. Validation: OPEN till session, `section_id` matches the till's branch (required), customer exists, tender ≥ total. Posting writes outbound SALE stock moves via `StockMoveService` (DayGuard inherited) — batch-tracked items drain FEFO via `StockBatchService` and emit one stock_move per pick. Mixed tender (cash + card + mobile money + voucher + store credit) via N `pos_payment` rows; card terminals record `terminal_id` + `last4` only. `V34` + `V34_1` + `V35` migrations seed `POS.SALE_POST`. Event: `PosSaleClosed.v1`. Web: read-only `/admin/pos-sales` viewer for managers.
- F5.3 — POS discounts, header discount, void path (per-line discount above `orbix.pos.discount-threshold-pct` requires a `discountApproverId` holding `POS.DISCOUNT_APPROVE`, must differ from caller; optional `headerDiscountAmount` applied after line tax, rejected when negative or > subtotal; same-business-day `POST /pos-sales/{id}/void` writes RETURN_IN compensating moves at the snapped line cost and rejects batch-tracked lines; `V36` seeds `POS.SALE_VOID` + `POS.DISCOUNT_APPROVE`; `PosSaleVoided.v1`; web `/admin/pos-sales` gains the Void button + header-discount display.)
- F5.4 — Offline-sync server contract (backend-complete; Flutter app deferred). `POST /api/v1/sync/push` batch-pushes locally-committed POS sales — each item runs in its own `PosSaleService.post` transaction so partial failures don't drop the batch; idempotency on `clientOpId` was already in place from F5.2. `GET /api/v1/sync/catalog/snapshot?branchId=&priceListId=` returns active items + vat rate + weighed/batch flags + min sell price + current price-list price + per-branch on-hand qty + all barcodes (so the till's local DB can scan EAN/PLU offline). `GET /api/v1/sync/balances/snapshot?branchId=` returns current `item_branch_balance` rows for a soft pre-flight oversell check. `V37` seeds `POS.SYNC`.
- F5.5 — Refund at till (backend). Same-business-day refund flow rejecting batch-tracked items and writing RETURN_IN compensating stock moves at the original snapped line cost; `orbix.pos.refund-threshold` (default 10000) requires a `supervisorId` holding `POS.REFUND_APPROVE` (different from the cashier); `PosSaleKind.REFUND` rows reuse `pos_sale` / `pos_sale_line` / `pos_payment` with `refunded_from_sale_id` linking back to the original; `V38` seeds `POS.REFUND_POST` + `POS.REFUND_APPROVE`; `PosSaleRefunded.v1` event.
- F5.6 — FX tender at till (backend). `pos_payment` can now snap a non-functional `tender_currency` + `fx_rate` per row (functional-currency `amount` is still the audit value used for totals/variance); till must accept the currency via `till_currency` (managed under `POS.TILL_CURRENCY_MANAGE` at `/api/v1/tills/{id}/currencies`); FX rate resolved most-recent-on-or-before the sale `at` from `fx_rate`; `V39` adds the `till_currency` table + the `pos_payment.tender_currency`/`tender_amount`/`fx_rate` columns + seeds `POS.TILL_CURRENCY_MANAGE`. Flutter tender-screen UI deferred.
- F5.8 — Weighed items + barcode parser (backend; Flutter scale integration deferred). `BarcodeResolverService` + `GET /api/v1/pos/barcode-lookup?code=…` resolve a scanned code → `ResolvedBarcodeDto { itemId, code, name, uomId, vatGroupId, weighed, batchTracked, weighingUnit, minSellPrice, qty, barcodeType }`. Plain symbologies (UPC/EAN13/EAN8/PLU) match `item_barcode.barcode` exactly with `qty = packQty`; scale-printed EAN-13 (13 digits, leading `2`) falls back to a 7-char prefix lookup against `EMBEDDED_WEIGHT` barcodes and decodes weight bytes 8..12 as `int / 1000` in the item's `WeighingUnit` (per the README §11 layout `2 + 6-digit PLU + 5-digit weight + check digit`). Archived / cross-company items rejected; zero-weight scans rejected. `ItemBarcodeServiceImpl.addBarcode` now shape-checks each symbology (EAN13=13 digits, EAN8=8, UPC=12) and enforces EMBEDDED_WEIGHT = 7 digits leading with `2` against a weighed item.

**Done in Phase 6:**
- F6.1 — Cash entries + cash book (backend; web `/cash/ledger` + `/cash/cash-book` screens deferred). `cash_entry` append-only ledger + `cash_book` write-through projection (per-branch / per-account / per-business-date opening + in + out + closing); idempotency UNIQUE on `(ref_type, ref_id, direction)` so a replayed producer call is a no-op; CashLedgerService is the posting port — all source modules call it in the same transaction so a rolled-back source doc rolls back the cash entry too; `V40` schema + `V40_1` sequence (per dialect) + `V41` seeds `CASH.READ` / `CASH.ADJUST` / `CASH.BANKING`; `CashEntryPosted.v1` + `CashBookBalanceUpdated.v1` events. Producer wiring landed in this slice: POS sale closes write IN-TILL per CASH `pos_payment` (`ref_type = PosSalePayment`); POS refunds write OUT-TILL per CASH refund payment (`PosRefundPayment`, `gl_category = CASH_REFUND`); same-day voids reverse the original CASH rows (`PosVoidPayment`); TillSession open writes the opening-float IN-TILL (`TillFloat` / `TILL_FLOAT`); TillSession close writes a variance entry on non-zero variance (surplus = IN, shortage = OUT, `TillVariance` / `VARIANCE`); SalesReceipt posts an IN entry on the method-mapped account (`CASH_BOX` / `BANK` / `MOBILE_MONEY`; CARD + STORE_CREDIT settle off-ledger); SupplierPayment posts an OUT entry on the method-mapped account. Read API at `GET /api/v1/cash-entries` + `GET /api/v1/cash-book` (gated by `CASH.READ`). Direct supervisor-adjustment + bank-deposit endpoints + cash pickup / petty cash consumers + multi-currency book deferred to follow-on slices.

**Next slice (start here):** **F5.9** — Cash pickup + petty cash (now unblocked by F6.1). Then F5.10 (X/Z reports), F6.2 (multi-currency cash book), F6.3 (end-of-day banking + supervisor adjustment). F5.7 still blocked on F7.1; F3.5 (vendor return) deferred to Phase 8; F4.1 (quotation) skipped; Flutter POS deferred. Phase-0 test debt still outstanding.

**Pending across all of Phase 0 (tests + docs):**
- Unit + integration tests for F0.1 / F0.2 / F0.3 — none authored yet. F0.4 has `RoleAdminServiceImplTest`; F0.5 has `BranchAccessGuardTest`. Integration/system layers still pending. See [docs/qa/](qa/).
- POS (Flutter) login wiring — deferred until F5.1 (till session work).
- "Logout everywhere" button — backend endpoint exists; UI button still missing.

**How to validate before continuing:** `cd orbix-engine-api && mvn -q clean compile` + `cd ../orbix-engine-web && npx ng build --configuration development`. Both should be silent (zero output = success in `-q` mode).



## How to use this plan

- Pick the next un-blocked feature. Don't jump ahead — dependencies are real.
- A feature is **done** when every checkbox under it ticks AND tests are green AND the DoD is met.
- One feature at a time. Resist parallelising features that touch the same module — merge conflicts swamp the speed gain.
- Cross-references: `docs/qa/modules/*.md` for the per-module test catalogue; `docs/qa/e2e-scenarios.md` for cross-module flows; `docs/design/PHASE-1.1-ADDITIONS.md` for the Phase 1.1 scope spec.

## Conventions

### Sizing
- **S** = 1-2 days
- **M** = 3-5 days
- **L** = 1-2 weeks
- **XL** = 3+ weeks (must be split before starting)

### Test layers per feature

| Layer | Tool | Scope |
|---|---|---|
| **Unit** | JUnit 5 + Mockito (backend) · Karma+Jasmine (Angular) · `flutter test` (Dart) | One class / function. Mocks for collaborators. Runs in seconds. |
| **Integration** | Spring Boot Test + Testcontainers MySQL & Postgres · `flutter integration_test` | One slice of the system with real DB / network. Runs in minutes. |
| **System (E2E)** | RestAssured + Testcontainers full stack · Playwright (web) · Flutter integration_test on emulator (POS / WMS) | One business flow end-to-end as a user would do it. Runs in tens of minutes; CI nightly. |
| **Contract** | Spring Cloud Contract (REST) · Pact (events) | Pinned event payload shapes and REST schemas. Runs in CI per commit. |

### Definition of Done (every feature)

- All backend tasks checked + ArchUnit boundary tests green.
- All web (and POS) tasks checked + lints clean.
- Unit + integration tests green; coverage ≥ 70% line for the new code.
- The specified system test (`TC-E2E-XXX`) passes.
- Domain events emitted are visible in `domain_event` and consumed idempotently.
- Audit log row exists for every state change.
- Migration applies cleanly on **both** MySQL and Postgres profiles.
- README / API docs updated if the public surface changed.

### Status legend

- `[ ]` — not started
- `[~]` — in progress
- `[x]` — done

Update the plan as you go; commit the change alongside the feature.

---

# Phase 0 — Foundation

**Goal:** Bootstrap an empty deployment and log in. Proves the architectural stack works end-to-end before we add real features.

**Modules touched:** `auth`, `admin`, `common`.

## F0.1 — First-run setup wizard

**Story:** US-COMP-001 · **Size:** M · **Status:** `[x]` (commit `a96a206` backend, `a2fdc13` web)
**Dependencies:** none.

**Backend:**
- [x] `AppUser`, `AppUserRepository`, `AuthService` + Impl (already in place).
- [x] `DevSeed` CommandLineRunner for `@Profile("local")`.
- [x] `organisation`, `company`, `branch`, `section` JPA entities under `modules/admin/domain/entity/`.
- [x] `OrganisationRepository`, `CompanyRepository`, `BranchRepository`, `SectionRepository`.
- [x] `FirstRunSetupService` + Impl + DTOs (`FirstRunRequestDto`, `FirstRunResponseDto`).
- [x] `SetupController` exposing `POST /api/v1/setup/first-run` + `GET /status`. Public endpoint.
- [x] Flyway `V3__admin_section_currency_fx.sql` adds `section`, `currency`, `fx_rate` (`till_currency` deferred to F5.1).
- [ ] Outbox events: `OrganisationCreated.v1`, `CompanyCreated.v1`, `BranchCreated.v1`, `SectionCreated.v1` — pending until `EventPublisher` is wired in the bootstrap path.

**Web:**
- [x] `/setup` route with a 4-step wizard component (org → company → first branch → admin).
- [x] `SetupService` calls `POST /api/v1/setup/first-run` + `GET /status`.
- [x] Auto-redirect to `/login` on success, with username pre-filled via router state.

**Tests:**
- **Unit (backend):** `FirstRunSetupServiceTest` — happy path; rejects when org already exists; idempotent on company code.
- **Integration:** `FirstRunIntegrationTest` (Testcontainers MySQL + Postgres) — POST `/first-run`, assert rows + events.
- **System (E2E):** `TC-E2E-001` (modified to start from empty DB) + `TC-ADMIN-001` / `TC-ADMIN-002` / `TC-ADMIN-003`.

**DoD:** A developer with an empty DB hits `/setup` in the browser, fills the form, and is redirected to `/login`. The seeded admin can log in.

---

## F0.2 — Login + JWT (cookie / Bearer)

**Story:** US-IAM-001 · **Size:** S · **Status:** `[x]` (commit `bd1617f` web wiring + ApiResponse envelope)
**Dependencies:** F0.1.

**Backend:**
- [x] `AuthServiceImpl.login` with BCrypt verify, 5-strike / 15-min lockout.
- [x] `AuthController` POST `/api/v1/auth/login`.
- [x] JWT carries `uid` / `cid` / `bid` / `perms[]` (real perms wired in F0.4).

**Web:**
- [x] `LoginComponent` posting username + password.
- [x] `AuthInterceptor` attaches `Authorization: Bearer <jwt>` to all subsequent calls.
- [x] `AuthGuard` redirects unauthenticated users to `/login`.
- [x] Token stored in `sessionStorage`.

**POS (Flutter):**
- [ ] `LoginScreen` (existing stub) wires to backend.
- [ ] `AuthRepository` with secure-storage backed JWT.
- [ ] Biometric login (post-MVP — gated by `tracks_biometrics` feature flag).

**Tests:**
- **Unit (backend):** `AuthServiceImplTest` — happy path, wrong password, locked account, lockout reset.
- **Integration:** `AuthIntegrationTest` (Testcontainers) — full /login → use token → /protected endpoint.
- **Unit (web):** `AuthInterceptorSpec`, `AuthGuardSpec`.
- **System (E2E):** `TC-AUTH-001` .. `TC-AUTH-013`; cross-module `TC-E2E-022` (lockout).

**DoD:** Web user logs in, sees the dashboard, JWT visible in Network tab as Bearer header. POS user logs in, JWT cached, used on next sync.

---

## F0.3 — Logout + refresh tokens

**Story:** US-IAM-002, US-IAM-003 · **Size:** M · **Status:** `[x]` (commit `9fcc325`)
**Dependencies:** F0.2.

**Backend:**
- [x] `RefreshToken` entity + repository (SHA-256 hashed at rest; `@ToString` excludes the hash).
- [x] `AuthServiceImpl.refresh()` — single-use rotation, theft detection revokes all user tokens.
- [x] `AuthServiceImpl.logout()` — revokes current refresh token.
- [x] `AuthController` POST `/refresh`, POST `/logout`, POST `/logout-everywhere`.

**Web:**
- [x] `AuthInterceptor` catches 401, hits `/refresh`, retries the original request (with in-flight latch).
- [x] `AuthService.logout()` calls backend then clears storage + redirects to `/login`.
- [ ] "Logout everywhere" button — backend endpoint exists, UI control missing.

**Tests:**
- **Unit:** `AuthServiceImplTest` covers rotation + theft detection.
- **Integration:** `RefreshTokenIntegrationTest` — issue, rotate, re-use rejected, theft revokes all.
- **System:** `TC-NFR-SEC-005` (refresh tokens single-use).

**DoD:** A 15-minute-old access token transparently refreshes; logout invalidates the token everywhere.

---

## F0.4 — RBAC wiring (permissions in JWT)

**Story:** US-IAM-008, US-IAM-009 · **Size:** M · **Status:** `[x]` (backend `b4b013a`; F0.4b admin UI + endpoints follow-up)
**Dependencies:** F0.2.

**Backend:**
- [x] `Role`, `Permission`, `UserRole` entities + repos (role_permission via `@ManyToMany` join table).
- [x] `RoleAdminService` + Impl + `RoleAdminController` — role CRUD, permission assignment, grant/revoke endpoints, all gated by `@PreAuthorize("hasAuthority('IAM.MANAGE_ROLES')")`. System roles are mutation-locked.
- [x] `AuthServiceImpl.login()` + `refresh()` load permissions via `PermissionResolverService.resolve(userId, companyId, branchId)`.
- [x] JWT `perms[]` populated from resolver instead of `List.of()`.
- [x] Flyway `V4` seeds 10 permissions + ADMIN role + grants all-to-ADMIN. Bootstrap (FirstRunSetupService + DevSeed) assigns ADMIN role to the admin user.

**Web:**
- [x] `RoleAdminComponent` (`/admin/roles`) — create roles, edit details, assign permissions (grouped by module), grant/revoke to users.
- [x] `HasPermissionDirective` (`*orbixHasPermission="'ITEM.CREATE'"`) hides UI per permission; `AuthService` decodes the JWT `perms[]` claim into a signal.

**Tests:**
- **Unit:** `RoleAdminServiceImplTest`, `AuthServiceImplTest` extended to load permissions.
- **Integration:** `RbacIntegrationTest` — assign role, login, verify JWT carries permissions, blocked user gets 403.
- **System:** `TC-AUTH-014`, `TC-AUTH-015`, `TC-NFR-SEC-007`.

**DoD:** A user without `ITEM.CREATE` cannot POST items; an admin with the permission can. UI elements hide accordingly.

---

## F0.5 — Active branch context switching

**Story:** US-COMP-005 · **Size:** S · **Status:** `[x]`
**Dependencies:** F0.4.

**Backend:**
- [x] `JwtAuthenticationFilter` reads `X-Branch-Id` to override JWT's `bid`; when the override differs from the token branch it runs `BranchAccessGuard` and sends 403 if denied.
- [x] `BranchAccessGuard` verifies the user holds an active `user_role` grant covering that branch (branch-specific or company-wide); throws `AccessDeniedException` otherwise.
- [x] `SessionService` + `SessionController` — `GET /api/v1/session/branches` (branches the caller can switch into) and `PUT /api/v1/session/active-branch` (persists `default_branch_id` on `app_user`, access-guarded).

**Web:**
- [x] Branch dropdown in the app shell (shown when the user has >1 accessible branch). On change it calls `PUT /session/active-branch`, persists to `localStorage` (`orbix.activeBranchId`, already read by `AuthInterceptor` as `X-Branch-Id`), then reloads. `AuthService` clears the key on login/logout.

**Tests:**
- **Unit:** `BranchAccessGuardTest` — done (allow / deny / incomplete-context).
- **Integration:** Switching branch then making a request — verify `RequestContext.branchId` matches. *(pending — Phase-0 test debt)*
- **System:** `TC-ADMIN-018`, `TC-ADMIN-019`. *(pending)*

**DoD:** A multi-branch user switches in the dropdown and reads / writes are scoped to the new branch.

---

# Phase 1 — Master data

**Goal:** Every entity that transactional modules reference must exist before transactions can post.

## F1.1 — Branch + section CRUD (admin module)

**Story:** US-COMP-004, US-ADMIN-001, US-ADMIN-002 · **Size:** M · **Status:** `[x]`
**Dependencies:** F0.4.

**Backend:**
- [x] `BranchService` + Impl + `BranchController` — list / get / `POST` / `PATCH` / `POST /{id}/deactivate`, gated by `ADMIN.MANAGE_BRANCHES`. Emits `BranchCreated/Updated/Deactivated.v1`.
- [x] `SectionService` + Impl + `SectionController` — `GET|POST /api/v1/branches/{id}/sections`, `PATCH /sections/{id}`, `POST /sections/{id}/deactivate`, gated by `ADMIN.MANAGE_SECTIONS`. Emits `SectionCreated/Updated/Deactivated.v1`.
- [x] On branch create: auto-create default `MAIN` RETAIL_FLOOR section. *(Per-branch number sequences deferred — `number_sequence` is a `common`-module concern, not yet built; walk-in customer deferred to F1.3 per plan.)*
- [x] Invariants: can't deactivate the last active RETAIL_FLOOR section in a branch; can't deactivate an already-inactive branch/section. *(Open-till / active-BOM checks are TODO markers — those entities land in F5.1 / F7.3.)*

**Web:**
- [x] `BranchAdminComponent` under `/admin/branches` — branch list + create + edit + deactivate, and per-branch section list with create / edit / deactivate. (Single cohesive component, matching the `RoleAdminComponent` precedent rather than the 3-component split originally sketched.)

**Tests:**
- **Unit:** `BranchServiceImplTest` (6), `SectionServiceImplTest` (9) — done.
- **Integration:** Branch deactivate with open till → 422. *(pending — needs the Till entity)*
- **System:** `TC-ADMIN-004` .. `TC-ADMIN-012`. *(pending)*

**DoD:** Admin creates a second branch, adds a Bakery section, sees both in the dropdown. *(Branch dropdown wired in F0.5; the new branch appears once the user has a grant covering it.)*

---

## F1.2 — Currency + FX rate

**Story:** US-ADMIN-003, US-ADMIN-004, US-ADMIN-006 · **Size:** S · **Status:** `[x]`
**Dependencies:** F1.1.

**Backend:**
- [x] `Currency`, `FxRate` entities + repos (pre-existing from V3). *(`TillCurrency` deferred to F5.1 — needs the Till entity.)*
- [x] `CurrencyService` + Impl, `FxRateService` + Impl. FX `effectiveRate(from,to,at)` does the "most recent ≤ time" lookup; `quoteRate` is append-only and rejects same-currency / non-positive / unknown-currency quotes.
- [x] Controllers: `CurrencyController` (`GET`, `POST`, `POST /{code}/enable|disable`, gated `ADMIN.MANAGE_CURRENCIES`); `FxRateController` (`GET`, `POST`, `GET /effective`, gated `ADMIN.MANAGE_FX`). *(`PUT /api/v1/tills/{id}/currencies` deferred to F5.1.)*

**Web:**
- [x] `/admin/currencies` — currency table with enable/disable + add-currency form.
- [x] `/admin/fx-rates` — quote form + rate-history table.

**Tests:**
- **Unit:** `FxRateServiceImplTest` (7) — done (most-recent lookup, same-currency, rate ≤ 0, unknown currency).
- **Integration:** Quote rate, look up at various timestamps. *(pending)*
- **System:** `TC-ADMIN-013` .. `TC-ADMIN-016`. *(pending)*

**DoD:** Admin enables USD, quotes today's rate. *(Till-accepts-USD half deferred with `TillCurrency` to F5.1.)*

---

## F1.3 — Catalog: item CRUD + groups

**Story:** US-CAT-001, US-CAT-002, US-CAT-004 · **Size:** M · **Status:** `[x]`
**Dependencies:** F0.4.

**Backend:**
- [x] `Item` entity + `ItemRepository` + `ItemServiceImpl.create()` + `ItemController` (POST).
- [x] `GET /api/v1/items` (paged, optional `status` filter, `PageDto<T>` envelope), `GET /{id}`, `PATCH /{id}`, `POST /{id}/archive`, `POST /{id}/activate`. Emits `ItemUpdated/Archived/Activated.v1`.
- [x] `ItemGroup` entity + repository + `ItemGroupService` tree-CRUD — `GET`, `POST /api/v1/item-groups`, `PATCH /{id}` (rename), `POST /{id}/move` (re-parents subtree, recomputes `level`, rejects cycles), `POST /{id}/archive`. Migration `V4_2` adds `item_group_seq` (mysql + postgres).
- [x] Soft-delete via `status = ARCHIVED`; archive idempotency guarded. *(Active-promotion / active-batch blocks are TODO — those entities land in F1.5 / F2.4.)*
- [ ] Meilisearch indexer that subscribes to `ItemCreated.v1` / `ItemUpdated.v1` / `BarcodeAdded.v1` — **deferred**: `platform.search` owns the indexer; catalog only emits the events (already does).

**Web:**
- [x] `/catalog/items` list + status filter + paged grid (`ItemListComponent`).
- [x] `/catalog/items/new` and `/catalog/items/:id/edit` (`ItemEditComponent`). *(UoM / VAT-group are raw id inputs pending F1.4 list endpoints.)*
- [x] `/catalog/groups` tree view + move-under-parent (`ItemGroupComponent`). *(Move via parent dropdown rather than drag-and-drop.)*

**Tests:**
- **Unit:** `ItemServiceImplTest` (10), `ItemGroupServiceImplTest` (8) — create / edit / archive / activate / list / move / cycle rejection.
- **Integration:** Create item, search Meilisearch within 1s. *(pending — needs the search module)*
- **System:** `TC-CAT-001` .. `TC-CAT-008`. *(pending)*

**DoD:** Admin creates an item via the web form, sees it in the list, archives it, filters it back. Item-group tree is editable.

---

## F1.4 — Catalog: barcodes, UoM, VAT groups

**Story:** US-CAT-003, US-COMP-006, US-COMP-007 · **Size:** M · **Status:** `[x]`
**Dependencies:** F1.3.

**Backend:**
- [x] `Uom`, `VatGroup`, `ItemBarcode` entities + repos. Migration `V4_3` adds `uom_seq` / `vat_group_seq` / `item_barcode_seq` (mysql + postgres).
- [x] CRUD endpoints — `UomController` (`/api/v1/uoms`), `VatGroupController` (`/api/v1/vat-groups`, + archive), `ItemBarcodeController` (`/api/v1/items/{id}/barcodes`, `DELETE /barcodes/{id}`). Gated by the `ITEM.*` catalog permissions.
- [x] Barcode globally unique (matches the `uk_item_barcode` DDL); VAT group enforces single company default; `BarcodeAdded.v1` emitted.
- [ ] `UomConversion` — **deferred**: no `uom_conversion` table yet (needs its own migration) and no UI in F1.4 scope; revisit alongside pack-conversion work.
- [ ] Weighed-item requires EMBEDDED_WEIGHT / PLU barcode — **deferred to F1.6**: `item.is_weighed` and `item_barcode.barcode_type` are Phase-1.1 columns not yet added.

**Web:**
- [x] `BarcodesPanelComponent` embedded in the item edit screen (add / remove rows).
- [x] `/catalog/uoms` and `/catalog/vat-groups` screens. *(Placed under `/catalog` rather than `/admin` — the backend is the catalog module; linked from the catalog landing page.)*

**Tests:**
- **Unit:** `UomServiceImplTest` (4), `VatGroupServiceImplTest` (7), `ItemBarcodeServiceImplTest` (5) — code uniqueness, single-default, barcode uniqueness, company scoping.
- **Integration:** Create item with 3 barcodes, scan each. *(pending)*
- **System:** `TC-CAT-010` .. `TC-CAT-015`. *(pending)*

**DoD:** UoM + VAT registries are editable; an item can have multiple barcodes added/removed. *(Weighed-item barcode rule lands with F1.6; UoM conversions deferred.)*

---

## F1.5 — Catalog: price lists + price-change audit

**Story:** US-CAT-007, US-CAT-008, US-CAT-014 · **Size:** M · **Status:** `[x]`
**Dependencies:** F1.3, F1.4.

**Backend:**
- [x] `PriceList`, `PriceListItem`, `PriceChangeLog` entities + repos. Migration `V5` creates the three tables; `V5_1` seeds their sequences (mysql + postgres).
- [x] `PriceListService` + Impl + `PriceListController` (`/api/v1/price-lists` CRUD + archive, `GET|PUT /{id}/items`, `GET /items/{id}/price-changes`). `setPrice` **closes** the prior open `price_list_item` row (`valid_to = effective_from − 1`) and **appends** to `price_change_log`; rejects an effective date not after the current row's start. Single company-default invariant. Gated by `ITEM.*`.
- [x] `ItemPriceChanged.v1` emitted on every price set.

**Web:**
- [x] `/catalog/price-lists` — price-list list + create/edit/archive, and a per-list current-price grid with a "set price" form (close+open). *(Form-driven rather than a CSV-style bulk grid.)*
- [x] Price-change-log read-only view per item — `PriceHistoryPanelComponent` embedded in the item edit screen.

**Tests:**
- **Unit:** `PriceListServiceImplTest` (10) — close+open flow, null old-price on first set, effective-date guard, single-default, company scoping.
- **Integration:** Multiple price changes — query log shows full history. *(pending)*
- **System:** `TC-CAT-016` .. `TC-CAT-019`. *(pending)*

**DoD:** Buyer sets a price on a list; the prior row closes and the change is logged; per-item price history shows the trail.

---

## F1.6 — Catalog: weighed-item + batch-tracking flags

**Story:** US-CAT-015, US-CAT-017 · **Size:** S · **Status:** `[x]`
**Dependencies:** F1.3.

**Backend:**
- [x] Migration `V6` adds `item.is_weighed` + `item.weighing_unit` and `item_barcode.barcode_type`. *(`item.is_batch_tracked` already existed from the V2 baseline — it is now mapped on the entity as `batchTracked`; the plan's `tracks_batches` name resolves to that column.)*
- [x] Validation in `ItemServiceImpl.updateItem`: `weighing_unit` set iff `weighed`; a weighed item needs ≥1 PLU / EMBEDDED_WEIGHT barcode. Emits `ItemWeighingChanged.v1`, `ItemBatchTrackingEnabled/Disabled.v1`.
- [x] Un-set `batch_tracked` / archive blocked when active batches exist (F2.4 wired the `StockBatchRepository` lookup; covered by `ItemServiceImplTest`).

**Web:**
- [x] Weighed + batch-tracked toggles and a weighing-unit dropdown on the item edit form; barcode-type dropdown in the barcodes panel.

**Tests:**
- **Unit:** `ItemServiceImplTest` extended (15) — weighed↔unit rule, weighed-needs-capable-barcode, weighing + batch-tracking events.
- **System:** `TC-CAT-025`, `TC-CAT-026`, `TC-CAT-027`. *(pending)*

**DoD:** A "Bananas — KG" item with a PLU barcode can be flagged `weighed = true`, `weighing_unit = KG`; flagging it weighed without such a barcode is rejected.

---

## F1.7 — Party: customer, supplier, employee, sales agent

**Story:** US-PROC-001, US-SALES-001, US-SALES-002, US-HR-001, US-HR-002 · **Size:** L · **Status:** `[x]`
**Dependencies:** F0.4.

**Backend:**
- [x] `Party`, `PartyAddress`, `PartyContact`, `Customer`, `Supplier`, `Employee`, `SalesAgent` entities (migration `V7` + `V7_1` sequences). Role tables share the party PK. `V8` seeds the 4 `PARTY.MANAGE_*` permissions.
- [x] Repos + a service per role (`CustomerService` / `SupplierService` / `EmployeeService` / `SalesAgentService`) over a shared `PartyService`; controllers gated by `PARTY.MANAGE_*`.
- [x] Shared-party logic: `PartyService.resolveOrCreate` reuses an existing company party with the same TIN rather than inserting a new row; a `GET /api/v1/parties/by-tin` lookup backs the web hint.
- [x] Walk-in `customer` per branch — `BranchServiceImpl.createBranch` now calls `CustomerService.createWalkInCustomer` (idempotent).
- [~] PII tagging — `@Pii` field marker added on party PII fields; the audit-log / event scrubbing that consumes it is **deferred** (touches shared `AuditAspect` / `EventPublisher` infra).
- [ ] `PartyAddress` / `PartyContact` CRUD endpoints — entities + tables exist; per-address/contact endpoints **deferred** (core party carries inline phone/email/address).

**Web:**
- [x] `/party/customers`, `/party/suppliers`, `/party/employees`, `/party/agents` list + create screens, sharing a `PartyDetailsFormComponent`.
- [x] On create-customer / create-supplier with an existing TIN: an inline hint shows the matching party and that the role will attach to it (form-driven rather than a modal).

**Tests:**
- **Unit:** `PartyServiceImplTest` (6), `CustomerServiceImplTest` (6), `SupplierServiceImplTest` (4) — shared-party reuse, duplicate-role rejection, walk-in idempotency, company scoping.
- **Integration:** Customer credit-limit + deactivate flow. *(pending)*
- **System:** `TC-PARTY-001` .. `TC-PARTY-018`. *(pending)*

**DoD:** Back-office user creates a B2B customer, a vendor, and an employee; a second supplier created with an existing customer's TIN attaches to that party instead of duplicating it. Walk-in customer is provisioned with every new branch.

---

## F1.8 — Party: biometric enrolment (optional MVP)

**Story:** US-IAM-011, US-IAM-012 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F1.7.

Defer to Phase 8 unless biometric-cashier-login is a launch requirement.

---

# Phase 2 — Transactional foundation

## F2.1 — Business day open / close / override

**Story:** US-DAY-001 .. US-DAY-005 · **Size:** M · **Status:** `[x]`
**Dependencies:** F1.1.

**Backend:**
- [x] `BusinessDay` (composite PK, `@IdClass`) + `BusinessDayOverride` entities. `business_day` ships in V1; `V9` adds `business_day_override`, `V9_1` its sequence, `V10` seeds `DAY.*` permissions.
- [x] `BusinessDayService` + Impl — OPEN → CLOSING → CLOSED state machine; invariants: at most one non-closed day per branch, monotonic business dates (no backdating an open). `BusinessDayController` gated by `DAY.OPEN` / `DAY.CLOSE`.
- [x] `DayGuard` synchronous port — `requireOpenDay(branchId)` for other modules' posting services to consume.
- [~] EOD pre-flight checks — `startClosing` is the hook; the delegated calls into pos / procurement / stock / production are **deferred** until those posting modules exist (TODO marker in place).
- [ ] Scheduled job (auto-warn 30h, auto-revoke override) — **deferred**: the `business_day_override` schema is an audit record (no expiry/standing window), so the plan's `OverrideExpired` model doesn't match; revisit if a standing-override model is wanted.
- [x] Events: `BusinessDayOpened.v1`, `BusinessDayClosingStarted.v1`, `BusinessDayClosed.v1`.

**Web:**
- [x] `/day` dashboard: current-day status for the active branch, open-day form, start-closing + close-day actions (gated by `DAY.*`), recent-days table. *(Override dialog deferred — overrides are written when back-dating posts, which don't exist yet.)*

**Tests:**
- **Unit:** `BusinessDayServiceImplTest` (9) — open / start-closing / close transitions, single-non-closed-day + monotonic-date guards, company scoping.
- **Integration:** Open day → fail EOD (open till) → close after till closes. *(pending — needs the till module)*
- **System:** `TC-DAY-001` .. `TC-DAY-025`; cross-module `TC-E2E-017`, `TC-E2E-018`. *(pending)*

**DoD:** Branch manager opens the day on the `/day` dashboard and ends it (start-closing → close); a second open is rejected while one is non-closed; backdated opens are rejected.

---

## F2.2 — Stock ledger + balances

**Story:** US-STOCK-001, US-STOCK-002, US-STOCK-009, US-STOCK-010 · **Size:** L · **Status:** `[x]`
**Dependencies:** F1.3 (items), F1.1 (branches).

**Backend:**
- [x] `StockMove` (append-only, immutable) + `ItemBranchBalance` (composite-PK cache) entities + repos. Migration `V11` + `V11_1` sequence. Append-only is enforced at the app layer (no update path) — a portable DB-level UPDATE-reject trigger was skipped.
- [x] `StockMoveService.post` updates the balance in the same transaction; moving-average cost on inbound, consume-at-`avg_cost` on outbound. Posting requires the branch's open business day (`DayGuard`).
- [x] Negative-stock guard with `STOCK.OVERSELL` override (`V12` seeds the permission). *(`NegativeStockBlocked.v1` not emitted — a blocking throw rolls back the outbox row; needs a separate-tx emit, deferred.)*
- [x] Events: `StockMoved.v1`, `BalanceUpdated.v1`, `LowStockTriggered.v1`.
- [x] `GET /api/v1/stock-moves` (paged, branch filter), `GET /api/v1/balances`, `GET /api/v1/stock-card`. No POST endpoint — moves are posted by the modules that own the causing document (and F2.5 adjustments).

**Web:**
- [x] `/stock/balances` grid (active branch, low-stock rows highlighted).
- [x] `/stock/card/:itemId` movement ledger per item.

**Tests:**
- **Unit:** `StockMoveServiceImplTest` (8) — moving-average math, oversell block + override, open-day requirement, same-tx event emission.
- **Integration:** Replay stock_move to rebuild balance — match. *(pending)*
- **System:** `TC-STOCK-001` .. `TC-STOCK-004`, `TC-STOCK-012`, `TC-STOCK-013`; `TC-E2E-021`. *(pending)*

**DoD:** A posted move updates `item_branch_balance` atomically (moving-average maintained); the per-item stock card is queryable; oversell is blocked without `STOCK.OVERSELL`.

---

## F2.3 — Stock counts + transfers

**Story:** US-STOCK-004 .. US-STOCK-008 · **Size:** M · **Status:** `[x]`
**Dependencies:** F2.2.

**Backend:**
- [x] `StockCount`, `StockCountLine`, `StockTransfer`, `StockTransferLine` entities + repos. Migration `V13` / `V13_1` sequences; `V14` seeds `STOCK.COUNT` + `STOCK.TRANSFER`.
- [x] Count lifecycle DRAFT → IN_PROGRESS → CLOSED → POSTED — create freezes system qty from the balance; close computes per-line variance; post turns every non-zero variance into an `ADJUSTMENT` move (`allowOversell`, posted at the item's current avg cost). `StockCountController` gated by `STOCK.COUNT`.
- [x] Transfer lifecycle DRAFT → ISSUED → RECEIVED → CLOSED — issue posts `TRANSFER_OUT` from the source branch (cost frozen from its avg cost), receive posts `TRANSFER_IN` into the destination at that cost. `StockTransferController` gated by `STOCK.TRANSFER`. *(`IN_TRANSIT` is in the enum but the flow goes ISSUED→RECEIVED directly; revisit if a separate in-transit confirmation step is wanted.)*

**Web:**
- [x] `/stock/counts` — list + create + lifecycle (start / record counts / close / post).
- [x] `/stock/transfers` — list + create + lifecycle (issue / receive / close).

**Tests:**
- **Unit:** `StockCountServiceImplTest` (5), `StockTransferServiceImplTest` (5) — variance compute + post, dup-number rejection, issue freezes cost + posts TRANSFER_OUT, receive posts TRANSFER_IN, non-ISSUED receive rejected.
- **System:** `TC-STOCK-005` .. `TC-STOCK-011`; `TC-E2E-020`. *(pending)*

**DoD:** Storekeeper runs a cycle count; non-zero variances post as ADJUSTMENT moves. An inter-branch transfer flows DRAFT → ISSUED → RECEIVED → CLOSED with the matching stock moves.

---

## F2.4 — Batch tracking + FEFO consumption

**Story:** US-STOCK-011, US-STOCK-012, US-STOCK-013, US-STOCK-014 · **Size:** L · **Status:** `[x]`
**Dependencies:** F2.2, F1.6.

**Backend:**
- [x] `StockBatch` entity (per (branch, item, batch_no), `ACTIVE`/`EXHAUSTED`/`EXPIRED`/`RECALLED`), `StockBatchRepository`, `StockBatchService` + Impl. `stock_move` gains a nullable `batch_id`; `PostStockMoveRequestDto` threads it through so callers can attribute a move to a specific batch.
- [x] `StockBatchService.createBatch` — used by inbound flows (GRN / production output / opening) once they land. The actual call sites land with F3.2 / F7.3; for now F2.4 ships the service surface + the recall write-off path that already exercises it.
- [x] `StockBatchService.drainFefo(itemId, branchId, qty)` — earliest-expiry first; drains each batch, flips it to `EXHAUSTED` when on-hand hits zero, emits `StockBatchExhausted.v1`, returns per-batch picks. Callers split outbound moves accordingly (POS / production wire it in F5.2 / F7.3).
- [x] `StockBatchExpiryJob` `@Scheduled(cron = ...)` — flips ACTIVE rows whose `expiry_at` is before today to `EXPIRED`, emits `StockBatchExpired.v1` per row. Doesn't write `stock_move` rows itself; the matching write-off is an operator action (recall endpoint).
- [x] `EXPIRY_WRITE_OFF` move type. `recallBatch(batchId, reason)` writes off remaining on-hand as an `EXPIRY_WRITE_OFF` move (with the right `batch_id` stamped) and emits `BatchRecalled.v1`.
- [x] `V15` / `V15_1` migrations create `stock_batch` + add `stock_move.batch_id`; `V16` seeds `STOCK.BATCH` permission. `StockBatchController` gated by it.

**Web:**
- [x] `/stock/batches` — "Expiring soon" (default; days-ahead configurable) and "All batches" (status / item / branch filters) modes; recall button on ACTIVE rows prompts for a reason.

**Tests:**
- **Unit:** `StockBatchServiceImplTest` (10) — create + dup rejection, FEFO order + exhaustion + insufficient-batches throw, mark-expired emits event, recall writes off remaining + emits event, recall on zero on-hand skips the move, foreign-company recall 404, expiring-soon filter. `StockMoveServiceImplTest` extended to cover `batchId` pass-through. `ItemServiceImplTest` extended with archive-guard + disable-tracking guard against active batches.
- **Integration:** Mixed-batch consumption; expiry job marks batches. *(pending — slots in with F3.2 GRN integration)*
- **System:** `TC-STOCK-014` .. `TC-STOCK-019`; `TC-E2E-009`, `TC-E2E-010`. *(pending)*

**DoD:** Batch-tracked item's stock card shows per-batch breakdown; POS pulls earliest expiry first. *(POS wiring lands with F5.2; stock card per-batch view lands when callers start stamping `batch_id` on moves.)*

---

## F2.5 — Stock adjustments + internal consumption

**Story:** US-STOCK-003, US-STOCK-015, US-STOCK-016 · **Size:** S · **Status:** `[x]`
**Dependencies:** F2.2.

**Backend:**
- [x] `POST /api/v1/adjustments` (`AdjustmentService` + Impl, gated by `STOCK.ADJUST`) — posts an `ADJUSTMENT` stock move; supervisor-threshold rule: when `|qty × cost|` exceeds `orbix.stock.adjustment-threshold` (default 50000) or `allowOversell = true`, an `authorisedByUserId` is required, must differ from the caller, and must hold `STOCK.ADJUST_APPROVE` (verified via `PermissionResolverService`).
- [x] `POST /api/v1/internal-consumption` (`InternalConsumptionService` + Impl, gated by `STOCK.INTERNAL_CONSUMPTION`) — outbound `INTERNAL_CONSUMPTION` move with required `consumptionCategory` (CANTEEN / DISPLAY / SAMPLES / DONATION / MAINTENANCE / OTHER), `sectionId`, and `authorisedByUserId` (must hold the same permission and differ from caller).
- [x] Section-tagged moves: `stock_move` gains nullable `section_id` / `consumption_category` / `authorised_by_user_id` (`V17`); `PostStockMoveRequestDto` threads them so any future section-to-section transfer path can stamp them.

**Web:**
- [x] `/stock/adjust` form — signed qty + reason + optional section / batch / authoriser + oversell checkbox.
- [x] `/stock/internal-consumption` form — qty + category dropdown + required section / authoriser / reason.

**Tests:**
- **Unit:** `AdjustmentServiceImplTest` (8) — below-threshold solo, above-threshold rejection without authoriser / self-authoriser / authoriser missing permission, above-threshold with approved authoriser, oversell forces authoriser, zero qty rejected, inbound uses unit cost for threshold. `InternalConsumptionServiceImplTest` (3) — happy path stamps category/section/authoriser, self-authoriser rejected, authoriser without `STOCK.INTERNAL_CONSUMPTION` 403.
- **System:** `TC-STOCK-007`, `TC-STOCK-008`, `TC-STOCK-023`, `TC-STOCK-024`, `TC-STOCK-025`. *(pending)*

**DoD:** Staff canteen draws stock; report shows consumption by category. *(Reporting view lands with F8.1/F8.2; F2.5 ships the posting path + per-category data on the move row.)*

---

# Phase 3 — Inbound (Procurement)

## F3.1 — LPO lifecycle

**Story:** US-PROC-002, US-PROC-003, US-PROC-011, US-PROC-012 · **Size:** L · **Status:** `[x]`
**Dependencies:** F1.7 (suppliers), F1.3 (items), F2.1 (day — declared but not enforced here; the day-guard kicks in at F3.2 when GRN posts stock).

**Backend:**
- [x] `LpoOrder` + `LpoOrderLine` entities (`V19` + `V19_1`); `LpoOrderRepository` + `LpoOrderLineRepository`. Header carries supplier / order date / expected delivery / currency / totals / status. Per-branch unique LPO number.
- [x] `LpoOrderService` + Impl with state machine DRAFT → PENDING_APPROVAL → APPROVED; DRAFT/PENDING → CANCELLED. PARTIALLY_RECEIVED / RECEIVED transitions land with F3.2 (GRN). Line totals = `ordered_qty × unit_price × (1 − discount_pct/100)`; header tax rolls up from `vat_group.rate` snapshot per line.
- [x] Auto-approval: when `total ≤ orbix.procurement.lpo-auto-approval-threshold` (defaults to 0 = always require explicit approval), `submit` skips PENDING_APPROVAL and goes straight to APPROVED. Approval endpoint is gated by `PROCUREMENT.APPROVE_LPO`.
- [x] Events: `LpoOrderCreated/Submitted/Approved/Cancelled.v1` via the outbox.
- [ ] PDF / email export via `LpoOrderRenderer` + email subscriber on `LpoOrderApproved.v1` — **deferred**: the event already fires, so a subscriber can land later without touching this service.

**Web:**
- [x] `/procurement/lpos` — list (with status badges), draft creation (single-line via the form), and state-aware Submit / Approve / Cancel buttons. View pane shows header + lines + approved-by/at + notes.
- [ ] PDF preview, "send to supplier" action — **deferred** with the renderer/email work above.

**Tests:**
- **Unit:** `LpoOrderServiceImplTest` (12) — create rolls up subtotal/tax/total + emits LpoOrderCreated.v1; line discount applied; duplicate number rejected; submit auto-approves below threshold + emits Approved (not Submitted); submit above threshold goes to PENDING_APPROVAL; submit with threshold=0 always pending; approve from PENDING_APPROVAL sets approvedBy/at + emits Approved; approve direct from DRAFT rejected; cancel from DRAFT works + emits Cancelled; cancel from APPROVED rejected; get-foreign-company 404; updateDraft deletes & replaces lines and re-rolls totals.
- **Integration:** Multi-line LPO with mixed tax classes; auto-approval boundary. *(pending)*
- **System:** `TC-PROC-004` .. `TC-PROC-009`. *(pending)*

**DoD:** Merchandiser creates an LPO, sends for approval, manager approves. *(Partial DoD: PDF / email to supplier deferred with the renderer.)*

---

## F3.2 — GRN posting (+ batch capture)

**Story:** US-PROC-004, US-PROC-005, US-STOCK-011 · **Size:** L · **Status:** `[x]`
**Dependencies:** F3.1, F2.2, F2.4.

**Backend:**
- [x] `Grn` + `GrnLine` entities (`V21` + `V21_1`). Line carries `lpo_order_line_id` (nullable for direct), `batch_no`, `expiry_date`. Per-branch unique GRN number.
- [x] `GrnService` + Impl. LPO-bound: requires APPROVED or PARTIALLY_RECEIVED LPO; matches supplier + branch; validates each line against `lpo_order_line.outstandingQty()` (over-receipt rejected); on post advances `received_qty` per LPO line and flips the LPO to PARTIALLY_RECEIVED or RECEIVED (all lines fully received). Header subtotal = Σ(received_qty × unit_cost); tax rolled up from `vat_group.rate` per line.
- [x] Direct GRN: `lpoOrderId = null`. Gated by `GRN.DIRECT` (checked at the controller — the create endpoint inspects the security context).
- [x] `GrnPosted.v1` event; posting routes through `StockMoveService.post` (DayGuard + moving-average reused). Batch-tracked items create a `stock_batch` via `StockBatchService.createBatch` first, then post the `stock_move` with the new `batch_id`. Validation: batch-tracked items require a non-blank `batchNo`.
- [x] `V22` seeds `GRN.POST` (everyday receiving) + `GRN.DIRECT` (no-LPO receiving).

**Web:**
- [x] `/procurement/grns` — list (status badges) + "Receive against LPO" form that loads an LPO by id, surfaces per-line `outstandingQty`, and accepts qty / unit cost / batch_no per line. State-aware Post / Cancel buttons.
- [ ] Direct (no-LPO) GRN form — **deferred**: the backend supports it (`GRN.DIRECT`), but the UI currently only drives receive-against-LPO. Add a separate flow when direct purchases land in pilot use.

**Tests:**
- **Unit:** `GrnServiceImplTest` (10) — create against LPO rolls up subtotal/tax/total + emits Created; over-receipt rejected; LPO not in APPROVED/PARTIALLY_RECEIVED rejected; batch-tracked item without batch_no rejected; post writes stock moves with the right type + cost + emits Posted; batch-tracked item creates stock_batch and stamps batch_id on the move; partial receipt flips LPO to PARTIALLY_RECEIVED; full receipt flips to RECEIVED; cancel from DRAFT works; duplicate number rejected.
- **Integration:** GRN post → stock_batch + stock_move + avg_cost recomputed. *(pending — exercised end-to-end once Testcontainers fixtures land)*
- **System:** `TC-PROC-010` .. `TC-PROC-015`; `TC-E2E-002`, `TC-E2E-009`. *(pending)*

**DoD:** Storekeeper receives goods against LPO; stock balance updates; batch-tracked items get batch rows. *(Direct-GRN UI deferred; backend ready.)*

---

## F3.3 — Supplier invoice match (3-way)

**Story:** US-PROC-006 · **Size:** M · **Status:** `[x]`
**Dependencies:** F3.2.

**Backend:**
- [x] `SupplierInvoice` + `SupplierInvoiceGrn` (composite-PK junction) entities (`V23` + `V23_1`). DRAFT → POSTED lifecycle; DRAFT or POSTED → CANCELLED; settlement transitions (PARTIALLY_PAID / PAID) land with F3.4. Per-branch unique `number`, per-supplier unique `supplier_invoice_no`.
- [x] `SupplierInvoiceService` + Impl. Match validation: every referenced GRN must be POSTED and from the same supplier; cumulative allocations to a GRN may not exceed `grn.total_amount` (over-allocation rejected; cancelled invoices excluded from the running total); Σ allocations must match `invoice.total_amount` within `orbix.procurement.invoice-match-tolerance-pct` (defaults 0.005 = ±0.5%). Due date defaults to `invoice_date + supplier.payment_terms_days`; can be overridden in the request. `SupplierInvoiceCreated/Matched/Cancelled.v1` events.
- [x] `V24` seeds `PROCUREMENT.MANAGE_INVOICE` — controller gates all paths.

**Web:**
- [x] `/procurement/invoices` — list with status-coloured badges; new-invoice form loads the chosen supplier's POSTED GRNs into a dropdown, each row adds one `(grnId, amount)` allocation, a live "Total / Allocated / Diff" banner warns when the match is outside tolerance; state-aware Post / Cancel buttons.

**Tests:**
- **Unit:** `SupplierInvoiceServiceImplTest` (13) — single-GRN full match + due-date from supplier terms; multi-GRN match within tolerance; outside-tolerance rejected; over-allocation rejected; foreign-supplier GRN rejected; unposted GRN rejected; explicit dueDate overrides supplier terms; post emits Matched event; post re-checks tolerance with current allocations; cancel from DRAFT works; duplicate branch number + duplicate supplier_invoice_no rejected; foreign-company 404.
- **System:** `TC-PROC-017` .. `TC-PROC-021`. *(pending)*

**DoD:** Accountant matches an invoice covering 2 GRNs; supplier debt opens correctly. *(Payable status changes land with F3.4; F3.3 ships the matched invoice + its allocations + the events the cash module will consume.)*

---

## F3.4 — Supplier payment

**Story:** US-PROC-007 · **Size:** M · **Status:** `[x]`
**Dependencies:** F3.3 (invoices). F6.1 (cash entries / cash_book mirror) listens via event subscriber; F3.4 ships the payable side ahead of it.

**Backend:**
- [x] `SupplierPayment` + `SupplierPaymentAllocation` entities (lives in `cash` module, `V25` + `V25_1`). Per-branch unique payment number. DRAFT → POSTED → terminal lifecycle (POSTED writes settlement); DRAFT → CANCELLED. `PaymentMethod` enum: CASH / BANK_TRANSFER / CHEQUE / MOBILE_MONEY. `SupplierPaymentStatus` enum.
- [x] `SupplierPaymentService` + Impl. Allocation guards: each invoice in-company + same supplier + no-duplicate-in-payment + `amount ≤ invoice.outstandingAmount()` + Σ allocations ≤ payment total. Posting requires `DayGuard.requireOpenDay(branchId)`. `SupplierInvoice` gained `outstandingAmount()` + `applyPayment(amount, actorId)` — flips to PARTIALLY_PAID until fully paid → PAID.
- [x] Events: `SupplierPaymentCreated/Posted/Cancelled.v1`. The cash-side mirror (cash_entry OUT + cash_book delta) is owned by F6.1 and subscribes to `SupplierPaymentPosted.v1`.
- [x] `V26` seeds `CASH.MANAGE_SUPPLIER_PAYMENT`; controller gates all paths.

**Web:**
- [x] `/procurement/payments` — supplier-scoped open-invoice picker (loads POSTED + PARTIALLY_PAID invoices for the chosen supplier); each allocation row shows the invoice's outstanding amount and pre-fills the input; live Total / Allocated / Remaining hint; state-aware Post / Cancel. (Mounted under `/procurement` for now; gets its own top-level `/cash` menu when F6.1 lands.)

**Tests:**
- **Unit:** `SupplierPaymentServiceImplTest` (10) — createDraft captures allocations + emits Created without touching invoices; allocation > outstanding rejected; Σ allocations > payment total rejected; foreign-supplier invoice rejected; duplicate-invoice allocation rejected; post advances paid_amount + flips PARTIALLY_PAID; full settlement flips to PAID; post requires DayGuard.requireOpenDay; cancel from DRAFT works; duplicate branch number rejected.
- **System:** `TC-PROC-022` .. `TC-PROC-025`; `TC-E2E-002`. *(pending)*

**DoD:** Accountant records a payment to a supplier; matched invoices flip to PARTIALLY_PAID / PAID; the `SupplierPaymentPosted.v1` event is queued for the F6.1 cash-side mirror.

---

## F3.5 — Vendor return + credit note

**Story:** US-PROC-008, US-PROC-009 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F3.2, F3.3.

Defer to Phase 8 if not used in pilot.

---

# Phase 4 — Outbound (Back-office Sales)

## F4.1 — Sales quotation

**Story:** US-SALES-003, US-SALES-004 · **Size:** M · **Status:** `[skipped]`
**Dependencies:** F1.7 (customers), F1.5 (price lists).

Skipped — per plan "Skip if pilot doesn't need quotations; jump to F4.2." Revisit when a sales-quote flow is requested.

---

## F4.2 — Sales invoice posting (cash + credit)

**Story:** US-SALES-005, US-SALES-006, US-SALES-007 · **Size:** L · **Status:** `[x]`
**Dependencies:** F1.7, F1.5, F2.2, F2.1.

**Backend:**
- [x] `SalesInvoice` + `SalesInvoiceLine` entities (`V27` + `V27_1`); per-branch unique number; `PaymentTerms` (CASH/CREDIT). DRAFT → POSTED → settlement-via-F4.3 → VOIDED | CANCELLED.
- [x] `SalesInvoiceService` + Impl. Credit-limit check sums POSTED + PARTIALLY_PAID outstanding debt against `customer.creditLimitAmount`; zero-limit rejects credit. Discount-threshold via `orbix.sales.discount-threshold-pct` requires `SALES.DISCOUNT_APPROVE` authoriser (verified via `PermissionResolverService`). `item.minSellPrice` enforced post-discount.
- [x] Same-business-day-only void writes RETURN_IN compensating moves at the snapped line cost. Rejects voids on invoices with any payment or any batch-tracked line (use F4.4 customer return instead).
- [x] Posting writes outbound SALE moves via `StockMoveService` (DayGuard + moving-average reused); batch-tracked items drain FEFO via `StockBatchService` and emit one move per pick — `line.cost_amount` becomes the qty-weighted cost across picks.
- [x] Events: `SalesInvoiceCreated/Posted/Voided/Cancelled.v1`. `V28` seeds the 5 sales permissions in one shot.

**Web:**
- [x] `/sales/invoices` — list with status-coloured badges + multi-line draft form (customer / agent / dates / terms / currency / price list / discount approver / per-line items). State-aware Post / Void / Cancel buttons.

**Tests:**
- **Unit:** `SalesInvoiceServiceImplTest` (14) — create + totals, credit-limit + zero-limit, discount-threshold (missing / wrong-permission / approved authoriser), min-sell-price, post writes stock moves, batch-tracked FEFO drain, day-closed, same-day void, void-on-different-day rejected, void-with-batch rejected, cancel from draft.
- **System:** `TC-SALES-001` .. `TC-SALES-013`; `TC-E2E-003`. *(pending)*

**DoD:** Back-office user raises a credit invoice; debt opens.

---

## F4.3 — Sales receipt + allocation

**Story:** US-SALES-008, US-SALES-009 · **Size:** M · **Status:** `[x]`
**Dependencies:** F4.2.

**Backend:**
- [x] `SalesReceipt` + `ReceiptAllocation` entities (`V29` + `V29_1`). DRAFT → POSTED → terminal + DRAFT → CANCELLED. `ReceiptMethod`: CASH/CARD/BANK_TRANSFER/MOBILE_MONEY/CHEQUE/STORE_CREDIT.
- [x] `SalesReceiptService` + Impl. Allocation guards mirror F3.4: same-customer + in-company + no-duplicate-invoice + amount ≤ outstanding + Σ ≤ receipt total. Surplus tracked on `unallocated_amount`; customer-credit routing is follow-on work.
- [x] DayGuard on post; `SalesInvoice.applyReceipt` flips PARTIALLY_PAID / PAID. `SalesReceiptCreated/Posted/Cancelled.v1` events.

**Web:**
- [x] `/sales/receipts` with customer-scoped open-invoice picker that pre-fills the allocation amount from outstanding. State-aware Post / Cancel.

**Tests:**
- **Unit:** `SalesReceiptServiceImplTest` (9) — capture allocations + unallocated math; outstanding/total overruns rejected; foreign customer rejected; post advances invoice paid + flips PARTIALLY_PAID; full settlement → PAID; day-closed; cancel; duplicate number.
- **System:** `TC-SALES-014` .. `TC-SALES-017`; `TC-E2E-003`. *(pending)*

---

## F4.4 — Customer return + credit note

**Story:** US-SALES-010, US-SALES-011 · **Size:** M · **Status:** `[x]`
**Dependencies:** F4.2.

**Backend:**
- [x] `CustomerReturn` + `CustomerReturnLine` entities (`V30` + `V30_1`). DRAFT → POSTED → CREDITED + DRAFT → CANCELLED. `ReturnReason`: DAMAGED/EXPIRED/WRONG_ITEM/BUYER_REMORSE/OTHER.
- [x] On post (DayGuard required) writes RETURN_IN moves when `restock=true`, DAMAGE moves otherwise. Batch-tracked items rejected — restock-to-original-batch needs a separate slice.
- [x] Issue-credit-note transitions POSTED → CREDITED and creates a `CustomerCreditNote` (DATA-MODEL §6.9) at the full return total. Allocation of the credit note to open invoices is a follow-on slice.
- [x] Events: `CustomerReturnCreated/Posted/Cancelled.v1`, `CustomerCreditNoteIssued.v1`.

**Web:**
- [x] `/sales/returns` — list + draft form with reason + restock toggle + per-line items. State-aware Post / Cancel / Issue-credit-note.

**Tests:** `TC-SALES-018` .. `TC-SALES-021`. *(pending)*

---

## F4.5 — Packing list

**Story:** US-SALES-012 · **Size:** M · **Status:** `[x]`
**Dependencies:** F4.2.

**Backend:**
- [x] `PackingList` + `PackingListLine` entities (`V31` + `V31_1`). DRAFT → DISPATCHED → DELIVERED → terminal + DRAFT → CANCELLED. Created against POSTED / PARTIALLY_PAID / PAID invoices.
- [x] Tracking-only — no stock moves are written by the packing list itself; the parent sales invoice already decremented stock on post. The IN_TRANSIT split mentioned in DATA-MODEL §6.10 is deferred to a follow-on slice when packing-then-invoice workflow becomes a real customer ask.
- [x] Events: `PackingListCreated/Dispatched/Delivered/Cancelled.v1`.

**Web:**
- [x] `/sales/packing-lists` — load invoice by id, tick lines + qty, draft → dispatch → deliver. State-aware buttons.

**Tests:** `TC-SALES-022` .. `TC-SALES-024`. *(pending)*

---

# Phase 5 — POS (Till)

The biggest phase. Strongly recommend doing F5.1 → F5.2 → F5.4 (offline sync) as a thin slice first, then F5.3 / F5.5 .. F5.10 in any order.

## F5.1 — Till + session lifecycle

**Story:** US-POS-002, US-POS-016 · **Size:** L · **Status:** `[x]`
**Dependencies:** F1.1, F2.1, F0.2.

**Backend:**
- [x] `Till` + `TillSession` entities (`V32` + `V32_1`). Till status ACTIVE / INACTIVE; cannot deactivate while a session is OPEN.
- [x] `TillSessionService` + Impl. OPEN → CLOSED → RECONCILED; at most one OPEN per till. Opening requires `DayGuard.requireOpenDay`. On close: `expected_cash = opening_float` (POS-sale cash + pickups + petty-cash contributions wire in F5.2+); `variance = declared - expected`. When `|variance| > orbix.pos.session-variance-threshold` (defaults to 1000), the close request requires a `supervisorId` holding `POS.SESSION_VARIANCE_APPROVE`, different from the caller (`PermissionResolverService` verifies).
- [x] Events: `TillCreated/Activated/Deactivated.v1`, `TillSessionOpened/Closed/Reconciled.v1`.
- [x] `V33` seeds `POS.MANAGE_TILL` / `SESSION_OPEN` / `SESSION_CLOSE` / `SESSION_RECONCILE` / `SESSION_VARIANCE_APPROVE`.

**Flutter POS:**
- [ ] Till-open screen, cashier+supervisor PIN flow — **deferred** to the Flutter POS slice. F5.1 ships the backend + a web admin screen at `/admin/tills` so managers can manage tills and inspect / close / reconcile sessions while the cashier flow lives in the upcoming Flutter app.
- [ ] Till-close screen; declare cash; show variance — same deferral.

**Web:**
- [x] `/admin/tills` — list + create + activate/deactivate tills; open / close / reconcile sessions; live variance display per session.

**Tests:**
- **Unit (backend):** `TillSessionServiceImplTest` (11) — open succeeds + emits event; rejects when an OPEN session exists; rejects inactive till; requires open business day; close below threshold succeeds; close above threshold without supervisor / with self-supervisor / with unauthorised supervisor / with approved supervisor; reconcile from CLOSED succeeds; reconcile from OPEN rejected.
- **Integration:** Open → multiple sales → close. *(pending until F5.2 lands cash sales)*
- **System:** `TC-POS-001` .. `TC-POS-004`, `TC-POS-020`, `TC-POS-021`. *(pending)*

**DoD:** Cashier signs in, opens till, signs out at end of shift. *(Backend ready; Flutter cashier UI lands with the POS sale slice.)*

---

## F5.2 — Basic POS sale (cash tender)

**Story:** US-POS-003, US-POS-004, US-POS-009, US-POS-010 · **Size:** L · **Status:** `[x]` *(backend-complete; Flutter till app deferred)*
**Dependencies:** F5.1, F2.2.

**Backend:**
- [x] `PosSale` + `PosSaleLine` + `PosPayment` entities (`V34` + `V34_1`). POS sales are committed locally and pushed as POSTED — there is no DRAFT. Per-company unique `(number, client_op_id)`; section_id required on the header per Phase 1.1 §17.12.
- [x] `PosSaleService` + Impl. Validates: idempotent retry on `clientOpId`; till session must be OPEN; section must belong to the till's branch; customer in-company; tender sum ≥ total → `change_amount = tender - total`. Posting routes through `StockMoveService` so DayGuard + moving-average + per-batch FEFO drains are reused. Batch-tracked items emit one stock_move per FEFO pick with `batch_id` stamped; `line.cost_amount` is the qty-weighted cost.
- [x] Mixed tender supported via N `pos_payment` rows (CASH / CARD / MOBILE_MONEY / VOUCHER / STORE_CREDIT). Card payments record `terminal_id` + `last4` only — never the PAN.
- [x] Events: `PosSaleClosed.v1`. `V35` seeds `POS.SALE_POST`.

**Flutter POS:**
- [ ] Barcode scan / typeahead → cart — **deferred** to the Flutter app slice.
- [ ] Tender + cash drawer interaction — **deferred**.
- [ ] Thermal-printer receipt — **deferred**.

**Web:**
- [x] `/admin/pos-sales` read-only viewer for managers (the push endpoint will be hit by the Flutter app; the admin web page lets managers inspect each sale's lines + payments).

**Tests:**
- **Unit:** `PosSaleServiceImplTest` (10) — single-line cash sale + stock move + change; tender < total rejected; mixed-tender sums to zero change; idempotent retry on same `clientOpId` returns the prior sale without re-posting stock; closed till session rejected; section from wrong branch rejected; duplicate number rejected; batch-tracked FEFO drain emits one move per pick; closed business day propagates from `StockMoveService`; card payment persists reference / terminal / last4.
- **System:** `TC-POS-005` .. `TC-POS-011`; `TC-E2E-001`. *(pending — exercise once Flutter app + Testcontainers end-to-end stack lands)*

**DoD:** Cashier scans 5 items, takes cash, prints receipt. *(Backend ready; cashier UI lands with the Flutter app slice.)*

---

## F5.3 — Discounts, voids, mixed tender

**Story:** US-POS-005, US-POS-006, US-POS-009 · **Size:** M · **Status:** `[x]`
**Dependencies:** F5.2.

**Backend:**
- [x] Per-line discount threshold via `orbix.pos.discount-threshold-pct` (default 10%); above-threshold lines require `discountApproverId` (must differ from caller, must hold `POS.DISCOUNT_APPROVE`).
- [x] Optional header-level `headerDiscountAmount` on the request — applied after per-line tax; rejected when negative or > subtotal.
- [x] Same-business-day void path: `POST /api/v1/pos-sales/{id}/void` (gated by `POS.SALE_VOID`). Writes RETURN_IN compensating stock moves at the snapped line cost; rejects voids on a different business day or on sales containing batch-tracked lines (use F5.5 refund there).
- [x] Mixed tender (CASH / CARD / MOBILE_MONEY / VOUCHER / STORE_CREDIT) already live from F5.2 — F5.3 doesn't re-touch it.
- [x] `V36` seeds `POS.SALE_VOID` + `POS.DISCOUNT_APPROVE`. Event: `PosSaleVoided.v1`.

**Web:**
- [x] `/admin/pos-sales` gains a Void button on POSTED sales (prompts for a reason); detail pane shows the header discount.

**Tests:**
- **Unit:** `PosSaleServiceImplTest` extended (+9 cases) — discount-threshold rejection / self-approver / unauthorised / approved-approver; header discount applied after tax; header discount > subtotal rejected; same-day void writes RETURN_IN + emits Voided event; different-day void rejected; batch-tracked void rejected.
- **System:** `TC-POS-012` .. `TC-POS-015`. *(pending)*

---

## F5.4 — Offline sync

**Story:** US-POS-017, US-POS-018 · **Size:** XL · **Status:** `[x]` *(backend-complete; Flutter app deferred)*
**Dependencies:** F5.2.

Split into:
- **F5.4a** Local SQLite catalog + balance snapshot pull — **server contract shipped**, Flutter side deferred.
- **F5.4b** Outbox queue + sync push with `client_op_id` idempotency — **server contract shipped**, Flutter outbox deferred.
- **F5.4c** Conflict resolution + re-order handling — partly served by per-item rejection in the batch push response; full UI deferred with the Flutter app.

**Backend:**
- [x] `POST /api/v1/sync/push` — batched POS-sale push. Each item runs in its own `PosSaleService.post` transaction so partial failures don't drop the batch. The response carries per-item accepted/rejected verdicts keyed by `clientOpId` for the till to ack its outbox.
- [x] Idempotency on `client_op_id` (UNIQUE on `pos_sale` per company) was already in place from F5.2; the sync push reuses it — the same `clientOpId` returns the prior sale's id without re-posting stock.
- [x] `GET /api/v1/sync/catalog/snapshot?branchId=&priceListId=` — active items + barcodes + vat rate + weighed/batch flags + min sell price + current price-list price + per-branch on-hand qty (so the till can scan offline and warn on oversell).
- [x] `GET /api/v1/sync/balances/snapshot?branchId=` — current `item_branch_balance` rows for a soft pre-flight oversell check.
- [x] `V37` seeds `POS.SYNC`.

**Flutter POS:**
- [ ] Local DB (Drift): items, prices, recent sales, outbox — **deferred** to the Flutter app slice. The server contract is locked.
- [ ] Sync poller, retry, backoff — **deferred**.
- [ ] Conflict UI when server rejects — **deferred** (server already returns per-item rejection reasons via the batch push DTO).

**Tests:**
- **Unit:** `SyncServiceImplTest` (4) — all-accepted batch push; partial-failure isolation (one bad item, others still POSTED); catalog snapshot returns items + barcodes + price + on-hand correctly; balance snapshot returns branch rows.
- **Integration:** Push duplicate → no second row. *(idempotency is already covered in `PosSaleServiceImplTest`)*
- **System:** `TC-POS-022` .. `TC-POS-025`; `TC-E2E-004`. *(pending — need the Flutter app to drive end-to-end)*

**DoD:** POS sells offline for 30 minutes, then catches up the moment network returns. *(Backend ready; cashier UI lands with the Flutter app slice.)*

---

## F5.5 — Refund at till

**Story:** US-POS-019, US-POS-020 · **Size:** M · **Status:** `[x]` backend (cash-out wiring deferred to F6.1)
**Dependencies:** F5.2, F6.1.

**Backend:**
- [x] `pos_sale.kind = REFUND` flow: `POST /api/v1/pos-sales/refund` gated by `POS.REFUND_POST`. Creates a new `PosSale` with `kind = REFUND` and `refunded_from_sale_id` pointing at the original; writes compensating `RETURN_IN` stock moves at the snapped line cost; same-business-day only; rejects batch-tracked items (restore-to-original-batch deferred); rejects refund lines whose item wasn't on the original; idempotent on `clientOpId`.
- [x] Threshold rule: `orbix.pos.refund-threshold` (default 10000). Above-threshold refund totals require `supervisorId` holding `POS.REFUND_APPROVE`, different from cashier.
- [x] Tender must equal refund total exactly (no change paid on a refund).
- [x] `V38` seeds `POS.REFUND_POST` + `POS.REFUND_APPROVE`. Event: `PosSaleRefunded.v1` — carries `originalSaleId`, `totalAmount`, `tenderedAmount`, `branchId`, `tillSessionId`. F6.1 subscribes to write `cash_entry` OUT.
- [ ] Cash refund posts `cash_entry` OUT — deferred to F6.1's event subscriber.

**Tests:** `TC-POS-026` .. `TC-POS-030`; `TC-E2E-005`. Unit-test coverage in `PosSaleServiceImplTest` (9 new cases): happy-path RETURN_IN snap, idempotency, different-day rejection, batch-tracked rejection, tender ≠ total rejection, above-threshold no-supervisor rejection, supervisor-missing-permission 403, supervisor-approved happy path, already-VOIDED original rejection.

---

## F5.6 — FX tender

**Story:** US-POS-021 · **Size:** M · **Status:** `[x]` backend (Flutter tender screen deferred)
**Dependencies:** F5.2, F1.2 (currencies).

**Backend:**
- [x] `pos_payment.tender_currency / tender_amount / fx_rate_snapshot` (V39). `pos_payment.amount` re-interpreted as the functional-currency-converted value. `till_currency` table (composite PK `(till_id, currency_code)`) records which foreign currencies a till accepts; functional currency is implicit.
- [x] Service-side validation: foreign tender currency must exist on the till (`till_currency` row); most-recent `fx_rate` quote with `effective_at ≤ saleAt` must exist (else reject); `amount = tenderAmount × fxRateSnapshot`; functional total ≥ sum(amount) for sales, == for refunds. Functional-currency tender (or omitted `tenderCurrency`) bypasses both the till-accepts check and the FX-rate lookup at rate 1.
- [x] `TillCurrencyService` CRUD: `GET/POST/DELETE /api/v1/tills/{tillId}/currencies[/{code}]` gated by `POS.TILL_CURRENCY_MANAGE` (V39 seeds permission 47). Rejects re-adding the functional currency (implicit-only).
- [x] PosPaymentDto, PostPosSaleRequestDto.Payment, PostPosRefundRequestDto.Payment all carry `tenderCurrency` (optional — defaults to functional).

**Flutter POS:**
- [ ] Tender screen accepts multiple currencies — deferred to the Flutter mini-phase.

**Tests:** `TC-POS-031` .. `TC-POS-035`; `TC-E2E-006`, `TC-E2E-007`. Unit-test coverage: 5 new cases in `PosSaleServiceImplTest` (USD→TZS happy path with snapshot, currency-not-accepted rejection, no-rate rejection, explicit functional-currency tender skips FX, mixed FX + functional sums correctly) + 7 in `TillCurrencyServiceImplTest`.

---

## F5.7 — Gift card tender (depends on F7.1)

Skip until F7.1 lands.

---

## F5.8 — Weighed items + barcode parser

**Story:** US-CAT-016, US-POS-003 (extended) · **Size:** M · **Status:** `[~]` (backend done, Flutter deferred)
**Dependencies:** F1.6, F5.2.

**Backend:**
- [x] `EmbeddedWeightDecoder` (server-side) parses EAN-13 starting with `2` (7-char prefix `'2' + 6-digit PLU` + 5-digit weight + EAN check digit; weight/1000 in the item's WeighingUnit).
- [x] `BarcodeResolverService` resolves a raw scan to `ResolvedBarcodeDto` — exact-match first against `item_barcode.barcode`, then 7-char prefix fallback restricted to `barcode_type = EMBEDDED_WEIGHT`. Rejects zero-weight scans, archived items, and cross-company items.
- [x] `GET /api/v1/pos/barcode-lookup?code=...` gated by `POS.SALE_POST` or `POS.MANAGE_TILL` (held by the till app already).
- [x] `ItemBarcodeServiceImpl.addBarcode` shape-checks each symbology — EAN13=13 digits, EAN8=8, UPC=12, EMBEDDED_WEIGHT=7 leading-`2` digits and requires the item to be `is_weighed`.
- [x] Unit tests — `EmbeddedWeightDecoder` covered via `BarcodeResolverServiceImplTest` (8 cases: exact / prefix / zero-weight / unknown PLU / unknown plain / archived / cross-company / blank); `ItemBarcodeServiceImplTest` extended with EAN13-length + EMBEDDED_WEIGHT happy / non-weighed / bad-prefix.

**Flutter POS:**
- [ ] On-device `EmbeddedWeightDecoder` so offline scans resolve from the catalog snapshot.
- [ ] Scale integration via platform channel (USB / serial).

**Tests:** `TC-CAT-013`; `TC-POS-039`; `TC-E2E-008` (backend halves covered; e2e + Flutter halves pending the Flutter mini-phase).

---

## F5.9 — Cash pickup + petty cash

**Story:** US-POS-013, US-POS-014 · **Size:** S · **Status:** `[ ]`
**Dependencies:** F5.1, F6.1.

**Tests:** `TC-POS-016` .. `TC-POS-018`.

---

## F5.10 — X / Z reports

**Story:** US-POS-015, US-POS-016 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F5.2, F5.9.

PDF generation, object-store upload on Z.

**Tests:** `TC-POS-019`, `TC-POS-020`; `TC-NFR-PERF-005`.

---

# Phase 6 — Money ledger (cash module)

## F6.1 — Cash entries + cash book (single currency)

**Story:** scattered across procurement, sales, pos · **Size:** L · **Status:** `[~]` (backend done, Flutter / web / direct-write endpoints deferred)
**Dependencies:** F2.1.

**Backend:**
- [x] `CashEntry` append-only entity (DATA-MODEL §10.2) + `CashBook` projection entity (§10.3) + repos; `CashAccount` / `CashDirection` / `GlCategory` enums; `CashRefType` string constants.
- [x] `V40` schema + `V40_1` per-dialect sequence + `V41` permission seed (`CASH.READ` / `CASH.ADJUST` / `CASH.BANKING`).
- [x] `CashLedgerService.post(...)` is the posting port — caller-side typed entry; idempotency UNIQUE on `(ref_type, ref_id, direction)` so a replayed producer call returns the existing row without re-inserting; `CashBook` projection updated write-through in the same transaction; emits `CashEntryPosted.v1` + `CashBookBalanceUpdated.v1`.
- [x] **Direct-call producer wiring** (synchronous, same-tx — replacing the planned event-consumer pattern, which would need outbox-poll infra we don't yet have):
  - `PosSaleServiceImpl.post` → IN-TILL per CASH `pos_payment` row (`PosSalePayment` / `CASH`).
  - `PosSaleServiceImpl.refund` → OUT-TILL per CASH refund payment row (`PosRefundPayment` / `CASH_REFUND`).
  - `PosSaleServiceImpl.voidSale` → OUT-TILL reversing the original CASH rows (`PosVoidPayment` / `CASH_REFUND`).
  - `TillSessionServiceImpl.open` → IN-TILL opening float (`TillFloat` / `TILL_FLOAT`).
  - `TillSessionServiceImpl.close` → variance entry on non-zero variance (surplus = IN, shortage = OUT; `TillVariance` / `VARIANCE`).
  - `SalesReceiptServiceImpl.post` → IN on the method-mapped account (CASH→CASH_BOX, MOBILE_MONEY→MOBILE_MONEY, BANK_TRANSFER/CHEQUE→BANK; CARD + STORE_CREDIT settle off-ledger) (`SalesReceipt` / `RECEIPT`).
  - `SupplierPaymentServiceImpl.post` → OUT on the method-mapped account (`SupplierPayment` / `SUPPLIER_SETTLEMENT`).
- [x] Read endpoints: `GET /api/v1/cash-entries?branchId&account&businessDate` + `GET /api/v1/cash-book?branchId&businessDate` (gated by `CASH.READ`).
- [x] Unit tests — `CashLedgerServiceImplTest` (6: save+events, idempotency no-op, OUT decrements book, zero-amount rejected, second-IN appends to existing book, findByRef-empty); existing pos/sales/procurement tests updated to mock the new `CashLedgerService` (+ `CompanyRepository` for TillSession).
- [ ] **Deferred to a follow-on slice:** direct write endpoints `POST /api/v1/cash-adjustments` (gated `CASH.ADJUST`) + `POST /api/v1/bank-deposits` (gated `CASH.BANKING`) — they need their own audit-doc tables (`cash_adjustment`, `bank_deposit`) before a stable `ref_id` is available.
- [ ] **Deferred to F5.9 / F6.3:** cash pickup + petty cash consumers (need the F5.9 events first); banking + supervisor adjustment (F6.3).
- [ ] **Deferred to F6.2:** multi-currency cash book (composite-PK extension on `cash_book`).

**Web:**
- [ ] `/cash/ledger`, `/cash/cash-book` — deferred.

**Tests:** `TC-CASH-001` .. `TC-CASH-016`. Backend covered by `CashLedgerServiceImplTest`; integration / e2e tests pending.

---

## F6.2 — Multi-currency cash book

**Story:** US-DAY-006 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F6.1, F5.6.

**Backend:**
- [ ] Composite PK extension; per-currency variance.

**Tests:** `TC-CASH-017` .. `TC-CASH-019`; `TC-E2E-007`.

---

## F6.3 — End-of-day banking + supervisor adjustment

**Story:** US-DAY-002 (banking side) · **Size:** S · **Status:** `[ ]`
**Dependencies:** F6.1, F2.1.

**Tests:** `TC-CASH-012` .. `TC-CASH-014`.

---

# Phase 7 — Phase 1.1 extensions

## F7.1 — Gift cards

**Story:** US-GC-001 .. US-GC-008 · **Size:** L · **Status:** `[ ]`
**Dependencies:** F5.2, F6.1.

**Backend:**
- [ ] `GiftCard`, `GiftCardTxn` entities.
- [ ] `GiftCardService` + Impl: issue, redeem, refund, freeze, unfreeze, expire.
- [ ] Scheduled expiry job.

**Web:**
- [ ] `/gift-cards` admin issuance + balance lookup.

**Flutter POS:**
- [ ] Issue-card flow.
- [ ] Redeem-card tender option (in F5.7).

**Tests:** `TC-GC-001` .. `TC-GC-031`; `TC-E2E-015`, `TC-E2E-016`.

---

## F7.2 — Layby + pre-order

**Story:** US-ORD-001 .. US-ORD-009 · **Size:** L · **Status:** `[ ]`
**Dependencies:** F2.2 (stock reservation), F6.1.

**Backend:**
- [ ] `CustomerOrder`, `CustomerOrderLine`, `CustomerOrderPayment` entities.
- [ ] State machine + reservation events.
- [ ] Pre-order triggers `production_batch` via `production` consumer.
- [ ] Expiry job.

**Web:**
- [ ] `/orders` flow.

**Flutter POS:**
- [ ] Layby collection at till — scan order number.

**Tests:** `TC-ORD-001` .. `TC-ORD-024`; `TC-E2E-013`, `TC-E2E-014`.

---

## F7.3 — Production: BOM + batch lifecycle

**Story:** US-PROD-001 .. US-PROD-010 · **Size:** XL · **Status:** `[ ]`
**Dependencies:** F1.3, F2.2, F1.1 (sections).

Split into:
- **F7.3a** BOM + sub-recipes.
- **F7.3b** Plan + start + record output (PROD_CONSUME / PROD_OUTPUT moves).
- **F7.3c** Lifecycle states + wastage.

**Tests:** `TC-PROD-001` .. `TC-PROD-028`; `TC-E2E-011`, `TC-E2E-012`.

---

## F7.4 — Production wastage + conversions

**Story:** US-PROD-007, US-PROD-009 · **Size:** M · **Status:** `[ ]`
**Dependencies:** F7.3.

**Tests:** `TC-PROD-012` .. `TC-PROD-020`.

---

## F7.5 — EOD orchestration (gates + auto-roll + Z-report)

**Story:** US-DAY-002 · **Size:** L · **Status:** `[ ]`
**Dependencies:** F2.1, F5.10, F6.3.

**Tests:** `TC-DAY-006` .. `TC-DAY-010`; `TC-E2E-017`.

---

# Phase 8 — Reporting + hardening

## F8.1 — Stock reporting (card, fast/slow, negative)
**Stories:** US-RPT-004, US-RPT-005, US-RPT-006 · **Size:** M · **Status:** `[ ]`

## F8.2 — Sales reporting (daily, Z-history, margin)
**Stories:** US-RPT-001, US-RPT-002, US-RPT-003 · **Size:** M · **Status:** `[ ]`

## F8.3 — Section P&L
**Story:** US-RPT-011 · **Size:** M · **Status:** `[ ]`

## F8.4 — Production yield + wastage
**Story:** US-RPT-012 · **Size:** S · **Status:** `[ ]`

## F8.5 — Gift card outstanding liability
**Story:** US-RPT-013 · **Size:** S · **Status:** `[ ]`

## F8.6 — Layby ageing
**Story:** US-RPT-014 · **Size:** S · **Status:** `[ ]`

## F8.7 — Customer / supplier statements
**Story:** US-RPT-007 · **Size:** M · **Status:** `[ ]`

## F8.8 — VAT return export
**Story:** US-NFR-COMP-001 · **Size:** M · **Status:** `[ ]`

## F8.9 — Biometric enrolment + login (defer)
**Story:** US-IAM-011, US-IAM-012 · **Size:** M · **Status:** `[ ]`

## F8.10 — WMS module (separate plan; out of current scope)

---

# Cross-cutting work (parallel to phases)

| Feature | Where | Notes |
|---|---|---|
| **Outbox dispatcher hardening** | `common.OutboxDispatcher` | Already scaffolded. Verify dead-letter behaviour under load. Tests: `TC-COMMON-010` .. `TC-COMMON-015`. |
| **Audit chain implementation** | `common.AuditLogWriterImpl` | TODO marker exists. Hash-chained write + verifier job. Tests: `TC-COMMON-004`, `TC-COMMON-005`, `TC-NFR-SEC-008`, `TC-NFR-SEC-009`. |
| **CI matrix: MySQL + Postgres** | `.github/workflows/` | Run every PR. Enforces DB-agnostic correctness. |
| **Migration smoke test** | `TC-E2E-032` | Spins both stacks weekly; diffs sample queries. |
| **Performance baseline** | `TC-NFR-PERF-001` .. `005` | After F5.2 lands, capture p95 baseline; track regressions. |
| **Security audit** | `TC-NFR-SEC-*` | Before pilot launch. |

---

# Recommended ordering (the actual week-by-week)

Treating each `L` as ~10 working days, an `M` as ~4, an `S` as ~1.5 — and one developer working solo:

| Week(s) | Slice |
|---|---|
| 1 | **Thin foundation slice:** F0.1 + F0.2 web wiring (login + admin can boot up an empty DB). |
| 2 | F0.3 + F0.4 + F0.5 (refresh, RBAC, branch switch). |
| 3-4 | F1.1 + F1.2 (branches, sections, currency). |
| 5-6 | F1.3 + F1.4 + F1.5 + F1.6 (catalog full). |
| 7-8 | F1.7 (party). |
| 9 | F2.1 (business day). |
| 10-11 | F2.2 + F2.5 (stock ledger + adjustments). |
| 12 | F2.3 (counts + transfers — defer if time pressure). |
| 13-14 | F2.4 (batches + FEFO). |
| 15-17 | F3.1 + F3.2 (LPO + GRN — biggest inbound feature). |
| 18-19 | F3.3 + F3.4 (invoice match + payment). |
| 20-21 | F4.2 + F4.3 (sales invoice + receipt). |
| 22-23 | F5.1 + F5.2 (till + basic sale — Flutter heavy). |
| 24-25 | F5.4 (offline sync — big risk; allocate buffer). |
| 26 | F5.3 + F5.9 + F5.10 (discounts, pickup, Z). |
| 27 | F5.5 (refund). |
| 28 | F5.6 + F6.2 (FX tender + multi-currency book). |
| 29-30 | F6.1 + F6.3 (cash module wiring — runs alongside earlier work since other modules emit events from week 14 onward). |
| 31-32 | F7.1 + F5.7 (gift cards). |
| 33-34 | F7.2 (layby / pre-order). |
| 35-38 | F7.3 (production). |
| 39 | F7.5 (EOD orchestration). |
| 40 | F8.1 + F8.2 (essential reports). |
| 41+ | F8.3 onwards as needed. |

**Pilot launch target:** end of Week 32 (gift cards + everything below it for one branch). Production extension (F7.3) can join during pilot stabilisation.

---

*This plan is the source of truth for delivery order. Update statuses as features close; raise blockers immediately when discovered.*
