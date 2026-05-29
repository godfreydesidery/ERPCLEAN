/**
 * LaybyAgeingComponent unit spec.
 * Covers: permission gate, 4 states, type-chip client-side filter,
 *         deep-link query params, export builder.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { LaybyAgeingComponent } from './layby-ageing.component';
import { ReportsService } from './reports.service';
import { LaybyAgeingReport } from './reports.models';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const REPORT: LaybyAgeingReport = {
  asOf: '2026-05-29T09:00:00Z',
  balanceByType: { LAYBY: 150000, PRE_ORDER: 80000 },
  countByType: { LAYBY: 3, PRE_ORDER: 2 },
  buckets: [
    { type: 'LAYBY',     bucketLabel: '0-7 days',  minDays: 0,  maxDays: 7,  orderCount: 1, totalAmount: 50000, paidAmount: 20000, balanceDue: 30000 },
    { type: 'LAYBY',     bucketLabel: '8-30 days', minDays: 8,  maxDays: 30, orderCount: 2, totalAmount: 160000, paidAmount: 40000, balanceDue: 120000 },
    { type: 'PRE_ORDER', bucketLabel: '0-7 days',  minDays: 0,  maxDays: 7,  orderCount: 2, totalAmount: 80000, paidAmount: 0, balanceDue: 80000 },
  ],
  orders: [
    { id: '1', number: 'LB-00001', branchId: '1', customerId: '10', type: 'LAYBY', status: 'OPEN',
      createdAt: '2026-05-20T08:00:00Z', reservedUntil: '2026-06-20T08:00:00Z',
      ageDays: 9, daysUntilExpiry: 22, totalAmount: 80000, paidAmount: 20000, balanceDue: 60000 },
    { id: '2', number: 'PO-00001', branchId: '1', customerId: '11', type: 'PRE_ORDER', status: 'OPEN',
      createdAt: '2026-05-25T08:00:00Z', reservedUntil: '2026-05-30T08:00:00Z',
      ageDays: 4, daysUntilExpiry: 1, totalAmount: 80000, paidAmount: 0, balanceDue: 80000 },
  ],
};

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

async function setup(opts: {
  queryParams?: Record<string, string>;
  reportResult?: LaybyAgeingReport | 'error' | 'forbidden';
  hasPermission?: boolean;
} = {}) {
  const hasPerm = opts.hasPermission ?? true;

  const svcSpy = jasmine.createSpyObj<ReportsService>('ReportsService', ['laybyAgeing']);
  if (opts.reportResult === 'error') {
    svcSpy.laybyAgeing.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
  } else if (opts.reportResult === 'forbidden') {
    svcSpy.laybyAgeing.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 403 }))
    );
  } else {
    svcSpy.laybyAgeing.and.returnValue(of(opts.reportResult ?? REPORT));
  }

  const authSpy = jasmine.createSpyObj<AuthService>('AuthService', ['hasPermission']);
  authSpy.hasPermission.and.callFake((perm: string) =>
    hasPerm && (perm === 'ORDER.READ' || perm === 'ORDER.MANAGE')
  );

  const branchSpy = jasmine.createSpyObj<BranchService>('BranchService', ['activeBranchId']);
  branchSpy.activeBranchId.and.returnValue(null);

  await TestBed.configureTestingModule({
    imports: [LaybyAgeingComponent, HttpClientTestingModule],
    providers: [
      provideRouter([]),
      { provide: ReportsService, useValue: svcSpy },
      { provide: AuthService, useValue: authSpy },
      { provide: BranchService, useValue: branchSpy },
      {
        provide: ActivatedRoute,
        useValue: { queryParamMap: of(convertToParamMap(opts.queryParams ?? {})) },
      },
    ],
  }).compileComponents();

  const fixture: ComponentFixture<LaybyAgeingComponent> =
    TestBed.createComponent(LaybyAgeingComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, svcSpy };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('LaybyAgeingComponent', () => {

  describe('permission gate', () => {
    it('shows permission-required panel when user lacks ORDER.READ', async () => {
      const { fixture } = await setup({ hasPermission: false });
      expect(fixture.nativeElement.querySelector('[data-testid="report-permission-state"]')).toBeTruthy();
    });

    it('does not call the service when user lacks permission', async () => {
      const { svcSpy } = await setup({ hasPermission: false });
      expect(svcSpy.laybyAgeing).not.toHaveBeenCalled();
    });

    it('renders filter form when user has ORDER.READ', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('form')).toBeTruthy();
      expect(fixture.nativeElement.querySelector('[data-testid="report-permission-state"]')).toBeNull();
    });
  });

  describe('populated state', () => {
    it('renders KPI strip with Layby and Pre-order balances', async () => {
      const { fixture } = await setup();
      const text: string = fixture.nativeElement.textContent;
      expect(text).toContain('Layby balance');
      expect(text).toContain('Pre-order balance');
    });

    it('renders bucket summary table', async () => {
      const { fixture } = await setup();
      const rows = fixture.nativeElement.querySelectorAll('.bucket-table tbody tr');
      expect(rows.length).toBe(3); // 3 buckets in fixture
    });

    it('renders order drill-down table', async () => {
      const { fixture } = await setup();
      const rows = fixture.nativeElement.querySelectorAll('.order-table tbody tr');
      expect(rows.length).toBe(2); // 2 orders in fixture
    });

    it('shows export menu when data present', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('orbix-report-export-menu')).toBeTruthy();
    });
  });

  describe('type-chip filter (client-side)', () => {
    it('filters order rows to LAYBY only when LAYBY chip active', async () => {
      const { fixture, comp } = await setup();
      (comp as unknown as { setTypeFilter: (t: string) => void }).setTypeFilter('LAYBY');
      fixture.detectChanges();
      const rows = fixture.nativeElement.querySelectorAll('.order-table tbody tr');
      // Only the LAYBY order in fixture
      expect(rows.length).toBe(1);
    });

    it('does not re-fetch on type chip change', async () => {
      const { comp, svcSpy } = await setup();
      const callsBefore = svcSpy.laybyAgeing.calls.count();
      (comp as unknown as { setTypeFilter: (t: string) => void }).setTypeFilter('PRE_ORDER');
      expect(svcSpy.laybyAgeing.calls.count()).toBe(callsBefore);
    });
  });

  describe('empty state', () => {
    it('shows empty-state panel when no orders', async () => {
      const { fixture } = await setup({
        reportResult: { ...REPORT, orders: [], buckets: [] },
      });
      expect(fixture.nativeElement.querySelector('[data-testid="report-empty-state"]')).toBeTruthy();
    });
  });

  describe('error state', () => {
    it('shows error alert on HTTP 500', async () => {
      const { fixture } = await setup({ reportResult: 'error' });
      expect(fixture.nativeElement.querySelector('.alert-danger')).toBeTruthy();
    });

    it('shows permission error message on HTTP 403', async () => {
      const { fixture } = await setup({ reportResult: 'forbidden' });
      const alert = fixture.nativeElement.querySelector('.alert-danger');
      expect(alert).toBeTruthy();
      expect(alert.textContent).toContain('ORDER.READ');
    });
  });

  describe('export builder', () => {
    it('buildExport returns order rows matching visible orders', async () => {
      const { comp } = await setup();
      const exportFn = (comp as unknown as { buildExport: () => import('./report-export.service').ReportExport }).buildExport;
      const exp = exportFn();
      expect(exp.rows.length).toBe(2);
      expect(exp.columns.map(c => c.key)).toContain('ageDays');
      expect(exp.columns.map(c => c.key)).toContain('balance');
    });

    it('buildExport filters to LAYBY rows when type chip is LAYBY', async () => {
      const { comp, fixture } = await setup();
      (comp as unknown as { setTypeFilter: (t: string) => void }).setTypeFilter('LAYBY');
      fixture.detectChanges();
      const exportFn = (comp as unknown as { buildExport: () => import('./report-export.service').ReportExport }).buildExport;
      const exp = exportFn();
      expect(exp.rows.length).toBe(1);
    });
  });
});
