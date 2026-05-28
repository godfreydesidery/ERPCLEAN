import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { SalesSummaryComponent } from './sales-summary.component';
import { ReportExportService } from './report-export.service';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';

const BASE = environment.apiUrl;

function envelope<T>(data: T): ApiResponse<T> {
  return { status: true, statusCode: 200, responseCode: 'OK', message: 'Success', errors: [], data };
}

const mockSummary = {
  businessDate: '2026-05-28',
  branchId: '1',
  sales: {
    invoiceTotal: 100000, invoiceTax: 18000, invoiceDiscount: 5000, invoiceCount: 3,
    posSaleNet: 50000, posSaleTax: 9000, posSaleDiscount: 2000, posSaleCount: 10,
    posRefundCount: 1, grandTotal: 150000,
  },
  purchases: { grnTotal: 80000, grnTax: 14400, grnCount: 2 },
  cash: { openingTotal: 20000, inTotal: 60000, outTotal: 10000, closingTotal: 70000, closingByAccount: null },
};

describe('SalesSummaryComponent', () => {
  let fixture: ComponentFixture<SalesSummaryComponent>;
  let http: HttpTestingController;
  let exportService: jasmine.SpyObj<ReportExportService>;

  beforeEach(async () => {
    exportService = jasmine.createSpyObj('ReportExportService', ['exportCsv', 'exportExcel', 'exportPdf']);
    exportService.exportCsv.and.returnValue(undefined);
    exportService.exportExcel.and.returnValue(Promise.resolve());
    exportService.exportPdf.and.returnValue(Promise.resolve());

    await TestBed.configureTestingModule({
      imports: [SalesSummaryComponent, HttpClientTestingModule],
      providers: [
        { provide: ReportExportService, useValue: exportService },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of({ get: (_: string) => null }) },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SalesSummaryComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // -------------------------------------------------------------------------
  // Loading state
  // -------------------------------------------------------------------------
  it('shows loading spinner while request is in flight', () => {
    fixture.detectChanges(); // triggers ngOnInit → fetch()

    const req = http.expectOne(r => r.url.includes('sales-summary'));
    // Before flush — loading should be true
    const spinner = fixture.nativeElement.querySelector('.spinner-border');
    expect(spinner).toBeTruthy();
    req.flush(envelope(mockSummary));
  });

  // -------------------------------------------------------------------------
  // Populated state
  // -------------------------------------------------------------------------
  it('renders KPI tiles after successful fetch', fakeAsync(() => {
    fixture.detectChanges();

    const req = http.expectOne(r => r.url.includes('sales-summary'));
    req.flush(envelope(mockSummary));
    tick();
    fixture.detectChanges();

    const tiles = fixture.nativeElement.querySelectorAll('.kpi-tile');
    expect(tiles.length).toBe(6);
  }));

  it('renders sales breakdown table', fakeAsync(() => {
    fixture.detectChanges();
    const req = http.expectOne(r => r.url.includes('sales-summary'));
    req.flush(envelope(mockSummary));
    tick();
    fixture.detectChanges();

    const tables = fixture.nativeElement.querySelectorAll('table');
    expect(tables.length).toBeGreaterThanOrEqual(2);
  }));

  // -------------------------------------------------------------------------
  // Empty state — null data inside envelope
  // -------------------------------------------------------------------------
  it('shows error when data is null in envelope', fakeAsync(() => {
    fixture.detectChanges();
    const req = http.expectOne(r => r.url.includes('sales-summary'));
    // Interceptor unwrap throws when data is null
    req.flush({ ...envelope(null), data: null });
    tick();
    fixture.detectChanges();

    const errorDiv = fixture.nativeElement.querySelector('[role="alert"]');
    expect(errorDiv).toBeTruthy();
  }));

  // -------------------------------------------------------------------------
  // Error state
  // -------------------------------------------------------------------------
  it('shows error alert on HTTP 500', fakeAsync(() => {
    fixture.detectChanges();
    const req = http.expectOne(r => r.url.includes('sales-summary'));
    req.flush({ message: 'Internal error', errors: [] }, { status: 500, statusText: 'Error' });
    tick();
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('.alert-danger');
    expect(alert).toBeTruthy();
  }));

  // -------------------------------------------------------------------------
  // Export builder
  // -------------------------------------------------------------------------
  it('buildExport() returns a ReportExport with rows matching sales blocks', fakeAsync(() => {
    fixture.detectChanges();
    const req = http.expectOne(r => r.url.includes('sales-summary'));
    req.flush(envelope(mockSummary));
    tick();
    fixture.detectChanges();

    const comp = fixture.componentInstance;
    const exported = comp.buildExport();
    expect(exported.title).toBe('Daily Sales Summary');
    expect(exported.rows.length).toBe(3); // invoice, POS sales, POS refunds
    expect(exported.totals?.['total']).toBe(mockSummary.sales.grandTotal);
  }));

  // -------------------------------------------------------------------------
  // Filter refetch
  // -------------------------------------------------------------------------
  it('re-fetches when Run button is clicked', fakeAsync(() => {
    fixture.detectChanges();
    // Initial fetch
    const req1 = http.expectOne(r => r.url.includes('sales-summary'));
    req1.flush(envelope(mockSummary));
    tick();
    fixture.detectChanges();

    // Click Run
    const runBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    runBtn.click();
    tick();

    const req2 = http.expectOne(r => r.url.includes('sales-summary'));
    expect(req2.request.method).toBe('GET');
    req2.flush(envelope(mockSummary));
  }));
});
