import { test as base, type APIRequestContext } from '@playwright/test';

/**
 * Auth fixture: programmatically logs in via /api/v1/auth/login and injects
 * the resulting tokens into sessionStorage via addInitScript before every
 * page navigation. Replaces the storageState approach because the Angular
 * app stores tokens in sessionStorage, which Playwright's storageState
 * does not capture.
 *
 * Override via env so CI / remote-QA runs can inject rotated creds.
 */
const ROOTADMIN_USER = process.env['ORBIX_QA_USER'] ?? 'rootadmin';
const ROOTADMIN_PASS = process.env['ORBIX_QA_PASS'] ?? 'SKp315goPN8Nb0yJtMCCD7cm';

const TOKEN_KEY = 'orbix.access';
const REFRESH_KEY = 'orbix.refresh';
const USER_KEY = 'orbix.user';

type AuthBundle = {
  accessToken: string;
  refreshToken: string | null;
  user: unknown;
};

let cachedAuth: AuthBundle | null = null;

async function fetchAuth(request: APIRequestContext): Promise<AuthBundle> {
  if (cachedAuth) return cachedAuth;
  const resp = await request.post('/api/v1/auth/login', {
    data: { username: ROOTADMIN_USER, password: ROOTADMIN_PASS },
  });
  if (!resp.ok()) {
    throw new Error(`Login failed: HTTP ${resp.status()} ${await resp.text()}`);
  }
  const body = await resp.json();
  const data = body?.data;
  if (!data?.accessToken) {
    throw new Error(`Login response missing accessToken: ${JSON.stringify(body)}`);
  }
  cachedAuth = {
    accessToken: data.accessToken,
    refreshToken: data.refreshToken ?? null,
    user: data.user ?? null,
  };
  return cachedAuth;
}

export const test = base.extend({
  page: async ({ page, request }, use) => {
    const auth = await fetchAuth(request);
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
