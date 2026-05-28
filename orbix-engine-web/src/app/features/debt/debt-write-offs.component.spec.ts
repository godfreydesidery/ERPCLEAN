import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { DebtWriteOffsComponent } from './debt-write-offs.component';
import { DebtService } from './debt.service';
import { AuthService } from '../../core/auth/auth.service';
import { DebtWriteOff, RejectDebtWriteOffRequest } from './debt.models';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------
const PENDING: DebtWriteOff = {
  id: '1001', uid: '01JTEST00000000000WO1',
  targetKind: 'CUSTOMER_INVOICE',
  targetInvoiceId: '500', targetInvoiceUid: '01JTEST00000000000INV',
  targetInvoiceNumber: 'INV-2026-001', partyName: 'Test Customer Ltd',
  amount: 50000, currencyCode: 'TZS', reason: 'Liquidated.',
  status: 'PENDING_APPROVAL',
  requestedByUserId: '10', requestedByUsername: 'accountant',
  requestedAt: '2026-05-28T08:00:00Z',
  approvedByUserId: null, approvedByUsername: null,
  approvedAt: null, postedAt: null, rejectedAt: null, reasonForReject: null,
};

const POSTED: DebtWriteOff = {
  ...PENDING, uid: '01JTEST00000000000WO2',
  status: 'POSTED',
  approvedByUserId: '11', approvedByUsername: 'approver',
  approvedAt: '2026-05-28T09:00:00Z', postedAt: '2026-05-28T09:00:00Z',
};

const REJECTED: DebtWriteOff = {
  ...PENDING, uid: '01JTEST00000000000WO3',
  status: 'REJECTED',
  approvedByUserId: '11', approvedByUsername: 'approver',
  rejectedAt: '2026-05-28T09:30:00Z',
  reasonForReject: 'Insufficient docs.',
};

function makePage(rows: DebtWriteOff[]): Page<DebtWriteOff> {
  return { content: rows, page: 0, size: 25, totalElements: rows.length, totalPages: 1 };
}

function makeDebtSpy(rows: DebtWriteOff[] = [PENDING]): jasmine.SpyObj<DebtService> {
  const spy = jasmine.createSpyObj<DebtService>('DebtService', [
    'listWriteOffs', 'approveWriteOff', 'rejectWriteOff',
  ]);
  spy.listWriteOffs.and.returnValue(of(makePage(rows)));
  spy.approveWriteOff.and.returnValue(of({ ...PENDING, status: 'POSTED' } as DebtWriteOff));
  spy.rejectWriteOff.and.returnValue(of({ ...PENDING, status: 'REJECTED', reasonForReject: 'Bad docs.' } as DebtWriteOff));
  return spy;
}

function makeAuthSpy(hasApprove = true): jasmine.SpyObj<AuthService> {
  const spy = jasmine.createSpyObj<AuthService>('AuthService', ['hasPermission']);
  spy.hasPermission.and.callFake((perm: string) => {
    if (perm === 'DEBT.WRITE_OFF.APPROVE') return hasApprove;
    return true;
  });
  return spy;
}

/** Helper: find a button by text content. */
function findBtn(el: HTMLElement, text: string): HTMLButtonElement | undefined {
  return Array.from(el.querySelectorAll<HTMLButtonElement>('button'))
    .find(b => b.textContent?.trim().includes(text));
}

async function setup(rows: DebtWriteOff[] = [PENDING], hasApprove = true) {
  const debtSpy = makeDebtSpy(rows);
  const authSpy = makeAuthSpy(hasApprove);

  await TestBed.configureTestingModule({
    imports: [DebtWriteOffsComponent, HttpClientTestingModule],
    providers: [
      provideRouter([]),
      { provide: DebtService, useValue: debtSpy },
      { provide: AuthService, useValue: authSpy },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(DebtWriteOffsComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, debtSpy, authSpy };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('DebtWriteOffsComponent', () => {

  describe('populated state', () => {
    it('renders the write-offs table with one row', async () => {
      const { fixture } = await setup([PENDING]);
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="write-offs-table"]')).toBeTruthy();
      const rows = el.querySelectorAll('[data-testid="write-off-row"]');
      expect(rows.length).toBe(1);
    });

    it('shows PENDING_APPROVAL badge for a pending row', async () => {
      const { fixture } = await setup([PENDING]);
      const badge = fixture.nativeElement.querySelector('.badge.text-bg-warning');
      expect(badge?.textContent?.trim()).toBe('Pending');
    });

    it('shows POSTED badge for a posted row', async () => {
      const { fixture } = await setup([POSTED]);
      const badge = fixture.nativeElement.querySelector('.badge.text-bg-success');
      expect(badge?.textContent?.trim()).toBe('Posted');
    });

    it('shows REJECTED badge for a rejected row', async () => {
      const { fixture } = await setup([REJECTED]);
      const badge = fixture.nativeElement.querySelector('.badge.text-bg-secondary');
      expect(badge?.textContent?.trim()).toBe('Rejected');
    });

    it('shows AR chip for CUSTOMER_INVOICE rows', async () => {
      const { fixture } = await setup([PENDING]);
      const chip = fixture.nativeElement.querySelector('.badge.text-bg-info');
      expect(chip?.textContent?.trim()).toBe('AR');
    });
  });

  describe('empty state', () => {
    it('renders the empty panel when no rows returned', async () => {
      const { fixture } = await setup([]);
      expect(fixture.nativeElement.querySelector('[data-testid="write-offs-empty"]')).toBeTruthy();
    });
  });

  describe('error state', () => {
    it('renders an error banner on HTTP failure', async () => {
      const debtSpy = makeDebtSpy();
      debtSpy.listWriteOffs.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' }))
      );
      await TestBed.configureTestingModule({
        imports: [DebtWriteOffsComponent, HttpClientTestingModule],
        providers: [
          provideRouter([]),
          { provide: DebtService, useValue: debtSpy },
          { provide: AuthService, useValue: makeAuthSpy() },
        ],
      }).compileComponents();
      const fixture = TestBed.createComponent(DebtWriteOffsComponent);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="write-offs-error"]')).toBeTruthy();
    });

    it('renders permission denied panel on 403', async () => {
      const debtSpy = makeDebtSpy();
      debtSpy.listWriteOffs.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 403, statusText: 'Forbidden' }))
      );
      await TestBed.configureTestingModule({
        imports: [DebtWriteOffsComponent, HttpClientTestingModule],
        providers: [
          provideRouter([]),
          { provide: DebtService, useValue: debtSpy },
          { provide: AuthService, useValue: makeAuthSpy() },
        ],
      }).compileComponents();
      const fixture = TestBed.createComponent(DebtWriteOffsComponent);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="write-offs-permission-required"]')).toBeTruthy();
    });
  });

  describe('filter combinations', () => {
    it('re-fetches with PENDING_APPROVAL status filter when chip clicked', async () => {
      const { fixture, debtSpy } = await setup([PENDING]);
      findBtn(fixture.nativeElement, 'Pending')?.click();
      fixture.detectChanges();
      expect(debtSpy.listWriteOffs).toHaveBeenCalledWith('PENDING_APPROVAL', undefined, 0, 25);
    });

    it('re-fetches with AR kind filter when chip clicked', async () => {
      const { fixture, debtSpy } = await setup([PENDING]);
      findBtn(fixture.nativeElement, 'AR')?.click();
      fixture.detectChanges();
      expect(debtSpy.listWriteOffs).toHaveBeenCalledWith(undefined, 'CUSTOMER_INVOICE', 0, 25);
    });

    it('clears filters and re-fetches on clear button click', async () => {
      const { fixture, debtSpy } = await setup([PENDING]);
      findBtn(fixture.nativeElement, 'Pending')?.click();
      fixture.detectChanges();

      findBtn(fixture.nativeElement, 'Clear filters')?.click();
      fixture.detectChanges();
      const calls = debtSpy.listWriteOffs.calls.all();
      const lastCall = calls[calls.length - 1];
      expect(lastCall.args[0]).toBeUndefined();
      expect(lastCall.args[1]).toBeUndefined();
    });
  });

  describe('approve action', () => {
    it('calls approveWriteOff with the row uid when Approve clicked', async () => {
      const { fixture, debtSpy } = await setup([PENDING], true);
      findBtn(fixture.nativeElement, 'Approve')?.click();
      fixture.detectChanges();
      expect(debtSpy.approveWriteOff).toHaveBeenCalledWith(PENDING.uid);
    });
  });

  describe('reject action', () => {
    it('calls rejectWriteOff with uid and reason after modal submit', async () => {
      const { fixture, debtSpy } = await setup([PENDING], true);

      // Open the reject modal by calling the component method directly — avoids
      // button-text matching brittleness from icon content.
      type RejectComp = {
        openRejectModal: (uid: string) => void;
        rejectReasonDraft: string;
        submitReject: () => void;
      };
      const comp = fixture.componentInstance as unknown as RejectComp;
      comp.openRejectModal(PENDING.uid);
      fixture.detectChanges();

      // Set the reason directly and submit
      comp.rejectReasonDraft = 'Insufficient documentation.';
      comp.submitReject();
      fixture.detectChanges();
      await fixture.whenStable();

      const req: RejectDebtWriteOffRequest = { reasonForReject: 'Insufficient documentation.' };
      expect(debtSpy.rejectWriteOff).toHaveBeenCalledWith(PENDING.uid, req);
    });
  });

  describe('permission gating', () => {
    it('hides Approve/Reject buttons when user lacks DEBT.WRITE_OFF.APPROVE', async () => {
      const { fixture } = await setup([PENDING], false);
      const btn = findBtn(fixture.nativeElement, 'Approve');
      expect(btn).toBeUndefined();
    });

    it('shows Approve/Reject buttons when user has DEBT.WRITE_OFF.APPROVE', async () => {
      const { fixture } = await setup([PENDING], true);
      const btn = findBtn(fixture.nativeElement, 'Approve');
      expect(btn).toBeTruthy();
    });
  });

  describe('row expansion', () => {
    it('toggles detail panel on row click', async () => {
      const { fixture } = await setup([PENDING]);
      expect(fixture.nativeElement.querySelector('[data-testid="write-off-detail"]')).toBeNull();

      const row: HTMLTableRowElement = fixture.nativeElement.querySelector('[data-testid="write-off-row"]');
      row.click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="write-off-detail"]')).toBeTruthy();

      row.click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="write-off-detail"]')).toBeNull();
    });
  });
});
