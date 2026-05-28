/**
 * Slice H.1 — ProcurementService vendor-return + vendor-credit-note method specs.
 * Verifies each new method posts to the correct URL with the correct body.
 */
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { ProcurementService } from './procurement.service';
import { environment } from '../../../environments/environment';
import {
  ApplyVendorCreditNoteRequest,
  CreateVendorReturnRequest,
  IssueVendorCreditNoteRequest,
  VendorCreditNote,
  VendorReturn,
} from './procurement.models';
import { SupplierStatement } from '../debt/debt.models';
import { ApiResponse } from '../../core/api/api-response';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const BASE = environment.apiUrl;

function envelope<T>(data: T): ApiResponse<T> {
  return { status: true, statusCode: 200, responseCode: 'SUCCESS', message: 'ok', errors: [], data };
}

const VENDOR_RETURN: VendorReturn = {
  id: '1', uid: '01JTEST00000000000VR1', number: 'VR-001',
  supplierId: '10', supplierUid: null,
  originalGrnId: null, originalGrnNumber: null,
  originalSupplierInvoiceId: null,
  returnDate: '2026-05-28', reason: 'DAMAGED', restock: true,
  totalAmount: 0, status: 'DRAFT', postedAt: null, notes: null, lines: [],
};

const VENDOR_CN: VendorCreditNote = {
  id: '201', uid: '01JTEST00000000000VCN1', number: 'VCN-001',
  supplierId: '10', supplierUid: null, vendorReturnId: '1',
  cnDate: '2026-05-28', currencyCode: 'TZS',
  totalAmount: 0, allocatedAmount: 0, availableAmount: 0,
  status: 'POSTED', notes: null, allocations: null,
};

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('ProcurementService — Slice H.1 vendor-return + credit-note methods', () => {
  let svc: ProcurementService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    svc = TestBed.inject(ProcurementService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // -- createVendorReturn -----------------------------------------------------
  describe('createVendorReturn', () => {
    it('POSTs to /vendor-returns', () => {
      const req: CreateVendorReturnRequest = {
        supplierUid: '01JTEST00000000000SUP',
        returnDate: '2026-05-28', reason: 'DAMAGED', restock: true,
        lines: [],
      };
      let result: VendorReturn | undefined;
      svc.createVendorReturn(req).subscribe(r => (result = r));

      const testReq = http.expectOne(`${BASE}/vendor-returns`);
      expect(testReq.request.method).toBe('POST');
      expect(testReq.request.body).toEqual(req);
      testReq.flush(envelope(VENDOR_RETURN));
      expect(result?.uid).toBe(VENDOR_RETURN.uid);
    });
  });

  // -- listVendorReturns ------------------------------------------------------
  describe('listVendorReturns', () => {
    it('GETs /vendor-returns with page params', () => {
      const page: Page<VendorReturn> = {
        content: [VENDOR_RETURN], page: 0, size: 20, totalElements: 1, totalPages: 1,
      };
      let result: Page<VendorReturn> | undefined;
      svc.listVendorReturns('10', 0, 20).subscribe(p => (result = p));

      const testReq = http.expectOne(
        r => r.url === `${BASE}/vendor-returns` && r.params.get('branchId') === '10'
      );
      expect(testReq.request.method).toBe('GET');
      testReq.flush(envelope(page));
      expect(result?.content.length).toBe(1);
    });
  });

  // -- getVendorReturn --------------------------------------------------------
  describe('getVendorReturn', () => {
    it('GETs /vendor-returns/uid/{uid}', () => {
      let result: VendorReturn | undefined;
      svc.getVendorReturn(VENDOR_RETURN.uid).subscribe(r => (result = r));

      const testReq = http.expectOne(`${BASE}/vendor-returns/uid/${VENDOR_RETURN.uid}`);
      expect(testReq.request.method).toBe('GET');
      testReq.flush(envelope(VENDOR_RETURN));
      expect(result?.uid).toBe(VENDOR_RETURN.uid);
    });
  });

  // -- postVendorReturn -------------------------------------------------------
  describe('postVendorReturn', () => {
    it('POSTs to /vendor-returns/uid/{uid}/post', () => {
      let result: VendorReturn | undefined;
      svc.postVendorReturn(VENDOR_RETURN.uid).subscribe(r => (result = r));

      const testReq = http.expectOne(
        `${BASE}/vendor-returns/uid/${VENDOR_RETURN.uid}/post`
      );
      expect(testReq.request.method).toBe('POST');
      testReq.flush(envelope({ ...VENDOR_RETURN, status: 'POSTED' }));
      expect(result?.status).toBe('POSTED');
    });
  });

  // -- cancelVendorReturn -----------------------------------------------------
  describe('cancelVendorReturn', () => {
    it('POSTs to /vendor-returns/uid/{uid}/cancel', () => {
      svc.cancelVendorReturn(VENDOR_RETURN.uid).subscribe();

      const testReq = http.expectOne(
        `${BASE}/vendor-returns/uid/${VENDOR_RETURN.uid}/cancel`
      );
      expect(testReq.request.method).toBe('POST');
      testReq.flush(envelope(VENDOR_RETURN));
    });
  });

  // -- issueVendorCreditNote --------------------------------------------------
  describe('issueVendorCreditNote', () => {
    it('POSTs to /vendor-returns/uid/{uid}/issue-credit-note', () => {
      const req: IssueVendorCreditNoteRequest = { cnDate: '2026-05-28' };
      let result: VendorCreditNote | undefined;
      svc.issueVendorCreditNote(VENDOR_RETURN.uid, req).subscribe(cn => (result = cn));

      const testReq = http.expectOne(
        `${BASE}/vendor-returns/uid/${VENDOR_RETURN.uid}/issue-credit-note`
      );
      expect(testReq.request.method).toBe('POST');
      expect(testReq.request.body).toEqual(req);
      testReq.flush(envelope(VENDOR_CN));
      expect(result?.uid).toBe(VENDOR_CN.uid);
    });
  });

  // -- listVendorCreditNotes --------------------------------------------------
  describe('listVendorCreditNotes', () => {
    it('GETs /vendor-credit-notes', () => {
      let result: VendorCreditNote[] | undefined;
      svc.listVendorCreditNotes('10').subscribe(cns => (result = cns));

      const testReq = http.expectOne(
        r => r.url === `${BASE}/vendor-credit-notes` && r.params.get('branchId') === '10'
      );
      expect(testReq.request.method).toBe('GET');
      testReq.flush(envelope([VENDOR_CN]));
      expect(result?.length).toBe(1);
    });
  });

  // -- applyVendorCreditNote -------------------------------------------------
  describe('applyVendorCreditNote', () => {
    it('POSTs to /vendor-credit-notes/uid/{uid}/apply', () => {
      const req: ApplyVendorCreditNoteRequest = {
        supplierInvoiceUid: '01JTEST00000000000INV', amount: 50000,
      };
      let result: VendorCreditNote | undefined;
      svc.applyVendorCreditNote(VENDOR_CN.uid, req).subscribe(cn => (result = cn));

      const testReq = http.expectOne(
        `${BASE}/vendor-credit-notes/uid/${VENDOR_CN.uid}/apply`
      );
      expect(testReq.request.method).toBe('POST');
      expect(testReq.request.body).toEqual(req);
      testReq.flush(envelope({ ...VENDOR_CN, allocatedAmount: 50000, availableAmount: 70000, status: 'PARTIALLY_ALLOCATED' }));
      expect(result?.status).toBe('PARTIALLY_ALLOCATED');
    });
  });

  // -- getSupplierOpenInvoices ------------------------------------------------
  describe('getSupplierOpenInvoices', () => {
    it('GETs /debt/supplier/uid/{uid} and returns openInvoices array', () => {
      const stmt: SupplierStatement = {
        supplierId: '10', supplierUid: '01JTEST00000000000SUP',
        supplierName: 'Acme Supplies', currencyCode: 'TZS',
        paymentTermsDays: 30, totalOutstanding: 200000,
        openInvoiceCount: 1, overdueInvoiceCount: 0, asOf: '2026-05-28',
        openInvoices: [{
          invoiceId: '301', invoiceUid: '01JTEST00000000000INV',
          number: 'SINV-001', supplierInvoiceNo: 'EXT-001',
          invoiceDate: '2026-05-01', dueDate: '2026-05-31',
          totalAmount: 200000, paidAmount: 0, outstanding: 200000,
          daysOverdue: null, status: 'POSTED',
        }],
        recentPayments: [],
      };
      let result: SupplierStatement['openInvoices'] | undefined;
      svc.getSupplierOpenInvoices('01JTEST00000000000SUP').subscribe(rows => (result = rows));

      const testReq = http.expectOne(
        `${BASE}/debt/supplier/uid/01JTEST00000000000SUP`
      );
      expect(testReq.request.method).toBe('GET');
      testReq.flush(envelope(stmt));
      expect(result?.length).toBe(1);
      expect(result?.[0].invoiceUid).toBe('01JTEST00000000000INV');
    });
  });
});
