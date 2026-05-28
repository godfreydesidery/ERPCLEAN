import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DebtService } from './debt.service';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';
import { Page } from '../../core/api/page';
import {
  PartyNote,
  SupplierAging,
  SupplierDunningQueueRow,
  SupplierStatement
} from './debt.models';

const BASE = environment.apiUrl;

function envelope<T>(data: T): ApiResponse<T> {
  return {
    status: true,
    statusCode: 200,
    responseCode: 'OK',
    message: 'Success',
    errors: [],
    data,
  };
}

describe('DebtService — Slice G.1 supplier-AP methods', () => {
  let service: DebtService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(DebtService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // -------------------------------------------------------------------------
  // supplierAging
  // -------------------------------------------------------------------------
  describe('supplierAging()', () => {
    const mockAging: SupplierAging = {
      asOf: '2026-05-28',
      branchId: null,
      currencyCode: 'TZS',
      totals: {
        current: 100000, d1_30: 50000, d31_60: 20000,
        d61_90: 5000, d90_plus: 1000, totalOutstanding: 176000, supplierCount: 2
      },
      rows: [],
    };

    it('GETs /debt/supplier-aging with no params', () => {
      let result: SupplierAging | undefined;
      service.supplierAging().subscribe(r => (result = r));

      const req = http.expectOne(`${BASE}/debt/supplier-aging`);
      expect(req.request.method).toBe('GET');
      req.flush(envelope(mockAging));

      expect(result).toEqual(mockAging);
    });

    it('sends branchId when provided', () => {
      service.supplierAging('branch-01').subscribe();
      const req = http.expectOne(r => r.url.includes('supplier-aging'));
      expect(req.request.params.get('branchId')).toBe('branch-01');
      req.flush(envelope(mockAging));
    });

    it('sends asOf when provided', () => {
      service.supplierAging(null, '2026-04-30').subscribe();
      const req = http.expectOne(r => r.url.includes('supplier-aging'));
      expect(req.request.params.get('asOf')).toBe('2026-04-30');
      req.flush(envelope(mockAging));
    });

    it('omits branchId when null', () => {
      service.supplierAging(null).subscribe();
      const req = http.expectOne(r => r.url.includes('supplier-aging'));
      expect(req.request.params.has('branchId')).toBeFalse();
      req.flush(envelope(mockAging));
    });
  });

  // -------------------------------------------------------------------------
  // supplierDunning
  // -------------------------------------------------------------------------
  describe('supplierDunning()', () => {
    const mockPage: Page<SupplierDunningQueueRow> = {
      content: [
        {
          supplierId: '42',
          supplierUid: '01JTEST00000000000001',
          supplierName: 'ACME Supplies Ltd',
          paymentTermsDays: 30,
          totalOutstanding: 75000,
          oldestDaysOverdue: 45,
          oldestDueDate: '2026-04-13',
          worstBucket: 'D_31_60',
          overdueInvoiceCount: 3,
        }
      ],
      page: 0,
      size: 25,
      totalElements: 1,
      totalPages: 1,
    };

    it('GETs /debt/supplier-dunning with default page/size', () => {
      let result: Page<SupplierDunningQueueRow> | undefined;
      service.supplierDunning().subscribe(r => (result = r));

      const req = http.expectOne(r => r.url.includes('supplier-dunning'));
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('25');
      req.flush(envelope(mockPage));

      expect(result?.content.length).toBe(1);
      expect(result?.content[0].supplierUid).toBe('01JTEST00000000000001');
    });

    it('sends bucketFilter when provided', () => {
      service.supplierDunning(null, 'D_31_60').subscribe();
      const req = http.expectOne(r => r.url.includes('supplier-dunning'));
      expect(req.request.params.get('bucket')).toBe('D_31_60');
      req.flush(envelope(mockPage));
    });

    it('omits bucket param when null', () => {
      service.supplierDunning(null, null).subscribe();
      const req = http.expectOne(r => r.url.includes('supplier-dunning'));
      expect(req.request.params.has('bucket')).toBeFalse();
      req.flush(envelope(mockPage));
    });

    it('sends custom page and size', () => {
      service.supplierDunning(null, null, 2, 10).subscribe();
      const req = http.expectOne(r => r.url.includes('supplier-dunning'));
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('10');
      req.flush(envelope(mockPage));
    });
  });

  // -------------------------------------------------------------------------
  // supplierStatement
  // -------------------------------------------------------------------------
  describe('supplierStatement()', () => {
    const mockStatement: SupplierStatement = {
      supplierId: '99',
      supplierUid: '01JTEST00000000000099',
      supplierName: 'ACME Supplies Ltd',
      currencyCode: 'TZS',
      paymentTermsDays: 30,
      totalOutstanding: 50000,
      openInvoiceCount: 2,
      overdueInvoiceCount: 1,
      asOf: '2026-05-28',
      openInvoices: [],
      recentPayments: [],
    };

    it('GETs /debt/supplier/uid/{uid}', () => {
      let result: SupplierStatement | undefined;
      service.supplierStatement('01JTEST00000000000099').subscribe(r => (result = r));

      const req = http.expectOne(`${BASE}/debt/supplier/uid/01JTEST00000000000099`);
      expect(req.request.method).toBe('GET');
      req.flush(envelope(mockStatement));

      expect(result?.supplierName).toBe('ACME Supplies Ltd');
      expect(result?.paymentTermsDays).toBe(30);
    });
  });

  // -------------------------------------------------------------------------
  // listNotes — extended with kind param
  // -------------------------------------------------------------------------
  describe('listNotes() with kind', () => {
    const mockNotes: PartyNote[] = [
      {
        id: '1', uid: '01JNOTE0000000000001',
        partyId: '99', kind: 'AP_CHASE', body: 'Called supplier',
        status: 'ACTIVE', createdAt: '2026-05-20T10:00:00Z',
        createdBy: 'accountant', archivedAt: null, archivedBy: null,
      }
    ];

    it('sends kind=AP_CHASE when specified', () => {
      let result: PartyNote[] | undefined;
      service.listNotes('01JTEST00000000000099', { limit: 50, kind: 'AP_CHASE' })
        .subscribe(r => (result = r));

      const req = http.expectOne(r => r.url.includes('debt/notes'));
      expect(req.request.params.get('kind')).toBe('AP_CHASE');
      expect(req.request.params.get('limit')).toBe('50');
      req.flush(envelope(mockNotes));

      expect(result?.length).toBe(1);
      expect(result?.[0].kind).toBe('AP_CHASE');
    });

    it('omits kind param when not specified', () => {
      service.listNotes('01JTEST00000000000001', { limit: 50 }).subscribe();
      const req = http.expectOne(r => r.url.includes('debt/notes'));
      expect(req.request.params.has('kind')).toBeFalse();
      req.flush(envelope([]));
    });

    it('sends kind=AR_CHASE for the AR path', () => {
      service.listNotes('01JTEST00000000000001', { kind: 'AR_CHASE' }).subscribe();
      const req = http.expectOne(r => r.url.includes('debt/notes'));
      expect(req.request.params.get('kind')).toBe('AR_CHASE');
      req.flush(envelope([]));
    });
  });
});
