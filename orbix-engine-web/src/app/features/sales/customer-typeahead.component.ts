import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { Page } from '../../core/api/page';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface CustomerSelectedEvent {
  partyUid: string;
  id: string;
  code: string;
  name: string;
}

/** Lightweight customer row returned by GET /api/v1/customers?q= */
export interface CustomerSummary {
  /** Party numeric PK serialised as string (JacksonConfig global Long-as-string). */
  partyId: string;
  party: {
    id: string;
    uid: string;
    code: string;
    name: string;
  };
}

type TypeaheadState = 'idle' | 'loading' | 'results' | 'no-results';

/**
 * Reusable standalone customer typeahead (Slice K / US-RPT-007).
 *
 * Emits {@link CustomerSelectedEvent} when the user picks a result.
 * Debounces HTTP at 250 ms. WCAG AA: role="combobox", aria-expanded,
 * arrow-key + Enter + Escape navigation, focus returns to input on close.
 *
 * Backed by GET /api/v1/customers?q=&page=0&size=20 (unauth-gated list).
 * Mirror of features/procurement/supplier-typeahead.component.ts.
 */
@Component({
  selector: 'orbix-customer-typeahead',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="customer-typeahead position-relative" [attr.data-state]="state()">

      <label [for]="inputId" class="form-label small fw-semibold text-secondary">
        Customer <span class="text-danger" aria-hidden="true">*</span>
      </label>

      <div class="input-group">
        <input
          #inputEl
          [id]="inputId"
          type="text"
          class="form-control"
          [class.is-invalid]="touched && !selected()"
          autocomplete="off"
          role="combobox"
          [attr.aria-expanded]="dropdownOpen()"
          [attr.aria-controls]="listboxId"
          [attr.aria-activedescendant]="activeDescendant()"
          aria-autocomplete="list"
          aria-haspopup="listbox"
          [attr.aria-label]="ariaLabel"
          [placeholder]="placeholder"
          [(ngModel)]="query"
          (ngModelChange)="onQueryChange($event)"
          (keydown)="onKeydown($event)"
          (blur)="onBlur()"
          (focus)="onFocus()"
          [attr.required]="required || null"
          [attr.data-testid]="inputTestid ?? null"
        >
        @if (state() === 'loading') {
          <span class="input-group-text bg-white border-start-0">
            <span class="spinner-border spinner-border-sm text-secondary" aria-hidden="true"></span>
            <span class="visually-hidden">Searching customers…</span>
          </span>
        }
        @if (selected()) {
          <button type="button" class="btn btn-outline-secondary border-start-0"
                  aria-label="Clear customer selection"
                  (click)="clear()">
            <i class="bi bi-x-lg" aria-hidden="true"></i>
          </button>
        }
      </div>

      @if (touched && !selected()) {
        <div class="invalid-feedback d-block">Select a customer from the list.</div>
      }

      <!-- Dropdown listbox -->
      @if (dropdownOpen()) {
        <ul
          [id]="listboxId"
          role="listbox"
          class="customer-typeahead__dropdown list-unstyled mb-0 border rounded bg-white shadow-sm position-absolute w-100"
          style="z-index:1055; max-height:240px; overflow-y:auto; top:calc(100% + 2px);"
          (mousedown)="$event.preventDefault()"
        >
          @if (state() === 'no-results') {
            <li class="px-3 py-2 small text-secondary" role="option" aria-selected="false">
              No customers found for "{{ query }}"
            </li>
          }
          @for (c of results(); track c.party.uid; let i = $index) {
            <li
              [id]="optionId(i)"
              role="option"
              [attr.aria-selected]="activeIndex() === i"
              class="customer-typeahead__option px-3 py-2 small"
              [class.is-active]="activeIndex() === i"
              (click)="selectResult(c)"
              (mouseenter)="activeIndex.set(i)"
            >
              <span class="fw-semibold">{{ c.party.code }}</span>
              <span class="text-secondary ms-2">{{ c.party.name }}</span>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .customer-typeahead__dropdown { font-size: 0.875rem; }
    .customer-typeahead__option { cursor: pointer; }
    .customer-typeahead__option:hover,
    .customer-typeahead__option.is-active { background: #eef4ff; color: #1d4ed8; }
    .invalid-feedback { font-size: 0.78rem; }
  `],
})
export class CustomerTypeaheadComponent implements OnInit, OnChanges, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** Pre-select a customer on edit forms. Setting this renders the name in the input. */
  @Input() initialCustomer: CustomerSelectedEvent | null = null;

  /** Forwarded to the inner input's aria-label (supplement the visible label). */
  @Input() ariaLabel = 'Customer';

  @Input() placeholder = 'Type to search customers…';
  @Input() required = true;

  /** Unique suffix so multiple instances on one page don't clash. */
  @Input() instanceId = 'default';

  /** Optional data-testid forwarded to the inner <input> for Playwright targeting. */
  @Input() inputTestid?: string;

  @Output() readonly customerSelected = new EventEmitter<CustomerSelectedEvent>();
  @Output() readonly customerCleared = new EventEmitter<void>();

  @ViewChild('inputEl') private inputEl?: ElementRef<HTMLInputElement>;

  protected readonly state = signal<TypeaheadState>('idle');
  protected readonly results = signal<CustomerSummary[]>([]);
  protected readonly selected = signal<CustomerSelectedEvent | null>(null);
  protected readonly dropdownOpen = signal(false);
  protected readonly activeIndex = signal(-1);
  protected touched = false;

  protected query = '';

  get inputId(): string { return `customer-typeahead-input-${this.instanceId}`; }
  get listboxId(): string { return `customer-typeahead-list-${this.instanceId}`; }
  optionId(i: number): string { return `customer-typeahead-opt-${this.instanceId}-${i}`; }

  protected activeDescendant(): string | null {
    const i = this.activeIndex();
    return i >= 0 ? this.optionId(i) : null;
  }

  private readonly search$ = new Subject<string>();
  private sub?: Subscription;

  ngOnInit(): void {
    this.sub = this.search$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(q => {
        this.state.set('loading');
        const params = new HttpParams()
          .set('q', q.trim())
          .set('page', 0)
          .set('size', 20);
        return unwrap(
          this.http.get<ApiResponse<Page<CustomerSummary>>>(`${this.base}/customers`, { params })
        );
      }),
    ).subscribe({
      next: page => {
        this.results.set(page.content);
        this.state.set(page.content.length > 0 ? 'results' : 'no-results');
        this.dropdownOpen.set(true);
        this.activeIndex.set(-1);
      },
      error: () => {
        this.results.set([]);
        this.state.set('no-results');
        this.dropdownOpen.set(true);
      },
    });

    if (this.initialCustomer) {
      this.applyInitial(this.initialCustomer);
    }
  }

  ngOnChanges(): void {
    if (this.initialCustomer && !this.selected()) {
      this.applyInitial(this.initialCustomer);
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  protected onQueryChange(q: string): void {
    if (this.selected()) {
      this.selected.set(null);
      this.customerCleared.emit();
    }
    if (!q.trim()) {
      this.state.set('idle');
      this.dropdownOpen.set(false);
      this.results.set([]);
      return;
    }
    this.search$.next(q);
  }

  protected onFocus(): void {
    if (this.results().length > 0 && !this.selected()) {
      this.dropdownOpen.set(true);
    }
  }

  protected onBlur(): void {
    this.touched = true;
    setTimeout(() => this.dropdownOpen.set(false), 150);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const len = this.results().length;
    if (!this.dropdownOpen() || len === 0) return;

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.activeIndex.update(i => Math.min(i + 1, len - 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.activeIndex.update(i => Math.max(i - 1, 0));
        break;
      case 'Enter': {
        event.preventDefault();
        const idx = this.activeIndex();
        if (idx >= 0 && idx < len) {
          this.selectResult(this.results()[idx]);
        }
        break;
      }
      case 'Escape':
        event.preventDefault();
        this.dropdownOpen.set(false);
        this.activeIndex.set(-1);
        break;
    }
  }

  protected selectResult(c: CustomerSummary): void {
    const evt: CustomerSelectedEvent = {
      partyUid: c.party.uid,
      id: c.partyId,
      code: c.party.code,
      name: c.party.name,
    };
    this.selected.set(evt);
    this.query = `${c.party.code} — ${c.party.name}`;
    this.dropdownOpen.set(false);
    this.activeIndex.set(-1);
    this.state.set('idle');
    this.customerSelected.emit(evt);
  }

  protected clear(): void {
    this.selected.set(null);
    this.query = '';
    this.results.set([]);
    this.state.set('idle');
    this.dropdownOpen.set(false);
    this.touched = false;
    this.customerCleared.emit();
    this.inputEl?.nativeElement.focus();
  }

  /** Click outside the host closes the dropdown. */
  @HostListener('document:click', ['$event.target'])
  onDocumentClick(target: HTMLElement): void {
    if (!this.dropdownOpen()) return;
    const host = this.inputEl?.nativeElement.closest('.customer-typeahead');
    if (host && !host.contains(target)) {
      this.dropdownOpen.set(false);
    }
  }

  private applyInitial(c: CustomerSelectedEvent): void {
    this.selected.set(c);
    this.query = `${c.code} — ${c.name}`;
  }
}
