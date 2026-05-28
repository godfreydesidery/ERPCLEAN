import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { DebtWriteOffModalComponent } from './debt-write-off-modal.component';
import { DebtService } from './debt.service';
import { DebtWriteOff } from './debt.models';

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------
const POSTED_WRITE_OFF: DebtWriteOff = {
  id: '1001', uid: '01JTEST00000000000WO1',
  targetKind: 'CUSTOMER_INVOICE',
  targetInvoiceId: '500', targetInvoiceUid: '01JTEST00000000000INV',
  targetInvoiceNumber: 'INV-2026-001', partyName: 'Test Customer Ltd',
  amount: 50000, currencyCode: 'TZS', reason: 'Liquidated.',
  status: 'POSTED',
  requestedByUserId: '10', requestedByUsername: 'accountant',
  requestedAt: '2026-05-28T08:00:00Z',
  approvedByUserId: '10', approvedByUsername: 'accountant',
  approvedAt: '2026-05-28T08:00:01Z',
  postedAt: '2026-05-28T08:00:01Z',
  rejectedAt: null, reasonForReject: null,
};

const PENDING_WRITE_OFF: DebtWriteOff = {
  ...POSTED_WRITE_OFF,
  status: 'PENDING_APPROVAL',
  approvedByUserId: null, approvedByUsername: null,
  approvedAt: null, postedAt: null,
};

function makeDebtSpy(result: DebtWriteOff = POSTED_WRITE_OFF): jasmine.SpyObj<DebtService> {
  const spy = jasmine.createSpyObj<DebtService>('DebtService', ['createWriteOff']);
  spy.createWriteOff.and.returnValue(of(result));
  return spy;
}

async function setup(
  overrides: Partial<{ outstanding: number; visible: boolean }> = {},
  debtSpy?: jasmine.SpyObj<DebtService>
) {
  const spy = debtSpy ?? makeDebtSpy();

  await TestBed.configureTestingModule({
    imports: [DebtWriteOffModalComponent, HttpClientTestingModule],
    providers: [{ provide: DebtService, useValue: spy }],
  }).compileComponents();

  const fixture: ComponentFixture<DebtWriteOffModalComponent> =
    TestBed.createComponent(DebtWriteOffModalComponent);
  const comp = fixture.componentInstance;

  comp.visible = overrides.visible ?? true;
  comp.targetKind = 'CUSTOMER_INVOICE';
  comp.targetInvoiceUid = '01JTEST00000000000INV';
  comp.targetInvoiceNumber = 'INV-2026-001';
  comp.outstanding = overrides.outstanding ?? 50000;

  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp, spy };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('DebtWriteOffModalComponent', () => {

  describe('when visible = true', () => {
    it('renders the modal dialog', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    });

    it('pre-fills amount input with the outstanding value', async () => {
      const { fixture, comp } = await setup({ outstanding: 75000 });
      // ngOnChanges is driven by Angular's change detection on @Input change.
      // Simulate a re-open with a fresh outstanding value by calling it directly.
      comp.outstanding = 75000;
      comp.ngOnChanges();
      fixture.detectChanges();
      const c = comp as unknown as { amountDraft: number | null };
      expect(c.amountDraft).toBe(75000);
    });

    it('shows the invoice number in the context banner', async () => {
      const { fixture } = await setup();
      const banner: HTMLElement = fixture.nativeElement.querySelector('.alert-info');
      expect(banner?.textContent).toContain('INV-2026-001');
    });
  });

  describe('when visible = false', () => {
    it('does not render the modal', async () => {
      const { fixture } = await setup({ visible: false });
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    });
  });

  describe('form validation', () => {
    it('shows an error when amount is zero on submit', async () => {
      const { fixture, comp } = await setup({ outstanding: 50000 });
      (comp as unknown as { amountDraft: number | null }).amountDraft = 0;
      fixture.detectChanges();

      const submitBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
      submitBtn.click();
      fixture.detectChanges();

      const errorEl = fixture.nativeElement.querySelector('.invalid-feedback');
      expect(errorEl?.textContent).toContain('at least 0.01');
    });

    it('shows an error when amount exceeds outstanding', async () => {
      const { fixture, comp } = await setup({ outstanding: 50000 });
      (comp as unknown as { amountDraft: number | null }).amountDraft = 99999;
      fixture.detectChanges();

      const submitBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
      submitBtn.click();
      fixture.detectChanges();

      const errorEl = fixture.nativeElement.querySelector('.invalid-feedback');
      expect(errorEl?.textContent).toContain('cannot exceed');
    });

    it('shows an error when reason is empty on submit', async () => {
      const { fixture, comp } = await setup({ outstanding: 50000 });
      const c = comp as unknown as { amountDraft: number | null; reasonDraft: string };
      c.amountDraft = 25000;
      c.reasonDraft = '';
      fixture.detectChanges();

      const submitBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
      submitBtn.click();
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('Reason is required');
    });
  });

  describe('successful submission', () => {
    it('shows auto-posted success message when status is POSTED', async () => {
      const spy = makeDebtSpy(POSTED_WRITE_OFF);
      const { fixture, comp } = await setup({}, spy);
      const c = comp as unknown as { amountDraft: number | null; reasonDraft: string };
      c.amountDraft = 50000;
      c.reasonDraft = 'Customer has been liquidated.';
      fixture.detectChanges();

      const submitBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
      submitBtn.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('auto-posted');
    });

    it('shows pending message when status is PENDING_APPROVAL', async () => {
      const spy = makeDebtSpy(PENDING_WRITE_OFF);
      const { fixture, comp } = await setup({}, spy);
      const c = comp as unknown as { amountDraft: number | null; reasonDraft: string };
      c.amountDraft = 50000;
      c.reasonDraft = 'Needs approval.';
      fixture.detectChanges();

      const submitBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
      submitBtn.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('awaiting approval');
    });

    it('emits writeOffCreated event with the returned write-off', async () => {
      const spy = makeDebtSpy(POSTED_WRITE_OFF);
      const { fixture, comp } = await setup({}, spy);
      const emitted: DebtWriteOff[] = [];
      comp.writeOffCreated.subscribe((wo: DebtWriteOff) => emitted.push(wo));

      const c = comp as unknown as { amountDraft: number | null; reasonDraft: string };
      c.amountDraft = 50000;
      c.reasonDraft = 'Liquidated.';
      fixture.detectChanges();

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(emitted.length).toBe(1);
      expect(emitted[0].uid).toBe(POSTED_WRITE_OFF.uid);
    });
  });

  describe('error handling', () => {
    it('maps 409 to a banner error about dual-approval gate', async () => {
      const spy = jasmine.createSpyObj<DebtService>('DebtService', ['createWriteOff']);
      spy.createWriteOff.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 409, statusText: 'Conflict' }))
      );
      // outstanding must be >= amount so client-side validation passes and the HTTP call fires
      const { fixture, comp } = await setup({ outstanding: 500000 }, spy);
      const c = comp as unknown as { amountDraft: number | null; reasonDraft: string };
      c.amountDraft = 200000;
      c.reasonDraft = 'Above-threshold write-off request.';
      fixture.detectChanges();

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('different user');
    });

    it('maps 422 to an amount field error', async () => {
      const spy = jasmine.createSpyObj<DebtService>('DebtService', ['createWriteOff']);
      spy.createWriteOff.and.returnValue(
        throwError(() => new HttpErrorResponse({
          status: 422,
          error: { message: 'Amount exceeds outstanding balance.' },
        }))
      );
      // outstanding must be >= amount so client-side validation passes and the HTTP call fires
      const { fixture, comp } = await setup({ outstanding: 999999 }, spy);
      const c = comp as unknown as { amountDraft: number | null; reasonDraft: string };
      c.amountDraft = 99999;
      c.reasonDraft = 'Test reason.';
      fixture.detectChanges();

      fixture.nativeElement.querySelector('button[type="submit"]').click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('exceeds outstanding');
    });
  });

  describe('ngOnChanges', () => {
    it('resets form fields when modal is re-opened', async () => {
      const { fixture, comp } = await setup();
      const c = comp as unknown as { amountDraft: number | null; reasonDraft: string };
      c.amountDraft = 1;
      c.reasonDraft = 'old value';
      fixture.detectChanges();

      // Simulate re-open
      comp.visible = false;
      comp.ngOnChanges();
      comp.visible = true;
      comp.outstanding = 80000;
      comp.ngOnChanges();
      fixture.detectChanges();

      expect(c.amountDraft).toBe(80000);
      expect(c.reasonDraft).toBe('');
    });
  });
});
