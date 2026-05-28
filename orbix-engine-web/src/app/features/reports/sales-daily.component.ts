import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { ReportExportMenuComponent } from './report-export-menu.component';
import { ReportExport } from './report-export.service';

/**
 * Slice F — KPI 1.A drill-through destination. Reads {@code branchId} +
 * {@code businessDate} from {@link ActivatedRoute.queryParamMap}, fetches
 * {@code GET /api/v1/reports/sales-daily} (backed by
 * {@code SalesReportController#dailySales}), and renders a flat per-document
 * list blending sales_invoice + pos_sale rows.
 *
 * Slice I — export menu wired in via {@link ReportExportMenuComponent}.
 */
interface DailySalesRow {
  source: 'SALES_INVOICE' | 'POS_SALE';
  id: string;
  number: string;
  customerId: string | null;
  branchId: string;
  totalAmount: number;
  taxAmount: number;
  discountAmount: number;
  status: string;
  paymentTerms: 'CASH' | 'CREDIT' | null;
  occurredAt: string;
}

@Component({
  selector: 'orbix-sales-daily',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe, DecimalPipe, ReportExportMenuComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/reports" class="text-decoration-none text-secondary">Reports</a> &rsaquo; Daily sales
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Daily sales</h1>
        <p class="text-secondary mb-0 small">
          @if (businessDate()) {
            Branch #{{ branchIdParam() ?? '—' }} · {{ businessDate() }}
            · {{ rows().length }} document{{ rows().length === 1 ? '' : 's' }}.
          } @else {
            No business date supplied.
          }
        </p>
      </div>

      <orbix-report-export-menu
        [exportBuilder]="buildExport"
        [disabled]="rows().length === 0">
      </orbix-report-export-menu>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2" role="alert">
        <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="error.set(null)"
                aria-label="Dismiss error"></button>
      </div>
    }

    <div class="card border-0 shadow-sm overflow-hidden">
      @if (rows().length === 0 && !loading()) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-receipt" aria-hidden="true"></i></div>
          <p class="small text-secondary mb-0">No documents for this date.</p>
        </div>
      } @else {
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 simple-table">
            <caption class="visually-hidden">Daily sales for {{ businessDate() }}</caption>
            <thead>
              <tr>
                <th scope="col">Source</th>
                <th scope="col">Number</th>
                <th scope="col">Customer</th>
                <th scope="col">Status</th>
                <th scope="col">Terms</th>
                <th scope="col" class="text-end">Discount</th>
                <th scope="col" class="text-end">Tax</th>
                <th scope="col" class="text-end">Total</th>
                <th scope="col">Occurred</th>
              </tr>
            </thead>
            <tbody>
              @for (row of rows(); track row.source + ':' + row.id) {
                <tr>
                  <td>
                    <span class="badge text-bg-light border text-secondary">
                      {{ row.source === 'SALES_INVOICE' ? 'INV' : 'POS' }}
                    </span>
                  </td>
                  <td><span class="font-monospace">{{ row.number }}</span></td>
                  <td class="small text-secondary">{{ row.customerId ? '#' + row.customerId : '—' }}</td>
                  <td class="small">{{ row.status }}</td>
                  <td class="small text-secondary">{{ row.paymentTerms ?? '—' }}</td>
                  <td class="text-end small text-secondary">{{ row.discountAmount | number:'1.2-2' }}</td>
                  <td class="text-end small text-secondary">{{ row.taxAmount | number:'1.2-2' }}</td>
                  <td class="text-end fw-semibold">{{ row.totalAmount | number:'1.2-2' }}</td>
                  <td class="small text-secondary">{{ row.occurredAt | date:'short' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.75rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }
    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `],
})
export class SalesDailyComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  protected readonly rows = signal<DailySalesRow[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly branchIdParam = signal<string | null>(null);
  protected readonly businessDate = signal<string | null>(null);

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const branchId = params.get('branchId');
      const businessDate = params.get('businessDate');
      this.branchIdParam.set(branchId);
      this.businessDate.set(businessDate);
      if (businessDate) this.fetch(branchId, businessDate);
    });
  }

  private fetch(branchId: string | null, businessDate: string): void {
    let params = new HttpParams().set('businessDate', businessDate);
    if (branchId !== null) params = params.set('branchId', branchId);
    this.loading.set(true);
    this.error.set(null);
    unwrap(this.http.get<ApiResponse<DailySalesRow[]>>(
      `${this.base}/reports/sales-daily`, { params }
    )).subscribe({
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
    title: 'Daily Sales',
    subtitle: `Branch: ${this.branchIdParam() ?? 'all'} · ${this.businessDate() ?? ''}`,
    columns: [
      { key: 'source',         label: 'Source',   align: 'left',  format: 'text' },
      { key: 'number',         label: 'Number',   align: 'left',  format: 'text' },
      { key: 'customerId',     label: 'Customer', align: 'left',  format: 'text' },
      { key: 'status',         label: 'Status',   align: 'left',  format: 'text' },
      { key: 'paymentTerms',   label: 'Terms',    align: 'left',  format: 'text' },
      { key: 'discountAmount', label: 'Discount', align: 'right', format: 'currency' },
      { key: 'taxAmount',      label: 'Tax',      align: 'right', format: 'currency' },
      { key: 'totalAmount',    label: 'Total',    align: 'right', format: 'currency' },
      { key: 'occurredAt',     label: 'Occurred', align: 'left',  format: 'date' },
    ],
    rows: this.rows().map(r => ({
      source:         r.source === 'SALES_INVOICE' ? 'Invoice' : 'POS',
      number:         r.number,
      customerId:     r.customerId ?? '',
      status:         r.status,
      paymentTerms:   r.paymentTerms ?? '',
      discountAmount: r.discountAmount,
      taxAmount:      r.taxAmount,
      totalAmount:    r.totalAmount,
      occurredAt:     r.occurredAt,
    })),
    totals: {
      source:         'TOTAL',
      number:         '',
      customerId:     '',
      status:         '',
      paymentTerms:   '',
      discountAmount: this.rows().reduce((s, r) => s + r.discountAmount, 0),
      taxAmount:      this.rows().reduce((s, r) => s + r.taxAmount, 0),
      totalAmount:    this.rows().reduce((s, r) => s + r.totalAmount, 0),
      occurredAt:     '',
    },
  });
}
