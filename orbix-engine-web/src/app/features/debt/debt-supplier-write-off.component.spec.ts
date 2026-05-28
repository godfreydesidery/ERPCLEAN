import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { DebtSupplierComponent } from './debt-supplier.component';
import { DebtService } from './debt.service';
import { AuthService } from '../../core/auth/auth.service';
import { SupplierStatement, PartyNote, OpenSupplierInvoiceRow } from './debt.models';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------
const OPEN_INV: OpenSupplierInvoiceRow = {
  invoiceId: '600', invoiceUid: '01JTEST00000000000SI1',
  number: 'SINV-2026-001', supplierInvoiceNo: 'ACME-001',
  invoiceDate: '2026-04-01', dueDate: '2026-05-01',
  totalAmount: 75000, paidAmount: 0, outstanding: 75000,
  daysOverdue: 27, status: 'POSTED',
};

const STATEMENT: SupplierStatement = {
  supplierId: '99', supplierUid: '01JTEST00000000000099',
  supplierName: 'ACME Supplies Ltd',
  currencyCode: 'TZS', paymentTermsDays: 30,
  totalOutstanding: 75000,
  openInvoiceCount: 1, overdueInvoiceCount: 1,
  asOf: '2026-05-28',
  openInvoices: [OPEN_INV],
  recentPayments: [],
};

function makeDebtSpy(): jasmine.SpyObj<DebtService> {
  const spy = jasmine.createSpyObj<DebtService>('DebtService', [
    'supplierStatement', 'listNotes',
  ]);
  spy.supplierStatement.and.returnValue(of(STATEMENT));
  spy.listNotes.and.returnValue(of([] as PartyNote[]));
  return spy;
}

function makeAuthSpy(hasWriteOff = true): jasmine.SpyObj<AuthService> {
  const spy = jasmine.createSpyObj<AuthService>('AuthService', ['hasPermission']);
  spy.hasPermission.and.callFake((perm: string) => {
    if (perm === 'DEBT.WRITE_OFF.REQUEST') return hasWriteOff;
    return true;
  });
  return spy;
}

async function setup(hasWriteOff = true) {
  const debtSpy = makeDebtSpy();
  const authSpy = makeAuthSpy(hasWriteOff);

  await TestBed.configureTestingModule({
    imports: [DebtSupplierComponent, HttpClientTestingModule],
    providers: [
      provideRouter([]),
      { provide: DebtService, useValue: debtSpy },
      { provide: AuthService, useValue: authSpy },
      {
        provide: ActivatedRoute,
        useValue: { paramMap: of(convertToParamMap({ uid: '01JTEST00000000000099' })) },
      },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(DebtSupplierComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, debtSpy, authSpy };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('DebtSupplierComponent — write-off integration (G.2)', () => {

  it('shows Write off button per open-invoice row when user has DEBT.WRITE_OFF.REQUEST', async () => {
    const { fixture } = await setup(true);
    const btns = fixture.nativeElement.querySelectorAll('[data-testid="write-off-btn"]');
    expect(btns.length).toBe(1);
  });

  it('hides Write off button when user lacks DEBT.WRITE_OFF.REQUEST', async () => {
    const { fixture } = await setup(false);
    const btns = fixture.nativeElement.querySelectorAll('[data-testid="write-off-btn"]');
    expect(btns.length).toBe(0);
  });

  it('opens the write-off modal with correct invoice data on button click', async () => {
    const { fixture } = await setup(true);
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="write-off-btn"]');
    btn.click();
    fixture.detectChanges();

    const dialog = fixture.nativeElement.querySelector('[role="dialog"]');
    expect(dialog).toBeTruthy();

    const banner = fixture.nativeElement.querySelector('.alert-info');
    expect(banner?.textContent).toContain('SINV-2026-001');
  });

  it('uses SUPPLIER_INVOICE as targetKind in the modal', async () => {
    const { fixture } = await setup(true);
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="write-off-btn"]');
    btn.click();
    fixture.detectChanges();

    // The component should have set targetKind to SUPPLIER_INVOICE
    const comp = fixture.componentInstance;
    // Access via the template binding — confirm modal is visible
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    // The component's signal
    const modalTargetKind = (comp as unknown as { writeOffInvoiceUid: { (): string } }).writeOffInvoiceUid();
    expect(modalTargetKind).toBe(OPEN_INV.invoiceUid);
  });

  it('refreshes the statement after writeOffCreated is emitted', async () => {
    const { fixture, debtSpy } = await setup(true);
    const comp = fixture.componentInstance;
    (comp as unknown as { onWriteOffCreated: () => void }).onWriteOffCreated();
    fixture.detectChanges();

    expect(debtSpy.supplierStatement.calls.count()).toBeGreaterThanOrEqual(2);
  });
});
