import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for Orbix Engine Web e2e suite.
 *
 * The tests drive the running QA container (Angular SPA + Spring Boot API
 * served from the same origin) at http://localhost:8081/. We do **not** spin
 * up `ng serve` — the QA container is the system-under-test.
 *
 * Override the target with PLAYWRIGHT_BASE_URL when running against a
 * different deployment (e.g. http://16.170.11.41/ for remote QA).
 */
export default defineConfig({
  testDir: './e2e',
  // Bootstraps the persona roster (cashier, accountant, procurement-officer,
  // supervisor, ...) once before any spec runs. See e2e/test-users.ts.
  // Path is resolved by Playwright relative to this config file.
  globalSetup: './e2e/global-setup.ts',
  fullyParallel: false,           // Real persisted writes → keep deterministic ordering
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  workers: 1,                     // Same DB, same rootadmin session → one worker
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  timeout: 60_000,
  expect: { timeout: 10_000 },

  use: {
    baseURL: process.env['PLAYWRIGHT_BASE_URL'] ?? 'http://localhost:8081/',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      // Auth is injected per-test via e2e/auth.fixture.ts (sessionStorage
      // addInitScript) — storageState would not capture sessionStorage.
      testIgnore: /.*\.setup\.ts/,
    },
  ],
});
