import type { FullConfig } from '@playwright/test';
import { bootstrapTestPersonas } from './test-users';

/**
 * Playwright global setup. Runs once per `playwright test` invocation, before
 * any worker spins up.
 *
 * Calls `bootstrapTestPersonas` against the QA container so every persona
 * (cashier, store-manager, accountant, procurement-officer, supervisor,
 * sales-rep) is guaranteed to exist with the right role + permission set
 * before any spec tries to log in as one. Idempotent — safe to re-run.
 *
 * The base URL is taken from the same source the rest of the suite uses:
 * the first project's `baseURL` if set, otherwise `PLAYWRIGHT_BASE_URL`,
 * otherwise the localhost QA default. Matches `playwright.config.ts`.
 */
export default async function globalSetup(config: FullConfig): Promise<void> {
  const projectBaseUrl = (config.projects[0]?.use?.baseURL as string | undefined) ?? undefined;
  const baseUrl =
    projectBaseUrl ?? process.env['PLAYWRIGHT_BASE_URL'] ?? 'http://localhost:8081/';

  // eslint-disable-next-line no-console
  console.log(`[global-setup] bootstrapping test personas against ${baseUrl}`);
  await bootstrapTestPersonas(baseUrl);
  // eslint-disable-next-line no-console
  console.log('[global-setup] test personas ready');
}
