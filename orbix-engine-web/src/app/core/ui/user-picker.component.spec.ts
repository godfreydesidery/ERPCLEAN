/**
 * UserPickerComponent unit spec.
 * Covers: idle state, debounce trigger, result selection, keyboard nav,
 *         clear, no-results, error state, initial pre-selection.
 */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import { UserPickerComponent, UserSelectedEvent } from './user-picker.component';
import { CoreLookupService, UserLookupRow } from './core-lookup.service';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const U1: UserLookupRow = { id: '10', uid: '01JUSR00000000000001', displayName: 'Alice Mwangi', username: 'alice' };
const U2: UserLookupRow = { id: '11', uid: '01JUSR00000000000002', displayName: 'Bob Kamau', username: 'bob' };

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

async function setup(initial: UserSelectedEvent | null = null) {
  const lookupSpy = jasmine.createSpyObj<CoreLookupService>('CoreLookupService', [
    'listBranches', 'listPriceLists', 'listSections', 'listStockBatches', 'lookupUsers',
  ]);
  lookupSpy.lookupUsers.and.returnValue(of([U1, U2]));

  await TestBed.configureTestingModule({
    imports: [UserPickerComponent, HttpClientTestingModule],
    providers: [{ provide: CoreLookupService, useValue: lookupSpy }],
  }).compileComponents();

  const fixture: ComponentFixture<UserPickerComponent> =
    TestBed.createComponent(UserPickerComponent);
  fixture.componentInstance.instanceId = 'test';
  if (initial) fixture.componentInstance.initialUser = initial;
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, lookupSpy };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('UserPickerComponent', () => {

  describe('idle state', () => {
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

  describe('initial pre-selection', () => {
    it('populates the input when initialUser is provided', async () => {
      const init: UserSelectedEvent = { id: U1.id, uid: U1.uid, displayName: U1.displayName };
      const { fixture } = await setup(init);
      const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
      expect(input.value).toContain(U1.displayName);
    });

    it('shows the clear button when pre-selected', async () => {
      const init: UserSelectedEvent = { id: U1.id, uid: U1.uid, displayName: U1.displayName };
      const { fixture } = await setup(init);
      const clearBtn = fixture.nativeElement.querySelector('button[aria-label="Clear user selection"]');
      expect(clearBtn).toBeTruthy();
    });
  });

  describe('loading state', () => {
    it('shows spinner while HTTP is in flight', fakeAsync(async () => {
      const { fixture, lookupSpy } = await setup();
      lookupSpy.lookupUsers.and.returnValue(of([U1]).pipe(delay(500)));

      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };
      comp.onQueryChange('ali');
      tick(260); // past debounce, before HTTP completes
      fixture.detectChanges();

      const spinner = fixture.nativeElement.querySelector('.spinner-border');
      expect(spinner).toBeTruthy();

      tick(500);
      fixture.detectChanges();
    }));
  });

  describe('results state', () => {
    it('calls lookupUsers after 250 ms debounce', fakeAsync(async () => {
      const { lookupSpy, fixture } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('ali');
      tick(260);
      fixture.detectChanges();

      expect(lookupSpy.lookupUsers).toHaveBeenCalledWith('ali', 20);
    }));

    it('renders result rows in the listbox', fakeAsync(async () => {
      const { fixture } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('ali');
      tick(260);
      fixture.detectChanges();

      const options = fixture.nativeElement.querySelectorAll('[role="option"]');
      expect(options.length).toBe(2);
    }));

    it('emits userSelected when a result is clicked', fakeAsync(async () => {
      const { fixture, comp } = await setup();
      const events: UserSelectedEvent[] = [];
      comp.userSelected.subscribe((e: UserSelectedEvent) => events.push(e));

      const compAny = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };
      compAny.onQueryChange('ali');
      tick(260);
      fixture.detectChanges();

      const firstOption: HTMLElement = fixture.nativeElement.querySelector('[role="option"]');
      firstOption.click();
      fixture.detectChanges();

      expect(events.length).toBe(1);
      expect(events[0].uid).toBe(U1.uid);
      expect(events[0].displayName).toBe(U1.displayName);
    }));

    it('hides the dropdown after selection', fakeAsync(async () => {
      const { fixture } = await setup();
      const compAny = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      compAny.onQueryChange('ali');
      tick(260);
      fixture.detectChanges();

      const firstOption: HTMLElement = fixture.nativeElement.querySelector('[role="option"]');
      firstOption.click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[role="listbox"]')).toBeNull();
    }));
  });

  describe('no-results state', () => {
    it('shows no-results message when lookup returns empty', fakeAsync(async () => {
      const { fixture, lookupSpy } = await setup();
      lookupSpy.lookupUsers.and.returnValue(of([]));
      const compAny = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      compAny.onQueryChange('zzz');
      tick(260);
      fixture.detectChanges();

      const listbox = fixture.nativeElement.querySelector('[role="listbox"]');
      expect(listbox).toBeTruthy();
      expect(listbox.textContent).toContain('No users found');
    }));

    it('shows no-results message on HTTP error', fakeAsync(async () => {
      const { fixture, lookupSpy } = await setup();
      lookupSpy.lookupUsers.and.returnValue(throwError(() => new Error('network')));
      const compAny = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      compAny.onQueryChange('err');
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
        activeIndex: { (): number };
      };

      comp.onQueryChange('ali');
      tick(260);
      fixture.detectChanges();

      comp.onKeydown(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
      expect(comp.activeIndex()).toBe(0);
    }));

    it('Escape closes dropdown', fakeAsync(async () => {
      const { fixture } = await setup();
      const comp = fixture.componentInstance as unknown as {
        onQueryChange: (q: string) => void;
        onKeydown: (e: KeyboardEvent) => void;
        dropdownOpen: { (): boolean };
      };

      comp.onQueryChange('ali');
      tick(260);
      fixture.detectChanges();

      comp.onKeydown(new KeyboardEvent('keydown', { key: 'Escape' }));
      expect(comp.dropdownOpen()).toBeFalse();
    }));
  });

  describe('clear', () => {
    it('emits userCleared and resets state', fakeAsync(async () => {
      const init: UserSelectedEvent = { id: U1.id, uid: U1.uid, displayName: U1.displayName };
      const { fixture, comp } = await setup(init);

      const emitSpy = spyOn(comp.userCleared, 'emit');
      const compAny = comp as unknown as {
        clear: () => void;
        query: string;
        selected: { (): UserSelectedEvent | null };
      };

      compAny.clear();
      fixture.detectChanges();

      expect(emitSpy).toHaveBeenCalled();
      expect(compAny.query).toBe('');
      expect(compAny.selected()).toBeNull();
    }));
  });
});
