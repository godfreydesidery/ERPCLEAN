import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { StockService } from './stock.service';

@Component({
  selector: 'orbix-stock-adjust',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2 class="h3 mb-4">Stock adjustment</h2>

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
          <label class="form-label small mb-1">Signed qty</label>
          <input class="form-control" type="number" step="0.0001" [(ngModel)]="qty" name="qty" required />
          <div class="form-text">Positive = found / inbound, negative = shrinkage / outbound.</div>
        </div>
        <div class="col-md-3">
          <label class="form-label small mb-1">Unit cost (inbound only)</label>
          <input class="form-control" type="number" step="0.0001" [(ngModel)]="unitCost" name="unitCost" />
        </div>
        <div class="col-12">
          <label class="form-label small mb-1">Reason</label>
          <input class="form-control" type="text" [(ngModel)]="reason" name="reason" required />
        </div>
        <div class="col-md-4">
          <label class="form-label small mb-1">Section id (optional)</label>
          <input class="form-control" type="number" [(ngModel)]="sectionId" name="sectionId" />
        </div>
        <div class="col-md-4">
          <label class="form-label small mb-1">Batch id (optional)</label>
          <input class="form-control" type="number" [(ngModel)]="batchId" name="batchId" />
        </div>
        <div class="col-md-4">
          <label class="form-label small mb-1">Authoriser user id</label>
          <input class="form-control" type="number" [(ngModel)]="authorisedByUserId" name="authoriser" />
          <div class="form-text">Required above threshold or for oversells.</div>
        </div>
        <div class="col-12">
          <div class="form-check">
            <input class="form-check-input" type="checkbox" id="oversell"
                   [(ngModel)]="allowOversell" name="oversell" />
            <label class="form-check-label small" for="oversell">
              Allow oversell (requires STOCK.OVERSELL)
            </label>
          </div>
        </div>
        <div class="col-12">
          <button type="submit" class="btn btn-primary" [disabled]="busy()">
            {{ busy() ? 'Posting…' : 'Post adjustment' }}
          </button>
        </div>
      </form>
    }
  `
})
export class AdjustComponent {
  private readonly stock = inject(StockService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly itemId = signal<number | null>(null);
  readonly qty = signal<number | null>(null);
  readonly unitCost = signal<number | null>(null);
  readonly reason = signal<string>('');
  readonly sectionId = signal<number | null>(null);
  readonly batchId = signal<number | null>(null);
  readonly authorisedByUserId = signal<number | null>(null);
  readonly allowOversell = signal<boolean>(false);

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
    const reason = this.reason().trim();
    if (branchId === null || itemId === null || qty === null || !reason) {
      this.error.set('Item, signed qty and reason are required.');
      return;
    }
    this.error.set(null);
    this.info.set(null);
    this.busy.set(true);
    this.stock.postAdjustment({
      itemId,
      branchId,
      qty,
      unitCost: this.unitCost(),
      reason,
      sectionId: this.sectionId(),
      batchId: this.batchId(),
      authorisedByUserId: this.authorisedByUserId(),
      allowOversell: this.allowOversell()
    }).subscribe({
      next: move => {
        this.busy.set(false);
        this.info.set(`Adjustment posted as stock_move #${move.id}.`);
        this.qty.set(null);
        this.reason.set('');
        this.allowOversell.set(false);
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
