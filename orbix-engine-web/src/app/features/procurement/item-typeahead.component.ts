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
import { ItemSummary } from './procurement.models';

export interface ItemSelectedEvent {
  uid: string;
  id: string;
  code: string;
  name: string;
  defaultUomUid: string | null;
  defaultUomCode: string | null;
  defaultVatGroupUid: string | null;
}

type TypeaheadState = 'idle' | 'loading' | 'results' | 'no-results';

/**
 * Reusable standalone item typeahead.
 *
 * Emits {@link ItemSelectedEvent} when the user picks a result.
 * Debounces HTTP at 250 ms. WCAG AA: role="combobox", aria-expanded,
 * arrow-key + Enter + Escape navigation, focus returns to input on close.
 * On selection, the event carries defaultUomUid / defaultVatGroupUid so the
 * parent line table can auto-populate the UoM and VAT-group dropdowns.
 */
@Component({
  selector: 'orbix-item-typeahead',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="item-typeahead position-relative" [attr.data-state]="state()">

      <label [for]="inputId" class="form-label small fw-semibold text-secondary">
        Item <span class="text-danger" aria-hidden="true">*</span>
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
          [disabled]="disabled"
        >
        @if (state() === 'loading') {
          <span class="input-group-text bg-white border-start-0">
            <span class="spinner-border spinner-border-sm text-secondary" aria-hidden="true"></span>
            <span class="visually-hidden">Searching items…</span>
          </span>
        }
        @if (selected()) {
          <button type="button" class="btn btn-outline-secondary border-start-0"
                  aria-label="Clear item selection"
                  (click)="clear()">
            <i class="bi bi-x-lg" aria-hidden="true"></i>
          </button>
        }
      </div>

      @if (touched && !selected()) {
        <div class="invalid-feedback d-block">Select an item from the list.</div>
      }

      <!-- Dropdown listbox -->
      @if (dropdownOpen()) {
        <ul
          [id]="listboxId"
          role="listbox"
          class="item-typeahead__dropdown list-unstyled mb-0 border rounded bg-white shadow-sm position-absolute w-100"
          style="z-index:1055; max-height:240px; overflow-y:auto; top:calc(100% + 2px);"
          (mousedown)="$event.preventDefault()"
        >
          @if (state() === 'no-results') {
            <li class="px-3 py-2 small text-secondary" role="option" aria-selected="false">
              No items found for "{{ query }}"
            </li>
          }
          @for (item of results(); track item.uid; let i = $index) {
            <li
              [id]="optionId(i)"
              role="option"
              [attr.aria-selected]="activeIndex() === i"
              class="item-typeahead__option px-3 py-2 small"
              [class.is-active]="activeIndex() === i"
              (click)="selectResult(item)"
              (mouseenter)="activeIndex.set(i)"
            >
              <span class="fw-semibold">{{ item.code }}</span>
              <span class="text-secondary ms-2">{{ item.name }}</span>
              @if (item.defaultUomCode) {
                <span class="badge text-bg-light border ms-2 font-monospace" style="font-size:0.7rem;">
                  {{ item.defaultUomCode }}
                </span>
              }
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .item-typeahead__dropdown { font-size: 0.875rem; }
    .item-typeahead__option { cursor: pointer; }
    .item-typeahead__option:hover,
    .item-typeahead__option.is-active { background: #eef4ff; color: #1d4ed8; }
    .invalid-feedback { font-size: 0.78rem; }
  `],
})
export class ItemTypeaheadComponent implements OnInit, OnChanges, OnDestroy {
  private readonly procurement = inject(ProcurementService);

  /** Pre-select an item on edit forms. Setting this renders the code+name in the input. */
  @Input() initialItem: ItemSelectedEvent | null = null;

  /** When true the inner input is disabled. */
  @Input() disabled = false;

  /** Forwarded to the inner input's aria-label (supplement the visible label). */
  @Input() ariaLabel = 'Item';

  @Input() placeholder = 'Type to search items…';
  @Input() required = true;

  /** Unique suffix so multiple instances on one page don't clash. */
  @Input() instanceId = 'default';

  @Output() readonly itemSelected = new EventEmitter<ItemSelectedEvent>();
  @Output() readonly itemCleared = new EventEmitter<void>();

  @ViewChild('inputEl') private inputEl?: ElementRef<HTMLInputElement>;

  protected readonly state = signal<TypeaheadState>('idle');
  protected readonly results = signal<ItemSummary[]>([]);
  protected readonly selected = signal<ItemSelectedEvent | null>(null);
  protected readonly dropdownOpen = signal(false);
  protected readonly activeIndex = signal(-1);
  protected touched = false;

  protected query = '';

  get inputId(): string { return `item-typeahead-input-${this.instanceId}`; }
  get listboxId(): string { return `item-typeahead-list-${this.instanceId}`; }
  optionId(i: number): string { return `item-typeahead-opt-${this.instanceId}-${i}`; }

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
        return this.procurement.searchItems(q.trim(), 20);
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

    if (this.initialItem) {
      this.applyInitial(this.initialItem);
    }
  }

  ngOnChanges(): void {
    if (this.initialItem && !this.selected()) {
      this.applyInitial(this.initialItem);
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  protected onQueryChange(q: string): void {
    // User typed — clear any prior selection.
    if (this.selected()) {
      this.selected.set(null);
      this.itemCleared.emit();
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

  protected selectResult(item: ItemSummary): void {
    const evt: ItemSelectedEvent = {
      uid: item.uid,
      id: item.id,
      code: item.code,
      name: item.name,
      defaultUomUid: item.defaultUomUid,
      defaultUomCode: item.defaultUomCode,
      defaultVatGroupUid: item.defaultVatGroupUid,
    };
    this.selected.set(evt);
    this.query = `${item.code} — ${item.name}`;
    this.dropdownOpen.set(false);
    this.activeIndex.set(-1);
    this.state.set('idle');
    this.itemSelected.emit(evt);
  }

  protected clear(): void {
    this.selected.set(null);
    this.query = '';
    this.results.set([]);
    this.state.set('idle');
    this.dropdownOpen.set(false);
    this.touched = false;
    this.itemCleared.emit();
    this.inputEl?.nativeElement.focus();
  }

  /** Click outside the host closes the dropdown. */
  @HostListener('document:click', ['$event.target'])
  onDocumentClick(target: HTMLElement): void {
    if (!this.dropdownOpen()) return;
    const host = this.inputEl?.nativeElement.closest('.item-typeahead');
    if (host && !host.contains(target)) {
      this.dropdownOpen.set(false);
    }
  }

  private applyInitial(item: ItemSelectedEvent): void {
    this.selected.set(item);
    this.query = `${item.code} — ${item.name}`;
  }
}
