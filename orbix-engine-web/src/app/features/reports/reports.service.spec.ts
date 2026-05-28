import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ReportsService } from './reports.service';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';
import { StockMove, ItemBranchBalance, ItemMovementRow } from './reports.models';
import { Page } from '../../core/api/page';

const BASE = environment.apiUrl;

function envelope<T>(data: T): ApiResponse<T> {
  return { status: true, statusCode: 200, responseCode: 'OK', message: 'OK', errors: [], data };
}

describe('ReportsService', () => {
  let service: ReportsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(ReportsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ---------------------------------------------------------------------------
  // stockCard
  // ---------------------------------------------------------------------------
  it('stockCard() hits /stock-card with correct params', () => {
    const mockPage: Page<StockMove> = { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 };
    service.stockCard('42', '1', 0, 100).subscribe();

    const req = http.expectOne(r => r.url === `${BASE}/stock-card`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('itemId')).toBe('42');
    expect(req.request.params.get('branchId')).toBe('1');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('100');
    req.flush(envelope(mockPage));
  });

  // ---------------------------------------------------------------------------
  // negativeStock
  // ---------------------------------------------------------------------------
  it('negativeStock() hits /reports/stock-negative with branchId', () => {
    service.negativeStock('2').subscribe();

    const req = http.expectOne(r => r.url.includes('stock-negative'));
    expect(req.request.params.get('branchId')).toBe('2');
    req.flush(envelope([] as ItemBranchBalance[]));
  });

  it('negativeStock() omits branchId when null', () => {
    service.negativeStock(null).subscribe();

    const req = http.expectOne(r => r.url.includes('stock-negative'));
    expect(req.request.params.has('branchId')).toBeFalse();
    req.flush(envelope([] as ItemBranchBalance[]));
  });

  // ---------------------------------------------------------------------------
  // fastMovers
  // ---------------------------------------------------------------------------
  it('fastMovers() hits /reports/stock-fast-movers with all params', () => {
    service.fastMovers('1', '2026-05-01', '2026-05-28', ['SALE', 'GRN'], 20).subscribe();

    const req = http.expectOne(r => r.url.includes('stock-fast-movers'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('branchId')).toBe('1');
    expect(req.request.params.get('from')).toBe('2026-05-01');
    expect(req.request.params.get('to')).toBe('2026-05-28');
    // Repeated moveTypes
    expect(req.request.params.getAll('moveTypes')).toEqual(['SALE', 'GRN']);
    expect(req.request.params.get('limit')).toBe('20');
    req.flush(envelope([] as ItemMovementRow[]));
  });

  it('fastMovers() omits optional params when null', () => {
    service.fastMovers(null, null, null, null, 10).subscribe();

    const req = http.expectOne(r => r.url.includes('stock-fast-movers'));
    expect(req.request.params.has('branchId')).toBeFalse();
    expect(req.request.params.has('from')).toBeFalse();
    expect(req.request.params.has('to')).toBeFalse();
    expect(req.request.params.has('moveTypes')).toBeFalse();
    req.flush(envelope([] as ItemMovementRow[]));
  });

  // ---------------------------------------------------------------------------
  // slowMovers
  // ---------------------------------------------------------------------------
  it('slowMovers() hits /reports/stock-slow-movers', () => {
    service.slowMovers('1', '2026-04-01', '2026-04-30', ['SALE'], 50).subscribe();

    const req = http.expectOne(r => r.url.includes('stock-slow-movers'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('limit')).toBe('50');
    req.flush(envelope([] as ItemMovementRow[]));
  });
});
