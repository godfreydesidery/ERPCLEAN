import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
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
import { CoreLookupService, StockBatchSummary } from './core-lookup.service';

export interface BatchSelectedEvent {
  id: string;
  uid: string;
  batchNo: string;
}

/**
 * Item-scoped batch picker. Stays disabled until `itemId` is provided.
 * Reloads whenever `itemId` changes.
 *
 * Selector: `orbix-batch-picker`
 *
 * Inputs:
 *   - itemId (string | null) — numeric item id (string-serialised Long);
 *     required to fetch; null disables the picker.
 *   - label (string, default "Batch").
 *   - required (boolean, default false — not all items are batch-tracked).
 *   - instanceId (string).
 *   - initialBatch (BatchSelectedEvent | null) — pre-selects on edit.
 *   - disabled (boolean, default false).
 *
 * Outputs:
 *   - batchSelected — emits BatchSelectedEvent.
 *   - batchCleared  — emits void.
 */
@Component({
  selector: 'orbix-batch-picker',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchSelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="batch-picker">
      <label [for]="selectId" class="form-label small fw-semibold text-secondary">
        {{ label }}<ng-container *ngIf="required"> <span class="text-danger" aria-hidden="true">*</span></ng-container>
      </label>

      @if (loadError()) {
        <div class="alert alert-warning py-1 px-2 small">Failed to load batches.</div>
      } @else {
        <orbix-search-select
          [id]="selectId"
          [options]="options()"
          [placeholder]="placeholderText()"
          [disabled]="isDisabled()"
          [(ngModel)]="selectedId"
          (ngModelChange)="onSelect($event)"
          [attr.aria-label]="label"
          [attr.aria-required]="required"
          [attr.aria-describedby]="touched && required && !selectedId ? errorId : null"
        ></orbix-search-select>
      }

      @if (touched && required && !selectedId) {
        <div [id]="errorId" class="text-danger" style="font-size:0.78rem; margin-top:0.25rem;">
          Select a batch.
        </div>
      }
    </div>
  `,
  styles: [`:host { display: block; }`],
})
export class BatchPickerComponent implements OnChanges, OnDestroy {
  private readonly lookup = inject(CoreLookupService);

  @Input() itemId: string | null = null;
  @Input() label = 'Batch';
  @Input() required = false;
  @Input() disabled = false;
  @Input() instanceId = 'default';
  @Input() initialBatch: BatchSelectedEvent | null = null;

  @Output() readonly batchSelected = new EventEmitter<BatchSelectedEvent>();
  @Output() readonly batchCleared = new EventEmitter<void>();

  protected readonly batches = signal<StockBatchSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly loadError = signal(false);

  protected readonly options = computed(() =>
    this.batches().map(b => ({
      id: b.uid,
      label: b.expiryAt
        ? `${b.batchNo} (exp: ${b.expiryAt.slice(0, 10)}) — qty: ${b.qtyOnHand}`
        : `${b.batchNo} — qty: ${b.qtyOnHand}`,
    }))
  );

  protected readonly isDisabled = computed(
    () => !this.itemId || this.loading() || this.disabled
  );

  protected readonly placeholderText = computed(() => {
    if (!this.itemId) return 'Select an item first…';
    if (this.loading()) return 'Loading batches…';
    return 'Select batch…';
  });

  protected selectedId: string | null = null;
  protected touched = false;

  get selectId(): string { return `batch-picker-select-${this.instanceId}`; }
  get errorId(): string { return `batch-picker-error-${this.instanceId}`; }

  private sub?: Subscription;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['itemId']) {
      // Item changed — clear selection and reload.
      this.selectedId = null;
      this.batches.set([]);
      this.loadError.set(false);
      if (this.itemId) {
        this.load(this.itemId);
      }
    }

    if (changes['initialBatch'] && this.initialBatch && !this.loading() && this.batches().length > 0) {
      this.applyInitial(this.initialBatch);
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  protected onSelect(uid: string | number | null): void {
    this.touched = true;
    if (!uid) {
      this.selectedId = null;
      this.batchCleared.emit();
      return;
    }
    const batch = this.batches().find(b => b.uid === uid);
    if (batch) {
      this.batchSelected.emit({ id: batch.id, uid: batch.uid, batchNo: batch.batchNo });
    }
  }

  private load(itemId: string): void {
    this.loading.set(true);
    this.sub?.unsubscribe();
    this.sub = this.lookup.listStockBatches(itemId).subscribe({
      next: page => {
        this.batches.set(page.content);
        this.loading.set(false);
        if (this.initialBatch) this.applyInitial(this.initialBatch);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  private applyInitial(b: BatchSelectedEvent): void {
    this.selectedId = b.uid;
  }
}
