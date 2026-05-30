/**
 * Unit tests for StockService — transfer endpoints (US-STOCK-007/008).
 * Tests the HTTP layer only: URL shape, method, body, unwrapping.
 */
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { StockService } from './stock.service';
import { StockTransfer } from './stock.models';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';

const BASE = environment.apiUrl;

function envelope<T>(data: T): ApiResponse<T> {
  return { status: true, statusCode: 200, responseCode: 'OK', message: 'Success', errors: [], data };
}

function makeTransfer(status = 'ISSUED'): StockTransfer {
  return {
    id: '1', uid: '01JTEST00000000000ST1',
    number: 'ST-001', companyId: '1',
    fromBranchId: '10', toBranchId: '20',
    issuedAt: '2026-05-28T08:00:00Z', receivedAt: null,
    status: status as StockTransfer['status'],
    lines: [
      { id: 'L1', itemId: '101', issuedQty: 10, receivedQty: null, costAmount: 500 },
    ],
  };
}

describe('StockService — transfer endpoints', () => {
  let service: StockService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(StockService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // -- listTransfers() -------------------------------------------------------
  describe('listTransfers()', () => {
    it('GETs /stock-transfers', () => {
      service.listTransfers().subscribe();
      const req = http.expectOne(`${BASE}/stock-transfers`);
      expect(req.request.method).toBe('GET');
      req.flush(envelope([makeTransfer()]));
    });

    it('returns the unwrapped array', () => {
      let result: StockTransfer[] | undefined;
      service.listTransfers().subscribe(r => (result = r));
      http.expectOne(`${BASE}/stock-transfers`).flush(envelope([makeTransfer()]));
      expect(result?.length).toBe(1);
      expect(result?.[0].number).toBe('ST-001');
    });
  });

  // -- issueTransfer() -------------------------------------------------------
  describe('issueTransfer()', () => {
    it('POSTs to /stock-transfers/uid/{uid}/issue with empty body', () => {
      service.issueTransfer('01JTEST00000000000ST1').subscribe();
      const req = http.expectOne(`${BASE}/stock-transfers/uid/01JTEST00000000000ST1/issue`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(envelope(makeTransfer('ISSUED')));
    });

    it('returns the updated StockTransfer', () => {
      let result: StockTransfer | undefined;
      service.issueTransfer('01JTEST00000000000ST1').subscribe(r => (result = r));
      http.expectOne(r => r.url.includes('/issue')).flush(envelope(makeTransfer('ISSUED')));
      expect(result?.status).toBe('ISSUED');
    });
  });

  // -- receiveTransfer() -----------------------------------------------------
  describe('receiveTransfer()', () => {
    it('PUTs to /stock-transfers/uid/{uid}/receive with line payload', () => {
      const request = { lines: [{ lineId: 'L1', receivedQty: 9 }] };
      service.receiveTransfer('01JTEST00000000000ST1', request).subscribe();
      const req = http.expectOne(`${BASE}/stock-transfers/uid/01JTEST00000000000ST1/receive`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(request);
      req.flush(envelope({ ...makeTransfer('RECEIVED'), lines: [{ id: 'L1', itemId: '101', issuedQty: 10, receivedQty: 9, costAmount: 500 }] }));
    });

    it('returns the updated StockTransfer with receivedQty populated', () => {
      let result: StockTransfer | undefined;
      const received = { ...makeTransfer('RECEIVED'), lines: [{ id: 'L1', itemId: '101', issuedQty: 10, receivedQty: 9, costAmount: 500 }] };
      service.receiveTransfer('01JTEST00000000000ST1', { lines: [{ lineId: 'L1', receivedQty: 9 }] })
        .subscribe(r => (result = r));
      http.expectOne(r => r.url.includes('/receive')).flush(envelope(received));
      expect(result?.status).toBe('RECEIVED');
      expect(result?.lines[0].receivedQty).toBe(9);
    });

    it('returns a shortage variance in the line data', () => {
      let result: StockTransfer | undefined;
      const shortfall = { ...makeTransfer('RECEIVED'), lines: [{ id: 'L1', itemId: '101', issuedQty: 10, receivedQty: 7, costAmount: 500 }] };
      service.receiveTransfer('01JTEST00000000000ST1', { lines: [{ lineId: 'L1', receivedQty: 7 }] })
        .subscribe(r => (result = r));
      http.expectOne(r => r.url.includes('/receive')).flush(envelope(shortfall));
      expect(result?.lines[0].receivedQty).toBe(7);
      // Component computes variance as receivedQty - issuedQty = -3
    });
  });

  // -- closeTransfer() -------------------------------------------------------
  describe('closeTransfer()', () => {
    it('POSTs to /stock-transfers/uid/{uid}/close with empty body', () => {
      service.closeTransfer('01JTEST00000000000ST1').subscribe();
      const req = http.expectOne(`${BASE}/stock-transfers/uid/01JTEST00000000000ST1/close`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(envelope(makeTransfer('CLOSED')));
    });

    it('returns a CLOSED StockTransfer', () => {
      let result: StockTransfer | undefined;
      service.closeTransfer('01JTEST00000000000ST1').subscribe(r => (result = r));
      http.expectOne(r => r.url.includes('/close')).flush(envelope(makeTransfer('CLOSED')));
      expect(result?.status).toBe('CLOSED');
    });
  });

  // -- getTransfer() ---------------------------------------------------------
  describe('getTransfer()', () => {
    it('GETs /stock-transfers/uid/{uid}', () => {
      service.getTransfer('01JTEST00000000000ST1').subscribe();
      const req = http.expectOne(`${BASE}/stock-transfers/uid/01JTEST00000000000ST1`);
      expect(req.request.method).toBe('GET');
      req.flush(envelope(makeTransfer()));
    });
  });
});
