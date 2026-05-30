/**
 * Unit tests for DayService — override endpoints (US-DAY-003).
 * Covers: listOverrides, postOverrideForDay, archiveOverride.
 *
 * NOTE: The BusinessDayController has no approve/reject/dual-signoff endpoint
 * for overrides. The current override lifecycle is: POST (grant) → archive (void).
 * The approval/dual-signoff step described in US-DAY-003 is NOT implemented in the
 * backend API. See REPORT for details.
 */
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DayService } from './day.service';
import { BusinessDayOverride } from './day.models';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../core/api/api-response';

const BASE = environment.apiUrl;

function envelope<T>(data: T): ApiResponse<T> {
  return { status: true, statusCode: 200, responseCode: 'OK', message: 'OK', errors: [], data };
}

function makeOverride(overrides: Partial<BusinessDayOverride> = {}): BusinessDayOverride {
  return {
    uid: '01JTEST00000000000OVR',
    id: '10',
    branchId: '5',
    targetBusinessDate: '2026-05-27',
    entityType: 'Item',
    entityId: '101',
    reason: 'Back-dated adjustment approved by manager',
    authorisedBy: 'admin',
    at: '2026-05-28T09:00:00Z',
    archivedAt: null,
    archivedBy: null,
    ...overrides,
  };
}

describe('DayService — override endpoints', () => {
  let service: DayService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(DayService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // -- listOverrides() -------------------------------------------------------
  describe('listOverrides()', () => {
    it('GETs /business-days/overrides with branchId param', () => {
      service.listOverrides('5').subscribe();
      const req = http.expectOne(r =>
        r.url === `${BASE}/business-days/overrides` &&
        r.params.get('branchId') === '5'
      );
      expect(req.request.method).toBe('GET');
      req.flush(envelope([makeOverride()]));
    });

    it('returns the unwrapped override array', () => {
      let result: BusinessDayOverride[] | undefined;
      service.listOverrides('5').subscribe(r => (result = r));
      http.expectOne(r => r.url.includes('/overrides')).flush(envelope([makeOverride()]));
      expect(result?.length).toBe(1);
      expect(result?.[0].entityType).toBe('Item');
    });
  });

  // -- postOverrideForDay() --------------------------------------------------
  describe('postOverrideForDay()', () => {
    it('POSTs to /business-days/uid/{dayUid}/overrides with the request body', () => {
      const request = { entityType: 'Item', entityId: '101', reason: 'Manager approved' };
      service.postOverrideForDay('01JTEST00000000000DAY', request).subscribe();
      const req = http.expectOne(
        `${BASE}/business-days/uid/01JTEST00000000000DAY/overrides`
      );
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(envelope(makeOverride()));
    });

    it('returns the created BusinessDayOverride', () => {
      let result: BusinessDayOverride | undefined;
      service.postOverrideForDay('01JTEST00000000000DAY', {
        entityType: 'Customer', entityId: '42', reason: 'Late entry approved',
      }).subscribe(r => (result = r));
      http.expectOne(r => r.url.includes('/overrides')).flush(envelope(makeOverride()));
      expect(result?.uid).toBe('01JTEST00000000000OVR');
      expect(result?.archivedAt).toBeNull();
    });
  });

  // -- archiveOverride() -----------------------------------------------------
  describe('archiveOverride()', () => {
    it('POSTs to /business-days/overrides/uid/{overrideUid}/archive with empty body', () => {
      service.archiveOverride('01JTEST00000000000OVR').subscribe();
      const req = http.expectOne(
        `${BASE}/business-days/overrides/uid/01JTEST00000000000OVR/archive`
      );
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(envelope(makeOverride({ archivedAt: '2026-05-28T10:00:00Z', archivedBy: 'admin' })));
    });

    it('returns the voided override with archivedAt set', () => {
      let result: BusinessDayOverride | undefined;
      service.archiveOverride('01JTEST00000000000OVR').subscribe(r => (result = r));
      http.expectOne(r => r.url.includes('/archive'))
        .flush(envelope(makeOverride({ archivedAt: '2026-05-28T10:00:00Z', archivedBy: 'admin' })));
      expect(result?.archivedAt).toBe('2026-05-28T10:00:00Z');
      expect(result?.archivedBy).toBe('admin');
    });
  });
});
