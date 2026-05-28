/**
 * Slice H.1 — VendorReturnCreateComponent unit spec.
 *
 * Uses the real child components (SupplierTypeaheadComponent,
 * GrnPickerModalComponent) with a mocked ProcurementService so no real HTTP
 * calls are made.  Drives the host component via its public API (signal-based
 * methods) and asserts:
 *   - supplier is set via onSupplierSelected (not a raw uid text input)
 *   - GRN pre-fill uses uid fields from the picked GRN
 *   - submitted payload carries the picked partyUid
 *   - form guards (no supplier, no valid lines) produce error signals
 */
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { VendorReturnCreateComponent } from './vendor-return-create.component';
import { ProcurementService } from './procurement.service';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import {
  CreateVendorReturnRequest,
  Grn,
  VendorReturn,
} from './procurement.models';
import { SupplierSelectedEvent } from './supplier-typeahead.component';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const SUPPLIER_EVT: SupplierSelectedEvent = {
  partyUid: '01JSUP00000000000001',
  id: '10',
  code: 'SUP-001',
  name: 'Acme Supplies',
};

const POSTED_GRN: Grn = {
  id: '50', uid: '01JGRN00000000000001',
  number: 'GRN-001', companyId: '1', branchId: '10',
  supplierId: '10', lpoOrderId: null,
  receivedDate: '2026-05-20', supplierDeliveryNote: null,
  subtotalAmount: 100000, taxAmount: 18000, totalAmount: 118000,
  status: 'POSTED', postedAt: null, postedBy: null,
  notes: null, cancellationReason: null,
  lines: [
    {
      id: '501', lpoOrderLineId: null,
      itemId: '9', itemUid: '01JITM00000000000001',
      itemName: 'Widget A', itemCode: 'WGT-001',
      uomId: '3', uomUid: '01JUOM00000000000001', uomCode: 'PCS',
      receivedQty: 10, unitCost: 10000,
      vatGroupId: '2', vatGroupUid: '01JVAT00000000000001', vatGroupName: 'Standard',
      lineTotal: 100000, batchNo: null, expiryDate: null,
    },
  ],
};

const CREATED_RETURN: VendorReturn = {
  id: '1', uid: '01JVR000000000000001',
  number: 'VR-001',
  supplierId: '10', supplierUid: SUPPLIER_EVT.partyUid,
  originalGrnId: null, originalGrnNumber: null,
  originalSupplierInvoiceId: null,
  returnDate: '2026-05-28', reason: 'DAMAGED',
  restock: true, totalAmount: 100000,
  status: 'DRAFT', postedAt: null, notes: null,
  lines: [],
};

function makeSupplierPage(): Page<{ id: string; partyUid: string; code: string; name: string }> {
  return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
}

function makeGrnPage(grns: Grn[]): Page<Grn> {
  return { content: grns, page: 0, size: 50, totalElements: grns.length, totalPages: 1 };
}

// ---------------------------------------------------------------------------
// Setup helper
// ---------------------------------------------------------------------------

async function setup() {
  const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', [
    'createVendorReturn', 'postVendorReturn', 'listGrns', 'searchSuppliers',
  ]);
  procSpy.createVendorReturn.and.returnValue(of(CREATED_RETURN));
  procSpy.postVendorReturn.and.returnValue(of({ ...CREATED_RETURN, status: 'POSTED' }));
  procSpy.listGrns.and.returnValue(of(makeGrnPage([POSTED_GRN])));
  procSpy.searchSuppliers.and.returnValue(of(makeSupplierPage()));

  const authStub = {
    currentUser: signal(null), permissions: signal([]),
    isAuthenticated: signal(false), hasPermission: () => true,
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

    it('renders the supplier typeahead (no raw uid text input)', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('orbix-supplier-typeahead')).toBeTruthy();
      // The old raw text input must NOT be present.
      expect(fixture.nativeElement.querySelector('input#supplierUid')).toBeNull();
    });

    it('renders the reason select with expected options', async () => {
      const { fixture } = await setup();
      const select: HTMLSelectElement = fixture.nativeElement.querySelector('select#reason');
      expect(select).toBeTruthy();
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

  // -- Supplier typeahead wiring -----------------------------------------------
  describe('supplier typeahead wiring', () => {
    it('onSupplierSelected stores the supplier signal', async () => {
      const { fixture, comp } = await setup();
      comp.onSupplierSelected(SUPPLIER_EVT);
      await stabilise(fixture);

      const c = comp as unknown as { selectedSupplier: () => SupplierSelectedEvent | null };
      expect(c.selectedSupplier()).toEqual(SUPPLIER_EVT);
    });

    it('onSupplierCleared resets selectedSupplier to null', async () => {
      const { fixture, comp } = await setup();
      comp.onSupplierSelected(SUPPLIER_EVT);
      comp.onSupplierCleared();
      await stabilise(fixture);

      const c = comp as unknown as { selectedSupplier: () => SupplierSelectedEvent | null };
      expect(c.selectedSupplier()).toBeNull();
    });
  });

  // -- Validation -------------------------------------------------------------------
  describe('form validation — supplier required', () => {
    it('sets an error when saveDraft is called without a supplier', async () => {
      const { fixture, comp } = await setup();
      await stabilise(fixture);

      comp.saveDraft();
      await stabilise(fixture);

      const c = comp as unknown as { error: () => string | null };
      expect(c.error()).toContain('Supplier is required');
    });

    it('sets an error when no valid lines are present', async () => {
      const { fixture, comp } = await setup();
      comp.onSupplierSelected(SUPPLIER_EVT);
      const c = comp as unknown as {
        lines: Array<{ itemUid: string; uomUid: string; returnedQty: number | null; unitPrice: number | null; vatGroupUid: string; fromGrn: boolean }>;
      };
      c.lines = [{ itemUid: '', uomUid: '', returnedQty: null, unitPrice: null, vatGroupUid: '', fromGrn: false }];
      await stabilise(fixture);

      comp.saveDraft();
      await stabilise(fixture);

      const e = comp as unknown as { error: () => string | null };
      expect(e.error()).toContain('at least one valid line');
    });
  });

  // -- GRN picker wiring ----------------------------------------------------------
  describe('GRN picker wiring', () => {
    it('onGrnSelected pre-fills lines from GRN uid fields', async () => {
      const { fixture, comp } = await setup();
      comp.onSupplierSelected(SUPPLIER_EVT);
      comp.onGrnSelected(POSTED_GRN);
      await stabilise(fixture);

      const c = comp as unknown as {
        lines: Array<{ itemUid: string; uomUid: string; vatGroupUid: string; returnedQty: number | null; fromGrn: boolean }>;
        selectedGrn: () => Grn | null;
      };
      expect(c.selectedGrn()?.uid).toBe(POSTED_GRN.uid);
      expect(c.lines.length).toBe(1);
      expect(c.lines[0].itemUid).toBe('01JITM00000000000001');
      expect(c.lines[0].uomUid).toBe('01JUOM00000000000001');
      expect(c.lines[0].vatGroupUid).toBe('01JVAT00000000000001');
      expect(c.lines[0].returnedQty).toBe(10);
      expect(c.lines[0].fromGrn).toBeTrue();
    });

    it('clearGrn resets lines and selectedGrn', async () => {
      const { fixture, comp } = await setup();
      comp.onSupplierSelected(SUPPLIER_EVT);
      comp.onGrnSelected(POSTED_GRN);
      comp.clearGrn();
      await stabilise(fixture);

      const c = comp as unknown as {
        lines: unknown[];
        selectedGrn: () => Grn | null;
      };
      expect(c.selectedGrn()).toBeNull();
      expect(c.lines.length).toBe(1);
    });
  });

  // -- Submit payload shape --------------------------------------------------------
  describe('submit posts correct payload', () => {
    it('saveDraft sends supplierUid from the picked supplier (not typed text)', async () => {
      const { fixture, comp, procSpy } = await setup();
      comp.onSupplierSelected(SUPPLIER_EVT);
      const c = comp as unknown as {
        lines: Array<{ itemUid: string; uomUid: string; returnedQty: number; unitPrice: number; vatGroupUid: string; fromGrn: boolean }>;
      };
      c.lines = [{
        itemUid: '01JITM00000000000001',
        uomUid: '01JUOM00000000000001',
        returnedQty: 5,
        unitPrice: 9000,
        vatGroupUid: '01JVAT00000000000001',
        fromGrn: false,
      }];
      await stabilise(fixture);

      comp.saveDraft();
      await stabilise(fixture);

      const payload = procSpy.createVendorReturn.calls.mostRecent().args[0] as CreateVendorReturnRequest;
      expect(payload.supplierUid).toBe(SUPPLIER_EVT.partyUid);
      expect(payload.lines[0].itemUid).toBe('01JITM00000000000001');
      expect(payload.lines[0].uomUid).toBe('01JUOM00000000000001');
      expect(payload.lines[0].returnedQty).toBe(5);
      expect(payload.lines[0].unitPrice).toBe(9000);
    });

    it('saveDraft includes originalGrnUid when a GRN was picked', async () => {
      const { fixture, comp, procSpy } = await setup();
      comp.onSupplierSelected(SUPPLIER_EVT);
      comp.onGrnSelected(POSTED_GRN);
      await stabilise(fixture);

      comp.saveDraft();
      await stabilise(fixture);

      const payload = procSpy.createVendorReturn.calls.mostRecent().args[0] as CreateVendorReturnRequest;
      expect(payload.originalGrnUid).toBe(POSTED_GRN.uid);
    });

    it('saveAndPost calls postVendorReturn after create', async () => {
      const { fixture, comp, procSpy } = await setup();
      comp.onSupplierSelected(SUPPLIER_EVT);
      comp.onGrnSelected(POSTED_GRN);
      await stabilise(fixture);

      comp.saveAndPost();
      await stabilise(fixture);

      expect(procSpy.createVendorReturn).toHaveBeenCalled();
      expect(procSpy.postVendorReturn).toHaveBeenCalledWith(CREATED_RETURN.uid);
    });
  });

  // -- Line management -------------------------------------------------------------
  describe('line management (no-GRN path)', () => {
    it('addLine appends a blank row', async () => {
      const { comp } = await setup();
      const before = (comp as unknown as { lines: unknown[] }).lines.length;
      comp.addLine();
      expect((comp as unknown as { lines: unknown[] }).lines.length).toBe(before + 1);
    });

    it('removeLine removes a row', async () => {
      const { comp } = await setup();
      comp.addLine();
      const before = (comp as unknown as { lines: unknown[] }).lines.length;
      comp.removeLine(0);
      expect((comp as unknown as { lines: unknown[] }).lines.length).toBe(before - 1);
    });

    it('removeLine keeps at least one row when last is removed', async () => {
      const { comp } = await setup();
      const lines = (comp as unknown as { lines: unknown[] }).lines;
      while (lines.length > 1) comp.removeLine(0);
      comp.removeLine(0);
      expect((comp as unknown as { lines: unknown[] }).lines.length).toBe(1);
    });
  });
});
