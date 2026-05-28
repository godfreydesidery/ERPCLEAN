/**
 * Slice H — light extension spec for ReturnsComponent.
 * Covers: Apply button visibility gating (status + perm),
 *         onCreditNoteApplied row patching, cnStatusClass helper.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { ReturnsComponent } from './returns.component';
import { SalesService } from './sales.service';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { CustomerCreditNote, CustomerReturn } from './sales.models';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

function makePage(items: CustomerReturn[]): Page<CustomerReturn> {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

const BASE_RETURN: CustomerReturn = {
  id: '1', uid: '01JTEST00000000000RET',
  number: 'RET-001', companyId: '1', branchId: '10', customerId: '42',
  originalInvoiceId: null, returnDate: '2026-05-01',
  reason: 'DAMAGED', totalAmount: 50000, status: 'CREDITED',
  restock: true, postedAt: '2026-05-01T10:00:00Z', postedBy: 'admin',
  notes: null, lines: [],
};

const CN_POSTED: CustomerCreditNote = {
  id: '201', uid: '01JTEST00000000000CN1',
  number: 'CN-001', companyId: '1', branchId: '10',
  customerId: '42', customerName: 'Acme Ltd',
  customerReturnId: '1',   // matches BASE_RETURN.id
  cnDate: '2026-05-28', currencyCode: 'TZS',
  totalAmount: 50000, allocatedAmount: 0, availableAmount: 50000,
  status: 'POSTED', notes: null, allocations: null,
};

const CN_PARTIAL: CustomerCreditNote = {
  ...CN_POSTED, uid: '01JTEST00000000000CN2', id: '202',
  allocatedAmount: 20000, availableAmount: 30000,
  status: 'PARTIALLY_ALLOCATED',
};

const CN_FULL: CustomerCreditNote = {
  ...CN_POSTED, uid: '01JTEST00000000000CN3', id: '203',
  allocatedAmount: 50000, availableAmount: 0,
  status: 'FULLY_ALLOCATED',
};

// ---------------------------------------------------------------------------
// Setup helper
// ---------------------------------------------------------------------------

async function setup(opts: {
  creditNotes?: CustomerCreditNote[];
  hasManageReturn?: boolean;
} = {}) {
  const creditNotes = opts.creditNotes ?? [CN_POSTED];
  const hasManageReturn = opts.hasManageReturn ?? true;

  const salesSpy = jasmine.createSpyObj<SalesService>('SalesService', [
    'listReturns', 'listCreditNotes',
    'getOpenInvoicesForCustomer', 'applyCreditNote',
  ]);
  salesSpy.listReturns.and.returnValue(of(makePage([BASE_RETURN])));
  salesSpy.listCreditNotes.and.returnValue(of(creditNotes));

  // AuthService and BranchService expose Angular signals as readonly properties —
  // createSpyObj's property-bag approach won't satisfy the Signal type. Use a
  // plain object cast instead.
  const authStub = {
    currentUser: signal(null),
    permissions: signal([] as string[]),
    isAuthenticated: signal(false),
    hasPermission: (code: string) => code === 'SALES.MANAGE_RETURN' ? hasManageReturn : false,
  } as unknown as AuthService;

  const branchStub = {
    activeBranchId: signal<string | null>('10'),
  } as unknown as BranchService;

  await TestBed.configureTestingModule({
    imports: [ReturnsComponent, HttpClientTestingModule],
    providers: [
      provideRouter([]),
      { provide: SalesService, useValue: salesSpy },
      { provide: AuthService, useValue: authStub },
      { provide: BranchService, useValue: branchStub },
    ],
  }).compileComponents();

  const fixture: ComponentFixture<ReturnsComponent> =
    TestBed.createComponent(ReturnsComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  // Simulate selecting the return so the credit-note card renders.
  fixture.componentInstance.select(BASE_RETURN);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, salesSpy };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('ReturnsComponent — Slice H credit-note Apply button', () => {

  describe('Apply button visibility by status', () => {
    it('shows Apply for POSTED credit note when user has SALES.MANAGE_RETURN', async () => {
      const { fixture } = await setup({ creditNotes: [CN_POSTED] });
      const btns: NodeListOf<HTMLButtonElement> =
        fixture.nativeElement.querySelectorAll('button[aria-label*="Apply credit note"]');
      expect(btns.length).toBe(1);
    });

    it('shows Apply for PARTIALLY_ALLOCATED credit note', async () => {
      const { fixture } = await setup({ creditNotes: [CN_PARTIAL] });
      const btns: NodeListOf<HTMLButtonElement> =
        fixture.nativeElement.querySelectorAll('button[aria-label*="Apply credit note"]');
      expect(btns.length).toBe(1);
    });

    it('hides Apply for FULLY_ALLOCATED credit note', async () => {
      const { fixture } = await setup({ creditNotes: [CN_FULL] });
      const btns: NodeListOf<HTMLButtonElement> =
        fixture.nativeElement.querySelectorAll('button[aria-label*="Apply credit note"]');
      expect(btns.length).toBe(0);
    });
  });

  describe('Apply button visibility by permission', () => {
    it('hides Apply when user lacks SALES.MANAGE_RETURN', async () => {
      const { fixture } = await setup({ creditNotes: [CN_POSTED], hasManageReturn: false });
      const btns: NodeListOf<HTMLButtonElement> =
        fixture.nativeElement.querySelectorAll('button[aria-label*="Apply credit note"]');
      expect(btns.length).toBe(0);
    });
  });

  describe('Apply button opens the modal', () => {
    it('sets applyModalOpen to true when Apply is clicked', async () => {
      const { fixture, comp } = await setup({ creditNotes: [CN_POSTED] });
      const applyBtn: HTMLButtonElement =
        fixture.nativeElement.querySelector('button[aria-label*="Apply credit note"]');
      expect(applyBtn).toBeTruthy();

      applyBtn.click();
      fixture.detectChanges();

      const c = comp as unknown as { applyModalOpen: () => boolean };
      expect(c.applyModalOpen()).toBe(true);
    });
  });

  describe('onCreditNoteApplied', () => {
    it('patches the credit-note row in the list without full reload', async () => {
      const { fixture, comp } = await setup({ creditNotes: [CN_POSTED] });

      const updatedCn: CustomerCreditNote = {
        ...CN_POSTED, allocatedAmount: 30000, availableAmount: 20000,
        status: 'PARTIALLY_ALLOCATED',
      };
      comp.onCreditNoteApplied(updatedCn);
      fixture.detectChanges();

      const c = comp as unknown as { creditNotes: () => CustomerCreditNote[] };
      const patched = c.creditNotes().find(cn => cn.uid === CN_POSTED.uid);
      expect(patched?.status).toBe('PARTIALLY_ALLOCATED');
      expect(patched?.availableAmount).toBe(20000);
    });
  });

  describe('cnStatusClass helper', () => {
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

    it('returns cn-cancelled for CANCELLED', async () => {
      const { comp } = await setup();
      expect(comp.cnStatusClass('CANCELLED')).toBe('cn-cancelled');
    });
  });
});
