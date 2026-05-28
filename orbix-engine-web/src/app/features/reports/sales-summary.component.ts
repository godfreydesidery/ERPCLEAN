import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { ReportExportMenuComponent } from './report-export-menu.component';
import { ReportExport } from './report-export.service';

// ---------------------------------------------------------------------------
// DTO shapes — mirrors DailySummaryDto from the backend
// ---------------------------------------------------------------------------

interface SalesBlock {
  invoiceTotal: number;
  invoiceTax: number;
  invoiceDiscount: number;
  invoiceCount: number;
  posSaleNet: number;
  posSaleTax: number;
  posSaleDiscount: number;
  posSaleCount: number;
  posRefundCount: number;
  grandTotal: number;
}

interface PurchasesBlock {
  grnTotal: number;
  grnTax: number;
  grnCount: number;
}

interface CashBlock {
  openingTotal: number;
  inTotal: number;
  outTotal: number;
  closingTotal: number;
  closingByAccount: Record<string, number> | null;
}

interface DailySummary {
  businessDate: string;
  branchId: string;
  sales: SalesBlock;
  purchases: PurchasesBlock;
  cash: CashBlock;
}

@Component({
  selector: 'orbix-sales-summary',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe, DecimalPipe, FormsModule, ReportExportMenuComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/reports" class="text-decoration-none text-secondary">Reports</a> &rsaquo; Daily summary
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Daily summary</h1>
        <p class="text-secondary mb-0 small">
          Sales, purchases and cash rollup for a branch on a single business day.
        </p>
      </div>

      @if (summary()) {
        <orbix-report-export-menu
          [exportBuilder]="buildExport"
          [disabled]="!summary()">
        </orbix-report-export-menu>
      }
    </header>

    <!-- Filters -->
    <form class="row g-2 mb-4 align-items-end" (ngSubmit)="fetch()" aria-label="Report filters">
      <div class="col-12 col-sm-6 col-md-4">
        <label for="ss-branch" class="form-label small fw-semibold mb-1">Branch ID</label>
        <input id="ss-branch" type="text" class="form-control form-control-sm"
               [(ngModel)]="branchIdInput" name="branchId"
               placeholder="Leave blank for all branches"
               aria-describedby="ss-branch-hint">
        <div id="ss-branch-hint" class="form-text">Numeric branch ID</div>
      </div>

      <div class="col-12 col-sm-6 col-md-4">
        <label for="ss-date" class="form-label small fw-semibold mb-1">Business date</label>
        <input id="ss-date" type="date" class="form-control form-control-sm"
               [(ngModel)]="businessDateInput" name="businessDate"
               required
               aria-required="true">
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
      <div aria-live="polite" aria-busy="true" class="visually-hidden">Loading report…</div>
      <div class="row g-3 mb-4">
        @for (_ of [0,1,2,3,4]; track $index) {
          <div class="col-6 col-md-4 col-lg-2">
            <div class="card border-0 shadow-sm p-3">
              <div class="placeholder-glow">
                <span class="placeholder col-8 mb-2 d-block"></span>
                <span class="placeholder col-6 d-block"></span>
              </div>
            </div>
          </div>
        }
      </div>
    }

    <!-- Empty state -->
    @if (!loading() && !error() && fetched() && !summary()) {
      <div class="card border-0 shadow-sm p-5 text-center">
        <div class="empty-icon mx-auto mb-3">
          <i class="bi bi-bar-chart" aria-hidden="true"></i>
        </div>
        <p class="small text-secondary mb-0">No summary data for the selected date.</p>
      </div>
    }

    <!-- Populated -->
    @if (summary(); as s) {
      <!-- KPI tiles -->
      <div class="row g-3 mb-4" role="region" aria-label="Key performance indicators">
        <div class="col-6 col-md-4 col-lg-2">
          <div class="kpi-tile kpi-tile--blue">
            <div class="kpi-tile__label">Grand total sales</div>
            <div class="kpi-tile__value">{{ s.sales.grandTotal | number:'1.0-0' }}</div>
            <div class="kpi-tile__sub">{{ s.sales.invoiceCount + s.sales.posSaleCount }} documents</div>
          </div>
        </div>
        <div class="col-6 col-md-4 col-lg-2">
          <div class="kpi-tile kpi-tile--green">
            <div class="kpi-tile__label">Invoice sales</div>
            <div class="kpi-tile__value">{{ s.sales.invoiceTotal | number:'1.0-0' }}</div>
            <div class="kpi-tile__sub">{{ s.sales.invoiceCount }} invoice{{ s.sales.invoiceCount === 1 ? '' : 's' }}</div>
          </div>
        </div>
        <div class="col-6 col-md-4 col-lg-2">
          <div class="kpi-tile kpi-tile--violet">
            <div class="kpi-tile__label">POS sales</div>
            <div class="kpi-tile__value">{{ s.sales.posSaleNet | number:'1.0-0' }}</div>
            <div class="kpi-tile__sub">{{ s.sales.posSaleCount }} sale{{ s.sales.posSaleCount === 1 ? '' : 's' }}, {{ s.sales.posRefundCount }} refund{{ s.sales.posRefundCount === 1 ? '' : 's' }}</div>
          </div>
        </div>
        <div class="col-6 col-md-4 col-lg-2">
          <div class="kpi-tile kpi-tile--amber">
            <div class="kpi-tile__label">Purchases (GRNs)</div>
            <div class="kpi-tile__value">{{ s.purchases.grnTotal | number:'1.0-0' }}</div>
            <div class="kpi-tile__sub">{{ s.purchases.grnCount }} GRN{{ s.purchases.grnCount === 1 ? '' : 's' }}</div>
          </div>
        </div>
        <div class="col-6 col-md-4 col-lg-2">
          <div class="kpi-tile kpi-tile--rose">
            <div class="kpi-tile__label">Cash in</div>
            <div class="kpi-tile__value">{{ s.cash.inTotal | number:'1.0-0' }}</div>
            <div class="kpi-tile__sub">Cash received</div>
          </div>
        </div>
        <div class="col-6 col-md-4 col-lg-2">
          <div class="kpi-tile kpi-tile--orange">
            <div class="kpi-tile__label">Closing cash</div>
            <div class="kpi-tile__value">{{ s.cash.closingTotal | number:'1.0-0' }}</div>
            <div class="kpi-tile__sub">Opening: {{ s.cash.openingTotal | number:'1.0-0' }}</div>
          </div>
        </div>
      </div>

      <!-- Sales breakdown table -->
      <div class="card border-0 shadow-sm mb-4 overflow-hidden">
        <div class="card-header bg-white border-bottom py-2 px-3">
          <h2 class="h6 fw-bold mb-0 text-dark">Sales breakdown</h2>
        </div>
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 summary-table">
            <caption class="visually-hidden">Sales breakdown for {{ s.businessDate }}</caption>
            <thead>
              <tr>
                <th scope="col">Category</th>
                <th scope="col" class="text-end">Count</th>
                <th scope="col" class="text-end">Total (TZS)</th>
                <th scope="col" class="text-end">Tax (TZS)</th>
                <th scope="col" class="text-end">Discount (TZS)</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Sales invoices</td>
                <td class="text-end">{{ s.sales.invoiceCount }}</td>
                <td class="text-end fw-semibold">{{ s.sales.invoiceTotal | number:'1.0-0' }}</td>
                <td class="text-end small text-secondary">{{ s.sales.invoiceTax | number:'1.0-0' }}</td>
                <td class="text-end small text-secondary">{{ s.sales.invoiceDiscount | number:'1.0-0' }}</td>
              </tr>
              <tr>
                <td>POS sales</td>
                <td class="text-end">{{ s.sales.posSaleCount }}</td>
                <td class="text-end fw-semibold">{{ s.sales.posSaleNet | number:'1.0-0' }}</td>
                <td class="text-end small text-secondary">{{ s.sales.posSaleTax | number:'1.0-0' }}</td>
                <td class="text-end small text-secondary">{{ s.sales.posSaleDiscount | number:'1.0-0' }}</td>
              </tr>
              <tr>
                <td>POS refunds</td>
                <td class="text-end">{{ s.sales.posRefundCount }}</td>
                <td class="text-end fw-semibold text-danger">—</td>
                <td class="text-end small text-secondary">—</td>
                <td class="text-end small text-secondary">—</td>
              </tr>
            </tbody>
            <tfoot>
              <tr class="fw-bold">
                <td>Grand total</td>
                <td class="text-end">{{ s.sales.invoiceCount + s.sales.posSaleCount }}</td>
                <td class="text-end">{{ s.sales.grandTotal | number:'1.0-0' }}</td>
                <td class="text-end small">{{ (s.sales.invoiceTax + s.sales.posSaleTax) | number:'1.0-0' }}</td>
                <td class="text-end small">{{ (s.sales.invoiceDiscount + s.sales.posSaleDiscount) | number:'1.0-0' }}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      <!-- Cash breakdown -->
      <div class="card border-0 shadow-sm overflow-hidden">
        <div class="card-header bg-white border-bottom py-2 px-3">
          <h2 class="h6 fw-bold mb-0 text-dark">Cash position</h2>
        </div>
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 summary-table">
            <caption class="visually-hidden">Cash position for {{ s.businessDate }}</caption>
            <thead>
              <tr>
                <th scope="col">Item</th>
                <th scope="col" class="text-end">Amount (TZS)</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Opening cash</td>
                <td class="text-end">{{ s.cash.openingTotal | number:'1.0-0' }}</td>
              </tr>
              <tr>
                <td>Cash in</td>
                <td class="text-end text-success fw-semibold">+{{ s.cash.inTotal | number:'1.0-0' }}</td>
              </tr>
              <tr>
                <td>Cash out</td>
                <td class="text-end text-danger fw-semibold">−{{ s.cash.outTotal | number:'1.0-0' }}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr class="fw-bold">
                <td>Closing cash</td>
                <td class="text-end">{{ s.cash.closingTotal | number:'1.0-0' }}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }

    .kpi-tile {
      padding: 1rem 1.25rem; border-radius: 12px; border: 1px solid #e5e7eb;
      background: #fff; height: 100%;
    }
    .kpi-tile__label {
      font-size: 0.72rem; font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.07em; color: #6b7280; margin-bottom: 0.25rem;
    }
    .kpi-tile__value {
      font-size: 1.35rem; font-weight: 700; color: #111827; line-height: 1.2;
    }
    .kpi-tile__sub { font-size: 0.72rem; color: #9ca3af; margin-top: 0.2rem; }

    .kpi-tile--blue   { border-left: 3px solid #1d4ed8; }
    .kpi-tile--green  { border-left: 3px solid #047857; }
    .kpi-tile--amber  { border-left: 3px solid #b45309; }
    .kpi-tile--rose   { border-left: 3px solid #be123c; }
    .kpi-tile--violet { border-left: 3px solid #6d28d9; }
    .kpi-tile--orange { border-left: 3px solid #c2410c; }

    .summary-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .summary-table tbody td { padding: 0.75rem 1rem; border-bottom: 1px solid #f3f4f6; }
    .summary-table tbody tr:last-child td { border-bottom: none; }
    .summary-table tfoot td { padding: 0.75rem 1rem; border-top: 2px solid #e5e7eb; background: #f9fafb; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `],
})
export class SalesSummaryComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  protected branchIdInput = '';
  protected businessDateInput = new Date().toISOString().slice(0, 10);

  protected readonly summary = signal<DailySummary | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly fetched = signal(false);

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const branchId = params.get('branchId') ?? '';
      const date = params.get('businessDate') ?? new Date().toISOString().slice(0, 10);
      this.branchIdInput = branchId;
      this.businessDateInput = date;
      this.fetch();
    });
  }

  fetch(): void {
    let params = new HttpParams().set('businessDate', this.businessDateInput);
    if (this.branchIdInput.trim()) params = params.set('branchId', this.branchIdInput.trim());

    this.loading.set(true);
    this.error.set(null);
    this.summary.set(null);

    unwrap(
      this.http.get<ApiResponse<DailySummary>>(`${this.base}/reports/sales-summary`, { params })
    ).subscribe({
      next: data => {
        this.summary.set(data);
        this.loading.set(false);
        this.fetched.set(true);
      },
      error: err => {
        this.loading.set(false);
        this.fetched.set(true);
        if (err instanceof HttpErrorResponse) {
          const envelope = err.error as ApiResponse<unknown> | null;
          this.error.set(envelope?.message ?? `Request failed (${err.status})`);
        } else {
          this.error.set('Unexpected error');
        }
      },
    });
  }

  readonly buildExport = (): ReportExport => {
    const s = this.summary()!;
    return {
      title: 'Daily Sales Summary',
      subtitle: `Business date: ${s.businessDate}${this.branchIdInput ? ' · Branch: ' + this.branchIdInput : ''}`,
      columns: [
        { key: 'category', label: 'Category', align: 'left', format: 'text' },
        { key: 'count', label: 'Count', align: 'right', format: 'number' },
        { key: 'total', label: 'Total (TZS)', align: 'right', format: 'currency' },
        { key: 'tax', label: 'Tax (TZS)', align: 'right', format: 'currency' },
        { key: 'discount', label: 'Discount (TZS)', align: 'right', format: 'currency' },
      ],
      rows: [
        {
          category: 'Sales invoices',
          count: s.sales.invoiceCount,
          total: s.sales.invoiceTotal,
          tax: s.sales.invoiceTax,
          discount: s.sales.invoiceDiscount,
        },
        {
          category: 'POS sales',
          count: s.sales.posSaleCount,
          total: s.sales.posSaleNet,
          tax: s.sales.posSaleTax,
          discount: s.sales.posSaleDiscount,
        },
        {
          category: 'POS refunds',
          count: s.sales.posRefundCount,
          total: 0,
          tax: 0,
          discount: 0,
        },
      ],
      totals: {
        category: 'Grand total',
        count: s.sales.invoiceCount + s.sales.posSaleCount,
        total: s.sales.grandTotal,
        tax: s.sales.invoiceTax + s.sales.posSaleTax,
        discount: s.sales.invoiceDiscount + s.sales.posSaleDiscount,
      },
    };
  };
}
