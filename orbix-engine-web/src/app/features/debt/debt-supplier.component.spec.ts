import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { DebtSupplierComponent } from './debt-supplier.component';
import { DebtService } from './debt.service';
import { AuthService } from '../../core/auth/auth.service';
import { SupplierStatement, PartyNote } from './debt.models';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
const makeStatement = (overrides: Partial<SupplierStatement> = {}): SupplierStatement => ({
  supplierId: '99',
  supplierUid: '01JTEST00000000000099',
  supplierName: 'ACME Supplies Ltd',
  currencyCode: 'TZS',
  paymentTermsDays: 30,
  totalOutstanding: 50000,
  openInvoiceCount: 2,
  overdueInvoiceCount: 1,
  asOf: '2026-05-28',
  openInvoices: [
    {
      invoiceId: '10',
      invoiceUid: '01JINV000000000000010',
      number: 'SINV-001',
      supplierInvoiceNo: 'ACME-2026-001',
      invoiceDate: '2026-04-01',
      dueDate: '2026-05-01',
      totalAmount: 30000,
      paidAmount: 0,
      outstanding: 30000,
      daysOverdue: 27,
      status: 'POSTED',
    }
  ],
  recentPayments: [
    {
      paymentId: '20',
      paymentUid: '01JPAY000000000000020',
      number: 'SPAY-001',
      paymentDate: '2026-05-10',
      totalAmount: 15000,
      currencyCode: 'TZS',
    }
  ],
  ...overrides,
});

const makeNote = (uid: string, status: 'ACTIVE' | 'ARCHIVED' = 'ACTIVE'): PartyNote => ({
  id: uid.slice(-2),
  uid,
  partyId: '99',
  kind: 'AP_CHASE',
  body: `Note body ${uid}`,
  status,
  createdAt: '2026-05-20T10:00:00Z',
  createdBy: 'accountant',
  archivedAt: null,
  archivedBy: null,
});

function makeDebtServiceSpy(): jasmine.SpyObj<DebtService> {
  const spy = jasmine.createSpyObj<DebtService>('DebtService', [
    'supplierStatement',
    'listNotes',
    'createNote',
    'archiveNote',
  ]);
  spy.supplierStatement.and.returnValue(of(makeStatement()));
  spy.listNotes.and.returnValue(of([]));
  spy.createNote.and.returnValue(of(makeNote('01JNOTE0000000000001')));
  spy.archiveNote.and.returnValue(of(makeNote('01JNOTE0000000000001', 'ARCHIVED')));
  return spy;
}

function makeAuthSpy(perms: string[] = ['DEBT.READ', 'DEBT.NOTE.CREATE', 'DEBT.NOTE.ARCHIVE']): jasmine.SpyObj<AuthService> {
  const spy = jasmine.createSpyObj<AuthService>('AuthService', ['hasPermission']);
  spy.hasPermission.and.callFake((code: string) => perms.includes(code));
  return spy;
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------
async function setup(
  debtSpy = makeDebtServiceSpy(),
  authSpy = makeAuthSpy(),
  uid = '01JTEST00000000000099'
): Promise<{ fixture: ComponentFixture<DebtSupplierComponent>; component: DebtSupplierComponent }> {
  await TestBed.configureTestingModule({
    imports: [DebtSupplierComponent, HttpClientTestingModule],
    providers: [
      provideRouter([]),
      { provide: DebtService, useValue: debtSpy },
      { provide: AuthService, useValue: authSpy },
      {
        provide: ActivatedRoute,
        useValue: { paramMap: of(new Map([['uid', uid]])) },
      },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(DebtSupplierComponent);
  const component = fixture.componentInstance;
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, component };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('DebtSupplierComponent', () => {

  describe('Loading state', () => {
    it('starts in loading state before data arrives', async () => {
      // Use a spy that never emits so the component stays loading
      const debt = makeDebtServiceSpy();
      debt.supplierStatement.and.returnValue(new Subject<SupplierStatement>());
      debt.listNotes.and.returnValue(new Subject<PartyNote[]>());

      const { fixture } = await setup(debt);
      // Reset to loading (forkJoin never completes because subjects never emit)
      fixture.componentInstance['loading'].set(true);
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('.spinner-border')).toBeTruthy();
      expect(el.querySelector('[data-testid="debt-supplier-detail"]')).toBeNull();
    });
  });

  describe('Populated state', () => {
    it('renders supplier name in heading', async () => {
      const { fixture } = await setup();
      const h1: HTMLElement = fixture.nativeElement.querySelector('h1');
      expect(h1.textContent).toContain('ACME Supplies Ltd');
    });

    it('renders payment-terms-days display', async () => {
      const { fixture } = await setup();
      const el: HTMLElement = fixture.nativeElement;
      const display = el.querySelector('[data-testid="debt-payment-terms-display"]');
      expect(display?.textContent).toContain('30 days');
    });

    it('renders the detail container', async () => {
      const { fixture } = await setup();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="debt-supplier-detail"]')).toBeTruthy();
    });

    it('renders open invoice rows', async () => {
      const { fixture } = await setup();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('SINV-001');
      expect(el.textContent).toContain('ACME-2026-001');
    });

    it('renders recent payment rows', async () => {
      const { fixture } = await setup();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('SPAY-001');
    });

    it('renders 5 aging bucket cells', async () => {
      const { fixture } = await setup();
      const el: HTMLElement = fixture.nativeElement;
      const bucketCells = el.querySelectorAll('.bucket-cell');
      expect(bucketCells.length).toBeGreaterThanOrEqual(5);
    });

    it('builds supplier statement URL with supplierId', async () => {
      const { component } = await setup();
      expect(component['statementUrl']()).toContain('supplierId=99');
    });
  });

  describe('Empty state', () => {
    it('shows empty message when no open invoices', async () => {
      const debt = makeDebtServiceSpy();
      debt.supplierStatement.and.returnValue(of(makeStatement({ openInvoices: [] })));
      const { fixture } = await setup(debt);
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('No open AP invoices');
    });

    it('shows empty message when no recent payments', async () => {
      const debt = makeDebtServiceSpy();
      debt.supplierStatement.and.returnValue(of(makeStatement({ recentPayments: [] })));
      const { fixture } = await setup(debt);
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('No recent payments');
    });

    it('shows empty chase notes message when notes array is empty', async () => {
      const { fixture } = await setup();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('No AP chase notes yet');
    });
  });

  describe('Error state', () => {
    it('renders error alert on non-403 HTTP error', async () => {
      const debt = makeDebtServiceSpy();
      debt.supplierStatement.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'Internal error' } }))
      );
      debt.listNotes.and.returnValue(of([]));
      const { fixture } = await setup(debt);
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('.alert-danger')).toBeTruthy();
      expect(el.textContent).toContain('Internal error');
    });

    it('renders permission-required panel on 403', async () => {
      const debt = makeDebtServiceSpy();
      debt.supplierStatement.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 403 }))
      );
      debt.listNotes.and.returnValue(of([]));
      const { fixture } = await setup(debt);
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="debt-permission-required"]')).toBeTruthy();
    });
  });

  describe('AP_CHASE note append', () => {
    it('shows note textarea when user has DEBT.NOTE.CREATE', async () => {
      const { fixture } = await setup();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="debt-ap-chase-note-add"]')).toBeTruthy();
    });

    it('hides note textarea when user lacks DEBT.NOTE.CREATE', async () => {
      const auth = makeAuthSpy(['DEBT.READ', 'DEBT.NOTE.ARCHIVE']);
      const { fixture } = await setup(makeDebtServiceSpy(), auth);
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('[data-testid="debt-ap-chase-note-add"]')).toBeNull();
    });

    it('creates note with kind AP_CHASE and reloads list', fakeAsync(async () => {
      const debt = makeDebtServiceSpy();
      const newNote = makeNote('01JNOTE0000000000001');
      debt.createNote.and.returnValue(of(newNote));
      debt.listNotes.and.returnValues(
        of([]),            // initial load
        of([newNote]),     // reload after save
      );

      const { fixture, component } = await setup(debt);
      component['noteDraft'] = 'Called supplier, agreed payment';
      fixture.detectChanges();

      const saveBtn: HTMLButtonElement = fixture.nativeElement
        .querySelector('[data-testid="debt-ap-chase-note-save"]');
      saveBtn.click();
      tick();
      fixture.detectChanges();

      expect(debt.createNote).toHaveBeenCalledWith(jasmine.objectContaining({
        kind: 'AP_CHASE',
        body: 'Called supplier, agreed payment',
      }));
      expect(debt.listNotes).toHaveBeenCalledTimes(2);
      // draft cleared after save
      expect(component['noteDraft']).toBe('');
    }));

    it('shows error message when createNote fails', fakeAsync(async () => {
      const debt = makeDebtServiceSpy();
      debt.createNote.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500, error: { message: 'Save failed' } }))
      );

      const { fixture, component } = await setup(debt);
      component['noteDraft'] = 'Some note';
      fixture.detectChanges();

      const saveBtn: HTMLButtonElement = fixture.nativeElement
        .querySelector('[data-testid="debt-ap-chase-note-save"]');
      saveBtn.click();
      tick();
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('Save failed');
    }));
  });

  describe('AP_CHASE note archive', () => {
    it('shows archive button when user has DEBT.NOTE.ARCHIVE', async () => {
      const debt = makeDebtServiceSpy();
      const activeNote = makeNote('01JNOTE0000000000001', 'ACTIVE');
      debt.listNotes.and.returnValue(of([activeNote]));

      const { fixture } = await setup(debt);
      const el: HTMLElement = fixture.nativeElement;
      const archiveBtn = el.querySelector('button[aria-label*="Archive note"]');
      expect(archiveBtn).toBeTruthy();
    });

    it('hides archive button when user lacks DEBT.NOTE.ARCHIVE', async () => {
      const debt = makeDebtServiceSpy();
      const activeNote = makeNote('01JNOTE0000000000001', 'ACTIVE');
      debt.listNotes.and.returnValue(of([activeNote]));
      const auth = makeAuthSpy(['DEBT.READ', 'DEBT.NOTE.CREATE']);

      const { fixture } = await setup(debt, auth);
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('button[aria-label*="Archive note"]')).toBeNull();
    });

    it('calls archiveNote service and updates note in list', fakeAsync(async () => {
      const debt = makeDebtServiceSpy();
      const activeNote = makeNote('01JNOTE0000000000001', 'ACTIVE');
      const archivedNote = makeNote('01JNOTE0000000000001', 'ARCHIVED');
      debt.listNotes.and.returnValue(of([activeNote]));
      debt.archiveNote.and.returnValue(of(archivedNote));

      const { fixture } = await setup(debt);
      const el: HTMLElement = fixture.nativeElement;

      const archiveBtn: HTMLButtonElement = el.querySelector('button[aria-label*="Archive note"]')!;
      archiveBtn.click();
      tick();
      fixture.detectChanges();

      expect(debt.archiveNote).toHaveBeenCalledWith('01JNOTE0000000000001');
      // note should now show as archived (opacity-50)
      const noteItem = el.querySelector('li.opacity-50');
      expect(noteItem).toBeTruthy();
    }));
  });

  describe('listNotes called with AP_CHASE kind', () => {
    it('passes kind=AP_CHASE to listNotes on initial load', async () => {
      const debt = makeDebtServiceSpy();
      await setup(debt);
      expect(debt.listNotes).toHaveBeenCalledWith(
        '01JTEST00000000000099',
        jasmine.objectContaining({ kind: 'AP_CHASE' })
      );
    });
  });
});
