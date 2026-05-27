/**
 * Test-persona roster + idempotent bootstrap helper for the e2e suite.
 *
 * The project rule: e2e tests run as realistic personas, not as `rootadmin`.
 * `rootadmin` is a bootstrap superadmin — using it for feature tests papers
 * over real permission gating, branch scoping, and role-separation bugs.
 *
 * The harness uses `rootadmin` ONLY here, at suite setup, to create the
 * persona accounts. Tests themselves log in as the persona declared via the
 * `personas.fixture.ts` `persona` option.
 *
 * Permission codes below are verified against:
 *   - orbix-engine-api/src/main/java/com/orbix/engine/modules/iam/domain/enums/Permissions.java
 *   - orbix-engine-api/src/main/resources/db/migration/common/V*__seed_*_permissions.sql
 *
 * Future-perm tolerance: some persona perm-code lists (notably the
 * `supervisor`'s `PROCUREMENT.CANCEL_LPO` + `GRN.CANCEL`) are not yet seeded
 * — Slice B will land them. The bootstrap looks up each requested code in
 * GET /api/v1/permissions and silently drops any that are missing, logging
 * a warning. On the next QA-image rebuild after Slice B, the same code-path
 * picks them up.
 */

import { request as pwRequest, type APIRequestContext } from '@playwright/test';

// ---------------------------------------------------------------------------
// Persona roster
// ---------------------------------------------------------------------------

export type Persona =
  | 'cashier'
  | 'cashier-with-override' // cashier + sales-invoice/receipt + SALES_INVOICE.OVERRIDE_CREDIT (Slice C)
  | 'store-manager'
  | 'accountant'
  | 'procurement-officer'
  | 'supervisor'      // can cancel POSTED GRNs / LPOs (GRN.CANCEL, PROCUREMENT.CANCEL_LPO)
  | 'sales-rep'
  | 'sales-clerk';    // POST sales invoices/receipts but NO override-credit, NO AR_SUMMARY

export interface TestUser {
  username: string;     // e.g. 'qa.cashier'
  password: string;     // shared known value, e.g. 'TestPass!1234'
  fullName: string;     // displayName on the AppUser
  defaultBranchId: number | null; // 1 (HQ) for branch-scoped personas; null for company-wide
  permissions: string[]; // permission codes, e.g. ['POS.SALE_POST', 'CASH.READ', ...]
}

/**
 * Shared password for every persona. Meets the >= 10-char policy
 * (see CreateUserRequestDto.password). NOT a secret — these accounts only
 * exist inside QA / dev containers.
 */
const TEST_PASSWORD = 'TestPass!1234';

/** Branch 1 = HQ on the fresh-volume QA container (see day-cash.spec.ts BRANCH_ID). */
const HQ_BRANCH = 1;

export const TEST_USERS: Record<Persona, TestUser> = {
  cashier: {
    username: 'qa.cashier',
    password: TEST_PASSWORD,
    fullName: 'QA Cashier',
    defaultBranchId: HQ_BRANCH,
    permissions: [
      'POS.SALE_POST',
      'POS.SALE_VOID',
      'POS.CASH_PICKUP.READ',
      'POS.PETTY_CASH.READ',
    ],
  },
  'cashier-with-override': {
    // Same POS footprint as `cashier`, plus sales-invoice/receipt + the
    // SALES_INVOICE.OVERRIDE_CREDIT permission Slice C introduces. Used by
    // sales.spec.ts to exercise the credit-limit override branch where the
    // baseline `sales-clerk` is blocked.
    username: 'qa.cashier.override',
    password: TEST_PASSWORD,
    fullName: 'QA Cashier-Override',
    defaultBranchId: HQ_BRANCH,
    permissions: [
      'POS.SALE_POST',
      'POS.SALE_VOID',
      'SALES_INVOICE.READ',
      'SALES_INVOICE.POST',
      'SALES_RECEIPT.READ',
      'SALES_RECEIPT.POST',
      // Slice-C-pending perm — bootstrap drops it gracefully if absent
      // (mirrors supervisor's PROCUREMENT.CANCEL_LPO / GRN.CANCEL pattern).
      'SALES_INVOICE.OVERRIDE_CREDIT',
    ],
  },
  'store-manager': {
    username: 'qa.store.manager',
    password: TEST_PASSWORD,
    fullName: 'QA Store Manager',
    defaultBranchId: HQ_BRANCH,
    permissions: [
      'DAY.OPEN',
      'DAY.CLOSE',
      'DAY.OVERRIDE',
      'DAY.READ',
      // No standalone STOCK.READ perm exists in the seeds yet — STOCK.COUNT
      // is the closest read-capable grant on the stock module.
      'STOCK.COUNT',
    ],
  },
  accountant: {
    username: 'qa.accountant',
    password: TEST_PASSWORD,
    fullName: 'QA Accountant',
    // Accountant is company-wide so they can post adjustments / deposits
    // across branches.
    defaultBranchId: null,
    permissions: [
      'CASH.READ',
      'CASH.ENTRY.READ',
      'CASH.BOOK.READ',
      'CASH.ADJUSTMENT.POST',
      'CASH.ADJUSTMENT.ARCHIVE',
      'CASH.BANK_DEPOSIT.POST',
      'CASH.BANK_DEPOSIT.ARCHIVE',
      // Slice-C-pending perm — bootstrap drops it gracefully if absent.
      // The accountant is the only persona who can read AR-summary reports.
      'SALES.REPORT.AR_SUMMARY',
    ],
  },
  'procurement-officer': {
    username: 'qa.procurement',
    password: TEST_PASSWORD,
    fullName: 'QA Procurement Officer',
    defaultBranchId: HQ_BRANCH,
    permissions: [
      'PROCUREMENT.MANAGE_LPO',
      'PROCUREMENT.APPROVE_LPO',
      // GRN.READ not yet a distinct seeded code; GRN.POST currently covers
      // read access on the GRN endpoints. Listed for clarity — gracefully
      // dropped at bootstrap if not present.
      'GRN.READ',
      'GRN.POST',
    ],
  },
  supervisor: {
    username: 'qa.supervisor',
    password: TEST_PASSWORD,
    fullName: 'QA Supervisor',
    defaultBranchId: HQ_BRANCH,
    permissions: [
      'PROCUREMENT.MANAGE_LPO',
      'PROCUREMENT.APPROVE_LPO',
      // Slice-B-pending perms — bootstrap drops them gracefully if absent
      // (see bootstrapTestPersonas below).
      'PROCUREMENT.CANCEL_LPO',
      'GRN.POST',
      'GRN.CANCEL',
      'DAY.OVERRIDE',
    ],
  },
  'sales-rep': {
    username: 'qa.sales.rep',
    password: TEST_PASSWORD,
    fullName: 'QA Sales Rep',
    defaultBranchId: HQ_BRANCH,
    permissions: [
      'CUSTOMER.CREATE',
      'CUSTOMER.UPDATE',
      'SALES_AGENT.CREATE',
    ],
  },
  'sales-clerk': {
    // Sales-invoice/receipt POST capability WITHOUT the credit-override perm
    // and WITHOUT AR_SUMMARY. Used by sales.spec.ts to prove (a) the
    // credit-limit gate actually fires on a non-override persona, and
    // (b) AR-summary is permission-gated, not open to anyone with
    // SALES_INVOICE.READ.
    username: 'qa.sales.clerk',
    password: TEST_PASSWORD,
    fullName: 'QA Sales Clerk',
    defaultBranchId: HQ_BRANCH,
    permissions: [
      'POS.SALE_POST',
      'SALES_INVOICE.READ',
      'SALES_INVOICE.POST',
      'SALES_RECEIPT.READ',
      'SALES_RECEIPT.POST',
    ],
  },
};

// ---------------------------------------------------------------------------
// Bootstrap
// ---------------------------------------------------------------------------

interface PermissionRow {
  id: number;
  code: string;
  description: string;
  module: string;
}

interface RoleSummary {
  id: number;
  uid: string;
  code: string;
  name: string;
  description: string | null;
  isSystem: boolean;
  status: string;
  permissionCount: number;
}

interface RoleDetail extends RoleSummary {
  permissions: PermissionRow[];
}

interface UserSummary {
  id: number;
  uid: string;
  username: string;
  displayName: string;
  defaultBranchId: number | null;
  status: string;
  mustChangePassword: boolean;
}

interface UserPage {
  content: UserSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

interface UserDetail extends UserSummary {
  grants: Array<{ uid: string; roleCode: string; branchId: number | null }>;
}

/** Coerce JSON:API-style stringified Long ids back to numbers for body fields. */
function toNum(v: unknown): number {
  if (typeof v === 'number') return v;
  if (typeof v === 'string' && /^-?\d+$/.test(v)) return Number(v);
  return Number(v);
}

function permsRoleCode(persona: Persona): string {
  // Role codes are <= 40 chars and uppercase by convention.
  return `QA_TEST_${persona.toUpperCase().replace(/-/g, '_')}`;
}

/**
 * Idempotently bootstrap the persona roster against the running QA container
 * using rootadmin's token. Creates a Role per persona with exactly the listed
 * permission codes, creates the user, assigns the role, sets the default
 * branch. On re-runs, no-ops if the user already exists with the right grants.
 *
 * Run once per test session (see e2e/global-setup.ts wired into
 * playwright.config.ts).
 */
export async function bootstrapTestPersonas(baseUrl: string): Promise<void> {
  const rootUser = process.env['ORBIX_QA_USER'] ?? 'rootadmin';
  const rootPass = process.env['ORBIX_QA_PASS'] ?? 'SKp315goPN8Nb0yJtMCCD7cm';

  const ctx = await pwRequest.newContext({ baseURL: baseUrl });
  try {
    const token = await loginRoot(ctx, rootUser, rootPass);

    const allPerms = await fetchPermissions(ctx, token);
    const permByCode = new Map<string, PermissionRow>(allPerms.map(p => [p.code, p]));

    const allRoles = await fetchRoles(ctx, token);
    const roleByCode = new Map<string, RoleSummary>(allRoles.map(r => [r.code, r]));

    for (const persona of Object.keys(TEST_USERS) as Persona[]) {
      await bootstrapOne(ctx, token, persona, permByCode, roleByCode);
    }
  } finally {
    await ctx.dispose();
  }
}

async function loginRoot(ctx: APIRequestContext, username: string, password: string): Promise<string> {
  const resp = await ctx.post('/api/v1/auth/login', {
    data: { username, password },
  });
  if (!resp.ok()) {
    throw new Error(`[personas] rootadmin login failed: HTTP ${resp.status()} ${await resp.text()}`);
  }
  const body = await resp.json();
  const accessToken = body?.data?.accessToken;
  if (!accessToken) {
    throw new Error(`[personas] login response missing accessToken: ${JSON.stringify(body)}`);
  }
  return accessToken as string;
}

function authHeaders(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function fetchPermissions(ctx: APIRequestContext, token: string): Promise<PermissionRow[]> {
  const resp = await ctx.get('/api/v1/permissions', { headers: authHeaders(token) });
  if (!resp.ok()) {
    throw new Error(`[personas] GET /permissions failed: HTTP ${resp.status()} ${await resp.text()}`);
  }
  const body = await resp.json();
  const list = (body?.data ?? body) as unknown[];
  return list.map(raw => {
    const p = raw as Record<string, unknown>;
    return { id: toNum(p['id']), code: String(p['code']), description: String(p['description'] ?? ''), module: String(p['module'] ?? '') };
  });
}

async function fetchRoles(ctx: APIRequestContext, token: string): Promise<RoleSummary[]> {
  const resp = await ctx.get('/api/v1/roles', { headers: authHeaders(token) });
  if (!resp.ok()) {
    throw new Error(`[personas] GET /roles failed: HTTP ${resp.status()} ${await resp.text()}`);
  }
  const body = await resp.json();
  const list = (body?.data ?? body) as unknown[];
  return list.map(raw => {
    const r = raw as Record<string, unknown>;
    return {
      id: toNum(r['id']),
      uid: String(r['uid']),
      code: String(r['code']),
      name: String(r['name']),
      description: (r['description'] as string | null) ?? null,
      isSystem: Boolean(r['isSystem']),
      status: String(r['status']),
      permissionCount: toNum(r['permissionCount']),
    };
  });
}

async function fetchRoleDetail(ctx: APIRequestContext, token: string, uid: string): Promise<RoleDetail> {
  const resp = await ctx.get(`/api/v1/roles/uid/${uid}`, { headers: authHeaders(token) });
  if (!resp.ok()) {
    throw new Error(`[personas] GET /roles/uid/${uid} failed: HTTP ${resp.status()} ${await resp.text()}`);
  }
  const body = await resp.json();
  const r = (body?.data ?? body) as Record<string, unknown>;
  const perms = ((r['permissions'] as unknown[]) ?? []).map(raw => {
    const p = raw as Record<string, unknown>;
    return { id: toNum(p['id']), code: String(p['code']), description: String(p['description'] ?? ''), module: String(p['module'] ?? '') };
  });
  return {
    id: toNum(r['id']),
    uid: String(r['uid']),
    code: String(r['code']),
    name: String(r['name']),
    description: (r['description'] as string | null) ?? null,
    isSystem: Boolean(r['isSystem']),
    status: String(r['status']),
    permissionCount: perms.length,
    permissions: perms,
  };
}

async function fetchUserByUsername(ctx: APIRequestContext, token: string, username: string): Promise<UserSummary | null> {
  const resp = await ctx.get(`/api/v1/users?q=${encodeURIComponent(username)}&size=50`, {
    headers: authHeaders(token),
  });
  if (!resp.ok()) {
    throw new Error(`[personas] GET /users?q=${username} failed: HTTP ${resp.status()} ${await resp.text()}`);
  }
  const body = await resp.json();
  const page = (body?.data ?? body) as UserPage;
  const exact = (page.content ?? []).find(u => u.username === username);
  if (!exact) return null;
  return {
    ...exact,
    id: toNum(exact.id),
    defaultBranchId: exact.defaultBranchId === null || exact.defaultBranchId === undefined ? null : toNum(exact.defaultBranchId),
  };
}

async function fetchUserDetail(ctx: APIRequestContext, token: string, uid: string): Promise<UserDetail> {
  const resp = await ctx.get(`/api/v1/users/uid/${uid}`, { headers: authHeaders(token) });
  if (!resp.ok()) {
    throw new Error(`[personas] GET /users/uid/${uid} failed: HTTP ${resp.status()} ${await resp.text()}`);
  }
  const body = await resp.json();
  const u = (body?.data ?? body) as Record<string, unknown>;
  const grants = ((u['grants'] as unknown[]) ?? []).map(raw => {
    const g = raw as Record<string, unknown>;
    return {
      uid: String(g['uid']),
      roleCode: String(g['roleCode'] ?? ''),
      branchId: g['branchId'] === null || g['branchId'] === undefined ? null : toNum(g['branchId']),
    };
  });
  return {
    id: toNum(u['id']),
    uid: String(u['uid']),
    username: String(u['username']),
    displayName: String(u['displayName']),
    defaultBranchId: u['defaultBranchId'] === null || u['defaultBranchId'] === undefined ? null : toNum(u['defaultBranchId']),
    status: String(u['status']),
    mustChangePassword: Boolean(u['mustChangePassword']),
    grants,
  };
}

async function createRole(
  ctx: APIRequestContext,
  token: string,
  code: string,
  name: string,
  description: string,
): Promise<RoleSummary> {
  const resp = await ctx.post('/api/v1/roles', {
    headers: authHeaders(token),
    data: { code, name, description },
  });
  if (!resp.ok()) {
    throw new Error(`[personas] POST /roles ${code} failed: HTTP ${resp.status()} ${await resp.text()}`);
  }
  const body = await resp.json();
  const r = (body?.data ?? body) as Record<string, unknown>;
  return {
    id: toNum(r['id']),
    uid: String(r['uid']),
    code: String(r['code']),
    name: String(r['name']),
    description: (r['description'] as string | null) ?? null,
    isSystem: Boolean(r['isSystem']),
    status: String(r['status']),
    permissionCount: 0,
  };
}

async function setRolePermissions(
  ctx: APIRequestContext,
  token: string,
  roleUid: string,
  permissionIds: number[],
): Promise<void> {
  const resp = await ctx.put(`/api/v1/roles/uid/${roleUid}/permissions`, {
    headers: authHeaders(token),
    data: { permissionIds },
  });
  if (!resp.ok()) {
    throw new Error(`[personas] PUT /roles/uid/${roleUid}/permissions failed: HTTP ${resp.status()} ${await resp.text()}`);
  }
}

async function createUser(
  ctx: APIRequestContext,
  token: string,
  user: TestUser,
): Promise<UserSummary> {
  const resp = await ctx.post('/api/v1/users', {
    headers: authHeaders(token),
    data: {
      username: user.username,
      displayName: user.fullName,
      defaultBranchId: user.defaultBranchId,
      password: user.password,
      mustChangePassword: false,
    },
  });
  if (!resp.ok()) {
    throw new Error(`[personas] POST /users ${user.username} failed: HTTP ${resp.status()} ${await resp.text()}`);
  }
  const body = await resp.json();
  const wrapper = (body?.data ?? body) as Record<string, unknown>;
  const u = (wrapper['user'] ?? wrapper) as Record<string, unknown>;
  return {
    id: toNum(u['id']),
    uid: String(u['uid']),
    username: String(u['username']),
    displayName: String(u['displayName']),
    defaultBranchId: u['defaultBranchId'] === null || u['defaultBranchId'] === undefined ? null : toNum(u['defaultBranchId']),
    status: String(u['status']),
    mustChangePassword: Boolean(u['mustChangePassword']),
  };
}

async function resetUserPassword(
  ctx: APIRequestContext,
  token: string,
  uid: string,
  newPassword: string,
): Promise<void> {
  const resp = await ctx.post(`/api/v1/users/uid/${uid}/reset-password`, {
    headers: authHeaders(token),
    data: { newPassword, mustChangePassword: false },
  });
  if (!resp.ok()) {
    throw new Error(`[personas] POST /users/uid/${uid}/reset-password failed: HTTP ${resp.status()} ${await resp.text()}`);
  }
}

async function grantRoleToUser(
  ctx: APIRequestContext,
  token: string,
  roleUid: string,
  username: string,
  branchId: number | null,
): Promise<void> {
  const resp = await ctx.post(`/api/v1/roles/uid/${roleUid}/grants`, {
    headers: authHeaders(token),
    data: { username, branchId },
  });
  if (!resp.ok()) {
    // Already-granted is fine on re-runs. The backend currently surfaces this
    // as 400 with "User already has role ..."; accept 409 too in case the
    // mapping is tightened later. Other 4xx/5xx still bubble up.
    if (resp.status() === 409) return;
    if (resp.status() === 400) {
      const body = await resp.text();
      if (/already has role/i.test(body)) return;
      throw new Error(`[personas] POST /roles/uid/${roleUid}/grants ${username} failed: HTTP 400 ${body}`);
    }
    throw new Error(`[personas] POST /roles/uid/${roleUid}/grants ${username} failed: HTTP ${resp.status()} ${await resp.text()}`);
  }
}

async function bootstrapOne(
  ctx: APIRequestContext,
  token: string,
  persona: Persona,
  permByCode: Map<string, PermissionRow>,
  roleByCode: Map<string, RoleSummary>,
): Promise<void> {
  const def = TEST_USERS[persona];
  const roleCode = permsRoleCode(persona);

  // Resolve requested perm codes → ids, silently dropping any that aren't
  // seeded yet. The supervisor's PROCUREMENT.CANCEL_LPO / GRN.CANCEL land in
  // Slice B; until then they're simply absent from the role.
  const matched: PermissionRow[] = [];
  const missing: string[] = [];
  for (const code of def.permissions) {
    const p = permByCode.get(code);
    if (p) matched.push(p);
    else missing.push(code);
  }
  if (missing.length > 0) {
    // eslint-disable-next-line no-console
    console.warn(`[personas] ${persona}: skipping unseeded perms: ${missing.join(', ')}`);
  }
  const permissionIds = matched.map(p => p.id).sort((a, b) => a - b);

  // ---- Role -----------------------------------------------------------------
  let role = roleByCode.get(roleCode) ?? null;
  if (!role) {
    role = await createRole(
      ctx, token, roleCode,
      `QA Test · ${def.fullName.replace(/^QA /, '')}`,
      `Auto-managed e2e persona role for ${persona}. Do not edit by hand.`,
    );
    roleByCode.set(roleCode, role);
  }

  // Always re-apply the permission set so re-runs converge on the declared
  // shape (PUT is a full replacement). Cheap and idempotent.
  await setRolePermissions(ctx, token, role.uid, permissionIds);

  // ---- User -----------------------------------------------------------------
  let userSummary = await fetchUserByUsername(ctx, token, def.username);
  if (!userSummary) {
    userSummary = await createUser(ctx, token, def);
  } else {
    // Re-apply the known password + clear must-change in case a prior run
    // left it in an inconsistent state (e.g. test rotated it).
    await resetUserPassword(ctx, token, userSummary.uid, def.password);
  }

  // ---- Grant ----------------------------------------------------------------
  const detail = await fetchUserDetail(ctx, token, userSummary.uid);
  const alreadyGranted = detail.grants.some(g =>
    g.roleCode === roleCode &&
    (g.branchId ?? null) === (def.defaultBranchId ?? null),
  );
  if (!alreadyGranted) {
    await grantRoleToUser(ctx, token, role.uid, def.username, def.defaultBranchId);
  }

  // eslint-disable-next-line no-console
  console.log(
    `[ok] ${persona} user=${def.username} branch=${def.defaultBranchId ?? 'company-wide'} perms=${permissionIds.length}` +
      (missing.length > 0 ? ` (skipped ${missing.length}: ${missing.join(',')})` : ''),
  );
}
