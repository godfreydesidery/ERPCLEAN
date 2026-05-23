import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

export interface FilterOption {
  value: string;
  label: string;
}

/**
 * A type-to-filter single-select. Behaves like a dropdown (you pick from the
 * list) but the long list is searchable. Free text that doesn't match an option
 * is discarded on blur, so the bound value is always a valid option value.
 *
 * Usage: <orbix-filter-select [options]="opts" [(value)]="model.code" placeholder="Currency"/>
 */
@Component({
  selector: 'orbix-filter-select',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="position-relative">
      <input class="form-control form-control-sm"
             [(ngModel)]="query" [placeholder]="placeholder" [disabled]="disabled"
             (focus)="open()" (input)="onInput()" (blur)="onBlur()" (keydown)="onKey($event)"
             autocomplete="off" role="combobox" [attr.aria-expanded]="isOpen">
      @if (isOpen) {
        <ul class="list-group position-absolute w-100 shadow-sm mt-1"
            style="z-index:1050; max-height:240px; overflow-y:auto;">
          @for (o of filtered(); track o.value; let i = $index) {
            <li class="list-group-item list-group-item-action py-1 small"
                style="cursor:pointer;"
                [class.active]="i === highlight"
                (mousedown)="select(o)">{{ o.label }}</li>
          } @empty {
            <li class="list-group-item py-1 small text-secondary">No matches</li>
          }
        </ul>
      }
    </div>
  `
})
export class FilterableSelectComponent implements OnChanges {
  @Input() options: FilterOption[] = [];
  @Input() placeholder = 'Select…';
  @Input() disabled = false;
  @Input() value: string | null = null;
  @Output() valueChange = new EventEmitter<string | null>();

  protected query = '';
  protected isOpen = false;
  protected highlight = 0;

  ngOnChanges(): void {
    // Keep the displayed text in sync with the bound value (e.g. when options
    // load asynchronously) unless the user is actively typing in an open list.
    if (!this.isOpen) {
      this.query = this.labelFor(this.value);
    }
  }

  protected filtered(): FilterOption[] {
    const q = this.query.trim().toLowerCase();
    // When the field still shows the current selection, list everything.
    if (!q || q === this.labelFor(this.value).toLowerCase()) {
      return this.options;
    }
    return this.options.filter(o =>
      o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q));
  }

  protected open(): void {
    if (this.disabled) return;
    this.isOpen = true;
    this.highlight = 0;
  }

  protected onInput(): void {
    this.isOpen = true;
    this.highlight = 0;
  }

  protected select(o: FilterOption): void {
    this.value = o.value;
    this.query = o.label;
    this.isOpen = false;
    this.valueChange.emit(o.value);
  }

  protected onBlur(): void {
    // Defer so an option's mousedown registers before we close + normalise.
    setTimeout(() => {
      this.isOpen = false;
      this.query = this.labelFor(this.value);
    }, 150);
  }

  protected onKey(e: KeyboardEvent): void {
    const list = this.filtered();
    switch (e.key) {
      case 'ArrowDown':
        this.isOpen = true;
        this.highlight = Math.min(this.highlight + 1, list.length - 1);
        e.preventDefault();
        break;
      case 'ArrowUp':
        this.highlight = Math.max(this.highlight - 1, 0);
        e.preventDefault();
        break;
      case 'Enter':
        if (this.isOpen && list[this.highlight]) {
          this.select(list[this.highlight]);
          e.preventDefault();
        }
        break;
      case 'Escape':
        this.isOpen = false;
        this.query = this.labelFor(this.value);
        break;
      default:
        break;
    }
  }

  private labelFor(value: string | null): string {
    if (!value) return '';
    return this.options.find(o => o.value === value)?.label ?? value;
  }
}
