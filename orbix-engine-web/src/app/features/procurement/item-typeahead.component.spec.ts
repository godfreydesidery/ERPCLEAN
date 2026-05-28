/**
 * ItemTypeaheadComponent unit spec.
 * Covers: 4 states (idle / loading / results / no-results),
 *         debounce trigger, result selection, keyboard nav, clear.
 * Mirrors supplier-typeahead.component.spec.ts exactly, swapping the
 * search source to ProcurementService.searchItems.
 */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import { ItemTypeaheadComponent, ItemSelectedEvent } from './item-typeahead.component';
import { ProcurementService } from './procurement.service';
import { ItemSummary } from './procurement.models';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const ITEM1: ItemSummary = {
  id: '20', uid: '01JITM00000000000001',
  code: 'WGT-001', name: 'Widget A',
  defaultUomUid: '01JUOM00000000000001', defaultUomCode: 'PCS',
  defaultVatGroupUid: '01JVAT00000000000001',
};
const ITEM2: ItemSummary = {
  id: '21', uid: '01JITM00000000000002',
  code: 'WGT-002', name: 'Widget B',
  defaultUomUid: null, defaultUomCode: null,
  defaultVatGroupUid: null,
};

function makePage(items: ItemSummary[]): Page<ItemSummary> {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

async function setup(initialItem: ItemSelectedEvent | null = null) {
  const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['searchItems']);
  procSpy.searchItems.and.returnValue(of(makePage([ITEM1, ITEM2])));

  await TestBed.configureTestingModule({
    imports: [ItemTypeaheadComponent, HttpClientTestingModule],
    providers: [{ provide: ProcurementService, useValue: procSpy }],
  }).compileComponents();

  const fixture: ComponentFixture<ItemTypeaheadComponent> =
    TestBed.createComponent(ItemTypeaheadComponent);
  fixture.componentInstance.instanceId = 'test';
  if (initialItem) fixture.componentInstance.initialItem = initialItem;
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, procSpy };
}

async function stabilise(fixture: ComponentFixture<unknown>): Promise<void> {
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('ItemTypeaheadComponent', () => {

  describe('rendering — idle state', () => {
    it('renders the input with role=combobox', async () => {
      const { fixture } = await setup();
      const input: HTMLInputElement = fixture.nativeElement.querySelector('input[role="combobox"]');
      expect(input).toBeTruthy();
    });

    it('starts with aria-expanded=false', async () => {
      const { fixture } = await setup();
      const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
      expect(input.getAttribute('aria-expanded')).toBe('false');
    });

    it('does not show dropdown in idle state', async () => {
      const { fixture } = await setup();
      expect(fixture.nativeElement.querySelector('[role="listbox"]')).toBeNull();
    });
  });

  describe('initial item pre-selection', () => {
    it('populates the input when initialItem is provided', async () => {
      const init: ItemSelectedEvent = {
        uid: ITEM1.uid, id: ITEM1.id, code: ITEM1.code, name: ITEM1.name,
        defaultUomUid: ITEM1.defaultUomUid, defaultUomCode: ITEM1.defaultUomCode,
        defaultVatGroupUid: ITEM1.defaultVatGroupUid,
      };
      const { fixture } = await setup(init);
      const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
      expect(input.value).toContain(ITEM1.code);
    });

    it('shows the clear button when pre-selected', async () => {
      const init: ItemSelectedEvent = {
        uid: ITEM1.uid, id: ITEM1.id, code: ITEM1.code, name: ITEM1.name,
        defaultUomUid: ITEM1.defaultUomUid, defaultUomCode: ITEM1.defaultUomCode,
        defaultVatGroupUid: ITEM1.defaultVatGroupUid,
      };
      const { fixture } = await setup(init);
      const clearBtn = fixture.nativeElement.querySelector('button[aria-label="Clear item selection"]');
      expect(clearBtn).toBeTruthy();
    });
  });

  describe('loading state', () => {
    it('shows spinner while HTTP is in flight', fakeAsync(async () => {
      const { fixture, procSpy } = await setup();
      // Return an observable that never completes during the tick window.
      procSpy.searchItems.and.returnValue(of(makePage([ITEM1])).pipe(delay(500)));

      const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
      input.value = 'wi';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      // Advance past debounce (250 ms) but not the HTTP delay (500 ms).
      tick(260);
      fixture.detectChanges();

      const spinner = fixture.nativeElement.querySelector('.spinner-border');
      expect(spinner).toBeTruthy();

      tick(500); // let HTTP complete
      fixture.detectChanges();
    }));
  });

  describe('results state', () => {
    it('calls searchItems after debounce', fakeAsync(async () => {
      const { fixture, procSpy } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('wid');
      tick(260);
      fixture.detectChanges();

      expect(procSpy.searchItems).toHaveBeenCalledWith('wid', 20);
    }));

    it('renders result rows in the listbox', fakeAsync(async () => {
      const { fixture } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('wid');
      tick(260);
      fixture.detectChanges();

      const options = fixture.nativeElement.querySelectorAll('[role="option"]');
      expect(options.length).toBe(2);
    }));

    it('emits itemSelected when a result is clicked', fakeAsync(async () => {
      const { fixture } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };
      const events: ItemSelectedEvent[] = [];
      fixture.componentInstance.itemSelected.subscribe((e: ItemSelectedEvent) => events.push(e));

      comp.onQueryChange('wid');
      tick(260);
      fixture.detectChanges();

      const firstOption = fixture.nativeElement.querySelector('[role="option"]');
      firstOption.click();
      fixture.detectChanges();

      expect(events.length).toBe(1);
      expect(events[0].uid).toBe(ITEM1.uid);
      expect(events[0].code).toBe(ITEM1.code);
    }));

    it('emits defaultUomUid and defaultVatGroupUid on selection', fakeAsync(async () => {
      const { fixture } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };
      const events: ItemSelectedEvent[] = [];
      fixture.componentInstance.itemSelected.subscribe((e: ItemSelectedEvent) => events.push(e));

      comp.onQueryChange('wid');
      tick(260);
      fixture.detectChanges();

      const firstOption = fixture.nativeElement.querySelector('[role="option"]');
      firstOption.click();
      fixture.detectChanges();

      expect(events[0].defaultUomUid).toBe(ITEM1.defaultUomUid);
      expect(events[0].defaultVatGroupUid).toBe(ITEM1.defaultVatGroupUid);
    }));

    it('hides the dropdown after selection', fakeAsync(async () => {
      const { fixture } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('wid');
      tick(260);
      fixture.detectChanges();

      const firstOption: HTMLElement = fixture.nativeElement.querySelector('[role="option"]');
      firstOption.click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[role="listbox"]')).toBeNull();
    }));
  });

  describe('no-results state', () => {
    it('shows no-results message when search returns empty', fakeAsync(async () => {
      const { fixture, procSpy } = await setup();
      procSpy.searchItems.and.returnValue(of(makePage([])));
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('zzz');
      tick(260);
      fixture.detectChanges();

      const listbox = fixture.nativeElement.querySelector('[role="listbox"]');
      expect(listbox).toBeTruthy();
      expect(listbox.textContent).toContain('No items found');
    }));

    it('shows no-results message on HTTP error', fakeAsync(async () => {
      const { fixture, procSpy } = await setup();
      procSpy.searchItems.and.returnValue(throwError(() => new Error('network')));
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('err');
      tick(260);
      fixture.detectChanges();

      const listbox = fixture.nativeElement.querySelector('[role="listbox"]');
      expect(listbox).toBeTruthy();
    }));
  });

  describe('keyboard navigation', () => {
    it('ArrowDown moves activeIndex down', fakeAsync(async () => {
      const { fixture } = await setup();
      const comp = fixture.componentInstance as unknown as {
        onQueryChange: (q: string) => void;
        onKeydown: (e: KeyboardEvent) => void;
        activeIndex: () => number;
        dropdownOpen: () => boolean;
      };

      comp.onQueryChange('wid');
      tick(260);
      fixture.detectChanges();

      comp.onKeydown(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
      expect(comp.activeIndex()).toBe(0);
      comp.onKeydown(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
      expect(comp.activeIndex()).toBe(1);
    }));

    it('Enter selects the active result', fakeAsync(async () => {
      const { fixture } = await setup();
      const comp = fixture.componentInstance as unknown as {
        onQueryChange: (q: string) => void;
        onKeydown: (e: KeyboardEvent) => void;
        activeIndex: () => number;
      };
      const events: ItemSelectedEvent[] = [];
      fixture.componentInstance.itemSelected.subscribe((e: ItemSelectedEvent) => events.push(e));

      comp.onQueryChange('wid');
      tick(260);
      fixture.detectChanges();

      comp.onKeydown(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
      comp.onKeydown(new KeyboardEvent('keydown', { key: 'Enter' }));
      fixture.detectChanges();

      expect(events.length).toBe(1);
    }));

    it('Escape closes dropdown', fakeAsync(async () => {
      const { fixture } = await setup();
      const comp = fixture.componentInstance as unknown as {
        onQueryChange: (q: string) => void;
        onKeydown: (e: KeyboardEvent) => void;
        dropdownOpen: () => boolean;
      };

      comp.onQueryChange('wid');
      tick(260);
      fixture.detectChanges();

      comp.onKeydown(new KeyboardEvent('keydown', { key: 'Escape' }));
      expect(comp.dropdownOpen()).toBeFalse();
    }));
  });

  describe('clear', () => {
    it('emits itemCleared and resets internal query/selected state', fakeAsync(async () => {
      const init: ItemSelectedEvent = {
        uid: ITEM1.uid, id: ITEM1.id, code: ITEM1.code, name: ITEM1.name,
        defaultUomUid: ITEM1.defaultUomUid, defaultUomCode: ITEM1.defaultUomCode,
        defaultVatGroupUid: ITEM1.defaultVatGroupUid,
      };
      const { fixture } = await setup(init);

      const emitSpy = spyOn(fixture.componentInstance.itemCleared, 'emit');

      const comp = fixture.componentInstance as unknown as {
        clear: () => void;
        query: string;
        selected: () => ItemSelectedEvent | null;
      };
      comp.clear();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(emitSpy).toHaveBeenCalled();
      expect(comp.query).toBe('');
      expect(comp.selected()).toBeNull();
    }));
  });

  describe('disabled input', () => {
    it('renders the input as disabled when disabled=true', async () => {
      const { fixture } = await setup();
      fixture.componentInstance.disabled = true;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
      expect(input.disabled).toBeTrue();
    });
  });
});
