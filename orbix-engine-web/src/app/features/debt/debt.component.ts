import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { DebtService } from './debt.service';
import {
  AgingBucket,
  DebtAging,
  DebtAgingTotals,
  DunningQueueRow,
  SupplierAging,
  SupplierAgingTotals,
  SupplierDunningQueueRow
} from './debt.models';

/**
 * Slice G / G.1 — debt landing page (US-DEBT-001 / US-DEBT-003 / US-DEBT-008).
 *
 * AR tab — customer dunning queue (Slice G, unchanged).
 * AP tab — supplier obligations queue (Slice G.1, new).
 *
 * Both tabs share the bucket-filter chips and the 5-bucket totals row.
 * The active tab is held in a signal; URL stays at /debt throughout
 * (tab state is UI-only, not persisted to the query string).
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
          {{ activeTab() === 'AR'
            ? 'Customers with open or overdue receivables, ordered by oldest-overdue first.'
            : 'Suppliers with outstanding payables, ordered by oldest-overdue first.' }}
        </p>
      </div>
      @if (!permissionDenied()) {
        <!--
          Deep-link: when a per-customer row is active, carry ?customerId= so
          /reports/customer-statement pre-loads that customer's statement.
          activeCustomerUid() is set by openCustomer() — null when no row is drilled.
        -->
        <a [routerLink]="'/reports/customer-statement'"
           [queryParams]="activeCustomerId() ? { customerId: activeCustomerId() } : {}"
           class="btn btn-outline-secondary d-inline-flex align-items-center gap-2">
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

      <!-- AR / AP tab control -->
      <ul class="nav nav-tabs mb-3" role="tablist" aria-label="Receivables / Payables view">
        <li class="nav-item" role="presentation">
          <button type="button"
                  class="nav-link"
                  [class.active]="activeTab() === 'AR'"
                  role="tab"
                  [attr.aria-selected]="activeTab() === 'AR'"
                  aria-controls="tab-panel-ar"
                  id="tab-ar"
                  (click)="switchTab('AR')">
            <i class="bi bi-arrow-down-circle me-1"></i>Receivables (AR)
          </button>
        </li>
        <li class="nav-item" role="presentation">
          <button type="button"
                  class="nav-link"
                  [class.active]="activeTab() === 'AP'"
                  role="tab"
                  [attr.aria-selected]="activeTab() === 'AP'"
                  aria-controls="tab-panel-ap"
                  id="tab-ap"
                  (click)="switchTab('AP')">
            <i class="bi bi-arrow-up-circle me-1"></i>Payables (AP)
          </button>
        </li>
      </ul>

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
          <table class="table table-sm mb-0 totals-table"
                 [attr.aria-label]="activeTab() === 'AR' ? 'AR aging-bucket totals' : 'AP aging-bucket totals'">
            <thead>
              <tr>
                <th scope="col" class="bucket-cell" data-testid="debt-bucket-current">
                  <div class="bucket-label">Current</div>
                  <div class="bucket-amount">{{ formatMoney(activeTotals()?.current) }}</div>
                </th>
                <th scope="col" class="bucket-cell" data-testid="debt-bucket-30">
                  <div class="bucket-label">1 – 30 days</div>
                  <div class="bucket-amount">{{ formatMoney(activeTotals()?.d1_30) }}</div>
                </th>
                <th scope="col" class="bucket-cell" data-testid="debt-bucket-60">
                  <div class="bucket-label">31 – 60 days</div>
                  <div class="bucket-amount">{{ formatMoney(activeTotals()?.d31_60) }}</div>
                </th>
                <th scope="col" class="bucket-cell" data-testid="debt-bucket-90">
                  <div class="bucket-label">61 – 90 days</div>
                  <div class="bucket-amount">{{ formatMoney(activeTotals()?.d61_90) }}</div>
                </th>
                <th scope="col" class="bucket-cell" data-testid="debt-bucket-over-90">
                  <div class="bucket-label">90+ days</div>
                  <div class="bucket-amount">{{ formatMoney(activeTotals()?.d90_plus) }}</div>
                </th>
              </tr>
            </thead>
          </table>
        </div>
      </div>

      <!-- AR dunning queue -->
      @if (activeTab() === 'AR') {
        <div id="tab-panel-ar" role="tabpanel" aria-labelledby="tab-ar" class="card border-0 shadow-sm">
          <div class="card-body p-0">
            @if (loading()) {
              <div class="p-4 text-center text-secondary small">
                <span class="spinner-border spinner-border-sm me-2"></span> Loading dunning queue…
              </div>
            } @else if (arRows().length === 0) {
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
                    @for (row of arRows(); track row.customerUid) {
                      <tr data-testid="debt-customer-row"
                          (click)="openCustomer(row.customerUid, row.customerId)"
                          (keydown.enter)="openCustomer(row.customerUid, row.customerId)"
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

      <!-- AP obligations queue -->
      @if (activeTab() === 'AP') {
        <div id="tab-panel-ap" role="tabpanel" aria-labelledby="tab-ap" class="card border-0 shadow-sm">
          <div class="card-body p-0">
            @if (loading()) {
              <div class="p-4 text-center text-secondary small">
                <span class="spinner-border spinner-border-sm me-2"></span> Loading obligations queue…
              </div>
            } @else if (apRows().length === 0) {
              <div class="p-4 text-center text-secondary small">
                <i class="bi bi-emoji-smile me-1"></i>
                No overdue suppliers match this filter.
              </div>
            } @else {
              <div class="table-responsive">
                <table data-testid="debt-ap-dunning-table" class="table table-hover align-middle mb-0">
                  <caption class="visually-hidden">Suppliers with outstanding payables</caption>
                  <thead>
                    <tr>
                      <th scope="col">Supplier</th>
                      <th scope="col" class="text-end">Payment terms</th>
                      <th scope="col" class="text-end">Outstanding</th>
                      <th scope="col" class="text-end">Oldest overdue</th>
                      <th scope="col" class="text-center">Worst bucket</th>
                      <th scope="col" class="text-end">Overdue invoices</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of apRows(); track row.supplierUid) {
                      <tr data-testid="debt-supplier-row"
                          (click)="openSupplier(row.supplierUid)"
                          (keydown.enter)="openSupplier(row.supplierUid)"
                          tabindex="0"
                          role="link"
                          [attr.aria-label]="'Drill into ' + row.supplierName">
                        <td class="fw-semibold">{{ row.supplierName }}</td>
                        <td class="text-end text-secondary">
                          {{ row.paymentTermsDays != null ? row.paymentTermsDays + ' days' : '—' }}
                        </td>
                        <td class="text-end font-monospace">{{ row.totalOutstanding | number:'1.0-2' }}</td>
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
    tbody tr[data-testid="debt-customer-row"],
    tbody tr[data-testid="debt-supplier-row"] { cursor: pointer; }
    tbody tr[data-testid="debt-customer-row"]:focus-visible,
    tbody tr[data-testid="debt-supplier-row"]:focus-visible {
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

  protected readonly activeTab = signal<'AR' | 'AP'>('AR');

  // AR state
  protected readonly arTotals = signal<DebtAgingTotals | null>(null);
  protected readonly arRows = signal<DunningQueueRow[]>([]);

  // AP state
  protected readonly apTotals = signal<SupplierAgingTotals | null>(null);
  protected readonly apRows = signal<SupplierDunningQueueRow[]>([]);

  protected readonly currencyCode = signal('TZS');
  protected readonly bucketFilter = signal<AgingBucket | null>(null);

  /**
   * The numeric party PK (string) of the last customer row clicked.
   * Used to deep-link the Statements button to that customer's AR statement.
   * Null when no row is active (header-level Statements button → no pre-filter).
   */
  protected readonly activeCustomerId = signal<string | null>(null);

  protected readonly bucketChips: { key: AgingBucket; label: string }[] = [
    { key: 'CURRENT',   label: 'Current' },
    { key: 'D_1_30',    label: '1 – 30' },
    { key: 'D_31_60',   label: '31 – 60' },
    { key: 'D_61_90',   label: '61 – 90' },
    { key: 'D_90_PLUS', label: '90+' },
  ];

  /** Returns the totals for the active tab so the bucket row is shared. */
  protected activeTotals(): DebtAgingTotals | SupplierAgingTotals | null {
    return this.activeTab() === 'AR' ? this.arTotals() : this.apTotals();
  }

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(qp => {
      const bf = qp.get('bucketFilter');
      this.bucketFilter.set(this.isAgingBucket(bf) ? bf : null);
      this.load();
    });
  }

  protected switchTab(tab: 'AR' | 'AP'): void {
    if (this.activeTab() === tab) return;
    this.activeTab.set(tab);
    this.error.set(null);
    this.load();
  }

  protected setBucketFilter(b: AgingBucket | null): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { bucketFilter: b },
      queryParamsHandling: 'merge',
    });
  }

  protected openCustomer(uid: string, customerId: string): void {
    // Track the numeric id so the Statements deep-link pre-filters to this customer.
    this.activeCustomerId.set(customerId);
    this.router.navigate(['/debt/customer/uid', uid]);
  }

  protected openSupplier(uid: string): void {
    this.router.navigate(['/debt/supplier/uid', uid]);
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

    if (this.activeTab() === 'AR') {
      this.loadAr(branchId);
    } else {
      this.loadAp(branchId);
    }
  }

  private loadAr(branchId: string | null): void {
    this.debt.aging(branchId).subscribe({
      next: (a: DebtAging) => {
        this.arTotals.set(a.totals);
        if (a.currencyCode) this.currencyCode.set(a.currencyCode);
        this.permissionDenied.set(false);
        this.loadArDunning(branchId);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.permissionDenied.set(true);
          this.arRows.set([]);
          this.arTotals.set(null);
        } else {
          this.error.set(this.formatError(err, 'Failed to load AR aging report.'));
        }
        this.loading.set(false);
      },
    });
  }

  private loadArDunning(branchId: string | null): void {
    this.debt.dunning(branchId, this.bucketFilter(), 0, 100).subscribe({
      next: page => {
        this.arRows.set(page.content);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.permissionDenied.set(true);
          this.arRows.set([]);
        } else {
          this.error.set(this.formatError(err, 'Failed to load AR dunning queue.'));
        }
        this.loading.set(false);
      },
    });
  }

  private loadAp(branchId: string | null): void {
    this.debt.supplierAging(branchId).subscribe({
      next: (a: SupplierAging) => {
        this.apTotals.set(a.totals);
        if (a.currencyCode) this.currencyCode.set(a.currencyCode);
        this.permissionDenied.set(false);
        this.loadApDunning(branchId);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.permissionDenied.set(true);
          this.apRows.set([]);
          this.apTotals.set(null);
        } else {
          this.error.set(this.formatError(err, 'Failed to load AP aging report.'));
        }
        this.loading.set(false);
      },
    });
  }

  private loadApDunning(branchId: string | null): void {
    this.debt.supplierDunning(branchId, this.bucketFilter(), 0, 100).subscribe({
      next: page => {
        this.apRows.set(page.content);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.permissionDenied.set(true);
          this.apRows.set([]);
        } else {
          this.error.set(this.formatError(err, 'Failed to load AP obligations queue.'));
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
