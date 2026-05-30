/**
 * BranchPickerComponent unit spec.
 * Covers: loading state, list rendering, selection emission, clear, error state.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { BranchPickerComponent, BranchSelectedEvent } from './branch-picker.component';
import { CoreLookupService, BranchSummary } from './core-lookup.service';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const B1: BranchSummary = {
  id: '1', uid: '01JBRA00000000000001', code: 'BR-001', name: 'Head Office',
  type: 'MAIN', isDefault: true, status: 'ACTIVE',
};
const B2: BranchSummary = {
  id: '2', uid: '01JBRA00000000000002', code: 'BR-002', name: 'Warehouse',
  type: 'WAREHOUSE', isDefault: false, status: 'ACTIVE',
};

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

async function setup(initial: BranchSelectedEvent | null = null) {
  const lookupSpy = jasmine.createSpyObj<CoreLookupService>('CoreLookupService', [
    'listBranches', 'listPriceLists', 'listSections', 'listStockBatches', 'lookupUsers',
  ]);
  lookupSpy.listBranches.and.returnValue(of([B1, B2]));

  await TestBed.configureTestingModule({
    imports: [BranchPickerComponent, HttpClientTestingModule],
    providers: [{ provide: CoreLookupService, useValue: lookupSpy }],
  }).compileComponents();

  const fixture: ComponentFixture<BranchPickerComponent> =
    TestBed.createComponent(BranchPickerComponent);
  fixture.componentInstance.instanceId = 'test';
  if (initial) fixture.componentInstance.initialBranch = initial;
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, lookupSpy };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('BranchPickerComponent', () => {

  it('renders a label', async () => {
    const { fixture } = await setup();
    const label: HTMLElement = fixture.nativeElement.querySelector('label');
    expect(label).toBeTruthy();
    expect(label.textContent).toContain('Branch');
  });

  it('calls listBranches on init', async () => {
    const { lookupSpy } = await setup();
    expect(lookupSpy.listBranches).toHaveBeenCalled();
  });

  it('emits branchSelected with correct payload when a branch is picked', async () => {
    const { fixture, comp } = await setup();
    const events: BranchSelectedEvent[] = [];
    comp.branchSelected.subscribe((e: BranchSelectedEvent) => events.push(e));

    // Simulate selection via the protected onSelect method.
    const compAny = comp as unknown as { onSelect: (uid: string) => void };
    compAny.onSelect(B1.uid);
    fixture.detectChanges();

    expect(events.length).toBe(1);
    expect(events[0].uid).toBe(B1.uid);
    expect(events[0].code).toBe(B1.code);
    expect(events[0].name).toBe(B1.name);
    expect(events[0].id).toBe(B1.id);
  });

  it('emits branchCleared when null is passed to onSelect', async () => {
    const { fixture, comp } = await setup();
    const emitSpy = spyOn(comp.branchCleared, 'emit');

    const compAny = comp as unknown as { onSelect: (uid: string | null) => void };
    compAny.onSelect(null);
    fixture.detectChanges();

    expect(emitSpy).toHaveBeenCalledTimes(1);
  });

  it('pre-selects the initial branch when initialBranch is provided', async () => {
    const initial: BranchSelectedEvent = { id: B2.id, uid: B2.uid, code: B2.code, name: B2.name };
    const { comp } = await setup(initial);
    const compAny = comp as unknown as { selectedId: string | null };
    expect(compAny.selectedId).toBe(B2.uid);
  });

  it('shows a warning when listBranches fails', async () => {
    const lookupSpy = jasmine.createSpyObj<CoreLookupService>('CoreLookupService', [
      'listBranches', 'listPriceLists', 'listSections', 'listStockBatches', 'lookupUsers',
    ]);
    lookupSpy.listBranches.and.returnValue(throwError(() => new Error('network')));

    await TestBed.configureTestingModule({
      imports: [BranchPickerComponent, HttpClientTestingModule],
      providers: [{ provide: CoreLookupService, useValue: lookupSpy }],
    }).compileComponents();

    const fixture = TestBed.createComponent(BranchPickerComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Failed to load branches');
  });
});
