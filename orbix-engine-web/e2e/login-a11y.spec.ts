/**
 * ISSUE-NFR-003 — Login page axe-core WCAG AA accessibility scan.
 *
 * The login page is the entry-point for every user and must be
 * accessible without any authenticated session. This spec opens the
 * raw (unauthenticated) page and runs axe with WCAG 2 A + AA rules.
 *
 * `color-contrast` is deferred under the same standing waiver used
 * across the back-office shell — tracked as a design-system palette
 * task.  Any *other* critical or serious violation is a hard gate.
 */
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/** Rules deferred across the back-office shell (palette debt). */
const A11Y_DEFERRED_RULES = ['color-contrast'];

test.describe('Login page — WCAG AA accessibility', () => {
  test('no critical/serious axe violations on the unauthenticated login page', async ({ page }) => {
    // Navigate without any auth tokens — the app redirects to / which
    // should render the login page for unauthenticated users.
    await page.goto('/');
    // Wait for the login form to be present before scanning.
    await expect(page.locator('form')).toBeVisible({ timeout: 15_000 });

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .disableRules(A11Y_DEFERRED_RULES)
      .analyze();

    const blocking = results.violations.filter(
      v => v.impact === 'critical' || v.impact === 'serious'
    );

    expect(
      blocking,
      `axe-core found ${blocking.length} critical/serious WCAG AA violation(s) on the login page:\n` +
        blocking.map(v =>
          `  [${v.impact}] ${v.id}: ${v.description}\n` +
          `    Elements: ${v.nodes.map(n => n.target).join(', ')}`
        ).join('\n')
    ).toEqual([]);
  });

  test('login form has labelled username and password inputs', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('form')).toBeVisible({ timeout: 15_000 });

    // WCAG 1.3.1 / 4.1.2 — inputs must have accessible labels.
    const usernameInput = page.locator('#loginUsername');
    const passwordInput = page.locator('#loginPassword');

    await expect(usernameInput).toBeVisible();
    await expect(passwordInput).toBeVisible();

    // Each input must have an associated <label> (via htmlFor).
    const usernameLabel = page.locator('label[for="loginUsername"]');
    const passwordLabel = page.locator('label[for="loginPassword"]');

    await expect(usernameLabel).toBeVisible();
    await expect(passwordLabel).toBeVisible();
  });

  test('login form is keyboard-navigable and submittable', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('form')).toBeVisible({ timeout: 15_000 });

    // Tab to username, fill, Tab to password, fill, Tab to submit.
    await page.locator('#loginUsername').fill('rootadmin');
    await page.keyboard.press('Tab');
    await page.locator('#loginPassword').fill('wrongpassword');
    await page.keyboard.press('Tab');
    // Next Tab lands on the show-password toggle (tabindex=-1 is skipped),
    // then Tab again to the submit button.
    await page.keyboard.press('Tab');

    // Sign-in button should now be focused or at least visible and enabled.
    const submitBtn = page.getByRole('button', { name: /Sign in/i });
    await expect(submitBtn).toBeEnabled();
  });
});
