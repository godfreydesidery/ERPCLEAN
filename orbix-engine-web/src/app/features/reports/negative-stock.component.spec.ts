import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { NegativeStockComponent } from './negative-stock.component';
import { ReportExportService } from './report-export.service';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { ApiResponse } from '../../core/api/api-response';
import { ItemBranchBalance } from './reports.models';

function envelope<T>(data: T): ApiResponse<T> {
  return { status: true, statusCode: 200, responseCode: 'OK', message: 'OK', errors: [], data };
}

function mockBalance(overrides: Partial<ItemBranchBalance> = {}): ItemBranchBalance {
  return {
    itemId: '42', branchId: '1', qtyOnHand: -5,
    qtyReserved: null, qtyInTransit: null,
    avgCost: null, lastCost: null, reorderMin: null, reorderMax: null,
    binLocation: null, lastMovedAt: '2026-05-01T10:00:00Z',
    ...overrides,
  };
}

function makeAuthSpy(hasPerm: boolean) {
  const spy = jasmine.createSpyObj<AuthService>('AuthService', ['hasPermission'], {
    currentUser: signal({ id: '1', username: 'test', displayName: 'Test', defaultCompanyId: '1', defaultBranchId: '1', mustChangePassword: false }),
    permissions: signal(hasPerm ? ['STOCK.COUNT'] : []),
  });
  spy.hasPermission.and.callFake((code: string) => hasPerm && code === 'STOCK.COUNT');
  return spy;
}

const branchStub = { activeBranchId: signal<string | null>('1') };

// ---------------------------------------------------------------------------
// Suite A — STOCK.COUNT granted
// ---------------------------------------------------------------------------
describe('NegativeStockComponent (STOCK.COUNT granted)', () => {
  let fixture: ComponentFixture<NegativeStockComponent>;
  let http: HttpTestingController;
  let exportService: jasmine.SpyObj<ReportExportService>;

  beforeEach(async () => {
    exportService = jasmine.createSpyObj('ReportExportService', ['exportCsv', 'exportExcel', 'exportPdf']);
    exportService.exportCsv.and.returnValue(undefined);
    exportService.exportExcel.and.returnValue(Promise.resolve());
    exportService.exportPdf.and.returnValue(Promise.resolve());

    await TestBed.configureTestingModule({
      imports: [NegativeStockComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        { provide: ReportExportService, useValue: exportService },
        { provide: AuthService, useValue: makeAuthSpy(true) },
        { provide: BranchService, useValue: branchStub },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NegativeStockComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges(); // ngOnInit → fetch()
  });

  afterEach(() => {
    http.match(r => r.url.includes('stock-negative')).forEach(r => r.flush(envelope([])));
    http.verify();
  });

  it('shows loading spinner while request is in flight', () => {
    expect(fixture.nativeElement.querySelector('.spinner-border')).toBeTruthy();
    http.expectOne(r => r.url.includes('stock-negative')).flush(envelope([]));
  });

  it('shows empty state when response is empty array', fakeAsync(() => {
    http.expectOne(r => r.url.includes('stock-negative')).flush(envelope([]));
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="report-empty-state"]')).toBeTruthy();
  }));

  it('renders a row for each negative balance', fakeAsync(() => {
    http.expectOne(r => r.url.includes('stock-negative'))
      .flush(envelope([mockBalance(), mockBalance({ itemId: '43', qtyOnHand: -2 })]));
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
  }));

  it('applies text-danger to negative on-hand cells', fakeAsync(() => {
    http.expectOne(r => r.url.includes('stock-negative')).flush(envelope([mockBalance()]));
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.text-danger')).toBeTruthy();
  }));

  it('shows error alert on HTTP 500', fakeAsync(() => {
    http.expectOne(r => r.url.includes('stock-negative'))
      .flush({ message: 'Server error', errors: [] }, { status: 500, statusText: 'Error' });
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.alert-danger')).toBeTruthy();
  }));

  it('navigates to /reports/stock-card on drillThrough()', fakeAsync(() => {
    http.expectOne(r => r.url.includes('stock-negative')).flush(envelope([mockBalance()]));
    tick();
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

    (fixture.componentInstance as any).drillThrough(mockBalance());
    expect(router.navigate).toHaveBeenCalledWith(
      ['/reports/stock-card'],
      { queryParams: { itemId: '42', branchId: '1' } }
    );
  }));

  it('buildExport() maps rows correctly', fakeAsync(() => {
    http.expectOne(r => r.url.includes('stock-negative')).flush(envelope([mockBalance()]));
    tick();
    fixture.detectChanges();

    const exp = fixture.componentInstance.buildExport();
    expect(exp.title).toBe('Negative Stock');
    expect(exp.rows.length).toBe(1);
    expect(exp.rows[0]['qtyOnHand']).toBe(-5);
  }));
});

// ---------------------------------------------------------------------------
// Suite B — STOCK.COUNT denied
// ---------------------------------------------------------------------------
describe('NegativeStockComponent (STOCK.COUNT denied)', () => {
  let fixture: ComponentFixture<NegativeStockComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NegativeStockComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        { provide: ReportExportService, useValue: jasmine.createSpyObj('ReportExportService', ['exportCsv']) },
        { provide: AuthService, useValue: makeAuthSpy(false) },
        { provide: BranchService, useValue: branchStub },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NegativeStockComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows permission-denied state', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="report-permission-state"]')).toBeTruthy();
  });
});
