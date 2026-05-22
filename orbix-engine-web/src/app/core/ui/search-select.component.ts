import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  ViewChild,
  computed,
  forwardRef,
  input,
  signal
} from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Lightweight type-to-filter combobox. Backs an `ngModel` of the selected
 * option's id; renders a button showing the matching label and opens a
 * popover with a search box + filtered list on click.
 *
 * Pure-client filter — assumes the caller passes a fully-loaded list. For
 * lists too large to ship to the browser, swap in a remote-search variant.
 */
@Component({
  selector: 'orbix-search-select',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => SearchSelectComponent),
    multi: true
  }],
  template: `
    <div class="ss" [class.is-open]="open()">
      <button #trigger type="button" class="form-select form-select-sm ss__trigger"
              [class.text-secondary]="!selectedLabel()"
              [disabled]="disabled()"
              (click)="toggle()">
        {{ selectedLabel() || placeholder() }}
      </button>
      @if (open()) {
        <div class="ss__panel"
             [style.top.px]="panelPos().top"
             [style.left.px]="panelPos().left"
             [style.width.px]="panelPos().width">
          <input #search class="form-control form-control-sm ss__search"
                 type="text"
                 autocomplete="off"
                 [value]="query()"
                 (input)="onSearchInput($event)"
                 placeholder="Search…"
                 (keydown)="onKey($event)">
          <ul class="ss__list">
            @for (opt of filtered(); track opt.id; let i = $index) {
              <li>
                <button type="button" class="ss__option"
                        [class.is-selected]="opt.id === value()"
                        [class.is-focused]="i === focusIndex()"
                        (mousedown)="$event.preventDefault()"
                        (click)="choose(opt.id)">
                  {{ opt.label }}
                </button>
              </li>
            } @empty {
              <li class="ss__empty">No matches.</li>
            }
          </ul>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; position: relative; }
    .ss { position: relative; }
    .ss__trigger {
      text-align: left; cursor: pointer;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    /* position: fixed escapes any ancestor overflow (e.g. .table-responsive). */
    .ss__panel {
      position: fixed; z-index: 1080;
      background: #fff; border: 1px solid #e5e7eb; border-radius: 8px;
      box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12);
      padding: 0.5rem; min-width: 240px;
    }
    .ss__search { margin-bottom: 0.4rem; }
    .ss__list { list-style: none; margin: 0; padding: 0; max-height: 240px; overflow-y: auto; }
    .ss__option {
      width: 100%; text-align: left; background: transparent; border: none;
      padding: 0.4rem 0.6rem; font-size: 0.875rem; color: #111827;
      border-radius: 6px; cursor: pointer;
    }
    .ss__option:hover, .ss__option.is-focused { background: #eef4ff; color: #1d4ed8; }
    .ss__option.is-selected { background: #dbeafe; color: #0d2a5b; font-weight: 600; }
    .ss__empty { padding: 0.5rem 0.6rem; font-size: 0.8rem; color: #6b7280; }
  `]
})
export class SearchSelectComponent implements ControlValueAccessor {
  /** Option list. Each item must have a stable {@code id} and display {@code label}. */
  readonly options = input<readonly SearchSelectOption[]>([]);
  readonly placeholder = input<string>('Select…');

  /** Disabled-from-parent flag, kept as a signal so the template stays reactive. */
  readonly disabled = signal<boolean>(false);

  @ViewChild('search') private readonly searchInput?: ElementRef<HTMLInputElement>;
  @ViewChild('trigger') private readonly trigger?: ElementRef<HTMLButtonElement>;

  protected readonly value = signal<number | string | null>(null);
  protected readonly open = signal<boolean>(false);
  protected readonly focusIndex = signal<number>(-1);
  protected readonly query = signal<string>('');
  protected readonly panelPos = signal<{ top: number; left: number; width: number }>(
    { top: 0, left: 0, width: 0 }
  );

  protected readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const opts = this.options();
    if (!q) return opts;
    return opts.filter(o => o.label.toLowerCase().includes(q));
  });

  protected readonly selectedLabel = computed(() => {
    const id = this.value();
    if (id == null) return '';
    const match = this.options().find(o => o.id === id);
    return match ? match.label : '';
  });

  private onChange: (value: number | string | null) => void = () => {};
  private onTouched: () => void = () => {};

  toggle(): void {
    if (this.disabled()) return;
    const next = !this.open();
    if (next) {
      this.measurePanel();
      this.query.set('');
      this.focusIndex.set(-1);
    }
    this.open.set(next);
    if (next) {
      queueMicrotask(() => this.searchInput?.nativeElement.focus());
    }
  }

  choose(id: number | string): void {
    this.value.set(id);
    this.onChange(id);
    this.onTouched();
    this.open.set(false);
    this.trigger?.nativeElement.focus();
  }

  onSearchInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);
    this.focusIndex.set(-1);
  }

  private measurePanel(): void {
    const el = this.trigger?.nativeElement;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    this.panelPos.set({
      top: rect.bottom + 4,
      left: rect.left,
      width: Math.max(rect.width, 240)
    });
  }

  @HostListener('window:scroll')
  @HostListener('window:resize')
  onViewportChange(): void {
    if (this.open()) this.measurePanel();
  }

  onKey(event: KeyboardEvent): void {
    const list = this.filtered();
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.focusIndex.update(i => Math.min(i + 1, list.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.focusIndex.update(i => Math.max(i - 1, 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const i = this.focusIndex();
      const pick = i >= 0 ? list[i] : list[0];
      if (pick) this.choose(pick.id);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.open.set(false);
      this.trigger?.nativeElement.focus();
    }
  }

  // ControlValueAccessor -----------------------------------------------------

  writeValue(value: number | string | null): void { this.value.set(value); }
  registerOnChange(fn: (value: number | string | null) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(isDisabled: boolean): void { this.disabled.set(isDisabled); }

  @HostListener('document:click', ['$event'])
  onDocClick(event: MouseEvent): void {
    if (!this.open()) return;
    const host = (event.target as HTMLElement).closest('orbix-search-select');
    if (host !== this.trigger?.nativeElement.closest('orbix-search-select')) {
      this.open.set(false);
    }
  }
}

export interface SearchSelectOption {
  id: number | string;
  label: string;
}
