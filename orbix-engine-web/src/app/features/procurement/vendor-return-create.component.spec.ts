/**
 * Slice H.1 — VendorReturnCreateComponent unit spec.
 * Covers: form validation (empty supplier / empty lines),
 *         GRN pre-fill, correct payload shape on submit.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { VendorReturnCreateComponent } from './vendor-return-create.component';
import { ProcurementService } from './procurement.service';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { CreateVendorReturnRequest, Grn, VendorReturn } from './procurement.models';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const POSTED_GRN: Grn = {
  id: '50', uid: '01JTEST00000000000GRN1',
  number: 'GRN-001', companyId: '1', branchId: '10',
  supplierId: '10', lpoOrderId: null,
  receivedDate: '2026-05-20', supplierDeliveryNote: null,
  subtotalAmount: 100000, taxAmount: 18000, totalAmount: 118000,
  status: 'POSTED', postedAt: '2026-05-20T08:00:00Z', postedBy: 'admin',
  notes: null, cancellationReason: null,
  lines: [
    {
      id: '501', lpoOrderLineId: null,
      itemId: '01JTEST00000000000ITM1', uomId: '01JTEST00000000000UOM1',
      receivedQty: 10, unitCost: 10000, vatGroupId: '01JTEST00000000000VAT1',
      lineTotal: 100000, batchNo: null, expiryDate: null,
    }
  ],
};

function makeGrnPage(grns: Grn[]): Page<Grn> {
  return { content: grns, page: 0, size: 200, totalElements: grns.length, totalPages: 1 };
}

const CREATED_RETURN: VendorReturn = {
  id: '1', uid: '01JTEST00000000000VR1',
  number: 'VR-001',
  supplierId: '10', supplierUid: '01JTEST00000000000SUP',
  originalGrnId: null, originalGrnNumber: null,
  originalSupplierInvoiceId: null,
  returnDate: '2026-05-28', reason: 'DAMAGED',
  restock: true, totalAmount: 100000,
  status: 'DRAFT', postedAt: null, notes: null,
  lines: [],
};

// ---------------------------------------------------------------------------
// Setup helper
// ---------------------------------------------------------------------------

async function setup() {
  const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', [
    'createVendorReturn', 'postVendorReturn', 'listGrns',
  ]);
  procSpy.createVendorReturn.and.returnValue(of(CREATED_RETURN));
  procSpy.postVendorReturn.and.returnValue(of({ ...CREATED_RETURN, status: 'POSTED' }));
  procSpy.listGrns.and.returnValue(of(makeGrnPage([POSTED_GRN])));

  const authStub = {
    currentUser: signal(null), permissions: signal([]), isAuthenticated: signal(false),
    hasPermission: () => true,
  } as unknown as AuthService;
  const branchStub = { activeBranchId: signal<string | null>('10') } as unknown as BranchService;

  await TestBed.configureTestingModule({
    imports: [VendorReturnCreateComponent, HttpClientTestingModule],
    providers: [
      provideRouter([]),
      { provide: ProcurementService, useValue: procSpy },
      { provide: AuthService, useValue: authStub },
      { provide: BranchService, useValue: branchStub },
    ],
  }).compileComponents();

  const fixture: ComponentFixture<VendorReturnCreateComponent> =
    TestBed.createComponent(VendorReturnCreateComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, procSpy };
}

async function stabilise(fixture: ComponentFixture<unknown>): Promise<void> {
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('VendorReturnCreateComponent', () => {

  // -- Rendering --------------------------------------------------------------
  describe('rendering', () => {
    it('renders the form', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('form')).toBeTruthy();
    });

    it('renders the supplier UID input', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('input#supplierUid')).toBeTruthy();
    });

    it('renders the reason select with correct options', async () => {
      const { fixture } = await setup();
      const select: HTMLSelectElement = fixture.nativeElement.querySelector('select#reason');
      expect(select).toBeTruthy();
      // Angular [ngValue] on string options sets value to "N: VALUE"; use textContent instead.
      const text = Array.from(select.options).map(o => o.textContent?.trim() ?? '');
      expect(text).toContain('DAMAGED');
      expect(text).toContain('WRONG_ITEM');
      expect(text).toContain('EXPIRED');
      expect(text).toContain('OTHER');
    });

    it('renders at least one line row by default', async () => {
      const { fixture } = await setup();
      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      expect(rows.length).toBeGreaterThanOrEqual(1);
    });
  });

  // -- Validation -------------------------------------------------------------
  describe('form validation — supplier required', () => {
    it('sets an error when Save draft is clicked without a supplier UID', async () => {
      const { fixture, comp } = await setup();
      // ensure supplier UID is blank
      const c = comp as unknown as { supplierUid: string };
      c.supplierUid = '';
      await stabilise(fixture);

      comp.saveDraft();
      await stabilise(fixture);

      const c2 = comp as unknown as { error: () => string | null };
      expect(c2.error()).toContain('Supplier UID is required');
    });

    it('sets an error when no valid lines are present', async () => {
      const { fixture, comp } = await setup();
      const c = comp as unknown as {
        supplierUid: string;
        lines: Array<{ itemUid: string; uomUid: string; returnedQty: number | null; unitPrice: number | null; vatGroupUid: string }>;
      };
      c.supplierUid = '01JTEST00000000000SUP';
      c.lines = [{ itemUid: '', uomUid: '', returnedQty: null, unitPrice: null, vatGroupUid: '' }];
      await stabilise(fixture);

      comp.saveDraft();
      await stabilise(fixture);

      const c2 = comp as unknown as { error: () => string | null };
      expect(c2.error()).toContain('at least one valid line');
    });
  });

  // -- GRN pre-fill -----------------------------------------------------------
  describe('GRN pre-fill', () => {
    it('loads GRNs when onSupplierSet is called', async () => {
      const { fixture, comp, procSpy } = await setup();
      const c = comp as unknown as { supplierUid: string };
      c.supplierUid = '01JTEST00000000000SUP';

      comp.onSupplierSet();
      await stabilise(fixture);

      expect(procSpy.listGrns).toHaveBeenCalled();
      const grnsSignal = (comp as unknown as { grns: () => Grn[] }).grns;
      expect(grnsSignal().length).toBe(1);
      expect(grnsSignal()[0].uid).toBe(POSTED_GRN.uid);
    });

    it('pre-fills lines from the selected GRN', async () => {
      const { fixture, comp } = await setup();
      const c = comp as unknown as { supplierUid: string };
      c.supplierUid = '01JTEST00000000000SUP';

      comp.onSupplierSet();
      await stabilise(fixture);

      comp.onGrnSelected(POSTED_GRN.uid);
      await stabilise(fixture);

      const lines = (comp as unknown as { lines: Array<{ returnedQty: number | null }> }).lines;
      expect(lines.length).toBe(1);
      expect(lines[0].returnedQty).toBe(10);
    });
  });

  // -- Submit payload shape ---------------------------------------------------
  describe('submit posts correct payload', () => {
    it('calls createVendorReturn with correct supplier + reason', async () => {
      const { fixture, comp, procSpy } = await setup();
      const c = comp as unknown as {
        supplierUid: string;
        reason: string;
        restock: boolean;
        returnDate: string;
        lines: Array<{ itemUid: string; uomUid: string; returnedQty: number; unitPrice: number; vatGroupUid: string }>;
      };
      c.supplierUid = '01JTEST00000000000SUP';
      c.reason = 'WRONG_ITEM';
      c.restock = false;
      c.returnDate = '2026-05-28';
      c.lines = [{
        itemUid: '01JTEST00000000000ITM1',
        uomUid: '01JTEST00000000000UOM1',
        returnedQty: 5,
        unitPrice: 9000,
        vatGroupUid: '01JTEST00000000000VAT1',
      }];
      await stabilise(fixture);

      comp.saveDraft();
      await stabilise(fixture);

      const calls = procSpy.createVendorReturn.calls.mostRecent();
      const payload = calls.args[0] as CreateVendorReturnRequest;
      expect(payload.supplierUid).toBe('01JTEST00000000000SUP');
      expect(payload.reason).toBe('WRONG_ITEM');
      expect(payload.restock).toBe(false);
      expect(payload.lines.length).toBe(1);
      expect(payload.lines[0].returnedQty).toBe(5);
      expect(payload.lines[0].unitPrice).toBe(9000);
    });

    it('calls postVendorReturn after create when saveDraft(true)', async () => {
      const { fixture, comp, procSpy } = await setup();
      const c = comp as unknown as {
        supplierUid: string;
        lines: Array<{ itemUid: string; uomUid: string; returnedQty: number; unitPrice: number; vatGroupUid: string }>;
      };
      c.supplierUid = '01JTEST00000000000SUP';
      c.lines = [{
        itemUid: '01JTEST00000000000ITM1',
        uomUid: '01JTEST00000000000UOM1',
        returnedQty: 3,
        unitPrice: 5000,
        vatGroupUid: '01JTEST00000000000VAT1',
      }];
      await stabilise(fixture);

      comp.saveDraft(true);
      await stabilise(fixture);

      expect(procSpy.createVendorReturn).toHaveBeenCalled();
      expect(procSpy.postVendorReturn).toHaveBeenCalledWith(CREATED_RETURN.uid);
    });
  });

  // -- Add / remove lines -----------------------------------------------------
  describe('line management', () => {
    it('adds a line on addLine()', async () => {
      const { comp } = await setup();
      const before = (comp as unknown as { lines: unknown[] }).lines.length;
      comp.addLine();
      expect((comp as unknown as { lines: unknown[] }).lines.length).toBe(before + 1);
    });

    it('removes a line on removeLine(i)', async () => {
      const { comp } = await setup();
      comp.addLine(); // ensure 2 lines
      const before = (comp as unknown as { lines: unknown[] }).lines.length;
      comp.removeLine(0);
      expect((comp as unknown as { lines: unknown[] }).lines.length).toBe(before - 1);
    });

    it('keeps at least one line when last line is removed', async () => {
      const { comp } = await setup();
      const lines = (comp as unknown as { lines: unknown[] }).lines;
      // Remove until one remains
      while (lines.length > 1) comp.removeLine(0);
      comp.removeLine(0);
      expect((comp as unknown as { lines: unknown[] }).lines.length).toBe(1);
    });
  });
});
