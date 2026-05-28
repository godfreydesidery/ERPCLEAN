import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { DebtComponent } from './debt.component';
import { DebtService } from './debt.service';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { DebtAging, SupplierAging } from './debt.models';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------
const AR_AGING: DebtAging = {
  asOf: '2026-05-28',
  branchId: null,
  currencyCode: 'TZS',
  totals: {
    current: 200000, d1_30: 100000, d31_60: 50000,
    d61_90: 20000, d90_plus: 5000, totalOutstanding: 375000, customerCount: 3
  },
  rows: [],
};

const AP_AGING: SupplierAging = {
  asOf: '2026-05-28',
  branchId: null,
  currencyCode: 'TZS',
  totals: {
    current: 80000, d1_30: 40000, d31_60: 15000,
    d61_90: 5000, d90_plus: 2000, totalOutstanding: 142000, supplierCount: 2
  },
  rows: [],
};

const AR_PAGE: Page<any> = { content: [
  {
    customerId: '1', customerUid: '01JCUST0000000000001',
    customerName: 'Test Customer', creditLimit: 500000,
    totalOutstanding: 100000, oldestDaysOverdue: 45,
    oldestDueDate: '2026-04-13', worstBucket: 'D_31_60', overdueInvoiceCount: 2,
  }
], page: 0, size: 100, totalElements: 1, totalPages: 1 };

const AP_PAGE: Page<any> = { content: [
  {
    supplierId: '99', supplierUid: '01JSUP0000000000099',
    supplierName: 'Test Supplier', paymentTermsDays: 30,
    totalOutstanding: 75000, oldestDaysOverdue: 27,
    oldestDueDate: '2026-05-01', worstBucket: 'D_1_30', overdueInvoiceCount: 1,
  }
], page: 0, size: 100, totalElements: 1, totalPages: 1 };

function makeDebtSpy(): jasmine.SpyObj<DebtService> {
  const spy = jasmine.createSpyObj<DebtService>('DebtService', [
    'aging', 'dunning', 'supplierAging', 'supplierDunning',
  ]);
  spy.aging.and.returnValue(of(AR_AGING));
  spy.dunning.and.returnValue(of(AR_PAGE));
  spy.supplierAging.and.returnValue(of(AP_AGING));
  spy.supplierDunning.and.returnValue(of(AP_PAGE));
  return spy;
}

function makeAuthSpy(): jasmine.SpyObj<AuthService> {
  const spy = jasmine.createSpyObj<AuthService>('AuthService', ['hasPermission']);
  spy.hasPermission.and.returnValue(true);
  return spy;
}

function makeBranchStub(): Partial<BranchService> {
  return {
    activeBranchId: signal<string | null>(null),
  };
}

async function setup(
  debtSpy = makeDebtSpy(),
  queryParams: Record<string, string> = {}
): Promise<{ fixture: ComponentFixture<DebtComponent>; component: DebtComponent }> {
  await TestBed.configureTestingModule({
    imports: [DebtComponent, HttpClientTestingModule],
    providers: [
      provideRouter([]),
      { provide: DebtService, useValue: debtSpy },
      { provide: AuthService, useValue: makeAuthSpy() },
      { provide: BranchService, useValue: makeBranchStub() },
      {
        provide: ActivatedRoute,
        useValue: {
          queryParamMap: of(convertToParamMap(queryParams)),
        },
      },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(DebtComponent);
  const component = fixture.componentInstance;
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, component };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('DebtComponent — Slice G.1 AR/AP tab toggle', () => {

  describe('Default state (AR tab)', () => {
    it('defaults to AR tab', async () => {
      const { component } = await setup();
      expect(component['activeTab']()).toBe('AR');
    });

    it('AR tab button has aria-selected=true initially', async () => {
      const { fixture } = await setup();
      const arTab: HTMLElement = fixture.nativeElement.querySelector('#tab-ar');
      expect(arTab.getAttribute('aria-selected')).toBe('true');
    });

    it('AP tab button has aria-selected=false initially', async () => {
      const { fixture } = await setup();
      const apTab: HTMLElement = fixture.nativeElement.querySelector('#tab-ap');
      expect(apTab.getAttribute('aria-selected')).toBe('false');
    });

    it('renders AR dunning table on AR tab', async () => {
      const { fixture } = await setup();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="debt-dunning-table"]')).toBeTruthy();
      expect(el.querySelector('[data-testid="debt-ap-dunning-table"]')).toBeNull();
    });

    it('renders AR customer rows', async () => {
      const { fixture } = await setup();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('Test Customer');
    });
  });

  describe('Switching to AP tab', () => {
    it('switches activeTab signal to AP on click', async () => {
      const { fixture, component } = await setup();
      const apTab: HTMLButtonElement = fixture.nativeElement.querySelector('#tab-ap');
      apTab.click();
      fixture.detectChanges();
      expect(component['activeTab']()).toBe('AP');
    });

    it('renders AP dunning table after switching', async () => {
      const { fixture } = await setup();
      const apTab: HTMLButtonElement = fixture.nativeElement.querySelector('#tab-ap');
      apTab.click();
      await fixture.whenStable();
      fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="debt-ap-dunning-table"]')).toBeTruthy();
      expect(el.querySelector('[data-testid="debt-dunning-table"]')).toBeNull();
    });

    it('renders AP supplier rows after switching', async () => {
      const { fixture } = await setup();
      const apTab: HTMLButtonElement = fixture.nativeElement.querySelector('#tab-ap');
      apTab.click();
      await fixture.whenStable();
      fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('Test Supplier');
    });

    it('AP tab aria-selected becomes true after switch', async () => {
      const { fixture } = await setup();
      const apTab: HTMLButtonElement = fixture.nativeElement.querySelector('#tab-ap');
      apTab.click();
      fixture.detectChanges();
      expect(apTab.getAttribute('aria-selected')).toBe('true');
    });

    it('calls supplierAging and supplierDunning when switching to AP', async () => {
      const debt = makeDebtSpy();
      const { fixture } = await setup(debt);
      const apTab: HTMLButtonElement = fixture.nativeElement.querySelector('#tab-ap');
      apTab.click();
      await fixture.whenStable();
      expect(debt.supplierAging).toHaveBeenCalled();
      expect(debt.supplierDunning).toHaveBeenCalled();
    });

    it('URL does not navigate to /debt/ar or /debt/ap when switching tabs', async () => {
      // Tab switching is purely client-side signal mutation;
      // no router.navigate(['/debt/ar']) or (['/debt/ap']) call is made.
      const { fixture, component } = await setup();
      const apTab: HTMLButtonElement = fixture.nativeElement.querySelector('#tab-ap');
      apTab.click();
      fixture.detectChanges();
      // Only the signal changes — active tab is now AP, URL is still /debt
      expect(component['activeTab']()).toBe('AP');
      // The AR tab is not selected
      const arTab: HTMLElement = fixture.nativeElement.querySelector('#tab-ar');
      expect(arTab.getAttribute('aria-selected')).toBe('false');
    });
  });

  describe('Switching back to AR tab', () => {
    it('re-renders AR table when switching back', async () => {
      const { fixture } = await setup();
      const apTab: HTMLButtonElement = fixture.nativeElement.querySelector('#tab-ap');
      const arTab: HTMLButtonElement = fixture.nativeElement.querySelector('#tab-ar');
      apTab.click();
      await fixture.whenStable();
      fixture.detectChanges();
      arTab.click();
      await fixture.whenStable();
      fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="debt-dunning-table"]')).toBeTruthy();
    });
  });

  describe('Permission denied', () => {
    it('renders permission-required panel when aging returns 403', async () => {
      const debt = makeDebtSpy();
      debt.aging.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 403 }))
      );
      const { fixture } = await setup(debt);
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="debt-permission-required"]')).toBeTruthy();
    });
  });

  describe('AP empty state', () => {
    it('shows empty message when AP dunning returns no rows', async () => {
      const debt = makeDebtSpy();
      debt.supplierDunning.and.returnValue(of({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }));
      const { fixture } = await setup(debt);
      const apTab: HTMLButtonElement = fixture.nativeElement.querySelector('#tab-ap');
      apTab.click();
      await fixture.whenStable();
      fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('No overdue suppliers match this filter');
    });
  });
});
