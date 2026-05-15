import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { StockService } from './stock.service';
import {
  STOCK_BATCH_STATUSES,
  StockBatch,
  StockBatchStatus
} from './stock.models';

type Mode = 'all' | 'expiring';

@Component({
  selector: 'orbix-stock-batches',
  standalone: true,
  imports: [DatePipe, FormsModule],
  template: `
    <h2 class="h3 mb-4">Stock batches</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }
    @if (info()) {
      <div class="alert alert-info py-2">{{ info() }}</div>
    }

    <div class="d-flex flex-wrap gap-3 align-items-end mb-3">
      <div>
        <label class="form-label small mb-1">View</label>
        <select class="form-select form-select-sm" [(ngModel)]="mode" (ngModelChange)="refresh()">
          <option value="all">All batches</option>
          <option value="expiring">Expiring soon</option>
        </select>
      </div>

      @if (mode() === 'all') {
        <div>
          <label class="form-label small mb-1">Status</label>
          <select class="form-select form-select-sm" [(ngModel)]="statusFilter" (ngModelChange)="refresh()">
            <option [ngValue]="null">All</option>
            @for (s of statuses; track s) {
              <option [ngValue]="s">{{ s }}</option>
            }
          </select>
        </div>
        <div>
          <label class="form-label small mb-1">Item id</label>
          <input class="form-control form-control-sm" type="number" [(ngModel)]="itemIdFilter"
                 (change)="refresh()" placeholder="any" />
        </div>
      } @else {
        <div>
          <label class="form-label small mb-1">Within (days)</label>
          <input class="form-control form-control-sm" type="number" min="0"
                 [(ngModel)]="daysAhead" (change)="refresh()" />
        </div>
      }

      <div class="form-check mt-3">
        <input class="form-check-input" type="checkbox" id="branchFilter"
               [(ngModel)]="scopeToBranch" (ngModelChange)="refresh()" />
        <label class="form-check-label small" for="branchFilter">
          Scope to active branch ({{ branchId() ?? '—' }})
        </label>
      </div>
    </div>

    <table class="table table-sm align-middle">
      <thead>
        <tr>
          <th>Batch no</th>
          <th>Item</th>
          <th>Branch</th>
          <th>Mfg</th>
          <th>Expiry</th>
          <th class="text-end">On hand</th>
          <th class="text-end">Received</th>
          <th class="text-end">Cost</th>
          <th>Status</th>
          <th>Source</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        @for (b of batches(); track b.id) {
          <tr [class.table-warning]="isExpiringSoon(b)">
            <td>{{ b.batchNo }}</td>
            <td>{{ b.itemId }}</td>
            <td>{{ b.branchId }}</td>
            <td>{{ b.manufacturedAt ?? '—' }}</td>
            <td>{{ b.expiryAt ?? '—' }}</td>
            <td class="text-end">{{ b.qtyOnHand }}</td>
            <td class="text-end">{{ b.qtyReceived }}</td>
            <td class="text-end">{{ b.cost }}</td>
            <td>
              <span class="badge"
                    [class.text-bg-success]="b.status === 'ACTIVE'"
                    [class.text-bg-secondary]="b.status === 'EXHAUSTED'"
                    [class.text-bg-warning]="b.status === 'EXPIRED'"
                    [class.text-bg-danger]="b.status === 'RECALLED'">{{ b.status }}</span>
            </td>
            <td>{{ b.sourceDocType }}#{{ b.sourceDocId }}</td>
            <td class="text-end">
              @if (b.status === 'ACTIVE') {
                <button class="btn btn-sm btn-outline-danger" (click)="onRecall(b)">Recall</button>
              }
            </td>
          </tr>
        } @empty {
          <tr><td colspan="11" class="text-muted">No batches.</td></tr>
        }
      </tbody>
    </table>
  `
})
export class BatchesComponent implements OnInit {
  private readonly stock = inject(StockService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly statuses = STOCK_BATCH_STATUSES;

  readonly batches = signal<StockBatch[]>([]);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  mode = signal<Mode>('expiring');
  statusFilter = signal<StockBatchStatus | null>(null);
  itemIdFilter = signal<number | null>(null);
  daysAhead = signal<number>(30);
  scopeToBranch = signal<boolean>(true);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.error.set(null);
    this.info.set(null);
    const branchId = this.scopeToBranch() ? this.branchId() : null;
    if (this.mode() === 'expiring') {
      this.stock.listExpiringSoon(branchId ?? null, this.daysAhead()).subscribe({
        next: list => this.batches.set(list),
        error: err => this.showError(err)
      });
    } else {
      this.stock.listBatches({
        branchId: branchId ?? undefined,
        itemId: this.itemIdFilter() ?? undefined,
        status: this.statusFilter() ?? undefined
      }).subscribe({
        next: list => this.batches.set(list),
        error: err => this.showError(err)
      });
    }
  }

  onRecall(batch: StockBatch): void {
    const reason = window.prompt(`Recall batch ${batch.batchNo} — reason?`);
    if (!reason || !reason.trim()) return;
    this.stock.recallBatch(batch.id, { reason: reason.trim() }).subscribe({
      next: () => {
        this.info.set(`Batch ${batch.batchNo} recalled.`);
        this.refresh();
      },
      error: err => this.showError(err)
    });
  }

  isExpiringSoon(batch: StockBatch): boolean {
    if (batch.status !== 'ACTIVE' || !batch.expiryAt) return false;
    const expiry = new Date(batch.expiryAt).getTime();
    const cutoff = Date.now() + this.daysAhead() * 24 * 60 * 60 * 1000;
    return expiry <= cutoff;
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
