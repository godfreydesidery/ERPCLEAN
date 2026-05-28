/**
 * Slice H — unit tests for the two new SalesService methods:
 *   applyCreditNote()
 *   getOpenInvoicesForCustomer()
 */
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SalesService } from './sales.service';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';
import { CustomerCreditNote } from './sales.models';
import { OpenInvoiceRow } from '../debt/debt.models';

const BASE = environment.apiUrl;

function envelope<T>(data: T): ApiResponse<T> {
  return {
    status: true, statusCode: 200, responseCode: 'OK',
    message: 'Success', errors: [], data,
  };
}

const MOCK_CN: CustomerCreditNote = {
  id: '201', uid: '01JTEST00000000000CN1',
  number: 'CN-2026-001',
  companyId: '1', branchId: '10',
  customerId: '42', customerName: 'Acme Ltd',
  customerReturnId: '101',
  cnDate: '2026-05-28', currencyCode: 'TZS',
  totalAmount: 50000, allocatedAmount: 30000, availableAmount: 20000,
  status: 'PARTIALLY_ALLOCATED', notes: null, allocations: null,
};

const MOCK_OPEN_INVOICE: OpenInvoiceRow = {
  invoiceId: '301', invoiceUid: '01JTEST00000000000INV',
  number: 'INV-2026-001', invoiceDate: '2026-05-01', dueDate: '2026-05-31',
  totalAmount: 80000, paidAmount: 0, outstanding: 80000,
  daysOverdue: null, status: 'POSTED',
};

describe('SalesService — Slice H credit-note methods', () => {
  let service: SalesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(SalesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ---- applyCreditNote() ---------------------------------------------------
  describe('applyCreditNote()', () => {
    it('POSTs to the correct URL with uid in path', () => {
      let result: CustomerCreditNote | undefined;
      service.applyCreditNote('01JTEST00000000000CN1', {
        salesInvoiceUid: '01JTEST00000000000INV',
        amount: 30000,
      }).subscribe(r => (result = r));

      const req = http.expectOne(
        `${BASE}/customer-credit-notes/uid/01JTEST00000000000CN1/apply`
      );
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        salesInvoiceUid: '01JTEST00000000000INV',
        amount: 30000,
      });
      req.flush(envelope(MOCK_CN));

      expect(result?.status).toBe('PARTIALLY_ALLOCATED');
      expect(result?.availableAmount).toBe(20000);
    });

    it('returns the unwrapped CustomerCreditNote', () => {
      let result: CustomerCreditNote | undefined;
      service.applyCreditNote('01JTEST00000000000CN1', {
        salesInvoiceUid: '01JTEST00000000000INV',
        amount: 50000,
      }).subscribe(r => (result = r));

      http.expectOne(r => r.url.includes('/apply')).flush(
        envelope({ ...MOCK_CN, allocatedAmount: 50000, availableAmount: 0, status: 'FULLY_ALLOCATED' })
      );

      expect(result?.status).toBe('FULLY_ALLOCATED');
      expect(result?.availableAmount).toBe(0);
    });
  });

  // ---- getOpenInvoicesForCustomer() ----------------------------------------
  describe('getOpenInvoicesForCustomer()', () => {
    it('GETs the debt statement endpoint for the customer uid', () => {
      service.getOpenInvoicesForCustomer('01JTEST00000000000CUST').subscribe();

      const req = http.expectOne(
        `${BASE}/debt/statement/uid/01JTEST00000000000CUST`
      );
      expect(req.request.method).toBe('GET');
      req.flush(envelope({ openInvoices: [MOCK_OPEN_INVOICE] }));
    });

    it('returns only the openInvoices array from the statement', () => {
      let result: OpenInvoiceRow[] | undefined;
      service.getOpenInvoicesForCustomer('01JTEST00000000000CUST')
        .subscribe(r => (result = r));

      http.expectOne(r => r.url.includes('debt/statement')).flush(
        envelope({ openInvoices: [MOCK_OPEN_INVOICE] })
      );

      expect(result?.length).toBe(1);
      expect(result?.[0].number).toBe('INV-2026-001');
      expect(result?.[0].outstanding).toBe(80000);
    });

    it('returns an empty array when openInvoices is missing from response', () => {
      let result: OpenInvoiceRow[] | undefined;
      service.getOpenInvoicesForCustomer('01JTEST00000000000CUST')
        .subscribe(r => (result = r));

      http.expectOne(r => r.url.includes('debt/statement')).flush(
        envelope({} as { openInvoices: OpenInvoiceRow[] })
      );

      expect(result).toEqual([]);
    });
  });
});
