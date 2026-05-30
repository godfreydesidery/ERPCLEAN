/**
 * BatchPickerComponent unit spec.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { BatchPickerComponent, BatchSelectedEvent } from './batch-picker.component';
import { CoreLookupService, StockBatchSummary } from './core-lookup.service';
import { Page } from '../api/page';

const BATCH1: StockBatchSummary = {
  id: '20', uid: '01JBAT00000000000001', itemId: '5',
  batchNo: 'BT-2026-001', expiryAt: '2027-01-31', qtyOnHand: 150, status: 'ACTIVE',
};
const BATCH2: StockBatchSummary = {
  id: '21', uid: '01JBAT00000000000002', itemId: '5',
  batchNo: 'BT-2026-002', expiryAt: null, qtyOnHand: 80, status: 'ACTIVE',
};

function makePage(items: StockBatchSummary[]): Page<StockBatchSummary> {
  return { content: items, page: 0, size: 50, totalElements: items.length, totalPages: 1 };
}

const ITEM_ID = '5';

async function setup(itemId: string | null = null) {
  const lookupSpy = jasmine.createSpyObj<CoreLookupService>('CoreLookupService', [
    'listBranches', 'listPriceLists', 'listSections', 'listStockBatches', 'lookupUsers',
  ]);
  lookupSpy.listStockBatches.and.returnValue(of(makePage([BATCH1, BATCH2])));

  await TestBed.configureTestingModule({
    imports: [BatchPickerComponent, HttpClientTestingModule],
    providers: [{ provide: CoreLookupService, useValue: lookupSpy }],
  }).compileComponents();

  const fixture: ComponentFixture<BatchPickerComponent> =
    TestBed.createComponent(BatchPickerComponent);
  fixture.componentInstance.instanceId = 'test';
  if (itemId) {
    fixture.componentRef.setInput('itemId', itemId);
  }
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, lookupSpy };
}

describe('BatchPickerComponent', () => {

  it('renders a label', async () => {
    const { fixture } = await setup();
    const label: HTMLElement = fixture.nativeElement.querySelector('label');
    expect(label).toBeTruthy();
    expect(label.textContent).toContain('Batch');
  });

  it('does NOT call listStockBatches when itemId is null', async () => {
    const { lookupSpy } = await setup(null);
    expect(lookupSpy.listStockBatches).not.toHaveBeenCalled();
  });

  it('calls listStockBatches when itemId is provided', async () => {
    const { lookupSpy } = await setup(ITEM_ID);
    expect(lookupSpy.listStockBatches).toHaveBeenCalledWith(ITEM_ID);
  });

  it('emits batchSelected when a batch is picked', async () => {
    const { fixture, comp } = await setup(ITEM_ID);
    const events: BatchSelectedEvent[] = [];
    comp.batchSelected.subscribe((e: BatchSelectedEvent) => events.push(e));

    const compAny = comp as unknown as { onSelect: (uid: string) => void };
    compAny.onSelect(BATCH1.uid);
    fixture.detectChanges();

    expect(events.length).toBe(1);
    expect(events[0].uid).toBe(BATCH1.uid);
    expect(events[0].batchNo).toBe(BATCH1.batchNo);
  });

  it('emits batchCleared when null is passed', async () => {
    const { fixture, comp } = await setup(ITEM_ID);
    const emitSpy = spyOn(comp.batchCleared, 'emit');

    const compAny = comp as unknown as { onSelect: (uid: string | null) => void };
    compAny.onSelect(null);
    fixture.detectChanges();

    expect(emitSpy).toHaveBeenCalledTimes(1);
  });

  it('reloads when itemId changes', async () => {
    const { fixture, lookupSpy } = await setup(ITEM_ID);
    fixture.componentRef.setInput('itemId', '99');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(lookupSpy.listStockBatches).toHaveBeenCalledWith('99');
  });

  it('shows a warning when listStockBatches fails', async () => {
    const lookupSpy = jasmine.createSpyObj<CoreLookupService>('CoreLookupService', [
      'listBranches', 'listPriceLists', 'listSections', 'listStockBatches', 'lookupUsers',
    ]);
    lookupSpy.listStockBatches.and.returnValue(throwError(() => new Error('network')));

    await TestBed.configureTestingModule({
      imports: [BatchPickerComponent, HttpClientTestingModule],
      providers: [{ provide: CoreLookupService, useValue: lookupSpy }],
    }).compileComponents();

    const fixture = TestBed.createComponent(BatchPickerComponent);
    fixture.componentRef.setInput('itemId', ITEM_ID);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Failed to load batches');
  });

  it('includes expiry date in the option label when expiryAt is set', async () => {
    const { comp } = await setup(ITEM_ID);
    const compAny = comp as unknown as { options: { (): { id: string; label: string }[] } };
    const opt = compAny.options().find(o => o.id === BATCH1.uid);
    expect(opt).toBeTruthy();
    expect(opt!.label).toContain('2027-01-31');
  });
});
