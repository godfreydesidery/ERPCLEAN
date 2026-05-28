import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { ReportExportMenuComponent } from './report-export-menu.component';
import { ReportExport } from './report-export.service';

// ---------------------------------------------------------------------------
// DTO shapes — mirrors ZHistoryEntryDto + TillReportDto from the backend
// ---------------------------------------------------------------------------

interface TillReport {
  tillSessionId: string;
  tillId: string;
  branchId: string;
  businessDate: string;
  status: string;
  cashierId: string;
  supervisorId: string | null;
  openedAt: string;
  closedAt: string | null;
  salesCount: number;
  refundsCount: number;
  grossSales: number;
  grossRefunds: number;
  netSales: number;
  discountTotal: number;
  expectedCash: number;
  declaredCash: number | null;
  variance: number | null;
}

interface ZHistoryEntry {
  tillSessionId: string;
  tillId: string;
  branchId: string;
  businessDate: string;
  sessionStatus: string;
  openedAt: string;
  closedAt: string | null;
  report: TillReport;
}

// Default last-7-days range helpers
function today(): string {
  return new Date().toISOString().slice(0, 10);
}
function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

@Component({
  selector: 'orbix-z-history',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe, DecimalPipe, FormsModule, ReportExportMenuComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/reports" class="text-decoration-none text-secondary">Reports</a> &rsaquo; Z-history
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Z-history</h1>
        <p class="text-secondary mb-0 small">
          Closed till sessions with Z-report totals.
          {{ rows().length }} session{{ rows().length === 1 ? '' : 's' }}.
        </p>
      </div>

      <orbix-report-export-menu
        [exportBuilder]="buildExport"
        [disabled]="rows().length === 0">
      </orbix-report-export-menu>
    </header>

    <!-- Filters -->
    <form class="row g-2 mb-4 align-items-end" (ngSubmit)="fetch()" aria-label="Report filters">
      <div class="col-12 col-sm-6 col-md-3">
        <label for="zh-branch" class="form-label small fw-semibold mb-1">Branch ID</label>
        <input id="zh-branch" type="text" class="form-control form-control-sm"
               [(ngModel)]="branchIdInput" name="branchId"
               placeholder="All branches"
               aria-describedby="zh-branch-hint">
        <div id="zh-branch-hint" class="form-text">Numeric branch ID</div>
      </div>

      <div class="col-12 col-sm-6 col-md-3">
        <label for="zh-from" class="form-label small fw-semibold mb-1">From</label>
        <input id="zh-from" type="date" class="form-control form-control-sm"
               [(ngModel)]="fromDateInput" name="fromDate"
               required aria-required="true">
      </div>

      <div class="col-12 col-sm-6 col-md-3">
        <label for="zh-to" class="form-label small fw-semibold mb-1">To</label>
        <input id="zh-to" type="date" class="form-control form-control-sm"
               [(ngModel)]="toDateInput" name="toDate"
               required aria-required="true">
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

    <!-- Loading -->
    @if (loading()) {
      <div aria-live="polite" aria-busy="true" class="visually-hidden">Loading Z-history…</div>
      <div class="card border-0 shadow-sm p-4 text-center">
        <div class="spinner-border text-primary mx-auto" role="status">
          <span class="visually-hidden">Loading…</span>
        </div>
      </div>
    }

    <!-- Empty -->
    @if (!loading() && !error() && rows().length === 0) {
      <div class="card border-0 shadow-sm p-5 text-center">
        <div class="empty-icon mx-auto mb-3">
          <i class="bi bi-printer" aria-hidden="true"></i>
        </div>
        <p class="small text-secondary mb-0">No closed till sessions in the selected date range.</p>
      </div>
    }

    <!-- Table -->
    @if (!loading() && rows().length > 0) {
      <div class="card border-0 shadow-sm overflow-hidden">
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 zh-table">
            <caption class="visually-hidden">Z-history from {{ fromDateInput }} to {{ toDateInput }}</caption>
            <thead>
              <tr>
                <th scope="col">Date</th>
                <th scope="col">Till</th>
                <th scope="col">Opened</th>
                <th scope="col">Closed</th>
                <th scope="col">Cashier</th>
                <th scope="col" class="text-end">Gross sales</th>
                <th scope="col" class="text-end">Refunds</th>
                <th scope="col" class="text-end">Net sales</th>
                <th scope="col" class="text-end">Discounts</th>
                <th scope="col" class="text-end">Declared cash</th>
                <th scope="col" class="text-end">Variance</th>
                <th scope="col"></th>
              </tr>
            </thead>
            <tbody>
              @for (row of rows(); track row.tillSessionId) {
                <tr>
                  <td><span class="font-monospace small">{{ row.businessDate }}</span></td>
                  <td class="small text-secondary">#{{ row.tillId }}</td>
                  <td class="small text-secondary">{{ row.openedAt | date:'short' }}</td>
                  <td class="small text-secondary">{{ row.closedAt ? (row.closedAt | date:'short') : '—' }}</td>
                  <td class="small text-secondary">#{{ row.report.cashierId }}</td>
                  <td class="text-end fw-semibold">{{ row.report.grossSales | number:'1.0-0' }}</td>
                  <td class="text-end small text-danger">{{ row.report.grossRefunds | number:'1.0-0' }}</td>
                  <td class="text-end fw-semibold">{{ row.report.netSales | number:'1.0-0' }}</td>
                  <td class="text-end small text-secondary">{{ row.report.discountTotal | number:'1.0-0' }}</td>
                  <td class="text-end small">
                    {{ row.report.declaredCash !== null ? (row.report.declaredCash | number:'1.0-0') : '—' }}
                  </td>
                  <td class="text-end small"
                      [class.text-danger]="(row.report.variance ?? 0) < 0"
                      [class.text-success]="(row.report.variance ?? 0) > 0">
                    {{ row.report.variance !== null ? (row.report.variance | number:'1.0-0') : '—' }}
                  </td>
                  <td>
                    <a class="btn btn-link btn-sm p-0"
                       [routerLink]="['/reports/z-report']"
                       [queryParams]="{ tillSessionId: row.tillSessionId }"
                       title="View Z-report">
                      <i class="bi bi-eye" [attr.aria-label]="'View Z-report for session ' + row.tillSessionId"></i>
                    </a>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }
    .zh-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.65rem 0.75rem;
    }
    .zh-table tbody td { padding: 0.65rem 0.75rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .zh-table tbody tr:last-child td { border-bottom: none; }
    .zh-table tbody tr:hover { background: #f8fafc; }
    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #ffedd5; color: #c2410c; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `],
})
export class ZHistoryComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  protected branchIdInput = '';
  protected fromDateInput = daysAgo(7);
  protected toDateInput = today();

  protected readonly rows = signal<ZHistoryEntry[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const branchId = params.get('branchId') ?? '';
      const from = params.get('from') ?? daysAgo(7);
      const to = params.get('to') ?? today();
      this.branchIdInput = branchId;
      this.fromDateInput = from;
      this.toDateInput = to;
      this.fetch();
    });
  }

  fetch(): void {
    let params = new HttpParams()
      .set('from', this.fromDateInput)
      .set('to', this.toDateInput);
    if (this.branchIdInput.trim()) params = params.set('branchId', this.branchIdInput.trim());

    this.loading.set(true);
    this.error.set(null);

    unwrap(
      this.http.get<ApiResponse<ZHistoryEntry[]>>(`${this.base}/reports/z-history`, { params })
    ).subscribe({
      next: list => { this.rows.set(list); this.loading.set(false); },
      error: err => {
        this.loading.set(false);
        if (err instanceof HttpErrorResponse) {
          const envelope = err.error as ApiResponse<unknown> | null;
          this.error.set(envelope?.message ?? `Request failed (${err.status})`);
        } else {
          this.error.set('Unexpected error');
        }
      },
    });
  }

  readonly buildExport = (): ReportExport => ({
    title: 'Z-History Report',
    subtitle: `${this.fromDateInput} to ${this.toDateInput}${this.branchIdInput ? ' · Branch: ' + this.branchIdInput : ''}`,
    columns: [
      { key: 'businessDate', label: 'Date', align: 'left', format: 'date' },
      { key: 'tillId', label: 'Till', align: 'left', format: 'text' },
      { key: 'openedAt', label: 'Opened', align: 'left', format: 'text' },
      { key: 'closedAt', label: 'Closed', align: 'left', format: 'text' },
      { key: 'cashierId', label: 'Cashier', align: 'left', format: 'text' },
      { key: 'grossSales', label: 'Gross Sales', align: 'right', format: 'currency' },
      { key: 'grossRefunds', label: 'Refunds', align: 'right', format: 'currency' },
      { key: 'netSales', label: 'Net Sales', align: 'right', format: 'currency' },
      { key: 'discountTotal', label: 'Discounts', align: 'right', format: 'currency' },
      { key: 'declaredCash', label: 'Declared Cash', align: 'right', format: 'currency' },
      { key: 'variance', label: 'Variance', align: 'right', format: 'currency' },
    ],
    rows: this.rows().map(r => ({
      businessDate: r.businessDate,
      tillId: r.tillId,
      openedAt: r.openedAt,
      closedAt: r.closedAt ?? '',
      cashierId: r.report.cashierId,
      grossSales: r.report.grossSales,
      grossRefunds: r.report.grossRefunds,
      netSales: r.report.netSales,
      discountTotal: r.report.discountTotal,
      declaredCash: r.report.declaredCash ?? 0,
      variance: r.report.variance ?? 0,
    })),
    totals: {
      businessDate: 'TOTAL',
      tillId: '',
      openedAt: '',
      closedAt: '',
      cashierId: '',
      grossSales: this.rows().reduce((s, r) => s + r.report.grossSales, 0),
      grossRefunds: this.rows().reduce((s, r) => s + r.report.grossRefunds, 0),
      netSales: this.rows().reduce((s, r) => s + r.report.netSales, 0),
      discountTotal: this.rows().reduce((s, r) => s + r.report.discountTotal, 0),
      declaredCash: this.rows().reduce((s, r) => s + (r.report.declaredCash ?? 0), 0),
      variance: this.rows().reduce((s, r) => s + (r.report.variance ?? 0), 0),
    },
  });
}
