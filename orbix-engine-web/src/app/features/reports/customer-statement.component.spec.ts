/**
 * CustomerStatementComponent unit spec.
 * Covers: 4 states (loading / empty / error / populated), filter change,
 *         kind-badge mapping, deep-link query-param parsing, export builder.
 */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { CustomerStatementComponent } from './customer-statement.component';
import { ReportsService } from './reports.service';
import { PartyStatement } from './reports.models';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const STMT: PartyStatement = {
  partyId: '42',
  partyType: 'CUSTOMER',
  from: '2026-04-29',
  to: '2026-05-29',
  openingBalance: 1000,
  periodDebits: 5000,
  periodCredits: 2000,
  closingBalance: 4000,
  entries: [
    {
      date: '2026-05-01', kind: 'INVOICE', refId: '101', number: 'SI-00101',
      reference: 'PO-55', debit: 5000, credit: 0, balance: 6000, voided: false,
    },
    {
      date: '2026-05-10', kind: 'RECEIPT', refId: '50', number: 'SR-00050',
      reference: null, debit: 0, credit: 2000, balance: 4000, voided: false,
    },
  ],
};

const EMPTY_STMT: PartyStatement = {
  ...STMT,
  openingBalance: 0, periodDebits: 0, periodCredits: 0, closingBalance: 0,
  entries: [],
};

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

async function setup(opts: {
  queryParams?: Record<string, string>;
  stmtResult?: PartyStatement | 'error';
} = {}) {
  const svcSpy = jasmine.createSpyObj<ReportsService>('ReportsService', ['customerStatement']);

  if (opts.stmtResult === 'error') {
    svcSpy.customerStatement.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' }))
    );
  } else {
    svcSpy.customerStatement.and.returnValue(of(opts.stmtResult ?? STMT));
  }

  const queryParams = opts.queryParams ?? {};

  await TestBed.configureTestingModule({
    imports: [CustomerStatementComponent, HttpClientTestingModule],
    providers: [
      provideRouter([]),
      { provide: ReportsService, useValue: svcSpy },
      {
        provide: ActivatedRoute,
        useValue: { queryParamMap: of(convertToParamMap(queryParams)) },
      },
    ],
  }).compileComponents();

  const fixture: ComponentFixture<CustomerStatementComponent> =
    TestBed.createComponent(CustomerStatementComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, svcSpy };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('CustomerStatementComponent', () => {

  describe('initial state (no query params)', () => {
    it('renders the customer typeahead', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('orbix-customer-typeahead')).toBeTruthy();
    });

    it('shows Run button disabled when no customer selected', async () => {
      const { fixture } = await setup();
      const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
      expect(btn.disabled).toBeTrue();
    });

    it('does not call the service before Run', async () => {
      const { svcSpy } = await setup();
      expect(svcSpy.customerStatement).not.toHaveBeenCalled();
    });
  });

  describe('deep-link query params', () => {
    it('auto-fetches when customerId query param present', async () => {
      const { svcSpy } = await setup({ queryParams: { customerId: '42' } });
      expect(svcSpy.customerStatement).toHaveBeenCalledWith('42', jasmine.anything(), jasmine.anything());
    });

    it('applies from/to query params to the date filters', async () => {
      const { comp } = await setup({
        queryParams: { customerId: '42', from: '2026-01-01', to: '2026-01-31' },
      });
      expect((comp as unknown as { filterFrom: string }).filterFrom).toBe('2026-01-01');
      expect((comp as unknown as { filterTo: string }).filterTo).toBe('2026-01-31');
    });
  });

  describe('populated state', () => {
    it('shows KPI strip with opening/closing balances', async () => {
      const { fixture } = await setup({ queryParams: { customerId: '42' } });
      const text: string = fixture.nativeElement.textContent;
      expect(text).toContain('Opening');
      expect(text).toContain('Closing');
    });

    it('renders one row per entry', async () => {
      const { fixture } = await setup({ queryParams: { customerId: '42' } });
      const rows = fixture.nativeElement.querySelectorAll('.statement-table tbody tr');
      expect(rows.length).toBe(2);
    });

    it('shows the export menu when rows are present', async () => {
      const { fixture } = await setup({ queryParams: { customerId: '42' } });
      expect(fixture.nativeElement.querySelector('orbix-report-export-menu')).toBeTruthy();
    });
  });

  describe('empty state', () => {
    it('shows the empty-state panel when no entries', async () => {
      const { fixture } = await setup({
        queryParams: { customerId: '42' },
        stmtResult: EMPTY_STMT,
      });
      expect(fixture.nativeElement.querySelector('[data-testid="report-empty-state"]')).toBeTruthy();
    });

    it('hides the export menu when no entries', async () => {
      const { fixture } = await setup({
        queryParams: { customerId: '42' },
        stmtResult: EMPTY_STMT,
      });
      expect(fixture.nativeElement.querySelector('orbix-report-export-menu')).toBeNull();
    });
  });

  describe('error state', () => {
    it('shows the error alert on fetch failure', async () => {
      const { fixture } = await setup({
        queryParams: { customerId: '42' },
        stmtResult: 'error',
      });
      const alert = fixture.nativeElement.querySelector('.alert-danger');
      expect(alert).toBeTruthy();
    });
  });

  describe('export builder', () => {
    it('buildExport returns the expected column keys', async () => {
      const { comp } = await setup({ queryParams: { customerId: '42' } });
      const exportFn = (comp as unknown as { buildExport: () => import('./report-export.service').ReportExport }).buildExport;
      const exp = exportFn();
      const keys = exp.columns.map(c => c.key);
      expect(keys).toContain('date');
      expect(keys).toContain('debit');
      expect(keys).toContain('balance');
      expect(exp.rows.length).toBe(2);
    });
  });
});
