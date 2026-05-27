import { test, expect, Page, Locator } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * End-to-end party spec.
 *
 * Drives the four party screens (customers, suppliers, employees, sales
 * agents) end-to-end in the running QA container. Every test creates a
 * real, persistently-stored entry through the actual Angular form, then
 * exercises the archive / reactivate lifecycle and a regression assertion
 * that there is no "Inactive" filter pill (the dead INACTIVE state is
 * gone from the UI).
 *
 * The QA container has pre-existing seed data (CUST0001..CUST0005, etc.),
 * so each test suffixes its party name with a unique-per-run token to
 * avoid collisions on unique constraints (party name, employee code,
 * agent code).
 */

// Short, alphanumeric, deterministic-per-run token. Five base-36 digits is
// enough headroom to avoid collisions across reruns within the same minute.
const RUN_TAG = Date.now().toString(36).slice(-5).toUpperCase();

/** Open the page and wait for the primary header to land. */
async function openListPage(page: Page, path: string, expectedHeading: RegExp): Promise<void> {
  await page.goto(path);
  await expect(page.getByRole('heading', { name: expectedHeading })).toBeVisible({ timeout: 20_000 });
}

/**
 * Click the {@code orbix-search-select} trigger whose placeholder matches
 * the given regex, then pick the first option in the dropdown. Used for
 * the branch / route pickers on the employee + sales-agent forms.
 */
async function pickFirstFromSearchSelect(page: Page, placeholder: RegExp): Promise<void> {
  const trigger = page.getByRole('button', { name: placeholder }).first();
  await trigger.click();
  // The panel renders with class `ss__panel`; pick the first option inside.
  const firstOption = page.locator('.ss__panel .ss__option').first();
  await expect(firstOption).toBeVisible({ timeout: 5_000 });
  await firstOption.click();
}

/** Switch the status filter to the named pill (Active / Archived / All). */
async function switchFilter(page: Page, label: 'Active' | 'Archived' | 'All'): Promise<void> {
  // Scope to the status-pills container so we don't accidentally match the
  // ACTIVE badge text on a table row.
  await page.locator('.status-pills').getByRole('button', { name: new RegExp(`^${label}$`) }).click();
  // Give the list a beat to re-fetch / re-render.
  await page.waitForTimeout(400);
}

/**
 * Locate the row carrying the just-created entry. Returns the {@code tr}
 * locator. Assumes the unique name token is present in the row.
 */
function rowForName(page: Page, name: string): Locator {
  return page.locator('tbody tr').filter({ hasText: name });
}

/** Click the archive icon-button inside the given row. */
async function archiveRow(row: Locator): Promise<void> {
  await row.getByRole('button', { name: /archive/i }).click();
}

/** Click the reactivate icon-button inside the given row. */
async function activateRow(row: Locator): Promise<void> {
  await row.getByRole('button', { name: /reactivate|activate/i }).click();
}

/** Regression check: the dead INACTIVE filter pill must never reappear. */
async function assertNoInactivePill(page: Page): Promise<void> {
  await expect(
    page.locator('.status-pills').getByRole('button', { name: /^Inactive$/i })
  ).toHaveCount(0);
}

/** Axe-core: assert no critical/serious accessibility violations on the page. */
async function assertNoSeriousA11yViolations(page: Page, contextLabel: string): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa'])
    .analyze();
  const blocking = results.violations.filter(v => v.impact === 'critical' || v.impact === 'serious');
  expect(
    blocking,
    `axe-core found ${blocking.length} critical/serious violations on ${contextLabel}: ` +
      blocking.map(v => `${v.id} (${v.impact})`).join(', ')
  ).toEqual([]);
}

/**
 * Walk a row through archive → archived-filter → reactivate → active-filter.
 * Used at the end of every party-type test once a row has been created.
 */
async function lifecycleArchiveAndReactivate(page: Page, name: string): Promise<void> {
  const active = rowForName(page, name);
  await expect(active).toBeVisible();
  await expect(active).toContainText('ACTIVE');

  await archiveRow(active);
  await expect(rowForName(page, name)).toHaveCount(0, { timeout: 10_000 });

  await switchFilter(page, 'Archived');
  const archived = rowForName(page, name);
  await expect(archived).toBeVisible({ timeout: 10_000 });
  await expect(archived).toContainText('ARCHIVED');

  await activateRow(archived);
  await expect(rowForName(page, name)).toHaveCount(0, { timeout: 10_000 });

  await switchFilter(page, 'Active');
  const reactivated = rowForName(page, name);
  await expect(reactivated).toBeVisible({ timeout: 10_000 });
  await expect(reactivated).toContainText('ACTIVE');
}

// -----------------------------------------------------------------------------
// Customers
// -----------------------------------------------------------------------------

test.describe('Party · Customers', () => {
  const customerName = `Mama Asha Duka ${RUN_TAG}`;
  const tin = `100-100-${RUN_TAG.slice(-3)}`;
  const phone = `+25571210${RUN_TAG.slice(-4)}`;

  test('create through the form, then archive and reactivate', async ({ page }) => {
    await openListPage(page, '/party/customers', /^Customers$/);
    await assertNoInactivePill(page);

    await page.getByRole('button', { name: /^New customer$/i }).click();
    // "Create new party" path — registers a brand-new party along with the
    // customer role in one transaction.
    await page.getByRole('button', { name: /Create new party/i }).click();

    await page.locator('input[name="pname"]').fill(customerName);
    // Category default is INDIVIDUAL; flip to BUSINESS for a retail/wholesale duka.
    await page.locator('select[name="pcat"]').selectOption('BUSINESS');
    await page.locator('input[name="ptin"]').fill(tin);
    await page.locator('input[name="pphone"]').fill(phone);

    await page.locator('input[name="cl"]').fill('500000');
    await page.locator('input[name="ct"]').fill('30');

    await page.getByRole('button', { name: /Create customer/i }).click();

    // After save the list re-renders. Pause for the async refresh.
    await expect(page.getByRole('button', { name: /^New customer$/i })).toBeVisible({ timeout: 15_000 });

    // Backend-allocated CUST00xx code shows up next to the new name.
    const row = rowForName(page, customerName);
    await expect(row).toBeVisible({ timeout: 10_000 });
    await expect(row).toContainText(/CUST\d{4,}/);

    await assertNoSeriousA11yViolations(page, '/party/customers');
    await lifecycleArchiveAndReactivate(page, customerName);
  });
});

// -----------------------------------------------------------------------------
// Suppliers
// -----------------------------------------------------------------------------

test.describe('Party · Suppliers', () => {
  const supplierName = `Sayona Drinks Ltd ${RUN_TAG}`;
  const tin = `200-200-${RUN_TAG.slice(-3)}`;

  test('create through the form, then archive and reactivate', async ({ page }) => {
    await openListPage(page, '/party/suppliers', /^Suppliers$/);
    await assertNoInactivePill(page);

    await page.getByRole('button', { name: /^New supplier$/i }).click();
    await page.getByRole('button', { name: /Create new party/i }).click();

    await page.locator('input[name="pname"]').fill(supplierName);
    await page.locator('select[name="pcat"]').selectOption('BUSINESS');
    await page.locator('input[name="ptin"]').fill(tin);

    await page.locator('input[name="pt"]').fill('30');
    await page.locator('input[name="lt"]').fill('5');

    await page.getByRole('button', { name: /Create supplier/i }).click();

    await expect(page.getByRole('button', { name: /^New supplier$/i })).toBeVisible({ timeout: 15_000 });

    const row = rowForName(page, supplierName);
    await expect(row).toBeVisible({ timeout: 10_000 });
    await expect(row).toContainText(/SUP\d{4,}/);

    await assertNoSeriousA11yViolations(page, '/party/suppliers');
    await lifecycleArchiveAndReactivate(page, supplierName);
  });
});

// -----------------------------------------------------------------------------
// Employees
// -----------------------------------------------------------------------------

test.describe('Party · Employees', () => {
  const employeeName = `Asha Cashier ${RUN_TAG}`;
  // Employee code must be unique within the company. RUN_TAG is 5 chars so
  // E + tag is well within the 16-char DB cap.
  const employeeCode = `E${RUN_TAG}`;
  const today = new Date().toISOString().slice(0, 10);

  test('create through the form, then archive and reactivate', async ({ page }) => {
    await openListPage(page, '/party/employees', /^Employees$/);
    await assertNoInactivePill(page);

    await page.getByRole('button', { name: /^New employee$/i }).click();
    await page.getByRole('button', { name: /Create new party/i }).click();

    await page.locator('input[name="ecode"]').fill(employeeCode);
    await page.locator('input[name="pname"]').fill(employeeName);

    await page.locator('input[name="jt"]').fill('Cashier');
    // Branch picker (search-select) — placeholder reads "Select a branch…".
    await pickFirstFromSearchSelect(page, /Select a branch/i);
    await page.locator('input[name="hd"]').fill(today);

    await page.getByRole('button', { name: /Create employee/i }).click();

    await expect(page.getByRole('button', { name: /^New employee$/i })).toBeVisible({ timeout: 15_000 });

    const row = rowForName(page, employeeName);
    await expect(row).toBeVisible({ timeout: 10_000 });
    await expect(row).toContainText(employeeCode);

    await assertNoSeriousA11yViolations(page, '/party/employees');
    await lifecycleArchiveAndReactivate(page, employeeName);
  });
});

// -----------------------------------------------------------------------------
// Sales agents
// -----------------------------------------------------------------------------

test.describe('Party · Sales agents', () => {
  const agentName = `Field Agent ${RUN_TAG}`;
  const agentCode = `A${RUN_TAG}`;

  test('create through the form, then archive and reactivate', async ({ page }) => {
    await openListPage(page, '/party/agents', /^Sales agents$/);
    await assertNoInactivePill(page);

    await page.getByRole('button', { name: /^New agent$/i }).click();
    await page.getByRole('button', { name: /Create new party/i }).click();

    await page.locator('input[name="acode"]').fill(agentCode);
    await page.locator('input[name="pname"]').fill(agentName);

    await page.locator('input[name="cr"]').fill('0.05');

    // Route is optional in the model but the QA seed has at least one
    // ACTIVE route. Pick the first one if present; otherwise skip.
    const routeTrigger = page.getByRole('button', { name: /^—$/ }).first();
    if (await routeTrigger.isVisible().catch(() => false)) {
      try {
        await routeTrigger.click();
        const firstOption = page.locator('.ss__panel .ss__option').first();
        if (await firstOption.isVisible({ timeout: 2_000 }).catch(() => false)) {
          await firstOption.click();
        } else {
          // Close the popover with Escape if no options materialised.
          await page.keyboard.press('Escape');
        }
      } catch {
        // Best-effort; the route picker is optional.
      }
    }

    // Branch is required.
    await pickFirstFromSearchSelect(page, /Select a branch/i);

    await page.getByRole('button', { name: /Create sales agent/i }).click();

    await expect(page.getByRole('button', { name: /^New agent$/i })).toBeVisible({ timeout: 15_000 });

    const row = rowForName(page, agentName);
    await expect(row).toBeVisible({ timeout: 10_000 });
    await expect(row).toContainText(agentCode);

    await assertNoSeriousA11yViolations(page, '/party/agents');
    await lifecycleArchiveAndReactivate(page, agentName);
  });
});
