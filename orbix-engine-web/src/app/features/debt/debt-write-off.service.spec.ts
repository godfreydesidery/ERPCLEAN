import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DebtService } from './debt.service';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';
import { Page } from '../../core/api/page';
import { DebtWriteOff, CreateDebtWriteOffRequest, RejectDebtWriteOffRequest } from './debt.models';

const BASE = environment.apiUrl;

function envelope<T>(data: T): ApiResponse<T> {
  return { status: true, statusCode: 200, responseCode: 'OK', message: 'Success', errors: [], data };
}

const MOCK_WRITE_OFF: DebtWriteOff = {
  id: '1001',
  uid: '01JTEST00000000000WO1',
  targetKind: 'CUSTOMER_INVOICE',
  targetInvoiceId: '500',
  targetInvoiceUid: '01JTEST00000000000INV',
  targetInvoiceNumber: 'INV-2026-001',
  partyName: 'Test Customer Ltd',
  amount: 50000,
  currencyCode: 'TZS',
  reason: 'Customer has been liquidated.',
  status: 'PENDING_APPROVAL',
  requestedByUserId: '10',
  requestedByUsername: 'accountant',
  requestedAt: '2026-05-28T08:00:00Z',
  approvedByUserId: null,
  approvedByUsername: null,
  approvedAt: null,
  postedAt: null,
  rejectedAt: null,
  reasonForReject: null,
};

const MOCK_PAGE: Page<DebtWriteOff> = {
  content: [MOCK_WRITE_OFF],
  page: 0,
  size: 25,
  totalElements: 1,
  totalPages: 1,
};

describe('DebtService — Slice G.2 write-off methods', () => {
  let service: DebtService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(DebtService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ---------------------------------------------------------------------------
  // createWriteOff
  // ---------------------------------------------------------------------------
  describe('createWriteOff()', () => {
    const req: CreateDebtWriteOffRequest = {
      targetKind: 'CUSTOMER_INVOICE',
      targetInvoiceUid: '01JTEST00000000000INV',
      amount: 50000,
      reason: 'Customer has been liquidated.',
    };

    it('POSTs to /debt/write-offs with the request body', () => {
      let result: DebtWriteOff | undefined;
      service.createWriteOff(req).subscribe(r => (result = r));

      const httpReq = http.expectOne(`${BASE}/debt/write-offs`);
      expect(httpReq.request.method).toBe('POST');
      expect(httpReq.request.body).toEqual(req);
      httpReq.flush(envelope(MOCK_WRITE_OFF));

      expect(result?.uid).toBe('01JTEST00000000000WO1');
      expect(result?.status).toBe('PENDING_APPROVAL');
    });

    it('returns POSTED status when auto-post fires (at/below threshold)', () => {
      const autoPosted: DebtWriteOff = { ...MOCK_WRITE_OFF, status: 'POSTED', postedAt: '2026-05-28T08:00:01Z' };
      let result: DebtWriteOff | undefined;
      service.createWriteOff(req).subscribe(r => (result = r));

      http.expectOne(`${BASE}/debt/write-offs`).flush(envelope(autoPosted));
      expect(result?.status).toBe('POSTED');
    });
  });

  // ---------------------------------------------------------------------------
  // listWriteOffs
  // ---------------------------------------------------------------------------
  describe('listWriteOffs()', () => {
    it('GETs /debt/write-offs with default page/size', () => {
      let result: Page<DebtWriteOff> | undefined;
      service.listWriteOffs().subscribe(r => (result = r));

      const httpReq = http.expectOne(r => r.url.includes('debt/write-offs') && !r.url.includes('/uid/'));
      expect(httpReq.request.method).toBe('GET');
      expect(httpReq.request.params.get('page')).toBe('0');
      expect(httpReq.request.params.get('size')).toBe('25');
      expect(httpReq.request.params.has('status')).toBeFalse();
      expect(httpReq.request.params.has('kind')).toBeFalse();
      httpReq.flush(envelope(MOCK_PAGE));

      expect(result?.content.length).toBe(1);
    });

    it('sends status param when provided', () => {
      service.listWriteOffs('PENDING_APPROVAL').subscribe();
      const httpReq = http.expectOne(r => r.url.includes('debt/write-offs') && !r.url.includes('/uid/'));
      expect(httpReq.request.params.get('status')).toBe('PENDING_APPROVAL');
      httpReq.flush(envelope(MOCK_PAGE));
    });

    it('sends kind param when provided', () => {
      service.listWriteOffs(undefined, 'SUPPLIER_INVOICE').subscribe();
      const httpReq = http.expectOne(r => r.url.includes('debt/write-offs') && !r.url.includes('/uid/'));
      expect(httpReq.request.params.get('kind')).toBe('SUPPLIER_INVOICE');
      httpReq.flush(envelope(MOCK_PAGE));
    });

    it('sends both status and kind filters together', () => {
      service.listWriteOffs('POSTED', 'CUSTOMER_INVOICE', 1, 10).subscribe();
      const httpReq = http.expectOne(r => r.url.includes('debt/write-offs') && !r.url.includes('/uid/'));
      expect(httpReq.request.params.get('status')).toBe('POSTED');
      expect(httpReq.request.params.get('kind')).toBe('CUSTOMER_INVOICE');
      expect(httpReq.request.params.get('page')).toBe('1');
      expect(httpReq.request.params.get('size')).toBe('10');
      httpReq.flush(envelope(MOCK_PAGE));
    });

    it('omits status/kind when not provided', () => {
      service.listWriteOffs(undefined, undefined, 0, 25).subscribe();
      const httpReq = http.expectOne(r => r.url.includes('debt/write-offs') && !r.url.includes('/uid/'));
      expect(httpReq.request.params.has('status')).toBeFalse();
      expect(httpReq.request.params.has('kind')).toBeFalse();
      httpReq.flush(envelope(MOCK_PAGE));
    });
  });

  // ---------------------------------------------------------------------------
  // getWriteOff
  // ---------------------------------------------------------------------------
  describe('getWriteOff()', () => {
    it('GETs /debt/write-offs/uid/{uid}', () => {
      let result: DebtWriteOff | undefined;
      service.getWriteOff('01JTEST00000000000WO1').subscribe(r => (result = r));

      const httpReq = http.expectOne(`${BASE}/debt/write-offs/uid/01JTEST00000000000WO1`);
      expect(httpReq.request.method).toBe('GET');
      httpReq.flush(envelope(MOCK_WRITE_OFF));

      expect(result?.uid).toBe('01JTEST00000000000WO1');
    });
  });

  // ---------------------------------------------------------------------------
  // approveWriteOff
  // ---------------------------------------------------------------------------
  describe('approveWriteOff()', () => {
    it('POSTs to /debt/write-offs/uid/{uid}/approve with empty body', () => {
      const approved: DebtWriteOff = {
        ...MOCK_WRITE_OFF,
        status: 'POSTED',
        approvedByUserId: '11',
        approvedByUsername: 'approver',
        approvedAt: '2026-05-28T09:00:00Z',
        postedAt: '2026-05-28T09:00:00Z',
      };
      let result: DebtWriteOff | undefined;
      service.approveWriteOff('01JTEST00000000000WO1').subscribe(r => (result = r));

      const httpReq = http.expectOne(`${BASE}/debt/write-offs/uid/01JTEST00000000000WO1/approve`);
      expect(httpReq.request.method).toBe('POST');
      httpReq.flush(envelope(approved));

      expect(result?.status).toBe('POSTED');
      expect(result?.approvedByUsername).toBe('approver');
    });
  });

  // ---------------------------------------------------------------------------
  // rejectWriteOff
  // ---------------------------------------------------------------------------
  describe('rejectWriteOff()', () => {
    it('POSTs to /debt/write-offs/uid/{uid}/reject with reasonForReject body', () => {
      const rejected: DebtWriteOff = {
        ...MOCK_WRITE_OFF,
        status: 'REJECTED',
        rejectedAt: '2026-05-28T09:30:00Z',
        reasonForReject: 'Insufficient documentation.',
      };
      const reqBody: RejectDebtWriteOffRequest = { reasonForReject: 'Insufficient documentation.' };
      let result: DebtWriteOff | undefined;
      service.rejectWriteOff('01JTEST00000000000WO1', reqBody).subscribe(r => (result = r));

      const httpReq = http.expectOne(`${BASE}/debt/write-offs/uid/01JTEST00000000000WO1/reject`);
      expect(httpReq.request.method).toBe('POST');
      expect(httpReq.request.body).toEqual(reqBody);
      httpReq.flush(envelope(rejected));

      expect(result?.status).toBe('REJECTED');
      expect(result?.reasonForReject).toBe('Insufficient documentation.');
    });
  });
});
