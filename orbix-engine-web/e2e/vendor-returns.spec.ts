import { execFileSync } from 'node:child_process';
import { type Page } from '@playwright/test';
import { test, expect } from './personas.fixture';
import type { Persona } from './test-users';
import AxeBuilder from '@axe-core/playwright';

/**
 * End-to-end Vendor Returns + Credit-Note Allocation spec (Slice H.1 gate).
 *
 * Drives the AP mirror of the customer-returns flow: vendor return → post →
 * issue credit note → apply against open supplier invoice. Runs against the
 * QA-parity container at http://localhost:8081/.
 *
 * Every scenario is tagged `test.fail` — the backend entities (vendor_return,
 * vendor_credit_note, vendor_credit_note_allocation tables — V76), the
 * permission seed (PROCUREMENT.MANAGE_RETURN id 136 — V77), and the Angular
 * feature pages do not exist yet. When tasks #2 (backend) + #3 (frontend) in
 * slice-h1-vendor-returns-plan.md land, drop the `test.fail` wrappers to flip
 * the gate green.
 *
 * Happy-path actor: `procurement-officer` — holds PROCUREMENT.MANAGE_LPO +
 * GRN.POST + PROCUREMENT.MANAGE_RETURN (added to the persona below for H.1).
 * Negative-path actors:
 *   - `sales-clerk` — holds SALES.* but no PROCUREMENT.MANAGE_RETURN.
 *   - `cashier`     — holds POS.* only, no procurement perms whatsoever.
 *
 * Backend gaps that must close before flipping:
 *   1. V76 migration: vendor_return, vendor_return_line, vendor_credit_note,
 *      vendor_credit_note_allocation tables + sequences + indexes.
 *   2. V77 migration: PROCUREMENT.MANAGE_RETURN (id 136) seed + ADMIN grant.
 *   3. VendorReturnServiceImpl: createDraft, post, cancel, issueCreditNote,
 *      list, listCreditNotes, applyToInvoice.
 *   4. VendorReturnController: flat under /api/v1 (NO /procurement/ sub-prefix).
 *   5. SupplierInvoiceService.applyVendorCredit method.
 *   6. Three outbox events: VendorReturnPosted.v1, VendorCreditNoteIssued.v1,
 *      VendorCreditNoteApplied.v1.
 *   7. Angular: /procurement/vendor-returns queue page + create form + apply modal.
 *
 * Frontend test-id contract (frontend agent wires these — mirror customer-returns):
 *
 *   data-testid                                | Component                                    | Purpose
 *   -------------------------------------------|----------------------------------------------|------------------------------------------
 *   vendor-credit-note-apply-btn               | vendor-returns.component.ts                  | "Apply" per POSTED/PARTIALLY_ALLOCATED row
 *   vendor-credit-note-status                  | vendor-returns.component.ts                  | Status badge per credit note row
 *   vendor-credit-note-allocated-amount        | vendor-returns.component.ts                  | Allocated amount cell per row
 *   vendor-credit-note-apply-modal             | vendor-credit-note-apply-modal.component.ts  | Apply modal container
 *   vendor-credit-note-apply-invoice-select    | vendor-credit-note-apply-modal.component.ts  | Open supplier invoice picker
 *   vendor-credit-note-apply-amount-input      | vendor-credit-note-apply-modal.component.ts  | Amount input
 *   vendor-credit-note-apply-submit            | vendor-credit-note-apply-modal.component.ts  | Submit button
 *   vendor-credit-note-apply-error             | vendor-credit-note-apply-modal.component.ts  | Inline error (422/409 responses)
 *   vendor-credit-note-allocations-list        | vendor-returns.component.ts (expanded row)   | Allocation history rows
 *
 * Sequencing: Playwright workers=1, fullyParallel=false. The `setup` describe
 * provisions all reference rows (UoM, item group, VAT group, item, supplier,
 * two supplier invoices as apply targets). Lifecycle tests reuse those ids.
 * DB probes go via `docker exec orbix mariadb -Nse`.
 */

// ---------------------------------------------------------------------------
// Run tag + DB probe helpers (mirror procurement.spec.ts / customer-returns.spec.ts)
// ---------------------------------------------------------------------------

const RUN_TAG = Date.now().toString(36).slice(-5).toUpperCase();
const REF_TAG = 'E2EVRET';   // Vendor RETurn
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
// Wire-shape type guards — pinned against slice-h1-vendor-returns-plan.md §5
// ---------------------------------------------------------------------------

type VendorReturnStatus = 'DRAFT' | 'POSTED' | 'CREDITED';
type VendorCreditNoteStatus = 'POSTED' | 'PARTIALLY_ALLOCATED' | 'FULLY_ALLOCATED';
type ReturnReason = 'DAMAGED' | 'WRONG_ITEM' | 'EXPIRED' | 'OTHER';

interface CreateVendorReturnRequest {
  supplierUid: string;
  originalGrnUid?: string;
  originalSupplierInvoiceUid?: string;
  returnDate: string;
  reason: ReturnReason;
  restock: boolean;
  notes?: string;
  lines: Array<{
    itemUid: string;
    uomUid: string;
    returnedQty: number;
    unitPrice: number;
    vatGroupUid: string;
    originalLineId?: string;
  }>;
}

interface VendorReturnDto {
  id: string;
  uid: string;
  number: string;
  supplierId: string;
  supplierUid: string | null;
  originalGrnId: string | null;
  originalGrnNumber: string | null;
  originalSupplierInvoiceId: string | null;
  returnDate: string;
  reason: ReturnReason;
  restock: boolean;
  totalAmount: number;
  status: VendorReturnStatus;
  postedAt: string | null;
  postedBy: string | null;
  lines: VendorReturnLineDto[];
}

interface VendorReturnLineDto {
  id: string;
  lineNo: number;
  itemId: string;
  uomId: string;
  returnedQty: number;
  unitPrice: number;
  taxAmount: number;
  lineTotal: number;
}

interface IssueVendorCreditNoteRequest {
  cnDate: string;
  notes?: string;
}

interface VendorCreditNoteDto {
  id: string;
  uid: string;
  number: string;
  supplierId: string;
  supplierUid: string | null;
  vendorReturnId: string | null;
  cnDate: string;
  currencyCode: string;
  totalAmount: number;
  allocatedAmount: number;
  availableAmount: number;
  status: VendorCreditNoteStatus;
  notes: string | null;
  allocations: Array<{
    id: string;
    supplierInvoiceId: string;
    supplierInvoiceNumber: string | null;
    amount: number;
    allocatedAt: string;
    allocatedBy: string | null;
  }> | null;
}

interface ApplyVendorCreditNoteRequest {
  supplierInvoiceUid: string;
  amount: number;
}

// Crockford ULID regex
const ULID_RE = /^[0-9A-HJKMNP-TV-Z]{26}$/;

// ---------------------------------------------------------------------------
// HTTP helpers — mirror customer-returns.spec.ts exactly
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

async function apiGet(
  page: Page,
  path: string,
  opts?: { acceptStatuses?: number[]; expectedStatus?: number },
): Promise<ApiResult> {
  return apiCall(page, 'GET', path, undefined, opts);
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
// We need TWO suppliers:
//   - `supplierAId` / `supplierAUid` — the "return" supplier. One supplier
//     invoice A is the source GRN/invoice reference (vendor return against it).
//     A second open supplier invoice B is the apply target (a different unpaid
//     invoice that we cancel the credit against).
//   - `supplierBId` / `supplierBUid` — a distinct supplier for the cross-
//     supplier 422 guard (credit note for A must not apply to B's invoice).
//
// We also track item + UoM + VAT group UIDs since the vendor-return create
// request requires UIDs (not numeric ids) for item/uom/vatGroup per the wire
// shape in slice-h1-vendor-returns-plan.md.
// ---------------------------------------------------------------------------

interface RefIds {
  uomId: string;
  uomUid: string;
  itemGroupId: string;
  vatGroupId: string;
  vatGroupUid: string;
  itemId: string;
  itemUid: string;
  // Supplier A — source of returns + credit notes
  supplierAId: string;
  supplierAUid: string;
  supplierAName: string;
  // Supplier B — separate supplier for cross-supplier 422 test
  supplierBId: string;
  supplierBUid: string;
  supplierBName: string;
}

const refs: Partial<RefIds> = {};

function loadRefsFromDb(): void {
  refs.uomId       = dbQuery(`SELECT id FROM uom WHERE code='UN-${REF_TAG}' LIMIT 1`) || refs.uomId;
  refs.uomUid      = refs.uomId ? dbQuery(`SELECT uid FROM uom WHERE id=${refs.uomId} LIMIT 1`) : refs.uomUid;
  refs.itemGroupId = dbQuery(`SELECT id FROM item_group WHERE code='IG-${REF_TAG}' LIMIT 1`) || refs.itemGroupId;
  refs.vatGroupId  = dbQuery(`SELECT id FROM vat_group WHERE code='V-${REF_TAG}' LIMIT 1`) || refs.vatGroupId;
  refs.vatGroupUid = refs.vatGroupId ? dbQuery(`SELECT uid FROM vat_group WHERE id=${refs.vatGroupId} LIMIT 1`) : refs.vatGroupUid;
  refs.itemId      = dbQuery(`SELECT id FROM item WHERE code='IT-${REF_TAG}' LIMIT 1`) || refs.itemId;
  refs.itemUid     = refs.itemId ? dbQuery(`SELECT uid FROM item WHERE id=${refs.itemId} LIMIT 1`) : refs.itemUid;

  refs.supplierAName = `QA VRET-A ${REF_TAG}`;
  refs.supplierBName = `QA VRET-B ${REF_TAG}`;

  const suppARow = dbQuery(
    `SELECT s.party_id FROM supplier s JOIN party p ON p.id=s.party_id WHERE p.name='${escapeSql(refs.supplierAName ?? '')}' LIMIT 1`,
  );
  if (suppARow) {
    refs.supplierAId  = suppARow;
    refs.supplierAUid = dbQuery(`SELECT uid FROM party WHERE id=${suppARow} LIMIT 1`);
  }

  const suppBRow = dbQuery(
    `SELECT s.party_id FROM supplier s JOIN party p ON p.id=s.party_id WHERE p.name='${escapeSql(refs.supplierBName ?? '')}' LIMIT 1`,
  );
  if (suppBRow) {
    refs.supplierBId  = suppBRow;
    refs.supplierBUid = dbQuery(`SELECT uid FROM party WHERE id=${suppBRow} LIMIT 1`);
  }
}

function requireRefs(): RefIds {
  if (!refs.uomId || !refs.uomUid || !refs.itemGroupId || !refs.vatGroupId || !refs.vatGroupUid
      || !refs.itemId || !refs.itemUid || !refs.supplierAId || !refs.supplierAUid
      || !refs.supplierBId || !refs.supplierBUid) {
    loadRefsFromDb();
  }
  if (!refs.uomId || !refs.uomUid || !refs.itemGroupId || !refs.vatGroupId || !refs.vatGroupUid
      || !refs.itemId || !refs.itemUid || !refs.supplierAId || !refs.supplierAUid
      || !refs.supplierBId || !refs.supplierBUid) {
    throw new Error(
      `VRET refs missing — setup must run first. Got: ${JSON.stringify(refs)}`,
    );
  }
  return refs as RefIds;
}

/**
 * Create and post a supplier invoice for the given supplier + item. Returns
 * its uid, numeric id as string, and totalAmount. Used both as a source-of-
 * return reference and as open-invoice apply targets.
 *
 * Mirrors procurement.spec.ts pattern for supplier invoices. The exact request
 * shape follows whatever SupplierInvoiceController accepts — lines carry
 * itemId + uomId as numeric string ids (JSON:API coercion) consistent with
 * the rest of the procurement suite.
 */
async function createAndPostSupplierInvoice(
  page: Page,
  opts: {
    supplierPartyId: string;
    itemId: string;
    uomId: string;
    vatGroupId: string;
    qty: number;
    unitPrice: number;
    numberSuffix: string;
  },
): Promise<{ uid: string; id: string; totalAmount: number }> {
  const draft = await apiPost(page, '/api/v1/supplier-invoices', {
    number: `SINV-${RUN_TAG}-${opts.numberSuffix}`,
    branchId: BRANCH_ID,
    supplierId: opts.supplierPartyId,
    invoiceDate: TODAY,
    dueDate: TODAY,
    currencyCode: 'TZS',
    reference: `e2e vret ${RUN_TAG}`,
    notes: null,
    lines: [{
      itemId: opts.itemId,
      uomId: opts.uomId,
      qty: String(opts.qty),
      unitPrice: String(opts.unitPrice),
      vatGroupId: opts.vatGroupId,
    }],
  });
  const dto = unwrap<{ uid: string; id: string; totalAmount: number }>(draft);
  await apiPost(page, `/api/v1/supplier-invoices/uid/${dto.uid}/post`, {}, { expectedStatus: 200 });
  return { uid: dto.uid, id: String(dto.id), totalAmount: dto.totalAmount };
}

/**
 * Create a vendor return in DRAFT against the given supplier, post it, then
 * issue a credit note. Returns the return uid + the credit note uid.
 *
 * `restock: true` — goods were on-hand and are being returned (stock-OUT move).
 * The vendor return does NOT require a source GRN/invoice in the request body
 * (both fields are optional per the plan); we pass the supplier invoice UID as
 * an informational reference where available.
 */
async function createReturnAndVendorCreditNote(
  page: Page,
  opts: {
    supplierUid: string;
    originalSupplierInvoiceUid?: string;
    itemUid: string;
    uomUid: string;
    vatGroupUid: string;
    qty: number;
    unitPrice: number;
    restock: boolean;
  },
): Promise<{ returnUid: string; returnId: string; creditNoteUid: string; creditNoteId: string }> {
  // POST vendor return — DRAFT.
  const returnBody: CreateVendorReturnRequest = {
    supplierUid: opts.supplierUid,
    originalSupplierInvoiceUid: opts.originalSupplierInvoiceUid,
    returnDate: TODAY,
    reason: 'DAMAGED',
    restock: opts.restock,
    notes: `e2e vret ${RUN_TAG}`,
    lines: [{
      itemUid: opts.itemUid,
      uomUid: opts.uomUid,
      returnedQty: opts.qty,
      unitPrice: opts.unitPrice,
      vatGroupUid: opts.vatGroupUid,
    }],
  };
  const draftR = await apiPost(page, '/api/v1/vendor-returns', returnBody);
  const rDto = unwrap<{ uid: string; id: string }>(draftR);

  // Advance DRAFT → POSTED (stock-OUT fires here).
  await apiPost(page, `/api/v1/vendor-returns/uid/${rDto.uid}/post`, {}, { expectedStatus: 200 });

  // Issue credit note: POSTED → CREDITED, credit note created with status=POSTED.
  const cnReq: IssueVendorCreditNoteRequest = {
    cnDate: TODAY,
    notes: `e2e cn ${RUN_TAG}`,
  };
  const cnR = await apiPost(
    page,
    `/api/v1/vendor-returns/uid/${rDto.uid}/issue-credit-note`,
    cnReq,
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
// 1. Setup — provision reference rows for VRET tests
//    Needs cross-module perms — rootadmin only. Tests consume these rows.
// =============================================================================

test.describe('Slice H.1 — VRET setup', () => {
  test.use({ persona: 'rootadmin' });

  test('provisions UoM, item, VAT group, suppliers A+B, initial stock, and open business day', async ({ page }) => {
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
    refs.uomUid = dbQuery(`SELECT uid FROM uom WHERE id=${refs.uomId} LIMIT 1`);
    expect(refs.uomUid, 'uom uid is Crockford ULID').toMatch(ULID_RE);

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
    refs.vatGroupUid = dbQuery(`SELECT uid FROM vat_group WHERE id=${refs.vatGroupId} LIMIT 1`);
    expect(refs.vatGroupUid, 'vatGroup uid is Crockford ULID').toMatch(ULID_RE);

    // 4) Item (PURCHASABLE so it can appear on a GRN / vendor return)
    const existingItem = dbQuery(`SELECT id FROM item WHERE code='IT-${REF_TAG}' LIMIT 1`);
    if (existingItem === '') {
      const r = await apiPost(page, '/api/v1/items', {
        code: `IT-${REF_TAG}`,
        name: `VRET test item ${REF_TAG}`,
        shortName: `Item ${REF_TAG}`,
        type: 'PURCHASABLE',
        itemGroupId: refs.itemGroupId,
        uomId: refs.uomId,
        vatGroupId: refs.vatGroupId,
      });
      refs.itemId = String(unwrap<{ id: string }>(r).id);
    } else {
      refs.itemId = existingItem;
    }
    refs.itemUid = dbQuery(`SELECT uid FROM item WHERE id=${refs.itemId} LIMIT 1`);
    expect(refs.itemUid, 'item uid is Crockford ULID').toMatch(ULID_RE);

    // 4b) Seed stock — 500 units so vendor-return post() doesn't fail on
    //     negative balance (restock=true reduces on-hand).
    await apiPost(page, '/api/v1/adjustments', {
      itemId: refs.itemId,
      branchId: BRANCH_ID,
      qty: '500',
      unitCost: '10',
      reason: `e2e vret seed ${RUN_TAG}`,
      sectionId: null,
      batchId: null,
      authorisedByUserId: null,
      allowOversell: false,
    }, { acceptStatuses: [200, 201] });

    // 5) Supplier A
    refs.supplierAName = `QA VRET-A ${REF_TAG}`;
    const existingSuppA = dbQuery(
      `SELECT s.party_id FROM supplier s JOIN party p ON p.id=s.party_id WHERE p.name='${escapeSql(refs.supplierAName)}' LIMIT 1`,
    );
    if (existingSuppA === '') {
      const r = await apiPost(page, '/api/v1/suppliers', {
        partyId: null,
        party: {
          name: refs.supplierAName,
          legalName: `${refs.supplierAName} Ltd`,
          category: 'BUSINESS',
          tin: `710-710-VRETA`,
          vrn: null,
          phone: `+255722800441`,
          email: null,
          physicalAddress: null,
          postalAddress: null,
          countryCode: 'TZ',
          notes: null,
        },
        creditTermsDays: 30,
        defaultBranchId: BRANCH_ID,
      });
      refs.supplierAId = String(unwrap<{ partyId: string }>(r).partyId);
    } else {
      refs.supplierAId = existingSuppA;
    }
    refs.supplierAUid = dbQuery(`SELECT uid FROM party WHERE id=${refs.supplierAId} LIMIT 1`);
    expect(refs.supplierAUid, 'supplierA uid is Crockford ULID').toMatch(ULID_RE);

    // 6) Supplier B — distinct party for cross-supplier guard test
    refs.supplierBName = `QA VRET-B ${REF_TAG}`;
    const existingSuppB = dbQuery(
      `SELECT s.party_id FROM supplier s JOIN party p ON p.id=s.party_id WHERE p.name='${escapeSql(refs.supplierBName)}' LIMIT 1`,
    );
    if (existingSuppB === '') {
      const r = await apiPost(page, '/api/v1/suppliers', {
        partyId: null,
        party: {
          name: refs.supplierBName,
          legalName: `${refs.supplierBName} Ltd`,
          category: 'BUSINESS',
          tin: `720-720-VRETB`,
          vrn: null,
          phone: `+255722800442`,
          email: null,
          physicalAddress: null,
          postalAddress: null,
          countryCode: 'TZ',
          notes: null,
        },
        creditTermsDays: 30,
        defaultBranchId: BRANCH_ID,
      });
      refs.supplierBId = String(unwrap<{ partyId: string }>(r).partyId);
    } else {
      refs.supplierBId = existingSuppB;
    }
    refs.supplierBUid = dbQuery(`SELECT uid FROM party WHERE id=${refs.supplierBId} LIMIT 1`);
    expect(refs.supplierBUid, 'supplierB uid is Crockford ULID').toMatch(ULID_RE);
  });
});

// =============================================================================
// 2. SCENARIO 1 — Full happy AP path
//    Actor: procurement-officer (holds PROCUREMENT.MANAGE_RETURN per H.1 widening)
//    Slice H.1 — flips when /issue-credit-note + /apply land
// =============================================================================

test.describe('Slice H.1 — happy AP path: vendor return → credit note → full apply', () => {
  test.use({ persona: 'procurement-officer' as Persona });

  /**
   * Acceptance criteria:
   *   - Vendor return created in DRAFT with valid Crockford ULID.
   *   - POST /uid/{uid}/post → status=POSTED; stock on-hand reduced (probe DB).
   *   - POST /uid/{uid}/issue-credit-note → vendor_return status=CREDITED;
   *     vendor_credit_note created with status=POSTED, uid is Crockford ULID.
   *   - POST /vendor-credit-notes/uid/{uid}/apply with supplierInvoiceUid (B) +
   *     full amount → status=FULLY_ALLOCATED; allocatedAmount=totalAmount;
   *     availableAmount=0; one allocation row.
   *   - Supplier invoice B paidAmount > 0; status=PAID (full cover).
   *   - domain_event rows: VendorReturnPosted.v1, VendorCreditNoteIssued.v1,
   *     VendorCreditNoteApplied.v1 — all present with correct payload needles.
   *   - axe-core: no critical/serious violations on /procurement/vendor-returns.
   */
  test.fail(
    // Slice H.1 — flips when /apply lands
    'procurement-officer creates vendor return → posts → issues credit note → applies full amount to open supplier invoice; invoice becomes PAID',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // ---- Supplier invoice A: will be the vendor-return reference ----
      const invA = await createAndPostSupplierInvoice(page, {
        supplierPartyId: r0.supplierAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
        numberSuffix: 'A1',
      });
      expect(invA.uid, 'supplier invoice A uid is Crockford ULID').toMatch(ULID_RE);

      // ---- Supplier invoice B: will be the apply target ----
      const invB = await createAndPostSupplierInvoice(page, {
        supplierPartyId: r0.supplierAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
        numberSuffix: 'B1',
      });
      expect(invB.uid, 'supplier invoice B uid is Crockford ULID').toMatch(ULID_RE);
      expect(invB.uid, 'invoice UIDs must differ').not.toBe(invA.uid);

      // Capture on-hand before return.
      const onHandBefore = dbCount(
        `SELECT COALESCE(SUM(qty_change),0) FROM stock_move WHERE item_id=${r0.itemId} AND branch_id=${BRANCH_ID}`,
      );

      // ---- Create vendor return (DRAFT) + post + issue credit note ----
      const { returnUid, creditNoteUid } = await createReturnAndVendorCreditNote(page, {
        supplierUid: r0.supplierAUid,
        originalSupplierInvoiceUid: invA.uid,
        itemUid: r0.itemUid,
        uomUid: r0.uomUid,
        vatGroupUid: r0.vatGroupUid,
        qty: 2,
        unitPrice: 5_000,
        restock: true,
      });
      expect(returnUid, 'vendor return uid is Crockford ULID').toMatch(ULID_RE);
      expect(creditNoteUid, 'vendor credit note uid is Crockford ULID').toMatch(ULID_RE);

      // DB: vendor return status = CREDITED.
      const returnStatus = dbQuery(`SELECT status FROM vendor_return WHERE uid='${returnUid}' LIMIT 1`);
      expect(returnStatus, 'vendor return status after credit-note issuance').toBe('CREDITED');

      // DB: credit note status = POSTED, allocatedAmount = 0 before apply.
      const cnStatusBefore = dbQuery(`SELECT status FROM vendor_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      expect(cnStatusBefore, 'vendor credit note status before apply').toBe('POSTED');
      const cnAllocBefore = dbQuery(`SELECT allocated_amount FROM vendor_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      expect(Number(cnAllocBefore), 'allocatedAmount before apply').toBe(0);

      // DB: stock on-hand reduced after post (restock=true fires a stock-OUT move).
      const onHandAfter = dbCount(
        `SELECT COALESCE(SUM(qty_change),0) FROM stock_move WHERE item_id=${r0.itemId} AND branch_id=${BRANCH_ID}`,
      );
      expect(onHandAfter, 'stock on-hand reduced after vendor return post').toBeLessThan(onHandBefore);

      // DB: VendorReturnPosted.v1 outbox event present.
      const postEvCount = dbCount(
        `SELECT COUNT(*) FROM domain_event `
        + `WHERE type='VendorReturnPosted.v1' `
        + `AND payload_json LIKE '%${returnUid}%'`,
      );
      expect(postEvCount, 'VendorReturnPosted.v1 in outbox').toBeGreaterThanOrEqual(1);

      // DB: VendorCreditNoteIssued.v1 outbox event present.
      const issuedEvCount = dbCount(
        `SELECT COUNT(*) FROM domain_event `
        + `WHERE type='VendorCreditNoteIssued.v1' `
        + `AND payload_json LIKE '%${creditNoteUid}%'`,
      );
      expect(issuedEvCount, 'VendorCreditNoteIssued.v1 in outbox').toBeGreaterThanOrEqual(1);

      // ---- Apply full credit to supplier invoice B ----
      const totalRaw = dbQuery(`SELECT total_amount FROM vendor_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      const totalAmount = Number(totalRaw);
      expect(totalAmount, 'credit note totalAmount > 0').toBeGreaterThan(0);

      const applyResp = await apiPost(
        page,
        `/api/v1/vendor-credit-notes/uid/${creditNoteUid}/apply`,
        { supplierInvoiceUid: invB.uid, amount: totalAmount } satisfies ApplyVendorCreditNoteRequest,
        { expectedStatus: 200 },
      );
      const cn = unwrap<VendorCreditNoteDto>(applyResp);

      // Contract pins on the response DTO.
      expect(cn.uid, 'response uid').toBe(creditNoteUid);
      expect(cn.uid).toMatch(ULID_RE);
      expect(cn.status, 'status after full apply').toBe('FULLY_ALLOCATED');
      expect(cn.allocatedAmount, 'allocatedAmount == totalAmount').toBe(cn.totalAmount);
      expect(cn.availableAmount, 'availableAmount == 0').toBe(0);
      expect(cn.allocations, 'allocations list is non-null').not.toBeNull();
      expect(cn.allocations?.length, 'one allocation row').toBe(1);

      const alloc = cn.allocations![0];
      expect(alloc.supplierInvoiceId, 'allocation.supplierInvoiceId is string').toEqual(expect.any(String));
      expect(alloc.amount, 'allocation.amount > 0').toBeGreaterThan(0);
      expect(alloc.allocatedAt, 'allocatedAt is ISO timestamp').toMatch(/^\d{4}-\d{2}-\d{2}/);

      // DB: credit note FULLY_ALLOCATED.
      const cnStatusAfter = dbQuery(`SELECT status FROM vendor_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      expect(cnStatusAfter, 'DB credit note status after full apply').toBe('FULLY_ALLOCATED');

      // DB: supplier invoice B paidAmount > 0; status = PAID.
      const invBStatus = dbQuery(`SELECT status FROM supplier_invoice WHERE uid='${invB.uid}' LIMIT 1`);
      expect(invBStatus, 'supplier invoice B status after apply').toBe('PAID');
      const invBPaid = dbQuery(`SELECT paid_amount FROM supplier_invoice WHERE uid='${invB.uid}' LIMIT 1`);
      expect(Number(invBPaid), 'supplier invoice B paid_amount > 0').toBeGreaterThan(0);

      // DB: allocation row in vendor_credit_note_allocation.
      const allocCount = dbCount(
        `SELECT COUNT(*) FROM vendor_credit_note_allocation vcna `
        + `JOIN vendor_credit_note vcn ON vcn.id=vcna.vendor_credit_note_id `
        + `WHERE vcn.uid='${creditNoteUid}'`,
      );
      expect(allocCount, 'allocation row in DB').toBeGreaterThanOrEqual(1);

      // DB: VendorCreditNoteApplied.v1 outbox event with required payload fields.
      const applyEvCount = dbCount(
        `SELECT COUNT(*) FROM domain_event `
        + `WHERE type='VendorCreditNoteApplied.v1' `
        + `AND payload_json LIKE '%${creditNoteUid}%' `
        + `AND payload_json LIKE '%${invB.uid}%' `
        + `AND payload_json LIKE '%TZS%'`,
      );
      expect(applyEvCount, 'VendorCreditNoteApplied.v1 in outbox').toBeGreaterThanOrEqual(1);

      // ---- UI verification + axe-core ----
      await page.goto('/procurement/vendor-returns');
      await dismissDismissableAlerts(page);

      // Credit note row must show FULLY_ALLOCATED status badge.
      const statusBadge = page.locator('[data-testid="vendor-credit-note-status"]', { hasText: 'FULLY_ALLOCATED' });
      await expect(statusBadge).toBeVisible({ timeout: 15_000 });

      // "Apply" button must NOT appear for a FULLY_ALLOCATED credit note.
      const applyBtn = page.locator('[data-testid="vendor-credit-note-apply-btn"]').first();
      await expect(applyBtn).toHaveCount(0);

      // axe-core on vendor-returns queue page.
      await assertNoSeriousA11yViolations(page, '/procurement/vendor-returns');
    },
  );
});

// =============================================================================
// 3. SCENARIO 2 — Partial apply twice → FULLY_ALLOCATED
//    Actor: procurement-officer
//    Slice H.1 — flips when /apply lands
// =============================================================================

test.describe('Slice H.1 — partial apply twice → FULLY_ALLOCATED', () => {
  test.use({ persona: 'procurement-officer' as Persona });

  // Credit note for X amount. Apply 40% to supplier invoice T1 → PARTIALLY_ALLOCATED.
  // Apply remaining 60% to supplier invoice T2 → FULLY_ALLOCATED.
  // Two allocation rows in vendor_credit_note_allocation. Both events emitted.
  test.fail(
    // Slice H.1 — flips when /apply lands
    'two partial applies exhaust the vendor credit note (POSTED → PARTIALLY_ALLOCATED → FULLY_ALLOCATED)',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // Source invoice (the one we're returning goods against).
      const invReturn = await createAndPostSupplierInvoice(page, {
        supplierPartyId: r0.supplierAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
        numberSuffix: 'PARTRET',
      });

      // Open invoice target-1 (apply 40% here).
      const invTarget1 = await createAndPostSupplierInvoice(page, {
        supplierPartyId: r0.supplierAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
        numberSuffix: 'PARTT1',
      });

      // Open invoice target-2 (apply remaining 60% here).
      const invTarget2 = await createAndPostSupplierInvoice(page, {
        supplierPartyId: r0.supplierAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
        numberSuffix: 'PARTT2',
      });

      const { creditNoteUid } = await createReturnAndVendorCreditNote(page, {
        supplierUid: r0.supplierAUid,
        originalSupplierInvoiceUid: invReturn.uid,
        itemUid: r0.itemUid,
        uomUid: r0.uomUid,
        vatGroupUid: r0.vatGroupUid,
        qty: 2,
        unitPrice: 5_000,
        restock: true,
      });

      // Resolve actual totalAmount from DB (includes VAT).
      const totalAmountRaw = dbQuery(`SELECT total_amount FROM vendor_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      const totalAmount = Number(totalAmountRaw);
      expect(totalAmount, 'vendor credit note totalAmount > 0').toBeGreaterThan(0);

      // Split 4/10 + 6/10.
      const firstAmount  = Math.round((totalAmount * 0.4) * 100) / 100;
      const secondAmount = Math.round((totalAmount - firstAmount) * 100) / 100;

      // --- First apply: POSTED → PARTIALLY_ALLOCATED ---
      const r1 = await apiPost(
        page,
        `/api/v1/vendor-credit-notes/uid/${creditNoteUid}/apply`,
        { supplierInvoiceUid: invTarget1.uid, amount: firstAmount } satisfies ApplyVendorCreditNoteRequest,
        { expectedStatus: 200 },
      );
      const cn1 = unwrap<VendorCreditNoteDto>(r1);

      expect(cn1.status, 'status after first partial apply').toBe('PARTIALLY_ALLOCATED');
      expect(cn1.allocatedAmount, 'allocatedAmount after first apply').toBeCloseTo(firstAmount, 2);
      expect(cn1.availableAmount, 'availableAmount after first apply').toBeCloseTo(secondAmount, 2);
      expect(cn1.allocations?.length, 'one allocation after first apply').toBe(1);

      // DB: PARTIALLY_ALLOCATED.
      const statusMid = dbQuery(`SELECT status FROM vendor_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      expect(statusMid, 'DB status after first apply').toBe('PARTIALLY_ALLOCATED');

      // --- Second apply: PARTIALLY_ALLOCATED → FULLY_ALLOCATED ---
      const r2 = await apiPost(
        page,
        `/api/v1/vendor-credit-notes/uid/${creditNoteUid}/apply`,
        { supplierInvoiceUid: invTarget2.uid, amount: secondAmount } satisfies ApplyVendorCreditNoteRequest,
        { expectedStatus: 200 },
      );
      const cn2 = unwrap<VendorCreditNoteDto>(r2);

      expect(cn2.status, 'status after second apply').toBe('FULLY_ALLOCATED');
      expect(cn2.allocatedAmount, 'allocatedAmount == totalAmount').toBeCloseTo(totalAmount, 2);
      expect(cn2.availableAmount, 'availableAmount == 0').toBeCloseTo(0, 2);
      expect(cn2.allocations?.length, 'two allocation rows').toBe(2);

      const allocInvoiceIds = (cn2.allocations ?? []).map(a => a.supplierInvoiceId);
      expect(allocInvoiceIds.length, 'two distinct allocation invoice ids').toBe(2);

      // DB: two VendorCreditNoteApplied.v1 events emitted.
      const evCount = dbCount(
        `SELECT COUNT(*) FROM domain_event `
        + `WHERE type='VendorCreditNoteApplied.v1' `
        + `AND payload_json LIKE '%${creditNoteUid}%'`,
      );
      expect(evCount, 'two VendorCreditNoteApplied.v1 events in outbox').toBeGreaterThanOrEqual(2);

      // DB: two allocation rows in vendor_credit_note_allocation.
      const dbAllocCount = dbCount(
        `SELECT COUNT(*) FROM vendor_credit_note_allocation vcna `
        + `JOIN vendor_credit_note vcn ON vcn.id=vcna.vendor_credit_note_id `
        + `WHERE vcn.uid='${creditNoteUid}'`,
      );
      expect(dbAllocCount, 'two allocation rows in DB').toBe(2);
    },
  );
});

// =============================================================================
// 4. SCENARIO 3 — Over-apply → 422
//    Actor: procurement-officer
//    Slice H.1 — flips when /apply lands
// =============================================================================

test.describe('Slice H.1 — over-apply guard → 422', () => {
  test.use({ persona: 'procurement-officer' as Persona });

  test.fail(
    // Slice H.1 — flips when /apply lands
    'applying more than availableAmount returns 422; UI surfaces error inline on the modal',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // Invoice to return (1 unit × 5000).
      const invReturn = await createAndPostSupplierInvoice(page, {
        supplierPartyId: r0.supplierAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
        numberSuffix: 'OVRA',
      });

      // Large open invoice — so the over-amount won't bounce on the invoice side.
      const invTarget = await createAndPostSupplierInvoice(page, {
        supplierPartyId: r0.supplierAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 10,
        unitPrice: 5_000,
        numberSuffix: 'OVRT',
      });

      const { creditNoteUid } = await createReturnAndVendorCreditNote(page, {
        supplierUid: r0.supplierAUid,
        originalSupplierInvoiceUid: invReturn.uid,
        itemUid: r0.itemUid,
        uomUid: r0.uomUid,
        vatGroupUid: r0.vatGroupUid,
        qty: 1,
        unitPrice: 5_000,
        restock: true,
      });

      const totalRaw = dbQuery(`SELECT total_amount FROM vendor_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      const overAmount = Number(totalRaw) + 1;

      // API-level: over-apply must 422.
      const apiResp = await apiPost(
        page,
        `/api/v1/vendor-credit-notes/uid/${creditNoteUid}/apply`,
        { supplierInvoiceUid: invTarget.uid, amount: overAmount } satisfies ApplyVendorCreditNoteRequest,
        { acceptStatuses: [422] },
      );
      expect(apiResp.status, 'over-apply status code').toBe(422);
      const errBody = JSON.parse(apiResp.body);
      const errMsg: string = errBody?.message ?? errBody?.errors?.[0] ?? '';
      expect(errMsg.length, 'error message is non-empty').toBeGreaterThan(0);

      // UI-level: open apply modal and verify inline error.
      await page.goto('/procurement/vendor-returns');
      await dismissDismissableAlerts(page);

      const applyBtn = page.locator('[data-testid="vendor-credit-note-apply-btn"]').first();
      await expect(applyBtn).toBeVisible({ timeout: 15_000 });
      await applyBtn.click();

      const modal = page.locator('[data-testid="vendor-credit-note-apply-modal"]');
      await expect(modal).toBeVisible({ timeout: 10_000 });

      // axe-core on the open apply modal.
      await dismissDismissableAlerts(page);
      await assertNoSeriousA11yViolations(page, '/procurement/vendor-returns — apply modal open');

      const amountInput = modal.locator('[data-testid="vendor-credit-note-apply-amount-input"]');
      await amountInput.fill(String(overAmount));
      const submitBtn = modal.locator('[data-testid="vendor-credit-note-apply-submit"]');
      await submitBtn.click();

      const inlineError = modal.locator('[data-testid="vendor-credit-note-apply-error"]');
      await expect(inlineError).toBeVisible({ timeout: 10_000 });
      await expect(inlineError).not.toBeEmpty();
    },
  );
});

// =============================================================================
// 5. SCENARIO 4 — Cross-supplier apply → 422
//    Actor: procurement-officer
//    Slice H.1 — flips when /apply lands
// =============================================================================

test.describe('Slice H.1 — cross-supplier apply guard → 422', () => {
  test.use({ persona: 'procurement-officer' as Persona });

  test.fail(
    // Slice H.1 — flips when /apply lands
    'applying vendor credit note for supplier A to an invoice of supplier B returns 422 (same-supplier constraint)',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // Invoice for supplier A (source of return + credit note).
      const invA = await createAndPostSupplierInvoice(page, {
        supplierPartyId: r0.supplierAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
        numberSuffix: 'CCA',
      });

      // Invoice for supplier B — the forbidden apply target.
      const invB = await createAndPostSupplierInvoice(page, {
        supplierPartyId: r0.supplierBId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
        numberSuffix: 'CCB',
      });

      // Credit note is issued for supplier A.
      const { creditNoteUid } = await createReturnAndVendorCreditNote(page, {
        supplierUid: r0.supplierAUid,
        originalSupplierInvoiceUid: invA.uid,
        itemUid: r0.itemUid,
        uomUid: r0.uomUid,
        vatGroupUid: r0.vatGroupUid,
        qty: 1,
        unitPrice: 5_000,
        restock: true,
      });

      const totalRaw = dbQuery(`SELECT total_amount FROM vendor_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      const applyAmount = Number(totalRaw);

      // Attempt to apply supplier A's credit note to supplier B's invoice.
      const apiResp = await apiPost(
        page,
        `/api/v1/vendor-credit-notes/uid/${creditNoteUid}/apply`,
        { supplierInvoiceUid: invB.uid, amount: applyAmount } satisfies ApplyVendorCreditNoteRequest,
        { acceptStatuses: [422] },
      );
      expect(apiResp.status, 'cross-supplier apply status code').toBe(422);

      const errBody = JSON.parse(apiResp.body);
      const errMsg: string = errBody?.message ?? errBody?.errors?.[0] ?? '';
      expect(errMsg.length, 'cross-supplier error message is non-empty').toBeGreaterThan(0);

      // DB: no allocation row was created.
      const allocCount = dbCount(
        `SELECT COUNT(*) FROM vendor_credit_note_allocation vcna `
        + `JOIN vendor_credit_note vcn ON vcn.id=vcna.vendor_credit_note_id `
        + `WHERE vcn.uid='${creditNoteUid}'`,
      );
      expect(allocCount, 'no allocation row created on cross-supplier attempt').toBe(0);
    },
  );
});

// =============================================================================
// 6. SCENARIO 5 — Apply to FULLY_ALLOCATED → 409
//    Actor: procurement-officer
//    Slice H.1 — flips when /apply lands
// =============================================================================

test.describe('Slice H.1 — apply to FULLY_ALLOCATED → 409', () => {
  test.use({ persona: 'procurement-officer' as Persona });

  test.fail(
    // Slice H.1 — flips when /apply lands
    'applying against a FULLY_ALLOCATED vendor credit note returns 409 (illegal-state)',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // Source invoice for return.
      const invReturn = await createAndPostSupplierInvoice(page, {
        supplierPartyId: r0.supplierAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
        numberSuffix: 'FULLA',
      });

      // First apply target — used to exhaust the credit note.
      const invTarget = await createAndPostSupplierInvoice(page, {
        supplierPartyId: r0.supplierAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 2,
        unitPrice: 5_000,
        numberSuffix: 'FULLT',
      });

      const { creditNoteUid } = await createReturnAndVendorCreditNote(page, {
        supplierUid: r0.supplierAUid,
        originalSupplierInvoiceUid: invReturn.uid,
        itemUid: r0.itemUid,
        uomUid: r0.uomUid,
        vatGroupUid: r0.vatGroupUid,
        qty: 1,
        unitPrice: 5_000,
        restock: true,
      });

      const totalRaw = dbQuery(`SELECT total_amount FROM vendor_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      const totalAmount = Number(totalRaw);

      // First apply — exhausts the credit note.
      await apiPost(
        page,
        `/api/v1/vendor-credit-notes/uid/${creditNoteUid}/apply`,
        { supplierInvoiceUid: invTarget.uid, amount: totalAmount } satisfies ApplyVendorCreditNoteRequest,
        { expectedStatus: 200 },
      );

      const statusFull = dbQuery(`SELECT status FROM vendor_credit_note WHERE uid='${creditNoteUid}' LIMIT 1`);
      expect(statusFull, 'credit note is FULLY_ALLOCATED after first apply').toBe('FULLY_ALLOCATED');

      // Second invoice for the re-apply attempt.
      const invTarget2 = await createAndPostSupplierInvoice(page, {
        supplierPartyId: r0.supplierAId,
        itemId: r0.itemId,
        uomId: r0.uomId,
        vatGroupId: r0.vatGroupId,
        qty: 1,
        unitPrice: 5_000,
        numberSuffix: 'FULLT2',
      });

      // Attempt to apply again — must 409.
      const apiResp = await apiPost(
        page,
        `/api/v1/vendor-credit-notes/uid/${creditNoteUid}/apply`,
        { supplierInvoiceUid: invTarget2.uid, amount: 1 } satisfies ApplyVendorCreditNoteRequest,
        { acceptStatuses: [409] },
      );
      expect(apiResp.status, 'apply-to-fully-allocated status code').toBe(409);
    },
  );
});

// =============================================================================
// 7. SCENARIO 6 — Permission 403 gate
//    Actors: sales-clerk AND cashier (neither holds PROCUREMENT.MANAGE_RETURN)
//    Slice H.1 — flips when /apply lands
// =============================================================================

test.describe('Slice H.1 — permission 403 gate (sales-clerk)', () => {
  test.use({ persona: 'sales-clerk' as Persona });

  test.fail(
    // Slice H.1 — flips when /apply lands
    'sales-clerk (no PROCUREMENT.MANAGE_RETURN) gets 403 on all vendor-return + credit-note endpoints',
    async ({ page }) => {
      await page.goto('/dashboard');

      // POST /vendor-returns → 403.
      const createResp = await apiPost(
        page,
        '/api/v1/vendor-returns',
        {
          supplierUid: '00000000000000000000000000',
          returnDate: TODAY,
          reason: 'DAMAGED',
          restock: false,
          lines: [],
        },
        { acceptStatuses: [403] },
      );
      expect(createResp.status, 'sales-clerk POST /vendor-returns → 403').toBe(403);

      // GET /vendor-returns → 403.
      const listResp = await apiGet(
        page,
        `/api/v1/vendor-returns?branchId=${BRANCH_ID}&page=0&size=10`,
        { acceptStatuses: [403] },
      );
      expect(listResp.status, 'sales-clerk GET /vendor-returns → 403').toBe(403);

      // GET /vendor-credit-notes → 403.
      const cnListResp = await apiGet(
        page,
        `/api/v1/vendor-credit-notes?branchId=${BRANCH_ID}`,
        { acceptStatuses: [403] },
      );
      expect(cnListResp.status, 'sales-clerk GET /vendor-credit-notes → 403').toBe(403);

      // POST /vendor-returns/uid/{uid}/post → 403.
      const existingReturnUid = dbQuery(`SELECT uid FROM vendor_return LIMIT 1`);
      if (existingReturnUid !== '') {
        const postResp = await apiPost(
          page,
          `/api/v1/vendor-returns/uid/${existingReturnUid}/post`,
          {},
          { acceptStatuses: [403] },
        );
        expect(postResp.status, 'sales-clerk POST /vendor-returns post → 403').toBe(403);

        const issueResp = await apiPost(
          page,
          `/api/v1/vendor-returns/uid/${existingReturnUid}/issue-credit-note`,
          { cnDate: TODAY },
          { acceptStatuses: [403] },
        );
        expect(issueResp.status, 'sales-clerk POST issue-credit-note → 403').toBe(403);
      }

      // POST /vendor-credit-notes/uid/{uid}/apply → 403.
      const existingCnUid = dbQuery(`SELECT uid FROM vendor_credit_note LIMIT 1`);
      if (existingCnUid !== '') {
        const applyResp = await apiPost(
          page,
          `/api/v1/vendor-credit-notes/uid/${existingCnUid}/apply`,
          { supplierInvoiceUid: '00000000000000000000000000', amount: 1 },
          { acceptStatuses: [403] },
        );
        expect(applyResp.status, 'sales-clerk POST /apply → 403').toBe(403);
      }

      // UI: /procurement/vendor-returns must show no "Apply" buttons for this persona.
      await page.goto('/procurement/vendor-returns');
      await dismissDismissableAlerts(page);
      await expect(
        page.locator('[data-testid="vendor-credit-note-apply-btn"]'),
      ).toHaveCount(0, { timeout: 10_000 });
    },
  );
});

test.describe('Slice H.1 — permission 403 gate (cashier)', () => {
  test.use({ persona: 'cashier' as Persona });

  test.fail(
    // Slice H.1 — flips when /apply lands
    'cashier (POS.* only, no PROCUREMENT.MANAGE_RETURN) gets 403 on all vendor-return + credit-note endpoints',
    async ({ page }) => {
      await page.goto('/dashboard');

      // POST /vendor-returns → 403.
      const createResp = await apiPost(
        page,
        '/api/v1/vendor-returns',
        {
          supplierUid: '00000000000000000000000000',
          returnDate: TODAY,
          reason: 'DAMAGED',
          restock: false,
          lines: [],
        },
        { acceptStatuses: [403] },
      );
      expect(createResp.status, 'cashier POST /vendor-returns → 403').toBe(403);

      // GET /vendor-returns → 403.
      const listResp = await apiGet(
        page,
        `/api/v1/vendor-returns?branchId=${BRANCH_ID}&page=0&size=10`,
        { acceptStatuses: [403] },
      );
      expect(listResp.status, 'cashier GET /vendor-returns → 403').toBe(403);

      // GET /vendor-credit-notes → 403.
      const cnListResp = await apiGet(
        page,
        `/api/v1/vendor-credit-notes?branchId=${BRANCH_ID}`,
        { acceptStatuses: [403] },
      );
      expect(cnListResp.status, 'cashier GET /vendor-credit-notes → 403').toBe(403);

      // POST /apply (any credit note uid) → 403.
      const existingCnUid = dbQuery(`SELECT uid FROM vendor_credit_note LIMIT 1`);
      if (existingCnUid !== '') {
        const applyResp = await apiPost(
          page,
          `/api/v1/vendor-credit-notes/uid/${existingCnUid}/apply`,
          { supplierInvoiceUid: '00000000000000000000000000', amount: 1 },
          { acceptStatuses: [403] },
        );
        expect(applyResp.status, 'cashier POST /apply → 403').toBe(403);
      }

      // UI: no Apply buttons visible for cashier.
      await page.goto('/procurement/vendor-returns');
      await dismissDismissableAlerts(page);
      await expect(
        page.locator('[data-testid="vendor-credit-note-apply-btn"]'),
      ).toHaveCount(0, { timeout: 10_000 });
    },
  );
});

// =============================================================================
// 8. SCENARIO 7 — Cancel path: DRAFT → cancel → post returns 409
//    Actor: procurement-officer
//    Slice H.1 — flips when cancel endpoint + state machine land
// =============================================================================

test.describe('Slice H.1 — cancel path: DRAFT cancel blocks subsequent post', () => {
  test.use({ persona: 'procurement-officer' as Persona });

  test.fail(
    // Slice H.1 — flips when /cancel + state guard land
    'DRAFT vendor return cancelled; subsequent POST /post returns 409 (illegal state transition)',
    async ({ page }) => {
      const r0 = requireRefs();
      await page.goto('/dashboard');

      // Create a DRAFT vendor return (do NOT post it).
      const returnBody: CreateVendorReturnRequest = {
        supplierUid: r0.supplierAUid,
        returnDate: TODAY,
        reason: 'OTHER',
        restock: false,
        notes: `e2e cancel test ${RUN_TAG}`,
        lines: [{
          itemUid: r0.itemUid,
          uomUid: r0.uomUid,
          returnedQty: 1,
          unitPrice: 1_000,
          vatGroupUid: r0.vatGroupUid,
        }],
      };
      const draftR = await apiPost(page, '/api/v1/vendor-returns', returnBody);
      const rDto = unwrap<VendorReturnDto>(draftR);
      expect(rDto.uid, 'draft return uid is Crockford ULID').toMatch(ULID_RE);
      expect(rDto.status, 'initial status is DRAFT').toBe('DRAFT');

      // DB: status=DRAFT.
      const statusDraft = dbQuery(`SELECT status FROM vendor_return WHERE uid='${rDto.uid}' LIMIT 1`);
      expect(statusDraft, 'DB status before cancel').toBe('DRAFT');

      // Cancel the DRAFT.
      await apiPost(
        page,
        `/api/v1/vendor-returns/uid/${rDto.uid}/cancel`,
        {},
        { expectedStatus: 200 },
      );

      // DB: status=CANCELLED (or equivalent cancel terminal state).
      const statusCancelled = dbQuery(`SELECT status FROM vendor_return WHERE uid='${rDto.uid}' LIMIT 1`);
      expect(
        ['CANCELLED', 'CANCELED', 'VOID'].includes(statusCancelled),
        `DB status after cancel should be a cancel terminal state; got: ${statusCancelled}`,
      ).toBe(true);

      // Attempt to post the cancelled return — must 409.
      const postResp = await apiPost(
        page,
        `/api/v1/vendor-returns/uid/${rDto.uid}/post`,
        {},
        { acceptStatuses: [409] },
      );
      expect(postResp.status, 'post after cancel returns 409').toBe(409);
    },
  );
});
