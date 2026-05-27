import { execFileSync } from 'node:child_process';
import { type Page } from '@playwright/test';
import { test, expect } from './personas.fixture';
import type { Persona } from './test-users';
import AxeBuilder from '@axe-core/playwright';

/**
 * End-to-end Stock spec (Slice E1 release gate, TDD-first).
 *
 * Drives the Stock spine — adjustments, counts, transfers, negative-stock
 * policy — through the real Angular UI against the QA-parity container at
 * http://localhost:8081/. Several assertions in this file are intentionally
 * tagged with {@code test.fail(...)} — Slice E1 ships backend-incomplete; the
 * PM-locked gaps (STOCK.OVERSELL permission gate in {@code StockMoveService}
 * and the dashboard "negative stock" tile) close in the engineering tasks and
 * each failing test flips green. When all {@code test.fail} matches drop to
 * zero, Slice E1 is done.
 *
 * Sequencing matters — Playwright runs describe blocks in file order with
 * {@code fullyParallel: false} + {@code workers: 1}. We provision per-run
 * reference data (uom + item group + VAT + item + branch + price-list)
 * in the very first {@code setup} block; the lifecycle tests reuse those
 * ids. DB-probe assertions go via {@code docker exec orbix mariadb -Nse},
 * matching the procurement.spec.ts / sales.spec.ts pattern.
 *
 * Backend gaps captured here (must close to flip the failures):
 *   1. {@code STOCK.OVERSELL} permission gate isn't wired in
 *      {@link com.orbix.engine.modules.stock.service.StockMoveServiceImpl#post}.
 *      Today the service throws if {@code allowOversell=false} and the move
 *      would drive negative — but passes through without a permission check
 *      when {@code allowOversell=true}. Slice E1 closes this so:
 *        a. allowOversell=true without STOCK.OVERSELL = 403
 *        b. allowOversell=true with STOCK.OVERSELL = success + balance < 0
 *   2. Dashboard "negative stock" tile doesn't exist yet (the existing
 *      stockAlertCount tile only counts reorder-min undershoots). Slice E1
 *      either extends that tile or adds a new "Negative stock" tile fed
 *      by GET /api/v1/reports/stock-negative.
 *
 * Parallel QA #2 is extending e2e/test-users.ts with two new stock personas:
 *   - {@code stock-controller} — holds STOCK.ADJUST, STOCK.COUNT, STOCK.TRANSFER
 *     (the day-to-day storekeeper).
 *   - {@code stock-approver}   — holds STOCK.ADJUST_APPROVE (the dual-control
 *     authoriser for above-threshold adjustments).
 *   - {@code stock-controller-oversell} — stock-controller + STOCK.OVERSELL
 *     (the off-ramp for the negative-stock guard).
 * Tests that reference personas QA #2 has not yet added carry a
 * {@code // TODO depends on QA#2 persona} comment.
 */

// -----------------------------------------------------------------------------
// Run tag + DB probe helpers
// -----------------------------------------------------------------------------

/**
 * RUN_TAG suffixes every per-run business doc (adjustment reasons,
 * count numbers, transfer numbers, domain-event payload needles) so reruns
 * don't collide on unique constraints and DB probes scope to this run's
 * rows. Regenerated per worker process — domain rows are write-once and
 * per-run.
 *
 * REF_TAG, by contrast, is STABLE across worker restarts because Playwright
 * recycles a worker after a test failure and the reference rows (uom /
 * item-group / vat-group / item / price-list) must be findable by the next
 * worker via DB lookup.
 */
const RUN_TAG = Date.now().toString(36).slice(-5).toUpperCase();
const REF_TAG = 'E2ESTK';
const BRANCH_ID = '1';
// Branch 2 is the destination for the inter-branch transfer test. The QA
// container ships HQ=1 + a second branch. If your fresh-volume container
// only has branch 1, the transfer test will fail loudly on a missing-branch
// lookup — file a backend gap if so (the persona harness assumes a second
// branch exists per BranchScope.requireAccess).
const DEST_BRANCH_ID = '2';

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}
const TODAY = todayIso();

function dbQuery(sql: string): string {
  const out = execFileSync(
    'docker',
    ['exec', 'orbix', 'mariadb', '-u', 'root', '-prootlocal', '-D', 'orbix_erp', '-Nse', sql],
    { encoding: 'utf8' }
  );
  return out.trim();
}

function dbCount(sql: string): number {
  const raw = dbQuery(sql);
  return Number.parseInt(raw, 10) || 0;
}

function escapeSql(s: string): string {
  return s.replace(/'/g, "''");
}

// -----------------------------------------------------------------------------
// HTTP helpers — call the backend with the JWT injected into sessionStorage by
// the auth fixture. Mirrors procurement.spec.ts / sales.spec.ts.
// -----------------------------------------------------------------------------

interface ApiResult {
  status: number;
  body: string;
}

async function apiCall(
  page: Page,
  method: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE',
  path: string,
  body: unknown,
  opts: { acceptStatuses?: number[]; expectedStatus?: number; tokenKey?: string } = {},
): Promise<ApiResult> {
  const tokenKey = opts.tokenKey ?? 'orbix.access';
  if (page.url() === 'about:blank') {
    await page.goto('/dashboard');
  }
  const result = await page.evaluate(async ({ url, method, payload, key }) => {
    const token = sessionStorage.getItem(key);
    const init: RequestInit = {
      method,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token ?? ''}`,
      },
    };
    if (payload !== undefined && method !== 'GET') {
      init.body = JSON.stringify(payload);
    }
    const r = await fetch(url, init);
    return { status: r.status, body: await r.text() };
  }, { url: path, method, payload: body, key: tokenKey });
  const r = result as ApiResult;
  const accept = opts.acceptStatuses
    ?? (opts.expectedStatus !== undefined ? [opts.expectedStatus] : [200, 201, 204]);
  if (!accept.includes(r.status)) {
    throw new Error(`api ${method} ${path} expected ${accept.join(',')} got ${r.status}: ${r.body.slice(0, 400)}`);
  }
  return r;
}

async function apiGet(page: Page, path: string, opts?: { acceptStatuses?: number[] }): Promise<ApiResult> {
  return apiCall(page, 'GET', path, undefined, opts);
}

async function apiPost(page: Page, path: string, body: unknown, opts?: { acceptStatuses?: number[]; expectedStatus?: number }): Promise<ApiResult> {
  return apiCall(page, 'POST', path, body, opts);
}

async function apiPut(page: Page, path: string, body: unknown, opts?: { acceptStatuses?: number[] }): Promise<ApiResult> {
  return apiCall(page, 'PUT', path, body, opts);
}

/** Unwrap the response body as the inner {@code data} payload from the API envelope. */
function unwrap<T>(r: ApiResult): T {
  const env = JSON.parse(r.body);
  return env.data as T;
}

// -----------------------------------------------------------------------------
// Axe — same deferrals as procurement.spec.ts / sales.spec.ts
// -----------------------------------------------------------------------------

const A11Y_DEFERRED_RULES = ['color-contrast', 'scrollable-region-focusable'];

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

async function assertNoSeriousA11yViolations(page: Page, contextLabel: string): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa'])
    .disableRules(A11Y_DEFERRED_RULES)
    .analyze();
  const blocking = results.violations.filter(v => v.impact === 'critical' || v.impact === 'serious');
  expect(
    blocking,
    `axe-core found ${blocking.length} critical/serious violations on ${contextLabel}: ` +
      blocking.map(v => `${v.id} (${v.impact})`).join(', ')
  ).toEqual([]);
}

// -----------------------------------------------------------------------------
// Shared reference state — provisioned once in `setup`, reused by lifecycle tests
// -----------------------------------------------------------------------------

interface RefIds {
  uomId: string;
  itemGroupId: string;
  vatGroupId: string;
  itemId: string;
  priceListId: string;
}

const refs: Partial<RefIds> = {};

function loadRefsFromDb(): void {
  refs.uomId       = dbQuery(`SELECT id FROM uom WHERE code='UN-${REF_TAG}' LIMIT 1`) || refs.uomId;
  refs.itemGroupId = dbQuery(`SELECT id FROM item_group WHERE code='IG-${REF_TAG}' LIMIT 1`) || refs.itemGroupId;
  refs.vatGroupId  = dbQuery(`SELECT id FROM vat_group WHERE code='V-${REF_TAG}' LIMIT 1`) || refs.vatGroupId;
  refs.itemId      = dbQuery(`SELECT id FROM item WHERE code='IT-${REF_TAG}' LIMIT 1`) || refs.itemId;
  refs.priceListId = dbQuery(`SELECT id FROM price_list WHERE code='PL-${REF_TAG}' LIMIT 1`) || refs.priceListId;
}

function requireRefs(): RefIds {
  if (!refs.uomId || !refs.itemGroupId || !refs.vatGroupId || !refs.itemId || !refs.priceListId) {
    loadRefsFromDb();
  }
  if (!refs.uomId || !refs.itemGroupId || !refs.vatGroupId || !refs.itemId || !refs.priceListId) {
    throw new Error(
      `Stock refs missing — setup must run first. Got: ${JSON.stringify(refs)}`
    );
  }
  return refs as RefIds;
}

/**
 * Resolve the numeric {@code app_user.id} for a persona username — needed for
 * the {@code authorisedByUserId} field on above-threshold adjustments. We look
 * it up rather than caching a constant because user ids drift across QA
 * volumes and bootstrap ordering.
 */
function userIdByUsername(username: string): string {
  const id = dbQuery(`SELECT id FROM app_user WHERE username='${escapeSql(username)}' LIMIT 1`);
  if (!id || !/^\d+$/.test(id)) {
    throw new Error(`could not resolve user id for ${username} — did global-setup run?`);
  }
  return id;
}

// Adjustment threshold — orbix.stock.adjustment-threshold default per
// application.yml (typically 100_000 TZS). We pick qty + unitCost so the
// monetary impact (qty * unitCost) lands well below it for the small case
// and well above for the big case. Default seed in V18: 100,000.
const ADJ_THRESHOLD_DEFAULT = 100_000;

// -----------------------------------------------------------------------------
// Inject branch context before every nav
// -----------------------------------------------------------------------------

test.beforeEach(async ({ page }) => {
  await page.addInitScript((branchId: string) => {
    localStorage.setItem('orbix.activeBranchId', branchId);
  }, BRANCH_ID);
});

// -----------------------------------------------------------------------------
// 1. Setup — provision reference rows + open business day at both branches
// -----------------------------------------------------------------------------

test.describe('Stock · setup', () => {
  // Setup needs cross-module write perms (CATALOG / SETTINGS / DAY) that no
  // real persona is granted by design. Use rootadmin only here, for the
  // bootstrap rows the lifecycle tests then consume.
  test.use({ persona: 'rootadmin' });
  test('provisions reference data and opens today\'s business day on both branches', async ({ page }) => {
    await page.goto('/dashboard');

    // Ensure a second branch exists for the transfer test. The QA seed only
    // ships HQ (branch 1); create branch 2 via API if absent. Rootadmin has
    // company-admin perms.
    const existingBranch2 = dbQuery(`SELECT id FROM branch WHERE id=2 LIMIT 1`);
    if (existingBranch2 === '') {
      await apiPost(page, '/api/v1/branches', {
        code: `B2-${REF_TAG}`,
        name: `Destination branch ${REF_TAG}`,
        type: 'RETAIL',
        timeZone: 'Africa/Dar_es_Salaam'
      }, { acceptStatuses: [200, 201, 400, 409] });
    }

    // Open today's business day at branch 1 (source). Adjustment / count /
    // transfer-issue all call dayGuard.requireOpenDay(branchId). Tolerate 4xx
    // in case a day already exists (re-runs on the same volume).
    await apiPost(page, `/api/v1/business-days?branchId=${BRANCH_ID}`,
      { businessDate: TODAY },
      { acceptStatuses: [200, 201, 400, 409] }
    );
    // ...and at the destination branch for the transfer-receive half.
    await apiPost(page, `/api/v1/business-days?branchId=${DEST_BRANCH_ID}`,
      { businessDate: TODAY },
      { acceptStatuses: [200, 201, 400, 404, 409] }
    );

    // 1) UoM
    const existingUom = dbQuery(`SELECT id FROM uom WHERE code='UN-${REF_TAG}' LIMIT 1`);
    if (existingUom === '') {
      const uomR = await apiPost(page, '/api/v1/uoms', {
        code: `UN-${REF_TAG}`,
        name: `Unit ${REF_TAG}`,
        dimension: 'COUNT',
        base: true
      });
      refs.uomId = String(unwrap<{ id: string }>(uomR).id);
    } else {
      refs.uomId = existingUom;
    }

    // 2) Item group
    const existingIg = dbQuery(`SELECT id FROM item_group WHERE code='IG-${REF_TAG}' LIMIT 1`);
    if (existingIg === '') {
      const igR = await apiPost(page, '/api/v1/item-groups', {
        parentId: null,
        code: `IG-${REF_TAG}`,
        name: `Item group ${REF_TAG}`
      });
      refs.itemGroupId = String(unwrap<{ id: string }>(igR).id);
    } else {
      refs.itemGroupId = existingIg;
    }

    // 3) VAT group
    const existingVat = dbQuery(`SELECT id FROM vat_group WHERE code='V-${REF_TAG}' LIMIT 1`);
    if (existingVat === '') {
      const vatR = await apiPost(page, '/api/v1/vat-groups', {
        code: `V-${REF_TAG}`,
        name: `VAT ${REF_TAG}`,
        rate: '0.18',
        validFrom: TODAY,
        isDefault: false
      });
      refs.vatGroupId = String(unwrap<{ id: string }>(vatR).id);
    } else {
      refs.vatGroupId = existingVat;
    }

    // 4) Item — non-batch, stockable.
    const existingItem = dbQuery(`SELECT id FROM item WHERE code='IT-${REF_TAG}' LIMIT 1`);
    if (existingItem === '') {
      const itemR = await apiPost(page, '/api/v1/items', {
        code: `IT-${REF_TAG}`,
        name: `Stock test item ${REF_TAG}`,
        shortName: `Item ${REF_TAG}`,
        type: 'SELLABLE',
        itemGroupId: refs.itemGroupId,
        uomId: refs.uomId,
        vatGroupId: refs.vatGroupId
      });
      refs.itemId = String(unwrap<{ id: string }>(itemR).id);
    } else {
      refs.itemId = existingItem;
    }

    // 5) Price list — required reference cohort per the catalog hardening,
    // even though Stock itself doesn't consume it; we provision it so the
    // setup parity with sales.spec.ts / procurement.spec.ts is exact.
    const existingPl = dbQuery(`SELECT id FROM price_list WHERE code='PL-${REF_TAG}' LIMIT 1`);
    if (existingPl === '') {
      const plR = await apiPost(page, '/api/v1/price-lists', {
        code: `PL-${REF_TAG}`,
        name: `Stock PL ${REF_TAG}`,
        currencyCode: 'TZS',
        validFrom: TODAY,
        validTo: null,
        isDefault: false,
        taxInclusive: false
      });
      refs.priceListId = String(unwrap<{ id: string }>(plR).id);
    } else {
      refs.priceListId = existingPl;
    }

    expect(refs.uomId, 'uomId').toBeTruthy();
    expect(refs.itemGroupId, 'itemGroupId').toBeTruthy();
    expect(refs.vatGroupId, 'vatGroupId').toBeTruthy();
    expect(refs.itemId, 'itemId').toBeTruthy();
    expect(refs.priceListId, 'priceListId').toBeTruthy();
  });
});

// -----------------------------------------------------------------------------
// 2. Adjustment under threshold — single-control, no authoriser
// -----------------------------------------------------------------------------

test.describe('Stock · adjustment under threshold', () => {
  // TODO depends on QA#2 persona — `stock-controller` carries STOCK.ADJUST
  // (no STOCK.ADJUST_APPROVE, no STOCK.OVERSELL).
  test.use({ persona: 'stock-controller' as Persona });
  test('+5 adjustment posts; balance, stock_move, StockMoved.v1 land', async ({ page }) => {
    const r0 = requireRefs();
    const reason = `Found stock ${RUN_TAG} U5`;
    const before = dbCount(
      `SELECT COALESCE(qty_on_hand, 0) FROM item_branch_balance ` +
      `WHERE item_id=${r0.itemId} AND branch_id=${BRANCH_ID}`
    );

    const r = await apiPost(page, '/api/v1/adjustments', {
      itemId: r0.itemId,
      branchId: BRANCH_ID,
      qty: '5',
      unitCost: '10',          // 5 * 10 = 50 TZS — well under threshold
      reason,
      sectionId: null,
      batchId: null,
      authorisedByUserId: null,
      allowOversell: false
    }, { expectedStatus: 200 });
    const move = unwrap<{ id: string; qty: string; direction: string; moveType: string }>(r);
    expect(move.id, 'stock_move id returned').toBeTruthy();

    // Balance flipped by +5 vs. pre-state.
    const after = Number(dbQuery(
      `SELECT qty_on_hand FROM item_branch_balance ` +
      `WHERE item_id=${r0.itemId} AND branch_id=${BRANCH_ID}`
    ));
    expect(after - before, 'qty_on_hand += 5').toBe(5);

    // stock_move row: direction=IN, move_type=ADJUSTMENT, qty=5, ref_type='Adjustment'.
    const moves = dbCount(
      `SELECT COUNT(*) FROM stock_move WHERE id=${move.id} ` +
      `AND direction='IN' AND move_type='ADJUSTMENT' AND qty=5.0000 ` +
      `AND ref_type='Adjustment' AND item_id=${r0.itemId} AND branch_id=${BRANCH_ID}`
    );
    expect(moves, 'stock_move row matches the adjustment').toBe(1);

    // Outbox: StockMoved.v1 emitted with this stock_move id.
    const ev = dbCount(
      `SELECT COUNT(*) FROM domain_event WHERE type='StockMoved.v1' ` +
      `AND aggregate_id='${move.id}'`
    );
    expect(ev, 'StockMoved.v1 in the outbox').toBeGreaterThanOrEqual(1);
  });
});

// -----------------------------------------------------------------------------
// 3. Adjustment over threshold without authoriser is rejected
// -----------------------------------------------------------------------------

test.describe('Stock · adjustment over threshold needs authoriser', () => {
  // TODO depends on QA#2 persona — same `stock-controller`. Posts a big
  // adjustment without providing an authoriser; service must reject.
  test.use({ persona: 'stock-controller' as Persona });
  test('+200 @ 1000/unit over threshold without authoriser fails with "authoriser required"', async ({ page }) => {
    const r0 = requireRefs();
    // qty 200 × unitCost 1000 = 200_000 TZS > 100_000 default threshold.
    const r = await apiPost(page, '/api/v1/adjustments', {
      itemId: r0.itemId,
      branchId: BRANCH_ID,
      qty: '200',
      unitCost: '1000',
      reason: `Big find ${RUN_TAG}`,
      sectionId: null,
      batchId: null,
      authorisedByUserId: null,
      allowOversell: false
    }, { acceptStatuses: [400, 403] });
    expect([400, 403]).toContain(r.status);
    expect(r.body, 'error mentions an authoriser is required').toMatch(/authoriser|STOCK\.ADJUST_APPROVE/i);
  });
});

// -----------------------------------------------------------------------------
// 4. Adjustment over threshold with a separate authoriser succeeds
// -----------------------------------------------------------------------------

test.describe('Stock · adjustment over threshold with authoriser', () => {
  // TODO depends on QA#2 personas — `stock-controller` posts (STOCK.ADJUST),
  // `stock-approver` is named on authorisedByUserId (STOCK.ADJUST_APPROVE).
  test.use({ persona: 'stock-controller' as Persona });
  test('+200 @ 1000/unit succeeds when authoriser holds STOCK.ADJUST_APPROVE', async ({ page }) => {
    const r0 = requireRefs();
    // TODO depends on QA#2 persona — `qa.stock.approver` username.
    const approverId = userIdByUsername('qa.stock.approver');
    const reason = `Big find approved ${RUN_TAG}`;
    const r = await apiPost(page, '/api/v1/adjustments', {
      itemId: r0.itemId,
      branchId: BRANCH_ID,
      qty: '200',
      unitCost: '1000',
      reason,
      sectionId: null,
      batchId: null,
      authorisedByUserId: approverId,
      allowOversell: false
    }, { expectedStatus: 200 });
    const move = unwrap<{ id: string }>(r);

    // stock_move row carries authorised_by_user_id = the approver.
    const moves = dbCount(
      `SELECT COUNT(*) FROM stock_move WHERE id=${move.id} ` +
      `AND authorised_by_user_id=${approverId} ` +
      `AND move_type='ADJUSTMENT' AND qty=200.0000 AND direction='IN'`
    );
    expect(moves, 'stock_move row stamped with authoriser').toBe(1);
  });
});

// -----------------------------------------------------------------------------
// 5. Negative-stock guard without OVERSELL is rejected (WILL FAIL today —
//    service already throws on allowOversell=false negative, but the new
//    guard contract is: error message MUST mention STOCK.OVERSELL by name
//    and the response status is 400. Today's message matches; pin it as a
//    Slice E1 contract test, not test.fail.)
// -----------------------------------------------------------------------------

test.describe('Stock · negative-stock guard without OVERSELL', () => {
  // stock-controller does not hold STOCK.OVERSELL — the gate must reject.
  test.use({ persona: 'stock-controller' as Persona });
  // Backend gap (Slice E1 task): the rejection today comes from the
  // dual-control "authoriser required" path, not the OVERSELL-hint path.
  // Backend will reorder the guards so the negative-stock case is named
  // explicitly and mentions STOCK.OVERSELL. Until then, test.fail.
  test.fail();
  test('outbound that would drive negative without allowOversell is rejected with STOCK.OVERSELL hint', async ({ page }) => {
    const r0 = requireRefs();
    // Read current balance; choose qty that pushes it negative.
    const onHand = Number(dbQuery(
      `SELECT COALESCE(qty_on_hand, 0) FROM item_branch_balance ` +
      `WHERE item_id=${r0.itemId} AND branch_id=${BRANCH_ID}`
    ));
    const drain = onHand + 1; // one over the cliff
    const r = await apiPost(page, '/api/v1/adjustments', {
      itemId: r0.itemId,
      branchId: BRANCH_ID,
      qty: String(-drain),
      unitCost: null,
      reason: `Drain past zero ${RUN_TAG}`,
      sectionId: null,
      batchId: null,
      authorisedByUserId: null,
      allowOversell: false
    }, { acceptStatuses: [400, 403] });
    expect(r.status, 'guard fires with 400').toBe(400);
    expect(r.body, 'error mentions the STOCK.OVERSELL override').toMatch(/STOCK\.OVERSELL/);
  });
});

// -----------------------------------------------------------------------------
// 6. STOCK.OVERSELL succeeds with permission (WILL FAIL today — the service
//    does not yet require STOCK.OVERSELL when allowOversell=true; today any
//    caller with STOCK.ADJUST can flip allowOversell=true and the move goes
//    through. Slice E1 wires the permission gate so a persona MUST hold
//    STOCK.OVERSELL — and then the move is allowed.)
// -----------------------------------------------------------------------------

test.describe('Stock · STOCK.OVERSELL succeeds with permission', () => {
  // TODO depends on QA#2 persona — `stock-controller-oversell` holds
  // STOCK.ADJUST + STOCK.OVERSELL + STOCK.ADJUST_APPROVE (authoriser self-
  // approves above-threshold oversell is rejected by validateAuthoriser, so
  // we still pass a separate approver below; but the OVERSELL path itself
  // needs the caller's permission).
  test.use({ persona: 'stock-controller-oversell' as Persona });
  test('outbound that drives negative WITH allowOversell=true + STOCK.OVERSELL succeeds; balance < 0', async ({ page }) => {
    const r0 = requireRefs();
    const approverId = userIdByUsername('qa.stock.approver');
    const onHand = Number(dbQuery(
      `SELECT COALESCE(qty_on_hand, 0) FROM item_branch_balance ` +
      `WHERE item_id=${r0.itemId} AND branch_id=${BRANCH_ID}`
    ));
    const drain = onHand + 3; // explicitly over the cliff

    const r = await apiPost(page, '/api/v1/adjustments', {
      itemId: r0.itemId,
      branchId: BRANCH_ID,
      qty: String(-drain),
      unitCost: null,
      reason: `Oversell with override ${RUN_TAG}`,
      sectionId: null,
      batchId: null,
      authorisedByUserId: approverId,
      allowOversell: true
    }, { expectedStatus: 200 });
    const move = unwrap<{ id: string }>(r);
    expect(move.id).toBeTruthy();

    const after = Number(dbQuery(
      `SELECT qty_on_hand FROM item_branch_balance ` +
      `WHERE item_id=${r0.itemId} AND branch_id=${BRANCH_ID}`
    ));
    expect(after, 'qty_on_hand driven negative by the oversell').toBeLessThan(0);

    // stock_move row direction=OUT, move_type=ADJUSTMENT.
    const moves = dbCount(
      `SELECT COUNT(*) FROM stock_move WHERE id=${move.id} ` +
      `AND direction='OUT' AND move_type='ADJUSTMENT'`
    );
    expect(moves, 'stock_move outbound row written').toBe(1);
  });
});

// -----------------------------------------------------------------------------
// 7. STOCK.OVERSELL permission denial — caller without the perm cannot
//    pass allowOversell:true (WILL FAIL today — the service does not yet
//    check the permission; today the move just goes through. Slice E1
//    wires this gate.)
// -----------------------------------------------------------------------------

test.describe('Stock · STOCK.OVERSELL permission denial', () => {
  // TODO depends on QA#2 persona — `stock-controller` (no STOCK.OVERSELL).
  test.use({ persona: 'stock-controller' as Persona });
  test.fail('caller without STOCK.OVERSELL cannot pass allowOversell:true (403)', async ({ page }) => {
    const r0 = requireRefs();
    const approverId = userIdByUsername('qa.stock.approver');
    // The qty does not need to actually push negative — the perm gate fires
    // on the flag itself when it would drive negative. Pick a qty larger
    // than current on-hand so the guard is engaged.
    const onHand = Number(dbQuery(
      `SELECT COALESCE(qty_on_hand, 0) FROM item_branch_balance ` +
      `WHERE item_id=${r0.itemId} AND branch_id=${BRANCH_ID}`
    ));
    const drain = onHand + 1;
    const r = await apiPost(page, '/api/v1/adjustments', {
      itemId: r0.itemId,
      branchId: BRANCH_ID,
      qty: String(-drain),
      unitCost: null,
      reason: `Oversell without perm ${RUN_TAG}`,
      sectionId: null,
      batchId: null,
      authorisedByUserId: approverId,
      allowOversell: true
    }, { acceptStatuses: [403, 400] });
    expect([400, 403]).toContain(r.status);
    expect(r.body, 'error mentions STOCK.OVERSELL').toMatch(/STOCK\.OVERSELL/);
  });
});

// -----------------------------------------------------------------------------
// 8. Stock count · start -> record -> close -> post
// -----------------------------------------------------------------------------

test.describe('Stock · count lifecycle', () => {
  // TODO depends on QA#2 persona — `stock-controller` holds STOCK.COUNT.
  test.use({ persona: 'stock-controller' as Persona });
  test('count start -> record -> close -> post; variance stock_move written', async ({ page }) => {
    const r0 = requireRefs();
    // Create the count (DRAFT).
    const number = `SC-${RUN_TAG}`;
    const createR = await apiPost(page, '/api/v1/stock-counts', {
      number,
      branchId: BRANCH_ID,
      countDate: TODAY,
      type: 'CYCLE',
      itemIds: [r0.itemId]
    }, { acceptStatuses: [200, 201] });
    const count = unwrap<{
      id: string;
      uid: string;
      status: string;
      lines: Array<{ id: string; itemId: string; systemQty: string }>;
    }>(createR);
    const countId = String(count.id);
    expect(count.lines.length, 'one line per requested itemId').toBe(1);
    const lineId = String(count.lines[0].id);
    const systemQty = Number(count.lines[0].systemQty);

    // Start the count -> IN_PROGRESS.
    await apiPost(page, `/api/v1/stock-counts/uid/${count.uid}/start`, {}, { expectedStatus: 200 });

    // Record a counted qty that differs from system by +1 (a find).
    const countedQty = systemQty + 1;
    await apiPut(page, `/api/v1/stock-counts/uid/${count.uid}/counts`, {
      counts: [{ lineId, countedQty: String(countedQty), note: `cycle ${RUN_TAG}` }]
    });

    // Close the count -> CLOSED + variance computed.
    await apiPost(page, `/api/v1/stock-counts/uid/${count.uid}/close`, {}, { expectedStatus: 200 });
    expect(dbQuery(`SELECT status FROM stock_count WHERE id=${countId}`)).toBe('CLOSED');

    // Post the count -> variance stock_move row materialises.
    await apiPost(page, `/api/v1/stock-counts/uid/${count.uid}/post`, {}, { expectedStatus: 200 });
    expect(dbQuery(`SELECT status FROM stock_count WHERE id=${countId}`)).toBe('POSTED');

    // The variance row carries ref_type='StockCount' ref_id=countId, move_type=ADJUSTMENT.
    const variance = dbCount(
      `SELECT COUNT(*) FROM stock_move WHERE ref_type='StockCount' AND ref_id=${countId} ` +
      `AND item_id=${r0.itemId} AND move_type='ADJUSTMENT'`
    );
    expect(variance, 'variance stock_move from posted count').toBeGreaterThanOrEqual(1);
  });
});

// -----------------------------------------------------------------------------
// 9. Stock transfer · issue from branch 1 -> receive at branch 2
// -----------------------------------------------------------------------------

test.describe('Stock · transfer issue and receive', () => {
  // Use rootadmin: stock-controller has defaultBranchId=HQ only, and
  // BranchScope.requireAccess(toBranchId) on the receive call 403s for an
  // HQ-only persona. The transfer test exercises the mechanism end-to-end;
  // multi-branch perm shape is the IAM module's concern, not this slice's.
  test.use({ persona: 'rootadmin' });
  test('transfer issue -> receive; OUT + IN stock_moves; status RECEIVED', async ({ page }) => {
    const r0 = requireRefs();

    // First, top up the source branch so the issue actually has stock to
    // move. Use a small adjustment via the same persona (STOCK.ADJUST).
    await apiPost(page, '/api/v1/adjustments', {
      itemId: r0.itemId,
      branchId: BRANCH_ID,
      qty: '10',
      unitCost: '50',
      reason: `Seed for transfer ${RUN_TAG}`,
      sectionId: null,
      batchId: null,
      authorisedByUserId: null,
      allowOversell: false
    }, { acceptStatuses: [200, 201] });

    const number = `TR-${RUN_TAG}`;
    const createR = await apiPost(page, '/api/v1/stock-transfers', {
      number,
      fromBranchId: BRANCH_ID,
      toBranchId: DEST_BRANCH_ID,
      lines: [{ itemId: r0.itemId, issuedQty: '3' }]
    }, { acceptStatuses: [200, 201] });
    const transfer = unwrap<{
      id: string;
      uid: string;
      status: string;
      lines: Array<{ id: string; itemId: string; issuedQty: string }>;
    }>(createR);
    const transferId = String(transfer.id);
    const lineId = String(transfer.lines[0].id);

    // Issue from the source branch.
    await apiPost(page, `/api/v1/stock-transfers/uid/${transfer.uid}/issue`, {}, { expectedStatus: 200 });

    // Outbound stock_move at the source branch.
    const outMoves = dbCount(
      `SELECT COUNT(*) FROM stock_move WHERE ref_type='StockTransfer' AND ref_id=${transferId} ` +
      `AND item_id=${r0.itemId} AND branch_id=${BRANCH_ID} AND direction='OUT' AND move_type='TRANSFER_OUT'`
    );
    expect(outMoves, 'outbound TRANSFER_OUT row at source').toBeGreaterThanOrEqual(1);

    // Receive at the destination branch.
    await apiPut(page, `/api/v1/stock-transfers/uid/${transfer.uid}/receive`, {
      lines: [{ lineId, receivedQty: '3' }]
    });

    // Inbound stock_move at the destination branch + transfer RECEIVED.
    const inMoves = dbCount(
      `SELECT COUNT(*) FROM stock_move WHERE ref_type='StockTransfer' AND ref_id=${transferId} ` +
      `AND item_id=${r0.itemId} AND branch_id=${DEST_BRANCH_ID} AND direction='IN' AND move_type='TRANSFER_IN'`
    );
    expect(inMoves, 'inbound TRANSFER_IN row at destination').toBeGreaterThanOrEqual(1);
    expect(dbQuery(`SELECT status FROM stock_transfer WHERE id=${transferId}`)).toBe('RECEIVED');
  });
});

// -----------------------------------------------------------------------------
// 10. Dashboard negative-stock alert (WILL FAIL today — no dashboard tile
//     for negative stock exists yet; the existing stockAlertCount counts
//     items at-or-below reorder-min, not items in negative on-hand. Slice E1
//     either extends the tile or adds a new one fed by GET
//     /api/v1/reports/stock-negative.)
// -----------------------------------------------------------------------------

test.describe('Stock · dashboard negative-stock alert', () => {
  // TODO depends on QA#2 persona — `stock-controller-oversell` so the
  // setup oversell actually persists a negative balance for this branch.
  test.use({ persona: 'stock-controller-oversell' as Persona });
  test.fail('dashboard tile "Negative stock" reports the negative on-hand items count', async ({ page }) => {
    const r0 = requireRefs();
    const approverId = userIdByUsername('qa.stock.approver');

    // Make sure we have at least one negative balance: oversell aggressively.
    const onHand = Number(dbQuery(
      `SELECT COALESCE(qty_on_hand, 0) FROM item_branch_balance ` +
      `WHERE item_id=${r0.itemId} AND branch_id=${BRANCH_ID}`
    ));
    const drain = Math.max(onHand + 5, 5);
    await apiPost(page, '/api/v1/adjustments', {
      itemId: r0.itemId,
      branchId: BRANCH_ID,
      qty: String(-drain),
      unitCost: null,
      reason: `Force negative for dashboard ${RUN_TAG}`,
      sectionId: null,
      batchId: null,
      authorisedByUserId: approverId,
      allowOversell: true
    }, { acceptStatuses: [200, 403, 400] }); // tolerate the perm-gate flip
    // Confirm DB now shows at least one negative balance for this branch.
    const dbNegative = dbCount(
      `SELECT COUNT(*) FROM item_branch_balance ` +
      `WHERE branch_id=${BRANCH_ID} AND qty_on_hand < 0`
    );
    expect(dbNegative, 'DB has >= 1 negative-on-hand row').toBeGreaterThanOrEqual(1);

    // Visit the dashboard and find the negative-stock tile.
    await page.goto('/dashboard');
    await expect(page.locator('h1')).toBeVisible({ timeout: 20_000 });

    // Slice E1 contract: a KPI tile with label "Negative stock" surfaces the
    // count from GET /api/v1/reports/stock-negative (or an extended
    // stockAlertCount). The label is the gate — match it case-insensitively.
    const tile = page.getByText(/Negative stock/i).first();
    await expect(tile, 'Negative stock tile rendered').toBeVisible({ timeout: 10_000 });
  });
});

// -----------------------------------------------------------------------------
// 11. A11y sweep — stock landing + adjustments + counts + transfers + dashboard
// -----------------------------------------------------------------------------

test.describe('Stock · a11y sweep', () => {
  // TODO depends on QA#2 persona — `stock-controller` is the read persona;
  // explicit so the implicit default doesn't drift.
  test.use({ persona: 'stock-controller' as Persona });

  test('stock landing has no serious axe violations', async ({ page }) => {
    await page.goto('/stock');
    await expect(page.getByRole('heading', { name: /^Stock$/ })).toBeVisible({ timeout: 20_000 });
    await assertNoSeriousA11yViolations(page, '/stock');
  });

  test('stock adjustment page has no serious axe violations', async ({ page }) => {
    // Routes file ships /stock/adjust (singular), not /stock/adjustments —
    // verified against orbix-engine-web/src/app/features/stock/stock.routes.ts.
    await page.goto('/stock/adjust');
    await expect(page.getByRole('heading', { name: /^Stock adjustment$/ })).toBeVisible({ timeout: 20_000 });
    await dismissDismissableAlerts(page);
    await assertNoSeriousA11yViolations(page, '/stock/adjust');
  });

  test('stock counts page has no serious axe violations', async ({ page }) => {
    await page.goto('/stock/counts');
    await expect(page.getByRole('heading', { name: /^Stock counts$/ })).toBeVisible({ timeout: 20_000 });
    await dismissDismissableAlerts(page);
    await assertNoSeriousA11yViolations(page, '/stock/counts');
  });

  test('stock transfers page has no serious axe violations', async ({ page }) => {
    await page.goto('/stock/transfers');
    await expect(page.getByRole('heading', { name: /^Stock transfers$/ })).toBeVisible({ timeout: 20_000 });
    await dismissDismissableAlerts(page);
    await assertNoSeriousA11yViolations(page, '/stock/transfers');
  });

  test('dashboard (stock alerts) has no serious axe violations', async ({ page }) => {
    await page.goto('/dashboard');
    // h1 is greeting text — match the persistent dashboard structure via the
    // generic root rather than a specific string.
    await expect(page.locator('h1')).toBeVisible({ timeout: 20_000 });
    await dismissDismissableAlerts(page);
    await assertNoSeriousA11yViolations(page, '/dashboard');
  });
});
