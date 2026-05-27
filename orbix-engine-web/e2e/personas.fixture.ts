import { test as base, type APIRequestContext } from '@playwright/test';
import { TEST_USERS, type Persona } from './test-users';

/**
 * Persona auth fixture.
 *
 * Drop-in replacement for the rootadmin-only `auth.fixture.ts`. Tests opt in
 * to a persona via the `persona` test-info option, e.g.
 *
 *   test.describe('Cash adjustments', () => {
 *     test.use({ persona: 'accountant' });
 *     test('post a CASH_BOX adjustment', async ({ page }) => { ... });
 *   });
 *
 * Behaviour mirrors `auth.fixture.ts`: it programmatically POSTs to
 * `/api/v1/auth/login` (bypassing the login UI), then injects the tokens
 * into `sessionStorage` via `addInitScript` so the Angular app sees an
 * authenticated session from the first navigation. `storageState` is NOT
 * an option — Angular stores tokens in `sessionStorage`, which Playwright's
 * `storageState` does not capture.
 *
 * Default persona is `procurement-officer` so specs that haven't picked a
 * persona yet still get a non-rootadmin identity (and will fail on missing
 * permission, which is exactly the signal we want).
 */

const TOKEN_KEY = 'orbix.access';
const REFRESH_KEY = 'orbix.refresh';
const USER_KEY = 'orbix.user';

type AuthBundle = {
  accessToken: string;
  refreshToken: string | null;
  user: unknown;
};

// One auth bundle per persona per worker — avoids re-logging-in for every test.
const cache = new Map<Persona, AuthBundle>();

async function loginAs(request: APIRequestContext, persona: Persona): Promise<AuthBundle> {
  const cached = cache.get(persona);
  if (cached) return cached;

  const user = TEST_USERS[persona];
  const resp = await request.post('/api/v1/auth/login', {
    data: { username: user.username, password: user.password },
  });
  if (!resp.ok()) {
    throw new Error(
      `[personas] login as ${persona} (${user.username}) failed — HTTP ${resp.status()} ${await resp.text()}. ` +
        `Did global-setup run? (e2e/global-setup.ts bootstraps the roster.)`,
    );
  }
  const body = await resp.json();
  const data = body?.data;
  if (!data?.accessToken) {
    throw new Error(`[personas] login response missing accessToken for ${persona}: ${JSON.stringify(body)}`);
  }
  const bundle: AuthBundle = {
    accessToken: data.accessToken,
    refreshToken: data.refreshToken ?? null,
    user: data.user ?? null,
  };
  cache.set(persona, bundle);
  return bundle;
}

export const test = base.extend<{ persona: Persona }>({
  persona: ['procurement-officer', { option: true }],
  page: async ({ page, request, persona }, use) => {
    const auth = await loginAs(request, persona);
    await page.addInitScript(
      ({ token, refresh, user, tokenKey, refreshKey, userKey }) => {
        sessionStorage.setItem(tokenKey, token);
        if (refresh !== null) sessionStorage.setItem(refreshKey, refresh);
        if (user !== null) sessionStorage.setItem(userKey, JSON.stringify(user));
      },
      {
        token: auth.accessToken,
        refresh: auth.refreshToken,
        user: auth.user,
        tokenKey: TOKEN_KEY,
        refreshKey: REFRESH_KEY,
        userKey: USER_KEY,
      },
    );
    await use(page);
  },
});

export { expect } from '@playwright/test';
