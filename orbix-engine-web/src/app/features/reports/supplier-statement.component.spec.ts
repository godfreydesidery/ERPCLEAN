/**
 * SupplierStatementComponent unit spec.
 * Mirrors customer-statement.component.spec.ts — 4 states, deep-link, export.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { SupplierStatementComponent } from './supplier-statement.component';
import { ReportsService } from './reports.service';
import { PartyStatement } from './reports.models';

const STMT: PartyStatement = {
  partyId: '77',
  partyType: 'SUPPLIER',
  from: '2026-04-29',
  to: '2026-05-29',
  openingBalance: 500,
  periodDebits: 8000,
  periodCredits: 3000,
  closingBalance: 5500,
  entries: [
    {
      date: '2026-05-02', kind: 'INVOICE', refId: '200', number: 'SINV-00200',
      reference: 'LPO-12', debit: 8000, credit: 0, balance: 8500, voided: false,
    },
    {
      date: '2026-05-15', kind: 'PAYMENT', refId: '80', number: 'SPAY-00080',
      reference: null, debit: 0, credit: 3000, balance: 5500, voided: false,
    },
  ],
};

const EMPTY_STMT: PartyStatement = { ...STMT, entries: [], openingBalance: 0, periodDebits: 0, periodCredits: 0, closingBalance: 0 };

async function setup(opts: {
  queryParams?: Record<string, string>;
  stmtResult?: PartyStatement | 'error';
} = {}) {
  const svcSpy = jasmine.createSpyObj<ReportsService>('ReportsService', ['supplierStatement']);
  if (opts.stmtResult === 'error') {
    svcSpy.supplierStatement.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' }))
    );
  } else {
    svcSpy.supplierStatement.and.returnValue(of(opts.stmtResult ?? STMT));
  }

  await TestBed.configureTestingModule({
    imports: [SupplierStatementComponent, HttpClientTestingModule],
    providers: [
      { provide: ReportsService, useValue: svcSpy },
      {
        provide: ActivatedRoute,
        useValue: { queryParamMap: of(convertToParamMap(opts.queryParams ?? {})) },
      },
    ],
  }).compileComponents();

  const fixture: ComponentFixture<SupplierStatementComponent> =
    TestBed.createComponent(SupplierStatementComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, comp: fixture.componentInstance, svcSpy };
}

describe('SupplierStatementComponent', () => {

  it('renders the supplier typeahead', async () => {
    const { fixture } = await setup();
    expect(fixture.nativeElement.querySelector('orbix-supplier-typeahead')).toBeTruthy();
  });

  it('auto-fetches when supplierId query param present', async () => {
    const { svcSpy } = await setup({ queryParams: { supplierId: '77' } });
    expect(svcSpy.supplierStatement).toHaveBeenCalledWith('77', jasmine.anything(), jasmine.anything());
  });

  it('renders entry rows in populated state', async () => {
    const { fixture } = await setup({ queryParams: { supplierId: '77' } });
    const rows = fixture.nativeElement.querySelectorAll('.statement-table tbody tr');
    expect(rows.length).toBe(2);
  });

  it('shows empty state when no entries', async () => {
    const { fixture } = await setup({ queryParams: { supplierId: '77' }, stmtResult: EMPTY_STMT });
    expect(fixture.nativeElement.querySelector('[data-testid="report-empty-state"]')).toBeTruthy();
  });

  it('shows error alert on fetch failure', async () => {
    const { fixture } = await setup({ queryParams: { supplierId: '77' }, stmtResult: 'error' });
    expect(fixture.nativeElement.querySelector('.alert-danger')).toBeTruthy();
  });

  it('buildExport includes debit and credit columns', async () => {
    const { comp } = await setup({ queryParams: { supplierId: '77' } });
    const exportFn = (comp as unknown as { buildExport: () => import('./report-export.service').ReportExport }).buildExport;
    const exp = exportFn();
    const keys = exp.columns.map(c => c.key);
    expect(keys).toContain('debit');
    expect(keys).toContain('credit');
    expect(exp.rows.length).toBe(2);
  });
});
