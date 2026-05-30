/**
 * LpoPickerModalComponent unit spec.
 * Covers: idle/loading/results/no-results states, selection emission,
 *         cancel emission, supplier filtering, status filter.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { LpoPickerModalComponent } from './lpo-picker-modal.component';
import { ProcurementService } from './procurement.service';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { LpoOrder } from './procurement.models';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const LPO1: LpoOrder = {
  id: '30', uid: '01JLPO00000000000001',
  number: 'LPO-001', companyId: '1', branchId: '10',
  supplierId: '20',
  orderDate: '2026-05-10', expectedDeliveryDate: '2026-05-20',
  currencyCode: 'TZS',
  subtotalAmount: 200000, taxAmount: 36000, totalAmount: 236000,
  status: 'APPROVED',
  approvedBy: null, approvedAt: null, notes: null, cancellationReason: null,
  lines: [],
};

const LPO2: LpoOrder = {
  ...LPO1,
  id: '31', uid: '01JLPO00000000000002',
  number: 'LPO-002', supplierId: '99', // different supplier
};

function makePage(lpos: LpoOrder[]): Page<LpoOrder> {
  return { content: lpos, page: 0, size: 100, totalElements: lpos.length, totalPages: 1 };
}

// ---------------------------------------------------------------------------
// Setup helpers
// ---------------------------------------------------------------------------

async function buildFixture(procSpy: jasmine.SpyObj<ProcurementService>) {
  const authStub = {
    currentUser: signal(null), permissions: signal([]),
    isAuthenticated: signal(false), hasPermission: () => true,
  } as unknown as AuthService;
  const branchStub = { activeBranchId: signal<string | null>('10') } as unknown as BranchService;

  await TestBed.configureTestingModule({
    imports: [LpoPickerModalComponent, HttpClientTestingModule],
    providers: [
      { provide: ProcurementService, useValue: procSpy },
      { provide: AuthService, useValue: authStub },
      { provide: BranchService, useValue: branchStub },
    ],
  }).compileComponents();

  const fixture: ComponentFixture<LpoPickerModalComponent> =
    TestBed.createComponent(LpoPickerModalComponent);
  fixture.componentRef.setInput('visible', false);
  fixture.componentRef.setInput('supplierId', null);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

async function open(fixture: ComponentFixture<LpoPickerModalComponent>): Promise<void> {
  fixture.componentRef.setInput('visible', true);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('LpoPickerModalComponent', () => {

  describe('idle state (visible=false)', () => {
    it('renders nothing when not visible', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listLpos']);
      procSpy.listLpos.and.returnValue(of(makePage([LPO1])));
      const fixture = await buildFixture(procSpy);
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    });
  });

  describe('results state', () => {
    it('shows the dialog when visible=true', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listLpos']);
      procSpy.listLpos.and.returnValue(of(makePage([LPO1])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    });

    it('calls listLpos with the active branch and statusFilter', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listLpos']);
      procSpy.listLpos.and.returnValue(of(makePage([LPO1])));
      const fixture = await buildFixture(procSpy);
      fixture.componentRef.setInput('statusFilter', 'APPROVED');
      await open(fixture);
      expect(procSpy.listLpos).toHaveBeenCalledWith('10', 0, 100, 'APPROVED');
    });

    it('renders a table row for each LPO', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listLpos']);
      procSpy.listLpos.and.returnValue(of(makePage([LPO1, LPO2])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);

      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      expect(rows.length).toBe(2);
    });

    it('filters rows by supplierId when set', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listLpos']);
      procSpy.listLpos.and.returnValue(of(makePage([LPO1, LPO2])));
      const fixture = await buildFixture(procSpy);
      fixture.componentRef.setInput('supplierId', '20'); // only LPO1 matches
      await open(fixture);

      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      expect(rows.length).toBe(1);
      expect(fixture.nativeElement.textContent).toContain('LPO-001');
      expect(fixture.nativeElement.textContent).not.toContain('LPO-002');
    });

    it('renders the LPO number in the first column', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listLpos']);
      procSpy.listLpos.and.returnValue(of(makePage([LPO1])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);

      const firstCell: HTMLElement = fixture.nativeElement.querySelector('tbody tr td');
      expect(firstCell.textContent?.trim()).toContain('LPO-001');
    });

    it('emits lpoSelected with the full LpoOrder on Select click', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listLpos']);
      procSpy.listLpos.and.returnValue(of(makePage([LPO1])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);

      const selected: LpoOrder[] = [];
      fixture.componentInstance.lpoSelected.subscribe((l: LpoOrder) => selected.push(l));

      const selectBtn: HTMLButtonElement = fixture.nativeElement.querySelector('tbody button');
      selectBtn.click();
      fixture.detectChanges();

      expect(selected.length).toBe(1);
      expect(selected[0].uid).toBe(LPO1.uid);
    });
  });

  describe('no-results state', () => {
    it('shows no-results message when listLpos returns empty page', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listLpos']);
      procSpy.listLpos.and.returnValue(of(makePage([])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);
      expect(fixture.nativeElement.textContent).toContain('No LPOs available');
    });

    it('shows no-results message on HTTP error', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listLpos']);
      procSpy.listLpos.and.returnValue(throwError(() => new Error('network')));
      const fixture = await buildFixture(procSpy);
      await open(fixture);
      expect(fixture.nativeElement.textContent).toContain('No LPOs available');
    });
  });

  describe('cancel', () => {
    it('onCancel() emits through the closed EventEmitter', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listLpos']);
      procSpy.listLpos.and.returnValue(of(makePage([LPO1])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);

      const emitSpy = spyOn(fixture.componentInstance.closed, 'emit');
      const comp = fixture.componentInstance as unknown as { onCancel: () => void };
      comp.onCancel();
      fixture.detectChanges();

      expect(emitSpy).toHaveBeenCalled();
    });

    it('Cancel button click emits closed', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listLpos']);
      procSpy.listLpos.and.returnValue(of(makePage([LPO1])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);

      const emitSpy = spyOn(fixture.componentInstance.closed, 'emit');
      const buttons: HTMLButtonElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('button')
      );
      const cancelBtn = buttons.find(b => b.textContent?.includes('Cancel'));
      expect(cancelBtn).withContext('Cancel button must exist').toBeTruthy();
      cancelBtn?.click();
      fixture.detectChanges();

      expect(emitSpy).toHaveBeenCalled();
    });
  });
});
