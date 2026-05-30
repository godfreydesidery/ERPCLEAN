import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { CreditNoteApplyModalComponent } from './credit-note-apply-modal.component';
import { SalesService } from './sales.service';
import { CustomerCreditNote } from './sales.models';
import { OpenInvoiceRow } from '../debt/debt.models';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const CREDIT_NOTE: CustomerCreditNote = {
  id: '201', uid: '01JTEST00000000000CN1',
  number: 'CN-2026-001',
  companyId: '1', branchId: '10',
  customerId: '42', customerName: 'Acme Ltd',
  customerReturnId: '101',
  cnDate: '2026-05-28',
  currencyCode: 'TZS',
  totalAmount: 50000, allocatedAmount: 0, availableAmount: 50000,
  status: 'POSTED', notes: null, allocations: null,
};

const OPEN_INVOICE: OpenInvoiceRow = {
  invoiceId: '301', invoiceUid: '01JTEST00000000000INV',
  number: 'INV-2026-001',
  invoiceDate: '2026-05-01', dueDate: '2026-05-31',
  totalAmount: 80000, paidAmount: 0, outstanding: 80000,
  daysOverdue: null, status: 'POSTED',
};

const PARTIALLY_APPLIED_CN: CustomerCreditNote = {
  ...CREDIT_NOTE,
  allocatedAmount: 30000, availableAmount: 20000,
  status: 'PARTIALLY_ALLOCATED',
};

const FULLY_APPLIED_CN: CustomerCreditNote = {
  ...CREDIT_NOTE,
  allocatedAmount: 50000, availableAmount: 0,
  status: 'FULLY_ALLOCATED',
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeSalesSpy(
  invoices: OpenInvoiceRow[] = [OPEN_INVOICE],
  applyResult: CustomerCreditNote = PARTIALLY_APPLIED_CN,
): jasmine.SpyObj<SalesService> {
  const spy = jasmine.createSpyObj<SalesService>('SalesService', [
    'getOpenInvoicesForCustomer', 'applyCreditNote',
  ]);
  spy.getOpenInvoicesForCustomer.and.returnValue(of(invoices));
  spy.applyCreditNote.and.returnValue(of(applyResult));
  return spy;
}

async function setup(
  overrides: {
    visible?: boolean;
    creditNote?: CustomerCreditNote;
    salesSpy?: jasmine.SpyObj<SalesService>;
  } = {}
) {
  const spy = overrides.salesSpy ?? makeSalesSpy();

  await TestBed.configureTestingModule({
    imports: [CreditNoteApplyModalComponent, HttpClientTestingModule],
    providers: [{ provide: SalesService, useValue: spy }],
  }).compileComponents();

  const fixture: ComponentFixture<CreditNoteApplyModalComponent> =
    TestBed.createComponent(CreditNoteApplyModalComponent);
  const comp = fixture.componentInstance;

  comp.visible = overrides.visible ?? true;
  comp.creditNote = overrides.creditNote ?? CREDIT_NOTE;
  comp.customerUid = '01JTEST00000000000CUST';

  // Manually call ngOnChanges: Angular won't call it for direct property
  // assignment in tests (only for @Input bindings in templates). This
  // triggers fetchInvoices() so the openInvoices signal populates.
  comp.ngOnChanges();

  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp, spy };
}

/** Resolve all pending microtasks + re-render. */
async function stabilise(fixture: ComponentFixture<unknown>): Promise<void> {
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('CreditNoteApplyModalComponent', () => {

  // -- State: hidden ---------------------------------------------------------
  describe('when visible = false', () => {
    it('does not render the modal', async () => {
      const { fixture } = await setup({ visible: false });
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    });
  });

  // -- State: loading then populated -----------------------------------------
  describe('loading / populated state', () => {
    it('renders the dialog when visible', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    });

    it('shows the credit note number in the context banner', async () => {
      const { fixture } = await setup();
      const banner: HTMLElement = fixture.nativeElement.querySelector('.alert-info');
      expect(banner?.textContent).toContain('CN-2026-001');
    });

    it('shows available amount in the context banner', async () => {
      const { fixture } = await setup();
      const banner: HTMLElement = fixture.nativeElement.querySelector('.alert-info');
      expect(banner?.textContent).toContain('50');
    });

    it('renders the invoice picker select after invoices load', async () => {
      const { fixture } = await setup();
      const select: HTMLSelectElement = fixture.nativeElement.querySelector('select#cnInvoicePicker');
      expect(select).toBeTruthy();
    });

    it('lists open invoices in the picker', async () => {
      const { fixture } = await setup();
      const options: NodeListOf<HTMLOptionElement> =
        fixture.nativeElement.querySelectorAll('select#cnInvoicePicker option');
      expect(options.length).toBe(2); // 1 placeholder + 1 invoice
      expect(options[1].textContent).toContain('INV-2026-001');
    });

    it('renders the amount input', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('input#cnApplyAmount')).toBeTruthy();
    });

    it('renders the submit button in the footer', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('button[type="submit"]')).toBeTruthy();
    });
  });

  // -- State: empty ----------------------------------------------------------
  describe('empty state (no open invoices)', () => {
    it('shows explainer text and hides the submit button', async () => {
      const emptySpy = makeSalesSpy([]);
      const { fixture } = await setup({ salesSpy: emptySpy });

      expect(fixture.nativeElement.textContent).toContain('No open invoices');
      expect(fixture.nativeElement.querySelector('button[type="submit"]')).toBeNull();
    });
  });

  // -- Validation ------------------------------------------------------------
  describe('form validation', () => {
    it('shows error when no invoice is selected on submit', async () => {
      const { fixture, comp } = await setup();
      (comp as unknown as { amountDraft: number | null }).amountDraft = 1000;
      await stabilise(fixture);

      const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
      expect(btn).withContext('submit button must be present').toBeTruthy();
      btn.click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('Please select an open invoice');
    });

    it('shows error when amount is zero', async () => {
      const { fixture, comp } = await setup();
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 0;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('at least 0.01');
    });

    it('shows error when amount exceeds max (min of available and outstanding)', async () => {
      const { fixture, comp } = await setup();
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      // max = min(50000 available, 80000 outstanding) = 50000
      c.amountDraft = 99999;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('cannot exceed');
    });
  });

  // -- Successful submission -------------------------------------------------
  describe('successful submission', () => {
    it('shows partial message when status is PARTIALLY_ALLOCATED', async () => {
      const spy = makeSalesSpy([OPEN_INVOICE], PARTIALLY_APPLIED_CN);
      const { fixture, comp } = await setup({ salesSpy: spy });
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 30000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      // Success message shows remaining credit
      expect(fixture.nativeElement.textContent).toContain('20');
    });

    it('shows fully-allocated message when status is FULLY_ALLOCATED', async () => {
      const spy = makeSalesSpy([OPEN_INVOICE], FULLY_APPLIED_CN);
      const { fixture, comp } = await setup({ salesSpy: spy });
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 50000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('fully allocated');
    });

    it('emits creditNoteApplied with the updated credit note', async () => {
      const spy = makeSalesSpy([OPEN_INVOICE], PARTIALLY_APPLIED_CN);
      const { fixture, comp } = await setup({ salesSpy: spy });
      const emitted: CustomerCreditNote[] = [];
      comp.creditNoteApplied.subscribe((cn: CustomerCreditNote) => emitted.push(cn));

      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 30000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(emitted.length).toBe(1);
      expect(emitted[0].uid).toBe(PARTIALLY_APPLIED_CN.uid);
      expect(emitted[0].status).toBe('PARTIALLY_ALLOCATED');
    });
  });

  // -- Error handling --------------------------------------------------------
  describe('error handling', () => {
    it('maps 409 to a banner error about full allocation', async () => {
      const spy = jasmine.createSpyObj<SalesService>('SalesService', [
        'getOpenInvoicesForCustomer', 'applyCreditNote',
      ]);
      spy.getOpenInvoicesForCustomer.and.returnValue(of([OPEN_INVOICE]));
      spy.applyCreditNote.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 409, statusText: 'Conflict' }))
      );

      const { fixture, comp } = await setup({ salesSpy: spy });
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 10000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('fully allocated');
    });

    it('maps 422 with FULLY_ALLOCATED message to a banner error', async () => {
      const spy = jasmine.createSpyObj<SalesService>('SalesService', [
        'getOpenInvoicesForCustomer', 'applyCreditNote',
      ]);
      spy.getOpenInvoicesForCustomer.and.returnValue(of([OPEN_INVOICE]));
      spy.applyCreditNote.and.returnValue(
        throwError(() => new HttpErrorResponse({
          status: 422,
          error: {
            message: 'Credit note 01KTEST0000000000000000000 is FULLY_ALLOCATED; only POSTED or PARTIALLY_ALLOCATED notes can be applied',
          },
        }))
      );

      const { fixture, comp } = await setup({ salesSpy: spy });
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 10000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      // Must render as banner (alert-danger), not inline field error
      const banner: HTMLElement = fixture.nativeElement.querySelector('.alert-danger');
      expect(banner).withContext('banner error must be present').toBeTruthy();
      expect(banner.textContent).toContain('fully allocated');
      // No inline amountError
      expect(fixture.nativeElement.querySelector('.invalid-feedback')).toBeNull();
    });

    it('maps 422 without FULLY_ALLOCATED message to an amount field error', async () => {
      const spy = jasmine.createSpyObj<SalesService>('SalesService', [
        'getOpenInvoicesForCustomer', 'applyCreditNote',
      ]);
      spy.getOpenInvoicesForCustomer.and.returnValue(of([OPEN_INVOICE]));
      spy.applyCreditNote.and.returnValue(
        throwError(() => new HttpErrorResponse({
          status: 422,
          error: { message: 'Amount exceeds available credit.' },
        }))
      );

      const { fixture, comp } = await setup({ salesSpy: spy });
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 5000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('Amount exceeds available credit');
    });

    it('shows a generic banner for unexpected server errors', async () => {
      const spy = jasmine.createSpyObj<SalesService>('SalesService', [
        'getOpenInvoicesForCustomer', 'applyCreditNote',
      ]);
      spy.getOpenInvoicesForCustomer.and.returnValue(of([OPEN_INVOICE]));
      spy.applyCreditNote.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' }))
      );

      const { fixture, comp } = await setup({ salesSpy: spy });
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 5000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('Could not apply the credit note');
    });

    it('shows a banner when the open-invoice fetch fails', async () => {
      const spy = jasmine.createSpyObj<SalesService>('SalesService', [
        'getOpenInvoicesForCustomer', 'applyCreditNote',
      ]);
      spy.getOpenInvoicesForCustomer.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 503 }))
      );

      const { fixture } = await setup({ salesSpy: spy });

      expect(fixture.nativeElement.textContent).toContain('Could not load open invoices');
    });
  });

  // -- ngOnChanges -----------------------------------------------------------
  describe('ngOnChanges', () => {
    it('resets the form when the modal is re-opened', async () => {
      const { fixture, comp } = await setup();
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 25000;
      await stabilise(fixture);

      // Simulate close + re-open
      comp.visible = false;
      comp.ngOnChanges();
      comp.visible = true;
      comp.ngOnChanges();
      await stabilise(fixture);

      expect(c.selectedInvoiceUid).toBe('');
      expect(c.amountDraft).toBeNull();
    });
  });
});
