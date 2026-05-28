import {
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ReportExportMenuComponent } from './report-export-menu.component';
import { ReportExport } from './report-export.service';
import { ReportsService } from './reports.service';
import { StockMove } from './reports.models';
import { ItemTypeaheadComponent, ItemSelectedEvent } from '../procurement/item-typeahead.component';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { Page } from '../../core/api/page';
import { ApiResponse } from '../../core/api/api-response';

const PERM = 'STOCK.COUNT';

// Direction chip config
const MOVE_CHIP: Record<string, { label: string; css: string }> = {
  IN:  { label: 'IN',     css: 'badge text-bg-success' },
  OUT: { label: 'OUT',    css: 'badge text-bg-danger'  },
};

// Deep-link route map for source documents
const DOC_ROUTE: Record<string, string> = {
  SALES_INVOICE: '/sales/invoices/uid',
  GRN:           '/procurement/grns/uid',
  SUPPLIER_INVOICE: '/procurement/supplier-invoices/uid',
  VENDOR_RETURN: '/procurement/vendor-returns/uid',
};

@Component({
  selector: 'orbix-stock-card',
  standalone: true,
  imports: [
    CommonModule, RouterLink, DatePipe, DecimalPipe,
    FormsModule, ReportExportMenuComponent, ItemTypeaheadComponent,
  ],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/reports" class="text-decoration-none text-secondary">Reports</a> &rsaquo; Stock card
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Stock card</h1>
        <p class="text-secondary mb-0 small">Chronological stock movements for an item at a branch.</p>
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
      <!-- Client-side date filter banner -->
      <div class="alert alert-info d-flex align-items-start gap-2 py-2 mb-3" role="note">
        <i class="bi bi-info-circle-fill flex-shrink-0 mt-1" aria-hidden="true"></i>
        <span class="small">
          Showing all chronological moves (server-side date filter not yet supported on this endpoint).
          Use the date-range fields below to narrow the <strong>rendered</strong> rows.
        </span>
      </div>

      <!-- Filters -->
      <form class="row g-2 mb-4 align-items-end" (ngSubmit)="fetch()" aria-label="Stock card filters">
        <!-- Item typeahead -->
        <div class="col-12 col-md-5">
          <orbix-item-typeahead
            instanceId="sc"
            [initialItem]="initialItem()"
            (itemSelected)="onItemSelected($event)"
            (itemCleared)="onItemCleared()">
          </orbix-item-typeahead>
        </div>

        <!-- Branch -->
        <div class="col-12 col-sm-6 col-md-3">
          <label for="sc-branch" class="form-label small fw-semibold mb-1">Branch ID</label>
          <input id="sc-branch" type="text" class="form-control form-control-sm"
                 [(ngModel)]="branchInput" name="branchId"
                 placeholder="Default: current branch"
                 aria-describedby="sc-branch-hint">
          <div id="sc-branch-hint" class="form-text">Numeric branch ID</div>
        </div>

        <!-- Client-side date from -->
        <div class="col-6 col-md-2">
          <label for="sc-from" class="form-label small fw-semibold mb-1">From (local filter)</label>
          <input id="sc-from" type="date" class="form-control form-control-sm"
                 [(ngModel)]="filterFrom" name="filterFrom">
        </div>

        <!-- Client-side date to -->
        <div class="col-6 col-md-2">
          <label for="sc-to" class="form-label small fw-semibold mb-1">To (local filter)</label>
          <input id="sc-to" type="date" class="form-control form-control-sm"
                 [(ngModel)]="filterTo" name="filterTo">
        </div>

        <div class="col-auto">
          <button type="submit" class="btn btn-primary btn-sm"
                  [disabled]="loading() || !selectedItem()">
            @if (loading()) {
              <span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>
            }
            Run
          </button>
        </div>

        @if (!selectedItem() && touched()) {
          <div class="col-12">
            <div class="text-danger small">Select an item to run the stock card.</div>
          </div>
        }
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

      <!-- Loading -->
      @if (loading()) {
        <div aria-live="polite" aria-busy="true" class="visually-hidden">Loading stock card…</div>
        <div class="card border-0 shadow-sm p-4">
          <div class="placeholder-glow">
            @for (_ of [0,1,2,3,4,5]; track $index) {
              <span class="placeholder col-12 d-block mb-2" style="height:32px; border-radius:4px;"></span>
            }
          </div>
        </div>
      }

      <!-- Empty state -->
      @if (!loading() && !error() && fetched() && visibleRows().length === 0) {
        <div class="card border-0 shadow-sm p-5 text-center" data-testid="report-empty-state">
          <div class="empty-icon mx-auto mb-3">
            <i class="bi bi-clipboard-data" aria-hidden="true"></i>
          </div>
          <p class="small text-secondary mb-0">
            No stock moves found{{ filterFrom || filterTo ? ' for the selected date range' : '' }}.
          </p>
        </div>
      }

      <!-- Populated -->
      @if (!loading() && visibleRows().length > 0) {
        <!-- Pagination summary -->
        <div class="d-flex align-items-center justify-content-between mb-2">
          <p class="small text-secondary mb-0">
            Showing {{ visibleRows().length }} of {{ page()?.totalElements ?? rows().length }} moves
            @if (filterFrom || filterTo) { (date-filtered) }
          </p>
          @if ((page()?.totalPages ?? 1) > 1) {
            <div class="btn-group btn-group-sm">
              <button class="btn btn-outline-secondary"
                      [disabled]="currentPage() === 0"
                      (click)="goPage(currentPage() - 1)"
                      aria-label="Previous page">
                <i class="bi bi-chevron-left" aria-hidden="true"></i>
              </button>
              <span class="btn btn-outline-secondary disabled" aria-live="polite">
                {{ currentPage() + 1 }} / {{ page()?.totalPages }}
              </span>
              <button class="btn btn-outline-secondary"
                      [disabled]="currentPage() + 1 >= (page()?.totalPages ?? 1)"
                      (click)="goPage(currentPage() + 1)"
                      aria-label="Next page">
                <i class="bi bi-chevron-right" aria-hidden="true"></i>
              </button>
            </div>
          }
        </div>

        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="table-responsive">
            <table class="table table-hover align-middle mb-0 stock-card-table">
              <caption class="visually-hidden">Stock card for item {{ selectedItem()?.code }}</caption>
              <thead>
                <tr>
                  <th scope="col">Date / time</th>
                  <th scope="col">Type</th>
                  <th scope="col">Source doc</th>
                  <th scope="col" class="text-end">Qty</th>
                  <th scope="col">Notes</th>
                </tr>
              </thead>
              <tbody>
                @for (move of visibleRows(); track move.id) {
                  <tr>
                    <td class="text-nowrap small">{{ move.at | date:'dd/MM/yyyy HH:mm' }}</td>
                    <td>
                      <span [class]="chipCss(move.direction)" style="font-size:0.7rem;">
                        {{ move.direction }}
                      </span>
                      <span class="ms-1 small text-secondary">{{ move.moveType }}</span>
                    </td>
                    <td class="small">
                      @if (docRoute(move.refType) && move.refId) {
                        <a [routerLink]="[docRoute(move.refType), move.refId]"
                           class="text-decoration-none">
                          {{ move.refType }} #{{ move.refId }}
                        </a>
                      } @else if (move.refType) {
                        <span class="text-secondary">{{ move.refType }} #{{ move.refId }}</span>
                      } @else {
                        <span class="text-muted">—</span>
                      }
                    </td>
                    <td class="text-end fw-semibold"
                        [class.text-success]="move.direction === 'IN'"
                        [class.text-danger]="move.direction === 'OUT'">
                      {{ move.direction === 'IN' ? '+' : '−' }}{{ move.qty | number:'1.2-4' }}
                    </td>
                    <td class="small text-secondary">{{ move.notes ?? '—' }}</td>
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
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
    .stock-card-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.05em; color: #6b7280; background: #f9fafb;
      border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .stock-card-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; }
    .stock-card-table tbody tr:last-child td { border-bottom: none; }
  `],
})
export class StockCardComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(ReportsService);
  private readonly auth = inject(AuthService);
  private readonly branchService = inject(BranchService);

  protected readonly hasPerm = computed(() => this.auth.hasPermission(PERM));

  protected branchInput = '';
  protected filterFrom = '';
  protected filterTo = '';

  protected readonly selectedItem = signal<ItemSelectedEvent | null>(null);
  protected readonly initialItem = signal<ItemSelectedEvent | null>(null);
  protected readonly rows = signal<StockMove[]>([]);
  protected readonly page = signal<Page<StockMove> | null>(null);
  protected readonly currentPage = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly fetched = signal(false);
  protected readonly touched = signal(false);

  /** Client-side date filter applied over the already-fetched rows. */
  protected readonly visibleRows = computed(() => {
    const all = this.rows();
    const from = this.filterFrom;
    const to = this.filterTo;
    if (!from && !to) return all;
    return all.filter(r => {
      const d = r.at.slice(0, 10);
      if (from && d < from) return false;
      if (to && d > to) return false;
      return true;
    });
  });

  ngOnInit(): void {
    // Pre-fill from query params (e.g. drill-through from negative-stock page)
    this.route.queryParamMap.subscribe(params => {
      const itemId = params.get('itemId');
      const branchId = params.get('branchId');
      if (branchId) this.branchInput = branchId;

      if (itemId && branchId && this.hasPerm()) {
        // We only have the id — build a minimal ItemSelectedEvent for display.
        // The typeahead will show the id as placeholder until the user re-searches.
        this.initialItem.set({
          id: itemId, uid: '', code: '', name: `Item #${itemId}`,
          defaultUomUid: null, defaultUomCode: null, defaultVatGroupUid: null,
        });
        this.selectedItem.set(this.initialItem()!);
        this.fetch();
      }
    });
  }

  protected onItemSelected(evt: ItemSelectedEvent): void {
    this.selectedItem.set(evt);
  }

  protected onItemCleared(): void {
    this.selectedItem.set(null);
    this.rows.set([]);
    this.page.set(null);
    this.fetched.set(false);
    this.error.set(null);
  }

  protected fetch(): void {
    this.touched.set(true);
    const item = this.selectedItem();
    if (!item) return;

    const branchId = this.branchInput.trim() ||
      (this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null);

    if (!branchId) {
      this.error.set('Select or enter a branch ID.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.service.stockCard(item.id, branchId, this.currentPage(), 100).subscribe({
      next: pg => {
        this.page.set(pg);
        this.rows.set(pg.content);
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

  protected goPage(p: number): void {
    this.currentPage.set(p);
    this.fetch();
  }

  protected chipCss(direction: string): string {
    return MOVE_CHIP[direction]?.css ?? 'badge text-bg-secondary';
  }

  protected docRoute(refType: string | null): string | null {
    return refType ? (DOC_ROUTE[refType] ?? null) : null;
  }

  readonly buildExport = (): ReportExport => {
    const item = this.selectedItem();
    const allRows = this.visibleRows();
    return {
      title: 'Stock Card',
      subtitle: `Item: ${item?.code ?? ''} ${item?.name ?? ''} · Branch: ${this.branchInput || 'current'}`,
      columns: [
        { key: 'at',       label: 'Date/Time',   format: 'date',   align: 'left'  },
        { key: 'direction',label: 'Direction',    format: 'text',   align: 'left'  },
        { key: 'moveType', label: 'Move type',   format: 'text',   align: 'left'  },
        { key: 'refType',  label: 'Source kind',  format: 'text',   align: 'left'  },
        { key: 'refId',    label: 'Doc ID',       format: 'text',   align: 'left'  },
        { key: 'qty',      label: 'Qty',          format: 'number', align: 'right' },
        { key: 'notes',    label: 'Notes',        format: 'text',   align: 'left'  },
      ],
      rows: allRows.map(r => ({
        at:        r.at,
        direction: r.direction,
        moveType:  r.moveType,
        refType:   r.refType ?? '',
        refId:     r.refId ?? '',
        qty:       r.direction === 'OUT' ? -r.qty : r.qty,
        notes:     r.notes ?? '',
      })),
    };
  };
}
