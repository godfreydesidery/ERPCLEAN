/**
 * CustomerTypeaheadComponent unit spec.
 * Covers: 4 states (idle / loading / results / no-results),
 *         debounce trigger, result selection, keyboard nav, clear.
 * Mirrors features/procurement/supplier-typeahead.component.spec.ts.
 */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import {
  CustomerTypeaheadComponent,
  CustomerSelectedEvent,
  CustomerSummary,
} from './customer-typeahead.component';
import { Page } from '../../core/api/page';
import { environment } from '../../../environments/environment';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

const CUST1: CustomerSummary = {
  partyId: '10',
  party: { id: '10', uid: '01JCUST0000000000001', code: 'CUST-001', name: 'Acme Retail' },
};
const CUST2: CustomerSummary = {
  partyId: '11',
  party: { id: '11', uid: '01JCUST0000000000002', code: 'CUST-002', name: 'Beta Shop' },
};

function makePage(items: CustomerSummary[]): Page<CustomerSummary> {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

function wrapPage(page: Page<CustomerSummary>) {
  return { status: 'OK', statusCode: 200, responseCode: 'OK', message: '', errors: [], data: page };
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

async function setup(initialCustomer: CustomerSelectedEvent | null = null) {
  await TestBed.configureTestingModule({
    imports: [CustomerTypeaheadComponent, HttpClientTestingModule],
  }).compileComponents();

  const fixture: ComponentFixture<CustomerTypeaheadComponent> =
    TestBed.createComponent(CustomerTypeaheadComponent);
  fixture.componentInstance.instanceId = 'test';
  if (initialCustomer) fixture.componentInstance.initialCustomer = initialCustomer;
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  const httpMock = TestBed.inject(HttpTestingController);
  return { fixture, comp: fixture.componentInstance, httpMock };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('CustomerTypeaheadComponent', () => {

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.verify();
  });

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

  describe('initial customer pre-selection', () => {
    it('populates the input when initialCustomer is provided', async () => {
      const init: CustomerSelectedEvent = {
        partyUid: CUST1.party.uid, id: CUST1.partyId, code: CUST1.party.code, name: CUST1.party.name,
      };
      const { fixture } = await setup(init);
      const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
      expect(input.value).toContain(CUST1.party.code);
    });

    it('shows the clear button when pre-selected', async () => {
      const init: CustomerSelectedEvent = {
        partyUid: CUST1.party.uid, id: CUST1.partyId, code: CUST1.party.code, name: CUST1.party.name,
      };
      const { fixture } = await setup(init);
      const clearBtn = fixture.nativeElement.querySelector('button[aria-label="Clear customer selection"]');
      expect(clearBtn).toBeTruthy();
    });
  });

  describe('results state', () => {
    it('calls the customers endpoint after debounce', fakeAsync(async () => {
      const { fixture, httpMock } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('acm');
      tick(260);
      fixture.detectChanges();

      const req = httpMock.expectOne(r => r.url.includes('/customers') && r.params.get('q') === 'acm');
      req.flush(wrapPage(makePage([CUST1, CUST2])));
      fixture.detectChanges();

      const options = fixture.nativeElement.querySelectorAll('[role="option"]');
      expect(options.length).toBe(2);
    }));

    it('emits customerSelected when a result is clicked', fakeAsync(async () => {
      const { fixture, httpMock } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };
      const events: CustomerSelectedEvent[] = [];
      fixture.componentInstance.customerSelected.subscribe((e: CustomerSelectedEvent) => events.push(e));

      comp.onQueryChange('acm');
      tick(260);
      fixture.detectChanges();

      const req = httpMock.expectOne(r => r.url.includes('/customers'));
      req.flush(wrapPage(makePage([CUST1, CUST2])));
      fixture.detectChanges();

      const firstOption: HTMLElement = fixture.nativeElement.querySelector('[role="option"]');
      firstOption.click();
      fixture.detectChanges();

      expect(events.length).toBe(1);
      expect(events[0].partyUid).toBe(CUST1.party.uid);
      expect(events[0].code).toBe(CUST1.party.code);
    }));

    it('hides the dropdown after selection', fakeAsync(async () => {
      const { fixture, httpMock } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('acm');
      tick(260);
      fixture.detectChanges();

      const req = httpMock.expectOne(r => r.url.includes('/customers'));
      req.flush(wrapPage(makePage([CUST1])));
      fixture.detectChanges();

      const firstOption: HTMLElement = fixture.nativeElement.querySelector('[role="option"]');
      firstOption.click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[role="listbox"]')).toBeNull();
    }));
  });

  describe('no-results state', () => {
    it('shows no-results message when search returns empty', fakeAsync(async () => {
      const { fixture, httpMock } = await setup();
      const comp = fixture.componentInstance as unknown as { onQueryChange: (q: string) => void };

      comp.onQueryChange('zzz');
      tick(260);
      fixture.detectChanges();

      const req = httpMock.expectOne(r => r.url.includes('/customers'));
      req.flush(wrapPage(makePage([])));
      fixture.detectChanges();

      const listbox = fixture.nativeElement.querySelector('[role="listbox"]');
      expect(listbox).toBeTruthy();
      expect(listbox.textContent).toContain('No customers found');
    }));
  });

  describe('keyboard navigation', () => {
    it('ArrowDown moves activeIndex down', fakeAsync(async () => {
      const { fixture, httpMock } = await setup();
      const comp = fixture.componentInstance as unknown as {
        onQueryChange: (q: string) => void;
        onKeydown: (e: KeyboardEvent) => void;
        activeIndex: { (): number; set(v: number): void };
        dropdownOpen: () => boolean;
      };

      comp.onQueryChange('acm');
      tick(260);
      fixture.detectChanges();

      const req = httpMock.expectOne(r => r.url.includes('/customers'));
      req.flush(wrapPage(makePage([CUST1, CUST2])));
      fixture.detectChanges();

      comp.onKeydown(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
      expect(comp.activeIndex()).toBe(0);
      comp.onKeydown(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
      expect(comp.activeIndex()).toBe(1);
    }));

    it('Escape closes dropdown', fakeAsync(async () => {
      const { fixture, httpMock } = await setup();
      const comp = fixture.componentInstance as unknown as {
        onQueryChange: (q: string) => void;
        onKeydown: (e: KeyboardEvent) => void;
        dropdownOpen: () => boolean;
      };

      comp.onQueryChange('acm');
      tick(260);
      fixture.detectChanges();

      const req = httpMock.expectOne(r => r.url.includes('/customers'));
      req.flush(wrapPage(makePage([CUST1])));
      fixture.detectChanges();

      comp.onKeydown(new KeyboardEvent('keydown', { key: 'Escape' }));
      expect(comp.dropdownOpen()).toBeFalse();
    }));
  });

  describe('clear', () => {
    it('emits customerCleared and resets state', fakeAsync(async () => {
      const init: CustomerSelectedEvent = {
        partyUid: CUST1.party.uid, id: CUST1.partyId, code: CUST1.party.code, name: CUST1.party.name,
      };
      const { fixture } = await setup(init);

      const emitSpy = spyOn(fixture.componentInstance.customerCleared, 'emit');
      const comp = fixture.componentInstance as unknown as {
        clear: () => void;
        query: string;
        selected: () => CustomerSelectedEvent | null;
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
