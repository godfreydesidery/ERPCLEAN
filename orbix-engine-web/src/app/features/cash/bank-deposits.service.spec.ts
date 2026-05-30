/**
 * Unit tests for CashService — bank-deposit endpoints.
 * Covers: listDeposits, postDeposit, archiveDeposit.
 * NOTE: No reconcile/confirm endpoint exists on the backend (BankDepositController);
 * the reconciliation step is flagged as a missing API — see REPORT below.
 */
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CashService } from './services/cash.service';
import { BankDeposit } from './models/cash.models';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';

const BASE = environment.apiUrl;

function envelope<T>(data: T): ApiResponse<T> {
  return { status: true, statusCode: 200, responseCode: 'OK', message: 'OK', errors: [], data };
}

function makeDeposit(overrides: Partial<BankDeposit> = {}): BankDeposit {
  return {
    uid: '01JTEST00000000000DEP',
    id: '50',
    companyId: '1', branchId: '10',
    businessDate: '2026-05-28',
    amount: '100000',
    currencyCode: 'TZS',
    reference: 'SLIP-001',
    notes: 'EOD deposit',
    at: '2026-05-28T18:00:00Z',
    postedBy: 'admin',
    reversedAt: null, reversedBy: null,
    reversedByOutEntryId: null, reversedByInEntryId: null,
    ...overrides,
  };
}

describe('CashService — bank-deposit endpoints', () => {
  let service: CashService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(CashService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // -- listDeposits() --------------------------------------------------------
  describe('listDeposits()', () => {
    it('GETs /bank-deposits with branchId and businessDate params', () => {
      service.listDeposits('10', '2026-05-28').subscribe();
      const req = http.expectOne(r =>
        r.url === `${BASE}/bank-deposits` &&
        r.params.get('branchId') === '10' &&
        r.params.get('businessDate') === '2026-05-28'
      );
      expect(req.request.method).toBe('GET');
      req.flush(envelope([makeDeposit()]));
    });

    it('returns the unwrapped deposit array', () => {
      let result: BankDeposit[] | undefined;
      service.listDeposits('10', '2026-05-28').subscribe(r => (result = r));
      http.expectOne(r => r.url.includes('/bank-deposits')).flush(envelope([makeDeposit()]));
      expect(result?.length).toBe(1);
      expect(result?.[0].reference).toBe('SLIP-001');
    });
  });

  // -- postDeposit() ---------------------------------------------------------
  describe('postDeposit()', () => {
    it('POSTs to /bank-deposits with the request body', () => {
      const req = { branchId: '10', amount: 100000, reference: 'SLIP-001', notes: null };
      service.postDeposit(req).subscribe();
      const posted = http.expectOne(`${BASE}/bank-deposits`);
      expect(posted.request.method).toBe('POST');
      expect(posted.request.body).toEqual(req);
      posted.flush(envelope(makeDeposit()));
    });

    it('returns the created BankDeposit', () => {
      let result: BankDeposit | undefined;
      service.postDeposit({ branchId: '10', amount: 100000, reference: 'SLIP-001', notes: null })
        .subscribe(r => (result = r));
      http.expectOne(`${BASE}/bank-deposits`).flush(envelope(makeDeposit()));
      expect(result?.uid).toBe('01JTEST00000000000DEP');
    });
  });

  // -- archiveDeposit() (reversal) -------------------------------------------
  describe('archiveDeposit()', () => {
    it('POSTs to /bank-deposits/uid/{uid}/archive with empty body', () => {
      service.archiveDeposit('01JTEST00000000000DEP').subscribe();
      const req = http.expectOne(`${BASE}/bank-deposits/uid/01JTEST00000000000DEP/archive`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(envelope(makeDeposit({ reversedAt: '2026-05-28T20:00:00Z', reversedBy: 'admin' })));
    });

    it('returns the reversed BankDeposit', () => {
      let result: BankDeposit | undefined;
      service.archiveDeposit('01JTEST00000000000DEP').subscribe(r => (result = r));
      http.expectOne(r => r.url.includes('/archive'))
        .flush(envelope(makeDeposit({ reversedAt: '2026-05-28T20:00:00Z', reversedBy: 'admin' })));
      expect(result?.reversedAt).toBe('2026-05-28T20:00:00Z');
    });
  });
});
