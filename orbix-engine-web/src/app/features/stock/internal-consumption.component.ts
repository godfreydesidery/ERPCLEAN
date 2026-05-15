import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { StockService } from './stock.service';
import { CONSUMPTION_CATEGORIES, ConsumptionCategory } from './stock.models';

@Component({
  selector: 'orbix-stock-internal-consumption',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2 class="h3 mb-4">Internal consumption</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }
    @if (info()) {
      <div class="alert alert-success py-2">{{ info() }}</div>
    }

    @if (branchId() === null) {
      <div class="alert alert-warning py-2">No active branch selected.</div>
    } @else {
      <form (ngSubmit)="onSubmit()" class="row g-3" style="max-width: 720px;">
        <div class="col-md-6">
          <label class="form-label small mb-1">Item id</label>
          <input class="form-control" type="number" [(ngModel)]="itemId" name="itemId" required />
        </div>
        <div class="col-md-3">
          <label class="form-label small mb-1">Qty consumed</label>
          <input class="form-control" type="number" step="0.0001" min="0.0001"
                 [(ngModel)]="qty" name="qty" required />
        </div>
        <div class="col-md-3">
          <label class="form-label small mb-1">Category</label>
          <select class="form-select" [(ngModel)]="category" name="category" required>
            @for (c of categories; track c) {
              <option [ngValue]="c">{{ c }}</option>
            }
          </select>
        </div>
        <div class="col-md-4">
          <label class="form-label small mb-1">Section id</label>
          <input class="form-control" type="number" [(ngModel)]="sectionId" name="sectionId" required />
        </div>
        <div class="col-md-4">
          <label class="form-label small mb-1">Authoriser user id</label>
          <input class="form-control" type="number" [(ngModel)]="authorisedByUserId"
                 name="authoriser" required />
        </div>
        <div class="col-md-4">
          <label class="form-label small mb-1">Batch id (optional)</label>
          <input class="form-control" type="number" [(ngModel)]="batchId" name="batchId" />
        </div>
        <div class="col-12">
          <label class="form-label small mb-1">Reason</label>
          <input class="form-control" type="text" [(ngModel)]="reason" name="reason" required />
        </div>
        <div class="col-12">
          <button type="submit" class="btn btn-primary" [disabled]="busy()">
            {{ busy() ? 'Posting…' : 'Post draw' }}
          </button>
        </div>
      </form>
    }
  `
})
export class InternalConsumptionComponent {
  private readonly stock = inject(StockService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly categories = CONSUMPTION_CATEGORIES;

  readonly itemId = signal<number | null>(null);
  readonly qty = signal<number | null>(null);
  readonly category = signal<ConsumptionCategory>('CANTEEN');
  readonly sectionId = signal<number | null>(null);
  readonly authorisedByUserId = signal<number | null>(null);
  readonly batchId = signal<number | null>(null);
  readonly reason = signal<string>('');

  readonly busy = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  onSubmit(): void {
    const branchId = this.branchId();
    const itemId = this.itemId();
    const qty = this.qty();
    const sectionId = this.sectionId();
    const authoriser = this.authorisedByUserId();
    const reason = this.reason().trim();
    if (branchId === null || itemId === null || qty === null || qty <= 0
        || sectionId === null || authoriser === null || !reason) {
      this.error.set('Item, qty (>0), category, section, authoriser and reason are required.');
      return;
    }
    this.error.set(null);
    this.info.set(null);
    this.busy.set(true);
    this.stock.postInternalConsumption({
      itemId,
      branchId,
      qty,
      consumptionCategory: this.category(),
      sectionId,
      authorisedByUserId: authoriser,
      reason,
      batchId: this.batchId()
    }).subscribe({
      next: move => {
        this.busy.set(false);
        this.info.set(`Internal consumption posted as stock_move #${move.id}.`);
        this.qty.set(null);
        this.reason.set('');
      },
      error: err => {
        this.busy.set(false);
        this.showError(err);
      }
    });
  }

  private showError(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      const envelope = err.error as ApiResponse<unknown> | null;
      this.error.set(envelope?.message ?? `Request failed (${err.status})`);
    } else {
      this.error.set('Unexpected error');
    }
  }
}
