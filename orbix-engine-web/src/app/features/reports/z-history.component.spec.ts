import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ZHistoryComponent } from './z-history.component';
import { ReportExportService } from './report-export.service';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';

const BASE = environment.apiUrl;

function envelope<T>(data: T): ApiResponse<T> {
  return { status: true, statusCode: 200, responseCode: 'OK', message: 'Success', errors: [], data };
}

function makeTillReport(sessionId: string) {
  return {
    tillSessionId: sessionId,
    tillId: '1',
    branchId: '1',
    businessDate: '2026-05-28',
    status: 'CLOSED',
    cashierId: '5',
    supervisorId: null,
    openedAt: '2026-05-28T06:00:00Z',
    closedAt: '2026-05-28T22:00:00Z',
    salesCount: 42,
    refundsCount: 1,
    grossSales: 250000,
    grossRefunds: 5000,
    netSales: 245000,
    discountTotal: 3000,
    expectedCash: 120000,
    declaredCash: 119500,
    variance: -500,
  };
}

function makeEntry(id: string) {
  return {
    tillSessionId: id,
    tillId: '1',
    branchId: '1',
    businessDate: '2026-05-28',
    sessionStatus: 'CLOSED',
    openedAt: '2026-05-28T06:00:00Z',
    closedAt: '2026-05-28T22:00:00Z',
    report: makeTillReport(id),
  };
}

describe('ZHistoryComponent', () => {
  let fixture: ComponentFixture<ZHistoryComponent>;
  let http: HttpTestingController;
  let exportService: jasmine.SpyObj<ReportExportService>;

  beforeEach(async () => {
    exportService = jasmine.createSpyObj('ReportExportService', ['exportCsv', 'exportExcel', 'exportPdf']);
    exportService.exportCsv.and.returnValue(undefined);
    exportService.exportExcel.and.returnValue(Promise.resolve());
    exportService.exportPdf.and.returnValue(Promise.resolve());

    await TestBed.configureTestingModule({
      imports: [ZHistoryComponent, HttpClientTestingModule],
      providers: [
        { provide: ReportExportService, useValue: exportService },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of({ get: (_: string) => null }) },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ZHistoryComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // BranchPickerComponent fires GET /branches on init — flush any open request.
    http.match(r => r.url.includes('/branches')).forEach(r => r.flush({ status: true, statusCode: 200, responseCode: 'OK', message: 'OK', errors: [], data: { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } }));
    http.verify();
  });

  // -------------------------------------------------------------------------
  // Loading state
  // -------------------------------------------------------------------------
  it('shows loading spinner while fetching', () => {
    fixture.detectChanges();
    const req = http.expectOne(r => r.url.includes('z-history'));
    expect(req.request.method).toBe('GET');

    const spinner = fixture.nativeElement.querySelector('.spinner-border');
    expect(spinner).toBeTruthy();
    req.flush(envelope([]));
  });

  // -------------------------------------------------------------------------
  // Empty state
  // -------------------------------------------------------------------------
  it('shows empty state when response is empty array', fakeAsync(() => {
    fixture.detectChanges();
    const req = http.expectOne(r => r.url.includes('z-history'));
    req.flush(envelope([]));
    tick();
    fixture.detectChanges();

    const empty = fixture.nativeElement.querySelector('.empty-icon');
    expect(empty).toBeTruthy();
  }));

  it('export menu is disabled when rows are empty', fakeAsync(() => {
    fixture.detectChanges();
    const req = http.expectOne(r => r.url.includes('z-history'));
    req.flush(envelope([]));
    tick();
    fixture.detectChanges();

    const exportMenu = fixture.nativeElement.querySelector('orbix-report-export-menu');
    expect(exportMenu).toBeTruthy();
    // All buttons should have aria-disabled="true"
    const buttons: NodeListOf<HTMLButtonElement> = exportMenu.querySelectorAll('button');
    buttons.forEach((btn: HTMLButtonElement) => {
      expect(btn.getAttribute('aria-disabled')).toBe('true');
    });
  }));

  // -------------------------------------------------------------------------
  // Populated state
  // -------------------------------------------------------------------------
  it('renders a table row per session', fakeAsync(() => {
    fixture.detectChanges();
    const req = http.expectOne(r => r.url.includes('z-history'));
    req.flush(envelope([makeEntry('1'), makeEntry('2')]));
    tick();
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
  }));

  it('renders the net sales value in a table cell', fakeAsync(() => {
    fixture.detectChanges();
    const req = http.expectOne(r => r.url.includes('z-history'));
    req.flush(envelope([makeEntry('1')]));
    tick();
    fixture.detectChanges();

    const text: string = fixture.nativeElement.textContent;
    // netSales = 245000 formatted as "245,000"
    expect(text).toContain('245,000');
  }));

  // -------------------------------------------------------------------------
  // Error state
  // -------------------------------------------------------------------------
  it('shows error alert on HTTP error', fakeAsync(() => {
    fixture.detectChanges();
    const req = http.expectOne(r => r.url.includes('z-history'));
    req.flush({ message: 'Server error' }, { status: 503, statusText: 'Unavailable' });
    tick();
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('.alert-danger');
    expect(alert).toBeTruthy();
  }));

  // -------------------------------------------------------------------------
  // Default date range — last 7 days
  // -------------------------------------------------------------------------
  it('sends from/to query params defaulting to last 7 days', fakeAsync(() => {
    fixture.detectChanges();
    const req = http.expectOne(r => r.url.includes('z-history'));
    expect(req.request.params.has('from')).toBeTrue();
    expect(req.request.params.has('to')).toBeTrue();
    req.flush(envelope([]));
  }));

  // -------------------------------------------------------------------------
  // buildExport
  // -------------------------------------------------------------------------
  it('buildExport() totals net sales across all sessions', fakeAsync(() => {
    fixture.detectChanges();
    const req = http.expectOne(r => r.url.includes('z-history'));
    req.flush(envelope([makeEntry('1'), makeEntry('2')]));
    tick();
    fixture.detectChanges();

    const exported = fixture.componentInstance.buildExport();
    expect(exported.rows.length).toBe(2);
    // netSales per entry = 245000; total = 490000
    expect(exported.totals?.['netSales']).toBe(490000);
  }));

  // -------------------------------------------------------------------------
  // Filter re-fetch
  // -------------------------------------------------------------------------
  it('re-fetches when Run is clicked', fakeAsync(() => {
    fixture.detectChanges();
    const req1 = http.expectOne(r => r.url.includes('z-history'));
    req1.flush(envelope([]));
    tick();
    fixture.detectChanges();

    const runBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    runBtn.click();
    tick();

    const req2 = http.expectOne(r => r.url.includes('z-history'));
    expect(req2.request.method).toBe('GET');
    req2.flush(envelope([]));
  }));
});
