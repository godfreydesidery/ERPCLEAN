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
