import { execFileSync } from 'node:child_process';
import { type Page } from '@playwright/test';
import { test, expect } from './personas.fixture';
import type { Persona } from './test-users';
import AxeBuilder from '@axe-core/playwright';

/**
 * End-to-end Sales spec (Slice C release gate, TDD-first).
 *
 * Drives the Sales-invoice + Sales-receipt lifecycles through the real
 * Angular UI against the QA-parity container at http://localhost:8081/.
 * Multiple assertions in this file are intentionally tagged with
 * `test.fail(...)` — Slice C is shipped backend-incomplete; the gaps get
 * closed in the engineering tasks listed in
 * `docs/design/slice-c-sales-plan.md` and the failing tests turn green
 * one by one. When all `test.fail` matches drop to zero, Slice C is done.
 *
 * Sequencing matters — Playwright runs describe blocks in file order with
 * `fullyParallel: false` + `workers: 1`. We provision per-run reference
 * data (customer, item, price-list) in the very first `setup` block; the
 * lifecycle tests reuse those ids. DB-probe assertions go via
 * `docker exec orbix mariadb -Nse`, matching the procurement.spec.ts
 * pattern.
 *
 * Backend gaps captured here (must close to flip the failures):
 *   1. `SALES_INVOICE.OVERRIDE_CREDIT` permission isn't seeded yet, and
 *      `checkCreditLimit` has no override branch (today it just throws).
 *   2. Posted-invoice void does write compensating stock moves but is gated
 *      to same-day non-batch and the event payload contract isn't pinned.
 *   3. Reprint endpoints + `SalesInvoiceReprinted.v1` outbox event don't
 *      exist yet (plan §3).
 *   4. `GET /api/v1/sales/reports/ar-summary` endpoint doesn't exist yet
 *      (plan §7) — dashboard tiles still read sample values.
 *   5. New `SALES.REPORT.AR_SUMMARY` permission isn't seeded yet.
 *
 * Parallel QA #2 is extending e2e/test-users.ts to add a
 * `cashier-with-override` persona (holds SALES_INVOICE.OVERRIDE_CREDIT) and
 * to expand the existing `cashier` / `accountant` roles with the sales
 * permissions they need. Tests that reference personas QA #2 has not yet
 * added carry a `// TODO depends on QA#2 persona` comment.
 */

// -----------------------------------------------------------------------------
// Run tag + DB probe helpers
// -----------------------------------------------------------------------------

/**
 * RUN_TAG suffixes every per-run business doc (invoice/receipt numbers,
 * reasons, domain-event payload needles) so reruns don't collide on
 * unique constraints and DB probes scope to this run's rows. It is
 * intentionally regenerated per worker process — domain rows are
 * write-once and per-run.
 *
 * REF_TAG, by contrast, is STABLE across worker restarts because
 * Playwright recycles a worker after a test failure and the reference
 * rows (customer / item / price-list) must be findable by the next
 * worker via DB lookup.
 */
const RUN_TAG = Date.now().toString(36).slice(-5).toUpperCase();
const REF_TAG = 'E2ESLS';
const BRANCH_ID = '1';

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
// the auth fixture. Mirrors procurement.spec.ts.
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
// Axe — same deferrals as procurement.spec.ts
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
  customerLowLimitId: string;     // creditLimit = 10_000 (overflows easily for credit-gate tests)
  customerCashId: string;         // creditLimit = 0    (used for CASH-terms flows)
}

const refs: Partial<RefIds> = {};

function loadRefsFromDb(): void {
  refs.uomId         = dbQuery(`SELECT id FROM uom WHERE code='UN-${REF_TAG}' LIMIT 1`) || refs.uomId;
  refs.itemGroupId   = dbQuery(`SELECT id FROM item_group WHERE code='IG-${REF_TAG}' LIMIT 1`) || refs.itemGroupId;
  refs.vatGroupId    = dbQuery(`SELECT id FROM vat_group WHERE code='V-${REF_TAG}' LIMIT 1`) || refs.vatGroupId;
  refs.itemId        = dbQuery(`SELECT id FROM item WHERE code='IT-${REF_TAG}' LIMIT 1`) || refs.itemId;
  refs.priceListId   = dbQuery(`SELECT id FROM price_list WHERE code='PL-${REF_TAG}' LIMIT 1`) || refs.priceListId;
  refs.customerLowLimitId = dbQuery(
    `SELECT c.party_id FROM customer c JOIN party p ON p.id=c.party_id WHERE p.name='QA Cust Low ${REF_TAG}' LIMIT 1`
  ) || refs.customerLowLimitId;
  refs.customerCashId = dbQuery(
    `SELECT c.party_id FROM customer c JOIN party p ON p.id=c.party_id WHERE p.name='QA Cust Cash ${REF_TAG}' LIMIT 1`
  ) || refs.customerCashId;
}

function requireRefs(): RefIds {
  if (!refs.uomId || !refs.itemGroupId || !refs.vatGroupId || !refs.itemId
      || !refs.priceListId || !refs.customerLowLimitId || !refs.customerCashId) {
    loadRefsFromDb();
  }
  if (!refs.uomId || !refs.itemGroupId || !refs.vatGroupId || !refs.itemId
      || !refs.priceListId || !refs.customerLowLimitId || !refs.customerCashId) {
    throw new Error(
      `Sales refs missing — setup must run first. Got: ${JSON.stringify(refs)}`
    );
  }
  return refs as RefIds;
}

// Money shape for the flows. Item priced well above the low-limit customer's
// credit cap so a single CREDIT line trips the gate; well below for the
// CASH-customer happy path.
const UNIT_PRICE = 5_000;          // TZS per unit
const CASH_QTY = 2;                // total 10_000 → fits under cash flow
const OVER_LIMIT_QTY = 5;          // total 25_000 → trips low-limit (10_000) on CREDIT

// -----------------------------------------------------------------------------
// Inject branch context before every nav
// -----------------------------------------------------------------------------

test.beforeEach(async ({ page }) => {
  await page.addInitScript((branchId: string) => {
    localStorage.setItem('orbix.activeBranchId', branchId);
  }, BRANCH_ID);
});

// -----------------------------------------------------------------------------
// 1. Setup — provision reference rows + open business day
// -----------------------------------------------------------------------------

test.describe('Sales · setup', () => {
  // Setup needs cross-module write perms (CATALOG / PARTY / SETTINGS / DAY)
  // that no real persona is granted by design. Use rootadmin only here, for
  // the bootstrap rows the lifecycle tests then consume.
  test.use({ persona: 'rootadmin' });
  test('provisions reference data and opens today\'s business day', async ({ page }) => {
    await page.goto('/dashboard');

    // Open today's business day at branch 1. Sales-invoice post + receipt
    // post both call `dayGuard.requireOpenDay(branchId)`. Tolerate 4xx in
    // case a day already exists (re-runs on the same volume).
    await apiPost(page, `/api/v1/business-days?branchId=${BRANCH_ID}`,
      { businessDate: TODAY },
      { acceptStatuses: [200, 201, 400, 409] }
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

    // 4) Item — non-batch sellable.
    const existingItem = dbQuery(`SELECT id FROM item WHERE code='IT-${REF_TAG}' LIMIT 1`);
    if (existingItem === '') {
      const itemR = await apiPost(page, '/api/v1/items', {
        code: `IT-${REF_TAG}`,
        name: `Sales test item ${REF_TAG}`,
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

    // 4b) Seed stock so the invoice-post stock-out doesn't drive the balance
    // negative. POS uses a dedicated rcv endpoint — for sales we just need a
    // positive on-hand. Tolerate 4xx in case the rcv endpoint is gated to a
    // different perm shape than rootadmin holds; the post still works
    // (negative balance is allowed today, just noisy).
    await apiPost(page, '/api/v1/stock-moves', {
      itemId: refs.itemId,
      branchId: BRANCH_ID,
      qty: '50',
      unitCost: '2000',
      type: 'IN',
      refType: 'E2ESalesSetup',
      refId: 0,
      reason: `e2e sales seed ${RUN_TAG}`,
      compensating: false,
      batchId: null
    }, { acceptStatuses: [200, 201, 400, 403, 404] });

    // 5) Price list (currency TZS, valid from today). The sales-invoice UI
    // requires a price list on every header.
    const existingPl = dbQuery(`SELECT id FROM price_list WHERE code='PL-${REF_TAG}' LIMIT 1`);
    if (existingPl === '') {
      const plR = await apiPost(page, '/api/v1/price-lists', {
        code: `PL-${REF_TAG}`,
        name: `Sales PL ${REF_TAG}`,
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

    // 6) Customer "low limit" — TZS 10_000 credit limit so the over-limit
    //    test reliably trips the gate with a single 25_000 line.
    const existingCustLow = dbQuery(
      `SELECT c.party_id FROM customer c JOIN party p ON p.id=c.party_id WHERE p.name='QA Cust Low ${REF_TAG}' LIMIT 1`
    );
    if (existingCustLow === '') {
      const cR = await apiPost(page, '/api/v1/customers', {
        partyId: null,
        party: {
          name: `QA Cust Low ${REF_TAG}`,
          legalName: `QA Cust Low ${REF_TAG} Ltd`,
          category: 'BUSINESS',
          tin: `500-500-CRD`,
          vrn: null,
          phone: `+255722900111`,
          email: null,
          physicalAddress: null,
          postalAddress: null,
          countryCode: 'TZ',
          notes: null
        },
        creditLimitAmount: '10000',
        creditTermsDays: 30,
        priceListId: refs.priceListId,
        defaultSalesAgentId: null,
        defaultBranchId: BRANCH_ID,
        taxExempt: false
      });
      refs.customerLowLimitId = String(unwrap<{ partyId: string }>(cR).partyId);
    } else {
      refs.customerLowLimitId = existingCustLow;
    }

    // 7) Customer "cash" — no credit limit (CASH terms only). Used for the
    //    happy-path post + receipt flows so we don't trip the credit gate.
    const existingCustCash = dbQuery(
      `SELECT c.party_id FROM customer c JOIN party p ON p.id=c.party_id WHERE p.name='QA Cust Cash ${REF_TAG}' LIMIT 1`
    );
    if (existingCustCash === '') {
      const cR = await apiPost(page, '/api/v1/customers', {
        partyId: null,
        party: {
          name: `QA Cust Cash ${REF_TAG}`,
          legalName: `QA Cust Cash ${REF_TAG} Ltd`,
          category: 'BUSINESS',
          tin: `500-500-CSH`,
          vrn: null,
          phone: `+255722900222`,
          email: null,
          physicalAddress: null,
          postalAddress: null,
          countryCode: 'TZ',
          notes: null
        },
        creditLimitAmount: '0',
        creditTermsDays: 0,
        priceListId: refs.priceListId,
        defaultSalesAgentId: null,
        defaultBranchId: BRANCH_ID,
        taxExempt: false
      });
      refs.customerCashId = String(unwrap<{ partyId: string }>(cR).partyId);
    } else {
      refs.customerCashId = existingCustCash;
    }

    expect(refs.uomId, 'uomId').toBeTruthy();
    expect(refs.itemGroupId, 'itemGroupId').toBeTruthy();
    expect(refs.vatGroupId, 'vatGroupId').toBeTruthy();
    expect(refs.itemId, 'itemId').toBeTruthy();
    expect(refs.priceListId, 'priceListId').toBeTruthy();
    expect(refs.customerLowLimitId, 'customerLowLimitId').toBeTruthy();
    expect(refs.customerCashId, 'customerCashId').toBeTruthy();
  });
});

// -----------------------------------------------------------------------------
// Shared helpers for invoice + receipt creation
// -----------------------------------------------------------------------------

interface CreateInvoiceResult { uid: string; id: string; number: string; }

async function createInvoiceDraft(
  page: Page,
  numberSuffix: string,
  customerId: string,
  qty: number,
  paymentTerms: 'CASH' | 'CREDIT',
  acceptStatuses?: number[],
): Promise<CreateInvoiceResult> {
  const r0 = requireRefs();
  const number = `INV-${RUN_TAG}-${numberSuffix}`;
  const r = await apiPost(page, '/api/v1/sales-invoices', {
    number,
    branchId: BRANCH_ID,
    customerId,
    salesAgentId: null,
    invoiceDate: TODAY,
    dueDate: TODAY,
    paymentTerms,
    currencyCode: 'TZS',
    priceListId: r0.priceListId,
    discountApproverId: null,
    reference: `e2e ${RUN_TAG}`,
    notes: null,
    lines: [{
      itemId: r0.itemId,
      uomId: r0.uomId,
      qty: String(qty),
      unitPrice: String(UNIT_PRICE),
      discountPct: '0',
      vatGroupId: r0.vatGroupId
    }]
  }, { acceptStatuses: acceptStatuses ?? [200, 201] });
  if (r.status >= 400) {
    return { uid: '', id: '', number };
  }
  const data = unwrap<{ uid: string; id: string; number: string }>(r);
  return { uid: data.uid, id: String(data.id), number: data.number };
}

// -----------------------------------------------------------------------------
// 2. Invoice draft → post (within credit limit / CASH)
// -----------------------------------------------------------------------------

test.describe('Sales · invoice post (within limit)', () => {
  // TODO depends on QA#2 expanding the `cashier` persona to include
  // SALES.MANAGE_INVOICE (the day-1 persona only carries POS perms).
  test.use({ persona: 'cashier' });
  test('CASH invoice posts; stock_move + SalesInvoicePosted.v1 emitted', async ({ page }) => {
    const r0 = requireRefs();
    const inv = await createInvoiceDraft(page, 'POST', r0.customerCashId, CASH_QTY, 'CASH');

    // Drive Post via the UI (sales/invoices detail panel).
    await page.goto('/sales/invoices');
    await expect(page.getByRole('heading', { name: /^Sales invoices$/ })).toBeVisible({ timeout: 20_000 });

    const invRow = page.locator('button.inv-row').filter({ hasText: inv.number }).first();
    await expect(invRow).toBeVisible({ timeout: 15_000 });
    await invRow.click();

    const postBtn = page.locator('button.btn-primary.btn-sm').filter({ hasText: /Post/i }).first();
    await expect(postBtn).toBeVisible({ timeout: 10_000 });
    await postBtn.click();
    await expect(page.getByText(/Invoice posted\./)).toBeVisible({ timeout: 15_000 });

    // DB pins.
    expect(dbQuery(`SELECT status FROM sales_invoice WHERE id=${inv.id}`)).toBe('POSTED');
    const move = dbCount(
      `SELECT COUNT(*) FROM stock_move WHERE ref_type='SalesInvoice' AND ref_id=${inv.id} AND item_id=${r0.itemId} AND qty < 0`
    );
    expect(move, 'outbound stock_move from invoice post').toBeGreaterThanOrEqual(1);
    const posted = dbCount(
      `SELECT COUNT(*) FROM domain_event WHERE type='SalesInvoicePosted.v1' ` +
      `AND aggregate_id='${inv.id}'`
    );
    expect(posted, 'SalesInvoicePosted.v1 in the outbox').toBeGreaterThanOrEqual(1);

    await dismissDismissableAlerts(page);
    await assertNoSeriousA11yViolations(page, '/sales/invoices');
  });
});

// -----------------------------------------------------------------------------
// 3. Credit-limit gate blocks over-limit post (WILL FAIL today — message
//    contract not pinned + no override path)
// -----------------------------------------------------------------------------

test.describe('Sales · credit-limit gate blocks over-limit', () => {
  // TODO depends on QA#2 expanding the `cashier` persona to include
  // SALES.MANAGE_INVOICE. The cashier here is intentionally one WITHOUT
  // SALES_INVOICE.OVERRIDE_CREDIT — the gate must reject them.
  test.use({ persona: 'cashier' });
  test.fail();
  test('CREDIT invoice over the customer limit fails to post with a credit-limit message', async ({ page }) => {
    const r0 = requireRefs();
    // The customer's credit limit is 10_000; this invoice grosses ~25_000.
    // Service currently throws at draft creation already (checkCreditLimit
    // runs in createDraft) — accept either a 400 on draft or a draft that
    // posts-rejects. The contract the slice should land is: draft is
    // allowed (stays DRAFT) and POST is what trips the gate.
    const number = `INV-${RUN_TAG}-OVL`;
    const draftR = await apiPost(page, '/api/v1/sales-invoices', {
      number,
      branchId: BRANCH_ID,
      customerId: r0.customerLowLimitId,
      salesAgentId: null,
      invoiceDate: TODAY,
      dueDate: TODAY,
      paymentTerms: 'CREDIT',
      currencyCode: 'TZS',
      priceListId: r0.priceListId,
      discountApproverId: null,
      reference: null,
      notes: null,
      lines: [{
        itemId: r0.itemId,
        uomId: r0.uomId,
        qty: String(OVER_LIMIT_QTY),
        unitPrice: String(UNIT_PRICE),
        discountPct: '0',
        vatGroupId: r0.vatGroupId
      }]
    }, { acceptStatuses: [200, 201, 400] });

    // Slice C contract: draft creation succeeds — the gate fires on POST.
    expect(draftR.status, 'draft creation under Slice C should not block on credit').toBeLessThan(400);
    const draft = unwrap<{ uid: string; id: string }>(draftR);
    const invId = String(draft.id);

    // Post must be rejected; invoice stays DRAFT; error mentions credit limit.
    const r = await apiPost(page, `/api/v1/sales-invoices/uid/${draft.uid}/post`, {}, {
      acceptStatuses: [400, 403, 409],
    });
    expect([400, 403, 409]).toContain(r.status);
    expect(r.body, 'error mentions credit limit').toMatch(/credit limit/i);

    const status = dbQuery(`SELECT status FROM sales_invoice WHERE id=${invId}`);
    expect(status, 'invoice stays DRAFT after the gate').toBe('DRAFT');
  });
});

// -----------------------------------------------------------------------------
// 4. Credit-limit override succeeds with permission (WILL FAIL today — perm
//    + override branch don't exist in the backend yet)
// -----------------------------------------------------------------------------

test.describe('Sales · credit-limit override with permission', () => {
  // TODO depends on QA#2 adding the `cashier-with-override` persona (with
  // SALES.MANAGE_INVOICE + the new SALES_INVOICE.OVERRIDE_CREDIT perm)
  // *and* on the backend seeding SALES_INVOICE.OVERRIDE_CREDIT (plan §6).
  // Until both land, this test fails at the persona-login layer.
  test.use({ persona: 'cashier-with-override' as Persona });
  test.fail();
  test('user with SALES_INVOICE.OVERRIDE_CREDIT posts the over-limit invoice with a reason', async ({ page }) => {
    const r0 = requireRefs();
    const number = `INV-${RUN_TAG}-OVR`;
    const draftR = await apiPost(page, '/api/v1/sales-invoices', {
      number,
      branchId: BRANCH_ID,
      customerId: r0.customerLowLimitId,
      salesAgentId: null,
      invoiceDate: TODAY,
      dueDate: TODAY,
      paymentTerms: 'CREDIT',
      currencyCode: 'TZS',
      priceListId: r0.priceListId,
      discountApproverId: null,
      reference: null,
      notes: null,
      lines: [{
        itemId: r0.itemId,
        uomId: r0.uomId,
        qty: String(OVER_LIMIT_QTY),
        unitPrice: String(UNIT_PRICE),
        discountPct: '0',
        vatGroupId: r0.vatGroupId
      }]
    });
    const draft = unwrap<{ uid: string; id: string }>(draftR);
    const invId = String(draft.id);

    // Slice C contract: the post endpoint accepts a {overrideReason} body
    // when the caller holds SALES_INVOICE.OVERRIDE_CREDIT.
    const overrideReason = `Manager override ${RUN_TAG}`;
    const r = await apiPost(page, `/api/v1/sales-invoices/uid/${draft.uid}/post`,
      { overrideReason },
      { expectedStatus: 200 }
    );
    const posted = unwrap<{ status: string }>(r);
    expect(posted.status).toBe('POSTED');

    expect(dbQuery(`SELECT status FROM sales_invoice WHERE id=${invId}`)).toBe('POSTED');
    const ev = dbCount(
      `SELECT COUNT(*) FROM domain_event WHERE type='SalesInvoicePosted.v1' ` +
      `AND aggregate_id='${invId}' ` +
      `AND payload_json LIKE '%"creditOverride":true%' ` +
      `AND payload_json LIKE '%${escapeSql(overrideReason)}%'`
    );
    expect(ev, 'SalesInvoicePosted.v1 with creditOverride=true + reason').toBeGreaterThanOrEqual(1);
  });
});

// -----------------------------------------------------------------------------
// 5. Receipt + allocation against the posted CASH invoice
// -----------------------------------------------------------------------------

test.describe('Sales · receipt allocation', () => {
  // TODO depends on QA#2 expanding the `cashier` persona to include
  // SALES.MANAGE_INVOICE + SALES.MANAGE_RECEIPT.
  test.use({ persona: 'cashier' });
  test('receipt posts, allocates against the invoice, cash_entry + SalesReceiptPosted.v1 land', async ({ page }) => {
    const r0 = requireRefs();
    // Provision a fresh CASH-customer invoice + post via API (the UI post
    // flow is exercised in test 2; this test focuses on the receipt half).
    const inv = await createInvoiceDraft(page, 'RCT', r0.customerCashId, CASH_QTY, 'CASH');
    await apiPost(page, `/api/v1/sales-invoices/uid/${inv.uid}/post`, {}, { expectedStatus: 200 });

    const invoiceTotal = Number(dbQuery(`SELECT total_amount FROM sales_invoice WHERE id=${inv.id}`));
    expect(invoiceTotal, 'invoice total readable').toBeGreaterThan(0);

    // Create draft receipt allocating the full invoice total.
    const receiptNumber = `RCT-${RUN_TAG}-A`;
    const draftR = await apiPost(page, '/api/v1/sales-receipts', {
      number: receiptNumber,
      branchId: BRANCH_ID,
      customerId: r0.customerCashId,
      receiptDate: TODAY,
      method: 'CASH',
      reference: `e2e ${RUN_TAG}`,
      currencyCode: 'TZS',
      totalAmount: String(invoiceTotal),
      notes: null,
      allocations: [{ salesInvoiceId: inv.id, amount: String(invoiceTotal) }]
    });
    const draft = unwrap<{ uid: string; id: string }>(draftR);
    const rcptId = String(draft.id);

    // Drive Post via the UI.
    await page.goto('/sales/receipts');
    await expect(page.getByRole('heading', { name: /^Sales receipts$/ })).toBeVisible({ timeout: 20_000 });
    const rcRow = page.locator('button.rc-row').filter({ hasText: receiptNumber }).first();
    await expect(rcRow).toBeVisible({ timeout: 15_000 });
    await rcRow.click();
    const postBtn = page.locator('button.btn-primary.btn-sm').filter({ hasText: /Post/i }).first();
    await expect(postBtn).toBeVisible({ timeout: 10_000 });
    await postBtn.click();
    await expect(page.getByText(/Receipt posted\.|posted/i)).toBeVisible({ timeout: 15_000 });

    // DB pins: receipt POSTED, allocation row exists, cash_entry landed,
    // outbox SalesReceiptPosted.v1 emitted, invoice flips to PAID.
    expect(dbQuery(`SELECT status FROM sales_receipt WHERE id=${rcptId}`)).toBe('POSTED');
    const alloc = dbCount(
      `SELECT COUNT(*) FROM receipt_allocation WHERE sales_receipt_id=${rcptId} AND sales_invoice_id=${inv.id}`
    );
    expect(alloc, 'receipt_allocation row links to the invoice').toBeGreaterThanOrEqual(1);

    const cash = dbCount(
      `SELECT COUNT(*) FROM cash_entry WHERE ref_type='SALES_RECEIPT' AND ref_id=${rcptId} AND direction='IN'`
    );
    expect(cash, 'cash_entry IN row for CASH receipt').toBeGreaterThanOrEqual(1);

    const ev = dbCount(
      `SELECT COUNT(*) FROM domain_event WHERE type='SalesReceiptPosted.v1' ` +
      `AND aggregate_id='${rcptId}'`
    );
    expect(ev, 'SalesReceiptPosted.v1 in the outbox').toBeGreaterThanOrEqual(1);

    const invStatus = dbQuery(`SELECT status FROM sales_invoice WHERE id=${inv.id}`);
    expect(['PAID', 'PARTIALLY_PAID']).toContain(invStatus);
  });
});

// -----------------------------------------------------------------------------
// 6. Invoice void with compensating entries (WILL FAIL today — event payload
//    contract not pinned to {compensating: true})
// -----------------------------------------------------------------------------

test.describe('Sales · invoice void with compensating entries', () => {
  // TODO depends on QA#2 adding SALES.MANAGE_INVOICE to the supervisor
  // persona (the day-1 supervisor only carries procurement perms).
  test.use({ persona: 'supervisor' });
  test.fail();
  test('voiding a posted invoice writes a compensating stock_move + emits SalesInvoiceVoided.v1 with compensating=true', async ({ page }) => {
    const r0 = requireRefs();
    // Fresh CASH invoice, posted via API (no receipt — void requires paidAmount=0).
    const inv = await createInvoiceDraft(page, 'VOID', r0.customerCashId, CASH_QTY, 'CASH');
    await apiPost(page, `/api/v1/sales-invoices/uid/${inv.uid}/post`, {}, { expectedStatus: 200 });
    expect(dbQuery(`SELECT status FROM sales_invoice WHERE id=${inv.id}`)).toBe('POSTED');

    const reason = `Customer cancelled order ${RUN_TAG}`;
    const r = await apiPost(page, `/api/v1/sales-invoices/uid/${inv.uid}/void`, { reason }, {
      expectedStatus: 200,
    });
    const voided = unwrap<{ status: string }>(r);
    expect(voided.status).toBe('VOIDED');

    // Compensating stock_move: same item + branch, positive qty (inbound).
    const compensating = dbCount(
      `SELECT COUNT(*) FROM stock_move WHERE ref_type='SalesInvoiceVoid' AND ref_id=${inv.id} ` +
      `AND item_id=${r0.itemId} AND qty > 0`
    );
    expect(compensating, 'compensating stock_move row').toBeGreaterThanOrEqual(1);

    expect(dbQuery(`SELECT status FROM sales_invoice WHERE id=${inv.id}`)).toBe('VOIDED');

    // Slice C contract: the event payload pins compensating=true so debt-side
    // subscribers can recognise the reversal without re-reading the invoice.
    const ev = dbCount(
      `SELECT COUNT(*) FROM domain_event WHERE type='SalesInvoiceVoided.v1' ` +
      `AND aggregate_id='${inv.id}' ` +
      `AND payload_json LIKE '%"compensating":true%' ` +
      `AND payload_json LIKE '%${escapeSql(reason)}%'`
    );
    expect(ev, 'SalesInvoiceVoided.v1 with compensating=true + reason').toBeGreaterThanOrEqual(1);
  });
});

// -----------------------------------------------------------------------------
// 7. Reprint audit (WILL FAIL today — endpoint + event don't exist)
// -----------------------------------------------------------------------------

test.describe('Sales · invoice reprint audit', () => {
  // TODO depends on QA#2 expanding the `cashier` persona to include
  // SALES.MANAGE_INVOICE + the new SALES_INVOICE.REPRINT perm (plan §2).
  test.use({ persona: 'cashier' });
  test.fail();
  test('reprinting a posted invoice writes SalesInvoiceReprinted.v1 with the chosen reason enum', async ({ page }) => {
    const r0 = requireRefs();
    const inv = await createInvoiceDraft(page, 'RPR', r0.customerCashId, CASH_QTY, 'CASH');
    await apiPost(page, `/api/v1/sales-invoices/uid/${inv.uid}/post`, {}, { expectedStatus: 200 });

    // Slice C contract: POST /sales-invoices/uid/{uid}/reprint with the
    // reprint-reason enum (DUPLICATE | REISSUE_TO_CUSTOMER | INTERNAL_FILE |
    // OTHER) and optional notes — see PM plan §1.
    const notes = `Customer lost original ${RUN_TAG}`;
    const r = await apiPost(page, `/api/v1/sales-invoices/uid/${inv.uid}/reprint`,
      { reason: 'DUPLICATE', notes },
      { acceptStatuses: [200, 204] }
    );
    expect([200, 204]).toContain(r.status);

    // Outbox event carries the enum + notes.
    const ev = dbCount(
      `SELECT COUNT(*) FROM domain_event WHERE type='SalesInvoiceReprinted.v1' ` +
      `AND aggregate_id='${inv.id}' ` +
      `AND payload_json LIKE '%"reason":"DUPLICATE"%' ` +
      `AND payload_json LIKE '%${escapeSql(notes)}%'`
    );
    expect(ev, 'SalesInvoiceReprinted.v1 with reason=DUPLICATE + notes').toBeGreaterThanOrEqual(1);

    // Reprint count column should increment. The plan §3 emits a per-reprint
    // event; the entity is expected to carry a `reprint_count` (verified
    // against the migration when the backend lands). The assertion below
    // pins that column name; if backend chooses a different name (e.g.
    // `reprint_total`) this test needs the spec touch-up — file a backend
    // gap if so.
    const count = Number(dbQuery(`SELECT COALESCE(reprint_count, 0) FROM sales_invoice WHERE id=${inv.id}`));
    expect(count, 'reprint_count >= 1 after first reprint').toBeGreaterThanOrEqual(1);
  });
});

// -----------------------------------------------------------------------------
// 8. AR-summary endpoint feeds the dashboard tiles (WILL FAIL today —
//    endpoint doesn't exist yet)
// -----------------------------------------------------------------------------

test.describe('Sales · AR-summary endpoint', () => {
  // TODO depends on QA#2 expanding the `accountant` persona to include the
  // new SALES.REPORT.AR_SUMMARY perm + SALES_INVOICE.READ (plan §2).
  test.use({ persona: 'accountant' });
  test.fail();
  test('GET /api/v1/sales/reports/ar-summary returns aggregate counts + outstanding', async ({ page }) => {
    const r = await apiGet(page, `/api/v1/sales/reports/ar-summary?branchId=${BRANCH_ID}`, {
      acceptStatuses: [200],
    });
    expect(r.status).toBe(200);
    const summary = unwrap<{
      openCount?: number;
      outstanding?: number;
      overdueCount?: number;
      currencyCode?: string;
      // Tolerate the alternative key names the design doc floated; pin to
      // the contract in the test body (the spec's failure message will name
      // the keys we actually expect).
      arOutstanding?: number;
      openInvoices?: number;
      overdueInvoices?: number;
    }>(r);

    // Slice C contract (plan §7): { openCount, outstanding, overdueCount, currencyCode }.
    expect(summary.openCount ?? summary.openInvoices, 'openCount').toEqual(expect.any(Number));
    expect(summary.outstanding ?? summary.arOutstanding, 'outstanding').toEqual(expect.any(Number));
    expect(summary.overdueCount ?? summary.overdueInvoices, 'overdueCount').toEqual(expect.any(Number));
    expect(summary.currencyCode ?? 'TZS', 'currencyCode default').toMatch(/^[A-Z]{3}$/);

    // Cross-check the AR aggregate against the DB:
    const dbOpen = dbCount(
      `SELECT COUNT(*) FROM sales_invoice WHERE branch_id=${BRANCH_ID} ` +
      `AND status IN ('POSTED','PARTIALLY_PAID')`
    );
    const reported = (summary.openCount ?? summary.openInvoices ?? -1) as number;
    expect(reported, 'reported openCount matches DB').toBe(dbOpen);
  });
});

// -----------------------------------------------------------------------------
// 9. AR-summary permission denial (WILL FAIL today — endpoint doesn't exist)
// -----------------------------------------------------------------------------

test.describe('Sales · AR-summary permission denial', () => {
  // The day-1 `cashier` persona has POS-only perms and explicitly should
  // NOT carry the new SALES.REPORT.AR_SUMMARY perm — this test pins that
  // gate. QA#2 should NOT grant SALES.REPORT.AR_SUMMARY to `cashier`.
  test.use({ persona: 'cashier' });
  test.fail();
  test('cashier without SALES.REPORT.AR_SUMMARY gets 403 from ar-summary', async ({ page }) => {
    const r = await apiGet(page, `/api/v1/sales/reports/ar-summary?branchId=${BRANCH_ID}`, {
      acceptStatuses: [403],
    });
    expect(r.status).toBe(403);
  });
});

// -----------------------------------------------------------------------------
// 10. A11y sweep — sales landing + invoices + receipts + dashboard (AR tiles)
// -----------------------------------------------------------------------------

test.describe('Sales · a11y sweep', () => {
  // TODO depends on QA#2 expanding the `cashier` persona to include
  // SALES.MANAGE_INVOICE + SALES.MANAGE_RECEIPT so the pages render
  // populated. Today they may render an error envelope, which is still
  // a valid axe surface — but the listing render is what the slice ships.
  test.use({ persona: 'cashier' });

  test('sales landing has no serious axe violations', async ({ page }) => {
    await page.goto('/sales');
    await expect(page.getByRole('heading', { name: /^Sales$/ })).toBeVisible({ timeout: 20_000 });
    await assertNoSeriousA11yViolations(page, '/sales');
  });

  test('sales invoices page has no serious axe violations', async ({ page }) => {
    await page.goto('/sales/invoices');
    await expect(page.getByRole('heading', { name: /^Sales invoices$/ })).toBeVisible({ timeout: 20_000 });
    await dismissDismissableAlerts(page);
    await assertNoSeriousA11yViolations(page, '/sales/invoices');
  });

  test('sales receipts page has no serious axe violations', async ({ page }) => {
    await page.goto('/sales/receipts');
    await expect(page.getByRole('heading', { name: /^Sales receipts$/ })).toBeVisible({ timeout: 20_000 });
    await dismissDismissableAlerts(page);
    await assertNoSeriousA11yViolations(page, '/sales/receipts');
  });

  test('dashboard (AR tiles) has no serious axe violations', async ({ page }) => {
    await page.goto('/dashboard');
    // h1 is greeting text — match the persistent dashboard structure via the
    // generic root rather than a specific string.
    await expect(page.locator('h1')).toBeVisible({ timeout: 20_000 });
    await assertNoSeriousA11yViolations(page, '/dashboard');
  });
});
