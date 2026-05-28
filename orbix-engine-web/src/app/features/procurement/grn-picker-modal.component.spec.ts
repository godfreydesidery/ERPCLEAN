/**
 * GrnPickerModalComponent unit spec.
 * Covers: 4 states (idle / loading / results / no-results),
 *         GRN selection emission, cancel emission, supplierId filtering.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { GrnPickerModalComponent } from './grn-picker-modal.component';
import { ProcurementService } from './procurement.service';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { Grn } from './procurement.models';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const POSTED_GRN: Grn = {
  id: '50', uid: '01JGRN00000000000001',
  number: 'GRN-001', companyId: '1', branchId: '10',
  supplierId: '10', lpoOrderId: null,
  receivedDate: '2026-05-20', supplierDeliveryNote: null,
  subtotalAmount: 100000, taxAmount: 18000, totalAmount: 118000,
  status: 'POSTED', postedAt: null, postedBy: null,
  notes: null, cancellationReason: null,
  lines: [],
};

function makePage(grns: Grn[]): Page<Grn> {
  return { content: grns, page: 0, size: 50, totalElements: grns.length, totalPages: 1 };
}

// ---------------------------------------------------------------------------
// Setup helpers
// ---------------------------------------------------------------------------

/** Creates the fixture with visible=false so no load fires during setup. */
async function buildFixture(procSpy: jasmine.SpyObj<ProcurementService>) {
  const authStub = {
    currentUser: signal(null), permissions: signal([]),
    isAuthenticated: signal(false), hasPermission: () => true,
  } as unknown as AuthService;
  const branchStub = { activeBranchId: signal<string | null>('10') } as unknown as BranchService;

  await TestBed.configureTestingModule({
    imports: [GrnPickerModalComponent, HttpClientTestingModule],
    providers: [
      { provide: ProcurementService, useValue: procSpy },
      { provide: AuthService, useValue: authStub },
      { provide: BranchService, useValue: branchStub },
    ],
  }).compileComponents();

  const fixture: ComponentFixture<GrnPickerModalComponent> =
    TestBed.createComponent(GrnPickerModalComponent);
  // Use setInput so ngOnChanges fires correctly on subsequent input changes.
  // Start closed so no load fires before the spy is ready.
  fixture.componentRef.setInput('visible', false);
  fixture.componentRef.setInput('supplierId', '10');
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

/** Opens the modal (triggers ngOnChanges → load). */
async function open(fixture: ComponentFixture<GrnPickerModalComponent>): Promise<void> {
  // setInput triggers ngOnChanges; direct property assignment does not.
  fixture.componentRef.setInput('visible', true);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('GrnPickerModalComponent', () => {

  describe('idle state (visible=false)', () => {
    it('renders nothing when not visible', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listGrns']);
      procSpy.listGrns.and.returnValue(of(makePage([POSTED_GRN])));
      const fixture = await buildFixture(procSpy);
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    });
  });

  describe('loading state', () => {
    it('shows the dialog when visible=true', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listGrns']);
      procSpy.listGrns.and.returnValue(of(makePage([POSTED_GRN])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    });

    it('calls listGrns with supplierId and status=POSTED when opened', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listGrns']);
      procSpy.listGrns.and.returnValue(of(makePage([POSTED_GRN])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);
      expect(procSpy.listGrns).toHaveBeenCalledWith(
        jasmine.anything(), 0, 50, '10', 'POSTED'
      );
    });
  });

  describe('results state', () => {
    it('renders a table row for each GRN', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listGrns']);
      procSpy.listGrns.and.returnValue(of(makePage([POSTED_GRN])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);

      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      expect(rows.length).toBe(1);
    });

    it('renders the GRN number in the first cell', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listGrns']);
      procSpy.listGrns.and.returnValue(of(makePage([POSTED_GRN])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);

      const firstCell: HTMLElement = fixture.nativeElement.querySelector('tbody tr td');
      expect(firstCell.textContent?.trim()).toContain('GRN-001');
    });

    it('emits grnSelected with the full GRN on Select click', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listGrns']);
      procSpy.listGrns.and.returnValue(of(makePage([POSTED_GRN])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);

      const selected: Grn[] = [];
      fixture.componentInstance.grnSelected.subscribe((g: Grn) => selected.push(g));

      const selectBtn: HTMLButtonElement = fixture.nativeElement.querySelector('tbody button');
      selectBtn.click();
      fixture.detectChanges();

      expect(selected.length).toBe(1);
      expect(selected[0].uid).toBe(POSTED_GRN.uid);
    });
  });

  describe('no-results state', () => {
    it('shows no-results message when listGrns returns empty page', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listGrns']);
      procSpy.listGrns.and.returnValue(of(makePage([])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);

      expect(fixture.nativeElement.textContent).toContain('No posted GRNs');
    });

    it('shows no-results message on HTTP error', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listGrns']);
      procSpy.listGrns.and.returnValue(throwError(() => new Error('network')));
      const fixture = await buildFixture(procSpy);
      await open(fixture);

      expect(fixture.nativeElement.textContent).toContain('No posted GRNs');
    });
  });

  describe('cancel', () => {
    it('onCancel() emits through the closed EventEmitter', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listGrns']);
      procSpy.listGrns.and.returnValue(of(makePage([POSTED_GRN])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);

      // Spy on the emit method directly.
      const emitSpy = spyOn(fixture.componentInstance.closed, 'emit');
      const comp = fixture.componentInstance as unknown as { onCancel: () => void };
      comp.onCancel();
      fixture.detectChanges();

      expect(emitSpy).toHaveBeenCalled();
    });

    it('Cancel button click calls onCancel()', async () => {
      const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['listGrns']);
      procSpy.listGrns.and.returnValue(of(makePage([POSTED_GRN])));
      const fixture = await buildFixture(procSpy);
      await open(fixture);

      const emitSpy = spyOn(fixture.componentInstance.closed, 'emit');

      // Find any button whose text contains 'Cancel'.
      const buttons: HTMLButtonElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('button')
      );
      const cancelBtn = buttons.find(b => b.textContent?.includes('Cancel'));
      expect(cancelBtn).withContext('Cancel button must exist in rendered modal').toBeTruthy();
      cancelBtn?.click();
      fixture.detectChanges();

      expect(emitSpy).toHaveBeenCalled();
    });
  });
});
