import { type Page } from '@playwright/test';
import { test, expect } from './personas.fixture';
import type { Persona } from './test-users';

/**
 * End-to-end Dashboard drill-through spec (Slice F release gate, TDD-first).
 *
 * Drives the dashboard's KPI tiles + alert rows through the real Angular UI
 * against the QA-parity container at http://localhost:8081/. Every scenario
 * answers the same question: when the user clicks a tile or an alert, does
 * the destination view actually filter to the subset that tile counted, or
 * does it dump them on an unfiltered list and force them to re-do the
 * filtering by hand?
 *
 * The current `main` answer is "unfiltered list" — Slice F closes the gap.
 * Most assertions here carry {@code test.fail(...)} markers; the
 * backend-engineer + frontend-engineer commits make them turn green one by
 * one. When all {@code test.fail} matches drop to zero, Slice F is done.
 *
 * Sequencing matters — Playwright runs describe blocks in file order with
 * {@code fullyParallel: false} + {@code workers: 1}. This file does not
 * provision per-run business data; it only exercises read-side drill-through
 * navigation, so the dashboard's existing aggregates (from prior specs +
 * QA-image seed) are what feed the tiles.
 *
 * Personas exercised (see e2e/test-users.ts):
 *   - accountant (happy path) — holds SALES.REPORT.AR_SUMMARY +
 *     SALES.MANAGE_INVOICE + STOCK.COUNT + PROCUREMENT.MANAGE_LPO.READ.
 *     Can drill into every dashboard tile + alert.
 *   - sales-clerk (partial path) — holds SALES.MANAGE_INVOICE but NOT
 *     SALES.REPORT.AR_SUMMARY. Sees AR tiles as "Permission required"
 *     (inert, no link); can drill into sales invoices via the destination
 *     URL directly.
 *   - store-manager (partial path) — holds STOCK.COUNT but NOT
 *     SALES.REPORT.AR_SUMMARY or PROCUREMENT.MANAGE_LPO. Can drill into
 *     stock-balances; AR + LPO drill-throughs render "Permission required".
 *
 * Front-end contract — stable test IDs the frontend agent will wire (see
 * docs/design/slice-f-reports-plan.md §3 and §5). Each is referenced below.
 *
 *   data-testid                          | Component               | Purpose
 *   -------------------------------------|-------------------------|--------------------------------------
 *   kpi-todays-sales                     | dashboard.component.ts  | KPI 1.A wrapper (link / inert state)
 *   kpi-stock-alerts                     | dashboard.component.ts  | KPI 1.B wrapper
 *   kpi-negative-stock                   | dashboard.component.ts  | KPI 1.C wrapper
 *   kpi-open-invoices                    | dashboard.component.ts  | KPI 1.D wrapper
 *   kpi-ar-outstanding                   | dashboard.component.ts  | KPI 1.E wrapper
 *   alert-stock-alerts                   | dashboard.component.ts  | Alert row "N SKUs at or below reorder"
 *   alert-negative-stock                 | dashboard.component.ts  | Alert row "N items in negative stock"
 *   alert-overdue-invoices               | dashboard.component.ts  | Alert row "N invoices past due"
 *   alert-lpos-pending                   | dashboard.component.ts  | Alert row "N LPOs awaiting approval"
 *   negative-only-filter                 | balances.component.ts   | "Negative only" checkbox (new)
 *   below-reorder-filter                 | balances.component.ts   | "Below reorder only" checkbox (existing — add test id)
 *   status-filter                        | invoices.component.ts   | Status bucket dropdown (new)
 *   status-filter                        | lpos.component.ts       | Status filter dropdown (new)
 *
 * When a KPI tile is permission-denied the wrapper renders WITHOUT an
 * {@code <a>} anchor — the value shows "Permission required" and clicks
 * are inert (the spec proves this by asserting the absence of an anchor
 * inside the testid'd wrapper).
 */

// -----------------------------------------------------------------------------
// Branch context — every nav primes the active branch via localStorage so the
// dashboard's branch-scoped tile loads pick HQ.
// -----------------------------------------------------------------------------

const BRANCH_ID = '1';

test.beforeEach(async ({ page }) => {
  await page.addInitScript((branchId: string) => {
    localStorage.setItem('orbix.activeBranchId', branchId);
  }, BRANCH_ID);
});

// -----------------------------------------------------------------------------
// Helpers — match KPI tiles + alert rows by stable test ids the frontend
// agent will add. The dashboard wraps each tile in a data-testid'd element
// whose only anchor (when present) is the drill-through link.
// -----------------------------------------------------------------------------

async function openDashboard(page: Page): Promise<void> {
  await page.goto('/dashboard');
  // The greeting h1 (Good morning Godfrey, …) is the dashboard's stable
  // landing marker — match the persistent structure, not the greeting text.
  await expect(page.locator('h1')).toBeVisible({ timeout: 20_000 });
}

/**
 * Click the drill-through anchor inside a KPI tile or alert row. The
 * wrapper carries the {@code data-testid}; the anchor is the descendant
 * link the user actually clicks. When the perm is denied the anchor is
 * absent — the caller should use {@link assertInertTile} instead.
 */
async function clickTile(page: Page, testId: string): Promise<void> {
  const link = page.locator(`[data-testid="${testId}"] a`).first();
  await expect(link, `drill-through anchor inside [data-testid="${testId}"]`).toBeVisible({ timeout: 10_000 });
  await link.click();
}

/**
 * Assert a permission-denied tile renders inert — present with the
 * "Permission required" copy but no clickable anchor.
 */
async function assertInertTile(page: Page, testId: string): Promise<void> {
  const tile = page.locator(`[data-testid="${testId}"]`);
  await expect(tile, `tile [data-testid="${testId}"] is rendered`).toBeVisible({ timeout: 10_000 });
  await expect(
    tile.getByText(/Permission required/i),
    `tile [data-testid="${testId}"] shows "Permission required"`,
  ).toBeVisible();
  // The drill-through anchor MUST be absent — clicks must be inert.
  await expect(
    tile.locator('a'),
    `tile [data-testid="${testId}"] has no clickable anchor when perm-denied`,
  ).toHaveCount(0);
}

// =============================================================================
// Slice F — Dashboard drill-throughs · ACCOUNTANT happy path
// =============================================================================

test.describe('Slice F — dashboard drill-through · accountant happy path', () => {
  test.use({ persona: 'accountant' as Persona });

  // ---------------------------------------------------------------------------
  // KPI 1.A — Today's sales → /reports/sales-daily?branchId=...&businessDate=...
  // ---------------------------------------------------------------------------
  test.fail(
    'KPI tile "Today\'s sales" drills through to /reports/sales-daily with branch + business-date params',
    async ({ page }) => {
      await openDashboard(page);
      await clickTile(page, 'kpi-todays-sales');

      // URL contract — Plan §3 row 1: branchId stringified, businessDate ISO.
      await expect(page).toHaveURL(/\/reports\/sales-daily\?.*branchId=1.*/);
      await expect(page).toHaveURL(/businessDate=\d{4}-\d{2}-\d{2}/);

      // Destination page heading — the new /reports/sales-daily component
      // renders a Daily-sales report (frontend agent owns the component +
      // route — Plan §5 last row).
      await expect(page.getByRole('heading', { name: /Daily sales/i })).toBeVisible({ timeout: 15_000 });
    },
  );

  // ---------------------------------------------------------------------------
  // KPI 1.B — Stock alerts → /stock/balances?belowReorderOnly=true
  // ---------------------------------------------------------------------------
  test.fail(
    'KPI tile "Stock alerts" drills through to /stock/balances with belowReorderOnly=true pre-applied',
    async ({ page }) => {
      await openDashboard(page);
      await clickTile(page, 'kpi-stock-alerts');

      await expect(page).toHaveURL(/\/stock\/balances\?.*belowReorderOnly=true/);

      // The destination component pre-ticks the "below reorder only" checkbox
      // — frontend agent adds data-testid="below-reorder-filter" to the
      // existing #lowOnly control (balances.component.ts:54-58).
      await expect(page.locator('[data-testid="below-reorder-filter"]')).toBeChecked();
    },
  );

  // ---------------------------------------------------------------------------
  // KPI 1.C — Negative stock → /stock/balances?negativeOnly=true
  // ---------------------------------------------------------------------------
  test.fail(
    'KPI tile "Negative stock" drills through to /stock/balances with negativeOnly=true pre-applied',
    async ({ page }) => {
      await openDashboard(page);
      await clickTile(page, 'kpi-negative-stock');

      await expect(page).toHaveURL(/\/stock\/balances\?.*negativeOnly=true/);

      // The destination component grows a sibling "negative only" checkbox
      // (Plan §6.3 + audit KPI 1.C) — frontend agent adds
      // data-testid="negative-only-filter".
      await expect(page.locator('[data-testid="negative-only-filter"]')).toBeChecked();
    },
  );

  // ---------------------------------------------------------------------------
  // KPI 1.D — Open invoices → /sales/invoices?status=OPEN
  // ---------------------------------------------------------------------------
  test(
    'KPI tile "Open invoices" drills through to /sales/invoices?status=OPEN with bucket pre-selected',
    async ({ page }) => {
      await openDashboard(page);
      await clickTile(page, 'kpi-open-invoices');

      await expect(page).toHaveURL(/\/sales\/invoices\?.*status=OPEN/);

      // Status bucket dropdown on invoices.component.ts — frontend agent adds
      // data-testid="status-filter". The dropdown carries the bucket aliases
      // ALL / OPEN / OVERDUE plus the raw SalesInvoiceStatus values.
      await expect(page.locator('[data-testid="status-filter"]')).toHaveValue('OPEN');
    },
  );

  // ---------------------------------------------------------------------------
  // KPI 1.E — AR outstanding → /sales/invoices?status=OPEN&sort=outstanding,desc
  // ---------------------------------------------------------------------------
  test(
    'KPI tile "AR outstanding" drills through to /sales/invoices with OPEN + sort=outstanding,desc',
    async ({ page }) => {
      await openDashboard(page);
      await clickTile(page, 'kpi-ar-outstanding');

      await expect(page).toHaveURL(/\/sales\/invoices\?.*status=OPEN/);
      // sort param is URL-encoded — match both literal and encoded forms.
      await expect(page).toHaveURL(/sort=(outstanding%2Cdesc|outstanding,desc)/);
      await expect(page.locator('[data-testid="status-filter"]')).toHaveValue('OPEN');
    },
  );

  // ---------------------------------------------------------------------------
  // ALERT 2.A — "N SKUs at or below reorder" → /stock/balances?belowReorderOnly=true
  // ---------------------------------------------------------------------------
  test.fail(
    'Alert "stock alerts" drills through to /stock/balances?belowReorderOnly=true with checkbox ticked',
    async ({ page }) => {
      await openDashboard(page);
      await clickTile(page, 'alert-stock-alerts');

      await expect(page).toHaveURL(/\/stock\/balances\?.*belowReorderOnly=true/);
      await expect(page.locator('[data-testid="below-reorder-filter"]')).toBeChecked();
    },
  );

  // ---------------------------------------------------------------------------
  // ALERT 2.B — "N items in negative stock" → /stock/balances?negativeOnly=true
  // ---------------------------------------------------------------------------
  test.fail(
    'Alert "negative stock" drills through to /stock/balances?negativeOnly=true with checkbox ticked',
    async ({ page }) => {
      await openDashboard(page);
      await clickTile(page, 'alert-negative-stock');

      await expect(page).toHaveURL(/\/stock\/balances\?.*negativeOnly=true/);
      await expect(page.locator('[data-testid="negative-only-filter"]')).toBeChecked();
    },
  );

  // ---------------------------------------------------------------------------
  // ALERT 2.C — "N invoices past due" → /sales/invoices?status=OVERDUE
  //              (re-pointed from /debt placeholder hub — Plan §3 last row)
  // ---------------------------------------------------------------------------
  test.fail(
    'Alert "overdue invoices" drills through to /sales/invoices?status=OVERDUE (NOT to /debt)',
    async ({ page }) => {
      await openDashboard(page);
      await clickTile(page, 'alert-overdue-invoices');

      await expect(page).toHaveURL(/\/sales\/invoices\?.*status=OVERDUE/);
      // Critical: must NOT land on the /debt placeholder hub.
      await expect(page).not.toHaveURL(/\/debt(\?|$)/);
      await expect(page.locator('[data-testid="status-filter"]')).toHaveValue('OVERDUE');
    },
  );

  // ---------------------------------------------------------------------------
  // ALERT 2.D — "N LPOs awaiting approval" → /procurement/lpos?status=PENDING_APPROVAL
  //              (re-pointed from /procurement hub — Plan §3 last row)
  // ---------------------------------------------------------------------------
  test.fail(
    'Alert "LPOs awaiting approval" drills through to /procurement/lpos?status=PENDING_APPROVAL',
    async ({ page }) => {
      await openDashboard(page);
      await clickTile(page, 'alert-lpos-pending');

      await expect(page).toHaveURL(/\/procurement\/lpos\?.*status=PENDING_APPROVAL/);
      // Critical: must NOT land on the /procurement hub.
      await expect(page).not.toHaveURL(/\/procurement(\?|$)/);
      await expect(page.locator('[data-testid="status-filter"]')).toHaveValue('PENDING_APPROVAL');
    },
  );

  // ---------------------------------------------------------------------------
  // Deep-link shareability — pasting the URL directly applies the filter,
  // proving the destination component reads from queryParamMap (not just
  // from the click event).
  // ---------------------------------------------------------------------------
  test(
    'shareable deep link: /sales/invoices?status=OVERDUE pre-applies the OVERDUE bucket',
    async ({ page }) => {
      await page.goto('/sales/invoices?status=OVERDUE');
      await expect(page.getByRole('heading', { name: /Sales invoices/i })).toBeVisible({ timeout: 15_000 });
      await expect(page.locator('[data-testid="status-filter"]')).toHaveValue('OVERDUE');
    },
  );
});

// =============================================================================
// Slice F — Dashboard drill-throughs · SALES-CLERK partial path
// =============================================================================

test.describe('Slice F — dashboard drill-through · sales-clerk partial path', () => {
  // sales-clerk holds SALES.MANAGE_INVOICE (so the sales-invoices destination
  // is reachable) but NOT SALES.REPORT.AR_SUMMARY — the AR tiles must render
  // inert ("Permission required") and the AR-summary-derived alert (overdue
  // invoices) MUST NOT surface since the count never came back.
  test.use({ persona: 'sales-clerk' as Persona });

  // ---------------------------------------------------------------------------
  // KPI 1.D — Open invoices → still reachable (sales-clerk holds the perm).
  // ---------------------------------------------------------------------------
  test(
    'sales-clerk CAN drill into /sales/invoices?status=OPEN via the Open invoices tile',
    async ({ page }) => {
      await openDashboard(page);
      await clickTile(page, 'kpi-open-invoices');
      await expect(page).toHaveURL(/\/sales\/invoices\?.*status=OPEN/);
      await expect(page.locator('[data-testid="status-filter"]')).toHaveValue('OPEN');
    },
  );

  // ---------------------------------------------------------------------------
  // KPI 1.E — AR outstanding → inert, "Permission required".
  // ---------------------------------------------------------------------------
  test.fail(
    'sales-clerk sees "AR outstanding" tile inert with "Permission required" and no anchor',
    async ({ page }) => {
      await openDashboard(page);
      await assertInertTile(page, 'kpi-ar-outstanding');
    },
  );

  // ---------------------------------------------------------------------------
  // KPI 1.D — Open invoices count is also gated behind AR_SUMMARY in the
  // current dashboard wiring (`arPermissionDenied` controls both
  // openInvoices + arOutstanding values — dashboard.component.ts:367-369).
  // Plan §8 question 3 leaves this resolved by the rollup endpoint's per-
  // fragment auth. Today: tile shows "Permission required". Expected post-
  // Slice-F: tile is a live link OR inert per the contract — for the QA
  // gate we pin "inert when AR_SUMMARY denied" since the count comes from
  // the same arSummary call.
  // ---------------------------------------------------------------------------
  test.fail(
    'sales-clerk sees "Open invoices" KPI inert with "Permission required" (count gated by AR_SUMMARY)',
    async ({ page }) => {
      await openDashboard(page);
      await assertInertTile(page, 'kpi-open-invoices');
    },
  );
});

// =============================================================================
// Slice F — Dashboard drill-throughs · STORE-MANAGER partial path
// =============================================================================

test.describe('Slice F — dashboard drill-through · store-manager partial path', () => {
  // store-manager holds STOCK.COUNT but NOT SALES.REPORT.AR_SUMMARY (so AR
  // tiles render "Permission required") and NOT SALES.MANAGE_INVOICE
  // (so the open / overdue drill-throughs would 403 even if attempted) and
  // NOT PROCUREMENT.MANAGE_LPO / .READ (so the LPO drill-through is inert).
  test.use({ persona: 'store-manager' as Persona });

  // ---------------------------------------------------------------------------
  // KPI 1.B — Stock alerts → CAN drill (store-manager has STOCK.COUNT).
  // ---------------------------------------------------------------------------
  test(
    'store-manager CAN drill into /stock/balances?belowReorderOnly=true via Stock alerts tile',
    async ({ page }) => {
      await openDashboard(page);
      await clickTile(page, 'kpi-stock-alerts');
      await expect(page).toHaveURL(/\/stock\/balances\?.*belowReorderOnly=true/);
      await expect(page.locator('[data-testid="below-reorder-filter"]')).toBeChecked();
    },
  );

  // ---------------------------------------------------------------------------
  // KPI 1.E — AR outstanding → inert ("Permission required").
  // ---------------------------------------------------------------------------
  test.fail(
    'store-manager sees "AR outstanding" tile inert (no SALES.REPORT.AR_SUMMARY)',
    async ({ page }) => {
      await openDashboard(page);
      await assertInertTile(page, 'kpi-ar-outstanding');
    },
  );

  // ---------------------------------------------------------------------------
  // ALERT 2.D — LPO pending → store-manager lacks PROCUREMENT.MANAGE_LPO[.READ]
  // so the lposPendingApproval count comes back null (403) and the alert
  // simply does not surface. Verify the row is absent — the alert grid's
  // empty state ("All clear" / no alert-lpos-pending row) is the signal.
  // ---------------------------------------------------------------------------
  test(
    'store-manager: LPOs-awaiting-approval alert is suppressed (no PROCUREMENT.MANAGE_LPO)',
    async ({ page }) => {
      await openDashboard(page);
      // Alert row must be absent — the count came back null via 403, so the
      // pushIfPositive branch never fired.
      await expect(page.locator('[data-testid="alert-lpos-pending"]')).toHaveCount(0);
    },
  );
});
