/**
 * SectionPickerComponent unit spec.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { SectionPickerComponent, SectionSelectedEvent } from './section-picker.component';
import { CoreLookupService, SectionSummary } from './core-lookup.service';

const SEC1: SectionSummary = {
  id: '5', uid: '01JSEC00000000000001', branchId: '1',
  code: 'SEC-A', name: 'Aisle A', type: 'STORAGE', status: 'ACTIVE',
};
const SEC2: SectionSummary = {
  id: '6', uid: '01JSEC00000000000002', branchId: '1',
  code: 'SEC-B', name: 'Aisle B', type: 'STORAGE', status: 'ACTIVE',
};
const BRANCH_UID = '01JBRA00000000000001';

async function setup(branchUid: string | null = null) {
  const lookupSpy = jasmine.createSpyObj<CoreLookupService>('CoreLookupService', [
    'listBranches', 'listPriceLists', 'listSections', 'listStockBatches', 'lookupUsers',
  ]);
  lookupSpy.listSections.and.returnValue(of([SEC1, SEC2]));

  await TestBed.configureTestingModule({
    imports: [SectionPickerComponent, HttpClientTestingModule],
    providers: [{ provide: CoreLookupService, useValue: lookupSpy }],
  }).compileComponents();

  const fixture: ComponentFixture<SectionPickerComponent> =
    TestBed.createComponent(SectionPickerComponent);
  fixture.componentInstance.instanceId = 'test';
  if (branchUid) {
    fixture.componentRef.setInput('branchUid', branchUid);
  }
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, lookupSpy };
}

describe('SectionPickerComponent', () => {

  it('renders a label', async () => {
    const { fixture } = await setup();
    const label: HTMLElement = fixture.nativeElement.querySelector('label');
    expect(label).toBeTruthy();
    expect(label.textContent).toContain('Section');
  });

  it('does NOT call listSections when branchUid is null', async () => {
    const { lookupSpy } = await setup(null);
    expect(lookupSpy.listSections).not.toHaveBeenCalled();
  });

  it('calls listSections when branchUid is provided', async () => {
    const { lookupSpy } = await setup(BRANCH_UID);
    expect(lookupSpy.listSections).toHaveBeenCalledWith(BRANCH_UID);
  });

  it('emits sectionSelected when a section is picked', async () => {
    const { fixture, comp } = await setup(BRANCH_UID);
    const events: SectionSelectedEvent[] = [];
    comp.sectionSelected.subscribe((e: SectionSelectedEvent) => events.push(e));

    const compAny = comp as unknown as { onSelect: (uid: string) => void };
    compAny.onSelect(SEC1.uid);
    fixture.detectChanges();

    expect(events.length).toBe(1);
    expect(events[0].uid).toBe(SEC1.uid);
    expect(events[0].code).toBe(SEC1.code);
  });

  it('emits sectionCleared when null is passed', async () => {
    const { fixture, comp } = await setup(BRANCH_UID);
    const emitSpy = spyOn(comp.sectionCleared, 'emit');

    const compAny = comp as unknown as { onSelect: (uid: string | null) => void };
    compAny.onSelect(null);
    fixture.detectChanges();

    expect(emitSpy).toHaveBeenCalledTimes(1);
  });

  it('reloads when branchUid changes', async () => {
    const { fixture, lookupSpy } = await setup(BRANCH_UID);
    const BRANCH2 = '01JBRA00000000000002';
    fixture.componentRef.setInput('branchUid', BRANCH2);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(lookupSpy.listSections).toHaveBeenCalledWith(BRANCH2);
  });

  it('shows a warning when listSections fails', async () => {
    const lookupSpy = jasmine.createSpyObj<CoreLookupService>('CoreLookupService', [
      'listBranches', 'listPriceLists', 'listSections', 'listStockBatches', 'lookupUsers',
    ]);
    lookupSpy.listSections.and.returnValue(throwError(() => new Error('network')));

    await TestBed.configureTestingModule({
      imports: [SectionPickerComponent, HttpClientTestingModule],
      providers: [{ provide: CoreLookupService, useValue: lookupSpy }],
    }).compileComponents();

    const fixture = TestBed.createComponent(SectionPickerComponent);
    fixture.componentRef.setInput('branchUid', BRANCH_UID);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Failed to load sections');
  });
});
