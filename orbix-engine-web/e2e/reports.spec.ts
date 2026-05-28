import { type Page } from '@playwright/test';
import { test, expect } from './personas.fixture';
import type { Persona } from './test-users';
import AxeBuilder from '@axe-core/playwright';

/**
 * End-to-end Reports breadth spec (Slice I release gate, TDD-first).
 *
 * Drives the /reports surface — sales-daily, sales-summary, z-history — through
 * the real Angular UI against the QA-parity container at http://localhost:8081/.
 * All scenarios are tagged `test.fail`; they flip green when the Slice I
 * frontend tasks land (slice-i-reports-breadth-plan.md §7 task #2).
 *
 * Happy-path actor: `accountant` — already holds SALES.REPORT.AR_SUMMARY +
 * SALES.MANAGE_INVOICE (widened in Slice F). No persona widening is required
 * for this slice; the plan §4 confirms the `accountant` is the canonical
 * report persona and that no new permissions are being introduced.
 *
 * Negative-path actor: `cashier` — holds POS.* perms only.
 *
 * IMPORTANT — backend perm gating:
 * SalesReportController is explicitly NOT @PreAuthorize-gated per its own
 * javadoc comment: "not @PreAuthorize-gated (authentication still required),
 * since sales reports are inspected by every manager / accountant." This means:
 *   - The HTTP endpoints return 200 to any authenticated user.
 *   - Scenario 4 (cashier permission gate) therefore CANNOT assert a backend
 *     403. It can only assert on the FRONTEND route-guard / sidebar state.
 *   - If the Angular app has no route-guard or sidebar hide for the Reports
 *     section, scenario 4 will fail loudly — the correct resolution is to add
 *     a FE guard in a follow-up, NOT to add @PreAuthorize on the backend for
 *     this slice.
 *   - Scenario 4 is the scenario most likely to remain `test.fail` AFTER the
 *     other scenarios flip, unless the FE agent explicitly implements a
 *     permission guard on the Reports routes/sidebar.
 *
 * Frontend test-id contract (frontend agent wires these — same pattern as
 * debt.spec.ts and customer-returns.spec.ts):
 *
 *   data-testid              | Component                              | Purpose
 *   -------------------------|----------------------------------------|--------------------------------------
 *   export-csv               | report-export-menu.component.ts        | CSV export button
 *   export-excel             | report-export-menu.component.ts        | Excel export button
 *   export-pdf               | report-export-menu.component.ts        | PDF export button
 *   report-empty-state       | sales-daily / sales-summary / z-history | Empty-state marker element
 *   report-permission-state  | reports index or each report component  | "Permission required" inert state
 *   reports-nav-section      | shell sidebar                          | Wrapper around the Reports nav group
 *
 * Scenarios (all `test.fail` — Slice I — flips when FE lands):
 *   1. Sales-daily export CSV (accountant)
 *   2. Sales-summary export Excel (accountant)
 *   3. Z-history export PDF (accountant)
 *   4. Cashier permission gate — FE-side only (see note above)
 *   5. Empty-state export buttons disabled (accountant)
 */

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const BRANCH_ID = '1';
const TODAY = new Date().toISOString().slice(0, 10);   // YYYY-MM-DD

/** A business date guaranteed to have zero sales — far in the past. */
const EMPTY_DATE = '1900-01-01';

/** Z-history default range: last 7 days. */
const ZHISTORY_FROM = (() => {
  const d = new Date();
  d.setDate(d.getDate() - 7);
  return d.toISOString().slice(0, 10);
})();
const ZHISTORY_TO = TODAY;

// ---------------------------------------------------------------------------
// Axe helper — same deferred rules as the rest of the suite
// ---------------------------------------------------------------------------

const A11Y_DEFERRED_RULES = ['color-contrast', 'scrollable-region-focusable'];

async function assertNoSeriousA11yViolations(page: Page, contextLabel: string): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa'])
    .disableRules(A11Y_DEFERRED_RULES)
    .analyze();
  const blocking = results.violations.filter(
    v => v.impact === 'critical' || v.impact === 'serious',
  );
  expect(
    blocking,
    `axe-core found ${blocking.length} critical/serious violations on ${contextLabel}: `
      + blocking.map(v => `${v.id} (${v.impact})`).join(', '),
  ).toEqual([]);
}

async function dismissDismissableAlerts(page: Page): Promise<void> {
  const closes = page.locator('.alert button.btn-close');
  const count = await closes.count();
  for (let i = 0; i < count; i++) {
    const btn = closes.nth(i);
    if (await btn.isVisible().catch(() => false)) {
      await btn.click().catch(() => { /* alert may have auto-dismissed */ });
    }
  }
}

// ---------------------------------------------------------------------------
// Branch context injection (mirrors debt.spec.ts / customer-returns.spec.ts)
// ---------------------------------------------------------------------------

test.beforeEach(async ({ page }) => {
  await page.addInitScript((branchId: string) => {
    localStorage.setItem('orbix.activeBranchId', branchId);
  }, BRANCH_ID);
});

// =============================================================================
// SCENARIO 1 — Sales-daily CSV export
// Actor: accountant
// Slice I — flips when FE lands
// =============================================================================

test.describe('Slice I — sales-daily CSV export', () => {
  test.use({ persona: 'accountant' as Persona });

  test.fail(
    // Slice I — flips when FE lands
    'accountant opens /reports/sales-daily, sees a populated table, clicks CSV, file downloads with correct filename and content',
    async ({ page }) => {
      // Navigate to the sales-daily report with today's date and branch 1.
      await page.goto(`/reports/sales-daily?branchId=${BRANCH_ID}&businessDate=${TODAY}`);
      await dismissDismissableAlerts(page);

      // The report must render — wait for the export button group to appear,
      // which implies the loading skeleton resolved and rows are visible.
      const csvBtn = page.locator('[data-testid="export-csv"]');
      await expect(csvBtn).toBeVisible({ timeout: 20_000 });
      await expect(csvBtn).toBeEnabled();

      // Axe-core on the report page before triggering the download.
      await assertNoSeriousA11yViolations(page, '/reports/sales-daily');

      // Trigger CSV download.
      const [download] = await Promise.all([
        page.waitForEvent('download'),
        csvBtn.click(),
      ]);

      // Filename must match /sales-daily.*\.csv$/i.
      expect(
        download.suggestedFilename(),
        'CSV download filename',
      ).toMatch(/sales-daily.*\.csv$/i);

      // Stream must be non-empty and contain a header + at least 1 data row.
      const stream = await download.createReadStream();
      const chunks: Buffer[] = [];
      await new Promise<void>((resolve, reject) => {
        stream.on('data', (chunk: Buffer) => chunks.push(chunk));
        stream.on('end', resolve);
        stream.on('error', reject);
      });
      const csvText = Buffer.concat(chunks).toString('utf-8');
      expect(csvText.length, 'CSV download is non-empty').toBeGreaterThan(0);
      const lines = csvText.split('\n').filter(l => l.trim().length > 0);
      expect(lines.length, 'CSV has header + at least 1 data row').toBeGreaterThanOrEqual(2);
    },
  );
});

// =============================================================================
// SCENARIO 2 — Sales-summary Excel export
// Actor: accountant
// Slice I — flips when FE lands
// =============================================================================

test.describe('Slice I — sales-summary Excel export', () => {
  test.use({ persona: 'accountant' as Persona });

  test.fail(
    // Slice I — flips when FE lands
    'accountant opens /reports/sales-summary, sees KPI tiles + summary table, clicks Excel, xlsx file downloads',
    async ({ page }) => {
      await page.goto(`/reports/sales-summary?branchId=${BRANCH_ID}&businessDate=${TODAY}`);
      await dismissDismissableAlerts(page);

      // Wait for the Excel export button — signals page is loaded and populated.
      const excelBtn = page.locator('[data-testid="export-excel"]');
      await expect(excelBtn).toBeVisible({ timeout: 20_000 });
      await expect(excelBtn).toBeEnabled();

      // Axe-core on the summary report page.
      await assertNoSeriousA11yViolations(page, '/reports/sales-summary');

      // Trigger Excel download.
      const [download] = await Promise.all([
        page.waitForEvent('download'),
        excelBtn.click(),
      ]);

      // Filename must match /sales-summary.*\.xlsx$/i.
      const filename = download.suggestedFilename();
      expect(filename, 'Excel download filename').toMatch(/sales-summary.*\.xlsx$/i);

      // Extension must be .xlsx (consistent with filename assertion above;
      // explicit guard so a mis-typed .xls extension fails loudly).
      expect(filename.toLowerCase().endsWith('.xlsx'), 'extension is .xlsx').toBe(true);

      // File must be non-empty (lazy xlsx library must have resolved and written bytes).
      const stream = await download.createReadStream();
      const chunks: Buffer[] = [];
      await new Promise<void>((resolve, reject) => {
        stream.on('data', (chunk: Buffer) => chunks.push(chunk));
        stream.on('end', resolve);
        stream.on('error', reject);
      });
      expect(
        Buffer.concat(chunks).length,
        'Excel download has non-zero size',
      ).toBeGreaterThan(0);
    },
  );
});

// =============================================================================
// SCENARIO 3 — Z-history PDF export
// Actor: accountant
// Slice I — flips when FE lands
// =============================================================================

test.describe('Slice I — z-history PDF export', () => {
  test.use({ persona: 'accountant' as Persona });

  test.fail(
    // Slice I — flips when FE lands
    'accountant opens /reports/z-history for the last 7 days, sees >= 0 rows, clicks PDF, valid PDF file downloads',
    async ({ page }) => {
      await page.goto(
        `/reports/z-history?branchId=${BRANCH_ID}&fromDate=${ZHISTORY_FROM}&toDate=${ZHISTORY_TO}`,
      );
      await dismissDismissableAlerts(page);

      // PDF button visible implies the page loaded (may show 0 rows — that is
      // acceptable per AC; the button is only disabled on the empty-state date
      // tested in scenario 5, not on dates where the container has no Z-reports).
      const pdfBtn = page.locator('[data-testid="export-pdf"]');
      await expect(pdfBtn).toBeVisible({ timeout: 20_000 });
      await expect(pdfBtn).toBeEnabled();

      // Axe-core on the z-history page.
      await assertNoSeriousA11yViolations(page, '/reports/z-history');

      // Trigger PDF download.
      const [download] = await Promise.all([
        page.waitForEvent('download'),
        pdfBtn.click(),
      ]);

      // Filename must match /z-history.*\.pdf$/i.
      expect(download.suggestedFilename(), 'PDF download filename').toMatch(/z-history.*\.pdf$/i);

      // Read the first 8 bytes and verify the PDF magic bytes (%PDF-).
      const stream = await download.createReadStream();
      const firstChunk = await new Promise<Buffer>((resolve, reject) => {
        stream.once('data', (chunk: Buffer) => {
          stream.destroy();
          resolve(chunk);
        });
        stream.once('error', reject);
      });
      expect(
        firstChunk.slice(0, 5).toString('ascii'),
        'PDF file starts with %PDF- magic bytes',
      ).toBe('%PDF-');
    },
  );
});

// =============================================================================
// SCENARIO 4 — Cashier permission gate (FE-side only)
// Actor: cashier
// Slice I — flips when FE lands
//
// NOTE ON BACKEND GATING:
// SalesReportController is NOT @PreAuthorize-gated — any authenticated user
// receives HTTP 200 from the report endpoints. This scenario therefore tests
// only the FRONTEND route-guard / sidebar state:
//   a) The sidebar "Reports" section is hidden OR individual links are absent
//      for a persona without an explicit report permission.
//   b) Navigating directly to /reports/sales-summary renders a
//      "Permission required" inert state (data-testid="report-permission-state")
//      rather than showing data.
// If the Angular app has no route-guard and no sidebar hide, this scenario
// fails loudly — the correct fix is a FE guard, NOT a backend @PreAuthorize
// for Slice I. Track as a follow-up if the FE agent does not implement guarding.
// =============================================================================

test.describe('Slice I — cashier permission gate (FE route guard)', () => {
  test.use({ persona: 'cashier' as Persona });

  test.fail(
    // Slice I — flips when FE lands (requires FE permission guard on reports routes / sidebar)
    'cashier sees no Reports section in sidebar and reports pages show permission-required state',
    async ({ page }) => {
      // Navigate to the dashboard — this triggers the Angular shell to render.
      await page.goto('/dashboard');
      await dismissDismissableAlerts(page);

      // ASSERTION A: sidebar "Reports" section is absent OR all items inside
      // it are hidden / disabled for a cashier persona.
      // The sidebar wrapper carries data-testid="reports-nav-section".
      // Accept two outcomes: (1) element is absent entirely, or (2) element
      // exists but is not visible.
      const navSection = page.locator('[data-testid="reports-nav-section"]');
      const navCount = await navSection.count();
      if (navCount > 0) {
        // If the wrapper renders, all nested report links must be hidden.
        await expect(navSection).not.toBeVisible(
          { timeout: 5_000 },
        );
      }
      // If navCount === 0 the assertion is satisfied: section is absent.

      // ASSERTION B: deep-linking to /reports/sales-summary renders the
      // permission-required inert state, NOT a populated report.
      await page.goto('/reports/sales-summary');
      await dismissDismissableAlerts(page);

      // The inert state element must be present and visible.
      const permState = page.locator('[data-testid="report-permission-state"]');
      await expect(permState).toBeVisible({ timeout: 10_000 });

      // The export buttons must NOT appear (no data, no export surface).
      await expect(page.locator('[data-testid="export-csv"]')).toHaveCount(0);
      await expect(page.locator('[data-testid="export-excel"]')).toHaveCount(0);
      await expect(page.locator('[data-testid="export-pdf"]')).toHaveCount(0);
    },
  );
});

// =============================================================================
// SCENARIO 5 — Empty-state: export buttons disabled
// Actor: accountant
// Slice I — flips when FE lands
// =============================================================================

test.describe('Slice I — empty-state export buttons disabled', () => {
  test.use({ persona: 'accountant' as Persona });

  test.fail(
    // Slice I — flips when FE lands
    'accountant opens /reports/sales-summary for 1900-01-01 (empty), sees empty state, export buttons are disabled with tooltip',
    async ({ page }) => {
      // Use a date that is guaranteed to have zero data on any QA container.
      await page.goto(
        `/reports/sales-summary?branchId=${BRANCH_ID}&businessDate=${EMPTY_DATE}`,
      );
      await dismissDismissableAlerts(page);

      // The empty-state marker must be visible.
      const emptyState = page.locator('[data-testid="report-empty-state"]');
      await expect(emptyState).toBeVisible({ timeout: 20_000 });

      // All three export buttons must be present but DISABLED.
      // (They render so the user knows the feature exists, but are inert.)
      const csvBtn = page.locator('[data-testid="export-csv"]');
      const excelBtn = page.locator('[data-testid="export-excel"]');
      const pdfBtn = page.locator('[data-testid="export-pdf"]');

      await expect(csvBtn, 'CSV button visible in empty state').toBeVisible({ timeout: 10_000 });
      await expect(excelBtn, 'Excel button visible in empty state').toBeVisible({ timeout: 10_000 });
      await expect(pdfBtn, 'PDF button visible in empty state').toBeVisible({ timeout: 10_000 });

      await expect(csvBtn, 'CSV button disabled in empty state').toBeDisabled();
      await expect(excelBtn, 'Excel button disabled in empty state').toBeDisabled();
      await expect(pdfBtn, 'PDF button disabled in empty state').toBeDisabled();

      // Each button must carry a tooltip attributing the disabled state to
      // "No data to export". Check via the `title` attribute (simplest
      // cross-browser signal; the FE may alternatively use aria-label or a
      // Bootstrap tooltip — adjust if the FE agent uses a different mechanism).
      for (const [btn, label] of [
        [csvBtn, 'CSV'],
        [excelBtn, 'Excel'],
        [pdfBtn, 'PDF'],
      ] as const) {
        const titleAttr = await btn.getAttribute('title');
        const ariaLabel = await btn.getAttribute('aria-label');
        const hasTooltip =
          (titleAttr !== null && /no data to export/i.test(titleAttr)) ||
          (ariaLabel !== null && /no data to export/i.test(ariaLabel));
        expect(hasTooltip, `${label} disabled button carries "No data to export" tooltip`).toBe(true);
      }

      // Verify no download is triggered by clicking the disabled buttons.
      // Playwright's page.waitForEvent('download') with a short timeout should
      // time out (no download event fires). We assert the button is disabled
      // above; this secondary check guards against disabled-but-clickable bugs.
      let downloadFired = false;
      page.once('download', () => { downloadFired = true; });
      // Attempt clicks — these should be no-ops since the buttons are disabled.
      await csvBtn.click({ force: true }).catch(() => { /* click may be blocked */ });
      await excelBtn.click({ force: true }).catch(() => { /* click may be blocked */ });
      await pdfBtn.click({ force: true }).catch(() => { /* click may be blocked */ });
      // Brief wait to let any spurious download event propagate.
      await page.waitForTimeout(1_000);
      expect(downloadFired, 'no download triggered from disabled export buttons').toBe(false);

      // Axe-core on the empty-state report page.
      await assertNoSeriousA11yViolations(page, '/reports/sales-summary (empty state)');
    },
  );
});

// =============================================================================
// Slice J — Stock reports (US-RPT-004, US-RPT-005, US-RPT-006)
//
// All six scenarios are tagged test.fail; they flip when the Slice J frontend
// tasks land (stock-card.component, negative-stock.component,
// stock-movers.component). See docs/design/slice-j-reports-tail-plan.md.
//
// Happy-path actor: `stock-controller` — holds STOCK.COUNT (and STOCK.ADJUST +
// STOCK.TRANSFER) from Slice E1. All three stock-report endpoints on
// StockReportController are @PreAuthorize("hasAuthority('STOCK.COUNT')").
// The stock-card endpoint (GET /api/v1/stock-card on StockController) is also
// readable by the same persona. No persona widening required.
//
// Negative-path actor: `cashier` — holds POS.* only; no STOCK.COUNT.
//
// DTO wire-shape notes (verified against actual Java records 2026-05-28):
//
//   StockMoveDto fields: id, at (Instant), itemId, branchId, companyId, qty
//     (BigDecimal, signed), costAmount, direction (enum), moveType (enum),
//     refType, refId, actorId, notes, batchId, sectionId,
//     consumptionCategory, authorisedByUserId.
//     — No uid, no runningBalance, no docKind/docNumber/movedAt.
//     — FE renders moveType and refType as the "type / doc reference" columns.
//     — "at" is the timestamp field (not "movedAt").
//
//   ItemBranchBalanceDto fields: itemId, branchId, qtyOnHand, qtyReserved,
//     qtyInTransit, avgCost, lastCost, reorderMin, reorderMax, binLocation,
//     lastMovedAt.
//     — No itemCode, itemName, branchName. FE must resolve names client-side
//       or the BE needs a follow-up projection. Flag to backend agent if
//       the negative-stock page AC requires item/branch names in the table.
//
//   ItemMovementRowDto fields: itemId, itemCode, itemName, movedQty
//     (BigDecimal, NOT totalQty), qtyOnHand.
//     — No rank, moveCount, lastMoveAt. FE derives rank from list index.
//
// Frontend test-id contract (FE agent wires these — same shared component as
// Slice I, extended to the stock-report pages):
//
//   data-testid              | Purpose
//   -------------------------|----------------------------------------------
//   export-csv               | CSV export trigger
//   export-excel             | Excel export trigger
//   export-pdf               | PDF export trigger
//   report-empty-state       | Visible when result set is empty
//   report-permission-state  | Visible when current user lacks STOCK.COUNT
//   reports-nav-section      | Sidebar Reports nav group (Slice I contract)
//   stock-card-item-input    | Item typeahead input on /reports/stock-card
//   stock-card-table-row     | Each row of the stock-card move table
//   stock-movers-fast-tab    | "Fast movers" tab trigger
//   stock-movers-slow-tab    | "Slow movers" tab trigger
//   stock-movers-table-row   | Each row in whichever movers tab is active
//   negative-stock-table-row | Each row on /reports/negative-stock
// =============================================================================

// =============================================================================
// SCENARIO 6 — Stock-card report happy-path
// Actor: stock-controller
// US-RPT-004 / Slice J — flips when FE lands
// =============================================================================

test.describe('Slice J — stock-card report happy-path (US-RPT-004)', () => {
  test.use({ persona: 'stock-controller' as Persona });

  test.fail(
    // Slice J — flips when FE lands
    'stock-controller opens /reports/stock-card, selects an item + branch via typeahead, sees move table, exports CSV, axe-AA clean',
    async ({ page }) => {
      // Navigate to stock-card with branch pre-selected via query param.
      // The page should default to branchId=1 (HQ) via the active-branch
      // localStorage key set in beforeEach; passing it explicitly is safer.
      await page.goto(`/reports/stock-card?branchId=${BRANCH_ID}`);
      await dismissDismissableAlerts(page);

      // The item typeahead must be visible — it is the primary filter control
      // and signals the component has mounted.
      const itemInput = page.locator('[data-testid="stock-card-item-input"]');
      await expect(itemInput).toBeVisible({ timeout: 20_000 });

      // Type a prefix to trigger the typeahead. Any item that was GRN'd on
      // the QA container will have at least one StockMove. The seeded catalog
      // from Slice E1 setup typically contains items starting with common
      // prefixes; "item" or the first item code works. Adjust prefix if the
      // QA-container seed uses a different naming convention.
      await itemInput.fill('item');
      // Wait for dropdown suggestions to appear.
      const suggestion = page.locator('[data-testid="stock-card-item-input"] ~ * [role="option"]').first();
      await expect(suggestion).toBeVisible({ timeout: 10_000 });
      await suggestion.click();

      // After selecting an item the report loads automatically (or the user
      // clicks a "Run" button — the FE agent decides; either way the table
      // must appear).
      const firstRow = page.locator('[data-testid="stock-card-table-row"]').first();
      await expect(firstRow).toBeVisible({ timeout: 20_000 });

      // Axe-AA scan before triggering the download.
      await assertNoSeriousA11yViolations(page, '/reports/stock-card');

      // CSV export.
      const csvBtn = page.locator('[data-testid="export-csv"]');
      await expect(csvBtn).toBeVisible();
      await expect(csvBtn).toBeEnabled();

      const [download] = await Promise.all([
        page.waitForEvent('download'),
        csvBtn.click(),
      ]);

      expect(download.suggestedFilename(), 'CSV filename').toMatch(/stock-card.*\.csv$/i);

      // Stream must be non-empty and contain header + at least 1 data row.
      const stream = await download.createReadStream();
      const chunks: Buffer[] = [];
      await new Promise<void>((resolve, reject) => {
        stream.on('data', (chunk: Buffer) => chunks.push(chunk));
        stream.on('end', resolve);
        stream.on('error', reject);
      });
      const csvText = Buffer.concat(chunks).toString('utf-8');
      expect(csvText.length, 'CSV is non-empty').toBeGreaterThan(0);
      const lines = csvText.split('\n').filter(l => l.trim().length > 0);
      expect(lines.length, 'CSV has header + at least 1 data row').toBeGreaterThanOrEqual(2);
    },
  );
});

// =============================================================================
// SCENARIO 7 — Negative-stock report happy-path
// Actor: stock-controller
// US-RPT-006 / Slice J — flips when FE lands
// =============================================================================

test.describe('Slice J — negative-stock report happy-path (US-RPT-006)', () => {
  test.use({ persona: 'stock-controller' as Persona });

  test.fail(
    // Slice J — flips when FE lands
    'stock-controller opens /reports/negative-stock, page renders (empty-or-rows), click-through to stock-card, exports Excel, axe-AA clean',
    async ({ page }) => {
      await page.goto(`/reports/negative-stock?branchId=${BRANCH_ID}`);
      await dismissDismissableAlerts(page);

      // The Excel export button appearing signals the page has mounted and the
      // API call resolved. On a fresh QA container the list may be empty —
      // that is an acceptable result; we test the empty-state separately.
      // Here we tolerate both populated and empty, but require the export
      // surface to be present (even if disabled in empty case).
      const excelBtn = page.locator('[data-testid="export-excel"]');
      await expect(excelBtn).toBeVisible({ timeout: 20_000 });

      // Axe-AA scan.
      await assertNoSeriousA11yViolations(page, '/reports/negative-stock');

      // If the table has rows, clicking the first one must navigate to
      // /reports/stock-card with itemId and branchId query params.
      const firstRow = page.locator('[data-testid="negative-stock-table-row"]').first();
      const hasRows = await firstRow.isVisible().catch(() => false);
      if (hasRows) {
        // Record current URL before click.
        await firstRow.click();
        // After drill-through the URL must contain /reports/stock-card.
        await expect(page).toHaveURL(/\/reports\/stock-card/, { timeout: 10_000 });
        // Navigate back to the negative-stock page to continue.
        await page.goBack();
        await dismissDismissableAlerts(page);
        await expect(excelBtn).toBeVisible({ timeout: 10_000 });
      }

      // Excel export — only assert if button is enabled (empty-state disables).
      const isEnabled = await excelBtn.isEnabled();
      if (isEnabled) {
        const [download] = await Promise.all([
          page.waitForEvent('download'),
          excelBtn.click(),
        ]);
        const filename = download.suggestedFilename();
        expect(filename, 'Excel filename').toMatch(/negative-stock.*\.xlsx$/i);
        expect(filename.toLowerCase().endsWith('.xlsx'), 'extension is .xlsx').toBe(true);
        const stream = await download.createReadStream();
        const chunks: Buffer[] = [];
        await new Promise<void>((resolve, reject) => {
          stream.on('data', (chunk: Buffer) => chunks.push(chunk));
          stream.on('end', resolve);
          stream.on('error', reject);
        });
        expect(Buffer.concat(chunks).length, 'Excel file non-empty').toBeGreaterThan(0);
      }
    },
  );
});

// =============================================================================
// SCENARIO 8 — Stock movers (fast + slow tabs)
// Actor: stock-controller
// US-RPT-005 / Slice J — flips when FE lands
// =============================================================================

/** Last 30 days — the default window the fast/slow-movers endpoint uses. */
const MOVERS_FROM = (() => {
  const d = new Date();
  d.setDate(d.getDate() - 30);
  return d.toISOString().slice(0, 10);
})();
const MOVERS_TO = TODAY;

test.describe('Slice J — stock movers (fast + slow tabs, US-RPT-005)', () => {
  test.use({ persona: 'stock-controller' as Persona });

  test.fail(
    // Slice J — flips when FE lands
    'stock-controller opens /reports/stock-movers, fast-tab shows rows, switches to slow-tab, exports PDF per tab, axe-AA clean',
    async ({ page }) => {
      await page.goto(
        `/reports/stock-movers?branchId=${BRANCH_ID}&from=${MOVERS_FROM}&to=${MOVERS_TO}`,
      );
      await dismissDismissableAlerts(page);

      // Default tab must be Fast movers.
      const fastTab = page.locator('[data-testid="stock-movers-fast-tab"]');
      await expect(fastTab).toBeVisible({ timeout: 20_000 });

      // Table rows in the fast tab (if any data exists in the date window).
      const fastRows = page.locator('[data-testid="stock-movers-table-row"]');
      // Wait for the loading state to resolve — at least the empty-state OR
      // the first row must appear.
      await Promise.race([
        fastRows.first().waitFor({ state: 'visible', timeout: 20_000 }).catch(() => {}),
        page.locator('[data-testid="report-empty-state"]').waitFor({ state: 'visible', timeout: 20_000 }).catch(() => {}),
      ]);

      // Axe-AA scan on the fast-movers tab.
      await assertNoSeriousA11yViolations(page, '/reports/stock-movers (fast tab)');

      // PDF export for the fast tab — only if enabled.
      const pdfBtn = page.locator('[data-testid="export-pdf"]');
      await expect(pdfBtn).toBeVisible();
      const fastPdfEnabled = await pdfBtn.isEnabled();
      if (fastPdfEnabled) {
        const [fastDownload] = await Promise.all([
          page.waitForEvent('download'),
          pdfBtn.click(),
        ]);
        expect(fastDownload.suggestedFilename(), 'fast-movers PDF filename').toMatch(/fast.*movers.*\.pdf$/i);
        const stream = await fastDownload.createReadStream();
        const firstChunk = await new Promise<Buffer>((resolve, reject) => {
          stream.once('data', (chunk: Buffer) => { stream.destroy(); resolve(chunk); });
          stream.once('error', reject);
        });
        expect(firstChunk.slice(0, 5).toString('ascii'), 'PDF magic bytes').toBe('%PDF-');
      }

      // Switch to Slow movers tab.
      const slowTab = page.locator('[data-testid="stock-movers-slow-tab"]');
      await expect(slowTab).toBeVisible();
      await slowTab.click();

      // Wait for slow-tab content to resolve.
      await Promise.race([
        page.locator('[data-testid="stock-movers-table-row"]').first().waitFor({ state: 'visible', timeout: 15_000 }).catch(() => {}),
        page.locator('[data-testid="report-empty-state"]').waitFor({ state: 'visible', timeout: 15_000 }).catch(() => {}),
      ]);

      // Axe-AA scan on the slow-movers tab.
      await assertNoSeriousA11yViolations(page, '/reports/stock-movers (slow tab)');

      // PDF export for the slow tab — only if enabled.
      const slowPdfEnabled = await pdfBtn.isEnabled();
      if (slowPdfEnabled) {
        const [slowDownload] = await Promise.all([
          page.waitForEvent('download'),
          pdfBtn.click(),
        ]);
        expect(slowDownload.suggestedFilename(), 'slow-movers PDF filename').toMatch(/slow.*movers.*\.pdf$/i);
        const stream = await slowDownload.createReadStream();
        const firstChunk = await new Promise<Buffer>((resolve, reject) => {
          stream.once('data', (chunk: Buffer) => { stream.destroy(); resolve(chunk); });
          stream.once('error', reject);
        });
        expect(firstChunk.slice(0, 5).toString('ascii'), 'PDF magic bytes').toBe('%PDF-');
      }
    },
  );
});

// =============================================================================
// SCENARIO 9 — Cashier permission gate on stock-report pages (FE-side)
// Actor: cashier
// Slice J — flips when FE lands
//
// Backend gating: StockReportController carries class-level
// @PreAuthorize("hasAuthority('STOCK.COUNT')"). Cashier holds POS.* only.
// HTTP 403 will fire on the API call; the FE must translate that into the
// permission-required inert state (report-permission-state test-id) rather
// than a raw error page, because a raw error is an accessibility/UX defect.
// This scenario asserts on the FE state; the 403 is the cause, not the check.
// =============================================================================

test.describe('Slice J — cashier permission gate on stock-report pages', () => {
  test.use({ persona: 'cashier' as Persona });

  test.fail(
    // Slice J — flips when FE lands (requires FE permission guard or 403-to-state mapping)
    'cashier opens /reports/stock-card, /reports/negative-stock, /reports/stock-movers — each shows permission-required state, no export buttons',
    async ({ page }) => {
      const pages: Array<{ path: string; label: string }> = [
        { path: '/reports/stock-card', label: 'stock-card' },
        { path: '/reports/negative-stock', label: 'negative-stock' },
        { path: '/reports/stock-movers', label: 'stock-movers' },
      ];

      for (const { path, label } of pages) {
        await page.goto(path);
        await dismissDismissableAlerts(page);

        // The permission-required inert state element must be present and
        // visible. Accept either a route-guard redirect to a permission page
        // OR the component rendering the inert state inline.
        const permState = page.locator('[data-testid="report-permission-state"]');
        await expect(
          permState,
          `${label}: report-permission-state visible for cashier`,
        ).toBeVisible({ timeout: 10_000 });

        // No export buttons may appear — no data, no export surface.
        await expect(
          page.locator('[data-testid="export-csv"]'),
          `${label}: no CSV button for cashier`,
        ).toHaveCount(0);
        await expect(
          page.locator('[data-testid="export-excel"]'),
          `${label}: no Excel button for cashier`,
        ).toHaveCount(0);
        await expect(
          page.locator('[data-testid="export-pdf"]'),
          `${label}: no PDF button for cashier`,
        ).toHaveCount(0);
      }
    },
  );
});

// =============================================================================
// SCENARIO 10 — Empty-state on stock-card (item with no moves)
// Actor: stock-controller
// Slice J — flips when FE lands
// =============================================================================

test.describe('Slice J — stock-card empty-state (item with no moves)', () => {
  test.use({ persona: 'stock-controller' as Persona });

  test.fail(
    // Slice J — flips when FE lands
    'stock-controller opens /reports/stock-card with an itemId that has no moves, sees empty-state, export buttons disabled with tooltip',
    async ({ page }) => {
      // Pass an itemId that is guaranteed to have no stock moves. On any fresh
      // QA container, a newly created item (not yet GRN'd) has zero moves.
      // Use itemId=9999 as a sentinel that is almost certainly absent; the
      // endpoint returns an empty page, not a 404, so the FE must render the
      // empty state. If the QA container happens to have id 9999 with moves,
      // use a sufficiently large value (e.g. 99999) — the scenario is resilient
      // as long as the empty state is reachable by ID.
      await page.goto(
        `/reports/stock-card?itemId=9999&branchId=${BRANCH_ID}`,
      );
      await dismissDismissableAlerts(page);

      // The empty-state marker must be visible within a reasonable timeout
      // (page loads, API returns empty page, component resolves the state).
      const emptyState = page.locator('[data-testid="report-empty-state"]');
      await expect(emptyState, 'empty-state visible for no-move item').toBeVisible({ timeout: 20_000 });

      // All export buttons must be present but disabled.
      const csvBtn = page.locator('[data-testid="export-csv"]');
      const excelBtn = page.locator('[data-testid="export-excel"]');
      const pdfBtn = page.locator('[data-testid="export-pdf"]');

      await expect(csvBtn, 'CSV button present').toBeVisible({ timeout: 10_000 });
      await expect(excelBtn, 'Excel button present').toBeVisible({ timeout: 10_000 });
      await expect(pdfBtn, 'PDF button present').toBeVisible({ timeout: 10_000 });

      await expect(csvBtn, 'CSV button disabled').toBeDisabled();
      await expect(excelBtn, 'Excel button disabled').toBeDisabled();
      await expect(pdfBtn, 'PDF button disabled').toBeDisabled();

      // Each disabled button must carry "No data to export" via title or aria-label.
      for (const [btn, label] of [
        [csvBtn, 'CSV'],
        [excelBtn, 'Excel'],
        [pdfBtn, 'PDF'],
      ] as const) {
        const titleAttr = await btn.getAttribute('title');
        const ariaLabel = await btn.getAttribute('aria-label');
        const hasTooltip =
          (titleAttr !== null && /no data to export/i.test(titleAttr)) ||
          (ariaLabel !== null && /no data to export/i.test(ariaLabel));
        expect(hasTooltip, `${label} disabled button carries "No data to export" tooltip`).toBe(true);
      }

      // Axe-AA scan on the empty-state page.
      await assertNoSeriousA11yViolations(page, '/reports/stock-card (empty state)');
    },
  );
});

// =============================================================================
// SCENARIO 11 — Empty-state on stock-movers (date range with no moves)
// Actor: stock-controller
// Slice J — flips when FE lands
// =============================================================================

test.describe('Slice J — stock-movers empty-state (date range with no moves)', () => {
  test.use({ persona: 'stock-controller' as Persona });

  test.fail(
    // Slice J — flips when FE lands
    'stock-controller opens /reports/stock-movers with to=1900-01-01 (empty window), both tabs show empty-state, exports disabled',
    async ({ page }) => {
      // Using EMPTY_DATE as `to` guarantees the window [1900-01-01, 1900-01-01]
      // contains zero stock moves on any QA container. Mirror the Slice I trick
      // (scenario 5 uses the same EMPTY_DATE to pin zero-sales-summary).
      await page.goto(
        `/reports/stock-movers?branchId=${BRANCH_ID}&from=${EMPTY_DATE}&to=${EMPTY_DATE}`,
      );
      await dismissDismissableAlerts(page);

      // Fast movers tab is the default; the empty-state must appear.
      const emptyState = page.locator('[data-testid="report-empty-state"]');
      await expect(emptyState, 'fast-tab empty-state visible').toBeVisible({ timeout: 20_000 });

      // Export buttons must be present but disabled on the fast tab.
      // (stock-movers exposes CSV + PDF; Excel is not wired for this report.)
      const csvBtn = page.locator('[data-testid="export-csv"]');
      const pdfBtn = page.locator('[data-testid="export-pdf"]');

      await expect(csvBtn, 'CSV button present on fast-tab empty').toBeVisible({ timeout: 10_000 });
      await expect(pdfBtn, 'PDF button present on fast-tab empty').toBeVisible({ timeout: 10_000 });

      await expect(csvBtn, 'CSV button disabled on fast-tab empty').toBeDisabled();
      await expect(pdfBtn, 'PDF button disabled on fast-tab empty').toBeDisabled();

      // Switch to Slow movers tab.
      const slowTab = page.locator('[data-testid="stock-movers-slow-tab"]');
      await expect(slowTab, 'slow tab visible').toBeVisible();
      await slowTab.click();

      // Empty-state must also appear on the slow tab.
      await expect(emptyState, 'slow-tab empty-state visible').toBeVisible({ timeout: 10_000 });

      // Disabled exports on slow tab.
      await expect(csvBtn, 'CSV button disabled on slow-tab empty').toBeDisabled();
      await expect(pdfBtn, 'PDF button disabled on slow-tab empty').toBeDisabled();

      // Axe-AA on the empty-movers page (either tab; slow is already active).
      await assertNoSeriousA11yViolations(page, '/reports/stock-movers (empty state)');
    },
  );
});
