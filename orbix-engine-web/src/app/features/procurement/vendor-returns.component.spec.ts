/**
 * Slice H.1 — VendorReturnsComponent unit spec.
 * Covers: tab switch, status pills, Apply button gating (status + perm),
 *         onCreditNoteApplied row patching, cnStatusClass / cnStatusLabel helpers.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { VendorReturnsComponent } from './vendor-returns.component';
import { ProcurementService } from './procurement.service';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { VendorCreditNote, VendorReturn } from './procurement.models';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

function makePage(items: VendorReturn[]): Page<VendorReturn> {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

const BASE_RETURN: VendorReturn = {
  id: '1', uid: '01JTEST00000000000VR1',
  number: 'VR-001',
  supplierId: '10', supplierUid: '01JTEST00000000000SUP',
  originalGrnId: null, originalGrnNumber: null,
  originalSupplierInvoiceId: null,
  returnDate: '2026-05-28', reason: 'DAMAGED',
  restock: true, totalAmount: 120000,
  status: 'POSTED',
  postedAt: '2026-05-28T10:00:00Z',
  notes: null, lines: [],
};

const CN_POSTED: VendorCreditNote = {
  id: '201', uid: '01JTEST00000000000VCN1',
  number: 'VCN-001',
  supplierId: '10', supplierUid: '01JTEST00000000000SUP',
  vendorReturnId: '1',
  cnDate: '2026-05-28', currencyCode: 'TZS',
  totalAmount: 120000, allocatedAmount: 0, availableAmount: 120000,
  status: 'POSTED', notes: null, allocations: null,
};

const CN_PARTIAL: VendorCreditNote = {
  ...CN_POSTED, uid: '01JTEST00000000000VCN2', id: '202',
  allocatedAmount: 50000, availableAmount: 70000,
  status: 'PARTIALLY_ALLOCATED',
};

const CN_FULL: VendorCreditNote = {
  ...CN_POSTED, uid: '01JTEST00000000000VCN3', id: '203',
  allocatedAmount: 120000, availableAmount: 0,
  status: 'FULLY_ALLOCATED',
};

// ---------------------------------------------------------------------------
// Setup helper
// ---------------------------------------------------------------------------

async function setup(opts: {
  creditNotes?: VendorCreditNote[];
  hasManageReturn?: boolean;
} = {}) {
  const creditNotes = opts.creditNotes ?? [CN_POSTED];
  const hasManageReturn = opts.hasManageReturn ?? true;

  const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', [
    'listVendorReturns', 'listVendorCreditNotes',
    'getSupplierOpenInvoices', 'applyVendorCreditNote',
    'postVendorReturn', 'cancelVendorReturn', 'issueVendorCreditNote',
  ]);
  procSpy.listVendorReturns.and.returnValue(of(makePage([BASE_RETURN])));
  procSpy.listVendorCreditNotes.and.returnValue(of(creditNotes));

  const authStub = {
    currentUser: signal(null),
    permissions: signal([] as string[]),
    isAuthenticated: signal(false),
    hasPermission: (code: string) => code === 'PROCUREMENT.MANAGE_RETURN' ? hasManageReturn : false,
  } as unknown as AuthService;

  const branchStub = {
    activeBranchId: signal<string | null>('10'),
  } as unknown as BranchService;

  await TestBed.configureTestingModule({
    imports: [VendorReturnsComponent, HttpClientTestingModule],
    providers: [
      provideRouter([]),
      { provide: ProcurementService, useValue: procSpy },
      { provide: AuthService, useValue: authStub },
      { provide: BranchService, useValue: branchStub },
    ],
  }).compileComponents();

  const fixture: ComponentFixture<VendorReturnsComponent> =
    TestBed.createComponent(VendorReturnsComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, procSpy };
}

/** Switch to the Credit notes tab and re-stabilise. */
async function switchToCreditNotesTab(
  fixture: ComponentFixture<VendorReturnsComponent>
): Promise<void> {
  const tabs: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('[role="tab"]');
  const cnTab = Array.from(tabs).find(t => t.textContent?.includes('Credit notes'));
  expect(cnTab).withContext('Credit notes tab must be present').toBeTruthy();
  cnTab!.click();
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('VendorReturnsComponent', () => {

  // -- Tab switching -----------------------------------------------------------
  describe('tab switching', () => {
    it('starts on the Returns tab', async () => {
      const { fixture } = await setup();
      const activeTab = fixture.nativeElement.querySelector('[role="tab"].active');
      expect(activeTab?.textContent).toContain('Returns');
    });

    it('switches to Credit notes tab on click', async () => {
      const { fixture } = await setup();
      await switchToCreditNotesTab(fixture);
      const activeTab = fixture.nativeElement.querySelector('[role="tab"].active');
      expect(activeTab?.textContent).toContain('Credit notes');
    });

    it('renders the credit notes table after switching tab', async () => {
      const { fixture } = await setup({ creditNotes: [CN_POSTED] });
      await switchToCreditNotesTab(fixture);
      expect(fixture.nativeElement.querySelector('table')).toBeTruthy();
      expect(fixture.nativeElement.textContent).toContain('VCN-001');
    });
  });

  // -- Status pills ------------------------------------------------------------
  describe('status pills on returns tab', () => {
    it('renders a status pill for the return', async () => {
      const { fixture } = await setup();
      const pill = fixture.nativeElement.querySelector('.status-badge--posted');
      expect(pill).toBeTruthy();
    });

    it('DRAFT return shows draft pill', async () => {
      const draftReturn: VendorReturn = { ...BASE_RETURN, status: 'DRAFT', postedAt: null };
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', [
        'listVendorReturns', 'listVendorCreditNotes',
      ]);
      procSpy.listVendorReturns.and.returnValue(of(makePage([draftReturn])));
      procSpy.listVendorCreditNotes.and.returnValue(of([]));
      const authStub = {
        currentUser: signal(null), permissions: signal([]), isAuthenticated: signal(false),
        hasPermission: () => true,
      } as unknown as AuthService;
      const branchStub = { activeBranchId: signal<string | null>('10') } as unknown as BranchService;

      await TestBed.configureTestingModule({
        imports: [VendorReturnsComponent, HttpClientTestingModule],
        providers: [
          provideRouter([]),
          { provide: ProcurementService, useValue: procSpy },
          { provide: AuthService, useValue: authStub },
          { provide: BranchService, useValue: branchStub },
        ],
      }).compileComponents();

      const fixture = TestBed.createComponent(VendorReturnsComponent);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.status-badge--draft')).toBeTruthy();
    });
  });

  // -- Apply button on credit-notes tab ----------------------------------------
  describe('Apply button gating (credit notes tab)', () => {
    it('shows Apply for POSTED credit note when user has perm', async () => {
      const { fixture } = await setup({ creditNotes: [CN_POSTED] });
      await switchToCreditNotesTab(fixture);
      const btns: NodeListOf<HTMLButtonElement> =
        fixture.nativeElement.querySelectorAll('button[aria-label*="Apply credit note"]');
      expect(btns.length).toBe(1);
    });

    it('shows Apply for PARTIALLY_ALLOCATED credit note', async () => {
      const { fixture } = await setup({ creditNotes: [CN_PARTIAL] });
      await switchToCreditNotesTab(fixture);
      const btns: NodeListOf<HTMLButtonElement> =
        fixture.nativeElement.querySelectorAll('button[aria-label*="Apply credit note"]');
      expect(btns.length).toBe(1);
    });

    it('hides Apply for FULLY_ALLOCATED credit note', async () => {
      const { fixture } = await setup({ creditNotes: [CN_FULL] });
      await switchToCreditNotesTab(fixture);
      const btns: NodeListOf<HTMLButtonElement> =
        fixture.nativeElement.querySelectorAll('button[aria-label*="Apply credit note"]');
      expect(btns.length).toBe(0);
    });

    it('hides Apply when user lacks PROCUREMENT.MANAGE_RETURN', async () => {
      const { fixture } = await setup({ creditNotes: [CN_POSTED], hasManageReturn: false });
      await switchToCreditNotesTab(fixture);
      const btns: NodeListOf<HTMLButtonElement> =
        fixture.nativeElement.querySelectorAll('button[aria-label*="Apply credit note"]');
      expect(btns.length).toBe(0);
    });
  });

  // -- Apply modal opening ------------------------------------------------------
  describe('Apply button opens the modal', () => {
    it('sets applyModalOpen to true when Apply is clicked', async () => {
      const { fixture, comp } = await setup({ creditNotes: [CN_POSTED] });
      await switchToCreditNotesTab(fixture);
      const applyBtn: HTMLButtonElement =
        fixture.nativeElement.querySelector('button[aria-label*="Apply credit note"]');
      expect(applyBtn).toBeTruthy();

      applyBtn.click();
      fixture.detectChanges();

      const c = comp as unknown as { applyModalOpen: () => boolean };
      expect(c.applyModalOpen()).toBe(true);
    });
  });

  // -- onCreditNoteApplied ------------------------------------------------------
  describe('onCreditNoteApplied', () => {
    it('patches the credit-note row in the list', async () => {
      const { fixture, comp } = await setup({ creditNotes: [CN_POSTED] });
      await switchToCreditNotesTab(fixture);

      const updated: VendorCreditNote = {
        ...CN_POSTED, allocatedAmount: 70000, availableAmount: 50000,
        status: 'PARTIALLY_ALLOCATED',
      };
      comp.onCreditNoteApplied(updated);
      fixture.detectChanges();

      const c = comp as unknown as { creditNotes: () => VendorCreditNote[] };
      const patched = c.creditNotes().find(cn => cn.uid === CN_POSTED.uid);
      expect(patched?.status).toBe('PARTIALLY_ALLOCATED');
      expect(patched?.availableAmount).toBe(50000);
    });
  });

  // -- cnStatusClass + cnStatusLabel helpers ------------------------------------
  describe('cnStatusClass', () => {
    it('returns cn-posted for POSTED', async () => {
      const { comp } = await setup();
      expect(comp.cnStatusClass('POSTED')).toBe('cn-posted');
    });
    it('returns cn-partially-allocated for PARTIALLY_ALLOCATED', async () => {
      const { comp } = await setup();
      expect(comp.cnStatusClass('PARTIALLY_ALLOCATED')).toBe('cn-partially-allocated');
    });
    it('returns cn-fully-allocated for FULLY_ALLOCATED', async () => {
      const { comp } = await setup();
      expect(comp.cnStatusClass('FULLY_ALLOCATED')).toBe('cn-fully-allocated');
    });
  });

  describe('cnStatusLabel', () => {
    it('returns Posted for POSTED', async () => {
      const { comp } = await setup();
      expect(comp.cnStatusLabel('POSTED')).toBe('Posted');
    });
    it('returns Partial for PARTIALLY_ALLOCATED', async () => {
      const { comp } = await setup();
      expect(comp.cnStatusLabel('PARTIALLY_ALLOCATED')).toBe('Partial');
    });
    it('returns Allocated for FULLY_ALLOCATED', async () => {
      const { comp } = await setup();
      expect(comp.cnStatusLabel('FULLY_ALLOCATED')).toBe('Allocated');
    });
  });
});
