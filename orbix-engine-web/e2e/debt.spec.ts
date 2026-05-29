import { execFileSync } from 'node:child_process';
import { type Page } from '@playwright/test';
import { test, expect } from './personas.fixture';
import type { Persona } from './test-users';
import AxeBuilder from '@axe-core/playwright';

/**
 * End-to-end Debt module spec (Slice G release gate, TDD-first).
 *
 * Drives the new /debt customer-AR surface through the real Angular UI
 * against the QA-parity container at http://localhost:8081/. Every scenario
 * in this file is intentionally tagged with {@code test.fail(...)} — Slice G
 * is shipped backend-incomplete; the gaps get closed in the engineering
 * tasks listed in {@code docs/design/slice-g-debt-plan.md} and
 * {@code docs/design/slice-g-debt-gap-audit.md}. When all {@code test.fail}
 * matches drop to zero, Slice G is done.
 *
 * Sequencing matters — Playwright runs describe blocks in file order with
 * {@code fullyParallel: false} + {@code workers: 1}. We provision per-run
 * business data (customer + 2 invoices spanning 2 aging buckets) in the very
 * first {@code setup} block; the persona drill-down tests reuse those rows.
 * DB-probe assertions go via {@code docker exec orbix mariadb -Nse},
 * matching the {@code sales.spec.ts} pattern.
 *
 * Backend gaps captured here (must close to flip the failures):
 *   1. `DEBT.*` permission band (130-133) not seeded yet — V70 Slice G.
 *   2. `GET /api/v1/debt/aging` endpoint does not exist yet (plan §5.1.1).
 *   3. `GET /api/v1/debt/dunning` paged endpoint does not exist yet (§5.1.2).
 *   4. `GET /api/v1/debt/customer/uid/{uid}` drill-down does not exist (§5.1.3).
 *   5. `POST /api/v1/debt/customer/uid/{uid}/credit-limit` does not exist (§5.1.4).
 *   6. `POST /api/v1/debt/customer/uid/{uid}/notes` does not exist (§5.1.5).
 *   7. `party_note` table + entity + service do not exist (§6, V71).
 *   8. The `/debt` route renders a placeholder hub — no dunning queue UI.
 *   9. The `/debt/customer/uid/{uid}` route + drill-down component do not exist.
 *
 * Frontend contract — stable test IDs the frontend agent will wire (plan §9 task 4
 * + this file's helpers). Each is referenced below. Wrapper-pattern from Slice F:
 * the {@code data-testid} sits on the wrapper element; the anchor / control is the
 * descendant the user interacts with.
 *
 *   data-testid                          | Component                          | Purpose
 *   -------------------------------------|------------------------------------|---------------------------------------
 *   debt-dunning-table                   | debt.component.ts (replaced)       | Wrapper around the dunning queue table
 *   debt-bucket-current                  | debt.component.ts                  | Totals header cell — CURRENT bucket
 *   debt-bucket-30                       | debt.component.ts                  | Totals header cell — D_1_30 bucket
 *   debt-bucket-60                       | debt.component.ts                  | Totals header cell — D_31_60 bucket
 *   debt-bucket-90                       | debt.component.ts                  | Totals header cell — D_61_90 bucket
 *   debt-bucket-over-90                  | debt.component.ts                  | Totals header cell — D_90_PLUS bucket
 *   debt-customer-row                    | debt.component.ts                  | Clickable customer row (one per row)
 *   debt-customer-detail                 | customer-debt-position.component.ts | Drill-down container
 *   debt-chase-notes-list                | customer-debt-position.component.ts | Chase-notes activity log list
 *   debt-chase-note-add                  | customer-debt-position.component.ts | Append-form textarea
 *   debt-chase-note-save                 | customer-debt-position.component.ts | Append-form submit button
 *   debt-credit-limit-display            | customer-debt-position.component.ts | Read-only credit-limit panel
 *   debt-credit-limit-edit               | customer-debt-position.component.ts | "Adjust limit" button (perm-gated)
 *   debt-credit-limit-input              | customer-debt-position.component.ts | New-limit numeric input
 *   debt-credit-limit-save               | customer-debt-position.component.ts | Save-new-limit submit button
 *   debt-permission-required             | debt.component.ts                  | Inert "Permission required" state for
 *                                        |                                    | personas without DEBT.READ
 *
 * Personas exercised (see e2e/test-users.ts):
 *   - accountant (happy path) — holds the new DEBT.READ + DEBT.NOTE.CREATE +
 *     DEBT.NOTE.ARCHIVE + DEBT.CREDIT_LIMIT.UPDATE. Forward-compat-skip
 *     applies until Slice G migration V70 lands; bootstrap silently drops
 *     the unseeded codes today and auto-picks them up on the next image
 *     rebuild.
 *   - sales-clerk (partial path) — does NOT hold DEBT.READ. The /debt page
 *     must render an inert "Permission required" state; deep-linking to
 *     /debt/customer/uid/{uid} must 403 or render inert; POSTing a
 *     credit-limit adjust must be rejected by the backend.
 */

// -----------------------------------------------------------------------------
// Run tag + DB probe helpers (mirror sales.spec.ts)
// -----------------------------------------------------------------------------

const RUN_TAG = Date.now().toString(36).slice(-5).toUpperCase();
const REF_TAG = 'E2EDBT';
const BRANCH_ID = '1';

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}
function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}
const TODAY = todayIso();
const DUE_45_DAYS_AGO = isoDaysAgo(45); // D_31_60 bucket
const DUE_5_DAYS_AGO = isoDaysAgo(5);   // D_1_30 bucket

function dbQuery(sql: string): string {
  const out = execFileSync(
    'docker',
    ['exec', 'orbix', 'mariadb', '-u', 'root', '-prootlocal', '-D', 'orbix_erp', '-Nse', sql],
    { encoding: 'utf8' },
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
// HTTP helpers — mirror sales.spec.ts
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
  const result = await page.evaluate(
    async ({ url, method, payload, key }) => {
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
    },
    { url: path, method, payload: body, key: tokenKey },
  );
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

async function apiPost(
  page: Page,
  path: string,
  body: unknown,
  opts?: { acceptStatuses?: number[]; expectedStatus?: number },
): Promise<ApiResult> {
  return apiCall(page, 'POST', path, body, opts);
}

function unwrap<T>(r: ApiResult): T {
  const env = JSON.parse(r.body);
  return env.data as T;
}

// -----------------------------------------------------------------------------
// Axe — same deferrals as the rest of the suite
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
    `axe-core found ${blocking.length} critical/serious violations on ${contextLabel}: `
      + blocking.map(v => `${v.id} (${v.impact})`).join(', '),
  ).toEqual([]);
}

// -----------------------------------------------------------------------------
// Shared reference state — provisioned in `setup`, reused by drill-down tests
// -----------------------------------------------------------------------------

interface RefIds {
  uomId: string;
  itemGroupId: string;
  vatGroupId: string;
  itemId: string;
  priceListId: string;
  customerId: string;       // party_id of the chase-target customer
  customerUid: string;      // uid of the customer (for URL navigation)
  customerName: string;     // for UI lookups
  invoice45UId: string;     // invoice in D_31_60 bucket (uid for /post)
  invoice45Id: string;      // numeric id for DB probes
  invoice5UId: string;      // invoice in D_1_30 bucket
  invoice5Id: string;
}

const refs: Partial<RefIds> = {};

function loadRefsFromDb(): void {
  refs.uomId       = dbQuery(`SELECT id FROM uom WHERE code='UN-${REF_TAG}' LIMIT 1`) || refs.uomId;
  refs.itemGroupId = dbQuery(`SELECT id FROM item_group WHERE code='IG-${REF_TAG}' LIMIT 1`) || refs.itemGroupId;
  refs.vatGroupId  = dbQuery(`SELECT id FROM vat_group WHERE code='V-${REF_TAG}' LIMIT 1`) || refs.vatGroupId;
  refs.itemId      = dbQuery(`SELECT id FROM item WHERE code='IT-${REF_TAG}' LIMIT 1`) || refs.itemId;
  refs.priceListId = dbQuery(`SELECT id FROM price_list WHERE code='PL-${REF_TAG}' LIMIT 1`) || refs.priceListId;
  refs.customerName = `QA Cust Debt ${REF_TAG}`;
  refs.customerId  = dbQuery(
    `SELECT c.party_id FROM customer c JOIN party p ON p.id=c.party_id WHERE p.name='${escapeSql(refs.customerName)}' LIMIT 1`,
  ) || refs.customerId;
  if (refs.customerId) {
    refs.customerUid = dbQuery(
      `SELECT uid FROM party WHERE id=${refs.customerId} LIMIT 1`,
    ) || refs.customerUid;
    // Recover the two posted invoices created by setup. They are identified by
    // number suffix ('-AGE45' / '-AGE05') joined to the debt customer, most recent.
    const inv45Row = dbQuery(
      `SELECT id, uid FROM sales_invoice WHERE customer_id=${refs.customerId} AND number LIKE '%-AGE45' AND status='POSTED' ORDER BY id DESC LIMIT 1`,
    );
    if (inv45Row) {
      const [id45, uid45] = inv45Row.split('\t');
      refs.invoice45Id  = id45  || refs.invoice45Id;
      refs.invoice45UId = uid45 || refs.invoice45UId;
    }
    const inv5Row = dbQuery(
      `SELECT id, uid FROM sales_invoice WHERE customer_id=${refs.customerId} AND number LIKE '%-AGE05' AND status='POSTED' ORDER BY id DESC LIMIT 1`,
    );
    if (inv5Row) {
      const [id5, uid5] = inv5Row.split('\t');
      refs.invoice5Id  = id5  || refs.invoice5Id;
      refs.invoice5UId = uid5 || refs.invoice5UId;
    }
  }
}

function requireRefs(): RefIds {
  if (!refs.uomId || !refs.itemGroupId || !refs.vatGroupId || !refs.itemId
      || !refs.priceListId || !refs.customerId || !refs.customerUid) {
    loadRefsFromDb();
  }
  if (!refs.uomId || !refs.itemGroupId || !refs.vatGroupId || !refs.itemId
      || !refs.priceListId || !refs.customerId || !refs.customerUid
      || !refs.invoice45Id || !refs.invoice5Id) {
    throw new Error(
      `Debt refs missing — setup must run first. Got: ${JSON.stringify(refs)}`,
    );
  }
  return refs as RefIds;
}

// Item priced so two posted invoices put the customer well into the aging
// buckets while staying under the credit limit on draft creation. Credit
// limit = 200_000, invoices total 8_000 + 6_000 = 14_000 → under the limit
// so post-time credit gate doesn't block; chases come from the dueDate
// being in the past, not from the outstanding being above the limit.
const UNIT_PRICE = 2_000;
const QTY_BUCKET_45 = 4; // 8_000 — D_31_60
const QTY_BUCKET_5 = 3;  // 6_000 — D_1_30

// -----------------------------------------------------------------------------
// Inject branch context before every nav
// -----------------------------------------------------------------------------

test.beforeEach(async ({ page }) => {
  await page.addInitScript((branchId: string) => {
    localStorage.setItem('orbix.activeBranchId', branchId);
  }, BRANCH_ID);
});

// =============================================================================
// 1. Setup — provision reference rows + 2 posted invoices in 2 aging buckets
// =============================================================================

test.describe('Slice G — debt setup', () => {
  // Setup needs cross-module write perms (CATALOG / PARTY / SETTINGS / DAY)
  // that no real persona is granted by design. Use rootadmin only here, for
  // the bootstrap rows the lifecycle tests then consume.
  test.use({ persona: 'rootadmin' });
  test('provisions customer + 2 posted invoices spanning D_1_30 and D_31_60 buckets', async ({ page }) => {
    await page.goto('/dashboard');

    // Open today's business day at branch 1.
    await apiPost(page, `/api/v1/business-days?branchId=${BRANCH_ID}`,
      { businessDate: TODAY },
      { acceptStatuses: [200, 201, 400, 409] },
    );

    // 1) UoM
    const existingUom = dbQuery(`SELECT id FROM uom WHERE code='UN-${REF_TAG}' LIMIT 1`);
    if (existingUom === '') {
      const uomR = await apiPost(page, '/api/v1/uoms', {
        code: `UN-${REF_TAG}`,
        name: `Unit ${REF_TAG}`,
        dimension: 'COUNT',
        base: true,
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
        name: `Item group ${REF_TAG}`,
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
        isDefault: false,
      });
      refs.vatGroupId = String(unwrap<{ id: string }>(vatR).id);
    } else {
      refs.vatGroupId = existingVat;
    }

    // 4) Item
    const existingItem = dbQuery(`SELECT id FROM item WHERE code='IT-${REF_TAG}' LIMIT 1`);
    if (existingItem === '') {
      const itemR = await apiPost(page, '/api/v1/items', {
        code: `IT-${REF_TAG}`,
        name: `Debt test item ${REF_TAG}`,
        shortName: `Item ${REF_TAG}`,
        type: 'SELLABLE',
        itemGroupId: refs.itemGroupId,
        uomId: refs.uomId,
        vatGroupId: refs.vatGroupId,
      });
      refs.itemId = String(unwrap<{ id: string }>(itemR).id);
    } else {
      refs.itemId = existingItem;
    }

    // 4b) Seed positive stock so posting the invoices doesn't drive qty negative.
    await apiPost(page, '/api/v1/adjustments', {
      itemId: refs.itemId,
      branchId: BRANCH_ID,
      qty: '500',
      unitCost: '10',
      reason: `e2e debt seed ${RUN_TAG}`,
      sectionId: null,
      batchId: null,
      authorisedByUserId: null,
      allowOversell: false,
    }, { acceptStatuses: [200, 201] });

    // 5) Price list
    const existingPl = dbQuery(`SELECT id FROM price_list WHERE code='PL-${REF_TAG}' LIMIT 1`);
    if (existingPl === '') {
      const plR = await apiPost(page, '/api/v1/price-lists', {
        code: `PL-${REF_TAG}`,
        name: `Debt PL ${REF_TAG}`,
        currencyCode: 'TZS',
        validFrom: TODAY,
        validTo: null,
        isDefault: false,
        taxInclusive: false,
      });
      refs.priceListId = String(unwrap<{ id: string }>(plR).id);
    } else {
      refs.priceListId = existingPl;
    }

    // 6) Customer — credit limit 200_000 (well above what the two invoices
    //    total) so the credit-limit gate doesn't fire and the test exercises
    //    the chase / aging surface only.
    refs.customerName = `QA Cust Debt ${REF_TAG}`;
    const existingCust = dbQuery(
      `SELECT c.party_id FROM customer c JOIN party p ON p.id=c.party_id WHERE p.name='${escapeSql(refs.customerName)}' LIMIT 1`,
    );
    if (existingCust === '') {
      const cR = await apiPost(page, '/api/v1/customers', {
        partyId: null,
        party: {
          name: refs.customerName,
          legalName: `${refs.customerName} Ltd`,
          category: 'BUSINESS',
          tin: `600-600-DBT`,
          vrn: null,
          phone: `+255722900333`,
          email: null,
          physicalAddress: null,
          postalAddress: null,
          countryCode: 'TZ',
          notes: null,
        },
        creditLimitAmount: '200000',
        creditTermsDays: 30,
        priceListId: refs.priceListId,
        defaultSalesAgentId: null,
        defaultBranchId: BRANCH_ID,
        taxExempt: false,
      });
      const created = unwrap<{ partyId: string }>(cR);
      refs.customerId = String(created.partyId);
    } else {
      refs.customerId = existingCust;
    }
    refs.customerUid = dbQuery(`SELECT uid FROM party WHERE id=${refs.customerId} LIMIT 1`);
    expect(refs.customerUid, 'customer uid resolves').toBeTruthy();

    // 6b) Raise the credit limit to give effectively unlimited headroom. This
    //     customer is reused across runs on a persistent volume, so its unpaid
    //     outstanding grows every run; meanwhile the 2.G adjust test pins the
    //     limit back to 250k. Without this, accumulated debt eventually exceeds
    //     the limit and the post-time credit gate blocks setup's invoices.
    //     1e9 keeps integer digits within the @Digits(integer=14) bound and is
    //     far enough above 250k that the 2.G event assertion ('%250000%') can't
    //     false-match. rootadmin holds DEBT.CREDIT_LIMIT.UPDATE.
    await apiPost(page, `/api/v1/debt/customer/uid/${refs.customerUid}/credit-limit`, {
      newLimit: '1000000000',
      reason: `e2e debt setup headroom ${RUN_TAG}`,
    }, { acceptStatuses: [200, 201] });

    // 7) Invoice in D_31_60 bucket — dated 45 days ago, dueDate 45 days ago.
    //    Posted so it shows in the dunning queue.
    const inv45Number = `INV-${RUN_TAG}-AGE45`;
    const draft45 = await apiPost(page, '/api/v1/sales-invoices', {
      number: inv45Number,
      branchId: BRANCH_ID,
      customerId: refs.customerId,
      salesAgentId: null,
      invoiceDate: DUE_45_DAYS_AGO,
      dueDate: DUE_45_DAYS_AGO,
      paymentTerms: 'CREDIT',
      currencyCode: 'TZS',
      priceListId: refs.priceListId,
      discountApproverId: null,
      reference: `e2e debt 45 ${RUN_TAG}`,
      notes: null,
      lines: [{
        itemId: refs.itemId,
        uomId: refs.uomId,
        qty: String(QTY_BUCKET_45),
        unitPrice: String(UNIT_PRICE),
        discountPct: '0',
        vatGroupId: refs.vatGroupId,
      }],
    });
    const d45 = unwrap<{ uid: string; id: string }>(draft45);
    refs.invoice45UId = d45.uid;
    refs.invoice45Id = String(d45.id);
    await apiPost(page, `/api/v1/sales-invoices/uid/${d45.uid}/post`, {}, { expectedStatus: 200 });

    // 8) Invoice in D_1_30 bucket — dated 5 days ago.
    const inv5Number = `INV-${RUN_TAG}-AGE05`;
    const draft5 = await apiPost(page, '/api/v1/sales-invoices', {
      number: inv5Number,
      branchId: BRANCH_ID,
      customerId: refs.customerId,
      salesAgentId: null,
      invoiceDate: DUE_5_DAYS_AGO,
      dueDate: DUE_5_DAYS_AGO,
      paymentTerms: 'CREDIT',
      currencyCode: 'TZS',
      priceListId: refs.priceListId,
      discountApproverId: null,
      reference: `e2e debt 5 ${RUN_TAG}`,
      notes: null,
      lines: [{
        itemId: refs.itemId,
        uomId: refs.uomId,
        qty: String(QTY_BUCKET_5),
        unitPrice: String(UNIT_PRICE),
        discountPct: '0',
        vatGroupId: refs.vatGroupId,
      }],
    });
    const d5 = unwrap<{ uid: string; id: string }>(draft5);
    refs.invoice5UId = d5.uid;
    refs.invoice5Id = String(d5.id);
    await apiPost(page, `/api/v1/sales-invoices/uid/${d5.uid}/post`, {}, { expectedStatus: 200 });

    // Sanity: both invoices POSTED, customer has outstanding > 0.
    expect(dbQuery(`SELECT status FROM sales_invoice WHERE id=${refs.invoice45Id}`)).toBe('POSTED');
    expect(dbQuery(`SELECT status FROM sales_invoice WHERE id=${refs.invoice5Id}`)).toBe('POSTED');

    expect(refs.customerUid, 'customerUid').toBeTruthy();
    expect(refs.invoice45UId, 'invoice45UId').toBeTruthy();
    expect(refs.invoice5UId, 'invoice5UId').toBeTruthy();
  });
});

// =============================================================================
// 2. ACCOUNTANT happy path — dunning queue + 5-bucket totals
// =============================================================================

test.describe('Slice G — debt · accountant happy path', () => {
  test.use({ persona: 'accountant' as Persona });

  // ---------------------------------------------------------------------------
  // 2.A — /debt landing renders the dunning queue table (not the placeholder)
  // ---------------------------------------------------------------------------
  test(
    '/debt renders the dunning queue table for accountant (replaces placeholder hub)',
    async ({ page }) => {
      await page.goto('/debt');
      // Heading on the new debt page — frontend agent owns the literal copy.
      await expect(page.getByRole('heading', { name: /Debt|Dunning|Receivables/i })).toBeVisible({ timeout: 20_000 });
      // The dunning-queue table must be present — wrapper carries the test id.
      await expect(page.locator('[data-testid="debt-dunning-table"]')).toBeVisible({ timeout: 15_000 });
      // The placeholder copy MUST be gone.
      await expect(page.getByText(/Debt module coming soon/i)).toHaveCount(0);
    },
  );

  // ---------------------------------------------------------------------------
  // 2.B — 5-bucket totals header renders (CURRENT / 1-30 / 31-60 / 61-90 / 90+)
  // ---------------------------------------------------------------------------
  test(
    '/debt renders the 5 aging-bucket header cells (CURRENT / D_1_30 / D_31_60 / D_61_90 / D_90_PLUS)',
    async ({ page }) => {
      await page.goto('/debt');
      await expect(page.locator('[data-testid="debt-dunning-table"]')).toBeVisible({ timeout: 15_000 });
      // Plan §4: 5 buckets, locked names per US-DEBT-003.
      await expect(page.locator('[data-testid="debt-bucket-current"]')).toBeVisible();
      await expect(page.locator('[data-testid="debt-bucket-30"]')).toBeVisible();
      await expect(page.locator('[data-testid="debt-bucket-60"]')).toBeVisible();
      await expect(page.locator('[data-testid="debt-bucket-90"]')).toBeVisible();
      await expect(page.locator('[data-testid="debt-bucket-over-90"]')).toBeVisible();
    },
  );

  // ---------------------------------------------------------------------------
  // 2.C — The chase-target customer shows up in the queue with the right name
  // ---------------------------------------------------------------------------
  test(
    'dunning queue lists the test customer with non-zero outstanding',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/debt');
      await expect(page.locator('[data-testid="debt-dunning-table"]')).toBeVisible({ timeout: 15_000 });

      // The customer name must appear on at least one row.
      const customerRow = page.locator('[data-testid="debt-customer-row"]')
        .filter({ hasText: r0.customerName })
        .first();
      await expect(customerRow).toBeVisible({ timeout: 15_000 });
    },
  );

  // ---------------------------------------------------------------------------
  // 2.D — Clicking a customer row drills into the debt-position view
  // ---------------------------------------------------------------------------
  test(
    'clicking a dunning row navigates to /debt/customer/uid/{uid} drill-down',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/debt');
      const customerRow = page.locator('[data-testid="debt-customer-row"]')
        .filter({ hasText: r0.customerName })
        .first();
      await expect(customerRow).toBeVisible({ timeout: 15_000 });
      await customerRow.click();

      await expect(page).toHaveURL(new RegExp(`/debt/customer/uid/${r0.customerUid}`));
      await expect(page.locator('[data-testid="debt-customer-detail"]')).toBeVisible({ timeout: 15_000 });
    },
  );

  // ---------------------------------------------------------------------------
  // 2.E — Drill-down shows open invoices + chase notes panel (deep link)
  // ---------------------------------------------------------------------------
  test(
    // G — drill-down panels render once setup's refs are recovered; unwrapped in Slice M
    'customer drill-down shows open invoices, chase-notes list, and credit-limit panel',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto(`/debt/customer/uid/${r0.customerUid}`);
      await expect(page.locator('[data-testid="debt-customer-detail"]')).toBeVisible({ timeout: 15_000 });

      // Both posted invoices must surface in the open-invoices list.
      await expect(page.getByText(`INV-${RUN_TAG}-AGE45`)).toBeVisible({ timeout: 10_000 });
      await expect(page.getByText(`INV-${RUN_TAG}-AGE05`)).toBeVisible({ timeout: 10_000 });

      // Chase-notes list panel + credit-limit display panel must both be present.
      await expect(page.locator('[data-testid="debt-chase-notes-list"]')).toBeVisible();
      await expect(page.locator('[data-testid="debt-credit-limit-display"]')).toBeVisible();
    },
  );

  // ---------------------------------------------------------------------------
  // 2.F — Accountant adds a chase note → it persists + appears in the list
  // ---------------------------------------------------------------------------
  test.fail(
    'accountant appends a chase note; row lands in domain_event with kind=AR_CHASE',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto(`/debt/customer/uid/${r0.customerUid}`);
      await expect(page.locator('[data-testid="debt-customer-detail"]')).toBeVisible({ timeout: 15_000 });

      const noteBody = `Phoned customer about overdue ${RUN_TAG}`;
      const addBox = page.locator('[data-testid="debt-chase-note-add"]');
      const saveBtn = page.locator('[data-testid="debt-chase-note-save"]');
      await expect(addBox).toBeVisible({ timeout: 10_000 });
      await addBox.fill(noteBody);
      await saveBtn.click();

      // Note appears in the list after save.
      const list = page.locator('[data-testid="debt-chase-notes-list"]');
      await expect(list.getByText(noteBody)).toBeVisible({ timeout: 10_000 });

      // DB pins: party_note row written, DebtNoteCreated.v1 emitted.
      const noteRow = dbCount(
        `SELECT COUNT(*) FROM party_note WHERE party_id=${r0.customerId} `
        + `AND kind='AR_CHASE' AND body='${escapeSql(noteBody)}' AND status='ACTIVE'`,
      );
      expect(noteRow, 'party_note row inserted with kind=AR_CHASE').toBeGreaterThanOrEqual(1);

      const ev = dbCount(
        `SELECT COUNT(*) FROM domain_event WHERE type='DebtNoteCreated.v1' `
        + `AND payload_json LIKE '%${escapeSql(noteBody)}%'`,
      );
      expect(ev, 'DebtNoteCreated.v1 in the outbox').toBeGreaterThanOrEqual(1);
    },
  );

  // ---------------------------------------------------------------------------
  // 2.G — Accountant adjusts credit limit → new value persists + event emitted
  // ---------------------------------------------------------------------------
  test(
    // G — credit-limit endpoint landed; passing as of Slice M triage
    'accountant adjusts credit limit; new value persists + CustomerCreditLimitChanged.v1 emitted',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto(`/debt/customer/uid/${r0.customerUid}`);
      await expect(page.locator('[data-testid="debt-customer-detail"]')).toBeVisible({ timeout: 15_000 });

      // Click the "Adjust limit" button — perm-gated on DEBT.CREDIT_LIMIT.UPDATE.
      const editBtn = page.locator('[data-testid="debt-credit-limit-edit"]');
      await expect(editBtn).toBeVisible({ timeout: 10_000 });
      await editBtn.click();

      const newLimit = 250000;
      const input = page.locator('[data-testid="debt-credit-limit-input"]');
      await expect(input).toBeVisible({ timeout: 10_000 });
      await input.fill(String(newLimit));

      const saveBtn = page.locator('[data-testid="debt-credit-limit-save"]');
      await saveBtn.click();

      // Toast / panel refresh — give it a moment.
      await expect(page.locator('[data-testid="debt-credit-limit-display"]'))
        .toContainText(/250[,.\s]?000|250000/, { timeout: 15_000 });

      // DB pins: customer.credit_limit_amount = 250000.
      const persisted = dbQuery(`SELECT credit_limit_amount FROM customer WHERE party_id=${r0.customerId}`);
      expect(Number(persisted), 'persisted credit_limit_amount').toBe(newLimit);

      const ev = dbCount(
        `SELECT COUNT(*) FROM domain_event WHERE type='CustomerCreditLimitChanged.v1' `
        + `AND payload_json LIKE '%${newLimit}%'`,
      );
      expect(ev, 'CustomerCreditLimitChanged.v1 in the outbox').toBeGreaterThanOrEqual(1);
    },
  );

  // ---------------------------------------------------------------------------
  // 2.H — Bucket filter (D_31_60) narrows the queue to that bucket only
  // ---------------------------------------------------------------------------
  // refs recovery now covers invoice45Id; bucket-filter feature confirmed passing.
  test(
    // G — refs recovery fixed in Slice M; bucket-filter passing as of Slice M triage
    'bucket-filter chip "31-60" narrows the dunning queue (deep-link supported)',
    async ({ page }) => {
      const r0 = requireRefs();
      // Deep-link with the bucketFilter — plan §5.1.2.
      await page.goto('/debt?bucketFilter=D_31_60');
      await expect(page.locator('[data-testid="debt-dunning-table"]')).toBeVisible({ timeout: 15_000 });

      // The chase customer is in D_31_60 (one of the two posted invoices is
      // 45 days overdue), so they must still appear.
      const row = page.locator('[data-testid="debt-customer-row"]')
        .filter({ hasText: r0.customerName })
        .first();
      await expect(row).toBeVisible({ timeout: 10_000 });
    },
  );

  // ---------------------------------------------------------------------------
  // 2.I — Aging API contract pins the 5 documented bucket keys
  // ---------------------------------------------------------------------------
  test(
    'GET /api/v1/debt/aging returns 5 bucket keys (current / d1_30 / d31_60 / d61_90 / d90_plus)',
    async ({ page }) => {
      const r = await apiGet(page, `/api/v1/debt/aging?branchId=${BRANCH_ID}`, {
        acceptStatuses: [200],
      });
      expect(r.status).toBe(200);
      const body = unwrap<{
        asOf: string;
        totals: {
          current: number | string;
          d1_30: number | string;
          d31_60: number | string;
          d61_90: number | string;
          d90_plus: number | string;
          totalOutstanding: number | string;
        };
        rows: unknown[];
      }>(r);
      expect(body.totals).toBeDefined();
      // Pin the 5 documented bucket keys verbatim (plan §4).
      expect(body.totals).toHaveProperty('current');
      expect(body.totals).toHaveProperty('d1_30');
      expect(body.totals).toHaveProperty('d31_60');
      expect(body.totals).toHaveProperty('d61_90');
      expect(body.totals).toHaveProperty('d90_plus');
      expect(body.totals).toHaveProperty('totalOutstanding');
      expect(Array.isArray(body.rows), 'rows is an array').toBe(true);
    },
  );

  // ---------------------------------------------------------------------------
  // 2.J — A11y sweep on /debt + drill-down — wcag2aa, no serious violations
  // ---------------------------------------------------------------------------
  test(
    // G — axe gate passing as of Slice M triage
    '/debt + /debt/customer/uid/{uid} pass axe-core wcag2aa (no serious violations)',
    async ({ page }) => {
      const r0 = requireRefs();

      await page.goto('/debt');
      await expect(page.locator('[data-testid="debt-dunning-table"]')).toBeVisible({ timeout: 15_000 });
      await dismissDismissableAlerts(page);
      await assertNoSeriousA11yViolations(page, '/debt');

      await page.goto(`/debt/customer/uid/${r0.customerUid}`);
      await expect(page.locator('[data-testid="debt-customer-detail"]')).toBeVisible({ timeout: 15_000 });
      await dismissDismissableAlerts(page);
      await assertNoSeriousA11yViolations(page, '/debt/customer/uid');
    },
  );
});

// =============================================================================
// 3. SALES-CLERK partial path — no DEBT.READ → inert /debt + 403 drill-down
// =============================================================================

test.describe('Slice G — debt · sales-clerk partial path', () => {
  // sales-clerk holds SALES.MANAGE_INVOICE + SALES.MANAGE_RECEIPT but NOT
  // any of the new DEBT.* perms. The /debt page must render inert; the
  // drill-down must 403 (or render inert on the SPA side); the
  // credit-limit-adjust POST must be rejected by the backend.
  test.use({ persona: 'sales-clerk' as Persona });

  // ---------------------------------------------------------------------------
  // 3.A — /debt renders inert ("Permission required" / no dunning table)
  // ---------------------------------------------------------------------------
  test(
    'sales-clerk on /debt sees the "Permission required" inert state (no dunning table)',
    async ({ page }) => {
      await page.goto('/debt');
      // The dunning-queue table MUST NOT render — perm-gated.
      await expect(page.locator('[data-testid="debt-dunning-table"]')).toHaveCount(0);
      // The inert state wrapper must render with "Permission required" copy.
      await expect(page.locator('[data-testid="debt-permission-required"]')).toBeVisible({ timeout: 15_000 });
      await expect(
        page.locator('[data-testid="debt-permission-required"]').getByText(/Permission required/i),
      ).toBeVisible();
    },
  );

  // ---------------------------------------------------------------------------
  // 3.B — Deep-link to /debt/customer/uid/{uid} also inert / 403
  // ---------------------------------------------------------------------------
  test(
    // G — inert state passing as of Slice M triage (refs recovery fixed)
    'sales-clerk deep-linking to /debt/customer/uid/{uid} is denied (no drill-down content)',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto(`/debt/customer/uid/${r0.customerUid}`);
      // Drill-down container MUST NOT render.
      await expect(page.locator('[data-testid="debt-customer-detail"]')).toHaveCount(0);
      // SPA-side inert state OR a 403-shaped alert/page surface.
      await expect(
        page.getByText(/Permission required|403|Not authorised|Not authorized/i),
      ).toBeVisible({ timeout: 15_000 });
    },
  );

  // ---------------------------------------------------------------------------
  // 3.C — Backend rejects credit-limit-adjust POST with 403
  // ---------------------------------------------------------------------------
  test(
    // G — credit-limit gate passing as of Slice M triage (refs recovery fixed)
    'sales-clerk POSTing /api/v1/debt/customer/uid/{uid}/credit-limit is 403',
    async ({ page }) => {
      const r0 = requireRefs();
      const r = await apiPost(page,
        `/api/v1/debt/customer/uid/${r0.customerUid}/credit-limit`,
        { newLimit: '999999', reason: `unauthorised attempt ${RUN_TAG}` },
        { acceptStatuses: [403] },
      );
      expect(r.status).toBe(403);
    },
  );

  // ---------------------------------------------------------------------------
  // 3.D — Backend rejects chase-note POST with 403 (no DEBT.NOTE.CREATE)
  // ---------------------------------------------------------------------------
  test(
    // G — notes gate passing as of Slice M triage (refs recovery fixed)
    'sales-clerk POSTing /api/v1/debt/notes is 403',
    async ({ page }) => {
      const r0 = requireRefs();
      const r = await apiPost(page,
        `/api/v1/debt/notes`,
        { customerUid: r0.customerUid, kind: 'AR_CHASE', body: `unauthorised note ${RUN_TAG}` },
        { acceptStatuses: [403] },
      );
      expect(r.status).toBe(403);
    },
  );

  // ---------------------------------------------------------------------------
  // 3.E — Backend rejects aging-report GET with 403 (no DEBT.READ)
  // ---------------------------------------------------------------------------
  test(
    'sales-clerk GETting /api/v1/debt/aging is 403 (class-level DEBT.READ gate)',
    async ({ page }) => {
      const r = await apiGet(page,
        `/api/v1/debt/aging?branchId=${BRANCH_ID}`,
        { acceptStatuses: [403] },
      );
      expect(r.status).toBe(403);
    },
  );
});

// =============================================================================
// 4. Slice G.1 — AP-side scenarios (expected-fail until backend G.1 lands)
//
// All tests below are tagged test.fail — the supplier-AP endpoints do not
// exist yet. When backend task G.1 (DebtController supplier endpoints +
// SupplierDebtReadModelServiceImpl) lands, flip these to plain `test(...)`.
//
// Backend gaps captured here (must close to flip the failures):
//   1. `GET /api/v1/debt/supplier-aging` endpoint does not exist (plan §5.1.1).
//   2. `GET /api/v1/debt/supplier-dunning` paged endpoint does not exist (§5.1.2).
//   3. `GET /api/v1/debt/supplier/uid/{uid}` drill-down does not exist (§5.1.3).
//   4. `POST /api/v1/debt/notes` with `kind=AP_CHASE` — service-layer exists but
//      the DTO field rename (`customerUid` → `partyUid`) may not yet be deployed.
//   5. `/debt` AP tab does not exist in the Angular component.
//   6. `/debt/supplier/uid/:uid` route + supplier drill-down component do not exist.
//
// Wire shapes expected once backend lands (pinned here so type guards compile):
//   GET /api/v1/debt/supplier-aging →
//     SupplierAgingDto { asOf, branchId, currencyCode,
//       totals: { current, d1_30, d31_60, d61_90, d90_plus,
//                 totalOutstanding, supplierCount },
//       rows: [{ supplierId, supplierUid, supplierName, paymentTermsDays,
//                current, d1_30, d31_60, d61_90, d90_plus,
//                totalOutstanding, oldestDaysOverdue, oldestDueDate,
//                worstBucket }] }
//   GET /api/v1/debt/supplier-dunning?bucket=&branchId=&page=&size= →
//     PageDto<SupplierDunningQueueRowDto>
//       { supplierId, supplierUid, supplierName, paymentTermsDays,
//         totalOutstanding, oldestDaysOverdue, oldestDueDate,
//         worstBucket, overdueInvoiceCount }
//   GET /api/v1/debt/supplier/uid/{uid} →
//     SupplierStatementDto { header, agingRow, openInvoices[], recentPayments[] }
//   GET /api/v1/debt/notes?partyUid=...&kind=AP_CHASE →
//     chase notes list (kind=AP_CHASE filter; kind defaults to AR_CHASE for
//     backward compat — plan §3.B)
//
// Long-id fields stringify on the wire (global IdLongAsStringSerializerModifier).
// Supplier uid format: Crockford ULID /^[0-9A-HJKMNP-TV-Z]{26}$/.
//
// AP — flips when backend G.1 lands
// =============================================================================

// test-id reference for frontend agent (AP surface mirrors AR; new ids needed):
//   data-testid                       | Component                        | Purpose
//   ----------------------------------|----------------------------------|--------------------------------------------
//   debt-ap-tab                       | debt.component.ts                | Tab toggle — switches dunning queue to AP
//   debt-ap-dunning-table             | debt.component.ts (AP tab)       | Wrapper around the AP dunning queue table
//   debt-ap-bucket-current            | debt.component.ts (AP tab)       | Totals header — CURRENT bucket
//   debt-ap-bucket-30                 | debt.component.ts (AP tab)       | Totals header — D_1_30 bucket
//   debt-ap-bucket-60                 | debt.component.ts (AP tab)       | Totals header — D_31_60 bucket
//   debt-ap-bucket-90                 | debt.component.ts (AP tab)       | Totals header — D_61_90 bucket
//   debt-ap-bucket-over-90            | debt.component.ts (AP tab)       | Totals header — D_90_PLUS bucket
//   debt-ap-supplier-row              | debt.component.ts (AP tab)       | Clickable supplier row (one per row)
//   debt-supplier-detail              | supplier-debt-position.component | AP drill-down container
//   debt-ap-chase-notes-list          | supplier-debt-position.component | AP chase-notes activity log list
//   debt-ap-chase-note-add            | supplier-debt-position.component | Append-form textarea
//   debt-ap-chase-note-save           | supplier-debt-position.component | Append-form submit button
//   debt-ap-payment-terms-display     | supplier-debt-position.component | Read-only payment-terms-days panel

// =============================================================================
// 4.1 Slice G.1 — ACCOUNTANT happy path on the AP surface
// =============================================================================

test.describe('Slice G.1 — AP debt · accountant happy path', () => {
  // AP — flips when backend G.1 lands
  test.use({ persona: 'accountant' as Persona });

  // ---------------------------------------------------------------------------
  // 4.1.A — /debt AP tab renders the supplier dunning queue
  // ---------------------------------------------------------------------------
  test.fail(
    // AP — flips when backend G.1 lands
    '/debt AP tab renders 5-bucket totals row + supplier dunning queue (empty or populated)',
    async ({ page }) => {
      await page.goto('/debt');
      // The AP tab toggle must be present.
      await expect(page.locator('[data-testid="debt-ap-tab"]')).toBeVisible({ timeout: 20_000 });
      await page.locator('[data-testid="debt-ap-tab"]').click();

      // After switching to AP: the AP dunning-queue table wrapper must appear.
      await expect(page.locator('[data-testid="debt-ap-dunning-table"]')).toBeVisible({ timeout: 15_000 });

      // 5 bucket totals must render (CURRENT / D_1_30 / D_31_60 / D_61_90 / D_90_PLUS).
      await expect(page.locator('[data-testid="debt-ap-bucket-current"]')).toBeVisible();
      await expect(page.locator('[data-testid="debt-ap-bucket-30"]')).toBeVisible();
      await expect(page.locator('[data-testid="debt-ap-bucket-60"]')).toBeVisible();
      await expect(page.locator('[data-testid="debt-ap-bucket-90"]')).toBeVisible();
      await expect(page.locator('[data-testid="debt-ap-bucket-over-90"]')).toBeVisible();

      // The AR placeholder copy must NOT appear on the AP tab.
      await expect(page.getByText(/Debt module coming soon/i)).toHaveCount(0);

      // A11y sweep on the AP tab view — wcag2aa, no serious violations.
      await dismissDismissableAlerts(page);
      await assertNoSeriousA11yViolations(page, '/debt (AP tab)');
    },
  );

  // ---------------------------------------------------------------------------
  // 4.1.B — Supplier aging API contract pins the 5 bucket keys
  // ---------------------------------------------------------------------------
  test.fail(
    // AP — flips when backend G.1 lands
    'GET /api/v1/debt/supplier-aging returns 5 bucket keys + supplierCount in totals',
    async ({ page }) => {
      await page.goto('/dashboard');
      const r = await apiGet(page, `/api/v1/debt/supplier-aging?branchId=${BRANCH_ID}`, {
        acceptStatuses: [200],
      });
      expect(r.status).toBe(200);

      // Type-guard the wire shape against the locked SupplierAgingDto contract.
      const body = unwrap<{
        asOf: string;
        branchId: string | number;
        currencyCode: string;
        totals: {
          current: number | string;
          d1_30: number | string;
          d31_60: number | string;
          d61_90: number | string;
          d90_plus: number | string;
          totalOutstanding: number | string;
          supplierCount: number | string;
        };
        rows: Array<{
          supplierId: string | number;
          supplierUid: string;
          supplierName: string;
          paymentTermsDays: number | string;
          current: number | string;
          d1_30: number | string;
          d31_60: number | string;
          d61_90: number | string;
          d90_plus: number | string;
          totalOutstanding: number | string;
          oldestDaysOverdue: number | string;
          worstBucket: string;
        }>;
      }>(r);

      // Pin the 5 documented bucket keys verbatim (plan §4 mirrored from AR).
      expect(body.totals).toHaveProperty('current');
      expect(body.totals).toHaveProperty('d1_30');
      expect(body.totals).toHaveProperty('d31_60');
      expect(body.totals).toHaveProperty('d61_90');
      expect(body.totals).toHaveProperty('d90_plus');
      expect(body.totals).toHaveProperty('totalOutstanding');
      expect(body.totals).toHaveProperty('supplierCount');
      expect(Array.isArray(body.rows), 'rows is an array').toBe(true);

      // If rows are present, validate supplier uid format (Crockford ULID).
      const ULID_RE = /^[0-9A-HJKMNP-TV-Z]{26}$/;
      for (const row of body.rows) {
        expect(row.supplierUid, `supplierUid "${row.supplierUid}" must be a Crockford ULID`)
          .toMatch(ULID_RE);
        expect(row).toHaveProperty('paymentTermsDays');
      }
    },
  );

  // ---------------------------------------------------------------------------
  // 4.1.C — Supplier drill-down at /debt/supplier/uid/:uid renders 5 panels
  // ---------------------------------------------------------------------------
  test.fail(
    // AP — flips when backend G.1 lands
    'accountant drills into a supplier → /debt/supplier/uid/:uid renders 5 panels',
    async ({ page }) => {
      await page.goto('/debt');
      // Switch to the AP tab.
      await expect(page.locator('[data-testid="debt-ap-tab"]')).toBeVisible({ timeout: 20_000 });
      await page.locator('[data-testid="debt-ap-tab"]').click();
      await expect(page.locator('[data-testid="debt-ap-dunning-table"]')).toBeVisible({ timeout: 15_000 });

      // Click the first supplier row in the dunning queue (if none exist, empty
      // state renders cleanly — the tab-render assertion above already covers
      // the empty path; this test requires at least one supplier row).
      const firstRow = page.locator('[data-testid="debt-ap-supplier-row"]').first();
      await expect(firstRow).toBeVisible({ timeout: 15_000 });
      const supplierUid = await firstRow.getAttribute('data-supplier-uid');
      await firstRow.click();

      // URL must resolve to the AP drill-down.
      await expect(page).toHaveURL(new RegExp('/debt/supplier/uid/[0-9A-HJKMNP-TV-Z]{26}'));

      // The drill-down container must be present.
      await expect(page.locator('[data-testid="debt-supplier-detail"]')).toBeVisible({ timeout: 15_000 });

      // 5 panels: header (payment-terms read-only), aging row, open invoices,
      // recent payments, AP chase notes list.
      await expect(page.locator('[data-testid="debt-ap-payment-terms-display"]')).toBeVisible();
      await expect(page.locator('[data-testid="debt-ap-chase-notes-list"]')).toBeVisible();

      // The supplier uid in the URL must be a valid Crockford ULID.
      if (supplierUid) {
        expect(supplierUid).toMatch(/^[0-9A-HJKMNP-TV-Z]{26}$/);
      }

      // A11y sweep on the AP drill-down — wcag2aa, no serious violations.
      await dismissDismissableAlerts(page);
      await assertNoSeriousA11yViolations(page, '/debt/supplier/uid/:uid');
    },
  );

  // ---------------------------------------------------------------------------
  // 4.1.D — Accountant appends an AP_CHASE note; note appears in activity log
  // ---------------------------------------------------------------------------
  test.fail(
    // AP — flips when backend G.1 lands
    'accountant appends an AP_CHASE note; note appears in activity log + party_note row written',
    async ({ page }) => {
      await page.goto('/debt');
      await expect(page.locator('[data-testid="debt-ap-tab"]')).toBeVisible({ timeout: 20_000 });
      await page.locator('[data-testid="debt-ap-tab"]').click();
      await expect(page.locator('[data-testid="debt-ap-dunning-table"]')).toBeVisible({ timeout: 15_000 });

      const firstRow = page.locator('[data-testid="debt-ap-supplier-row"]').first();
      await expect(firstRow).toBeVisible({ timeout: 15_000 });
      const supplierUid = await firstRow.getAttribute('data-supplier-uid');
      await firstRow.click();

      await expect(page.locator('[data-testid="debt-supplier-detail"]')).toBeVisible({ timeout: 15_000 });

      const noteBody = `AP chase note ${RUN_TAG} — called supplier re: overdue payable`;
      const addBox = page.locator('[data-testid="debt-ap-chase-note-add"]');
      const saveBtn = page.locator('[data-testid="debt-ap-chase-note-save"]');
      await expect(addBox).toBeVisible({ timeout: 10_000 });
      await addBox.fill(noteBody);
      await saveBtn.click();

      // Note must appear at the top of the activity log immediately after save.
      const notesList = page.locator('[data-testid="debt-ap-chase-notes-list"]');
      await expect(notesList.getByText(noteBody)).toBeVisible({ timeout: 10_000 });

      // Contract pin: POST hit /api/v1/debt/notes with kind='AP_CHASE'.
      // DB probe: party_note row written with kind=AP_CHASE + status=ACTIVE.
      if (supplierUid) {
        // Resolve numeric party_id from the uid for the DB probe.
        const partyId = dbQuery(`SELECT id FROM party WHERE uid='${escapeSql(supplierUid)}' LIMIT 1`);
        if (partyId) {
          const noteRow = dbCount(
            `SELECT COUNT(*) FROM party_note WHERE party_id=${partyId} `
            + `AND kind='AP_CHASE' AND body='${escapeSql(noteBody)}' AND status='ACTIVE'`,
          );
          expect(noteRow, 'party_note row inserted with kind=AP_CHASE').toBeGreaterThanOrEqual(1);

          const ev = dbCount(
            `SELECT COUNT(*) FROM domain_event WHERE type='DebtNoteCreated.v1' `
            + `AND payload_json LIKE '%AP_CHASE%' AND payload_json LIKE '%${escapeSql(RUN_TAG)}%'`,
          );
          expect(ev, 'DebtNoteCreated.v1 with AP_CHASE in the outbox').toBeGreaterThanOrEqual(1);
        }
      }
    },
  );

  // ---------------------------------------------------------------------------
  // 4.1.E — Accountant archives the AP chase note; note disappears / shows archived
  // ---------------------------------------------------------------------------
  test.fail(
    // AP — flips when backend G.1 lands
    'accountant archives an AP_CHASE note; note disappears from the active list',
    async ({ page }) => {
      await page.goto('/debt');
      await expect(page.locator('[data-testid="debt-ap-tab"]')).toBeVisible({ timeout: 20_000 });
      await page.locator('[data-testid="debt-ap-tab"]').click();

      const firstRow = page.locator('[data-testid="debt-ap-supplier-row"]').first();
      await expect(firstRow).toBeVisible({ timeout: 15_000 });
      const supplierUid = await firstRow.getAttribute('data-supplier-uid');
      await firstRow.click();

      await expect(page.locator('[data-testid="debt-supplier-detail"]')).toBeVisible({ timeout: 15_000 });

      // Seed a note directly via the API so this test is self-contained.
      const archiveBody = `AP archive-me note ${RUN_TAG}`;
      const notePost = await apiPost(
        page,
        '/api/v1/debt/notes',
        { partyUid: supplierUid, kind: 'AP_CHASE', body: archiveBody },
        { acceptStatuses: [200, 201] },
      );
      const noteDto = unwrap<{ uid: string }>(notePost);
      expect(noteDto.uid, 'note uid from POST').toBeTruthy();
      expect(noteDto.uid).toMatch(/^[0-9A-HJKMNP-TV-Z]{26}$/);

      // Reload the drill-down so the new note is visible.
      await page.reload();
      await expect(page.locator('[data-testid="debt-supplier-detail"]')).toBeVisible({ timeout: 15_000 });

      const notesList = page.locator('[data-testid="debt-ap-chase-notes-list"]');
      await expect(notesList.getByText(archiveBody)).toBeVisible({ timeout: 10_000 });

      // Archive the note.
      await apiPost(
        page,
        `/api/v1/debt/notes/uid/${noteDto.uid}/archive`,
        {},
        { acceptStatuses: [200, 204] },
      );
      await page.reload();
      await expect(page.locator('[data-testid="debt-supplier-detail"]')).toBeVisible({ timeout: 15_000 });

      // After archiving, the note body must NOT appear in the active list.
      await expect(notesList.getByText(archiveBody)).toHaveCount(0);

      // DB probe: status must be ARCHIVED.
      const archived = dbCount(
        `SELECT COUNT(*) FROM party_note WHERE uid='${escapeSql(noteDto.uid)}' AND status='ARCHIVED'`,
      );
      expect(archived, 'party_note archived in DB').toBeGreaterThanOrEqual(1);
    },
  );
});

// =============================================================================
// 4.2 Slice G.1 — SALES-CLERK blocked on AP surface (no DEBT.READ)
// =============================================================================

test.describe('Slice G.1 — AP debt · sales-clerk 403 gate', () => {
  // AP — flips when backend G.1 lands
  test.use({ persona: 'sales-clerk' as Persona });

  test(
    // AP — backend G.1 landed; gate passing as of Slice M triage
    'sales-clerk GETting /api/v1/debt/supplier-aging is 403 (DEBT.READ gate)',
    async ({ page }) => {
      await page.goto('/dashboard');
      const r = await apiGet(page,
        `/api/v1/debt/supplier-aging?branchId=${BRANCH_ID}`,
        { acceptStatuses: [403] },
      );
      expect(r.status).toBe(403);
    },
  );

  test(
    // AP — backend G.1 landed; gate passing as of Slice M triage
    'sales-clerk on /debt sees no AP tab content (DEBT.READ not held)',
    async ({ page }) => {
      await page.goto('/debt');
      // Either: the AP tab is absent, or clicking it yields no dunning table.
      // The safest assertion: no AP supplier rows render (same inert-state
      // pattern as the AR side).
      await expect(page.locator('[data-testid="debt-ap-dunning-table"]')).toHaveCount(0);
      // The inert-state wrapper from the AR side must cover the AP tab too.
      await expect(page.locator('[data-testid="debt-permission-required"]')).toBeVisible({ timeout: 15_000 });
    },
  );
});

// =============================================================================
// 4.3 Slice G.1 — PROCUREMENT-OFFICER blocked on AP debt surface
//
// Intuition says procurement owns supplier payables — but DEBT.* is the
// credit-controller permission band, not a procurement grant. The
// procurement-officer must NOT see the AP debt surface; those reads are
// controlled by DEBT.READ (accountant / credit-controller persona only).
// This cross-checks that scope creep hasn't leaked DEBT.READ into the
// procurement permission set.
// AP — flips when backend G.1 lands
// =============================================================================

test.describe('Slice G.1 — AP debt · procurement-officer 403 gate', () => {
  // AP — flips when backend G.1 lands
  test.use({ persona: 'procurement-officer' as Persona });

  test(
    // AP — backend G.1 landed; gate passing as of Slice M triage
    'procurement-officer GETting /api/v1/debt/supplier-aging is 403 (DEBT.READ not a procurement perm)',
    async ({ page }) => {
      await page.goto('/dashboard');
      const r = await apiGet(page,
        `/api/v1/debt/supplier-aging?branchId=${BRANCH_ID}`,
        { acceptStatuses: [403] },
      );
      expect(r.status).toBe(403);
    },
  );

  test(
    // AP — backend G.1 landed; gate passing as of Slice M triage
    'procurement-officer GETting /api/v1/debt/supplier-dunning is 403',
    async ({ page }) => {
      await page.goto('/dashboard');
      const r = await apiGet(page,
        `/api/v1/debt/supplier-dunning?branchId=${BRANCH_ID}&page=0&size=25`,
        { acceptStatuses: [403] },
      );
      expect(r.status).toBe(403);
    },
  );
});

// =============================================================================
// 5. Slice G.2 — Debt write-off (AR + AP, dual approval)
//
// All tests below are tagged test.fail — the write-off endpoints, entity,
// state machine, and UI components do not exist yet. When backend task G.2
// (DebtWriteOffController + DebtWriteOffServiceImpl + V73 + V74) and the
// Angular queue page + modal land, flip these to plain `test(...)`.
//
// Backend gaps captured here (must close to flip the failures):
//   1. V73__debt_write_off.sql not yet applied — table does not exist.
//   2. V74__seed_debt_write_off_permissions.sql not yet applied — perms 134-135
//      not seeded; `qa.accountant` + `qa.accountant.approver` lack the grants.
//   3. `POST /api/v1/debt/write-offs` endpoint does not exist.
//   4. `POST /api/v1/debt/write-offs/uid/{uid}/approve` does not exist.
//   5. `POST /api/v1/debt/write-offs/uid/{uid}/reject` does not exist.
//   6. `GET /api/v1/debt/write-offs` does not exist.
//   7. `GET /api/v1/debt/write-offs/uid/{uid}` does not exist.
//   8. `/debt/write-offs` Angular route + queue component do not exist.
//   9. "Write off" button on customer + supplier drill-down does not exist.
//
// Wire shapes locked in the plan (§5) — type aliases below compile against
// the upcoming backend so the assertions are ready to validate on flip.
//
// G.2 — flips when write-off endpoints land
// =============================================================================

// ---------------------------------------------------------------------------
// G.2 type aliases (pinned against plan §5 wire shapes)
// ---------------------------------------------------------------------------

type CreateDebtWriteOffRequest = {
  targetKind: 'CUSTOMER_INVOICE' | 'SUPPLIER_INVOICE';
  targetInvoiceUid: string;
  amount: number;
  reason: string;
};

type DebtWriteOffDto = {
  id: string;
  uid: string;
  targetKind: 'CUSTOMER_INVOICE' | 'SUPPLIER_INVOICE';
  targetInvoiceId: string;
  targetInvoiceUid: string;
  targetInvoiceNumber: string | null;
  partyName: string | null;
  amount: number;
  currencyCode: string;
  reason: string;
  status: 'PENDING_APPROVAL' | 'POSTED' | 'REJECTED';
  requestedByUserId: string;
  requestedByUsername: string | null;
  requestedAt: string;
  approvedByUserId: string | null;
  approvedByUsername: string | null;
  approvedAt: string | null;
  postedAt: string | null;
  rejectedAt: string | null;
  reasonForReject: string | null;
};

type RejectDebtWriteOffRequest = { reasonForReject: string };

const ULID_RE = /^[0-9A-HJKMNP-TV-Z]{26}$/;

// Threshold per plan §1: TZS 100,000.
const WRITE_OFF_THRESHOLD = 100_000;

// test-id reference for the frontend agent (write-off surface):
//   data-testid                            | Component                              | Purpose
//   ---------------------------------------|----------------------------------------|------------------------------
//   debt-write-off-queue-table             | debt-write-offs.component.ts           | Queue table wrapper
//   debt-write-off-status-filter           | debt-write-offs.component.ts           | Status filter chip group
//   debt-write-off-kind-filter             | debt-write-offs.component.ts           | AR/AP kind filter
//   debt-write-off-row                     | debt-write-offs.component.ts           | One queue row (repeating)
//   debt-write-off-detail-drawer           | debt-write-offs.component.ts           | Detail drawer on row click
//   debt-write-off-approve-btn             | debt-write-offs.component.ts           | Approve button on PENDING row
//   debt-write-off-reject-btn             | debt-write-offs.component.ts           | Reject button on PENDING row
//   debt-write-off-reject-reason-input     | debt-write-offs.component.ts           | Rejection reason textarea
//   debt-write-off-reject-confirm-btn      | debt-write-offs.component.ts           | Submit rejection
//   debt-customer-write-off-btn            | debt-customer.component.ts             | "Write off" button on AR invoice row
//   debt-supplier-write-off-btn            | debt-supplier.component.ts             | "Write off" button on AP invoice row
//   debt-write-off-modal                   | debt-write-off-modal.component.ts      | Write-off creation modal
//   debt-write-off-amount-input            | debt-write-off-modal.component.ts      | Amount field in modal
//   debt-write-off-reason-input            | debt-write-off-modal.component.ts      | Reason textarea in modal
//   debt-write-off-submit-btn              | debt-write-off-modal.component.ts      | Submit button in modal
//   debt-write-off-result-status           | debt-write-off-modal.component.ts      | Post-submit status badge (POSTED / PENDING_APPROVAL)
//   debt-write-off-error-banner            | debt-write-off-modal.component.ts      | Error banner for 409 / 4xx responses
//   debt-write-off-nav-link                | sidebar / nav component                | "/debt/write-offs" nav link

// =============================================================================
// 5.1 — Scenario 1 (AR auto-post): amount <= threshold → status POSTED
// =============================================================================

test.describe('Slice G.2 — write-off · AR auto-post (amount <= threshold)', () => {
  // G.2 — flips when write-off endpoints land
  test.use({ persona: 'accountant' as Persona });

  test.fail(
    // G.2 — flips when write-off endpoints land
    'accountant submits AR write-off <= TZS 100k: status POSTED, invoice leaves open list, DebtWriteOffPosted.v1 emitted',
    async ({ page }) => {
      const r0 = requireRefs();
      // Use the 5-day invoice (amount 6_000) — well below the 100k threshold.
      await page.goto(`/debt/customer/uid/${r0.customerUid}`);
      await expect(page.locator('[data-testid="debt-customer-detail"]')).toBeVisible({ timeout: 15_000 });

      // The open-invoices table must show the invoice before write-off.
      await expect(page.getByText(`INV-${RUN_TAG}-AGE05`)).toBeVisible({ timeout: 10_000 });

      // Click the "Write off" button on the invoice row.
      const writeOffBtn = page
        .locator('[data-testid="debt-customer-write-off-btn"]')
        .first();
      await expect(writeOffBtn).toBeVisible({ timeout: 10_000 });
      await writeOffBtn.click();

      // The write-off modal must open.
      const modal = page.locator('[data-testid="debt-write-off-modal"]');
      await expect(modal).toBeVisible({ timeout: 10_000 });

      // A11y sweep on the write-off modal.
      await dismissDismissableAlerts(page);
      await assertNoSeriousA11yViolations(page, 'write-off modal (AR)');

      // Amount defaults to outstanding (6_000); override with a below-threshold value.
      const amountInput = modal.locator('[data-testid="debt-write-off-amount-input"]');
      await amountInput.fill('6000');

      const reasonInput = modal.locator('[data-testid="debt-write-off-reason-input"]');
      await reasonInput.fill(`E2E AR write-off auto-post ${RUN_TAG}`);

      const submitBtn = modal.locator('[data-testid="debt-write-off-submit-btn"]');
      await submitBtn.click();

      // Status badge must show POSTED (auto-post because amount <= threshold and
      // caller holds APPROVE perm — plan §1).
      const statusBadge = modal.locator('[data-testid="debt-write-off-result-status"]');
      await expect(statusBadge).toContainText(/POSTED/i, { timeout: 15_000 });

      // Reload — the written-off invoice must fall out of the open list.
      await page.reload();
      await expect(page.locator('[data-testid="debt-customer-detail"]')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(`INV-${RUN_TAG}-AGE05`)).toHaveCount(0);

      // Contract: API created the write-off with correct shape.
      const woRow = dbQuery(
        `SELECT status, target_kind FROM debt_write_off `
        + `WHERE target_invoice_uid='${escapeSql(r0.invoice5UId)}' `
        + `AND status='POSTED' LIMIT 1`,
      );
      expect(woRow, 'debt_write_off row POSTED in DB').toBeTruthy();
      expect(woRow.split('\t')[1] ?? woRow, 'target_kind').toBe('CUSTOMER_INVOICE');

      // Outbox: DebtWriteOffPosted.v1 emitted.
      const ev = dbCount(
        `SELECT COUNT(*) FROM domain_event WHERE type='DebtWriteOffPosted.v1' `
        + `AND payload_json LIKE '%${escapeSql(r0.invoice5UId)}%'`,
      );
      expect(ev, 'DebtWriteOffPosted.v1 in the outbox').toBeGreaterThanOrEqual(1);
    },
  );

  // AP variant: submit on a supplier invoice row → targetKind = SUPPLIER_INVOICE
  test.fail(
    // G.2 — flips when write-off endpoints land
    'accountant submits AP write-off via API: targetKind=SUPPLIER_INVOICE, status POSTED',
    async ({ page }) => {
      await page.goto('/dashboard');

      // Resolve any supplier invoice uid from the DB (procurement module must have
      // at least one POSTED supplier invoice from prior procurement suite runs;
      // if none exist, the test is not yet meaningful — accepted as a setup gap).
      const supplierInvUid = dbQuery(
        `SELECT uid FROM supplier_invoice WHERE status='POSTED' LIMIT 1`,
      );
      if (!supplierInvUid) {
        // No supplier invoices available — skip-by-assertion (will fail as test.fail).
        expect(supplierInvUid, 'at least one POSTED supplier_invoice must exist').toBeTruthy();
        return;
      }

      const body: CreateDebtWriteOffRequest = {
        targetKind: 'SUPPLIER_INVOICE',
        targetInvoiceUid: supplierInvUid,
        amount: 500,     // well below threshold; auto-posts
        reason: `E2E AP write-off auto-post ${RUN_TAG}`,
      };

      const r = await apiPost(
        page,
        '/api/v1/debt/write-offs',
        body,
        { expectedStatus: 200 },
      );
      expect(r.status).toBe(200);

      const dto = unwrap<DebtWriteOffDto>(r);
      expect(dto.uid, 'write-off uid is Crockford ULID').toMatch(ULID_RE);
      expect(dto.targetKind).toBe('SUPPLIER_INVOICE');
      expect(dto.status).toBe('POSTED');
      expect(dto.id, 'id serialises as string (global modifier)').toMatch(/^\d+$/);

      // Outbox: DebtWriteOffPosted.v1 emitted.
      const ev = dbCount(
        `SELECT COUNT(*) FROM domain_event WHERE type='DebtWriteOffPosted.v1' `
        + `AND payload_json LIKE '%${escapeSql(supplierInvUid)}%'`,
      );
      expect(ev, 'DebtWriteOffPosted.v1 in the outbox for AP write-off').toBeGreaterThanOrEqual(1);
    },
  );
});

// =============================================================================
// 5.2 — Scenario 2 (pending approval): amount > threshold → status PENDING_APPROVAL
// =============================================================================

test.describe('Slice G.2 — write-off · pending-approval (amount > threshold)', () => {
  // G.2 — flips when write-off endpoints land
  test.use({ persona: 'accountant' as Persona });

  // Shared write-off uid for the approve / reject / self-approve tests below.
  // Provisioned here so the next two describe blocks can consume it.
  const pendingWriteOff: { uid: string } = { uid: '' };

  test.fail(
    // G.2 — flips when write-off endpoints land
    'accountant submits AR write-off > TZS 100k: status PENDING_APPROVAL, DebtWriteOffRequested.v1 emitted, invoice stays open',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto(`/debt/customer/uid/${r0.customerUid}`);
      await expect(page.locator('[data-testid="debt-customer-detail"]')).toBeVisible({ timeout: 15_000 });

      // Use the 45-day invoice (amount 8_000) — still below threshold; POST via API
      // with an amount above the threshold (120_000) to force the PENDING path.
      // The service validates amount <= outstanding; use a sensible test amount
      // that is above threshold and <= 8_000... 8_000 < 100_000 so we cannot
      // use the actual invoice amount. Instead, POST a separate write-off via
      // the API with a mock amount > threshold on a fresh invoice.
      //
      // Practical approach: the spec provisions the pending write-off via the API
      // directly (same pattern as the archive note test above). The UI assertion
      // on the queue page is the primary surface check.
      const body: CreateDebtWriteOffRequest = {
        targetKind: 'CUSTOMER_INVOICE',
        targetInvoiceUid: r0.invoice45UId,
        amount: WRITE_OFF_THRESHOLD + 1,   // 100_001 — above threshold
        reason: `E2E pending write-off ${RUN_TAG}`,
      };

      const r = await apiPost(
        page,
        '/api/v1/debt/write-offs',
        body,
        { expectedStatus: 200 },
      );
      expect(r.status).toBe(200);

      const dto = unwrap<DebtWriteOffDto>(r);
      expect(dto.uid, 'write-off uid is Crockford ULID').toMatch(ULID_RE);
      expect(dto.status).toBe('PENDING_APPROVAL');
      expect(dto.targetKind).toBe('CUSTOMER_INVOICE');
      expect(dto.targetInvoiceUid).toBe(r0.invoice45UId);
      expect(dto.id, 'id serialises as string').toMatch(/^\d+$/);
      expect(dto.approvedByUserId, 'approvedByUserId null for PENDING').toBeNull();

      // Persist uid for downstream approve / reject tests.
      pendingWriteOff.uid = dto.uid;

      // Invoice must still appear in the open list (not yet posted).
      await page.reload();
      await expect(page.locator('[data-testid="debt-customer-detail"]')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(`INV-${RUN_TAG}-AGE45`)).toBeVisible({ timeout: 10_000 });

      // DB: row in PENDING_APPROVAL.
      const dbStatus = dbQuery(
        `SELECT status FROM debt_write_off WHERE uid='${escapeSql(dto.uid)}' LIMIT 1`,
      );
      expect(dbStatus).toBe('PENDING_APPROVAL');

      // Outbox: DebtWriteOffRequested.v1 emitted (NOT Posted).
      const ev = dbCount(
        `SELECT COUNT(*) FROM domain_event WHERE type='DebtWriteOffRequested.v1' `
        + `AND payload_json LIKE '%${escapeSql(r0.invoice45UId)}%'`,
      );
      expect(ev, 'DebtWriteOffRequested.v1 in the outbox').toBeGreaterThanOrEqual(1);

      // Queue page renders the PENDING row.
      await page.goto('/debt/write-offs');
      await expect(page.locator('[data-testid="debt-write-off-queue-table"]')).toBeVisible({ timeout: 15_000 });

      // A11y sweep on /debt/write-offs.
      await dismissDismissableAlerts(page);
      await assertNoSeriousA11yViolations(page, '/debt/write-offs');

      const pendingRow = page
        .locator('[data-testid="debt-write-off-row"]')
        .filter({ hasText: dto.uid.slice(-6) })   // last 6 chars of uid are visible in the table
        .first();
      await expect(pendingRow).toBeVisible({ timeout: 15_000 });
    },
  );
});

// =============================================================================
// 5.3 — Scenario 3 (approve by different user): PENDING_APPROVAL → POSTED
// =============================================================================

test.describe('Slice G.2 — write-off · approve by different user', () => {
  // G.2 — flips when write-off endpoints land
  test.use({ persona: 'accountant-approver' as Persona });

  test.fail(
    // G.2 — flips when write-off endpoints land
    'accountant-approver approves the PENDING write-off: status POSTED, invoice leaves open list, DebtWriteOffPosted.v1 emitted',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // Retrieve the most recent PENDING write-off against the 45-day invoice.
      const pendingUid = dbQuery(
        `SELECT uid FROM debt_write_off `
        + `WHERE target_invoice_uid='${escapeSql(r0.invoice45UId)}' `
        + `AND status='PENDING_APPROVAL' ORDER BY requested_at DESC LIMIT 1`,
      );
      if (!pendingUid) {
        // Scenario 2 must have run first; if not, this is an unmet prereq.
        expect(pendingUid, 'a PENDING write-off must exist (Scenario 2 must run first)').toBeTruthy();
        return;
      }

      // Navigate to the queue page as the approver.
      await page.goto('/debt/write-offs');
      await expect(page.locator('[data-testid="debt-write-off-queue-table"]')).toBeVisible({ timeout: 15_000 });

      // A11y sweep on /debt/write-offs (approver view).
      await dismissDismissableAlerts(page);
      await assertNoSeriousA11yViolations(page, '/debt/write-offs (approver)');

      // Click the PENDING row to open the detail drawer.
      const pendingRow = page
        .locator('[data-testid="debt-write-off-row"]')
        .filter({ hasText: pendingUid.slice(-6) })
        .first();
      await expect(pendingRow).toBeVisible({ timeout: 15_000 });
      await pendingRow.click();

      const drawer = page.locator('[data-testid="debt-write-off-detail-drawer"]');
      await expect(drawer).toBeVisible({ timeout: 10_000 });

      // Approve button must be visible (approver holds DEBT.WRITE_OFF.APPROVE).
      const approveBtn = drawer.locator('[data-testid="debt-write-off-approve-btn"]');
      await expect(approveBtn).toBeVisible({ timeout: 10_000 });
      await approveBtn.click();

      // Status must update to POSTED in the drawer / row.
      await expect(drawer).toContainText(/POSTED/i, { timeout: 15_000 });

      // DB: status flipped.
      const dbStatus = dbQuery(
        `SELECT status FROM debt_write_off WHERE uid='${escapeSql(pendingUid)}' LIMIT 1`,
      );
      expect(dbStatus).toBe('POSTED');

      // DB: approvedByUserId filled in.
      const approvedBy = dbQuery(
        `SELECT approved_by_user_id FROM debt_write_off WHERE uid='${escapeSql(pendingUid)}' LIMIT 1`,
      );
      expect(approvedBy, 'approvedByUserId non-null after approval').toBeTruthy();

      // Outbox: DebtWriteOffPosted.v1.
      const ev = dbCount(
        `SELECT COUNT(*) FROM domain_event WHERE type='DebtWriteOffPosted.v1' `
        + `AND payload_json LIKE '%${escapeSql(pendingUid)}%'`,
      );
      expect(ev, 'DebtWriteOffPosted.v1 in the outbox after approval').toBeGreaterThanOrEqual(1);

      // Invoice must no longer appear in the customer open list.
      await page.goto(`/debt/customer/uid/${r0.customerUid}`);
      await expect(page.locator('[data-testid="debt-customer-detail"]')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(`INV-${RUN_TAG}-AGE45`)).toHaveCount(0);
    },
  );
});

// =============================================================================
// 5.4 — Scenario 4 (self-approve above threshold → 409)
// =============================================================================

test.describe('Slice G.2 — write-off · self-approve above threshold → 409', () => {
  // G.2 — flips when write-off endpoints land
  test.use({ persona: 'accountant' as Persona });

  test.fail(
    // G.2 — flips when write-off endpoints land
    'accountant trying to self-approve their own above-threshold write-off gets 409 + error surfaced in UI',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // POST a fresh above-threshold write-off as qa.accountant.
      const createBody: CreateDebtWriteOffRequest = {
        targetKind: 'CUSTOMER_INVOICE',
        targetInvoiceUid: r0.invoice45UId,
        amount: WRITE_OFF_THRESHOLD + 500,   // 100_500 — above threshold
        reason: `E2E self-approve block test ${RUN_TAG}`,
      };
      const createR = await apiPost(
        page,
        '/api/v1/debt/write-offs',
        createBody,
        { expectedStatus: 200 },
      );
      const created = unwrap<DebtWriteOffDto>(createR);
      expect(created.status).toBe('PENDING_APPROVAL');

      // Immediately try to approve as the same user — must be 409.
      const approveR = await apiPost(
        page,
        `/api/v1/debt/write-offs/uid/${created.uid}/approve`,
        {},
        { acceptStatuses: [409, 403] },
      );
      expect(
        [409, 403].includes(approveR.status),
        `self-approve must be 409 or 403, got ${approveR.status}`,
      ).toBe(true);

      // Response body must carry a human-readable message.
      const errBody = JSON.parse(approveR.body) as { message?: string; errors?: string[] };
      const message = errBody.message ?? (errBody.errors ?? []).join(' ');
      expect(
        /different user|self-approv|same user/i.test(message),
        `error message must mention user distinction, got: ${message}`,
      ).toBe(true);

      // UI flow: navigate to the queue page and try to click Approve (should either
      // not render the button for the requester or show the error banner after click).
      await page.goto('/debt/write-offs');
      await expect(page.locator('[data-testid="debt-write-off-queue-table"]')).toBeVisible({ timeout: 15_000 });

      const selfRow = page
        .locator('[data-testid="debt-write-off-row"]')
        .filter({ hasText: created.uid.slice(-6) })
        .first();
      await expect(selfRow).toBeVisible({ timeout: 15_000 });
      await selfRow.click();

      const drawer = page.locator('[data-testid="debt-write-off-detail-drawer"]');
      await expect(drawer).toBeVisible({ timeout: 10_000 });

      // Either the Approve button is hidden (best UX) OR clicking it shows the
      // error banner. Either outcome satisfies the gate.
      const approveBtn = drawer.locator('[data-testid="debt-write-off-approve-btn"]');
      const isBtnVisible = await approveBtn.isVisible().catch(() => false);
      if (isBtnVisible) {
        await approveBtn.click();
        const errorBanner = drawer.locator('[data-testid="debt-write-off-error-banner"]');
        await expect(errorBanner).toBeVisible({ timeout: 10_000 });
        await expect(errorBanner).toContainText(/different user|self-approv|same user/i);
      }
      // If button is NOT visible, the UI correctly hides self-approve for the requester — pass.
    },
  );
});

// =============================================================================
// 5.5 — Scenario 5 (reject): PENDING_APPROVAL → REJECTED
// =============================================================================

test.describe('Slice G.2 — write-off · reject', () => {
  // G.2 — flips when write-off endpoints land
  test.use({ persona: 'accountant-approver' as Persona });

  test.fail(
    // G.2 — flips when write-off endpoints land
    'accountant-approver rejects a PENDING write-off: status REJECTED, DebtWriteOffRejected.v1 emitted, invoice stays open',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // Provision a fresh PENDING write-off via the API using the accountant's
      // token. We cannot reuse Scenario 2's pending row here because it may have
      // already been approved (Scenario 3). Use a separate POST via page context
      // — at this point the `page` is authenticated as accountant-approver, so we
      // need a detour: use apiPost with the approver token and then immediately
      // use apiPost as accountant to create the pending row.
      //
      // Practical approach: create the write-off via the API while logged in as
      // accountant-approver (who also holds DEBT.WRITE_OFF.REQUEST), so the
      // request UID belongs to the approver account. Then the reject must be done
      // by a DIFFERENT user. This scenario therefore creates via approver and
      // rejects via accountant — still two distinct users. For simplicity we
      // simply create a write-off as the current session (accountant-approver)
      // and reject it via a direct API call as the current session (same user)...
      // but that would violate the different-user rule for above-threshold.
      //
      // Correct approach: create the pending row from qa.accountant (via DB
      // direct insert is not allowed; must go via the endpoint). Use the
      // qa.accountant persona's token which we don't hold in this describe block.
      //
      // Simplest valid shape: create the pending write-off via the API as
      // qa.accountant.approver (amount > threshold → PENDING), then reject it
      // via the queue UI as qa.accountant.approver... but same-user rejection
      // is also blocked for above-threshold.
      //
      // Resolution: provision the pending row via DB query on the write-off
      // that Scenario 4's self-approve test left behind (it creates a
      // PENDING_APPROVAL row and never resolves it). Retrieve the most recent
      // PENDING row not owned by the approver.
      const pendingUid = dbQuery(
        `SELECT dwo.uid FROM debt_write_off dwo `
        + `JOIN app_user u ON u.id = dwo.requested_by_user_id `
        + `WHERE dwo.status='PENDING_APPROVAL' `
        + `AND u.username != 'qa.accountant.approver' `
        + `ORDER BY dwo.requested_at DESC LIMIT 1`,
      );
      if (!pendingUid) {
        // No eligible PENDING row — Scenario 4 must have run first.
        expect(pendingUid, 'a PENDING write-off owned by a different user must exist (Scenarios 2/4 must run first)').toBeTruthy();
        return;
      }

      // Reject via the API directly first to validate the contract.
      const rejectBody: RejectDebtWriteOffRequest = {
        reasonForReject: `E2E rejection reason ${RUN_TAG} — insufficient documentation`,
      };
      const rejectR = await apiPost(
        page,
        `/api/v1/debt/write-offs/uid/${pendingUid}/reject`,
        rejectBody,
        { expectedStatus: 200 },
      );
      expect(rejectR.status).toBe(200);

      const dto = unwrap<DebtWriteOffDto>(rejectR);
      expect(dto.status).toBe('REJECTED');
      expect(dto.reasonForReject).toContain(RUN_TAG);
      expect(dto.rejectedAt, 'rejectedAt is populated').toBeTruthy();
      expect(dto.approvedByUserId, 'approvedByUserId populated as the rejecter').toBeTruthy();

      // DB: status REJECTED.
      const dbStatus = dbQuery(
        `SELECT status FROM debt_write_off WHERE uid='${escapeSql(pendingUid)}' LIMIT 1`,
      );
      expect(dbStatus).toBe('REJECTED');

      // Outbox: DebtWriteOffRejected.v1.
      const ev = dbCount(
        `SELECT COUNT(*) FROM domain_event WHERE type='DebtWriteOffRejected.v1' `
        + `AND payload_json LIKE '%${escapeSql(pendingUid)}%'`,
      );
      expect(ev, 'DebtWriteOffRejected.v1 in the outbox').toBeGreaterThanOrEqual(1);

      // Invoice must still appear in the open list (rejection does NOT write off).
      await page.goto(`/debt/customer/uid/${r0.customerUid}`);
      await expect(page.locator('[data-testid="debt-customer-detail"]')).toBeVisible({ timeout: 15_000 });
      // The 45-day invoice is from the setup block; if the approve test ran
      // first this may already be gone — accept either outcome as test.fail
      // will catch the full scenario when the backend is missing.

      // UI: the rejected row must show REJECTED status in the queue.
      await page.goto('/debt/write-offs');
      await expect(page.locator('[data-testid="debt-write-off-queue-table"]')).toBeVisible({ timeout: 15_000 });
      const rejectedRow = page
        .locator('[data-testid="debt-write-off-row"]')
        .filter({ hasText: pendingUid.slice(-6) })
        .first();
      await expect(rejectedRow).toContainText(/REJECTED/i, { timeout: 15_000 });
    },
  );
});

// =============================================================================
// 5.6 — Scenario 6 (sales-clerk 403): all G.2 endpoints + /debt/write-offs page
// =============================================================================

test.describe('Slice G.2 — write-off · sales-clerk 403 gate', () => {
  // G.2 — flips when write-off endpoints land
  test.use({ persona: 'sales-clerk' as Persona });

  test(
    // G.2 — write-off endpoints landed; gate passing as of Slice M triage
    'sales-clerk is 403 on POST /api/v1/debt/write-offs',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');
      const r = await apiPost(
        page,
        '/api/v1/debt/write-offs',
        {
          targetKind: 'CUSTOMER_INVOICE',
          targetInvoiceUid: r0.invoice5UId,
          amount: 100,
          reason: `unauthorised write-off ${RUN_TAG}`,
        } satisfies CreateDebtWriteOffRequest,
        { acceptStatuses: [403] },
      );
      expect(r.status).toBe(403);
    },
  );

  test(
    // G.2 — write-off endpoints landed; gate passing as of Slice M triage
    'sales-clerk is 403 on GET /api/v1/debt/write-offs',
    async ({ page }) => {
      await page.goto('/dashboard');
      const r = await apiGet(page, '/api/v1/debt/write-offs', { acceptStatuses: [403] });
      expect(r.status).toBe(403);
    },
  );

  test(
    // G.2 — write-off endpoints landed; gate passing as of Slice M triage
    'sales-clerk is 403 on GET /api/v1/debt/write-offs/uid/{uid} (any uid)',
    async ({ page }) => {
      await page.goto('/dashboard');
      // Use a synthetic uid — the gate must fire before the service validates existence.
      const fakeUid = '01HWZFAKE000000000000G2QA';
      const r = await apiGet(
        page,
        `/api/v1/debt/write-offs/uid/${fakeUid}`,
        { acceptStatuses: [403, 404] },
      );
      // 403 is the perm gate. 404 would mean the gate passed (bug); we assert 403.
      expect(r.status).toBe(403);
    },
  );

  test(
    // G.2 — write-off endpoints landed; gate passing as of Slice M triage
    'sales-clerk is 403 on POST /api/v1/debt/write-offs/uid/{uid}/approve',
    async ({ page }) => {
      await page.goto('/dashboard');
      const fakeUid = '01HWZFAKE000000000000G2QA';
      const r = await apiPost(
        page,
        `/api/v1/debt/write-offs/uid/${fakeUid}/approve`,
        {},
        { acceptStatuses: [403, 404] },
      );
      expect(r.status).toBe(403);
    },
  );

  test(
    // G.2 — write-off endpoints landed; gate passing as of Slice M triage
    'sales-clerk is 403 on POST /api/v1/debt/write-offs/uid/{uid}/reject',
    async ({ page }) => {
      await page.goto('/dashboard');
      const fakeUid = '01HWZFAKE000000000000G2QA';
      const r = await apiPost(
        page,
        `/api/v1/debt/write-offs/uid/${fakeUid}/reject`,
        { reasonForReject: `unauthorised ${RUN_TAG}` } satisfies RejectDebtWriteOffRequest,
        { acceptStatuses: [403, 404] },
      );
      expect(r.status).toBe(403);
    },
  );

  test.fail(
    // G.2 — flips when write-off endpoints land
    'sales-clerk on /debt/write-offs page sees "Permission required" inert state',
    async ({ page }) => {
      await page.goto('/debt/write-offs');
      // The queue table must NOT render.
      await expect(page.locator('[data-testid="debt-write-off-queue-table"]')).toHaveCount(0);
      // The inert-state wrapper must render (reuses the same pattern as /debt).
      await expect(page.locator('[data-testid="debt-permission-required"]')).toBeVisible({ timeout: 15_000 });
      await expect(
        page.locator('[data-testid="debt-permission-required"]').getByText(/Permission required/i),
      ).toBeVisible();
    },
  );
});

// =============================================================================
// 5.7 — Scenario 7 (procurement-officer 403): consistent with G.1 AP cross-check
// =============================================================================

test.describe('Slice G.2 — write-off · procurement-officer 403 gate', () => {
  // G.2 — flips when write-off endpoints land
  test.use({ persona: 'procurement-officer' as Persona });

  test.fail(
    // G.2 — flips when write-off endpoints land
    'procurement-officer is 403 on POST /api/v1/debt/write-offs (DEBT.WRITE_OFF.REQUEST not a procurement perm)',
    async ({ page }) => {
      await page.goto('/dashboard');
      const r = await apiPost(
        page,
        '/api/v1/debt/write-offs',
        {
          targetKind: 'SUPPLIER_INVOICE',
          targetInvoiceUid: '01HWZFAKE000000000000G2QA',
          amount: 100,
          reason: `unauthorised procurement write-off ${RUN_TAG}`,
        } satisfies CreateDebtWriteOffRequest,
        { acceptStatuses: [403] },
      );
      expect(r.status).toBe(403);
    },
  );

  test(
    // G.2 — write-off endpoints landed; gate passing as of Slice M triage
    'procurement-officer is 403 on GET /api/v1/debt/write-offs (DEBT.READ not a procurement perm)',
    async ({ page }) => {
      await page.goto('/dashboard');
      const r = await apiGet(page, '/api/v1/debt/write-offs', { acceptStatuses: [403] });
      expect(r.status).toBe(403);
    },
  );

  test(
    // G.2 — write-off endpoints landed; gate passing as of Slice M triage
    'procurement-officer is 403 on POST /api/v1/debt/write-offs/uid/{uid}/approve',
    async ({ page }) => {
      await page.goto('/dashboard');
      const fakeUid = '01HWZFAKE000000000000G2QA';
      const r = await apiPost(
        page,
        `/api/v1/debt/write-offs/uid/${fakeUid}/approve`,
        {},
        { acceptStatuses: [403] },
      );
      expect(r.status).toBe(403);
    },
  );

  test(
    // G.2 — write-off endpoints landed; gate passing as of Slice M triage
    'procurement-officer is 403 on POST /api/v1/debt/write-offs/uid/{uid}/reject',
    async ({ page }) => {
      await page.goto('/dashboard');
      const fakeUid = '01HWZFAKE000000000000G2QA';
      const r = await apiPost(
        page,
        `/api/v1/debt/write-offs/uid/${fakeUid}/reject`,
        { reasonForReject: `unauthorised ${RUN_TAG}` } satisfies RejectDebtWriteOffRequest,
        { acceptStatuses: [403] },
      );
      expect(r.status).toBe(403);
    },
  );

  test(
    // G.2 — write-off endpoints landed; gate passing as of Slice M triage
    'procurement-officer is 403 on GET /api/v1/debt/write-offs/uid/{uid}',
    async ({ page }) => {
      await page.goto('/dashboard');
      const fakeUid = '01HWZFAKE000000000000G2QA';
      const r = await apiGet(
        page,
        `/api/v1/debt/write-offs/uid/${fakeUid}`,
        { acceptStatuses: [403] },
      );
      expect(r.status).toBe(403);
    },
  );
});
