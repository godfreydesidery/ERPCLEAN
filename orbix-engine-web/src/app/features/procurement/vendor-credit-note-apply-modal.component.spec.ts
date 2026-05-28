/**
 * Slice H.1 — VendorCreditNoteApplyModalComponent unit spec.
 * Mirrors credit-note-apply-modal.component.spec.ts from the sales side.
 * Covers: 4 states (hidden / loading→populated / empty / error),
 *         validation, success messages, 409 / 422 error mapping.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { VendorCreditNoteApplyModalComponent } from './vendor-credit-note-apply-modal.component';
import { ProcurementService } from './procurement.service';
import { VendorCreditNote } from './procurement.models';
import { OpenSupplierInvoiceRow } from '../debt/debt.models';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const CREDIT_NOTE: VendorCreditNote = {
  id: '201', uid: '01JTEST00000000000VCN1',
  number: 'VCN-2026-001',
  supplierId: '10', supplierUid: '01JTEST00000000000SUP',
  vendorReturnId: '1',
  cnDate: '2026-05-28', currencyCode: 'TZS',
  totalAmount: 120000, allocatedAmount: 0, availableAmount: 120000,
  status: 'POSTED', notes: null, allocations: null,
};

const OPEN_INVOICE: OpenSupplierInvoiceRow = {
  invoiceId: '301', invoiceUid: '01JTEST00000000000INV',
  number: 'SINV-2026-001',
  supplierInvoiceNo: 'EXT-001',
  invoiceDate: '2026-05-01', dueDate: '2026-05-31',
  totalAmount: 200000, paidAmount: 0, outstanding: 200000,
  daysOverdue: null, status: 'POSTED',
};

const PARTIALLY_APPLIED: VendorCreditNote = {
  ...CREDIT_NOTE,
  allocatedAmount: 80000, availableAmount: 40000,
  status: 'PARTIALLY_ALLOCATED',
};

const FULLY_APPLIED: VendorCreditNote = {
  ...CREDIT_NOTE,
  allocatedAmount: 120000, availableAmount: 0,
  status: 'FULLY_ALLOCATED',
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeProcSpy(
  invoices: OpenSupplierInvoiceRow[] = [OPEN_INVOICE],
  applyResult: VendorCreditNote = PARTIALLY_APPLIED,
): jasmine.SpyObj<ProcurementService> {
  const spy = jasmine.createSpyObj<ProcurementService>('ProcurementService', [
    'getSupplierOpenInvoices', 'applyVendorCreditNote',
  ]);
  spy.getSupplierOpenInvoices.and.returnValue(of(invoices));
  spy.applyVendorCreditNote.and.returnValue(of(applyResult));
  return spy;
}

async function setup(overrides: {
  visible?: boolean;
  creditNote?: VendorCreditNote;
  procSpy?: jasmine.SpyObj<ProcurementService>;
} = {}) {
  const spy = overrides.procSpy ?? makeProcSpy();

  await TestBed.configureTestingModule({
    imports: [VendorCreditNoteApplyModalComponent, HttpClientTestingModule],
    providers: [{ provide: ProcurementService, useValue: spy }],
  }).compileComponents();

  const fixture: ComponentFixture<VendorCreditNoteApplyModalComponent> =
    TestBed.createComponent(VendorCreditNoteApplyModalComponent);
  const comp = fixture.componentInstance;

  comp.visible = overrides.visible ?? true;
  comp.creditNote = overrides.creditNote ?? CREDIT_NOTE;
  comp.supplierUid = '01JTEST00000000000SUP';

  comp.ngOnChanges();
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp, spy };
}

async function stabilise(fixture: ComponentFixture<unknown>): Promise<void> {
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('VendorCreditNoteApplyModalComponent', () => {

  // -- Hidden state -----------------------------------------------------------
  describe('when visible = false', () => {
    it('does not render the modal', async () => {
      const { fixture } = await setup({ visible: false });
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    });
  });

  // -- Populated state --------------------------------------------------------
  describe('loading / populated state', () => {
    it('renders the dialog when visible', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    });

    it('shows the credit note number in the context banner', async () => {
      const { fixture } = await setup();
      const banner: HTMLElement = fixture.nativeElement.querySelector('.alert-info');
      expect(banner?.textContent).toContain('VCN-2026-001');
    });

    it('shows available amount in the context banner', async () => {
      const { fixture } = await setup();
      const banner: HTMLElement = fixture.nativeElement.querySelector('.alert-info');
      expect(banner?.textContent).toContain('120');
    });

    it('renders the invoice picker after invoices load', async () => {
      const { fixture } = await setup();
      const select: HTMLSelectElement = fixture.nativeElement.querySelector('select#vnInvoicePicker');
      expect(select).toBeTruthy();
    });

    it('lists open invoices in the picker', async () => {
      const { fixture } = await setup();
      const options: NodeListOf<HTMLOptionElement> =
        fixture.nativeElement.querySelectorAll('select#vnInvoicePicker option');
      expect(options.length).toBe(2); // 1 placeholder + 1 invoice
      expect(options[1].textContent).toContain('SINV-2026-001');
    });

    it('renders the amount input', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('input#vnApplyAmount')).toBeTruthy();
    });

    it('renders the submit button in the footer', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('button[type="submit"]')).toBeTruthy();
    });
  });

  // -- Empty state ------------------------------------------------------------
  describe('empty state (no open invoices)', () => {
    it('shows explainer text and hides the submit button', async () => {
      const emptySpy = makeProcSpy([]);
      const { fixture } = await setup({ procSpy: emptySpy });
      expect(fixture.nativeElement.textContent).toContain('No open supplier invoices');
      expect(fixture.nativeElement.querySelector('button[type="submit"]')).toBeNull();
    });
  });

  // -- Validation -------------------------------------------------------------
  describe('form validation', () => {
    it('shows error when no invoice is selected on submit', async () => {
      const { fixture, comp } = await setup();
      (comp as unknown as { amountDraft: number | null }).amountDraft = 10000;
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

    it('shows error when amount exceeds max', async () => {
      const { fixture, comp } = await setup();
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      // max = min(120000 available, 200000 outstanding) = 120000
      c.amountDraft = 250000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('cannot exceed');
    });
  });

  // -- Successful submission --------------------------------------------------
  describe('successful submission', () => {
    it('shows partial message when status is PARTIALLY_ALLOCATED', async () => {
      const spy = makeProcSpy([OPEN_INVOICE], PARTIALLY_APPLIED);
      const { fixture, comp } = await setup({ procSpy: spy });
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 80000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('40');
    });

    it('shows fully-allocated message when status is FULLY_ALLOCATED', async () => {
      const spy = makeProcSpy([OPEN_INVOICE], FULLY_APPLIED);
      const { fixture, comp } = await setup({ procSpy: spy });
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 120000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('fully allocated');
    });

    it('emits creditNoteApplied with the updated credit note', async () => {
      const spy = makeProcSpy([OPEN_INVOICE], PARTIALLY_APPLIED);
      const { fixture, comp } = await setup({ procSpy: spy });
      const emitted: VendorCreditNote[] = [];
      comp.creditNoteApplied.subscribe((cn: VendorCreditNote) => emitted.push(cn));

      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 80000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(emitted.length).toBe(1);
      expect(emitted[0].uid).toBe(PARTIALLY_APPLIED.uid);
      expect(emitted[0].status).toBe('PARTIALLY_ALLOCATED');
    });
  });

  // -- Error handling ---------------------------------------------------------
  describe('error handling', () => {
    it('maps 409 to a banner error about full allocation', async () => {
      const spy = jasmine.createSpyObj<ProcurementService>('ProcurementService', [
        'getSupplierOpenInvoices', 'applyVendorCreditNote',
      ]);
      spy.getSupplierOpenInvoices.and.returnValue(of([OPEN_INVOICE]));
      spy.applyVendorCreditNote.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 409, statusText: 'Conflict' }))
      );

      const { fixture, comp } = await setup({ procSpy: spy });
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 10000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('fully allocated');
    });

    it('maps 422 to an amount field error', async () => {
      const spy = jasmine.createSpyObj<ProcurementService>('ProcurementService', [
        'getSupplierOpenInvoices', 'applyVendorCreditNote',
      ]);
      spy.getSupplierOpenInvoices.and.returnValue(of([OPEN_INVOICE]));
      spy.applyVendorCreditNote.and.returnValue(
        throwError(() => new HttpErrorResponse({
          status: 422,
          error: { message: 'Amount exceeds available credit.' },
        }))
      );

      const { fixture, comp } = await setup({ procSpy: spy });
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 5000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('Amount exceeds available credit');
    });

    it('shows a generic banner for unexpected server errors', async () => {
      const spy = jasmine.createSpyObj<ProcurementService>('ProcurementService', [
        'getSupplierOpenInvoices', 'applyVendorCreditNote',
      ]);
      spy.getSupplierOpenInvoices.and.returnValue(of([OPEN_INVOICE]));
      spy.applyVendorCreditNote.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500 }))
      );

      const { fixture, comp } = await setup({ procSpy: spy });
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 5000;
      await stabilise(fixture);

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      await stabilise(fixture);

      expect(fixture.nativeElement.textContent).toContain('Could not apply the credit note');
    });

    it('shows a banner when the open-invoice fetch fails', async () => {
      const spy = jasmine.createSpyObj<ProcurementService>('ProcurementService', [
        'getSupplierOpenInvoices', 'applyVendorCreditNote',
      ]);
      spy.getSupplierOpenInvoices.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 503 }))
      );

      const { fixture } = await setup({ procSpy: spy });
      expect(fixture.nativeElement.textContent).toContain('Could not load open invoices');
    });
  });

  // -- ngOnChanges reset -------------------------------------------------------
  describe('ngOnChanges', () => {
    it('resets the form when the modal is re-opened', async () => {
      const { fixture, comp } = await setup();
      const c = comp as unknown as { selectedInvoiceUid: string; amountDraft: number | null };
      c.selectedInvoiceUid = OPEN_INVOICE.invoiceUid;
      c.amountDraft = 60000;
      await stabilise(fixture);

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
