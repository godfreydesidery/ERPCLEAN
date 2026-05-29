import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ReportsService } from './reports.service';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';
import { StockMove, ItemBranchBalance, ItemMovementRow, PartyStatement, LaybyAgeingReport } from './reports.models';
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

  // ---------------------------------------------------------------------------
  // customerStatement (Slice K)
  // ---------------------------------------------------------------------------

  const EMPTY_STMT: PartyStatement = {
    partyId: '42', partyType: 'CUSTOMER',
    from: '2026-04-29', to: '2026-05-29',
    openingBalance: 0, periodDebits: 0, periodCredits: 0, closingBalance: 0,
    entries: [],
  };

  it('customerStatement() hits /reports/customer-statement with customerId', () => {
    service.customerStatement('42', '2026-04-29', '2026-05-29').subscribe();

    const req = http.expectOne(r => r.url.includes('/reports/customer-statement'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('customerId')).toBe('42');
    expect(req.request.params.get('from')).toBe('2026-04-29');
    expect(req.request.params.get('to')).toBe('2026-05-29');
    req.flush(envelope(EMPTY_STMT));
  });

  it('customerStatement() omits from/to when null', () => {
    service.customerStatement('42', null, null).subscribe();

    const req = http.expectOne(r => r.url.includes('/reports/customer-statement'));
    expect(req.request.params.has('from')).toBeFalse();
    expect(req.request.params.has('to')).toBeFalse();
    req.flush(envelope(EMPTY_STMT));
  });

  // ---------------------------------------------------------------------------
  // supplierStatement (Slice K)
  // ---------------------------------------------------------------------------

  const EMPTY_SUP_STMT: PartyStatement = {
    partyId: '77', partyType: 'SUPPLIER',
    from: '2026-04-29', to: '2026-05-29',
    openingBalance: 0, periodDebits: 0, periodCredits: 0, closingBalance: 0,
    entries: [],
  };

  it('supplierStatement() hits /reports/supplier-statement with supplierId', () => {
    service.supplierStatement('77', '2026-04-01', '2026-04-30').subscribe();

    const req = http.expectOne(r => r.url.includes('/reports/supplier-statement'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('supplierId')).toBe('77');
    expect(req.request.params.get('from')).toBe('2026-04-01');
    expect(req.request.params.get('to')).toBe('2026-04-30');
    req.flush(envelope(EMPTY_SUP_STMT));
  });

  it('supplierStatement() omits from/to when null', () => {
    service.supplierStatement('77', null, null).subscribe();

    const req = http.expectOne(r => r.url.includes('/reports/supplier-statement'));
    expect(req.request.params.has('from')).toBeFalse();
    expect(req.request.params.has('to')).toBeFalse();
    req.flush(envelope(EMPTY_SUP_STMT));
  });

  // ---------------------------------------------------------------------------
  // laybyAgeing (Slice K)
  // ---------------------------------------------------------------------------

  const EMPTY_AGEING: LaybyAgeingReport = {
    asOf: '2026-05-29T09:00:00Z',
    balanceByType: {}, countByType: {},
    buckets: [], orders: [],
  };

  it('laybyAgeing() hits /reports/layby-ageing with all params', () => {
    service.laybyAgeing('1', 'LAYBY', '2026-05-29T00:00:00Z').subscribe();

    const req = http.expectOne(r => r.url.includes('/reports/layby-ageing'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('branchId')).toBe('1');
    expect(req.request.params.get('type')).toBe('LAYBY');
    expect(req.request.params.get('asOf')).toBe('2026-05-29T00:00:00Z');
    req.flush(envelope(EMPTY_AGEING));
  });

  it('laybyAgeing() omits optional params when null', () => {
    service.laybyAgeing(null, null, null).subscribe();

    const req = http.expectOne(r => r.url.includes('/reports/layby-ageing'));
    expect(req.request.params.has('branchId')).toBeFalse();
    expect(req.request.params.has('type')).toBeFalse();
    expect(req.request.params.has('asOf')).toBeFalse();
    req.flush(envelope(EMPTY_AGEING));
  });
});
