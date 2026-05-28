import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { DebtCustomerComponent } from './debt-customer.component';
import { DebtService } from './debt.service';
import { AuthService } from '../../core/auth/auth.service';
import { CustomerStatement, PartyNote, OpenInvoiceRow } from './debt.models';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------
const OPEN_INVOICE: OpenInvoiceRow = {
  invoiceId: '500', invoiceUid: '01JTEST00000000000INV',
  number: 'INV-2026-001',
  invoiceDate: '2026-04-01', dueDate: '2026-05-01',
  totalAmount: 100000, paidAmount: 20000, outstanding: 80000,
  daysOverdue: 27, status: 'POSTED',
};

const STATEMENT: CustomerStatement = {
  customerId: '1', customerUid: '01JTEST00000000000001',
  customerName: 'Test Customer Ltd',
  currencyCode: 'TZS', creditLimit: 500000,
  totalOutstanding: 80000, creditUtilisation: 0.16,
  openInvoiceCount: 1, overdueInvoiceCount: 1,
  asOf: '2026-05-28',
  openInvoices: [OPEN_INVOICE],
  recentReceipts: [],
};

function makeDebtSpy(): jasmine.SpyObj<DebtService> {
  const spy = jasmine.createSpyObj<DebtService>('DebtService', [
    'customerStatement', 'listNotes',
  ]);
  spy.customerStatement.and.returnValue(of(STATEMENT));
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
    imports: [DebtCustomerComponent, HttpClientTestingModule],
    providers: [
      provideRouter([]),
      { provide: DebtService, useValue: debtSpy },
      { provide: AuthService, useValue: authSpy },
      {
        provide: ActivatedRoute,
        useValue: { paramMap: of(convertToParamMap({ uid: '01JTEST00000000000001' })) },
      },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(DebtCustomerComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, debtSpy, authSpy };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('DebtCustomerComponent — write-off integration (G.2)', () => {

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

    // Modal should become visible
    const dialog = fixture.nativeElement.querySelector('[role="dialog"]');
    expect(dialog).toBeTruthy();

    // Invoice context banner should show the invoice number
    const banner = fixture.nativeElement.querySelector('.alert-info');
    expect(banner?.textContent).toContain('INV-2026-001');
  });

  it('refreshes the statement after writeOffCreated is emitted', async () => {
    const { fixture, debtSpy } = await setup(true);
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="write-off-btn"]');
    btn.click();
    fixture.detectChanges();

    // Simulate writeOffCreated event from child component
    const comp = fixture.componentInstance;
    (comp as unknown as { onWriteOffCreated: () => void }).onWriteOffCreated();
    fixture.detectChanges();

    // customerStatement should have been called again (initial + refresh)
    expect(debtSpy.customerStatement.calls.count()).toBeGreaterThanOrEqual(2);
  });
});
