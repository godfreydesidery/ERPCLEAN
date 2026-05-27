import { expect, test as setup } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

/**
 * One-shot login that stashes the post-login storage state for every other
 * spec to reuse. Credentials are the QA-container rootadmin from
 * `orbix-engine-infra/qa/CREDENTIALS.local.md`.
 *
 * Overridable via env so CI can inject rotated creds without editing source.
 */
const ROOTADMIN_USER = process.env['ORBIX_QA_USER'] ?? 'rootadmin';
const ROOTADMIN_PASS = process.env['ORBIX_QA_PASS'] ?? 'SKp315goPN8Nb0yJtMCCD7cm';

const AUTH_FILE = 'e2e/.auth/rootadmin.json';

setup('authenticate as rootadmin', async ({ page }) => {
  mkdirSync(dirname(AUTH_FILE), { recursive: true });

  await page.goto('/login');

  await page.getByLabel('Username').fill(ROOTADMIN_USER);
  await page.getByLabel('Password').fill(ROOTADMIN_PASS);
  await page.getByRole('button', { name: /sign in/i }).click();

  // Successful sign-in either lands on `/` (dashboard) or `/change-password`
  // if the rootadmin still has the bootstrap flag. We accept both, but the
  // QA container should already be past first-run, so the dashboard is the
  // expected path.
  await expect(page).toHaveURL(/\/(?!login)/, { timeout: 15_000 });

  // Confirm a JWT was persisted; otherwise the storage state would be useless.
  const hasToken = await page.evaluate(() => {
    return Object.keys(localStorage).some(k => k.toLowerCase().includes('token'))
        || !!localStorage.getItem('orbix.auth')
        || document.cookie.length > 0;
  });
  expect(hasToken, 'expected a JWT or auth cookie after login').toBeTruthy();

  await page.context().storageState({ path: AUTH_FILE });
});
