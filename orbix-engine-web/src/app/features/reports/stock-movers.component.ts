import {
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ReportExportMenuComponent } from './report-export-menu.component';
import { ReportExport } from './report-export.service';
import { ReportsService } from './reports.service';
import { ItemMovementRow } from './reports.models';
import { BranchPickerComponent, BranchSelectedEvent } from '../../core/ui/branch-picker.component';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { ApiResponse } from '../../core/api/api-response';

const PERM = 'STOCK.COUNT';

type ActiveTab = 'fast' | 'slow';

const ALL_MOVE_TYPES = [
  'SALE', 'RETURN_OUT', 'PROD_CONSUME', 'GRN', 'ADJUST',
  'TRANSFER_IN', 'TRANSFER_OUT',
];

function last30DaysRange(): { from: string; to: string } {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - 30);
  return {
    from: from.toISOString().slice(0, 10),
    to:   to.toISOString().slice(0, 10),
  };
}

@Component({
  selector: 'orbix-stock-movers',
  standalone: true,
  imports: [CommonModule, RouterLink, DecimalPipe, FormsModule, ReportExportMenuComponent, BranchPickerComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/reports" class="text-decoration-none text-secondary">Reports</a> &rsaquo; Stock movers
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Stock movers</h1>
        <p class="text-secondary mb-0 small">Top fast and slow moving items over a date range.</p>
      </div>
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
      <!-- Filters (shared between both tabs) -->
      <form class="row g-2 mb-4 align-items-end" (ngSubmit)="fetchBoth()" aria-label="Stock movers filters">
        <!-- Branch -->
        <div class="col-12 col-sm-6 col-md-3">
          <orbix-branch-picker
            instanceId="sm"
            label="Branch (optional — blank = all)"
            [required]="false"
            (branchSelected)="onBranchSelected($event)"
            (branchCleared)="onBranchCleared()">
          </orbix-branch-picker>
        </div>

        <!-- Date from -->
        <div class="col-6 col-md-2">
          <label for="sm-from" class="form-label small fw-semibold mb-1">From</label>
          <input id="sm-from" type="date" class="form-control form-control-sm"
                 [(ngModel)]="dateFrom" name="from">
        </div>

        <!-- Date to -->
        <div class="col-6 col-md-2">
          <label for="sm-to" class="form-label small fw-semibold mb-1">To</label>
          <input id="sm-to" type="date" class="form-control form-control-sm"
                 [(ngModel)]="dateTo" name="to">
        </div>

        <!-- Limit -->
        <div class="col-6 col-md-2">
          <label for="sm-limit" class="form-label small fw-semibold mb-1">Limit</label>
          <select id="sm-limit" class="form-select form-select-sm"
                  [(ngModel)]="limit" name="limit">
            <option [ngValue]="10">10</option>
            <option [ngValue]="20">20</option>
            <option [ngValue]="50">50</option>
            <option [ngValue]="100">100</option>
          </select>
        </div>

        <div class="col-auto">
          <button type="submit" class="btn btn-primary btn-sm" [disabled]="fastLoading() || slowLoading()">
            @if (fastLoading() || slowLoading()) {
              <span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>
            }
            Run
          </button>
        </div>

        <!-- Move-type chip multi-select -->
        <div class="col-12">
          <fieldset>
            <legend class="form-label small fw-semibold mb-1">Move types</legend>
            <div class="d-flex flex-wrap gap-1" role="group" aria-label="Select move types">
              @for (mt of allMoveTypes; track mt) {
                <button type="button"
                        class="btn btn-sm"
                        [class.btn-primary]="selectedMoveTypes().includes(mt)"
                        [class.btn-outline-secondary]="!selectedMoveTypes().includes(mt)"
                        (click)="toggleMoveType(mt)"
                        [attr.aria-pressed]="selectedMoveTypes().includes(mt)">
                  {{ mt }}
                </button>
              }
            </div>
          </fieldset>
        </div>
      </form>

      <!-- Tabs -->
      <ul class="nav nav-tabs mb-3" role="tablist" aria-label="Fast and slow movers">
        <li class="nav-item" role="presentation">
          <button class="nav-link" [class.active]="activeTab() === 'fast'"
                  role="tab"
                  id="tab-fast"
                  [attr.aria-selected]="activeTab() === 'fast'"
                  aria-controls="panel-fast"
                  (click)="activeTab.set('fast')">
            <i class="bi bi-lightning-fill text-warning me-1" aria-hidden="true"></i>
            Fast movers
          </button>
        </li>
        <li class="nav-item" role="presentation">
          <button class="nav-link" [class.active]="activeTab() === 'slow'"
                  role="tab"
                  id="tab-slow"
                  [attr.aria-selected]="activeTab() === 'slow'"
                  aria-controls="panel-slow"
                  (click)="activeTab.set('slow')">
            <i class="bi bi-hourglass-split text-secondary me-1" aria-hidden="true"></i>
            Slow movers
          </button>
        </li>
      </ul>

      <!-- Fast movers panel -->
      <div id="panel-fast" role="tabpanel" aria-labelledby="tab-fast"
           [hidden]="activeTab() !== 'fast'">
        <div class="d-flex justify-content-end mb-2">
          @if (fastRows().length > 0) {
            <orbix-report-export-menu
              [exportBuilder]="buildFastExport"
              [disabled]="fastRows().length === 0">
            </orbix-report-export-menu>
          }
        </div>
        <ng-container *ngTemplateOutlet="moversTable;
          context: {
            rows: fastRows(), loading: fastLoading(), error: fastError(),
            fetched: fastFetched(), label: 'Fast movers', testIdPrefix: 'fast'
          }">
        </ng-container>
      </div>

      <!-- Slow movers panel -->
      <div id="panel-slow" role="tabpanel" aria-labelledby="tab-slow"
           [hidden]="activeTab() !== 'slow'">
        <div class="d-flex justify-content-end mb-2">
          @if (slowRows().length > 0) {
            <orbix-report-export-menu
              [exportBuilder]="buildSlowExport"
              [disabled]="slowRows().length === 0">
            </orbix-report-export-menu>
          }
        </div>
        <ng-container *ngTemplateOutlet="moversTable;
          context: {
            rows: slowRows(), loading: slowLoading(), error: slowError(),
            fetched: slowFetched(), label: 'Slow movers', testIdPrefix: 'slow'
          }">
        </ng-container>
      </div>
    }

    <!-- Shared table template -->
    <ng-template #moversTable let-rows="rows" let-loading="loading" let-error="error"
                 let-fetched="fetched" let-label="label" let-testIdPrefix="testIdPrefix">

      @if (error) {
        <div class="alert alert-danger d-flex align-items-center gap-2 py-2" role="alert">
          <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
          <span>{{ error }}</span>
        </div>
      }

      @if (loading) {
        <div aria-live="polite" aria-busy="true" class="visually-hidden">Loading {{ label }}…</div>
        <div class="card border-0 shadow-sm p-4">
          <div class="placeholder-glow">
            @for (_ of [0,1,2,3,4]; track $index) {
              <span class="placeholder col-12 d-block mb-2" style="height:32px; border-radius:4px;"></span>
            }
          </div>
        </div>
      }

      @if (!loading && !error && fetched && rows.length === 0) {
        <div class="card border-0 shadow-sm p-5 text-center" [attr.data-testid]="testIdPrefix + '-empty'">
          <div class="empty-icon mx-auto mb-3">
            <i class="bi bi-bar-chart-line" aria-hidden="true"></i>
          </div>
          <p class="small text-secondary mb-0">No data for the selected range and move types.</p>
        </div>
      }

      @if (!loading && rows.length > 0) {
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="table-responsive">
            <table class="table table-hover align-middle mb-0 movers-table">
              <caption class="visually-hidden">{{ label }} by total moved quantity</caption>
              <thead>
                <tr>
                  <th scope="col" class="text-center">#</th>
                  <th scope="col">Item ID</th>
                  <th scope="col">Item code</th>
                  <th scope="col">Item name</th>
                  <th scope="col" class="text-end">Moved qty</th>
                  <th scope="col" class="text-end">On-hand</th>
                  <th scope="col">Stock card</th>
                </tr>
              </thead>
              <tbody>
                @for (row of rows; track row.itemId; let i = $index) {
                  <tr>
                    <td class="text-center text-muted small">{{ i + 1 }}</td>
                    <td class="font-monospace small">{{ row.itemId }}</td>
                    <td class="small fw-semibold">{{ row.itemCode }}</td>
                    <td class="small">{{ row.itemName }}</td>
                    <td class="text-end fw-semibold">{{ row.movedQty | number:'1.2-4' }}</td>
                    <td class="text-end small"
                        [class.text-danger]="row.qtyOnHand < 0">
                      {{ row.qtyOnHand | number:'1.2-4' }}
                    </td>
                    <td>
                      <a [routerLink]="['/reports/stock-card']"
                         [queryParams]="{ itemId: row.itemId, branchId: resolvedBranchId() }"
                         class="btn btn-sm btn-outline-primary"
                         [attr.aria-label]="'View stock card for ' + row.itemCode">
                        <i class="bi bi-clipboard2-pulse" aria-hidden="true"></i>
                      </a>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
    </ng-template>
  `,
  styles: [`
    :host { display: block; }
    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
    .movers-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.05em; color: #6b7280; background: #f9fafb;
      border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .movers-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; }
    .movers-table tbody tr:last-child td { border-bottom: none; }
  `],
})
export class StockMoversComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(ReportsService);
  private readonly auth = inject(AuthService);
  private readonly branchService = inject(BranchService);

  protected readonly hasPerm = computed(() => this.auth.hasPermission(PERM));

  protected branchInput = '';
  protected dateFrom: string;
  protected dateTo: string;
  protected limit = 20;

  protected readonly allMoveTypes = ALL_MOVE_TYPES;
  protected readonly selectedMoveTypes = signal<string[]>(['SALE']);
  protected readonly activeTab = signal<ActiveTab>('fast');

  // Fast movers state
  protected readonly fastRows    = signal<ItemMovementRow[]>([]);
  protected readonly fastLoading = signal(false);
  protected readonly fastError   = signal<string | null>(null);
  protected readonly fastFetched = signal(false);

  // Slow movers state
  protected readonly slowRows    = signal<ItemMovementRow[]>([]);
  protected readonly slowLoading = signal(false);
  protected readonly slowError   = signal<string | null>(null);
  protected readonly slowFetched = signal(false);

  protected readonly resolvedBranchId = computed(() =>
    this.branchInput.trim() ||
    this.branchService.activeBranchId() ||
    this.auth.currentUser()?.defaultBranchId ||
    null
  );

  constructor() {
    const range = last30DaysRange();
    this.dateFrom = range.from;
    this.dateTo   = range.to;
  }

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const b = params.get('branchId');
      if (b) this.branchInput = b;
      else {
        const active = this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId;
        if (active) this.branchInput = active;
      }
      if (this.hasPerm()) this.fetchBoth();
    });
  }

  protected onBranchSelected(evt: BranchSelectedEvent): void {
    this.branchInput = evt.id;
  }

  protected onBranchCleared(): void {
    this.branchInput = '';
  }

  protected toggleMoveType(mt: string): void {
    const cur = this.selectedMoveTypes();
    if (cur.includes(mt)) {
      // Keep at least one selected
      if (cur.length > 1) this.selectedMoveTypes.set(cur.filter(t => t !== mt));
    } else {
      this.selectedMoveTypes.set([...cur, mt]);
    }
  }

  protected fetchBoth(): void {
    this.fetchFast();
    this.fetchSlow();
  }

  private fetchFast(): void {
    this.fastLoading.set(true);
    this.fastError.set(null);
    const { branchId, from, to, moveTypes } = this.queryArgs();

    this.service.fastMovers(branchId, from, to, moveTypes, this.limit).subscribe({
      next: data => {
        this.fastRows.set(data);
        this.fastLoading.set(false);
        this.fastFetched.set(true);
      },
      error: (err: unknown) => {
        this.fastLoading.set(false);
        this.fastFetched.set(true);
        this.fastError.set(extractError(err));
      },
    });
  }

  private fetchSlow(): void {
    this.slowLoading.set(true);
    this.slowError.set(null);
    const { branchId, from, to, moveTypes } = this.queryArgs();

    this.service.slowMovers(branchId, from, to, moveTypes, this.limit).subscribe({
      next: data => {
        this.slowRows.set(data);
        this.slowLoading.set(false);
        this.slowFetched.set(true);
      },
      error: (err: unknown) => {
        this.slowLoading.set(false);
        this.slowFetched.set(true);
        this.slowError.set(extractError(err));
      },
    });
  }

  private queryArgs() {
    return {
      branchId:  this.branchInput.trim() || null,
      from:      this.dateFrom || null,
      to:        this.dateTo || null,
      moveTypes: this.selectedMoveTypes().length > 0 ? this.selectedMoveTypes() : null,
    };
  }

  readonly buildFastExport = (): ReportExport => ({
    title: 'Fast Movers',
    subtitle: `Branch: ${this.branchInput || 'all'} · ${this.dateFrom} – ${this.dateTo}`,
    columns: buildMoversExportColumns(),
    rows: buildMoversExportRows(this.fastRows()),
  });

  readonly buildSlowExport = (): ReportExport => ({
    title: 'Slow Movers',
    subtitle: `Branch: ${this.branchInput || 'all'} · ${this.dateFrom} – ${this.dateTo}`,
    columns: buildMoversExportColumns(),
    rows: buildMoversExportRows(this.slowRows()),
  });
}

function buildMoversExportColumns() {
  return [
    { key: 'rank',      label: '#',           format: 'number' as const, align: 'right' as const },
    { key: 'itemId',    label: 'Item ID',      format: 'text'   as const, align: 'left'  as const },
    { key: 'itemCode',  label: 'Item code',    format: 'text'   as const, align: 'left'  as const },
    { key: 'itemName',  label: 'Item name',    format: 'text'   as const, align: 'left'  as const },
    { key: 'movedQty',  label: 'Moved qty',    format: 'number' as const, align: 'right' as const },
    { key: 'qtyOnHand', label: 'On-hand qty',  format: 'number' as const, align: 'right' as const },
  ];
}

function buildMoversExportRows(rows: ItemMovementRow[]) {
  return rows.map((r, i) => ({
    rank:      i + 1,
    itemId:    r.itemId,
    itemCode:  r.itemCode,
    itemName:  r.itemName,
    movedQty:  r.movedQty,
    qtyOnHand: r.qtyOnHand,
  }));
}

function extractError(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const env = err.error as ApiResponse<unknown> | null;
    return env?.message ?? `Request failed (${err.status})`;
  }
  return 'Unexpected error';
}
