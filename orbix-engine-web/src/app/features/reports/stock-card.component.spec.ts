import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { StockCardComponent } from './stock-card.component';
import { ReportExportService } from './report-export.service';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { ProcurementService } from '../procurement/procurement.service';
import { ApiResponse } from '../../core/api/api-response';
import { StockMove } from './reports.models';
import { Page } from '../../core/api/page';

function envelope<T>(data: T): ApiResponse<T> {
  return { status: true, statusCode: 200, responseCode: 'OK', message: 'OK', errors: [], data };
}

function mockMove(overrides: Partial<StockMove> = {}): StockMove {
  return {
    id: '1', at: '2026-05-01T08:00:00Z', itemId: '42', branchId: '1',
    companyId: '1', qty: 10, costAmount: 500, direction: 'IN',
    moveType: 'GRN', refType: 'GRN', refId: '99',
    actorId: null, notes: null, batchId: null, sectionId: null,
    consumptionCategory: null, authorisedByUserId: null,
    ...overrides,
  };
}

function mockPage(moves: StockMove[]): Page<StockMove> {
  return { content: moves, page: 0, size: 100, totalElements: moves.length, totalPages: 1 };
}

function selectItem(comp: StockCardComponent, id = '42'): void {
  (comp as any).selectedItem.set({
    id, uid: 'uid-' + id, code: 'ITEM-001', name: 'Test Item',
    defaultUomUid: null, defaultUomCode: null, defaultVatGroupUid: null,
  });
  (comp as any).branchInput = '1';
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

const procurementStub = {
  searchItems: () => of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
};

// ---------------------------------------------------------------------------
// Suite A — STOCK.COUNT granted
// ---------------------------------------------------------------------------
describe('StockCardComponent (STOCK.COUNT granted)', () => {
  let fixture: ComponentFixture<StockCardComponent>;
  let http: HttpTestingController;
  let exportService: jasmine.SpyObj<ReportExportService>;

  beforeEach(async () => {
    exportService = jasmine.createSpyObj('ReportExportService', ['exportCsv', 'exportExcel', 'exportPdf']);
    exportService.exportCsv.and.returnValue(undefined);
    exportService.exportExcel.and.returnValue(Promise.resolve());
    exportService.exportPdf.and.returnValue(Promise.resolve());

    await TestBed.configureTestingModule({
      imports: [StockCardComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        { provide: ReportExportService, useValue: exportService },
        { provide: AuthService, useValue: makeAuthSpy(true) },
        { provide: BranchService, useValue: branchStub },
        { provide: ProcurementService, useValue: procurementStub },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StockCardComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('does not auto-fetch without itemId query param', () => {
    http.expectNone(r => r.url.includes('stock-card'));
  });

  it('renders move rows after successful fetch', fakeAsync(() => {
    selectItem(fixture.componentInstance);
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    tick();

    const req = http.expectOne(r => r.url.includes('stock-card'));
    expect(req.request.params.get('itemId')).toBe('42');
    expect(req.request.params.get('branchId')).toBe('1');
    req.flush(envelope(mockPage([mockMove(), mockMove({ id: '2', direction: 'OUT' })])));
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
  }));

  it('shows error alert on HTTP failure', fakeAsync(() => {
    selectItem(fixture.componentInstance);
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    tick();

    const req = http.expectOne(r => r.url.includes('stock-card'));
    req.flush({ message: 'Server error', errors: [] }, { status: 500, statusText: 'Error' });
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.alert-danger')).toBeTruthy();
  }));

  it('shows empty state when page returns no content', fakeAsync(() => {
    selectItem(fixture.componentInstance);
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    tick();

    const req = http.expectOne(r => r.url.includes('stock-card'));
    req.flush(envelope(mockPage([])));
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="report-empty-state"]')).toBeTruthy();
  }));

  it('buildExport() produces correct title and column count', fakeAsync(() => {
    selectItem(fixture.componentInstance);
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    tick();

    const req = http.expectOne(r => r.url.includes('stock-card'));
    req.flush(envelope(mockPage([mockMove()])));
    tick();
    fixture.detectChanges();

    const exp = fixture.componentInstance.buildExport();
    expect(exp.title).toBe('Stock Card');
    expect(exp.columns.length).toBe(7);
    expect(exp.rows.length).toBe(1);
  }));
});

// ---------------------------------------------------------------------------
// Suite B — STOCK.COUNT denied
// ---------------------------------------------------------------------------
describe('StockCardComponent (STOCK.COUNT denied)', () => {
  let fixture: ComponentFixture<StockCardComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StockCardComponent, HttpClientTestingModule],
      providers: [
        provideRouter([]),
        { provide: ReportExportService, useValue: jasmine.createSpyObj('ReportExportService', ['exportCsv']) },
        { provide: AuthService, useValue: makeAuthSpy(false) },
        { provide: BranchService, useValue: branchStub },
        { provide: ProcurementService, useValue: procurementStub },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StockCardComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows permission-denied state', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="report-permission-state"]')).toBeTruthy();
  });
});
