/**
 * Unit tests for ISSUE-PROC-001 fix — supplier name hydration in LposComponent.
 *
 * Verifies:
 * 1. LposComponent loads supplier names on init via searchSuppliers.
 * 2. LPOs returned by listLpos are enriched with supplierCode + supplierName
 *    from the supplier map built on init.
 * 3. Template no longer renders raw "#<id>" patterns after hydration.
 */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { LposComponent } from './lpos.component';
import { ProcurementService } from './procurement.service';
import { CurrencyService } from '../../core/currency/currency.service';
import { BranchService } from '../../core/branch/branch.service';
import { AuthService } from '../../core/auth/auth.service';
import { LpoOrder, SupplierSummary } from './procurement.models';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const SUPPLIER_SUMMARY: SupplierSummary = {
  id: '7', partyUid: '01JSUP00000000000007',
  code: 'SUP-007', name: 'Simba Logistics',
};

function makeLpo(supplierId: string): LpoOrder {
  return {
    id: '101', uid: '01JLPO00000000000101',
    number: 'LPO0001',
    companyId: '1', branchId: '1',
    supplierId,
    supplierName: null, supplierCode: null,
    orderDate: '2024-01-15', expectedDeliveryDate: null,
    currencyCode: 'TZS',
    subtotalAmount: 50000, taxAmount: 9000, totalAmount: 59000,
    status: 'DRAFT',
    approvedBy: null, approvedAt: null,
    notes: null, cancellationReason: null,
    lines: [],
  };
}

function makeSupplierPage(items: SupplierSummary[]): Page<SupplierSummary> {
  return { content: items, page: 0, size: 200, totalElements: items.length, totalPages: 1 };
}

function makeLpoPage(items: LpoOrder[]): Page<LpoOrder> {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

async function setup() {
  const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', [
    'listLpos', 'searchSuppliers', 'searchItems',
    'createLpo', 'submitLpo', 'approveLpo', 'cancelLpo',
  ]);
  procSpy.searchSuppliers.and.returnValue(of(makeSupplierPage([SUPPLIER_SUMMARY])));
  procSpy.listLpos.and.returnValue(of(makeLpoPage([makeLpo(SUPPLIER_SUMMARY.id)])));

  const currencySpy = jasmine.createSpyObj<CurrencyService>('CurrencyService', ['listCurrencies']);
  currencySpy.listCurrencies.and.returnValue(of([]));

  const branchSpy = jasmine.createSpyObj<BranchService>('BranchService', ['activeBranchId']);
  branchSpy.activeBranchId.and.returnValue('1');

  const authSpy = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser', 'hasPermission']);
  authSpy.currentUser.and.returnValue({ defaultBranchId: '1' } as any);
  authSpy.hasPermission.and.returnValue(false);

  const fakeRoute = {
    queryParamMap: of(convertToParamMap({})),
    snapshot: { queryParamMap: convertToParamMap({}) },
  };

  await TestBed.configureTestingModule({
    imports: [LposComponent, HttpClientTestingModule],
    providers: [
      { provide: ProcurementService, useValue: procSpy },
      { provide: CurrencyService, useValue: currencySpy },
      { provide: BranchService, useValue: branchSpy },
      { provide: AuthService, useValue: authSpy },
      { provide: ActivatedRoute, useValue: fakeRoute },
    ],
  }).compileComponents();

  const fixture: ComponentFixture<LposComponent> = TestBed.createComponent(LposComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, procSpy };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('LposComponent — supplier name hydration (ISSUE-PROC-001)', () => {

  it('calls searchSuppliers on init to build the display name map', async () => {
    const { procSpy } = await setup();
    expect(procSpy.searchSuppliers).toHaveBeenCalledWith('', 0, 200);
  });

  it('enriches listed LPOs with supplierCode and supplierName', fakeAsync(async () => {
    const { comp } = await setup();
    tick(); // allow init subscriptions to flush
    const lpos = comp['lpos']();
    expect(lpos.length).toBe(1);
    expect(lpos[0].supplierCode).toBe(SUPPLIER_SUMMARY.code);
    expect(lpos[0].supplierName).toBe(SUPPLIER_SUMMARY.name);
  }));

  it('does not render raw "#<supplierId>" in the LPO list when name is known', fakeAsync(async () => {
    const { fixture } = await setup();
    tick();
    fixture.detectChanges();

    const html: string = fixture.nativeElement.innerHTML;
    // The supplier id is "7"; if the template falls back to raw id we'd see
    // either "Supplier #7" or just "#7" in the text. With the fix the label
    // should show the code and name instead.
    expect(html).not.toContain('Supplier #' + SUPPLIER_SUMMARY.id);
    expect(html).toContain(SUPPLIER_SUMMARY.code);
  }));
});
