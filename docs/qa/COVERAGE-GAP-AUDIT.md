# QA Coverage-Gap Audit

Audit of the test catalog (`docs/qa/test-cases/`, 163 cases) against the **actual** menus / submenus / routes / endpoints of web, POS, and API. Generated 2026-05-30.

## Headline

**237 features** across the three surfaces: **86 COVERED** (36%), **79 PARTIAL** (33%), **72 GAP** (30%).

| Surface | Features | Covered | Partial | Gap |
|---|---|---|---|---|
| WEB | 62 | 19 | 27 | 16 |
| POS | 52 | 14 | 17 | 21 |
| API | 123 | 53 | 35 | 35 |

## Prioritized gaps

- **[P0/web]** Stock — Transfers (/stock/transfers)
  - _why:_ Inter-branch stock transfers directly affect inventory accuracy across branches. This is a transactional operation that decrements one branch and credits another, yet transfers.component.ts has zero TC coverage at any level — no API test, no UI test, no axe gate. A bug here causes silent stock discrepancies that are hard to reconcile post-facto.
  - _add:_ TC-STOCK-008: POST /stock-transfers with valid source/dest branch, item, qty — verify TRANSFER_OUT at source and TRANSFER_IN at dest; stock balances correct at both branches. TC-STOCK-009: Transfer attempt when source has insufficient stock is rejected. TC-STOCK-010 (web e2e): Navigate to /stock/transfers, fill form with item and branch pickers, submit, verify success state + balances update. axe gate on the screen.
- **[P0/web]** Stock — Stock Counts (/stock/counts)
  - _why:_ Stock counts are the mechanism for reconciling physical vs system inventory. Zero TC exists. Any bug in count submission logic could silently corrupt qty_on_hand for every item counted — the most consequential stock operation outside of GRN.
  - _add:_ TC-STOCK-COUNT-001: Create a stock count sheet for a branch, submit counted quantities, post — verify ADJUSTMENT moves for variance items. TC-STOCK-COUNT-002: Count posted on closed business day is blocked. TC-STOCK-COUNT-003 (web e2e): Navigate to /stock/counts, create count, enter counts via item picker rows, post. axe gate.
- **[P0/pos]** Offline login with cached session (DioException fallback)
  - _why:_ If the server is unreachable, the cashier must still be able to log in using the previously-stored session. This is the first line of the offline-first guarantee. A regression here locks out every cashier on connectivity loss. The code path exists (login_screen.dart DioException branch) but is completely untested.
  - _add:_ TC-POS-AUTH-001: Simulate DioException on authRepo.login(); assert that when tokenStore.hasAnySession=true and username matches stored session, the app navigates to /till/open. TC-POS-AUTH-002: Same scenario with username mismatch; assert error message 'Cannot reach server. Connect to the network or use the last logged-in account.' is shown.
- **[P0/pos]** Offline → reconnect → outbox drain (full loop integration test)
  - _why:_ The POS's core promise is: take a sale offline, sync when back online. No test exercises the complete Flutter-layer loop: (1) record sale to Drift + outbox while offline, (2) simulate reconnect, (3) assert outbox flushes and server acknowledges. Without this, offline regressions are invisible until deployment.
  - _add:_ TC-POS-RELI-001: Flutter integration test — mock Dio to fail for 3 sales, then succeed; assert outbox table is empty after flush and 3 pos_sale rows exist on the server. TC-POS-RELI-002: Repeat with a CASH_PICKUP and PETTY_CASH op in the outbox to verify all op types drain correctly.
- **[P0/pos]** PETTY_CASH sync push op idempotency
  - _why:_ TC-POS-SYNC-007 covers CASH_PICKUP idempotency but PETTY_CASH is a separate op type. A double-push of a PETTY_CASH op would double-deduct from the expected drawer balance, corrupt the till-close manifest, and create incorrect cash ledger entries. This is a money-integrity gap.
  - _add:_ TC-POS-SYNC-021: Push PETTY_CASH op twice with same clientOpId; assert second verdict=DUPLICATE; assert single cash_entry OUT row for the session; assert pettyCashTotal unchanged on second push.
- **[P0/pos]** TILL_SESSION_CLOSE sync push op idempotency
  - _why:_ If the till-close outbox op is pushed twice (network retry after timeout), a second session close must be a no-op. A second close could produce a second Z-report, corrupt expected-cash totals, or change session status. No test covers this path.
  - _add:_ TC-POS-SYNC-022: Push TILL_SESSION_CLOSE op twice with same clientOpId; assert second verdict=DUPLICATE; assert till_session.status=CLOSED (not re-transitioned); assert single Z-report entry.
- **[P0/api]** Vendor return + vendor credit note lifecycle (POST /api/v1/vendor-returns, /post, /cancel, /issue-credit-note, /vendor-credit-notes/uid/{uid}/apply)
  - _why:_ Mutates stock (RETURN_OUT move) and AP debt (credit note reduces outstanding payable). A defect here silently inflates or deflates supplier balances. The entire VendorReturnController has zero TCs — no create, no post, no credit-note issue, no credit-note apply.
  - _add:_ TC-PROC-008: POST vendor-return against posted GRN, verify RETURN_OUT stock move. TC-PROC-009: issue credit note on posted return, verify AP debt_entry reduced. TC-PROC-010: apply vendor credit note to open supplier invoice, verify partial+full settlement. TC-PROC-011: cancel vendor return in DRAFT before posting, verify no stock move. TC-PROC-012: attempt cancel on POSTED vendor return without GRN.CANCEL — expect 403.
- **[P0/api]** Customer credit note application (POST /api/v1/customer-credit-notes/uid/{uid}/apply)
  - _why:_ Reduces AR debt on an open sales invoice. Incorrect allocation silently corrupts customer outstanding balances and debt ageing. TC-SALES-005 covers return + issue-credit-note but the apply step is absent.
  - _add:_ TC-SALES-009: apply a customer credit note to an open invoice for the same customer; verify invoice amountDue decreases by applied amount and credit note balance decreases. TC-SALES-010: apply credit note to invoice from a different customer — expect 422. TC-SALES-011: over-apply credit note (amount > outstanding) — expect 422.
- **[P0/api]** Stock transfer lifecycle (POST /api/v1/stock-transfers, /issue, /receive, /close)
  - _why:_ Creates paired TRANSFER_OUT / TRANSFER_IN stock moves across branches. No TC exists for the entire StockTransferController. A defect causes phantom stock gains or losses and violates multi-tenant branch balances.
  - _add:_ TC-STOCK-008: create transfer, issue, verify TRANSFER_OUT at source branch; receive (partial), verify TRANSFER_IN at destination and partial qty accounted. TC-STOCK-009: close transfer with unresolved difference, verify discrepancy move or rejection. TC-STOCK-010: multi-tenant — branch-A user cannot receive a transfer issued from branch-B (403).
- **[P0/api]** Physical stock count lifecycle (POST /api/v1/stock-counts, /start, /counts, /close, /post)
  - _why:_ Posting a count generates ADJUSTMENT stock moves for every variance found. Incorrect posting corrupts on-hand balances for the whole branch. No TC exists for any StockCountController endpoint.
  - _add:_ TC-STOCK-011: create count, start, record counts for 3 items (one overage, one shortage, one exact), close, post; verify ADJUSTMENT moves match variances. TC-STOCK-012: attempt to post count with unclosed till session open — expect 422 blocker. TC-STOCK-013: post count with zero variance, verify no stock_move generated.
- **[P0/api]** Supplier AP debt surface (GET /api/v1/debt/supplier-aging, /supplier-dunning, /supplier/uid/{uid})
  - _why:_ AP ageing and supplier statement are the primary tool for managing payables. No TC covers any endpoint in SupplierDebtController. Silently wrong ageing buckets could misrepresent outstanding payables.
  - _add:_ TC-DEBT-006: supplier-aging returns correct bucket totals for a supplier with invoices at various ages. TC-DEBT-007: supplier statement uid/{uid} returns open invoices, credit notes, payments. TC-DEBT-008: supplier-dunning page lists only suppliers above zero balance ordered by oldest bucket.
- **[P0/api]** RBAC authoring — roles, permissions, grants (GET/POST/PATCH/DELETE /api/v1/roles + /permissions + /grants)
  - _why:_ Entire RoleAdminController has zero TCs. Creating a role with wrong permissions or granting to wrong user directly undermines multi-tenant security. The ability to revoke or spoof grants is especially high-risk.
  - _add:_ TC-IAM-001: create role, set permissions, grant to user, verify new JWT carries the granted perms. TC-IAM-002: revoke grant, user must refresh to lose the permission. TC-IAM-003: delete role with active grants — expect 409. TC-IAM-004: non-IAM.MANAGE_ROLES user cannot POST /roles — expect 403.
- **[P1/web]** Business Day — Day Overrides (/day/overrides)
  - _why:_ day-overrides.component.ts is a live, navigable screen in the shell. Overrides presumably allow posting outside the normal business-day gate. If overrides can be created without proper auth, that bypasses DayGuard — the cross-module gating control. Currently completely untested.
  - _add:_ TC-DAY-008: Describe what a day override is and document the override creation API endpoint. TC-DAY-009: User without DAY.OVERRIDE permission cannot create an override. TC-DAY-010 (web e2e): Navigate to /day/overrides, verify permission-gated access, test override creation form.
- **[P1/web]** Sales — Packing Lists (/sales/packing-lists)
  - _why:_ Packing lists are a shipping/dispatch document in the sales workflow. The screen is live and navigable but has zero TC. If packing list creation has bugs it silently omits dispatch evidence.
  - _add:_ TC-SALES-009: POST /packing-lists against a posted invoice — verify packing list created with correct line items. TC-SALES-010 (web e2e): Navigate to /sales/packing-lists, create from a posted invoice, verify PDF generation or printable view. axe gate.
- **[P1/web]** Procurement — Vendor Returns (/procurement/vendor-returns, /procurement/vendor-returns/new)
  - _why:_ Vendor returns have a full create screen (vendor-return-create.component.ts) plus a credit-note application modal (vendor-credit-note-apply-modal.component.ts). Both the GRN-reversal API flow and the credit-note-against-invoice application flow are untested. AP errors here cause supplier balance discrepancies.
  - _add:_ TC-PROC-008: POST /vendor-returns against a GRN — verify RETURN stock move and vendor credit note created. TC-PROC-009: Apply vendor credit note to an open supplier invoice — verify AP balance reduced. TC-PROC-010 (web e2e): Navigate to /procurement/vendor-returns/new, fill GRN picker and lines, submit. axe gate.
- **[P1/web]** Admin — Currencies + FX Rates (/admin/currencies, /admin/fx-rates)
  - _why:_ Multi-currency is a live feature in admin. Incorrect FX rate setup affects all cross-currency transactions across the system. Both currency-admin.component.ts and fx-rate-admin.component.ts have zero TC — no creation, no uniqueness check, no rate-validity window test.
  - _add:_ TC-ADMIN-006: POST /currencies creates currency with unique code; duplicate code rejected 409. TC-ADMIN-007: POST /fx-rates with from/to currency and effective date; rate is used in downstream conversion. TC-ADMIN-008 (web e2e): Navigate /admin/currencies and /admin/fx-rates, create entries, verify in table. axe gates.
- **[P1/web]** Reports — Z-History (/reports/z-history)
  - _why:_ Z-history shows closed till sessions and Z-report totals — the primary daily reconciliation tool for managers. No TC exists for the API endpoint or the web screen. If the Z-report calculation is wrong (e.g. session totals, variance) it goes undetected.
  - _add:_ TC-RPT-007: GET /reports/z-history?branchId=1&from=&to= returns closed till sessions with correct sales total, expected cash, and variance. TC-RPT-008: Z-report totals tie to sum of individual cash entries in the session. TC-RPT-009 (web e2e): Navigate /reports/z-history, filter by date range, verify table.
- **[P1/web]** Reports — Stock Movers (/reports/stock-movers)
  - _why:_ Stock movers (fast/slow moving items) is a buying-decision report. The API endpoint (/stock-report/movers) has no TC in TC-REPORTS at all, and the web screen stock-movers.component.ts has no e2e coverage.
  - _add:_ TC-RPT-010: GET /stock-report/movers?branchId=1&from=&to=&top=10 returns top items sorted by qty moved descending. TC-RPT-011: Empty date range returns empty list, not 500. TC-RPT-012 (web e2e): Navigate /reports/stock-movers, set item picker + date range, verify table.
- **[P1/web]** Admin — POS Sales viewer (/admin/pos-sales)
  - _why:_ Back-office POS sales viewer is the manager's read-only window into till transactions. It is gated by POS.VIEW permission. Zero TC exists — no permission gate test, no filter test, no pagination test, no axe gate.
  - _add:_ TC-ADMIN-009: User without POS.VIEW cannot access /admin/pos-sales (403). TC-ADMIN-010: GET /admin/pos-sales returns POS sales for active branch, paginated, newest first. TC-ADMIN-011 (web e2e): Navigate /admin/pos-sales, apply date/cashier filters, verify sale rows. axe gate.
- **[P1/pos]** X-report expected drawer cash — incorrect accumulation (only last sale, not all sales)
  - _why:_ The x_report_screen.dart source comment says 'TODO: once the sell path is fully backed by the DB (not mocks), replace the mock-session tender breakdown with an outbox query.' Currently cashSales = lastSale's cash tender only. After a second sale the expected drawer is wrong. This will confuse cashiers doing mid-shift checks and lead to incorrect variance at close.
  - _add:_ TC-POS-XRPT-001: Record 3 cash sales of 1000 TZS each; open X-report; assert 'Gross sales' = 3000 TZS and 'Cash sales' = 3000 TZS (not 1000 from the last sale only). TC-POS-XRPT-002: Same with a mixed-tender sale; assert only the cash portion is counted in expected drawer.
- **[P1/pos]** Settings 'Push now' button does not actually call outboxDispatcher.flush()
  - _why:_ The Push now button in settings_screen.dart does Future.delayed(700ms) and shows a snackbar. It does NOT invoke ref.read(outboxDispatcherProvider).flush(). Cashiers who use this button to force a sync before close will believe ops are pushed when they are not. This is a silent data-loss scenario at till close.
  - _add:_ TC-POS-SETTINGS-001: Widget test — tap 'Push now'; assert outboxDispatcher.flush() is called (mock the provider); assert snackbar shows 'Manual push triggered' only after flush completes. Fix: wire the button to the actual dispatcher as in till_open_screen.dart.
- **[P1/pos]** Supervisor PIN screen (/supervisor) does not gate any real action
  - _why:_ SupervisorPinScreen at /supervisor shows 'You may now perform a void, discount, or refund on this sale' but does not return a token or set any state that the cart/payment screens check. The discount dialog shows a warning at ≥15% but does not actually call the supervisor screen. The two supervisor flows (standalone route and inline refund PIN) are disconnected. No test exposes this.
  - _add:_ TC-POS-SUPER-001: Navigate to /supervisor; enter PIN 1234; assert returned state is usable by the cart (e.g. a supervisorGrantedProvider becomes true). TC-POS-SUPER-002: Apply a 20% discount without supervisor grant; assert Pay button is blocked. TC-POS-SUPER-003: Apply 20% discount after supervisor grant; assert Pay button is enabled.
- **[P1/pos]** Mode-specific customer requirement gating (pharmacy and wholesale)
  - _why:_ Pharmacy and wholesale modes require a named customer before payment. The Pay button is disabled when missingCustomer=true, but there is no test verifying this gate for either mode. A regression could allow walk-in sales in modes that require customer identity (compliance risk for pharmacy).
  - _add:_ TC-POS-CART-001: In pharmacy mode with walk-in customer, assert Pay button is disabled and 'Customer required' message visible. TC-POS-CART-002: Pick a named customer; assert Pay button becomes enabled. TC-POS-CART-003: Same sequence for wholesale mode.
- **[P1/api]** Internal consumption draw (POST /api/v1/internal-consumption)
  - _why:_ Writes INTERNAL_CONSUMPTION stock move, reducing on-hand without a customer or sales document. Entirely untested. Incorrect quantity reduces stock silently.
  - _add:_ TC-STOCK-014: POST internal consumption for 5 units of SOAP with reason; verify INTERNAL_CONSUMPTION move qty=-5 and balance decremented. TC-STOCK-015: consumption without STOCK.INTERNAL_CONSUMPTION permission returns 403.
- **[P1/api]** Business day override sub-resource (POST /api/v1/business-days/uid/{uid}/overrides, /archive)
  - _why:_ Overrides allow transactions on a closed business day — a special-access bypass with audit importance. The posting and archiving of overrides have no TCs.
  - _add:_ TC-DAY-008: post a business-day override for a CLOSED day; verify the target entity (e.g. a GRN) can now be posted against that day. TC-DAY-009: archive an active override, verify access reverts. TC-DAY-010: user without DAY.OVERRIDE cannot post override — expect 403.
- **[P1/api]** VAT return report (GET /api/v1/reports/vat-return)
  - _why:_ This is a fiscal compliance output. Incorrect VAT grouping or incorrect period defaults directly affect TRA filing. Currently has no TC despite being classified as US-NFR-COMP-001.
  - _add:_ TC-RPT-007: VAT return for a period with known sales; verify per-VAT-group output tax matches sum of (unitPrice * vatRate * qty) for all invoices in range. TC-RPT-008: VAT-exempt items produce zero output tax in the return. TC-RPT-009: period default (omit from/to) returns previous calendar month.
- **[P1/api]** Sync: bootstrap endpoint + till-session/close handshake + contract version negotiation
  - _why:_ Bootstrap is the first call a fresh/reinstalled POS device makes — if it fails or returns stale data the device cannot operate. till-session/close handshake is the only server-authorised mechanism to reconcile offline sessions. Contract-version mismatch (426/409) is the only guardrail preventing protocol divergence.
  - _add:_ TC-POS-SYNC-021: GET /sync/bootstrap returns full catalog+price+balance snapshot and a non-null nextCursor. TC-POS-SYNC-022: POST /sync/till-session/close with matching manifest returns CLOSED; with manifest mismatch returns RECONCILE_INCOMPLETE. TC-POS-SYNC-023: push with X-Orbix-Contract-Version too old returns 426; too new returns 409.
- **[P1/api]** Till master data CRUD + till currency management
  - _why:_ Tills must exist before sessions can open. No TC covers till create, update, activate/deactivate, or FX currency attachment. An error in till setup silently blocks all POS operations.
  - _add:_ TC-POS-CORE-021: POST /tills creates till, GET confirms; PATCH renames till; deactivate blocks session open. TC-POS-CORE-022: add USD currency to till; GET /tills/{id}/currencies lists it; remove currency; list confirms empty.
- **[P1/api]** Packing list lifecycle (POST /api/v1/packing-lists, /dispatch, /deliver, /cancel)
  - _why:_ PackingListController (F4.5 — bulk dispatch) is entirely untested. Dispatching moves stock or locks it for delivery; a defect corrupts inventory for field deliveries.
  - _add:_ TC-SALES-009: create packing list from sales invoice, dispatch, verify stock reservation; deliver, verify stock deducted and invoice updated. TC-SALES-010: cancel packing list before dispatch, verify no stock move.
- **[P1/api]** Session management — GET /api/v1/auth/sessions + POST /logout-everywhere
  - _why:_ Auth sessions endpoint and logout-everywhere are the user's only tools to audit and terminate rogue active sessions. Missing TCs mean a persistent compromised refresh token scenario is never exercised.
  - _add:_ TC-AUTH-016: login twice; GET /auth/sessions returns 2 sessions with distinct refreshTokenIds. TC-AUTH-017: POST /auth/logout-everywhere; all refresh tokens for that user invalidated; subsequent refresh returns 401.
- **[P1/api]** Stock batch FEFO management — list, expiring-soon, recall (GET /api/v1/stock-batches, /recall)
  - _why:_ Batch recall writes a compensating RECALL stock move and marks the batch RECALLED. No TC exercises this path. Incorrect recall could leave recalled stock visible as available.
  - _add:_ TC-STOCK-016: list batches expiring within 7 days. TC-STOCK-017: recall a batch; verify RECALL stock move created, batch status=RECALLED, balance decremented. TC-STOCK-018: recall already-recalled batch returns 422.
- **[P2/web]** Parties — Employees (/party/employees)
  - _why:_ Employee records feed payroll, sales agent commissions, and HR flows. The screen is live but entirely untested. Create/edit with no coverage means regressions in party-record creation for employee type go undetected.
  - _add:_ TC-PARTY-006: POST /employees creates party+employee record; code unique per company. TC-PARTY-007 (web e2e): Navigate /party/employees, create employee, verify in list. axe gate.
- **[P2/web]** Parties — Sales Agents (/party/agents)
  - _why:_ Sales agents are assigned to invoices for commission tracking. The screen is live and untested.
  - _add:_ TC-PARTY-008: POST /sales-agents creates agent record linked to an employee party. TC-PARTY-009 (web e2e): Navigate /party/agents, create agent, assign commission rate. axe gate.
- **[P2/web]** Stock — Batches (/stock/batches)
  - _why:_ Batch tracking controls FEFO expiry logic and lot traceability. batches.component.ts is live with zero TC. If batch assignment is silently broken, FEFO picks the wrong lot for perishables.
  - _add:_ TC-STOCK-BATCH-001: POST /stock-batches assigns a GRN line to a batch with expiry date. TC-STOCK-BATCH-002: FEFO picks the earliest-expiry batch on a POS sale. TC-STOCK-BATCH-003 (web e2e): Navigate /stock/batches, create batch, assign item. axe gate.
- **[P2/web]** Stock — Internal Consumption (/stock/internal-consumption)
  - _why:_ The only live Production sub-feature. Draw forms feed the production cost model. internal-consumption.component.ts is fully built but has zero TC — the form (item picker, section picker, batch picker, category, qty) is untested.
  - _add:_ TC-STOCK-IC-001: POST /internal-consumptions creates CONSUMPTION stock move with correct category and qty. TC-STOCK-IC-002: Consumption without STOCK.ADJUST or equivalent permission rejected. TC-STOCK-IC-003 (web e2e): Navigate /stock/internal-consumption, fill item and category, submit. axe gate.
- **[P2/web]** Branch switcher (topbar shell)
  - _why:_ The branch chip in shell.component.ts reloads the entire app context on switch. If switching silently fails (error in setActiveBranch) or the wrong branch header is sent after switch, every subsequent API call operates in the wrong branch context — a multi-tenant isolation issue that is completely untested.
  - _add:_ TC-SHELL-001 (web e2e): Login as multi-branch user; open branch chip; switch to Branch 2; verify page reloads and branch chip shows Branch 2 name; verify a stock balance query returns Branch 2 data. TC-SHELL-002: Single-branch user sees chip but it is not clickable (disabled). axe gate on chip.
- **[P2/web]** Reports — Supplier Statement (/reports/supplier-statement)
  - _why:_ Supplier statement is the AP equivalent of the customer statement. The web screen supplier-statement.component.ts exists but has no TC at all. Incorrect AP running balances go undetected.
  - _add:_ TC-RPT-013: GET /reports/supplier-statement?supplierId=&from=&to= returns supplier AP statement with open invoices, payments, and total outstanding. TC-RPT-014 (web e2e): Navigate /reports/supplier-statement, pick supplier, set date range, verify totals match debt module.
- **[P2/pos]** Restaurant mode — send-to-kitchen stub (KOT not implemented)
  - _why:_ The 'Send to kitchen' button fires a snackbar. No kitchen order ticket (KOT) is printed or recorded. Table management uses hardcoded mock data. No test exists. If restaurant mode is deployed, cashiers will believe orders are sent when they are not.
  - _add:_ TC-POS-REST-001: Document the mock status; add a test that asserts the KOT snackbar appears when Send is tapped with a table selected. File a tracked issue for the KOT print integration. TC-POS-REST-002: Assert Send button is disabled when no table is selected.
- **[P2/pos]** Held carts — persistence across app restart (currently in-memory only)
  - _why:_ heldCartsProvider is a StateProvider backed by in-memory state only. If the app crashes or is restarted, all held carts are lost with no notification to the cashier. On a busy till this means silent transaction abandonment. No test covers the non-persistence, and no Drift-backed hold mechanism exists.
  - _add:_ TC-POS-HELD-001: Hold a cart; simulate app restart (provider reset); assert held cart is gone and user sees an empty held-carts screen. TC-POS-HELD-002 (future): After Drift-backed holds are implemented, assert held cart survives restart. File a gap issue: held carts must survive app crashes.
- **[P2/api]** Production wastage recording + wastage rollup report
  - _why:_ ProductionWastageController and ProductionWastageReportController both have zero TCs. Wastage reduces stock and feeds the section P&L; incorrect writes silently distort cost.
  - _add:_ TC-PROD-005: record wastage on active batch, verify stock decremented. TC-PROD-006: GET /reports/production-wastage with section filter returns correct per-category totals. TC-PROD-007: duplicate wastage record (compensating) correctly offsets prior entry.
- **[P3/web]** Admin — Routes admin (/admin/routes)
  - _why:_ Delivery routes are used for field-sales assignment (WMS). The route-admin screen is live with zero TC coverage.
  - _add:_ TC-ADMIN-012: POST /routes creates a route with name and assigned branch; duplicate name rejected. TC-ADMIN-013 (web e2e): Navigate /admin/routes, create route, verify in table. axe gate.

## WEB — full inventory

| Menu | Feature | Locator | Coverage | Cases | Note |
|---|---|---|---|---|---|
| Overview | Dashboard | `/dashboard — dashboard.component.ts` | PARTIAL | TC-RPT-003, TC-NFR-UX-002 | TC-RPT-003 covers the API data surface; TC-NFR-UX-002 covers the axe gate. No TC exercises the drill-through navigation from KPI tiles to /reports/sales-daily or verifies the branch-switcher on the dashboard. |
| Operations | Business Day — open day | `/day — day.component.ts` | COVERED | TC-DAY-001, TC-DAY-002, TC-DAY-007 |  |
| Operations | Business Day — close day | `/day — day.component.ts` | COVERED | TC-DAY-003, TC-DAY-004 |  |
| Operations | Business Day — day gating (sales/POS blocked on closed day) | `/day — day.component.ts` | COVERED | TC-DAY-005, TC-DAY-006, TC-POS-CORE-003 |  |
| Operations | Business Day — day overrides | `/day/overrides — day-overrides.component.ts` | GAP | none | day-overrides.component.ts exists as a distinct route and screen. No TC covers what overrides are, how they are created, or what they permit. The sub-route is visible from /day navigation but is entirely untested. |
| Operations | Catalog — Items list | `/catalog/items — item-list.component.ts` | COVERED | TC-CAT-001, TC-CAT-002, TC-CAT-003, TC-CAT-006, TC-CAT-009, TC-CAT-010, TC-NFR-UX-004 |  |
| Operations | Catalog — Item create/edit form | `/catalog/items/new, /catalog/items/:uid/edit — item-edit.component.ts` | COVERED | TC-CAT-001, TC-CAT-002, TC-CAT-003, TC-CAT-009, TC-NFR-UX-003, TC-NFR-UX-004 |  |
| Operations | Catalog — Barcodes panel (within item edit) | `/catalog/items/:uid/edit — barcodes-panel.component.ts` | PARTIAL | TC-CAT-004 | TC-CAT-004 covers API-level barcode creation/uniqueness. No Playwright e2e TC covers navigating to the barcodes panel in the web UI and verifying the add-barcode workflow or duplicate rejection in-browser. |
| Operations | Catalog — Price history panel (within item edit) | `/catalog/items/:uid/edit — price-history-panel.component.ts` | PARTIAL | TC-CAT-005 | TC-CAT-005 covers the API price-change-log data. No e2e TC validates the price history panel renders, that the effective-from date picker works, or that the price-close confirmation is shown to the user. |
| Operations | Catalog — Item Groups | `/catalog/groups — item-group.component.ts` | PARTIAL | TC-CAT-007, TC-CAT-008 | API-level hierarchy-depth and deletion-guard cases exist. No web UI TC covers the group CRUD form, the tree rendering, or the archive action in-browser. |
| Operations | Catalog — Units of Measure | `/catalog/uoms — uom.component.ts` | GAP | none | UoM CRUD screen exists as a distinct route. No TC at all — not API-level, not UI-level. Any UoM create/edit/delete paths are completely untested. |
| Operations | Catalog — VAT Groups | `/catalog/vat-groups — vat-group.component.ts` | PARTIAL | TC-CAT-011 | TC-CAT-011 checks rate precision via API. No TC covers the web form for creating/editing VAT groups, nor axe/UX gate for this screen. |
| Operations | Catalog — Price Lists | `/catalog/price-lists — price-list.component.ts` | PARTIAL | TC-CAT-005 | TC-CAT-005 covers price-list item update via API. No TC covers web-level create/edit of a price list header, assigning items to a price list in the UI, or deleting a price list. |
| Operations | Parties — Customers | `/party/customers — customers.component.ts` | COVERED | TC-PARTY-001, TC-PARTY-002, TC-PARTY-003, TC-PARTY-004 |  |
| Operations | Parties — Suppliers | `/party/suppliers — suppliers.component.ts` | PARTIAL | TC-PARTY-005 | Only one API-level TC for supplier create/uniqueness. No web UI TC covers the supplier create form, edit form, or axe gate for the supplier screen. |
| Operations | Parties — Employees | `/party/employees — employees.component.ts` | GAP | none | Employee CRUD screen exists with no TC coverage whatsoever — no API, no UI, no UX/axe cases. |
| Operations | Parties — Sales Agents | `/party/agents — sales-agents.component.ts` | GAP | none | Sales agent CRUD screen exists with no TC coverage at all. |
| Operations | Stock — Balances | `/stock/balances — balances.component.ts` | COVERED | TC-STOCK-001, TC-STOCK-006 |  |
| Operations | Stock — Stock Card (drill-down from balances) | `/stock/card/:itemId — stock-card.component.ts` | PARTIAL | TC-STOCK-005 | TC-STOCK-005 covers the API stock-card endpoint. No TC validates the in-browser navigation from the balances list to the stock card drill-down, or the filter/date controls on the screen. |
| Operations | Stock — Stock Counts | `/stock/counts — counts.component.ts` | GAP | none | Stock count/reconciliation screen exists but has no test coverage — no API test for the count submission workflow, no UI/e2e test. |
| Operations | Stock — Transfers | `/stock/transfers — transfers.component.ts` | GAP | none | Inter-branch transfer screen is a distinct route with zero TC coverage. |
| Operations | Stock — Batches | `/stock/batches — batches.component.ts` | GAP | none | Batch tracking screen (expiry, FEFO) is a distinct route with zero TC coverage. |
| Operations | Stock — Adjust | `/stock/adjust — adjust.component.ts` | COVERED | TC-STOCK-002, TC-STOCK-003 |  |
| Operations | Stock — Internal Consumption | `/stock/internal-consumption — internal-consumption.component.ts` | GAP | none | Distinct live screen (also linked from Production landing). No TC covers the draw form, category picker, section/batch pickers, or posting. TC-PROD-002/003 cover production stock moves at API level but not this screen's flow. |
| Operations | Production — Landing (tiles) | `/production — production.component.ts` | PARTIAL | TC-PROD-001, TC-PROD-002, TC-PROD-003, TC-PROD-004 | Production landing is a stub tile-board. Recipes/Work-orders/Yield are flagged 'SOON' in the component (status:'soon') — these routes do not exist in production.routes.ts. TC-PROD-001 through TC-PROD-004 cover the backend APIs only; the front-end for BOM and batch creation is not built. |
| Operations | Production — Recipes (BOM) screen | `/production/recipes — NOT BUILT (status:'soon' in production.component.ts)` | GAP | TC-PROD-001 | Route does not exist in production.routes.ts. TC-PROD-001 covers the API only. Web surface is intentionally unbuilt — stub. |
| Operations | Production — Work Orders screen | `/production/work-orders — NOT BUILT (status:'soon')` | GAP | TC-PROD-002 | Route absent. TC-PROD-002 covers API batch completion. Web surface is an announced stub. |
| Operations | Production — Yield Variance screen | `/production/yield — NOT BUILT (status:'soon')` | GAP | none | Route absent. No TC at all for yield variance reporting in the web. |
| Commerce | Sales — Invoices list + create/post/void | `/sales/invoices — invoices.component.ts` | COVERED | TC-SALES-001, TC-SALES-002, TC-SALES-003, TC-SALES-006, TC-SALES-007 |  |
| Commerce | Sales — Receipts list + create | `/sales/receipts — receipts.component.ts` | PARTIAL | TC-SALES-004, TC-SALES-008 | TC-SALES-004 covers API receipt creation and debt reduction. TC-SALES-008 covers allocation logic. No web UI TC validates the receipts list screen, the create-receipt form (customer picker, method dropdown), or the allocation sub-form in browser. |
| Commerce | Sales — Customer Returns list + create | `/sales/returns — returns.component.ts` | PARTIAL | TC-SALES-005 | TC-SALES-005 covers the API return/restock flow. No web UI e2e TC verifies the returns list screen or the create-return workflow in browser (original invoice picker, line entry, restock toggle). |
| Commerce | Sales — Packing Lists | `/sales/packing-lists — packing-lists.component.ts` | GAP | none | Packing list screen is a distinct route with zero TC coverage — neither API-level nor UI-level. |
| Commerce | Procurement — LPOs list + create/submit/approve | `/procurement/lpos — lpos.component.ts` | COVERED | TC-PROC-001, TC-PROC-002, TC-PROC-007 |  |
| Commerce | Procurement — GRNs list + create/post/cancel | `/procurement/grns — grns.component.ts` | COVERED | TC-PROC-003, TC-PROC-004, TC-PROC-005, TC-PROC-007 |  |
| Commerce | Procurement — Supplier Invoices list + create/post | `/procurement/invoices — invoices.component.ts` | PARTIAL | TC-PROC-003 | TC-PROC-003 step 6-8 documents the SupplierInvoice flow at API level. No web UI TC covers the supplier invoices list screen, 3-way-match creation form (GRN picker, amounts), or post action in browser. |
| Commerce | Procurement — Payments list + create | `/procurement/payments — payments.component.ts` | PARTIAL | TC-PROC-006, TC-CASH-004 | TC-PROC-006 covers API supplier payment creation and debt settlement. TC-CASH-004 covers the CASH_OUT entry. No web UI TC verifies the payments screen or the create-payment form in browser. |
| Commerce | Procurement — Vendor Returns list + create | `/procurement/vendor-returns, /procurement/vendor-returns/new — vendor-returns.component.ts, vendor-return-create.component.ts` | GAP | none | Vendor returns have a dedicated list and create screen (including vendor-credit-note-apply-modal.component.ts). No TC exists for any of these flows — API or UI level. |
| Finance | Cash — Cash Books | `/cash/books — cash-books.component.ts` | PARTIAL | TC-CASH-001, TC-CASH-002 | TC-CASH-001 and TC-CASH-002 cover API-level cash ledger correctness. No web UI TC verifies the cash books screen renders, filters work, or the axe gate passes. |
| Finance | Cash — Cash Entries | `/cash/entries — cash-entries.component.ts` | PARTIAL | TC-CASH-001, TC-CASH-002, TC-CASH-003, TC-CASH-004 | API-level coverage exists for entry creation. No web UI e2e TC covers viewing the cash entries list in browser. |
| Finance | Cash — Adjustments | `/cash/adjustments — cash-adjustments.component.ts` | PARTIAL | TC-CASH-005 | TC-CASH-005 covers the permission gate. No TC covers the adjustments form UI, the reason field requirement, or the supervisor flow in browser. |
| Finance | Cash — Bank Deposits | `/cash/bank-deposits — bank-deposits.component.ts` | PARTIAL | TC-CASH-003 | TC-CASH-003 covers API-level bank deposit creation. No web UI TC covers the bank deposits screen or the create-deposit form in browser. |
| Finance | Debt — AR/AP ageing queue landing | `/debt — debt.component.ts` | PARTIAL | TC-DEBT-001, TC-DEBT-002 | TC-DEBT-001 and TC-DEBT-002 cover the API statement and ageing endpoints. No web UI TC validates the debt landing page renders ageing buckets for the right party, or that the drill-down navigation works. |
| Finance | Debt — Customer drill-down (AR statement) | `/debt/customer/uid/:uid — debt-customer.component.ts` | PARTIAL | TC-DEBT-001, TC-DEBT-003 | API statement content covered. No e2e TC navigates to the customer drill-down page, verifies the open invoices table, recent receipts list, or the write-off request action in browser. |
| Finance | Debt — Supplier drill-down (AP statement) | `/debt/supplier/uid/:uid — debt-supplier.component.ts` | PARTIAL | TC-DEBT-003 | Supplier statement is analogous to customer. TC-DEBT-003 only mentions customer statement. No TC at all specifically covers the supplier drill-down page in the web UI. |
| Finance | Debt — Write-offs queue + dual-approval | `/debt/write-offs — debt-write-offs.component.ts` | PARTIAL | TC-DEBT-004 | TC-DEBT-004 covers the API dual-approval flow. No web UI TC covers the write-offs list screen, the write-off request modal (debt-write-off-modal.component.ts), or the approve action in browser. |
| Finance | Reports — Hub landing | `/reports — reports.component.ts` | GAP | none | The reports hub landing page has no TC — no axe gate, no navigation link validation. |
| Finance | Reports — Sales Daily (drill-through from dashboard) | `/reports/sales-daily — sales-daily.component.ts` | PARTIAL | TC-RPT-001 | TC-RPT-001 covers the API data. No e2e TC navigates the web screen, exercises the branchId/businessDate query params, or validates the invoice table renders. |
| Finance | Reports — Sales Summary (daily) | `/reports/sales-summary — sales-summary.component.ts` | PARTIAL | TC-RPT-001, TC-RPT-005 | Closest API coverage from TC-RPT-001 and TC-RPT-005. No e2e TC specifically navigates /reports/sales-summary and verifies the date picker, branch filter, and totals table in browser. |
| Finance | Reports — Z-History | `/reports/z-history — z-history.component.ts` | GAP | none | Z-history (closed till sessions) report screen has no TC whatsoever — no API test for the z-report endpoint, no UI test. |
| Finance | Reports — Stock Card | `/reports/stock-card — stock-card.component.ts (reports feature)` | PARTIAL | TC-RPT-002, TC-STOCK-005 | API data covered. No e2e TC drives the web form (item picker, branch picker, date range) and verifies the running-balance table renders. |
| Finance | Reports — Negative Stock | `/reports/negative-stock — negative-stock.component.ts` | PARTIAL | TC-RPT-004 | API endpoint covered. No e2e TC for the web screen (branch filter, table, export). |
| Finance | Reports — Stock Movers | `/reports/stock-movers — stock-movers.component.ts` | GAP | none | Stock movers report screen exists in the web but has no TC at all — no API test in TC-REPORTS for this endpoint, no UI test. |
| Finance | Reports — Customer Statement | `/reports/customer-statement — customer-statement.component.ts` | PARTIAL | TC-DEBT-003 | TC-DEBT-003 covers the API statement content. No e2e TC drives the web screen (customer picker, date range, table with running balance or separate lists). |
| Finance | Reports — Supplier Statement | `/reports/supplier-statement — supplier-statement.component.ts` | GAP | none | Supplier statement report screen exists in the web. TC-DEBT-003 only mentions customer. No TC covers this screen — API or UI. |
| Finance | Reports — Layby Ageing | `/reports/layby-ageing — layby-ageing.component.ts` | PARTIAL | TC-ORDERS-004 | TC-ORDERS-004 covers the API endpoint. No e2e TC navigates the web screen, verifies the permission gate (ORDER.READ), or checks the ageing table renders. |
| Settings | Admin — Landing hub | `/admin — admin.component.ts` | GAP | none | Admin hub landing page has no TC — no navigation test, no axe gate. |
| Settings | Admin — Users list + create/edit/deactivate | `/admin/users, /admin/users/:uid — user-admin.component.ts, user-detail.component.ts` | PARTIAL | TC-AUTH-012, TC-AUTH-013 | TC-AUTH-012 and TC-AUTH-013 cover user create and deactivate via API. No web UI TC covers the user-admin list screen, the user-detail edit form, role assignment picker, or branch-grant configuration in browser. |
| Settings | Admin — Roles + permissions | `/admin/roles — role-admin.component.ts` | PARTIAL | TC-AUTH-009, TC-AUTH-011 | Permission enforcement is tested at API level. No web UI TC covers the role-admin screen: creating a role, assigning permissions to it, or the in-browser RBAC configuration flow. |
| Settings | Admin — Audit Log | `/admin/audit — audit-log.component.ts` | PARTIAL | TC-AUTH-014, TC-ADMIN-002, TC-NFR-SEC-008 | Audit event generation is tested at API level. No web UI TC verifies the audit log screen renders the table, filters by actor/action/date, or that the axe gate passes. |
| Settings | Admin — Settings (system thresholds) | `/admin/settings — settings.component.ts` | PARTIAL | TC-ADMIN-004 | TC-ADMIN-004 covers the GET /settings API. No web UI TC covers the settings screen in browser or any editable threshold update flow. |
| Settings | Admin — Company Profile | `/admin/company — company.component.ts` | PARTIAL | TC-ADMIN-002 | TC-ADMIN-002 covers the TIN/VRN update and audit via API. No web UI TC covers the company profile form in browser. |
| Settings | Admin — Branches | `/admin/branches — branch-admin.component.ts` | PARTIAL | TC-ADMIN-003, TC-ADMIN-005 | API-level default-flag mutual-exclusion and number sequence tests exist. No web UI TC covers the branch admin screen: creating a branch, editing, setting default, or the axe gate. |
| Settings | Admin — Routes (delivery routes) | `/admin/routes — route-admin.component.ts` | GAP | none | Delivery route admin screen is a distinct route with zero TC coverage. |
| Settings | Admin — Currencies | `/admin/currencies — currency-admin.component.ts` | GAP | none | Currency admin screen exists with zero TC coverage. |
| Settings | Admin — FX Rates | `/admin/fx-rates — fx-rate-admin.component.ts` | GAP | none | FX rate admin screen exists with zero TC coverage. |
| Settings | Admin — Tills | `/admin/tills — till-admin.component.ts` | PARTIAL | TC-POS-CORE-001 | Till existence is a precondition for POS tests. No web UI TC covers the till-admin screen: creating a till, editing it, linking it to a branch/section, or axe gate. |
| Settings | Admin — POS Sales viewer | `/admin/pos-sales — pos-sales.component.ts` | GAP | none | Admin POS sales viewer (back-office read-only POS sale list, gated by POS.VIEW) has zero TC coverage. |
| Auth | Login page | `/login — login.component.ts` | COVERED | TC-AUTH-001, TC-AUTH-002, TC-AUTH-003, TC-AUTH-004, TC-NFR-UX-001 |  |
| Auth | Change Password page | `/change-password — change-password.component.ts` | PARTIAL | TC-AUTH-012 | TC-AUTH-012 covers the must-change-password flag in the API. No web UI TC navigates to /change-password, exercises the form, or validates the axe gate. |
| Auth | Branch switcher (topbar) | `shell.component.ts — branch chip dropdown` | GAP | none | The branch switcher in the topbar (visible to multi-branch users) is a distinct UX surface. No TC covers switching branches, verifying the data reloads for the new branch, or that the chip is accessible. |
| Auth | User menu — logout | `shell.component.ts — user menu / logout-btn` | PARTIAL | TC-AUTH-007 | TC-AUTH-007 covers API-level logout and token revocation. No Playwright e2e TC clicks the logout button in the shell and verifies redirect to /login. |

## POS — full inventory

| Menu | Feature | Locator | Coverage | Cases | Note |
|---|---|---|---|---|---|
| Auth | Username/password login (online) | `/login — orbix-engine-pos/lib/features/auth/login_screen.dart` | PARTIAL | TC-POS-CORE-001 (precondition), TC-POS-SYNC-020 | Auth is a precondition in every POS-CORE case but there is no dedicated Flutter-layer UI test for the login screen itself — no test verifies the error banner, empty-field guard, or the 'Connect to network' branch. |
| Auth | Offline login with cached session (DioException fallback path) | `/login — login_screen.dart _signIn() DioException branch` | GAP | none | The offline-login path (tokenStore.hasAnySession check) has no test case in the catalog. This is a P0 reliability path — a power-cycle while offline must not lock cashiers out. |
| Auth | Biometric login (mock — hardware integration pending) | `/login — login_screen.dart _biometric()` | GAP | none | Biometric is a simulated 900 ms auto-dismiss dialog. The QA README notes biometric (US-IAM-011/012) is not implemented; no TC exists. Correctly flagged as mock. |
| Till Open | Select till from dropdown (TILL-1/2/3) | `/till/open — till_open_screen.dart _tills constant` | PARTIAL | TC-POS-CORE-001 | TC-POS-CORE-001 covers the happy path via the API; no Flutter widget test exercises the till-dropdown selection itself. |
| Till Open | Mode picker (retail/supermarket/pharmacy/wholesale/restaurant) | `/till/open — till_open_screen.dart PosMode.values picker` | GAP | none | No test verifies that selecting each mode at till-open persists and changes the cart screen layout. Five distinct UX paths with no coverage. |
| Till Open | Opening float entry and validation (negative rejected) | `/till/open — till_open_screen.dart _openTill()` | PARTIAL | TC-POS-CORE-001 | TC-POS-CORE-001 covers a valid float. The negative-float guard in the UI and the zero-float edge case have no test. |
| Till Open | Duplicate open session blocked | `/till/open → API POST /api/v1/till-sessions/open` | COVERED | TC-POS-CORE-002 |  |
| Till Open | Open blocked when business day CLOSED | `/till/open → API POST /api/v1/till-sessions/open` | COVERED | TC-POS-CORE-003 |  |
| Till Open | FX currency note (UI info banner only — no FX tender at open) | `/till/open — info Container widget` | GAP | none | The screen shows 'Foreign currencies are added at close-till declaration time.' No test verifies this message is correct or that FX amounts flow into the close-till manifest. |
| Cart — Retail mode | Item tile grid — tap to add item to cart | `/cart — retail_pane.dart _ItemTile onTap` | PARTIAL | TC-POS-CORE-004 | TC-POS-CORE-004 posts a sale via API. No Flutter widget test exercises the tile-tap → cart-add flow. |
| Cart — Retail mode | Barcode scan / item-code search → add to cart | `/cart — retail_pane.dart _scan() + catalogRepo.findByBarcode()` | PARTIAL | TC-POS-CORE-019, TC-POS-CORE-020 | TC-POS-CORE-019/020 test the API barcode-lookup endpoint. No Flutter integration test drives the scan field and asserts the cart line appears. |
| Cart — Retail mode | Item with no price row shows 'No price' and is not sellable | `/cart — retail_pane.dart _ItemTile sellable guard` | GAP | none | The UI disables the tile and shows a 'No price' label when hasPriceRow=false. No test covers this guard path. |
| Cart — Retail mode | Hold cart (pause icon → dialog → heldCartsProvider) | `/cart — retail_pane.dart _hold()` | GAP | none | Held-cart park flow has no TC. The held-cart state is in-memory only (not persisted to Drift) — an important offline-first gap beyond the test coverage gap. |
| Cart — Retail mode | Catalog sync progress / error strip | `/cart — retail_pane.dart syncState.isLoading / hasError banners` | GAP | none | The 'Syncing catalog…' and 'Catalog sync failed — showing cached data' banners have no test. Offline UX state is untested at the widget layer. |
| Cart — Retail mode | First-sync empty state (no catalog yet) | `/cart — retail_pane.dart _firstSyncState()` | GAP | none | No test covers the state where items.isEmpty and sync is in progress / not yet done. |
| Cart — Supermarket mode | Barcode scan field → add line to spreadsheet table | `/cart — supermarket_pane.dart _scan()` | PARTIAL | TC-POS-CORE-019 | Barcode API tested; the spreadsheet-mode scan input interaction is not. |
| Cart — Supermarket mode | Name-filter typeahead dropdown → pick item | `/cart — supermarket_pane.dart _suggestionsDropdown()` | GAP | none | Supermarket-specific name-search dropdown has no test. |
| Cart — Supermarket mode | Code-filter column filter | `/cart — supermarket_pane.dart _codeFilter` | GAP | none |  |
| Cart — Supermarket mode | Qty stepper in spreadsheet row (inline +/–) | `/cart — supermarket_pane.dart _DataRow _qtyStepper()` | GAP | none |  |
| Cart — Pharmacy mode | Search + tile grid with batch/expiry display | `/cart — pharmacy_pane.dart mockBatchFor() shim` | GAP | none | Batch info is mock-derived (mockBatchFor). No test covers pharmacy mode; batch display is entirely unverified. |
| Cart — Pharmacy mode | Rx confirmation dialog for DR-* coded items | `/cart — pharmacy_pane.dart _addItem() _isRx() dialog` | GAP | none | Rx gate is a mock heuristic (code starts with 'DR-'). No test verifies the dialog appears or that cancelling prevents the line being added. |
| Cart — Pharmacy mode | Customer required enforcement in pharmacy mode | `/cart — cart_pane.dart customerRequired = mode == pharmacy` | GAP | none | The Pay button is disabled when customerRequired && customer.isWalkIn. No test covers this gate for pharmacy mode. |
| Cart — Wholesale mode | Qty-first numpad → select item → add to cart with fractional qty | `/cart — wholesale_pane.dart _add()` | GAP | none | No tests exist for wholesale mode. Fractional qty (decimal key) is untested. |
| Cart — Wholesale mode | Customer required enforcement in wholesale mode | `/cart — cart_pane.dart customerRequired = mode == wholesale` | GAP | none |  |
| Cart — Restaurant mode | Table picker strip (mock tables — table entity not in sync schema) | `/cart — restaurant_pane.dart mockTables / selectedTableProvider` | GAP | none | Tables are hardcoded mock data. No sync, no Drift table. The feature is explicitly noted as out of scope for US-POS-017/018. No test exists. |
| Cart — Restaurant mode | Area filter chips (Indoor/Window/Patio/Bar/Takeaway) | `/cart — restaurant_pane.dart _area filter chips` | GAP | none |  |
| Cart — Restaurant mode | 'Send to kitchen' button (mock snackbar — KOT not implemented) | `/cart — cart_pane.dart _sendToKitchen() SnackBar` | GAP | none | Send-to-kitchen is a snackbar stub. No KOT print, no kitchen order record. Correctly classified as mock/unbuilt. |
| Cart — Restaurant mode | Bill button → payment screen (requires table selected) | `/cart — cart_pane.dart _actionRow restaurant FilledButton 'Bill'` | GAP | none |  |
| Cart — Universal | Customer picker dialog (search by name/code from Drift) | `/cart — cart_pane.dart _CustomerPickerDialog / customerSearchResultsProvider` | PARTIAL | TC-POS-SYNC-014 | TC-POS-SYNC-014 tests the server-side customer pull. The Flutter picker dialog (search, select, walk-in fallback) has no widget test. |
| Cart — Universal | Line discount dialog — preset chips + free-entry + supervisor warning | `/cart — cart_pane.dart _showDiscountDialog()` | PARTIAL | TC-POS-CORE-008, TC-POS-CORE-009 | TC-POS-CORE-008/009 test the API-side threshold enforcement. The Flutter discount dialog (UI warning at ≥15%, free-entry, chip presets) is untested at the widget layer. The dialog shows a warning at 15% but the API threshold is 10% — potential inconsistency. |
| Cart — Universal | Cart line qty stepper (+/–) and removal (× button) | `/cart — cart_pane.dart _QtyStepper, IconButton close` | GAP | none | Qty stepper removing a line at qty=0 (calls setQty which calls remove) has no test. Regression risk. |
| Cart — Universal | Void cart (clear all lines) | `/cart — cart_pane.dart OutlinedButton 'Void' → cartProvider.clear()` | GAP | none |  |
| Cart — Universal | Empty cart state (empty-state widget with mode-specific hint) | `/cart — cart_pane.dart _emptyState()` | GAP | none |  |
| Payment | Single-method cash tender with change calculation | `/payment — payment_screen.dart _complete() / _recordSaleReal()` | COVERED | TC-POS-CORE-004, TC-POS-CORE-006 |  |
| Payment | Mixed-tender (cash + card/mobile) split across methods | `/payment — payment_screen.dart _tenders list` | COVERED | TC-POS-CORE-005 |  |
| Payment | Payment sum mismatch rejected (tender < total) | `/payment — payment_screen.dart _canComplete() gate` | COVERED | TC-POS-CORE-007 |  |
| Payment | Cash quick-fill buttons (round up to 5k/10k/50k) | `/payment — payment_screen.dart _roundUp() quick-fill Wrap` | GAP | none | The quick-fill buttons call _quickFill() which pre-populates the amount field. No test verifies the rounding logic for different totals. |
| Payment | Remove a committed tender line (× on tender row) | `/payment — payment_screen.dart _removeTender()` | GAP | none |  |
| Payment | Non-cash tender clamped to remaining (not over-tenderable) | `/payment — payment_screen.dart _addTender() clamp for non-cash` | GAP | none | The code clamps non-cash tender to remaining balance. This edge case has no test. |
| Payment | Outbox write when no active server session (fallback mock path) | `/payment — payment_screen.dart _recordSaleReal() else branch` | GAP | none | If activeSession.clientOpId is empty the code falls back to the mock recordSale(). This should never happen in production but is a silent data-loss path if it does. |
| Receipt | Paper receipt rendering — header, lines, tender breakdown, VAT notice | `/receipt — receipt_screen.dart _PaperReceipt` | GAP | none | No widget test verifies receipt layout, line items, or TIN display. TC-POS-CORE-004 reaches the sale but does not verify receipt content. |
| Receipt | Fiscal block (FISCALIZED / PROVISIONAL states, QR placeholder) | `/receipt — receipt_screen.dart _FiscalStatus handling` | PARTIAL | TC-FISCAL-001, TC-FISCAL-002 | TC-FISCAL-001/002 test the backend fiscal status. The Flutter receipt screen fiscal-block rendering (FISCALIZED banner, QR placeholder, PROVISIONAL italic) is not covered by a widget test. |
| Receipt | Reprint receipt (stub — snackbar only, printer not integrated) | `/receipt — receipt_screen.dart _ActionPanel OutlinedButton 'Reprint'` | GAP | none | Reprint fires a snackbar. No actual printer integration. No test. Marked as pending in code comment. |
| Receipt | Email receipt dialog (mock send) | `/receipt — receipt_screen.dart _emailDialog()` | GAP | none | Email dialog shows a snackbar on 'Send'. Mock only. |
| Receipt | Open cash drawer (stub — snackbar only) | `/receipt — receipt_screen.dart OutlinedButton 'Open drawer'` | GAP | none | Drawer kick is a snackbar stub. No platform channel / printer kick integration. No test. |
| Receipt | Start next sale (clear cart, reset customer, go /cart) | `/receipt — receipt_screen.dart FilledButton 'Start next sale'` | GAP | none |  |
| Refund | Search recent sales from Drift (by receipt no / date) | `/refund — refund_screen.dart _recentSalesProvider / _filterSales()` | PARTIAL | TC-POS-CORE-017 | TC-POS-CORE-017 tests the API refund endpoint. The Flutter refund screen's Drift-backed sale search and the filter logic are not tested at the widget/integration layer. |
| Refund | Select lines and partial qty for refund | `/refund — refund_screen.dart _refundQty map + qty stepper` | GAP | none | Partial-qty refund UI (checkbox + stepper) has no test. |
| Refund | Supervisor PIN prompt (inline _InlinePinPrompt — mock PIN 1234) | `/refund — refund_screen.dart _InlinePinPrompt` | PARTIAL | TC-POS-CORE-009 | TC-POS-CORE-009 covers the API-side supervisor token. The Flutter PIN dialog itself (correct PIN succeeds, wrong PIN clears and shows error) is not widget-tested. PIN is hardcoded '1234' — mock only. |
| Refund | Refund blocked for sale from closed business day | `/refund → API POST /api/v1/pos-sales/refund` | COVERED | TC-POS-CORE-018 |  |
| Refund | Refund enqueued in outbox (offline-capable) | `/refund — refund_screen.dart saleRepo.recordRefund() outbox write` | PARTIAL | TC-POS-SYNC-001, TC-POS-SYNC-002 | Sync push is tested at the API layer. The Flutter outbox-write path for refunds specifically is not tested. |
| Supervisor PIN | Standalone supervisor PIN screen (from cart topbar button) | `/supervisor — supervisor_pin_screen.dart` | GAP | none | SupervisorPinScreen is a standalone route navigated from the cart topbar. PIN 1234 grants authorisation (mock). The granted state does not actually gate any real action — it just shows a dialog and returns. No test exists. Different from the inline PIN prompt in refund_screen. |
| Held Carts | Recall held cart (replaces current cart, warns if active cart non-empty) | `/held — held_carts_screen.dart _recall()` | GAP | none |  |
| Held Carts | Delete held cart with confirmation dialog | `/held — held_carts_screen.dart _delete()` | GAP | none |  |
| Held Carts | Empty held-carts state | `/held — held_carts_screen.dart _empty()` | GAP | none |  |
| X-Report | X-report mid-shift read — session header, sales total, tender breakdown | `/till/x-report — x_report_screen.dart` | PARTIAL | TC-POS-CORE-001, TC-POS-CORE-014 | TC-POS-CORE-001 verifies the X-report API endpoint. The Flutter X-report screen data (sales total reads from lastSaleProvider mock, tender breakdown reads from the last completed sale only — not cumulative Drift query) is not widget-tested. Sales total is derived from mock, not DB. |
| X-Report | Cash movements section (pickup total + petty cash total from Drift outbox) | `/till/x-report — x_report_screen.dart _loadMovements()` | PARTIAL | TC-POS-CORE-012, TC-POS-CORE-013 | TC-POS-CORE-012/013 test pickup/petty via API. The Flutter X-report cash-movements aggregation from the Drift outbox is not tested. |
| X-Report | Expected drawer cash calculation (float + cash sales − pickups − petty) | `/till/x-report — x_report_screen.dart expectedDrawer formula` | GAP | none | Formula is correct only if cashSales is correctly accumulated. Currently cashSales = last sale's cash tender only (not all session sales). This is a known TODO in the source comment. No test exposes the incorrect accumulation. |
| Cash Movements | Cash pickup — amount entry, note, quick amounts, outbox write | `/cash/pickup — cash_pickup_screen.dart` | COVERED | TC-POS-CORE-012, TC-POS-SYNC-007 |  |
| Cash Movements | Cash pickup — negative/zero amount rejected | `/cash/pickup — cash_pickup_screen.dart _canSubmit guard` | GAP | none | The UI has a parsedAmount > 0 guard but no test exercises it. |
| Cash Movements | Cash pickup — no active session error path | `/cash/pickup — cash_pickup_screen.dart sessionClientOpId.isEmpty check` | GAP | none |  |
| Cash Movements | Petty cash — category dropdown, amount, paid-to, description, outbox write | `/cash/petty — petty_cash_screen.dart` | COVERED | TC-POS-CORE-013 |  |
| Cash Movements | Petty cash — invalid category enum value blocked | `/cash/petty — petty_cash_screen.dart PettyCashCategory enum` | PARTIAL | TC-POS-CORE-013 | TC-POS-CORE-013 notes the corrected valid values (TRANSPORT, OFFICE, MAINTENANCE, OTHER). The UI uses the enum directly so the dropdown cannot produce an invalid value — but this is not explicitly verified by a test. |
| Till Close | Declare cash and close session (variance = 0) | `/till/close — till_close_screen.dart _close()` | COVERED | TC-POS-CORE-014 |  |
| Till Close | Variance above threshold blocked without supervisor | `/till/close → API POST /till-sessions/uid/<uid>/close` | COVERED | TC-POS-CORE-015 |  |
| Till Close | RECONCILE_INCOMPLETE error path (missing/unexpected clientOpIds) | `/till/close — till_close_screen.dart result.status == TillCloseStatus.reconcile_incomplete` | GAP | none | When the sync reconcile is incomplete the UI shows a 'Missing: N, Unexpected: N. Sync all ops and retry.' message. No test exercises this path. |
| Till Close | Z-report generated on successful close, sign-out redirect to /login | `/till/close — till_close_screen.dart AlertDialog on closed status` | PARTIAL | TC-POS-CORE-014 | TC-POS-CORE-014 verifies Z-report is generated/queued server-side. The Flutter dialog content (expected/declared/variance display) and the sign-out redirect are not widget-tested. |
| Settings | Hardware section — printer status (static mock), scanner status (static mock) | `/settings — settings_screen.dart _SectionCard 'Hardware'` | GAP | none | Printer and scanner show hardcoded 'connected' status. No actual hardware probe. No test. |
| Settings | Cash drawer test kick (snackbar only) | `/settings — settings_screen.dart TextButton 'Test'` | GAP | none | Drawer test is a snackbar. No platform channel integration. No test. |
| Settings | Fiscal device status display (static 'Not configured') | `/settings — settings_screen.dart _SettingsTile fiscal` | GAP | none |  |
| Settings | Price list code edit (persisted via posConfigStore) | `/settings — settings_screen.dart _EditableTile priceListCode` | GAP | none | Changing the price list code should trigger a catalog re-sync. No test verifies the save → reload chain. |
| Settings | POS Section ID edit (persisted via posConfigStore) | `/settings — settings_screen.dart _EditableTile sectionId` | GAP | none |  |
| Settings | API base URL edit (persisted, requires restart note) | `/settings — settings_screen.dart _EditableTile apiBaseUrl` | GAP | none | Changing the API URL while sessions are active is a data-integrity risk. No test verifies it is persisted correctly or that the restart warning is shown. |
| Settings | Device / Till ID edit (persisted via posConfigStore) | `/settings — settings_screen.dart _EditableTile deviceId` | GAP | none |  |
| Settings | Manual sync push (Push now button — currently a fake delay snackbar) | `/settings — settings_screen.dart TextButton 'Push now'` | GAP | none | The Push now button does a 700 ms Future.delayed then shows 'Manual push triggered'. It does NOT actually invoke outboxDispatcher.flush(). This is a bug masked by lack of test coverage. |
| Sync (background) | Outbox dispatcher flush — push POS_SALE op, receive ACCEPTED | `orbix-engine-pos/lib/data/sync/outbox_dispatcher.dart` | COVERED | TC-POS-SYNC-001 |  |
| Sync (background) | Idempotency — duplicate clientOpId returns DUPLICATE, no double-post | `orbix-engine-pos/lib/data/sync/outbox_dispatcher.dart` | COVERED | TC-POS-SYNC-002 |  |
| Sync (background) | Batch push (5 ops) in FIFO order | `orbix-engine-pos/lib/data/sync/outbox_dispatcher.dart` | COVERED | TC-POS-SYNC-003 |  |
| Sync (background) | Batch exceeds server max (500) rejected with 400 | `POST /api/v1/sync/push pushBatchMax guard` | COVERED | TC-POS-SYNC-004 |  |
| Sync (background) | Op with unsettled dependsOn returns DEFERRED | `POST /api/v1/sync/push DEFERRED logic` | COVERED | TC-POS-SYNC-005 |  |
| Sync (background) | TILL_SESSION_OPEN op idempotent | `POST /api/v1/sync/push opType=TILL_SESSION_OPEN` | COVERED | TC-POS-SYNC-006 |  |
| Sync (background) | CASH_PICKUP op idempotent | `POST /api/v1/sync/push opType=CASH_PICKUP` | COVERED | TC-POS-SYNC-007 |  |
| Sync (background) | Unknown opType returns REJECTED | `POST /api/v1/sync/push unknown opType` | COVERED | TC-POS-SYNC-008 |  |
| Sync (background) | Mixed batch (1 valid + 1 invalid) — valid ACCEPTED, invalid REJECTED, no rollback | `POST /api/v1/sync/push per-op transaction isolation` | COVERED | TC-POS-SYNC-010 |  |
| Sync (background) | Pull catalog — full + incremental (cursor-based delta) | `GET /api/v1/sync/pull?datasets=catalog` | COVERED | TC-POS-SYNC-011, TC-POS-SYNC-015, TC-POS-SYNC-016 |  |
| Sync (background) | Pull price dataset | `GET /api/v1/sync/pull?datasets=price` | COVERED | TC-POS-SYNC-012 |  |
| Sync (background) | Pull balance dataset (stock on hand) | `GET /api/v1/sync/pull?datasets=balance` | COVERED | TC-POS-SYNC-013 |  |
| Sync (background) | Pull customer dataset | `GET /api/v1/sync/pull?datasets=customer` | COVERED | TC-POS-SYNC-014 |  |
| Sync (background) | Multi-tenant isolation in pull (branch-scoped data only) | `GET /api/v1/sync/pull X-Branch-Id isolation` | COVERED | TC-POS-SYNC-017 |  |
| Sync (background) | Connectivity-triggered outbox flush (reconnect after offline period) | `orbix-engine-pos/lib/data/sync/outbox_dispatcher.dart` | GAP | none | The dispatcher is timer-based per the implementation. There is no test that simulates offline → reconnect → automatic flush. The full offline-then-online loop (P0 invariant) has no Flutter-layer integration test. |
| Sync (background) | Outbox drain after multiple offline sales — stock and cash consistency | `orbix-engine-pos/lib/data/sync/outbox_dispatcher.dart + pos_sale_repository.dart` | PARTIAL | TC-POS-SYNC-001, TC-POS-SYNC-002, TC-POS-SYNC-003 | API-level batch push is tested. An end-to-end test that (1) goes offline, (2) records 3 sales in Flutter, (3) reconnects, (4) verifies outbox drains and server state is correct does not exist. |
| Sync (background) | PETTY_CASH op via sync push | `POST /api/v1/sync/push opType=PETTY_CASH` | GAP | none | TC-POS-CORE-013 tests the direct API endpoint for petty cash. There is no TC that pushes a PETTY_CASH op through the sync push endpoint (the offline path). TC-POS-SYNC-007 covers CASH_PICKUP but not PETTY_CASH. |
| Sync (background) | TILL_SESSION_CLOSE op via sync push | `POST /api/v1/sync/push opType=TILL_SESSION_CLOSE` | GAP | none | Till close goes through syncRepo.closeTillSession(). There is no TC verifying the TILL_SESSION_CLOSE op is idempotent through the sync push path. |
| VAT / Tax | VAT-inclusive price — correct extraction on sale lines | `POST /api/v1/pos-sales taxInclusive computation` | COVERED | TC-POS-CORE-010 |  |
| VAT / Tax | Mixed VAT cart (standard + exempt items) | `POST /api/v1/pos-sales mixed vatRate lines` | COVERED | TC-POS-CORE-010 |  |
| VAT / Tax | Receipt VAT notice and TIN display | `/receipt — receipt_screen.dart 'Incl. VAT · TIN 100-000-000'` | GAP | none | TIN is hardcoded. No test verifies it matches the company's actual TIN from config. |
| Stock / Oversell | Oversell blocked when qty_on_hand = 0 and no STOCK.OVERSELL permission | `POST /api/v1/pos-sales stock guard` | COVERED | TC-POS-CORE-011 |  |
| Void Sale | Supervisor void of posted sale — reverses stock and cash | `POST /api/v1/pos-sales/uid/<uid>/void` | COVERED | TC-POS-CORE-016 |  |

## API — full inventory

| Menu | Feature | Locator | Coverage | Cases | Note |
|---|---|---|---|---|---|
| auth | POST /api/v1/auth/login | `AuthController#login` | COVERED | TC-AUTH-001, TC-AUTH-002, TC-AUTH-003 |  |
| auth | POST /api/v1/auth/refresh | `AuthController#refresh` | COVERED | TC-AUTH-005, TC-AUTH-006 |  |
| auth | POST /api/v1/auth/logout | `AuthController#logout` | COVERED | TC-AUTH-007 |  |
| auth | POST /api/v1/auth/logout-everywhere | `AuthController#logoutEverywhere` | GAP | none | No TC targets this endpoint specifically; TC-AUTH-007 only covers /logout |
| auth | GET /api/v1/auth/sessions | `AuthController#sessions` | GAP | none | Active session listing for 'log out everywhere' screen has no dedicated TC |
| auth | Account lockout (5 bad attempts) | `AuthController via AuthServiceImpl` | COVERED | TC-AUTH-004 |  |
| auth | JWT tamper / expiry rejection | `AuthController via JwtServiceImpl` | COVERED | TC-AUTH-006, TC-NFR-SEC-002, TC-NFR-SEC-003 |  |
| auth | Permission gate (403) | `AuthController / Spring Security` | COVERED | TC-AUTH-009 |  |
| auth | Branch-scope gate (X-Branch-Id) | `SessionController + RequestContext` | COVERED | TC-AUTH-010, TC-AUTH-015 |  |
| iam | GET /api/v1/session/branches | `SessionController#accessibleBranches` | GAP | none | No TC covers branch listing or active-branch switch |
| iam | PUT /api/v1/session/active-branch | `SessionController#setActiveBranch` | GAP | none | Branch-switch returns a fresh JWT pair — no TC covers token re-issue |
| iam | GET/POST/PATCH/DELETE /api/v1/users | `UserAdminController` | PARTIAL | TC-AUTH-012, TC-AUTH-013 | Create + disable covered; update, lookup-users, reset-password, unlock, force-logout have no TC |
| iam | POST /api/v1/users/me/change-password | `UserAdminController#changeMyPassword` | GAP | none | Self-service password change not tested |
| iam | POST /api/v1/users/uid/{uid}/reset-password | `UserAdminController#resetPassword` | GAP | none |  |
| iam | GET/POST/PATCH/DELETE /api/v1/roles + /grants | `RoleAdminController` | GAP | none | Entire RBAC authoring surface (create role, set permissions, grant role, revoke grant) has zero TCs |
| iam | GET /api/v1/permissions | `RoleAdminController#listPermissions` | GAP | none |  |
| iam | GET /api/v1/audit | `AuditController#list` | PARTIAL | TC-AUTH-014 | Query surface covered via AUTH-014 but no TC specifically exercises filter params or pagination |
| iam | GET /api/v1/audit/integrity | `AuditController#integrity` | PARTIAL | TC-NFR-SEC-008 | NFR-SEC-008 checks append-only but does not call the /integrity endpoint |
| admin | GET/PATCH /api/v1/company | `CompanyController` | PARTIAL | TC-ADMIN-002 | TC-ADMIN-002 references a non-existent PUT /companies/uid URL; actual endpoint is PATCH /company (singular). Functional gap. |
| admin | GET/POST/PATCH /api/v1/branches + /activate /deactivate | `BranchController` | PARTIAL | TC-ADMIN-003 | Default-flag mutual-exclusion covered; create/update/activate/deactivate endpoints have no TC |
| admin | GET /api/v1/branches/uid/{uid}/sections + POST + PATCH + /deactivate | `SectionController` | GAP | none | Section CRUD entirely untested |
| admin | GET/POST/PATCH /api/v1/routes + /activate /deactivate | `RouteController` | GAP | none | Delivery-route management entirely untested |
| admin | GET/POST/PATCH /api/v1/currencies + /enable /disable | `CurrencyController` | GAP | none | Currency management has no TC |
| admin | GET/POST /api/v1/fx-rates + /effective | `FxRateController` | GAP | none | FX rate management and lookup entirely untested |
| admin | GET/PUT /api/v1/settings | `SettingsController` | PARTIAL | TC-ADMIN-004 | TC-ADMIN-004 covers GET; PUT (update settings) has no TC |
| admin | GET /api/v1/setup/status + POST /reset-rootadmin-password | `SetupController` | PARTIAL | TC-ADMIN-001 | Boot status checked by TC-ADMIN-001; reset-rootadmin-password endpoint (token-gated) never tested |
| catalog | GET/POST /api/v1/items + /uid/{uid} + PATCH + /archive + /activate | `ItemController` | COVERED | TC-CAT-001, TC-CAT-002, TC-CAT-003, TC-CAT-006, TC-CAT-010 |  |
| catalog | GET/POST /api/v1/item-groups + /uid/{uid} + PATCH + /move + /archive + /activate | `ItemGroupController` | PARTIAL | TC-CAT-007, TC-CAT-008 | Hierarchy depth limit and deletion-with-items covered; move-group and activate/deactivate have no TC |
| catalog | GET/POST /api/v1/uoms + /uid/{uid} + PATCH + /archive + /activate | `UomController` | GAP | none | UoM CRUD has no dedicated TC; referenced in item tests as a precondition only |
| catalog | GET/POST /api/v1/vat-groups + /uid/{uid} + PATCH + /archive + /activate | `VatGroupController` | PARTIAL | TC-CAT-011 | VAT rate precision tested; create/update/archive lifecycle has no TC |
| catalog | GET /api/v1/items/uid/{itemUid}/barcodes + POST + DELETE /barcodes/uid/{uid} | `ItemBarcodeController` | PARTIAL | TC-CAT-004 | Add+uniqueness covered; delete-barcode endpoint has no TC |
| catalog | GET/POST /api/v1/price-lists + /uid/{uid} + PATCH + /archive + /activate | `PriceListController (list/header CRUD)` | PARTIAL | TC-CAT-005 | Price set covered; price-list header create/update/archive have no TC |
| catalog | GET /api/v1/price-lists/code/{code} | `PriceListController#getPriceListByCode` | GAP | none |  |
| catalog | GET /api/v1/price-lists/uid/{uid}/items + /resolve | `PriceListController#listPrices + resolvePrice` | GAP | none | Price resolve and date-scoped list have no TC |
| catalog | PUT /api/v1/price-lists/uid/{uid}/items (setPrice) | `PriceListController#setPrice` | COVERED | TC-CAT-005 |  |
| catalog | DELETE /api/v1/price-lists/uid/{uid}/items (discontinuePrice) | `PriceListController#discontinuePrice` | GAP | none |  |
| catalog | POST /api/v1/price-lists/uid/{uid}/items/copy-from | `PriceListController#copyPrices` | GAP | none |  |
| catalog | POST /api/v1/price-lists/uid/{uid}/items/adjust | `PriceListController#adjustPrices` | GAP | none |  |
| catalog | GET /api/v1/items/uid/{itemUid}/price-changes | `PriceListController#priceHistory` | PARTIAL | TC-CAT-005 | Log row existence checked in TC-CAT-005 but the endpoint itself is not called |
| party | GET /api/v1/parties + /uid/{uid} + /by-tin + POST /codes/reserve | `PartyController` | GAP | none | Shared party lookup and code-reservation have no TC |
| party | GET/POST /api/v1/customers + /uid/{uid} + PATCH + /archive + /activate | `CustomerController` | PARTIAL | TC-PARTY-001, TC-PARTY-002, TC-PARTY-003 | Create and credit-limit cross-branch covered; update/archive/activate lifecycle has no TC |
| party | GET/POST /api/v1/suppliers + /uid/{uid} + PATCH + /archive + /activate | `SupplierController` | PARTIAL | TC-PARTY-005 | Create + duplicate code covered; update/archive/activate has no TC |
| party | GET/POST /api/v1/employees + /uid/{uid} + PATCH + /archive + /activate | `EmployeeController` | GAP | none | Employee CRUD entirely untested |
| party | GET/POST /api/v1/sales-agents + /uid/{uid} + PATCH + /archive + /activate | `SalesAgentController` | GAP | none | Sales-agent CRUD entirely untested |
| procurement | GET/POST /api/v1/lpos + /uid/{uid} + /submit + /approve + /cancel | `LpoOrderController` | COVERED | TC-PROC-001, TC-PROC-002 |  |
| procurement | GET /api/v1/lpos/pending-approval/count | `LpoOrderController#pendingApprovalCount` | GAP | none | Dashboard tile feed never tested |
| procurement | GET/POST /api/v1/grns + /uid/{uid}/post + /cancel + /cancel-posted | `GrnController` | COVERED | TC-PROC-003, TC-PROC-004, TC-PROC-005 |  |
| procurement | Direct GRN (no LPO) requires GRN.DIRECT permission | `GrnController#create (lpoOrderId==null)` | GAP | none | Permission branch for direct GRN never tested |
| procurement | GET/POST /api/v1/supplier-invoices + /uid/{uid}/post + /cancel | `SupplierInvoiceController` | PARTIAL | TC-PROC-003, TC-PROC-006 | Create+post covered as part of 3-way-match flow; cancel has no TC |
| procurement | GET/POST /api/v1/vendor-returns + /post + /cancel + /issue-credit-note | `VendorReturnController` | GAP | none | Vendor return entire lifecycle untested |
| procurement | GET /api/v1/vendor-credit-notes + POST /apply | `VendorReturnController#listCreditNotes + applyVendorCreditNote` | GAP | none |  |
| procurement | GET/POST /api/v1/supplier-payments + /uid/{uid}/post + /cancel | `SupplierPaymentController` | COVERED | TC-PROC-006, TC-CASH-004 |  |
| sales | GET/POST /api/v1/sales-invoices + /uid/{uid}/post + /void + /cancel + /reprint | `SalesInvoiceController` | COVERED | TC-SALES-001, TC-SALES-002, TC-SALES-003, TC-SALES-007 |  |
| sales | GET/POST /api/v1/sales-receipts + /uid/{uid}/post + /cancel | `SalesReceiptController` | COVERED | TC-SALES-004, TC-SALES-008 |  |
| sales | GET/POST /api/v1/customer-returns + /post + /cancel + /issue-credit-note | `CustomerReturnController` | PARTIAL | TC-SALES-005 | Create+post with restock covered; cancel and issue-credit-note have no TC |
| sales | GET /api/v1/customer-credit-notes + POST /apply | `CustomerReturnController#listCreditNotes + applyCreditNote` | GAP | none | Credit note application to invoice not tested |
| sales | GET/POST /api/v1/packing-lists + /dispatch + /deliver + /cancel | `PackingListController` | GAP | none | Packing list entire lifecycle untested |
| stock | GET /api/v1/stock-moves + /balances + /stock-card | `StockController` | PARTIAL | TC-STOCK-001, TC-STOCK-005, TC-STOCK-006 | TC-STOCK-005 URL differs from actual endpoint (/stock-card vs /stock-report/card) |
| stock | POST /api/v1/adjustments | `AdjustmentController` | COVERED | TC-STOCK-002, TC-STOCK-003 |  |
| stock | GET/POST /api/v1/stock-transfers + /issue + /receive + /close | `StockTransferController` | GAP | none | Inter-branch transfer lifecycle entirely untested |
| stock | GET/POST /api/v1/stock-counts + /start + /counts + /close + /post | `StockCountController` | GAP | none | Physical count lifecycle entirely untested |
| stock | GET /api/v1/stock-batches + /expiring-soon + /uid/{uid}/recall | `StockBatchController` | GAP | none | Batch/FEFO management entirely untested |
| stock | POST /api/v1/internal-consumption | `InternalConsumptionController` | GAP | none | Internal consumption draw entirely untested |
| pos | GET/POST/PATCH /api/v1/tills + /activate + /deactivate | `TillController` | GAP | none | Till master data CRUD entirely untested |
| pos | GET/POST /api/v1/till-sessions/open + /close + /reconcile + /balance | `TillSessionController` | COVERED | TC-POS-CORE-001, TC-POS-CORE-002, TC-POS-CORE-003, TC-POS-CORE-014, TC-POS-CORE-015 |  |
| pos | GET /api/v1/till-sessions/uid/{uid}/balance | `TillSessionController#getBalance` | PARTIAL | TC-POS-CORE-001 | Referenced in TC-POS-CORE-001 step 3 indirectly; no dedicated TC for balance computation edge cases |
| pos | POST /api/v1/till-sessions/uid/{uid}/reconcile | `TillSessionController#reconcile` | GAP | none | Reconcile (supervisor sign-off after close) has no TC |
| pos | GET/POST /api/v1/pos-sales + /uid/{uid}/void + /refund | `PosSaleController` | COVERED | TC-POS-CORE-004 through TC-POS-CORE-011, TC-POS-CORE-016, TC-POS-CORE-017, TC-POS-CORE-018 |  |
| pos | GET /api/v1/pos/barcode-lookup | `BarcodeLookupController` | COVERED | TC-POS-CORE-019, TC-POS-CORE-020 |  |
| pos | GET/POST /api/v1/cash-pickups + /uid/{uid} | `CashPickupController` | COVERED | TC-POS-CORE-012, TC-POS-SYNC-007 |  |
| pos | GET/POST /api/v1/petty-cash + /uid/{uid} | `PettyCashController` | COVERED | TC-POS-CORE-013 |  |
| pos | GET/POST/DELETE /api/v1/tills/{tillId}/currencies | `TillCurrencyController` | GAP | none | FX tender currency list for till entirely untested |
| sync | POST /api/v1/sync/push | `SyncController#push` | COVERED | TC-POS-SYNC-001 through TC-POS-SYNC-010 |  |
| sync | GET /api/v1/sync/pull | `SyncController#pull` | COVERED | TC-POS-SYNC-011 through TC-POS-SYNC-017 |  |
| sync | GET /api/v1/sync/bootstrap | `SyncController#bootstrap` | GAP | none | Bootstrap endpoint (full snapshot for fresh device) never tested; TC-POS-SYNC-011 tests /pull with empty cursor, not /bootstrap |
| sync | POST /api/v1/sync/till-session/close | `SyncController#closeTillSession` | GAP | none | Device-side till close reconciliation handshake has no TC |
| sync | GET /api/v1/sync/catalog/snapshot (legacy) | `SyncController#catalogSnapshot` | GAP | none | Legacy snapshot endpoint not tested |
| sync | GET /api/v1/sync/balances/snapshot (legacy) | `SyncController#balanceSnapshot` | GAP | none |  |
| sync | Contract version validation (426/409) | `SyncController#validateContractVersion` | GAP | none | Header X-Orbix-Contract-Version mismatch paths have no TC |
| day | GET/POST /api/v1/business-days + /current + /{date}/start-closing + /close + /blockers + /{date}/end | `BusinessDayController (composite-key paths)` | COVERED | TC-DAY-001, TC-DAY-002, TC-DAY-003, TC-DAY-004, TC-DAY-005, TC-DAY-006, TC-DAY-007 |  |
| day | GET/POST /api/v1/business-days/uid/{uid} + /start-closing + /close + /end + /blockers (uid paths) | `BusinessDayController (uid paths, Slice D)` | PARTIAL | TC-DAY-003, TC-DAY-004 | TC-DAY-003/004 use uid endpoints; uid-specific blockers and end-day have no standalone TC |
| day | GET /api/v1/business-days/overrides + POST + /archive | `BusinessDayController (override sub-resource)` | GAP | none | Business day override (post/archive) entirely untested |
| cash | GET /api/v1/cash-entries + /uid/{uid} | `CashLedgerController#listEntries + getEntryByUid` | PARTIAL | TC-CASH-001 | TC-CASH-001 checks DB directly; the /cash-entries endpoint itself is not called in any TC |
| cash | GET /api/v1/cash-book + /uid/{uid} | `CashLedgerController#listCashBook + getCashBookByUid` | PARTIAL | TC-CASH-002 | TC-CASH-002 references a non-existent URL (/cash-ledger/session/…); actual endpoint /cash-book never called |
| cash | GET/POST /api/v1/bank-deposits + /uid/{uid}/archive | `BankDepositController` | PARTIAL | TC-CASH-003 | Create covered; archive and list endpoints have no TC |
| cash | GET/POST /api/v1/cash-adjustments + /uid/{uid}/archive | `CashAdjustmentController` | PARTIAL | TC-CASH-005 | Permission check covered; full create/archive flow has no TC |
| debt | GET /api/v1/debt/aging + /dunning + /statement + /statement/uid/{uid} | `DebtController (AR read model)` | COVERED | TC-DEBT-001, TC-DEBT-002, TC-DEBT-003 |  |
| debt | POST /api/v1/debt/customer/uid/{uid}/credit-limit | `DebtController#adjustCreditLimit` | GAP | none | Credit limit adjustment endpoint has no TC |
| debt | GET/POST /api/v1/debt/notes + /uid/{uid}/archive | `DebtController (chase notes)` | GAP | none | AR chase notes CRUD entirely untested |
| debt | GET /api/v1/debt/supplier-aging + /supplier-dunning + /supplier/uid/{uid} | `SupplierDebtController (AP read model)` | GAP | none | Supplier-side AP debt surface entirely untested |
| debt | GET/POST /api/v1/debt/write-offs + /uid/{uid}/approve + /reject | `DebtWriteOffController` | COVERED | TC-DEBT-004 |  |
| giftcard | POST /api/v1/gift-cards + GET /{code} + /transactions + /redeem + /refund + /freeze + /unfreeze | `GiftCardController` | COVERED | TC-GIFTCARD-001 through TC-GIFTCARD-005 | Freeze/unfreeze has no dedicated TC |
| orders | GET/POST /api/v1/orders + PATCH + /reserve + /payments + /cancel + /ready + /collect | `CustomerOrderController` | COVERED | TC-ORDERS-001, TC-ORDERS-002, TC-ORDERS-003 | Reserve and ready transitions have no TC |
| production | GET/POST /api/v1/boms + /uid/{uid} + PATCH + /activate + /retire + /version | `BomController` | PARTIAL | TC-PROD-001 | Create covered; activate/retire/version lifecycle has no TC |
| production | GET/POST /api/v1/production-batches + /start + /post-output + /cancel + /advance-lifecycle + /close | `ProductionBatchController` | PARTIAL | TC-PROD-002 | Output posting covered; advance-lifecycle (hot→cold→donated/write-off) and cancel have no TC |
| production | GET/POST /api/v1/production-wastage | `ProductionWastageController` | GAP | none | Wastage recording entirely untested |
| production | GET/POST /api/v1/conversions + /post + /cancel | `ConversionController` | PARTIAL | TC-PROD-003 | TC-PROD-003 describes the endpoint; post and cancel have no TC |
| reports | GET /api/v1/reports/sales-daily | `SalesReportController#dailySales` | PARTIAL | TC-RPT-001 | TC-RPT-001 URL is wrong (/reports/sales); actual endpoint /reports/sales-daily |
| reports | GET /api/v1/reports/sales-summary | `SalesReportController#dailySummary` | PARTIAL | TC-RPT-003 | TC-RPT-003 references /reports/dashboard (wrong URL); actual endpoint /reports/sales-summary |
| reports | GET /api/v1/reports/z-history | `SalesReportController#zHistory` | GAP | none | Z-history (all sessions in date range) has no TC |
| reports | GET /api/v1/reports/section-pnl | `SalesReportController#sectionPnl` | GAP | none |  |
| reports | GET /api/v1/reports/vat-return | `SalesReportController#vatReturn` | GAP | none | VAT return report has no TC despite being a fiscal compliance requirement |
| reports | GET /api/v1/reports/x-report | `TillReportController#xReport` | PARTIAL | TC-POS-CORE-001, TC-POS-CORE-012 | Referenced in steps but no TC validates x-report content correctness |
| reports | GET /api/v1/reports/z-report | `TillReportController#zReport` | GAP | none | Z-report (post-close) has no TC |
| reports | GET /api/v1/reports/dashboard-rollup | `DashboardReportController#rollup` | PARTIAL | TC-RPT-003 | TC-RPT-003 URL is wrong; no TC accurately targets /reports/dashboard-rollup |
| reports | GET /api/v1/sales/reports/ar-summary | `SalesAggregateReportController#arSummary` | GAP | none | AR summary tile has no TC |
| reports | GET /api/v1/reports/stock-negative | `StockReportController#negativeOnHand` | PARTIAL | TC-RPT-004 | TC-RPT-004 URL is wrong (/stock-report/negative); actual endpoint /reports/stock-negative |
| reports | GET /api/v1/reports/stock-fast-movers + /stock-slow-movers | `StockReportController` | GAP | none |  |
| reports | GET /api/v1/reports/customer-statement + /supplier-statement | `StatementReportController` | GAP | none | Chronological AR/AP statement with period-opening balance has no TC |
| reports | GET /api/v1/reports/gift-card-liability | `GiftCardLiabilityReportController` | GAP | none |  |
| reports | GET /api/v1/reports/layby-ageing | `LaybyAgeingReportController` | PARTIAL | TC-ORDERS-004 | TC-ORDERS-004 references the endpoint; no TC validates bucket content |
| reports | GET /api/v1/reports/production-variance | `ProductionVarianceReportController` | GAP | none |  |
| reports | GET /api/v1/reports/production-wastage | `ProductionWastageReportController` | GAP | none |  |
| fiscal | FiscalStatus=NONE default (regime=NONE) | `PosSaleController via FiscalizationService` | COVERED | TC-FISCAL-001 |  |
| fiscal | FiscalStatus PROVISIONAL→FISCALIZED (regime=TZ_VFD) | `Domain outbox + FiscalizationService` | PARTIAL | TC-FISCAL-002 | TC blocked if TZ_VFD not configured in QA; no automated assertion path |
| fiscal | Outbox retry → FAILED on EFDMS error | `FiscalizationService outbox dispatcher` | PARTIAL | TC-FISCAL-003 | Partial — requires timer control and failure-mode stub |
| fiscal | FiscalizationRequested domain event in same TX | `Domain outbox` | PARTIAL | TC-FISCAL-004 |  |
| fiscal | pos_sale.fiscal_status sync with fiscal_receipt.status | `FiscalizationService` | PARTIAL | TC-FISCAL-005 |  |
| nfr | BCrypt cost >= 12 | `AuthServiceImpl` | COVERED | TC-NFR-SEC-001 |  |
| nfr | CORS disallowed origin rejected | `Spring Security CORS config` | COVERED | TC-NFR-SEC-004 |  |
| nfr | SQL injection returns 400/200 not 500 | `GlobalExceptionHandler + JPQL` | COVERED | TC-NFR-SEC-005 |  |
| nfr | No sensitive data in error responses | `GlobalExceptionHandler` | COVERED | TC-NFR-SEC-006 |  |
| nfr | Multi-tenant POS sale isolation by branch | `RequestContext + repository predicates` | COVERED | TC-NFR-SEC-007 |  |
| nfr | Audit log append-only (no DELETE endpoint) | `AuditController (absence check)` | COVERED | TC-NFR-SEC-008 |  |
| nfr | PAN never stored in pos_payment | `PosSaleServiceImpl` | COVERED | TC-NFR-SEC-009 |  |
| nfr | Login < 2s p95 | `AuthController` | COVERED | TC-NFR-PERF-001 |  |
| nfr | Item list < 500ms p95 (1000 items) | `ItemController` | COVERED | TC-NFR-PERF-002 |  |
| nfr | Sync push 50 ops < 5s | `SyncController` | COVERED | TC-NFR-PERF-003, TC-POS-SYNC-019 |  |
| nfr | Report pagination / no OOM | `SalesReportController` | COVERED | TC-NFR-PERF-004 |  |
| nfr | Concurrent till sales no stock corruption | `StockMoveServiceImpl + optimistic lock` | COVERED | TC-NFR-PERF-005 |  |
| nfr | DB-agnostic Flyway on MariaDB + PostgreSQL | `Flyway + HealthSmokeTest` | PARTIAL | TC-NFR-DATA-001 | BLOCKED — HealthSmokeTest needs Testcontainers DB infra (tracked debt since 2026-05-24) |
| nfr | ULID uniqueness (1000 generated) | `UidGenerator` | COVERED | TC-NFR-DATA-002 |  |
| nfr | BigDecimal money only (no float/double) | `PosSaleServiceImpl etc.` | COVERED | TC-NFR-DATA-003 |  |
| nfr | company_id + branch_id on every transactional table | `Flyway migrations` | COVERED | TC-NFR-DATA-004 |  |
| nfr | Idempotency key unique constraint on outbox | `domain_event table` | COVERED | TC-NFR-DATA-005 |  |
| nfr | App restart preserves pending outbox events | `Outbox dispatcher` | COVERED | TC-NFR-RELI-002 |  |
| nfr | POS offline 30min → sync on reconnect (E2E) | `Flutter + SyncController` | COVERED | TC-NFR-RELI-003 |  |
| nfr | Invalid enum returns 400 not 500 | `GlobalExceptionHandler` | COVERED | TC-NFR-RELI-004 |  |
| nfr | ArchUnit ModuleBoundaryTest passes | `ModuleBoundaryTest` | COVERED | TC-NFR-RELI-005 |  |
