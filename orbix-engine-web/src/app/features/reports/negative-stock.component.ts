import {
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ReportExportMenuComponent } from './report-export-menu.component';
import { ReportExport } from './report-export.service';
import { ReportsService } from './reports.service';
import { ItemBranchBalance } from './reports.models';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { ApiResponse } from '../../core/api/api-response';
import { BranchPickerComponent, BranchSelectedEvent } from '../../core/ui/branch-picker.component';

const PERM = 'STOCK.COUNT';

@Component({
  selector: 'orbix-negative-stock',
  standalone: true,
  imports: [CommonModule, RouterLink, DecimalPipe, FormsModule, ReportExportMenuComponent, BranchPickerComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/reports" class="text-decoration-none text-secondary">Reports</a> &rsaquo; Negative stock
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Negative stock</h1>
        <p class="text-secondary mb-0 small">
          Items with on-hand quantity below zero — requires immediate review.
        </p>
      </div>
      @if (rows().length > 0) {
        <orbix-report-export-menu
          [exportBuilder]="buildExport"
          [disabled]="rows().length === 0">
        </orbix-report-export-menu>
      }
    </header>

    <!-- Permission denied -->
    @if (!hasPerm()) {
      <div class="card border-0 shadow-sm p-5 text-center" data-testid="report-permission-state">
        <i class="bi bi-lock fs-1 text-secondary mb-3" aria-hidden="true"></i>
        <p class="fw-semibold mb-1">Permission required</p>
        <p class="small text-secondary mb-0">You need the <code>STOCK.COUNT</code> permission to view this report.</p>
      </div>
    }

    @if (hasPerm()) {
      <!-- Filters -->
      <form class="row g-2 mb-4 align-items-end" (ngSubmit)="fetch()" aria-label="Negative stock filters">
        <div class="col-12 col-sm-6 col-md-4">
          <orbix-branch-picker
            instanceId="ns"
            label="Branch (optional — blank = all)"
            [required]="false"
            (branchSelected)="onBranchSelected($event)"
            (branchCleared)="onBranchCleared()">
          </orbix-branch-picker>
        </div>
        <div class="col-auto">
          <button type="submit" class="btn btn-primary btn-sm" [disabled]="loading()">
            @if (loading()) {
              <span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>
            }
            Run
          </button>
        </div>
      </form>

      <!-- Error -->
      @if (error()) {
        <div class="alert alert-danger d-flex align-items-center gap-2 py-2" role="alert">
          <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
          <span class="flex-grow-1">{{ error() }}</span>
          <button type="button" class="btn-close btn-sm" (click)="error.set(null)"
                  aria-label="Dismiss error"></button>
        </div>
      }

      <!-- Loading skeleton -->
      @if (loading()) {
        <div aria-live="polite" aria-busy="true" class="visually-hidden">Loading negative stock report…</div>
        <div class="card border-0 shadow-sm p-4">
          <div class="placeholder-glow">
            @for (_ of [0,1,2,3,4]; track $index) {
              <span class="placeholder col-12 d-block mb-2" style="height:32px; border-radius:4px;"></span>
            }
          </div>
        </div>
      }

      <!-- Empty state -->
      @if (!loading() && !error() && fetched() && rows().length === 0) {
        <div class="card border-0 shadow-sm p-5 text-center" data-testid="report-empty-state">
          <div class="empty-icon mx-auto mb-3">
            <i class="bi bi-check-circle text-success" aria-hidden="true"></i>
          </div>
          <p class="fw-semibold mb-1 text-success">No negative stock</p>
          <p class="small text-secondary mb-0">All item-branch balances are at or above zero.</p>
        </div>
      }

      <!-- Populated -->
      @if (!loading() && rows().length > 0) {
        <div class="d-flex align-items-center mb-2">
          <p class="small text-secondary mb-0">
            <span class="badge text-bg-danger me-1">{{ rows().length }}</span>
            item{{ rows().length === 1 ? '' : 's' }} in negative stock
          </p>
        </div>
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="table-responsive">
            <table class="table table-hover align-middle mb-0 neg-stock-table">
              <caption class="visually-hidden">Items with negative on-hand stock</caption>
              <thead>
                <tr>
                  <th scope="col">Item ID</th>
                  <th scope="col">Branch ID</th>
                  <th scope="col" class="text-end">On-hand qty</th>
                  <th scope="col">Last moved at</th>
                  <th scope="col">Stock card</th>
                </tr>
              </thead>
              <tbody>
                @for (row of rows(); track row.itemId + '-' + row.branchId) {
                  <tr class="clickable-row"
                      style="cursor:pointer"
                      (click)="drillThrough(row)"
                      (keydown.enter)="drillThrough(row)"
                      tabindex="0"
                      [attr.aria-label]="'View stock card for item ' + row.itemId">
                    <td class="font-monospace small">{{ row.itemId }}</td>
                    <td class="small">{{ row.branchId }}</td>
                    <td class="text-end fw-semibold"
                        [class.text-danger]="row.qtyOnHand < 0">
                      {{ row.qtyOnHand | number:'1.2-4' }}
                    </td>
                    <td class="small text-secondary">
                      {{ row.lastMovedAt ? (row.lastMovedAt | date:'dd/MM/yyyy HH:mm') : '—' }}
                    </td>
                    <td>
                      <a [routerLink]="['/reports/stock-card']"
                         [queryParams]="{ itemId: row.itemId, branchId: row.branchId }"
                         class="btn btn-sm btn-outline-primary"
                         (click)="$event.stopPropagation()"
                         [attr.aria-label]="'View stock card for item ' + row.itemId">
                        <i class="bi bi-clipboard2-pulse me-1" aria-hidden="true"></i>Card
                      </a>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
    }
  `,
  styles: [`
    :host { display: block; }
    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #d1fae5; color: #047857; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
    .neg-stock-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.05em; color: #6b7280; background: #f9fafb;
      border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .neg-stock-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; }
    .neg-stock-table tbody tr:last-child td { border-bottom: none; }
    .clickable-row:hover { background: #f0f4ff; }
    .clickable-row:focus { outline: 2px solid #1d4ed8; outline-offset: -2px; }
  `],
})
export class NegativeStockComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(ReportsService);
  private readonly auth = inject(AuthService);
  private readonly branchService = inject(BranchService);

  protected readonly hasPerm = computed(() => this.auth.hasPermission(PERM));

  protected branchInput = '';

  protected readonly rows = signal<ItemBranchBalance[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly fetched = signal(false);

  ngOnInit(): void {
    // Pre-fill branch from query params; picker handles active-branch display.
    this.route.queryParamMap.subscribe(params => {
      const b = params.get('branchId');
      if (b) this.branchInput = b;
      else {
        const active = this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId;
        if (active) this.branchInput = active;
      }
      if (this.hasPerm()) this.fetch();
    });
  }

  protected onBranchSelected(evt: BranchSelectedEvent): void {
    this.branchInput = evt.id;
  }

  protected onBranchCleared(): void {
    this.branchInput = '';
  }

  protected fetch(): void {
    const branchId = this.branchInput.trim() || null;

    this.loading.set(true);
    this.error.set(null);

    this.service.negativeStock(branchId).subscribe({
      next: data => {
        this.rows.set(data);
        this.loading.set(false);
        this.fetched.set(true);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.fetched.set(true);
        if (err instanceof HttpErrorResponse) {
          const env = err.error as ApiResponse<unknown> | null;
          this.error.set(env?.message ?? `Request failed (${err.status})`);
        } else {
          this.error.set('Unexpected error');
        }
      },
    });
  }

  protected drillThrough(row: ItemBranchBalance): void {
    void this.router.navigate(['/reports/stock-card'], {
      queryParams: { itemId: row.itemId, branchId: row.branchId },
    });
  }

  readonly buildExport = (): ReportExport => ({
    title: 'Negative Stock',
    subtitle: `Branch: ${this.branchInput || 'all'} · Generated: ${new Date().toISOString().slice(0, 10)}`,
    columns: [
      { key: 'itemId',      label: 'Item ID',      format: 'text',   align: 'left'  },
      { key: 'branchId',    label: 'Branch ID',    format: 'text',   align: 'left'  },
      { key: 'qtyOnHand',   label: 'On-hand qty',  format: 'number', align: 'right' },
      { key: 'lastMovedAt', label: 'Last moved at', format: 'date',  align: 'left'  },
    ],
    rows: this.rows().map(r => ({
      itemId:      r.itemId,
      branchId:    r.branchId,
      qtyOnHand:   r.qtyOnHand,
      lastMovedAt: r.lastMovedAt ?? '',
    })),
  });
}
