import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { StockMoversComponent } from './stock-movers.component';
import { ReportExportService } from './report-export.service';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { ApiResponse } from '../../core/api/api-response';
import { ItemMovementRow } from './reports.models';

function envelope<T>(data: T): ApiResponse<T> {
  return { status: true, statusCode: 200, responseCode: 'OK', message: 'OK', errors: [], data };
}

function mockRow(overrides: Partial<ItemMovementRow> = {}): ItemMovementRow {
  return {
    itemId: '10', itemCode: 'SKU-001', itemName: 'Widget A',
    movedQty: 500, qtyOnHand: 20,
    ...overrides,
  };
}

function flushBoth(http: HttpTestingController, fastData: ItemMovementRow[], slowData: ItemMovementRow[]): void {
  http.expectOne(r => r.url.includes('stock-fast-movers')).flush(envelope(fastData));
  http.expectOne(r => r.url.includes('stock-slow-movers')).flush(envelope(slowData));
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
describe('StockMoversComponent (STOCK.COUNT granted)', () => {
  let fixture: ComponentFixture<StockMoversComponent>;
  let http: HttpTestingController;
  let exportService: jasmine.SpyObj<ReportExportService>;

  beforeEach(async () => {
    exportService = jasmine.createSpyObj('ReportExportService', ['exportCsv', 'exportExcel', 'exportPdf']);
    exportService.exportCsv.and.returnValue(undefined);
    exportService.exportExcel.and.returnValue(Promise.resolve());
    exportService.exportPdf.and.returnValue(Promise.resolve());

    await TestBed.configureTestingModule({
      imports: [StockMoversComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        { provide: ReportExportService, useValue: exportService },
        { provide: AuthService, useValue: makeAuthSpy(true) },
        { provide: BranchService, useValue: branchStub },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StockMoversComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges(); // ngOnInit → fetchBoth()
  });

  afterEach(() => {
    http.match(r => r.url.includes('stock-fast-movers')).forEach(r => r.flush(envelope([])));
    http.match(r => r.url.includes('stock-slow-movers')).forEach(r => r.flush(envelope([])));
    // BranchPickerComponent fires GET /branches on init — flush any open request.
    http.match(r => r.url.includes('/branches')).forEach(r => r.flush({ status: true, statusCode: 200, responseCode: 'OK', message: 'OK', errors: [], data: { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } }));
    http.verify();
  });

  it('shows loading spinners while both requests are in flight', () => {
    expect(fixture.nativeElement.querySelectorAll('.spinner-border').length).toBeGreaterThanOrEqual(1);
    flushBoth(http, [], []);
  });

  it('renders fast-movers rows in the fast tab', fakeAsync(() => {
    flushBoth(http, [mockRow(), mockRow({ itemId: '11', movedQty: 300 })], []);
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('#panel-fast tbody tr').length).toBe(2);
  }));

  it('renders slow-movers rows after switching to slow tab', fakeAsync(() => {
    flushBoth(http, [], [mockRow({ itemId: '20', movedQty: 1 })]);
    tick();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('#tab-slow').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('#panel-slow tbody tr').length).toBe(1);
  }));

  it('shows empty state element when fast movers returns empty', fakeAsync(() => {
    flushBoth(http, [], []);
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="fast-empty"]')).toBeTruthy();
  }));

  it('shows error for fast movers on HTTP failure', fakeAsync(() => {
    http.expectOne(r => r.url.includes('stock-fast-movers'))
      .flush({ message: 'Server error', errors: [] }, { status: 500, statusText: 'Error' });
    http.expectOne(r => r.url.includes('stock-slow-movers')).flush(envelope([]));
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.alert-danger').length).toBeGreaterThanOrEqual(1);
  }));

  it('toggleMoveType adds and removes types while keeping at least one', fakeAsync(() => {
    flushBoth(http, [], []);
    tick();

    const comp = fixture.componentInstance;
    expect((comp as any).selectedMoveTypes()).toContain('SALE');

    (comp as any).toggleMoveType('GRN');
    expect((comp as any).selectedMoveTypes()).toContain('GRN');

    (comp as any).toggleMoveType('SALE');
    expect((comp as any).selectedMoveTypes()).not.toContain('SALE');

    // Attempt to remove last entry — must be blocked
    (comp as any).toggleMoveType('GRN');
    expect((comp as any).selectedMoveTypes().length).toBe(1);
    expect((comp as any).selectedMoveTypes()).toContain('GRN');
  }));

  it('buildFastExport and buildSlowExport have distinct titles', fakeAsync(() => {
    flushBoth(http, [mockRow()], [mockRow({ itemId: '99', movedQty: 1 })]);
    tick();
    fixture.detectChanges();

    const fastExp = fixture.componentInstance.buildFastExport();
    const slowExp = fixture.componentInstance.buildSlowExport();
    expect(fastExp.title).toBe('Fast Movers');
    expect(slowExp.title).toBe('Slow Movers');
    expect(fastExp.rows[0]['rank']).toBe(1);
  }));

  it('sends multiple moveTypes as repeated query params', fakeAsync(() => {
    flushBoth(http, [], []);
    tick();

    (fixture.componentInstance as any).toggleMoveType('RETURN_OUT');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    tick();

    const fastReq = http.expectOne(r => r.url.includes('stock-fast-movers'));
    expect(fastReq.request.urlWithParams).toContain('moveTypes=SALE');
    expect(fastReq.request.urlWithParams).toContain('moveTypes=RETURN_OUT');
    fastReq.flush(envelope([]));
    http.expectOne(r => r.url.includes('stock-slow-movers')).flush(envelope([]));
    tick();
  }));
});

// ---------------------------------------------------------------------------
// Suite B — STOCK.COUNT denied
// ---------------------------------------------------------------------------
describe('StockMoversComponent (STOCK.COUNT denied)', () => {
  let fixture: ComponentFixture<StockMoversComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StockMoversComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        { provide: ReportExportService, useValue: jasmine.createSpyObj('ReportExportService', ['exportCsv']) },
        { provide: AuthService, useValue: makeAuthSpy(false) },
        { provide: BranchService, useValue: branchStub },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StockMoversComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.match(r => r.url.includes('/branches')).forEach(r => r.flush({ status: true, statusCode: 200, responseCode: 'OK', message: 'OK', errors: [], data: { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } }));
    http.verify();
  });

  it('shows permission-denied state', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="report-permission-state"]')).toBeTruthy();
  });
});
