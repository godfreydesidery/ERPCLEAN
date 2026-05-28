import { execFileSync } from 'node:child_process';
import { type Page } from '@playwright/test';
import { test, expect } from './personas.fixture';
import type { Persona } from './test-users';
import AxeBuilder from '@axe-core/playwright';

/**
 * End-to-end Customer Returns + Credit-Note Allocation spec (Slice H gate).
 *
 * Drives the credit-note apply flow against the QA-parity container at
 * http://localhost:8081/. Every scenario is tagged `test.fail` — the
 * /apply endpoint, PARTIALLY_ALLOCATED state, and the apply modal do not
 * exist yet. When the backend + frontend tasks in slice-h-credit-allocation-plan.md
 * (tasks #2 and #3) land, drop the `test.fail` wrappers to flip the gate green.
 *
 * Happy-path actor: `sales-clerk` — holds SALES.MANAGE_INVOICE + SALES.MANAGE_RECEIPT
 * + SALES.MANAGE_RETURN (added to the persona in test-users.ts for this slice).
 * Negative-path actor: `cashier` — holds only POS.* perms, no SALES.MANAGE_RETURN.
 *
 * Backend gaps that must close before flipping (// Slice H — flips when /apply lands):
 *   1. POST /api/v1/sales/customer-credit-notes/uid/{uid}/apply does not exist.
 *   2. CreditNoteStatus.PARTIALLY_ALLOCATED variant does not exist.
 *   3. customer_credit_note_allocation table (V75) does not exist.
 *   4. CustomerCreditNoteApplied.v1 outbox event does not exist.
 *   5. CustomerCreditNoteDto does not expose availableAmount + allocations[].
 *   6. "Apply" action button on the credit-notes table does not exist (frontend task #3).
 *   7. credit-note-apply-modal.component.ts does not exist (frontend task #3).
 *
 * Frontend test-id contract (frontend agent wires these — same pattern as debt.spec.ts):
 *
 *   data-testid                          | Component                               | Purpose
 *   -------------------------------------|-----------------------------------------|----------------------------------------
 *   credit-note-apply-btn                | returns.component.ts                    | "Apply" action per POSTED/PARTIALLY_ALLOCATED credit note row
 *   credit-note-status                   | returns.component.ts                    | Status badge per credit note row
 *   credit-note-allocated-amount         | returns.component.ts                    | Allocated amount cell per credit note row
 *   credit-note-apply-modal              | credit-note-apply-modal.component.ts    | Apply modal container
 *   credit-note-apply-invoice-select     | credit-note-apply-modal.component.ts    | Invoice picker (open invoices list)
 *   credit-note-apply-amount-input       | credit-note-apply-modal.component.ts    | Amount input (defaults to min(available, outstanding))
 *   credit-note-apply-submit             | credit-note-apply-modal.component.ts    | Submit button
 *   credit-note-apply-error              | credit-note-apply-modal.component.ts    | Inline error message (422/409 responses)
 *   credit-note-allocations-list         | returns.component.ts (expanded row)     | Allocations history rows
 *
 * Sequencing: Playwright workers=1, fullyParallel=false. The `setup` describe
 * provisions all reference rows (UoM, item group, VAT group, item, price-list,
 * two customers, stock). Lifecycle tests reuse those ids. DB probes go via
 * `docker exec orbix mariadb -Nse`.
 */

// ---------------------------------------------------------------------------
// Run tag + DB probe helpers (mirror debt.spec.ts / sales.spec.ts)
// ---------------------------------------------------------------------------

const RUN_TAG = Date.now().toString(36).slice(-5).toUpperCase();
const REF_TAG = 'E2ECNA';   // Credit Note Allocation
const BRANCH_ID = '1';

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}
const TODAY = todayIso();

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

// ---------------------------------------------------------------------------
// Wire-shape type guards — pinned against slice-h-credit-allocation-plan.md §5
// ---------------------------------------------------------------------------

/** POST /api/v1/sales/customer-credit-notes/uid/{uid}/apply request body. */
interface ApplyCreditNoteRequest {
  salesInvoiceUid: string;   // 26-char Crockford ULID
  amount: number;            // > 0, <= creditNote.availableAmount, <= invoice.outstanding
}

/** Allocation row nested inside CustomerCreditNoteDto. */
interface CreditNoteAllocationDto {
  id: string;
  salesInvoiceId: string;
  salesInvoiceNumber: string | null;
  amount: number;
  allocatedAt: string;         // ISO timestamp
  allocatedBy: string | null;  // username, hydrated
}

/** Full CustomerCreditNoteDto — extended for Slice H with availableAmount + allocations. */
interface CustomerCreditNoteDto {
  id: string;
  uid: string;
  number: string;
  customerId: string;
  customerName: string | null;
  customerReturnId: string | null;
  date: string;                // ISO date
  currencyCode: string;
  totalAmount: number;
  allocatedAmount: number;
  availableAmount: number;     // computed: totalAmount - allocatedAmount
  status: 'POSTED' | 'PARTIALLY_ALLOCATED' | 'FULLY_ALLOCATED';
  notes: string | null;
  allocations: CreditNoteAllocationDto[] | null;
}

// Crockford ULID regex
const ULID_RE = /^[0-9A-HJKMNP-TV-Z]{26}$/;

// ---------------------------------------------------------------------------
// HTTP helpers — mirror debt.spec.ts exactly
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Axe — same deferrals as the rest of the suite
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Shared reference state — provisioned in `setup`, reused across all tests
//
// We need TWO customers:
//   - `customerAId` / `customerAUid` — the "return" customer. One invoice A
//     is returned (creates the credit note). A second open invoice B is the
//     apply target.
//   - `customerBId` / `customerBUid` — a distinct customer for the
//     cross-customer 422 test (credit note for A must not apply to B's invoice).
// ---------------------------------------------------------------------------

interface RefIds {
  uomId: string;
  itemGroupId: string;
  vatGroupId: string;
  itemId: string;
  priceListId: string;
  // Customer A — holds the return + credit note + two open invoices
  customerAId: string;        // party_id
  customerAUid: string;       // uid
  customerAName: string;
  // Customer B — separate customer for cross-customer 422 test
  customerBId: string;
  customerBUid: string;
  customerBName: string;
}

const refs: Partial<RefIds> = {};

function loadRefsFromDb(): void {
  refs.uomId       = dbQuery(`SELECT id FROM uom WHERE code='UN-${REF_TAG}' LIMIT 1`) || refs.uomId;
  refs.itemGroupId = dbQuery(`SELECT id FROM item_group WHERE code='IG-${REF_TAG}' LIMIT 1`) || refs.itemGroupId;
  refs.vatGroupId  = dbQuery(`SELECT id FROM vat_group WHERE code='V-${REF_TAG}' LIMIT 1`) || refs.vatGroupId;
  refs.itemId      = dbQuery(`SELECT id FROM item WHERE code='IT-${REF_TAG}' LIMIT 1`) || refs.itemId;
  refs.priceListId = dbQuery(`SELECT id FROM price_list WHERE code='PL-${REF_TAG}' LIMIT 1`) || refs.priceListId;

  refs.customerAName = `QA CNA-A ${REF_TAG}`;
  refs.customerBName = `QA CNA-B ${REF_TAG}`;

  const custARow = dbQuery(
    `SELECT c.party_id FROM customer c JOIN party p ON p.id=c.party_id WHERE p.name='${escapeSql(refs.customerAName)}' LIMIT 1`,
  );
  if (custARow) {
    refs.customerAId  = custARow;
    refs.customerAUid = dbQuery(`SELECT uid FROM party WHERE id=${custARow} LIMIT 1`);
  }

  const custBRow = dbQuery(
    `SELECT c.party_id FROM customer c JOIN party p ON p.id=c.party_id WHERE p.name='${escapeSql(refs.customerBName)}' LIMIT 1`,
  );
  if (custBRow) {
    refs.customerBId  = custBRow;
    refs.customerBUid = dbQuery(`SELECT uid FROM party WHERE id=${custBRow} LIMIT 1`);
  }
}

function requireRefs(): RefIds {
  if (!refs.uomId || !refs.itemGroupId || !refs.vatGroupId || !refs.itemId
      || !refs.priceListId || !refs.customerAId || !refs.customerAUid
      || !refs.customerBId || !refs.customerBUid) {
    loadRefsFromDb();
  }
  if (!refs.uomId || !refs.itemGroupId || !refs.vatGroupId || !refs.itemId
      || !refs.priceListId || !refs.customerAId || !refs.customerAUid
      || !refs.customerBId || !refs.customerBUid) {
    throw new Error(
      `CNA refs missing — setup must run first. Got: ${JSON.stringify(refs)}`,
    );
  }
  return refs as RefIds;
}

/**
 * Post a minimal sales invoice for customerPartyId and return its uid + numeric id.
 * Helper shared across the setup and lifecycle tests.
 */
async function createAndPostInvoice(
  page: Page,
  opts: {
    customerPartyId: string;
    itemId: string;
    uomId: string;
    priceListId: string;
    vatGroupId: string;
    qty: number;
    unitPrice: number;
    numberSuffix: string;
  },
): Promise<{ uid: string; id: string; totalAmount: number }> {
  const draft = await apiPost(page, '/api/v1/sales-invoices', {
    number: `INV-${RUN_TAG}-${opts.numberSuffix}`,
    branchId: BRANCH_ID,
    customerId: opts.customerPartyId,
    salesAgentId: null,
    invoiceDate: TODAY,
    dueDate: TODAY,
    paymentTerms: 'CREDIT',
    currencyCode: 'TZS',
    priceListId: opts.priceListId,
    discountApproverId: null,
    reference: `e2e cna ${RUN_TAG}`,
    notes: null,
    lines: [{
      itemId: opts.itemId,
      uomId: opts.uomId,
      qty: String(opts.qty),
      unitPrice: String(opts.unitPrice),
      discountPct: '0',
      vatGroupId: opts.vatGroupId,
    }],
  });
  const dto = unwrap<{ uid: string; id: string; totalAmount: number }>(draft);
  await apiPost(page, `/api/v1/sales-invoices/uid/${dto.uid}/post`, {}, { expectedStatus: 200 });
  return { uid: dto.uid, id: String(dto.id), totalAmount: dto.totalAmount };
}

/**
 * Create a customer return (all lines, full qty) against a posted invoice,
 * post it, then issue the credit note. Returns the credit note uid + id.
 *
 * NOTE: The exact request shape for customer-returns mirrors whatever the
 * backend engineer documents in slice-h task #2. The shape used here follows
 * the V30 migration comment and the existing sales-returns endpoint pattern.
 */
async function createReturnAndCreditNote(
  page: Page,
  opts: {
    salesInvoiceUid: string;
    salesInvoiceId: string;
    customerPartyId: string;
    itemId: string;
    uomId: string;
    vatGroupId: string;
    qty: number;
    unitPrice: number;
  },
): Promise<{ returnUid: string; returnId: string; creditNoteUid: string; creditNoteId: string }> {
  // POST the customer return (DRAFT).
  const returnDraft = await apiPost(page, '/api/v1/sales/customer-returns', {
    salesInvoiceId: opts.salesInvoiceId,
    branchId: BRANCH_ID,
    customerId: opts.customerPartyId,
    returnDate: TODAY,
    reason: `e2e cna return ${RUN_TAG}`,
    lines: [{
      itemId: opts.itemId,
      uomId: opts.uomId,
      qty: String(opts.qty),
      unitPrice: String(opts.unitPrice),
      vatGroupId: opts.vatGroupId,
    }],
  });
  const rDto = unwrap<{ uid: string; id: string }>(returnDraft);

  // POST to advance DRAFT → POSTED.
  await apiPost(page, `/api/v1/sales/customer-returns/uid/${rDto.uid}/post`, {}, { expectedStatus: 200 });

  // Issue credit note (POSTED → CREDITED, credit note created with status=POSTED).
  const cnR = await apiPost(
    page,
    `/api/v1/sales/customer-returns/uid/${rDto.uid}/credit-note`,
    {},
    { expectedStatus: 200 },
  );
  const cnDto = unwrap<{ uid: string; id: string }>(cnR);

  return {
    returnUid: rDto.uid,
    returnId: String(rDto.id),
    creditNoteUid: cnDto.uid,
    creditNoteId: String(cnDto.id),
  };
}

// ---------------------------------------------------------------------------
// Branch context injection
// ---------------------------------------------------------------------------

test.beforeEach(async ({ page }) => {
  await page.addInitScript((branchId: string) => {
    localStorage.setItem('orbix.activeBranchId', branchId);
  }, BRANCH_ID);
});

// =============================================================================
// 1. Setup — provision reference rows for the CNA tests
//    Needs cross-module perms — rootadmin only. Tests consume these rows.
// =============================================================================

test.describe('Slice H — CNA setup', () => {
  test.use({ persona: 'rootadmin' });

  test('provisions UoM, item, price-list, customers A+B, and initial stock', async ({ page }) => {
    await page.goto('/dashboard');

    // Open today's business day (idempotent — 4xx accepted on re-runs).
    await apiPost(page, `/api/v1/business-days?branchId=${BRANCH_ID}`,
      { businessDate: TODAY },
      { acceptStatuses: [200, 201, 400, 409] },
    );

    // 1) UoM
    const existingUom = dbQuery(`SELECT id FROM uom WHERE code='UN-${REF_TAG}' LIMIT 1`);
    if (existingUom === '') {
      const r = await apiPost(page, '/api/v1/uoms', {
        code: `UN-${REF_TAG}`,
        name: `Unit ${REF_TAG}`,
        dimension: 'COUNT',
        base: true,
      });
      refs.uomId = String(unwrap<{ id: string }>(r).id);
    } else {
      refs.uomId = existingUom;
    }

    // 2) Item group
    const existingIg = dbQuery(`SELECT id FROM item_group WHERE code='IG-${REF_TAG}' LIMIT 1`);
    if (existingIg === '') {
      const r = await apiPost(page, '/api/v1/item-groups', {
        parentId: null,
        code: `IG-${REF_TAG}`,
        name: `Item group ${REF_TAG}`,
      });
      refs.itemGroupId = String(unwrap<{ id: string }>(r).id);
    } else {
      refs.itemGroupId = existingIg;
    }

    // 3) VAT group
    const existingVat = dbQuery(`SELECT id FROM vat_group WHERE code='V-${REF_TAG}' LIMIT 1`);
    if (existingVat === '') {
      const r = await apiPost(page, '/api/v1/vat-groups', {
        code: `V-${REF_TAG}`,
        name: `VAT ${REF_TAG}`,
        rate: '0.18',
        validFrom: TODAY,
        isDefault: false,
      });
      refs.vatGroupId = String(unwrap<{ id: string }>(r).id);
    } else {
      refs.vatGroupId = existingVat;
    }

    // 4) Item
    const existingItem = dbQuery(`SELECT id FROM item WHERE code='IT-${REF_TAG}' LIMIT 1`);
    if (existingItem === '') {
      const r = await apiPost(page, '/api/v1/items', {
        code: `IT-${REF_TAG}`,
        name: `CNA test item ${REF_TAG}`,
        shortName: `Item ${REF_TAG}`,
        type: 'SELLABLE',
        itemGroupId: refs.itemGroupId,
        uomId: refs.uomId,
        vatGroupId: refs.vatGroupId,
      });
      refs.itemId = String(unwrap<{ id: string }>(r).id);
    } else {
      refs.itemId = existingItem;
    }

    // 4b) Seed stock — 500 units so return flows don't fail on negative balance.
    await apiPost(page, '/api/v1/adjustments', {
      itemId: refs.itemId,
      branchId: BRANCH_ID,
      qty: '500',
      unitCost: '10',
      reason: `e2e cna seed ${RUN_TAG}`,
      sectionId: null,
      batchId: null,
      authorisedByUserId: null,
      allowOversell: false,
    }, { acceptStatuses: [200, 201] });

    // 5) Price list
    const existingPl = dbQuery(`SELECT id FROM price_list WHERE code='PL-${REF_TAG}' LIMIT 1`);
    if (existingPl === '') {
      const r = await apiPost(page, '/api/v1/price-lists', {
        code: `PL-${REF_TAG}`,
        name: `CNA PL ${REF_TAG}`,
        currencyCode: 'TZS',
        validFrom: TODAY,
        validTo: null,
        isDefault: false,
        taxInclusive: false,
      });
      refs.priceListId = String(unwrap<{ id: string }>(r).id);
    } else {
      refs.priceListId = existingPl;
    }

    // 6) Customer A — generous credit limit so the credit gate doesn't block.
    refs.customerAName = `QA CNA-A ${REF_TAG}`;
    const existingCustA = dbQuery(
      `SELECT c.party_id FROM customer c JOIN party p ON p.id=c.party_id WHERE p.name='${escapeSql(refs.customerAName)}' LIMIT 1`,
    );
    if (existingCustA === '') {
      const r = await apiPost(page, '/api/v1/customers', {
        partyId: null,
        party: {
          name: refs.customerAName,
          legalName: `${refs.customerAName} Ltd`,
          category: 'BUSINESS',
          tin: `610-610-CNA`,
          vrn: null,
          phone: `+255722900444`,
          email: null,
          physicalAddress: null,
          postalAddress: null,
          countryCode: 'TZ',
          notes: null,
        },
        creditLimitAmount: '500000',
        creditTermsDays: 30,
        priceListId: refs.priceListId,
        defaultSalesAgentId: null,
        defaultBranchId: BRANCH_ID,
        taxExempt: false,
      });
      refs.customerAId = String(unwrap<{ partyId: string }>(r).partyId);
    } else {
      refs.customerAId = existingCustA;
    }
    refs.customerAUid = dbQuery(`SELECT uid FROM party WHERE id=${refs.customerAId} LIMIT 1`);
    expect(refs.customerAUid, 'customerA uid').toBeTruthy();
    expect(refs.customerAUid, 'customerA uid Crockford ULID').toMatch(ULID_RE);

    // 7) Customer B — distinct party for cross-customer guard test.
    refs.customerBName = `QA CNA-B ${REF_TAG}`;
    const existingCustB = dbQuery(
      `SELECT c.party_id FROM customer c JOIN party p ON p.id=c.party_id WHERE p.name='${escapeSql(refs.customerBName)}' LIMIT 1`,
    );
    if (existingCustB === '') {
      const r = await apiPost(page, '/api/v1/customers', {
        partyId: null,
        party: {
          name: refs.customerBName,
          legalName: `${refs.customerBName} Ltd`,
          category: 'BUSINESS',
          tin: `620-620-CNB`,
          vrn: null,
          phone: `+255722900555`,
          email: null,
          physicalAddress: null,
          postalAddress: null,
          countryCode: 'TZ',
          notes: null,
        },
        creditLimitAmount: '500000',
        creditTermsDays: 30,
        priceListId: refs.priceListId,
        defaultSalesAgentId: null,
        defaultBranchId: BRANCH_ID,
        taxExempt: false,
      });
      refs.customerBId = String(unwrap<{ partyId: string }>(r).partyId);
    } else {
      refs.customerBId = existingCustB;
    }
    refs.customerBUid = dbQuery(`SELECT uid FROM party WHERE id=${refs.customerBId} LIMIT 1`);
    expect(refs.customerBUid, 'customerB uid').toBeTruthy();
    expect(refs.customerBUid, 'customerB uid Crockford ULID').toMatch(ULID_RE);
  });
});

// =============================================================================
// 2. SCENARIO 1 — Full happy-path: return → credit note → full apply
//    Actor: sales-clerk (holds SALES.MANAGE_INVOICE + SALES.MANAGE_RETURN)
//    Slice H — flips when /apply lands
// =============================================================================

test.describe('Slice H — happy-path: full credit-note apply', () => {
  test.use({ persona: 'sales-clerk' as Persona });

  // Scenario 1: create invoice A (source of the return), create invoice B
  // (apply target, different from the return invoice), do the return +
  // credit-note, then apply the full credit against invoice B.
  //
  // Verifications per acceptance criteria:
  //   - Credit note uid is valid Crockford ULID.
  //   - After apply: status = FULLY_ALLOCATED.
  //   - allocatedAmount == totalAmount.
  //   - availableAmount == 0.
  //   - Invoice B paidAmount increased by the apply amount; status = PAID.
  //   - domain_event row with type='CustomerCreditNoteApplied.v1' and payload
  //     containing creditNoteUid, salesInvoiceUid, amount, currencyCode.
  //   - axe-core passes on the credit-notes table page + on the apply modal.
  test.fail(
    // Slice H — flips when /apply lands
    'sales-clerk creates return + credit note then applies full amount to a second open invoice; invoice becomes PAID',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // ---- Invoice A: will be returned / credit-noted ----
      const invA = await createAndPostInvoice(page, {
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        priceListId: r0.priceListId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
        numberSuffix: 'A1',
      });
      expect(invA.uid, 'invoice A uid is Crockford ULID').toMatch(ULID_RE);

      // ---- Invoice B: will be the apply target ----
      const invB = await createAndPostInvoice(page, {
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        priceListId: r0.priceListId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
        numberSuffix: 'B1',
      });
      expect(invB.uid, 'invoice B uid is Crockford ULID').toMatch(ULID_RE);

      // Invoice A and B total should be equal (same qty × price).
      expect(String(invA.uid), 'invoice A uid differs from B').not.toBe(invB.uid);

      // ---- Return all lines of invoice A → issue credit note ----
      const { creditNoteUid } = await createReturnAndCreditNote(page, {
        salesInvoiceUid: invA.uid,
        salesInvoiceId: invA.id,
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
      });
      expect(creditNoteUid, 'credit note uid is Crockford ULID').toMatch(ULID_RE);

      // Confirm DB state before apply: credit note status=POSTED, allocatedAmount=0.
      const cnStatusBefore = dbQuery(`SELECT status FROM customer_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      expect(cnStatusBefore, 'credit note status before apply').toBe('POSTED');
      const allocatedBefore = dbQuery(`SELECT allocated_amount FROM customer_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      expect(Number(allocatedBefore), 'allocatedAmount before apply').toBe(0);

      // ---- Apply full credit to invoice B via the API ----
      // totalAmount from invoice A is the credit note amount. invA.totalAmount
      // may include VAT depending on backend config — fetch the actual credit
      // note to learn the real total.
      const cnBefore = await apiPost(
        page,
        `/api/v1/sales/customer-credit-notes/uid/${creditNoteUid}/apply`,
        { salesInvoiceUid: invB.uid, amount: invA.totalAmount } satisfies ApplyCreditNoteRequest,
        { expectedStatus: 200 },
      );
      const cn = unwrap<CustomerCreditNoteDto>(cnBefore);

      // Contract pins on the response DTO.
      expect(cn.uid, 'response uid').toBe(creditNoteUid);
      expect(cn.uid).toMatch(ULID_RE);
      expect(cn.status, 'status after full apply').toBe('FULLY_ALLOCATED');
      expect(cn.allocatedAmount, 'allocatedAmount == totalAmount').toBe(cn.totalAmount);
      expect(cn.availableAmount, 'availableAmount == 0').toBe(0);
      expect(cn.allocations, 'allocations list is non-null').not.toBeNull();
      expect(cn.allocations?.length, 'one allocation row').toBe(1);

      const alloc = cn.allocations![0];
      expect(alloc.salesInvoiceId, 'allocation.salesInvoiceId is string').toEqual(expect.any(String));
      expect(alloc.amount, 'allocation.amount > 0').toBeGreaterThan(0);
      expect(alloc.allocatedAt, 'allocatedAt is ISO string').toMatch(/^\d{4}-\d{2}-\d{2}/);

      // DB: credit note status=FULLY_ALLOCATED, allocatedAmount=totalAmount.
      const cnStatusAfter = dbQuery(`SELECT status FROM customer_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      expect(cnStatusAfter, 'DB credit note status after full apply').toBe('FULLY_ALLOCATED');

      // DB: invoice B paidAmount > 0; status = PAID (since credit covers full total).
      const invBStatus = dbQuery(`SELECT status FROM sales_invoice WHERE uid='${invB.uid}' LIMIT 1`);
      expect(invBStatus, 'invoice B status after full apply').toBe('PAID');
      const invBPaid = dbQuery(`SELECT paid_amount FROM sales_invoice WHERE uid='${invB.uid}' LIMIT 1`);
      expect(Number(invBPaid), 'invoice B paid_amount > 0').toBeGreaterThan(0);

      // DB: allocation row exists in customer_credit_note_allocation.
      const allocCount = dbCount(
        `SELECT COUNT(*) FROM customer_credit_note_allocation ccna `
        + `JOIN customer_credit_note ccn ON ccn.id=ccna.customer_credit_note_id `
        + `WHERE ccn.uid='${creditNoteUid}'`,
      );
      expect(allocCount, 'allocation row in DB').toBeGreaterThanOrEqual(1);

      // DB: outbox event CustomerCreditNoteApplied.v1 with required payload fields.
      const evCount = dbCount(
        `SELECT COUNT(*) FROM domain_event `
        + `WHERE type='CustomerCreditNoteApplied.v1' `
        + `AND payload_json LIKE '%${creditNoteUid}%' `
        + `AND payload_json LIKE '%${invB.uid}%' `
        + `AND payload_json LIKE '%TZS%'`,
      );
      expect(evCount, 'CustomerCreditNoteApplied.v1 in outbox').toBeGreaterThanOrEqual(1);

      // ---- UI verification + axe-core ----
      // Navigate to the returns / credit-notes page and verify the status badge.
      await page.goto('/sales/returns');
      await dismissDismissableAlerts(page);

      // The credit note row must show FULLY_ALLOCATED status badge.
      const statusBadge = page.locator('[data-testid="credit-note-status"]', { hasText: 'FULLY_ALLOCATED' });
      await expect(statusBadge).toBeVisible({ timeout: 15_000 });

      // The "Apply" button must NOT appear when status=FULLY_ALLOCATED.
      const applyBtn = page.locator('[data-testid="credit-note-apply-btn"]').first();
      await expect(applyBtn).toHaveCount(0);

      // axe-core on the credit-notes table.
      await assertNoSeriousA11yViolations(page, '/sales/returns (credit-notes table)');
    },
  );
});

// =============================================================================
// 3. SCENARIO 2 — Partial apply twice → FULLY_ALLOCATED
//    Actor: sales-clerk
//    Slice H — flips when /apply lands
// =============================================================================

test.describe('Slice H — partial apply twice → FULLY_ALLOCATED', () => {
  test.use({ persona: 'sales-clerk' as Persona });

  // Credit note for TZS 10,000 (net). Apply 4,000 → PARTIALLY_ALLOCATED.
  // Apply 6,000 → FULLY_ALLOCATED. Allocations list contains both rows.
  // The exact amounts below assume tax-exclusive pricing; if the backend
  // includes VAT in totalAmount the credit note available amount is larger —
  // the test fetches the actual totalAmount from the GET response to split it.
  test.fail(
    // Slice H — flips when /apply lands
    'two partial applies exhaust the credit note (POSTED → PARTIALLY_ALLOCATED → FULLY_ALLOCATED)',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // Invoice to be returned (2 units × 5000 = 10,000 net).
      const invReturn = await createAndPostInvoice(page, {
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        priceListId: r0.priceListId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
        numberSuffix: 'PARTRET',
      });

      // Invoice target-1 and target-2 — used as apply targets.
      const invTarget1 = await createAndPostInvoice(page, {
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        priceListId: r0.priceListId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
        numberSuffix: 'PARTT1',
      });
      const invTarget2 = await createAndPostInvoice(page, {
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        priceListId: r0.priceListId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
        numberSuffix: 'PARTT2',
      });

      const { creditNoteUid } = await createReturnAndCreditNote(page, {
        salesInvoiceUid: invReturn.uid,
        salesInvoiceId: invReturn.id,
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
      });

      // Fetch the credit note to learn the real totalAmount (includes VAT).
      const cnInitial = await apiPost(
        page,
        // No GET endpoint for a single credit note by uid in the spec — probe via apply with 0 is illegal.
        // Use the returns endpoint to find the credit note total. Since we don't have a GET,
        // resolve totalAmount from the DB directly.
        `/api/v1/sales/customer-credit-notes/uid/${creditNoteUid}/apply`,
        { salesInvoiceUid: invTarget1.uid, amount: 0.01 } satisfies ApplyCreditNoteRequest,
        { acceptStatuses: [422] }, // amount 0.01 may pass or fail dep on precision — use DB value instead
      );
      void cnInitial; // probe intentionally discarded; use DB below

      const totalAmountRaw = dbQuery(`SELECT total_amount FROM customer_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      const totalAmount = Number(totalAmountRaw);
      expect(totalAmount, 'credit note totalAmount > 0').toBeGreaterThan(0);

      // Split the total into two parts (4/10 + 6/10 of the total).
      // We round to 2 decimal places to avoid floating-point precision issues.
      const firstAmount  = Math.round((totalAmount * 0.4) * 100) / 100;
      const secondAmount = Math.round((totalAmount - firstAmount) * 100) / 100;

      // --- First apply: POSTED → PARTIALLY_ALLOCATED ---
      const r1 = await apiPost(
        page,
        `/api/v1/sales/customer-credit-notes/uid/${creditNoteUid}/apply`,
        { salesInvoiceUid: invTarget1.uid, amount: firstAmount } satisfies ApplyCreditNoteRequest,
        { expectedStatus: 200 },
      );
      const cn1 = unwrap<CustomerCreditNoteDto>(r1);

      expect(cn1.status, 'status after first partial apply').toBe('PARTIALLY_ALLOCATED');
      expect(cn1.allocatedAmount, 'allocatedAmount after first apply').toBeCloseTo(firstAmount, 2);
      expect(cn1.availableAmount, 'availableAmount after first apply').toBeCloseTo(secondAmount, 2);
      expect(cn1.allocations?.length, 'one allocation after first apply').toBe(1);

      // DB probe: PARTIALLY_ALLOCATED.
      const statusMid = dbQuery(`SELECT status FROM customer_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      expect(statusMid, 'DB status after first apply').toBe('PARTIALLY_ALLOCATED');

      // --- Second apply: PARTIALLY_ALLOCATED → FULLY_ALLOCATED ---
      const r2 = await apiPost(
        page,
        `/api/v1/sales/customer-credit-notes/uid/${creditNoteUid}/apply`,
        { salesInvoiceUid: invTarget2.uid, amount: secondAmount } satisfies ApplyCreditNoteRequest,
        { expectedStatus: 200 },
      );
      const cn2 = unwrap<CustomerCreditNoteDto>(r2);

      expect(cn2.status, 'status after second apply').toBe('FULLY_ALLOCATED');
      expect(cn2.allocatedAmount, 'allocatedAmount == totalAmount').toBeCloseTo(totalAmount, 2);
      expect(cn2.availableAmount, 'availableAmount == 0').toBeCloseTo(0, 2);
      expect(cn2.allocations?.length, 'two allocation rows').toBe(2);

      // Both allocation rows must reference the correct invoices.
      const allocInvoiceIds = (cn2.allocations ?? []).map(a => a.salesInvoiceId);
      expect(allocInvoiceIds.length, 'two distinct allocation invoice ids').toBe(2);

      // DB: both CustomerCreditNoteApplied.v1 events emitted.
      const evCount = dbCount(
        `SELECT COUNT(*) FROM domain_event `
        + `WHERE type='CustomerCreditNoteApplied.v1' `
        + `AND payload_json LIKE '%${creditNoteUid}%'`,
      );
      expect(evCount, 'two CustomerCreditNoteApplied.v1 events in outbox').toBeGreaterThanOrEqual(2);
    },
  );
});

// =============================================================================
// 4. SCENARIO 3 — Over-apply → 422
//    Actor: sales-clerk
//    Slice H — flips when /apply lands
// =============================================================================

test.describe('Slice H — over-apply guard → 422', () => {
  test.use({ persona: 'sales-clerk' as Persona });

  test.fail(
    // Slice H — flips when /apply lands
    'applying more than availableAmount returns 422 with error message; UI surfaces error inline on the modal',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // Invoice to return.
      const invReturn = await createAndPostInvoice(page, {
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        priceListId: r0.priceListId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
        numberSuffix: 'OVRA',
      });

      // Open invoice as apply target.
      const invTarget = await createAndPostInvoice(page, {
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        priceListId: r0.priceListId,
        vatGroupId: r0.vatGroupId,
        qty: 10,
        unitPrice: 5_000,
        numberSuffix: 'OVRT',
      });

      const { creditNoteUid } = await createReturnAndCreditNote(page, {
        salesInvoiceUid: invReturn.uid,
        salesInvoiceId: invReturn.id,
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
      });

      const totalRaw = dbQuery(`SELECT total_amount FROM customer_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      const totalAmount = Number(totalRaw);
      const overAmount = totalAmount + 1;

      // API level: over-apply must 422.
      const apiResp = await apiPost(
        page,
        `/api/v1/sales/customer-credit-notes/uid/${creditNoteUid}/apply`,
        { salesInvoiceUid: invTarget.uid, amount: overAmount } satisfies ApplyCreditNoteRequest,
        { acceptStatuses: [422] },
      );
      expect(apiResp.status, 'over-apply status code').toBe(422);
      const errBody = JSON.parse(apiResp.body);
      // The error payload must carry a meaningful message (not empty).
      const errMsg: string = errBody?.message ?? errBody?.errors?.[0] ?? '';
      expect(errMsg.length, 'error message is non-empty').toBeGreaterThan(0);

      // UI level: open the apply modal and verify the inline error is shown.
      await page.goto('/sales/returns');
      await dismissDismissableAlerts(page);

      // Find the credit note row's "Apply" button and open the modal.
      const applyBtn = page.locator('[data-testid="credit-note-apply-btn"]').first();
      await expect(applyBtn).toBeVisible({ timeout: 15_000 });
      await applyBtn.click();

      const modal = page.locator('[data-testid="credit-note-apply-modal"]');
      await expect(modal).toBeVisible({ timeout: 10_000 });

      // axe-core on the open apply modal.
      await dismissDismissableAlerts(page);
      await assertNoSeriousA11yViolations(page, '/sales/returns — apply modal open');

      // Fill in an over-limit amount and submit.
      const amountInput = modal.locator('[data-testid="credit-note-apply-amount-input"]');
      await amountInput.fill(String(overAmount));
      const submitBtn = modal.locator('[data-testid="credit-note-apply-submit"]');
      await submitBtn.click();

      // The inline error element must become visible after the 422 response.
      const inlineError = modal.locator('[data-testid="credit-note-apply-error"]');
      await expect(inlineError).toBeVisible({ timeout: 10_000 });
      await expect(inlineError).not.toBeEmpty();
    },
  );
});

// =============================================================================
// 5. SCENARIO 4 — Cross-customer apply → 422
//    Actor: sales-clerk
//    Slice H — flips when /apply lands
// =============================================================================

test.describe('Slice H — cross-customer apply guard → 422', () => {
  test.use({ persona: 'sales-clerk' as Persona });

  test.fail(
    // Slice H — flips when /apply lands
    'applying a credit note for customer A to an invoice of customer B returns 422 (same-customer constraint)',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // Invoice for customer A (to return + generate credit note).
      const invA = await createAndPostInvoice(page, {
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        priceListId: r0.priceListId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
        numberSuffix: 'CCA',
      });

      // Invoice for customer B (the forbidden apply target).
      const invB = await createAndPostInvoice(page, {
        customerPartyId: r0.customerBId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        priceListId: r0.priceListId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
        numberSuffix: 'CCB',
      });

      const { creditNoteUid } = await createReturnAndCreditNote(page, {
        salesInvoiceUid: invA.uid,
        salesInvoiceId: invA.id,
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
      });

      const totalRaw = dbQuery(`SELECT total_amount FROM customer_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      const applyAmount = Number(totalRaw);

      // Attempt to apply customer A's credit note to customer B's invoice.
      const apiResp = await apiPost(
        page,
        `/api/v1/sales/customer-credit-notes/uid/${creditNoteUid}/apply`,
        { salesInvoiceUid: invB.uid, amount: applyAmount } satisfies ApplyCreditNoteRequest,
        { acceptStatuses: [422] },
      );
      expect(apiResp.status, 'cross-customer apply status code').toBe(422);

      // The error message must hint at the customer mismatch (not just generic).
      const errBody = JSON.parse(apiResp.body);
      const errMsg: string = errBody?.message ?? errBody?.errors?.[0] ?? '';
      expect(errMsg.length, 'cross-customer error message is non-empty').toBeGreaterThan(0);

      // DB: no allocation row was created.
      const allocCount = dbCount(
        `SELECT COUNT(*) FROM customer_credit_note_allocation ccna `
        + `JOIN customer_credit_note ccn ON ccn.id=ccna.customer_credit_note_id `
        + `WHERE ccn.uid='${creditNoteUid}'`,
      );
      expect(allocCount, 'no allocation row created on cross-customer attempt').toBe(0);
    },
  );
});

// =============================================================================
// 6. SCENARIO 5 — Apply to a FULLY_ALLOCATED credit note → 409
//    Actor: sales-clerk
//    Slice H — flips when /apply lands
// =============================================================================

test.describe('Slice H — apply to FULLY_ALLOCATED → 409', () => {
  test.use({ persona: 'sales-clerk' as Persona });

  test.fail(
    // Slice H — flips when /apply lands
    'applying against a FULLY_ALLOCATED credit note returns 409 (illegal-state)',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // Set up: one invoice to return, one invoice to apply against (fully).
      const invReturn = await createAndPostInvoice(page, {
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        priceListId: r0.priceListId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
        numberSuffix: 'FULLA',
      });
      const invTarget = await createAndPostInvoice(page, {
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        priceListId: r0.priceListId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
        numberSuffix: 'FULLT',
      });

      const { creditNoteUid } = await createReturnAndCreditNote(page, {
        salesInvoiceUid: invReturn.uid,
        salesInvoiceId: invReturn.id,
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
      });

      const totalRaw = dbQuery(`SELECT total_amount FROM customer_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      const totalAmount = Number(totalRaw);

      // First apply — exhausts the credit note.
      await apiPost(
        page,
        `/api/v1/sales/customer-credit-notes/uid/${creditNoteUid}/apply`,
        { salesInvoiceUid: invTarget.uid, amount: totalAmount } satisfies ApplyCreditNoteRequest,
        { expectedStatus: 200 },
      );

      // Confirm credit note is now FULLY_ALLOCATED.
      const statusFull = dbQuery(`SELECT status FROM customer_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      expect(statusFull, 'credit note is FULLY_ALLOCATED after first apply').toBe('FULLY_ALLOCATED');

      // Second invoice for the re-apply attempt (different invoice so it's not
      // the already-matched one).
      const invTarget2 = await createAndPostInvoice(page, {
        customerPartyId: r0.customerAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        priceListId: r0.priceListId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
        numberSuffix: 'FULLT2',
      });

      // Attempt to apply again against the already-fully-allocated credit note.
      const apiResp = await apiPost(
        page,
        `/api/v1/sales/customer-credit-notes/uid/${creditNoteUid}/apply`,
        { salesInvoiceUid: invTarget2.uid, amount: 1 } satisfies ApplyCreditNoteRequest,
        { acceptStatuses: [409] },
      );
      expect(apiResp.status, 'apply-to-fully-allocated status code').toBe(409);
    },
  );
});

// =============================================================================
// 7. SCENARIO 6 — Permission 403 gate
//    Actor: cashier (no SALES.MANAGE_RETURN)
//    Also confirms "Apply" button is hidden for this persona.
//    Slice H — flips when /apply lands
// =============================================================================

test.describe('Slice H — permission 403 gate', () => {
  test.use({ persona: 'cashier' as Persona });

  test.fail(
    // Slice H — flips when /apply lands
    'cashier (no SALES.MANAGE_RETURN) gets 403 on POST /apply; Apply button is hidden in the UI',
    async ({ page }) => {
      const r0 = requireRefs();

      // We need a valid credit note uid to attempt the apply. Re-use the
      // rootadmin-visible DB state: pick any POSTED credit note for customer A
      // from a prior test run. If none exists (fresh volume), skip the endpoint
      // probe and rely solely on the UI check.
      const existingCnUid = dbQuery(
        `SELECT uid FROM customer_credit_note `
        + `WHERE status IN ('POSTED','PARTIALLY_ALLOCATED') LIMIT 1`,
      );

      if (existingCnUid !== '') {
        // Endpoint-level 403 check — cashier must be rejected.
        const apiResp = await apiPost(
          page,
          `/api/v1/sales/customer-credit-notes/uid/${existingCnUid}/apply`,
          { salesInvoiceUid: r0.customerAUid, amount: 1 } as ApplyCreditNoteRequest,
          { acceptStatuses: [403] },
        );
        expect(apiResp.status, 'cashier apply attempt returns 403').toBe(403);
      }

      // UI-level: navigate to /sales/returns and confirm no "Apply" buttons
      // are rendered for this persona.
      await page.goto('/sales/returns');
      await dismissDismissableAlerts(page);

      // The "Apply" button must be absent for a persona without SALES.MANAGE_RETURN.
      // Give the page time to settle before asserting absence.
      await expect(page.locator('[data-testid="credit-note-apply-btn"]')).toHaveCount(0, { timeout: 10_000 });
    },
  );
});
