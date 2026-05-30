/**
 * PriceListPickerComponent unit spec.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { PriceListPickerComponent, PriceListSelectedEvent } from './price-list-picker.component';
import { CoreLookupService, PriceListSummary } from './core-lookup.service';

const PL1: PriceListSummary = {
  id: '1', uid: '01JPL000000000000001', code: 'PL-RETAIL', name: 'Retail',
  currencyCode: 'TZS', isDefault: true, status: 'ACTIVE',
};
const PL2: PriceListSummary = {
  id: '2', uid: '01JPL000000000000002', code: 'PL-WHOLESALE', name: 'Wholesale',
  currencyCode: 'TZS', isDefault: false, status: 'ACTIVE',
};

async function setup(initial: PriceListSelectedEvent | null = null) {
  const lookupSpy = jasmine.createSpyObj<CoreLookupService>('CoreLookupService', [
    'listBranches', 'listPriceLists', 'listSections', 'listStockBatches', 'lookupUsers',
  ]);
  lookupSpy.listPriceLists.and.returnValue(of([PL1, PL2]));

  await TestBed.configureTestingModule({
    imports: [PriceListPickerComponent, HttpClientTestingModule],
    providers: [{ provide: CoreLookupService, useValue: lookupSpy }],
  }).compileComponents();

  const fixture: ComponentFixture<PriceListPickerComponent> =
    TestBed.createComponent(PriceListPickerComponent);
  fixture.componentInstance.instanceId = 'test';
  if (initial) fixture.componentInstance.initialPriceList = initial;
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, lookupSpy };
}

describe('PriceListPickerComponent', () => {

  it('renders a label', async () => {
    const { fixture } = await setup();
    const label: HTMLElement = fixture.nativeElement.querySelector('label');
    expect(label).toBeTruthy();
    expect(label.textContent).toContain('Price list');
  });

  it('calls listPriceLists on init', async () => {
    const { lookupSpy } = await setup();
    expect(lookupSpy.listPriceLists).toHaveBeenCalled();
  });

  it('emits priceListSelected with correct payload when selected', async () => {
    const { fixture, comp } = await setup();
    const events: PriceListSelectedEvent[] = [];
    comp.priceListSelected.subscribe((e: PriceListSelectedEvent) => events.push(e));

    const compAny = comp as unknown as { onSelect: (uid: string) => void };
    compAny.onSelect(PL1.uid);
    fixture.detectChanges();

    expect(events.length).toBe(1);
    expect(events[0].uid).toBe(PL1.uid);
    expect(events[0].code).toBe(PL1.code);
  });

  it('emits priceListCleared on null selection', async () => {
    const { fixture, comp } = await setup();
    const emitSpy = spyOn(comp.priceListCleared, 'emit');

    const compAny = comp as unknown as { onSelect: (uid: string | null) => void };
    compAny.onSelect(null);
    fixture.detectChanges();

    expect(emitSpy).toHaveBeenCalledTimes(1);
  });

  it('pre-selects when initialPriceList is provided', async () => {
    const initial: PriceListSelectedEvent = { id: PL2.id, uid: PL2.uid, code: PL2.code, name: PL2.name };
    const { comp } = await setup(initial);
    const compAny = comp as unknown as { selectedId: string | null };
    expect(compAny.selectedId).toBe(PL2.uid);
  });

  it('shows a warning when listPriceLists fails', async () => {
    const lookupSpy = jasmine.createSpyObj<CoreLookupService>('CoreLookupService', [
      'listBranches', 'listPriceLists', 'listSections', 'listStockBatches', 'lookupUsers',
    ]);
    lookupSpy.listPriceLists.and.returnValue(throwError(() => new Error('network')));

    await TestBed.configureTestingModule({
      imports: [PriceListPickerComponent, HttpClientTestingModule],
      providers: [{ provide: CoreLookupService, useValue: lookupSpy }],
    }).compileComponents();

    const fixture = TestBed.createComponent(PriceListPickerComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Failed to load price lists');
  });
});
