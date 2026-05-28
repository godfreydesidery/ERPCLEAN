/**
 * SupplierTypeaheadComponent unit spec.
 * Covers: 4 states (idle / loading / results / no-results),
 *         debounce trigger, result selection, keyboard nav, clear.
 */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import { SupplierTypeaheadComponent, SupplierSelectedEvent } from './supplier-typeahead.component';
import { ProcurementService } from './procurement.service';
import { SupplierSummary } from './procurement.models';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const SUP1: SupplierSummary = { id: '10', partyUid: '01JSUP00000000000001', code: 'SUP-001', name: 'Acme Supplies' };
const SUP2: SupplierSummary = { id: '11', partyUid: '01JSUP00000000000002', code: 'SUP-002', name: 'Beta Traders' };

function makePage(items: SupplierSummary[]): Page<SupplierSummary> {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

async function setup(initialSupplier: SupplierSelectedEvent | null = null) {
  const procSpy = jasmine.createSpyObj<ProcurementService>('ProcurementService', ['searchSuppliers']);
  procSpy.searchSuppliers.and.returnValue(of(makePage([SUP1, SUP2])));

  await TestBed.configureTestingModule({
    imports: [SupplierTypeaheadComponent, HttpClientTestingModule],
    providers: [{ provide: ProcurementService, useValue: procSpy }],
  }).compileComponents();

  const fixture: ComponentFixture<SupplierTypeaheadComponent> =
    TestBed.createComponent(SupplierTypeaheadComponent);
  fixture.componentInstance.instanceId = 'test';
  if (initialSupplier) fixture.componentInstance.initialSupplier = initialSupplier;
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

describe('SupplierTypeaheadComponent', () => {

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

  describe('initial supplier pre-selection', () => {
    it('populates the input when initialSupplier is provided', async () => {
      const init: SupplierSelectedEvent = { partyUid: SUP1.partyUid, id: SUP1.id, code: SUP1.code, name: SUP1.name };
      const { fixture } = await setup(init);
      const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
      expect(input.value).toContain(SUP1.code);
    });

    it('shows the clear button when pre-selected', async () => {
      const init: SupplierSelectedEvent = { partyUid: SUP1.partyUid, id: SUP1.id, code: SUP1.code, name: SUP1.name };
      const { fixture } = await setup(init);
      const clearBtn = fixture.nativeElement.querySelector('button[aria-label="Clear supplier selection"]');
      expect(clearBtn).toBeTruthy();
    });
  });

  describe('loading state', () => {
    it('shows spinner while HTTP is in flight', fakeAsync(async () => {
      const { fixture, procSpy } = await setup();
      // Return an observable that never completes during the tick window.
      procSpy.searchSuppliers.and.returnValue(of(makePage([SUP1])).pipe(delay(500)));

      const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
      input.value = 'ac';
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
    it('calls searchSuppliers after debounce', fakeAsync(async () => {
      const { fixture, procSpy } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('acm');
      tick(260);
      fixture.detectChanges();

      expect(procSpy.searchSuppliers).toHaveBeenCalledWith('acm', 0, 20);
    }));

    it('renders result rows in the listbox', fakeAsync(async () => {
      const { fixture } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('acm');
      tick(260);
      fixture.detectChanges();

      const options = fixture.nativeElement.querySelectorAll('[role="option"]');
      expect(options.length).toBe(2);
    }));

    it('emits supplierSelected when a result is clicked', fakeAsync(async () => {
      const { fixture } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };
      const events: SupplierSelectedEvent[] = [];
      fixture.componentInstance.supplierSelected.subscribe((e: SupplierSelectedEvent) => events.push(e));

      comp.onQueryChange('acm');
      tick(260);
      fixture.detectChanges();

      const firstOption = fixture.nativeElement.querySelector('[role="option"]');
      firstOption.click();
      fixture.detectChanges();

      expect(events.length).toBe(1);
      expect(events[0].partyUid).toBe(SUP1.partyUid);
      expect(events[0].code).toBe(SUP1.code);
    }));

    it('hides the dropdown after selection', fakeAsync(async () => {
      const { fixture } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('acm');
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
      procSpy.searchSuppliers.and.returnValue(of(makePage([])));
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('zzz');
      tick(260);
      fixture.detectChanges();

      const listbox = fixture.nativeElement.querySelector('[role="listbox"]');
      expect(listbox).toBeTruthy();
      expect(listbox.textContent).toContain('No suppliers found');
    }));

    it('shows no-results message on HTTP error', fakeAsync(async () => {
      const { fixture, procSpy } = await setup();
      procSpy.searchSuppliers.and.returnValue(throwError(() => new Error('network')));
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

      comp.onQueryChange('acm');
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
      const events: SupplierSelectedEvent[] = [];
      fixture.componentInstance.supplierSelected.subscribe((e: SupplierSelectedEvent) => events.push(e));

      comp.onQueryChange('acm');
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

      comp.onQueryChange('acm');
      tick(260);
      fixture.detectChanges();

      comp.onKeydown(new KeyboardEvent('keydown', { key: 'Escape' }));
      expect(comp.dropdownOpen()).toBeFalse();
    }));
  });

  describe('clear', () => {
    it('emits supplierCleared and resets internal query/selected state', fakeAsync(async () => {
      const init: SupplierSelectedEvent = { partyUid: SUP1.partyUid, id: SUP1.id, code: SUP1.code, name: SUP1.name };
      const { fixture } = await setup(init);

      // Spy on the emit method — same pattern as GrnPickerModal cancel tests.
      const emitSpy = spyOn(fixture.componentInstance.supplierCleared, 'emit');

      const comp = fixture.componentInstance as unknown as {
        clear: () => void;
        query: string;
        selected: () => SupplierSelectedEvent | null;
      };
      comp.clear();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(emitSpy).toHaveBeenCalled();
      expect(comp.query).toBe('');
      expect(comp.selected()).toBeNull();
    }));
  });
});
