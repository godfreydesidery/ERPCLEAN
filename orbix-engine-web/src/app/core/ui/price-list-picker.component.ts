import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { SearchSelectComponent } from './search-select.component';
import { CoreLookupService, PriceListSummary } from './core-lookup.service';

export interface PriceListSelectedEvent {
  id: string;
  uid: string;
  code: string;
  name: string;
}

/**
 * Loads all price-lists once on init and presents a searchable select.
 *
 * Selector: `orbix-price-list-picker`
 *
 * Inputs:
 *   - label (string, default "Price list").
 *   - required (boolean, default true).
 *   - initialPriceList (PriceListSelectedEvent | null) — pre-selects on edit.
 *   - instanceId (string) — unique suffix for id/aria attributes.
 *   - disabled (boolean, default false).
 *
 * Outputs:
 *   - priceListSelected — emits PriceListSelectedEvent.
 *   - priceListCleared  — emits void.
 */
@Component({
  selector: 'orbix-price-list-picker',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchSelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="price-list-picker">
      <label [for]="selectId" class="form-label small fw-semibold text-secondary">
        {{ label }}<ng-container *ngIf="required"> <span class="text-danger" aria-hidden="true">*</span></ng-container>
      </label>

      @if (loadError()) {
        <div class="alert alert-warning py-1 px-2 small">Failed to load price lists.</div>
      } @else {
        <orbix-search-select
          [id]="selectId"
          [options]="options()"
          [placeholder]="loading() ? 'Loading price lists…' : 'Select price list…'"
          [disabled]="loading() || disabled"
          [(ngModel)]="selectedId"
          (ngModelChange)="onSelect($event)"
          [attr.aria-label]="label"
          [attr.aria-required]="required"
          [attr.aria-describedby]="touched && required && !selectedId ? errorId : null"
        ></orbix-search-select>
      }

      @if (touched && required && !selectedId) {
        <div [id]="errorId" class="text-danger" style="font-size:0.78rem; margin-top:0.25rem;">
          Select a price list.
        </div>
      }
    </div>
  `,
  styles: [`:host { display: block; }`],
})
export class PriceListPickerComponent implements OnInit, OnChanges, OnDestroy {
  private readonly lookup = inject(CoreLookupService);

  @Input() label = 'Price list';
  @Input() required = true;
  @Input() disabled = false;
  @Input() instanceId = 'default';
  @Input() initialPriceList: PriceListSelectedEvent | null = null;

  @Output() readonly priceListSelected = new EventEmitter<PriceListSelectedEvent>();
  @Output() readonly priceListCleared = new EventEmitter<void>();

  protected readonly priceLists = signal<PriceListSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);

  protected readonly options = computed(() =>
    this.priceLists().map(pl => ({ id: pl.uid, label: `${pl.code} — ${pl.name}` }))
  );

  protected selectedId: string | null = null;
  protected touched = false;

  get selectId(): string { return `price-list-picker-select-${this.instanceId}`; }
  get errorId(): string { return `price-list-picker-error-${this.instanceId}`; }

  private sub?: Subscription;

  ngOnInit(): void {
    this.sub = this.lookup.listPriceLists().subscribe({
      next: list => {
        this.priceLists.set(list);
        this.loading.set(false);
        if (this.initialPriceList) this.applyInitial(this.initialPriceList);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['initialPriceList'] && this.initialPriceList && !this.loading()) {
      this.applyInitial(this.initialPriceList);
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  protected onSelect(uid: string | number | null): void {
    this.touched = true;
    if (!uid) {
      this.selectedId = null;
      this.priceListCleared.emit();
      return;
    }
    const pl = this.priceLists().find(p => p.uid === uid);
    if (pl) {
      this.priceListSelected.emit({
        id: pl.id,
        uid: pl.uid,
        code: pl.code,
        name: pl.name,
      });
    }
  }

  private applyInitial(pl: PriceListSelectedEvent): void {
    this.selectedId = pl.uid;
  }
}
