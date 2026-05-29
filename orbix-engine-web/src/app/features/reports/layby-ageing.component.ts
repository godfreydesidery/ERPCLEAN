import {
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ReportExportMenuComponent } from './report-export-menu.component';
import { ReportExport } from './report-export.service';
import { ReportsService } from './reports.service';
import { LaybyAgeingReport, LaybyAgeingBucket, LaybyAgeingOrder } from './reports.models';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';

/** Permission required to see this report (mirrors backend @PreAuthorize). */
const PERM = 'ORDER.READ';
const PERM_MANAGE = 'ORDER.MANAGE';

type OrderType = 'ALL' | 'LAYBY' | 'PRE_ORDER';

const TYPE_CHIPS: { key: OrderType; label: string }[] = [
  { key: 'ALL',       label: 'All' },
  { key: 'LAYBY',     label: 'Layby' },
  { key: 'PRE_ORDER', label: 'Pre-order' },
];

@Component({
  selector: 'orbix-layby-ageing',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe, DecimalPipe, FormsModule, ReportExportMenuComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/reports" class="text-decoration-none text-secondary">Reports</a> &rsaquo; Layby ageing
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Layby ageing</h1>
        <p class="text-secondary mb-0 small">
          Outstanding layby and pre-order balances bucketed by age.
        </p>
      </div>
      @if (report() && hasData()) {
        <orbix-report-export-menu
          [exportBuilder]="buildExport"
          [disabled]="!hasData()">
        </orbix-report-export-menu>
      }
    </header>

    <!-- Permission denied -->
    @if (!hasPerm()) {
      <div class="card border-0 shadow-sm p-5 text-center" data-testid="report-permission-state">
        <i class="bi bi-lock fs-1 text-secondary mb-3" aria-hidden="true"></i>
        <p class="fw-semibold mb-1">Permission required</p>
        <p class="small text-secondary mb-0">
          You need the <code>ORDER.READ</code> or <code>ORDER.MANAGE</code> permission to view this report.
          Contact your administrator.
        </p>
      </div>
    }

    @if (hasPerm()) {
      <!-- Filters -->
      <form class="row g-2 mb-4 align-items-end" (ngSubmit)="fetch()" aria-label="Layby ageing filters">
        <!-- Type chips -->
        <div class="col-12 col-md-auto">
          <div class="d-flex align-items-center gap-2 flex-wrap" role="group" aria-label="Order type filter">
            <span class="small fw-semibold text-secondary">Type:</span>
            @for (chip of typeChips; track chip.key) {
              <button type="button" class="btn btn-sm"
                      [class.btn-primary]="typeFilter() === chip.key"
                      [class.btn-outline-secondary]="typeFilter() !== chip.key"
                      (click)="setTypeFilter(chip.key)">
                {{ chip.label }}
              </button>
            }
          </div>
        </div>

        <!-- As-of date -->
        <div class="col-6 col-md-2">
          <label for="la-asof" class="form-label small fw-semibold mb-1">As of</label>
          <input id="la-asof" type="date" class="form-control form-control-sm"
                 [(ngModel)]="asOfDate" name="asOfDate"
                 aria-describedby="la-asof-hint">
          <div id="la-asof-hint" class="form-text">Default: now</div>
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
        <div aria-live="polite" aria-busy="true" class="visually-hidden">Loading layby ageing report…</div>
        <div class="card border-0 shadow-sm p-4 mb-3">
          <div class="placeholder-glow">
            @for (_ of [0,1,2,3,4,5]; track $index) {
              <span class="placeholder col-12 d-block mb-2" style="height:28px; border-radius:4px;"></span>
            }
          </div>
        </div>
      }

      <!-- Empty state -->
      @if (!loading() && !error() && fetched() && !hasData()) {
        <div class="card border-0 shadow-sm p-5 text-center" data-testid="report-empty-state">
          <div class="empty-icon mx-auto mb-3">
            <i class="bi bi-bag-check" aria-hidden="true"></i>
          </div>
          <p class="fw-semibold mb-1">No open orders</p>
          <p class="small text-secondary mb-0">
            No layby or pre-order balances match the selected filters.
          </p>
        </div>
      }

      <!-- Populated -->
      @if (!loading() && report() && hasData()) {
        <!-- KPI strip -->
        <div class="row g-3 mb-4">
          <div class="col-6 col-md-3">
            <div class="card border-0 shadow-sm p-3 text-center">
              <div class="small text-secondary text-uppercase fw-semibold mb-1" style="letter-spacing:0.06em;">Layby balance</div>
              <div class="fw-bold font-monospace">{{ formatMoney(report()!.balanceByType['LAYBY'] ?? 0) }}</div>
              <div class="small text-secondary">{{ report()!.countByType['LAYBY'] ?? 0 }} orders</div>
            </div>
          </div>
          <div class="col-6 col-md-3">
            <div class="card border-0 shadow-sm p-3 text-center">
              <div class="small text-secondary text-uppercase fw-semibold mb-1" style="letter-spacing:0.06em;">Pre-order balance</div>
              <div class="fw-bold font-monospace">{{ formatMoney(report()!.balanceByType['PRE_ORDER'] ?? 0) }}</div>
              <div class="small text-secondary">{{ report()!.countByType['PRE_ORDER'] ?? 0 }} orders</div>
            </div>
          </div>
          <div class="col-12 col-md-6">
            <div class="card border-0 shadow-sm p-3">
              <div class="small text-secondary text-uppercase fw-semibold mb-1" style="letter-spacing:0.06em;">Report as of</div>
              <div class="fw-semibold">{{ report()!.asOf | date:'dd/MM/yyyy HH:mm' }}</div>
            </div>
          </div>
        </div>

        <!-- Bucket rollup table -->
        <div class="card border-0 shadow-sm overflow-hidden mb-4">
          <div class="card-header bg-white py-2 px-3">
            <h2 class="h6 fw-bold mb-0 text-dark">Age bucket summary</h2>
          </div>
          <div class="table-responsive">
            <table class="table table-sm align-middle mb-0 bucket-table" aria-label="Layby ageing bucket summary">
              <caption class="visually-hidden">Orders grouped by age bucket and type</caption>
              <thead>
                <tr>
                  <th scope="col">Type</th>
                  <th scope="col">Bucket</th>
                  <th scope="col" class="text-end">Orders</th>
                  <th scope="col" class="text-end">Total</th>
                  <th scope="col" class="text-end">Paid</th>
                  <th scope="col" class="text-end">Balance due</th>
                </tr>
              </thead>
              <tbody>
                @for (bucket of visibleBuckets(); track bucket.type + bucket.bucketLabel) {
                  <tr>
                    <td>
                      <span class="badge"
                            [class.text-bg-primary]="bucket.type === 'LAYBY'"
                            [class.text-bg-violet]="bucket.type === 'PRE_ORDER'"
                            style="font-size:0.7rem;">
                        {{ bucket.type }}
                      </span>
                    </td>
                    <td class="small">{{ bucket.bucketLabel }}</td>
                    <td class="text-end">{{ bucket.orderCount }}</td>
                    <td class="text-end font-monospace small">{{ formatMoney(bucket.totalAmount) }}</td>
                    <td class="text-end font-monospace small text-success">{{ formatMoney(bucket.paidAmount) }}</td>
                    <td class="text-end font-monospace small fw-semibold"
                        [class.text-danger]="bucket.balanceDue > 0">
                      {{ formatMoney(bucket.balanceDue) }}
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>

        <!-- Per-order drill-down (oldest first) -->
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white py-2 px-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Order drill-down</h2>
            <span class="badge text-bg-secondary">{{ visibleOrders().length }} orders</span>
          </div>
          <div class="table-responsive">
            <table class="table table-hover align-middle mb-0 order-table" aria-label="Layby ageing order list">
              <caption class="visually-hidden">Individual orders sorted oldest-first</caption>
              <thead>
                <tr>
                  <th scope="col">Number</th>
                  <th scope="col">Type</th>
                  <th scope="col">Status</th>
                  <th scope="col" class="text-end">Age</th>
                  <th scope="col" class="text-end">Expires</th>
                  <th scope="col" class="text-end">Total</th>
                  <th scope="col" class="text-end">Paid</th>
                  <th scope="col" class="text-end">Balance due</th>
                </tr>
              </thead>
              <tbody>
                @for (order of visibleOrders(); track order.id) {
                  <tr>
                    <td class="small">
                      <!--
                        Deep-link to /orders/uid/{id}. The /orders feature is deferred
                        (Phase 7 web — not yet implemented). Rendered as a disabled-looking
                        span until that route lands; easy to swap to <a [routerLink]> then.
                        TODO: wire up when Phase 7 ships: [routerLink]="['/orders/uid', order.id]"
                      -->
                      <span class="text-secondary font-monospace">{{ order.number }}</span>
                    </td>
                    <td>
                      <span class="badge"
                            [class.text-bg-primary]="order.type === 'LAYBY'"
                            [class.text-bg-warning]="order.type === 'PRE_ORDER'"
                            style="font-size:0.7rem;">
                        {{ order.type }}
                      </span>
                    </td>
                    <td class="small text-secondary">{{ order.status }}</td>
                    <td class="text-end">
                      <span class="badge"
                            [class.text-bg-secondary]="order.ageDays <= 7"
                            [class.text-bg-info]="order.ageDays > 7 && order.ageDays <= 30"
                            [class.text-bg-warning]="order.ageDays > 30 && order.ageDays <= 90"
                            [class.text-bg-danger]="order.ageDays > 90">
                        {{ order.ageDays }}d
                      </span>
                    </td>
                    <td class="text-end small">
                      @if (order.daysUntilExpiry == null) {
                        <span class="text-secondary">—</span>
                      } @else if (order.daysUntilExpiry < 0) {
                        <span class="badge text-bg-danger">Expired {{ -order.daysUntilExpiry }}d ago</span>
                      } @else if (order.daysUntilExpiry <= 7) {
                        <span class="badge text-bg-warning">{{ order.daysUntilExpiry }}d left</span>
                      } @else {
                        <span class="text-secondary">{{ order.daysUntilExpiry }}d</span>
                      }
                    </td>
                    <td class="text-end font-monospace small">{{ formatMoney(order.totalAmount) }}</td>
                    <td class="text-end font-monospace small text-success">{{ formatMoney(order.paidAmount) }}</td>
                    <td class="text-end font-monospace small fw-semibold"
                        [class.text-danger]="order.balanceDue > 0">
                      {{ formatMoney(order.balanceDue) }}
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
      background: #ffedd5; color: #c2410c; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
    .bucket-table thead th,
    .order-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.05em; color: #6b7280; background: #f9fafb;
      border-bottom: 1px solid #e5e7eb; padding: 0.6rem 0.75rem;
    }
    .bucket-table tbody td,
    .order-table tbody td { padding: 0.55rem 0.75rem; border-bottom: 1px solid #f3f4f6; }
    .bucket-table tbody tr:last-child td,
    .order-table tbody tr:last-child td { border-bottom: none; }
    .text-bg-violet { background-color: #ede9fe !important; color: #6d28d9 !important; }
  `],
})
export class LaybyAgeingComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(ReportsService);
  private readonly auth = inject(AuthService);
  private readonly branch = inject(BranchService);

  protected readonly hasPerm = computed(() =>
    this.auth.hasPermission(PERM) || this.auth.hasPermission(PERM_MANAGE)
  );

  protected readonly typeFilter = signal<OrderType>('ALL');
  protected asOfDate = '';
  protected branchId: string | null = null;

  protected readonly report = signal<LaybyAgeingReport | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly fetched = signal(false);

  protected readonly typeChips = TYPE_CHIPS;

  protected readonly hasData = computed(() => {
    const r = this.report();
    return r != null && (r.orders.length > 0 || r.buckets.length > 0);
  });

  protected readonly visibleBuckets = computed((): LaybyAgeingBucket[] => {
    const r = this.report();
    if (!r) return [];
    const tf = this.typeFilter();
    if (tf === 'ALL') return r.buckets;
    return r.buckets.filter(b => b.type === tf);
  });

  protected readonly visibleOrders = computed((): LaybyAgeingOrder[] => {
    const r = this.report();
    if (!r) return [];
    const tf = this.typeFilter();
    if (tf === 'ALL') return r.orders;
    return r.orders.filter(o => o.type === tf);
  });

  ngOnInit(): void {
    this.branchId = this.branch.activeBranchId();

    this.route.queryParamMap.subscribe(params => {
      const branchIdParam = params.get('branchId');
      const typeParam = params.get('type');
      const asOfParam = params.get('asOf');
      if (branchIdParam) this.branchId = branchIdParam;
      if (typeParam && (typeParam === 'LAYBY' || typeParam === 'PRE_ORDER')) {
        this.typeFilter.set(typeParam);
      }
      if (asOfParam) this.asOfDate = asOfParam.slice(0, 10);

      if (this.hasPerm()) {
        this.fetch();
      }
    });
  }

  protected setTypeFilter(t: OrderType): void {
    this.typeFilter.set(t);
    // Client-side filter only — no re-fetch needed; visibleBuckets/visibleOrders recompute.
  }

  protected fetch(): void {
    if (!this.hasPerm()) return;
    this.loading.set(true);
    this.error.set(null);

    const typeArg = this.typeFilter() === 'ALL'
      ? null
      : (this.typeFilter() as 'LAYBY' | 'PRE_ORDER');

    // Convert date input to ISO-8601 instant (start of day UTC) if set.
    let asOf: string | null = null;
    if (this.asOfDate) {
      asOf = new Date(this.asOfDate + 'T00:00:00Z').toISOString();
    }

    this.service.laybyAgeing(this.branchId, typeArg, asOf).subscribe({
      next: r => {
        this.report.set(r);
        this.loading.set(false);
        this.fetched.set(true);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.fetched.set(true);
        if (err instanceof HttpErrorResponse && err.status === 403) {
          // Handled by hasPerm() in the template — but guard here too in case
          // the permission claim is stale after a role change without re-login.
          this.error.set('You do not have permission to view this report (ORDER.READ required).');
        } else {
          this.error.set(extractMessage(err, 'Failed to load layby ageing report.'));
        }
      },
    });
  }

  protected formatMoney(v: number): string {
    return `TZS ${Math.round(v).toLocaleString('en-US')}`;
  }

  readonly buildExport = (): ReportExport => {
    const orders = this.visibleOrders();
    const tf = this.typeFilter();
    return {
      title: 'Layby Ageing',
      subtitle: `Type: ${tf} · As of: ${this.report()?.asOf ?? 'now'}`,
      columns: [
        { key: 'number',     label: 'Order #',    format: 'text',     align: 'left'  },
        { key: 'type',       label: 'Type',        format: 'text',     align: 'left'  },
        { key: 'status',     label: 'Status',      format: 'text',     align: 'left'  },
        { key: 'ageDays',    label: 'Age (days)',  format: 'number',   align: 'right' },
        { key: 'expiry',     label: 'Expiry (d)',  format: 'text',     align: 'right' },
        { key: 'total',      label: 'Total',       format: 'currency', align: 'right' },
        { key: 'paid',       label: 'Paid',        format: 'currency', align: 'right' },
        { key: 'balance',    label: 'Balance due', format: 'currency', align: 'right' },
      ],
      rows: orders.map(o => ({
        number:   o.number,
        type:     o.type,
        status:   o.status,
        ageDays:  o.ageDays,
        expiry:   o.daysUntilExpiry != null ? String(o.daysUntilExpiry) : '—',
        total:    o.totalAmount,
        paid:     o.paidAmount,
        balance:  o.balanceDue,
      })),
    };
  };
}

function extractMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { message?: string } | null;
    return body?.message ?? fallback;
  }
  return fallback;
}
