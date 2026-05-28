import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { DebtService } from './debt.service';
import { AgingBucket, DebtAging, DebtAgingTotals, DunningQueueRow } from './debt.models';

/**
 * Slice G — debt landing page (US-DEBT-001 / US-DEBT-003).
 *
 * Replaces the legacy placeholder hub with the operator-shaped dunning queue:
 *  - 5-bucket totals header (CURRENT / D_1_30 / D_31_60 / D_61_90 / D_90_PLUS)
 *  - customer rows sorted by oldest-overdue desc
 *  - optional bucket-filter (deep-linkable via {@code ?bucketFilter=D_31_60})
 *  - row click → /debt/customer/uid/{customerUid}
 *
 * Permission-gating mirrors the Slice F dashboard pattern: on 403 from
 * {@code GET /debt/aging} we hide the table and render an inert
 * "Permission required" state (the QA spec asserts this on the
 * {@code sales-clerk} persona).
 */
@Component({
  selector: 'orbix-debt',
  standalone: true,
  imports: [CommonModule, RouterLink, DecimalPipe],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Finance</p>
        <h1 class="h3 fw-bold mb-1 text-dark">Debt &middot; Dunning queue</h1>
        <p class="text-secondary mb-0 small">
          Customers with open or overdue receivables, ordered by oldest-overdue first.
        </p>
      </div>
      @if (!permissionDenied()) {
        <a routerLink="/reports/customer-statement" class="btn btn-outline-secondary d-inline-flex align-items-center gap-2">
          <i class="bi bi-file-earmark-person"></i><span class="d-none d-sm-inline">Statements</span>
        </a>
      }
    </header>

    @if (permissionDenied()) {
      <div data-testid="debt-permission-required" class="alert alert-warning d-flex align-items-start gap-2">
        <i class="bi bi-shield-lock mt-1"></i>
        <div class="flex-grow-1">
          <strong>Permission required.</strong>
          <span class="small d-block text-secondary">
            You don't have the <code>DEBT.READ</code> permission needed to view the dunning queue.
            Ask an administrator to grant it if you should be chasing receivables.
          </span>
        </div>
      </div>
    } @else {
      @if (error()) {
        <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
          <i class="bi bi-exclamation-triangle-fill"></i>
          <span class="flex-grow-1">{{ error() }}</span>
          <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
        </div>
      }

      <!-- Bucket filter chips -->
      <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
        <span class="small fw-semibold text-secondary">Filter:</span>
        @for (chip of bucketChips; track chip.key) {
          <button type="button" class="btn btn-sm"
                  [class.btn-primary]="bucketFilter() === chip.key"
                  [class.btn-outline-secondary]="bucketFilter() !== chip.key"
                  (click)="setBucketFilter(chip.key)">
            {{ chip.label }}
          </button>
        }
        @if (bucketFilter() !== null) {
          <button type="button" class="btn btn-sm btn-link text-decoration-none"
                  (click)="setBucketFilter(null)">
            <i class="bi bi-x-lg"></i> Clear
          </button>
        }
      </div>

      <!-- 5-bucket totals -->
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-body p-0">
          <table class="table table-sm mb-0 totals-table" aria-label="Aging-bucket totals">
            <thead>
              <tr>
                <th scope="col" data-testid="debt-bucket-current" class="bucket-cell">
                  <div class="bucket-label">Current</div>
                  <div class="bucket-amount">{{ formatMoney(totals()?.current) }}</div>
                </th>
                <th scope="col" data-testid="debt-bucket-30" class="bucket-cell">
                  <div class="bucket-label">1 – 30 days</div>
                  <div class="bucket-amount">{{ formatMoney(totals()?.d1_30) }}</div>
                </th>
                <th scope="col" data-testid="debt-bucket-60" class="bucket-cell">
                  <div class="bucket-label">31 – 60 days</div>
                  <div class="bucket-amount">{{ formatMoney(totals()?.d31_60) }}</div>
                </th>
                <th scope="col" data-testid="debt-bucket-90" class="bucket-cell">
                  <div class="bucket-label">61 – 90 days</div>
                  <div class="bucket-amount">{{ formatMoney(totals()?.d61_90) }}</div>
                </th>
                <th scope="col" data-testid="debt-bucket-over-90" class="bucket-cell">
                  <div class="bucket-label">90+ days</div>
                  <div class="bucket-amount">{{ formatMoney(totals()?.d90_plus) }}</div>
                </th>
              </tr>
            </thead>
          </table>
        </div>
      </div>

      <!-- Dunning queue -->
      <div class="card border-0 shadow-sm">
        <div class="card-body p-0">
          @if (loading()) {
            <div class="p-4 text-center text-secondary small">
              <span class="spinner-border spinner-border-sm me-2"></span> Loading dunning queue…
            </div>
          } @else if (rows().length === 0) {
            <div class="p-4 text-center text-secondary small">
              <i class="bi bi-emoji-smile me-1"></i>
              No overdue customers match this filter.
            </div>
          } @else {
            <div class="table-responsive">
              <table data-testid="debt-dunning-table" class="table table-hover align-middle mb-0">
                <caption class="visually-hidden">Customers with outstanding receivables</caption>
                <thead>
                  <tr>
                    <th scope="col">Customer</th>
                    <th scope="col" class="text-end">Outstanding</th>
                    <th scope="col" class="text-end">Credit limit</th>
                    <th scope="col" class="text-end">Oldest overdue</th>
                    <th scope="col" class="text-center">Worst bucket</th>
                    <th scope="col" class="text-end">Open invoices</th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of rows(); track row.customerUid) {
                    <tr data-testid="debt-customer-row"
                        (click)="openCustomer(row.customerUid)"
                        (keydown.enter)="openCustomer(row.customerUid)"
                        tabindex="0"
                        role="link"
                        [attr.aria-label]="'Drill into ' + row.customerName">
                      <td class="fw-semibold">{{ row.customerName }}</td>
                      <td class="text-end font-monospace">{{ row.totalOutstanding | number:'1.0-2' }}</td>
                      <td class="text-end font-monospace text-secondary">{{ row.creditLimit | number:'1.0-2' }}</td>
                      <td class="text-end">
                        @if (row.oldestDaysOverdue != null && row.oldestDaysOverdue > 0) {
                          <span class="badge text-bg-warning">{{ row.oldestDaysOverdue }} d</span>
                        } @else {
                          <span class="text-secondary small">—</span>
                        }
                      </td>
                      <td class="text-center">
                        <span class="badge"
                              [class.text-bg-success]="row.worstBucket === 'CURRENT'"
                              [class.text-bg-info]="row.worstBucket === 'D_1_30'"
                              [class.text-bg-warning]="row.worstBucket === 'D_31_60'"
                              [class.text-bg-danger]="row.worstBucket === 'D_61_90' || row.worstBucket === 'D_90_PLUS'">
                          {{ bucketLabel(row.worstBucket) }}
                        </span>
                      </td>
                      <td class="text-end">{{ row.overdueInvoiceCount }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }
    .totals-table { table-layout: fixed; }
    .bucket-cell {
      padding: 1rem 0.75rem;
      text-align: center;
      border-right: 1px solid #f1f5f9;
      vertical-align: middle;
    }
    .bucket-cell:last-child { border-right: none; }
    .bucket-label {
      font-size: 0.7rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: #64748b;
      margin-bottom: 0.25rem;
    }
    .bucket-amount {
      font-family: 'Source Code Pro', SFMono-Regular, Menlo, monospace;
      font-size: 1.05rem;
      font-weight: 700;
      color: #0f172a;
    }
    tbody tr[data-testid="debt-customer-row"] { cursor: pointer; }
    tbody tr[data-testid="debt-customer-row"]:focus-visible {
      outline: 2px solid #1d4ed8;
      outline-offset: -2px;
    }
  `]
})
export class DebtComponent implements OnInit {
  private readonly debt = inject(DebtService);
  private readonly auth = inject(AuthService);
  private readonly branch = inject(BranchService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly permissionDenied = signal(false);

  protected readonly totals = signal<DebtAgingTotals | null>(null);
  protected readonly rows = signal<DunningQueueRow[]>([]);
  protected readonly currencyCode = signal('TZS');

  protected readonly bucketFilter = signal<AgingBucket | null>(null);

  protected readonly bucketChips: { key: AgingBucket; label: string }[] = [
    { key: 'CURRENT',   label: 'Current' },
    { key: 'D_1_30',    label: '1 – 30' },
    { key: 'D_31_60',   label: '31 – 60' },
    { key: 'D_61_90',   label: '61 – 90' },
    { key: 'D_90_PLUS', label: '90+' },
  ];

  ngOnInit(): void {
    // Deep-link via ?bucketFilter=D_31_60.
    this.route.queryParamMap.subscribe(qp => {
      const bf = qp.get('bucketFilter');
      this.bucketFilter.set(this.isAgingBucket(bf) ? bf : null);
      this.load();
    });
  }

  protected setBucketFilter(b: AgingBucket | null): void {
    // Route nav so the URL stays the source of truth + the queryParamMap
    // subscription above re-fires the load.
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { bucketFilter: b },
      queryParamsHandling: 'merge',
    });
  }

  protected openCustomer(uid: string): void {
    this.router.navigate(['/debt/customer/uid', uid]);
  }

  protected bucketLabel(b: AgingBucket): string {
    switch (b) {
      case 'CURRENT':   return 'Current';
      case 'D_1_30':    return '1 – 30';
      case 'D_31_60':   return '31 – 60';
      case 'D_61_90':   return '61 – 90';
      case 'D_90_PLUS': return '90+';
    }
  }

  protected formatMoney(v: number | undefined | null): string {
    if (v == null) return '—';
    return `${this.currencyCode()} ${v.toLocaleString('en-US', { maximumFractionDigits: 0 })}`;
  }

  private load(): void {
    this.error.set(null);
    this.loading.set(true);
    const branchId = this.branch.activeBranchId();

    // Aging totals are the gate: if the caller lacks DEBT.READ this 403s,
    // and we flip into the "Permission required" inert state.
    this.debt.aging(branchId).subscribe({
      next: (a: DebtAging) => {
        this.totals.set(a.totals);
        if (a.currencyCode) this.currencyCode.set(a.currencyCode);
        this.permissionDenied.set(false);
        this.loadDunning(branchId);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.permissionDenied.set(true);
          this.rows.set([]);
          this.totals.set(null);
        } else {
          this.error.set(this.formatError(err, 'Failed to load aging report.'));
        }
        this.loading.set(false);
      },
    });
  }

  private loadDunning(branchId: string | null): void {
    this.debt.dunning(branchId, this.bucketFilter(), 0, 100).subscribe({
      next: page => {
        this.rows.set(page.content);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.permissionDenied.set(true);
          this.rows.set([]);
        } else {
          this.error.set(this.formatError(err, 'Failed to load dunning queue.'));
        }
        this.loading.set(false);
      },
    });
  }

  private isAgingBucket(v: string | null): v is AgingBucket {
    return v === 'CURRENT' || v === 'D_1_30' || v === 'D_31_60'
        || v === 'D_61_90' || v === 'D_90_PLUS';
  }

  private formatError(err: HttpErrorResponse, fallback: string): string {
    const body = err.error;
    if (body && typeof body === 'object' && 'message' in body && body.message) {
      return String((body as { message: unknown }).message);
    }
    return fallback;
  }
}
