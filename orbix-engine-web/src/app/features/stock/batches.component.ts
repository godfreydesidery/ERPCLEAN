import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { PagerComponent } from '../../core/ui/pager.component';
import { ItemTypeaheadComponent, ItemSelectedEvent } from '../procurement/item-typeahead.component';
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
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, PagerComponent, ItemTypeaheadComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Stock</a> &rsaquo; Batches
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Stock batches</h1>
        <p class="text-secondary mb-0 small">
          @if (mode() === 'all') { {{ total() }} batch{{ total() === 1 ? '' : 'es' }} on file. }
          @else { {{ batches().length }} batch{{ batches().length === 1 ? '' : 'es' }} expiring soon. }
        </p>
      </div>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i><span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="error.set(null)"></button>
      </div>
    }
    @if (info()) {
      <div class="alert alert-success d-flex align-items-center gap-2 py-2">
        <i class="bi bi-check-circle-fill"></i><span class="flex-grow-1">{{ info() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="info.set(null)"></button>
      </div>
    }

    <div class="card border-0 shadow-sm mb-3">
      <div class="card-body p-3 d-flex flex-wrap align-items-center gap-3">
        <div class="mode-pills d-flex gap-1">
          <button type="button" class="mode-pill"
                  [class.is-active]="mode() === 'expiring'"
                  (click)="mode.set('expiring'); refresh()">
            <i class="bi bi-hourglass-split me-1"></i> Expiring soon
          </button>
          <button type="button" class="mode-pill"
                  [class.is-active]="mode() === 'all'"
                  (click)="mode.set('all'); refresh()">
            <i class="bi bi-collection me-1"></i> All batches
          </button>
        </div>

        @if (mode() === 'all') {
          <div class="filter-cell">
            <label class="form-label small fw-semibold text-secondary mb-1">Status</label>
            <select class="form-select form-select-sm" [(ngModel)]="statusFilterModel"
                    (ngModelChange)="statusFilter.set($event); refresh()">
              <option [ngValue]="null">All</option>
              @for (s of statuses; track s) { <option [ngValue]="s">{{ s }}</option> }
            </select>
          </div>
          <div class="filter-cell" style="min-width:220px;">
            <orbix-item-typeahead
              instanceId="batch-filter-item"
              (itemSelected)="onItemFilterSelected($event)"
              (itemCleared)="onItemFilterCleared()">
            </orbix-item-typeahead>
          </div>
        } @else {
          <div class="filter-cell">
            <label class="form-label small fw-semibold text-secondary mb-1">Within (days)</label>
            <input class="form-control form-control-sm" type="number" min="0"
                   [(ngModel)]="daysAheadModel"
                   (change)="daysAhead.set(daysAheadModel); refresh()">
          </div>
        }

        <div class="form-check ms-auto align-self-end pb-1">
          <input class="form-check-input" type="checkbox" id="branchFilter"
                 [(ngModel)]="scopeToBranchModel"
                 (ngModelChange)="scopeToBranch.set($event); refresh()">
          <label class="form-check-label small" for="branchFilter">
            Active branch only <span class="text-muted">(#{{ branchId() ?? '—' }})</span>
          </label>
        </div>
      </div>
    </div>

    <div class="card border-0 shadow-sm overflow-hidden">
      @if (batches().length === 0) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-collection"></i></div>
          <p class="small text-secondary mb-0">No batches match these filters.</p>
        </div>
      } @else {
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 simple-table">
            <thead>
              <tr>
                <th>Batch no</th>
                <th>Item</th>
                <th>Branch</th>
                <th>Mfg</th>
                <th>Expiry</th>
                <th class="text-end">On hand</th>
                <th class="text-end">Recv'd</th>
                <th class="text-end">Cost</th>
                <th>Status</th>
                <th>Source</th>
                <th class="text-end actions-col"></th>
              </tr>
            </thead>
            <tbody>
              @for (b of batches(); track b.id) {
                <tr [class.row-warn]="isExpiringSoon(b)">
                  <td><span class="badge text-bg-light border text-secondary font-monospace">{{ b.batchNo }}</span></td>
                  <td class="font-monospace small">#{{ b.itemId }}</td>
                  <td class="small text-secondary">#{{ b.branchId }}</td>
                  <td class="small text-secondary">{{ b.manufacturedAt ? (b.manufacturedAt | date:'mediumDate') : '—' }}</td>
                  <td class="small"
                      [class.text-warning]="isExpiringSoon(b)"
                      [class.fw-semibold]="isExpiringSoon(b)"
                      [class.text-secondary]="!isExpiringSoon(b)">
                    {{ b.expiryAt ? (b.expiryAt | date:'mediumDate') : '—' }}
                  </td>
                  <td class="text-end fw-semibold">{{ b.qtyOnHand | number:'1.0-4' }}</td>
                  <td class="text-end small text-secondary">{{ b.qtyReceived | number:'1.0-4' }}</td>
                  <td class="text-end">{{ b.cost | number:'1.2-2' }}</td>
                  <td>
                    <span class="status-badge status-badge--{{ b.status.toLowerCase() }}">
                      <span class="status-badge__dot"></span>{{ b.status }}
                    </span>
                  </td>
                  <td class="small text-secondary font-monospace">{{ b.sourceDocType }}#{{ b.sourceDocId }}</td>
                  <td class="text-end actions-col">
                    @if (b.status === 'ACTIVE') {
                      <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1"
                              (click)="onRecall(b)" title="Recall">
                        <i class="bi bi-shield-exclamation"></i>
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
      @if (mode() === 'all' && totalPages() >= 1) {
        <div class="card-footer bg-white border-top">
          <orbix-pager [page]="pageNo()" [totalPages]="totalPages()"
                       [totalElements]="total()" [pageSize]="pageSize"
                       (pageChange)="goTo($event)"/>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }

    .mode-pills { display: flex; }
    .mode-pill {
      padding: 0.4rem 0.85rem; font-size: 0.85rem; font-weight: 500;
      border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; color: #6b7280;
      transition: all 0.15s ease;
    }
    .mode-pill:hover { border-color: #cbd5e1; color: #1f2937; }
    .mode-pill.is-active { background: #0d2a5b; border-color: #0d2a5b; color: #fff; }

    .filter-cell { min-width: 140px; }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }
    .simple-table tbody tr.row-warn { background: #fffbeb; }
    .simple-table tbody tr.row-warn:hover { background: #fef3c7; }
    .simple-table .actions-col { width: 1%; white-space: nowrap; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.2rem 0.55rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 5px; height: 5px; border-radius: 50%; }
    .status-badge--active    { background: #d1fae5; color: #047857; }
    .status-badge--active .status-badge__dot    { background: #10b981; }
    .status-badge--exhausted { background: #f3f4f6; color: #4b5563; }
    .status-badge--exhausted .status-badge__dot { background: #9ca3af; }
    .status-badge--expired   { background: #fef3c7; color: #92400e; }
    .status-badge--expired .status-badge__dot   { background: #f59e0b; }
    .status-badge--recalled  { background: #fee2e2; color: #b91c1c; }
    .status-badge--recalled .status-badge__dot  { background: #f43f5e; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #fef3c7; color: #b45309; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class BatchesComponent implements OnInit {
  private readonly stock = inject(StockService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly statuses = STOCK_BATCH_STATUSES;

  protected readonly batches = signal<StockBatch[]>([]);
  protected readonly pageNo = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly pageSize = 20;
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);

  protected readonly mode = signal<Mode>('expiring');
  protected readonly statusFilter = signal<StockBatchStatus | null>(null);
  protected readonly itemIdFilter = signal<string | null>(null);
  protected readonly daysAhead = signal<number>(30);
  protected readonly scopeToBranch = signal<boolean>(true);

  // ngModel mirrors for the filter controls
  protected statusFilterModel: StockBatchStatus | null = null;
  protected daysAheadModel = 30;
  protected scopeToBranchModel = true;

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  ngOnInit(): void { this.refresh(); }

  onItemFilterSelected(evt: ItemSelectedEvent): void {
    this.itemIdFilter.set(evt.id);
    this.refresh();
  }

  onItemFilterCleared(): void {
    this.itemIdFilter.set(null);
    this.refresh();
  }

  /** A mode/filter change resets to the first page. */
  refresh(): void {
    this.pageNo.set(0);
    this.load();
  }

  goTo(p: number): void {
    if (p < 0 || p >= this.totalPages()) return;
    this.pageNo.set(p);
    this.load();
  }

  private load(): void {
    this.error.set(null);
    this.info.set(null);
    const branchId = this.scopeToBranch() ? this.branchId() : null;
    if (this.mode() === 'expiring') {
      // Bounded report — naturally short, not paginated.
      this.totalPages.set(0);
      this.total.set(0);
      this.stock.listExpiringSoon(branchId ?? null, this.daysAhead()).subscribe({
        next: list => this.batches.set(list),
        error: err => this.showError(err)
      });
    } else {
      this.stock.listBatches({
        branchId: branchId ?? undefined,
        itemId: this.itemIdFilter() ?? undefined,
        status: this.statusFilter() ?? undefined
      }, this.pageNo(), this.pageSize).subscribe({
        next: page => {
          this.batches.set(page.content);
          this.total.set(page.totalElements);
          this.totalPages.set(page.totalPages);
          this.pageNo.set(page.page);
        },
        error: err => this.showError(err)
      });
    }
  }

  onRecall(batch: StockBatch): void {
    const reason = globalThis.prompt(`Recall batch ${batch.batchNo} — reason?`);
    if (!reason?.trim()) return;
    this.stock.recallBatch(batch.uid, { reason: reason.trim() }).subscribe({
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
