import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { StockService } from './stock.service';
import { ItemBranchBalance } from './stock.models';

@Component({
  selector: 'orbix-stock-balances',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Stock</a> &rsaquo; Balances
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Stock balances</h1>
        <p class="text-secondary mb-0 small">
          @if (branchId() !== null) {
            Branch #{{ branchId() }} · {{ filtered().length }} item{{ filtered().length === 1 ? '' : 's' }} on hand.
          } @else { No active branch. }
        </p>
      </div>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i><span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="error.set(null)"></button>
      </div>
    }

    @if (branchId() === null) {
      <div class="card border-0 shadow-sm">
        <div class="card-body p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-building"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No active branch</h2>
          <p class="small text-secondary mb-0">Pick a branch in the top bar to see balances.</p>
        </div>
      </div>
    } @else {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-body p-3 d-flex flex-wrap align-items-center gap-3">
          <div class="search-box flex-grow-1">
            <i class="bi bi-search"></i>
            <input type="search" class="form-control" placeholder="Search by item ID"
                   [(ngModel)]="searchTerm" (ngModelChange)="searchSignal.set(searchTerm)">
          </div>
          <div class="form-check">
            <input class="form-check-input" type="checkbox" id="lowOnly"
                   data-testid="below-reorder-filter"
                   [(ngModel)]="lowOnly" (ngModelChange)="onBelowReorderToggle($event)">
            <label class="form-check-label small" for="lowOnly">Below reorder only</label>
          </div>
          <div class="form-check">
            <input class="form-check-input" type="checkbox" id="negativeOnly"
                   data-testid="negative-only-filter"
                   [(ngModel)]="negativeOnly" (ngModelChange)="onNegativeOnlyToggle($event)">
            <label class="form-check-label small" for="negativeOnly">Negative only</label>
          </div>
        </div>
      </div>

      <div class="card border-0 shadow-sm overflow-hidden">
        @if (filtered().length === 0) {
          <div class="p-5 text-center">
            <div class="empty-icon mx-auto mb-3"><i class="bi bi-boxes"></i></div>
            <p class="small text-secondary mb-0">No balances match your filter.</p>
          </div>
        } @else {
          <div class="table-responsive">
            <table class="table table-hover align-middle mb-0 simple-table">
              <thead>
                <tr>
                  <th>Item</th>
                  <th class="text-end">On hand</th>
                  <th class="text-end">Reserved</th>
                  <th class="text-end">In transit</th>
                  <th class="text-end">Avg cost</th>
                  <th>Last moved</th>
                  <th class="text-end actions-col"></th>
                </tr>
              </thead>
              <tbody>
                @for (b of filtered(); track b.itemId) {
                  <tr [class.row-warn]="isLow(b)">
                    <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ b.itemId }}</span></td>
                    <td class="text-end fw-semibold"
                        [class.text-warning]="isLow(b)"
                        [class.text-dark]="!isLow(b)">
                      {{ b.qtyOnHand | number:'1.0-4' }}
                      @if (isLow(b)) {
                        <i class="bi bi-exclamation-circle-fill ms-1" title="Below reorder min"></i>
                      }
                    </td>
                    <td class="text-end small text-secondary">{{ b.qtyReserved | number:'1.0-4' }}</td>
                    <td class="text-end small text-secondary">{{ b.qtyInTransit | number:'1.0-4' }}</td>
                    <td class="text-end">{{ b.avgCost | number:'1.2-2' }}</td>
                    <td class="small text-secondary">{{ b.lastMovedAt ? (b.lastMovedAt | date:'short') : '—' }}</td>
                    <td class="text-end actions-col">
                      <a class="btn btn-sm btn-outline-secondary d-inline-flex align-items-center gap-1"
                         [routerLink]="['/stock/card', b.itemId]" title="Open stock card">
                        <i class="bi bi-graph-up"></i><span class="d-none d-md-inline">Card</span>
                      </a>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    :host { display: block; }

    .search-box { position: relative; min-width: 220px; }
    .search-box i { position: absolute; left: 0.875rem; top: 50%; transform: translateY(-50%); color: #9ca3af; pointer-events: none; }
    .search-box .form-control { padding-left: 2.4rem; border: 1px solid #e5e7eb; }
    .search-box .form-control:focus { border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12); }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.75rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }
    .simple-table tbody tr.row-warn { background: #fffbeb; }
    .simple-table tbody tr.row-warn:hover { background: #fef3c7; }
    .simple-table .actions-col { width: 1%; white-space: nowrap; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class BalancesComponent implements OnInit {
  private readonly stock = inject(StockService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly balances = signal<ItemBranchBalance[]>([]);
  protected readonly error = signal<string | null>(null);

  protected readonly searchSignal = signal('');
  protected searchTerm = '';
  protected readonly lowOnlySignal = signal(false);
  protected lowOnly = false;
  /** Slice F — "negative only" filter, driven from {@code ?negativeOnly=true}. */
  protected readonly negativeOnlySignal = signal(false);
  protected negativeOnly = false;

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected readonly filtered = computed(() => {
    const q = this.searchSignal().trim().toLowerCase();
    const onlyLow = this.lowOnlySignal();
    const onlyNeg = this.negativeOnlySignal();
    return this.balances().filter(b => {
      if (onlyLow && !this.isLow(b)) return false;
      if (onlyNeg && b.qtyOnHand >= 0) return false;
      if (!q) return true;
      return String(b.itemId).includes(q);
    });
  });

  isLow(b: ItemBranchBalance): boolean {
    return b.reorderMin !== null && b.qtyOnHand <= b.reorderMin;
  }

  ngOnInit(): void {
    // Slice F drill-through — read filters from the URL on every navigation so
    // a deep-link like /stock/balances?negativeOnly=true pre-applies the
    // filter without the user re-finding the checkbox.
    this.route.queryParamMap.subscribe(params => {
      const below = params.get('belowReorderOnly') === 'true';
      const negative = params.get('negativeOnly') === 'true';
      this.lowOnly = below;
      this.lowOnlySignal.set(below);
      this.negativeOnly = negative;
      this.negativeOnlySignal.set(negative);
      this.fetch();
    });
  }

  protected onBelowReorderToggle(checked: boolean): void {
    this.lowOnlySignal.set(checked);
    this.syncUrl({ belowReorderOnly: checked ? 'true' : null });
  }

  protected onNegativeOnlyToggle(checked: boolean): void {
    this.negativeOnlySignal.set(checked);
    this.syncUrl({ negativeOnly: checked ? 'true' : null });
  }

  /** Merge filter changes into the URL so deep-links stay shareable. */
  private syncUrl(patch: Record<string, string | null>): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: patch,
      queryParamsHandling: 'merge',
    });
  }

  private fetch(): void {
    const branchId = this.branchId();
    if (branchId === null) return;
    this.stock.listBalances(branchId, {
      negativeOnly: this.negativeOnlySignal(),
      belowReorderOnly: this.lowOnlySignal(),
    }).subscribe({
      next: list => this.balances.set(list),
      error: err => this.showError(err)
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
