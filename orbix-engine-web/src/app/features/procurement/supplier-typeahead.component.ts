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
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { ProcurementService } from './procurement.service';
import { SupplierSummary } from './procurement.models';

export interface SupplierSelectedEvent {
  partyUid: string;
  id: string;
  code: string;
  name: string;
}

type TypeaheadState = 'idle' | 'loading' | 'results' | 'no-results';

/**
 * Reusable standalone supplier typeahead.
 *
 * Emits {@link SupplierSelectedEvent} when the user picks a result.
 * Debounces HTTP at 250 ms. WCAG AA: role="combobox", aria-expanded,
 * arrow-key + Enter + Escape navigation, focus returns to input on close.
 */
@Component({
  selector: 'orbix-supplier-typeahead',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="supplier-typeahead position-relative" [attr.data-state]="state()">

      <label [for]="inputId" class="form-label small fw-semibold text-secondary">
        Supplier <span class="text-danger" aria-hidden="true">*</span>
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
            <span class="visually-hidden">Searching suppliers…</span>
          </span>
        }
        @if (selected()) {
          <button type="button" class="btn btn-outline-secondary border-start-0"
                  aria-label="Clear supplier selection"
                  (click)="clear()">
            <i class="bi bi-x-lg" aria-hidden="true"></i>
          </button>
        }
      </div>

      @if (touched && !selected()) {
        <div class="invalid-feedback d-block">Select a supplier from the list.</div>
      }

      <!-- Dropdown listbox -->
      @if (dropdownOpen()) {
        <ul
          [id]="listboxId"
          role="listbox"
          class="supplier-typeahead__dropdown list-unstyled mb-0 border rounded bg-white shadow-sm position-absolute w-100"
          style="z-index:1055; max-height:240px; overflow-y:auto; top:calc(100% + 2px);"
          (mousedown)="$event.preventDefault()"
        >
          @if (state() === 'no-results') {
            <li class="px-3 py-2 small text-secondary" role="option" aria-selected="false">
              No suppliers found for "{{ query }}"
            </li>
          }
          @for (s of results(); track s.partyUid; let i = $index) {
            <li
              [id]="optionId(i)"
              role="option"
              [attr.aria-selected]="activeIndex() === i"
              class="supplier-typeahead__option px-3 py-2 small"
              [class.is-active]="activeIndex() === i"
              (click)="selectResult(s)"
              (mouseenter)="activeIndex.set(i)"
            >
              <span class="fw-semibold">{{ s.code }}</span>
              <span class="text-secondary ms-2">{{ s.name }}</span>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .supplier-typeahead__dropdown { font-size: 0.875rem; }
    .supplier-typeahead__option { cursor: pointer; }
    .supplier-typeahead__option:hover,
    .supplier-typeahead__option.is-active { background: #eef4ff; color: #1d4ed8; }
    .invalid-feedback { font-size: 0.78rem; }
  `],
})
export class SupplierTypeaheadComponent implements OnInit, OnChanges, OnDestroy {
  private readonly procurement = inject(ProcurementService);

  /** Pre-select a supplier on edit forms. Setting this renders the name in the input. */
  @Input() initialSupplier: SupplierSelectedEvent | null = null;

  /** Forwarded to the inner input's aria-label (supplement the visible label). */
  @Input() ariaLabel = 'Supplier';

  @Input() placeholder = 'Type to search suppliers…';
  @Input() required = true;

  /** Unique suffix so multiple instances on one page don't clash. */
  @Input() instanceId = 'default';

  /** Optional data-testid forwarded to the inner <input> for Playwright targeting. */
  @Input() inputTestid?: string;

  @Output() readonly supplierSelected = new EventEmitter<SupplierSelectedEvent>();
  @Output() readonly supplierCleared = new EventEmitter<void>();

  @ViewChild('inputEl') private inputEl?: ElementRef<HTMLInputElement>;

  protected readonly state = signal<TypeaheadState>('idle');
  protected readonly results = signal<SupplierSummary[]>([]);
  protected readonly selected = signal<SupplierSelectedEvent | null>(null);
  protected readonly dropdownOpen = signal(false);
  protected readonly activeIndex = signal(-1);
  protected touched = false;

  protected query = '';

  get inputId(): string { return `supplier-typeahead-input-${this.instanceId}`; }
  get listboxId(): string { return `supplier-typeahead-list-${this.instanceId}`; }
  optionId(i: number): string { return `supplier-typeahead-opt-${this.instanceId}-${i}`; }

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
        return this.procurement.searchSuppliers(q.trim(), 0, 20);
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

    if (this.initialSupplier) {
      this.applyInitial(this.initialSupplier);
    }
  }

  ngOnChanges(): void {
    if (this.initialSupplier && !this.selected()) {
      this.applyInitial(this.initialSupplier);
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  protected onQueryChange(q: string): void {
    // User typed — clear any prior selection.
    if (this.selected()) {
      this.selected.set(null);
      this.supplierCleared.emit();
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
    // Small delay so click on an option fires before we close.
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

  protected selectResult(s: SupplierSummary): void {
    const evt: SupplierSelectedEvent = {
      partyUid: s.partyUid,
      id: s.id,
      code: s.code,
      name: s.name,
    };
    this.selected.set(evt);
    this.query = `${s.code} — ${s.name}`;
    this.dropdownOpen.set(false);
    this.activeIndex.set(-1);
    this.state.set('idle');
    this.supplierSelected.emit(evt);
  }

  protected clear(): void {
    this.selected.set(null);
    this.query = '';
    this.results.set([]);
    this.state.set('idle');
    this.dropdownOpen.set(false);
    this.touched = false;
    this.supplierCleared.emit();
    this.inputEl?.nativeElement.focus();
  }

  /** Click outside the host closes the dropdown. */
  @HostListener('document:click', ['$event.target'])
  onDocumentClick(target: HTMLElement): void {
    if (!this.dropdownOpen()) return;
    const host = this.inputEl?.nativeElement.closest('.supplier-typeahead');
    if (host && !host.contains(target)) {
      this.dropdownOpen.set(false);
    }
  }

  private applyInitial(s: SupplierSelectedEvent): void {
    this.selected.set(s);
    this.query = `${s.code} — ${s.name}`;
  }
}
