import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { ApiResponse, unwrap } from '../../../core/api/api-response';
import { AuthService } from '../../../core/auth/auth.service';
import { BranchService } from '../../../core/branch/branch.service';
import { environment } from '../../../../environments/environment';
import { PosSale } from './pos-sales.models';

@Component({
  selector: 'orbix-pos-sales',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
        <a routerLink=".." class="text-decoration-none text-secondary">Admin</a> &rsaquo; POS sales
      </p>
      <h1 class="h3 fw-bold mb-1 text-dark">POS sales</h1>
      <p class="text-secondary mb-0 small">
        {{ sales().length }} sale{{ sales().length === 1 ? '' : 's' }} on file — read-only mirror of till transactions pushed from the Flutter app.
      </p>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i><span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="error.set(null)"></button>
      </div>
    }
    @if (info()) {
      <div class="alert alert-success d-flex align-items-center gap-2 py-2">
        <i class="bi bi-check-circle-fill"></i><span class="flex-grow-1">{{ info() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="info.set(null)"></button>
      </div>
    }

    <div class="row g-3 g-md-4">
      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Recent sales</h2>
            <span class="badge text-bg-light text-secondary">{{ sales().length }}</span>
          </div>
          @if (sales().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-receipt"></i></div>
              <p class="small text-secondary mb-0">No POS sales yet.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 ps-list">
              @for (s of sales(); track s.id) {
                <li>
                  <button type="button" class="ps-row"
                          [class.is-active]="selected()?.id === s.id"
                          (click)="select(s)">
                    <div class="flex-grow-1 min-w-0">
                      <div class="d-flex align-items-center gap-2 mb-1">
                        <span class="badge text-bg-light border text-secondary font-monospace">{{ s.number }}</span>
                        <span class="status-badge status-badge--{{ s.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ s.status }}
                        </span>
                      </div>
                      <p class="small text-secondary mb-0">
                        Session #{{ s.tillSessionId }} · {{ s.saleAt | date:'short' }}
                      </p>
                    </div>
                    <div class="fw-bold text-dark">{{ s.totalAmount | number:'1.2-2' }}</div>
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      </div>

      <div class="col-12 col-lg-7">
        @if (selected(); as sale) {
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4">
              <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                <div>
                  <p class="small text-secondary mb-1">{{ sale.kind }} · {{ sale.saleAt | date:'short' }}</p>
                  <h2 class="h4 fw-bold mb-1 text-dark">{{ sale.number }}</h2>
                  <span class="status-badge status-badge--{{ sale.status.toLowerCase() }}">
                    <span class="status-badge__dot"></span>{{ sale.status }}
                  </span>
                </div>
                @if (sale.status === 'POSTED') {
                  <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1"
                          [disabled]="busy()" (click)="voidSale(sale)">
                    <i class="bi bi-slash-circle"></i> Void (same-day)
                  </button>
                }
              </div>

              <div class="row g-2 totals-row mb-3">
                <div class="col-6 col-md-3">
                  <p class="totals-row__label">Subtotal</p>
                  <p class="totals-row__value">{{ sale.subtotalAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-3">
                  <p class="totals-row__label">Discount</p>
                  <p class="totals-row__value">{{ sale.discountAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-3">
                  <p class="totals-row__label">Tax</p>
                  <p class="totals-row__value">{{ sale.taxAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-3">
                  <p class="totals-row__label">Total</p>
                  <p class="totals-row__value totals-row__value--strong">{{ sale.totalAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-3">
                  <p class="totals-row__label">Tendered</p>
                  <p class="totals-row__value">{{ sale.tenderedAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-3">
                  <p class="totals-row__label">Change</p>
                  <p class="totals-row__value">{{ sale.changeAmount | number:'1.2-2' }}</p>
                </div>
              </div>

              <dl class="row small mb-0">
                <dt class="col-4 text-secondary">Cashier</dt>
                <dd class="col-8 mb-1">#{{ sale.cashierId }}
                  @if (sale.supervisorId) { · supervisor #{{ sale.supervisorId }} }
                </dd>
                <dt class="col-4 text-secondary">Customer</dt><dd class="col-8 mb-1">#{{ sale.customerId }}</dd>
                <dt class="col-4 text-secondary">Section</dt><dd class="col-8 mb-1">#{{ sale.sectionId }}</dd>
                <dt class="col-4 text-secondary">Business date</dt><dd class="col-8 mb-1">{{ sale.businessDate }}</dd>
                <dt class="col-4 text-secondary">Server received</dt>
                <dd class="col-8 mb-1">{{ sale.serverAt | date:'short' }}</dd>
                @if (sale.voidedAt) {
                  <dt class="col-4 text-secondary">Voided</dt>
                  <dd class="col-8 mb-1">by #{{ sale.voidedBy }} — {{ sale.voidReason }}</dd>
                }
              </dl>
            </div>
          </div>

          <div class="card border-0 shadow-sm overflow-hidden mb-3">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Lines</h3>
              <span class="badge text-bg-light text-secondary">{{ sale.lines.length }}</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>#</th><th>Item</th>
                    <th class="text-end">Qty</th><th class="text-end">Price</th>
                    <th class="text-end">Disc</th><th class="text-end">Tax</th>
                    <th class="text-end">Total</th>
                  </tr>
                </thead>
                <tbody>
                  @for (line of sale.lines; track line.id) {
                    <tr>
                      <td class="small text-secondary">{{ line.lineNo }}</td>
                      <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ line.itemId }}</span></td>
                      <td class="text-end">{{ line.qty }}</td>
                      <td class="text-end">{{ line.unitPrice | number:'1.2-2' }}</td>
                      <td class="text-end small text-secondary">{{ line.discountPct | number:'1.0-2' }}%</td>
                      <td class="text-end small text-secondary">{{ line.taxAmount | number:'1.2-2' }}</td>
                      <td class="text-end fw-semibold">{{ line.lineTotal | number:'1.2-2' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>

          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Payments</h3>
              <span class="badge text-bg-light text-secondary">{{ sale.payments.length }}</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>Method</th><th class="text-end">Amount</th>
                    <th>Reference</th><th>Terminal</th><th>Last 4</th>
                  </tr>
                </thead>
                <tbody>
                  @for (pay of sale.payments; track pay.id) {
                    <tr>
                      <td>
                        <span class="method-pill">{{ pay.method }}</span>
                      </td>
                      <td class="text-end fw-semibold">{{ pay.amount | number:'1.2-2' }}</td>
                      <td class="small text-secondary font-monospace">{{ pay.reference ?? '—' }}</td>
                      <td class="small text-secondary">{{ pay.terminalId ?? '—' }}</td>
                      <td class="small text-secondary font-monospace">{{ pay.last4 ?? '—' }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="5" class="text-center text-secondary py-4">No payments.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick a POS sale</h2>
              <p class="small text-secondary mb-0">Sales are pushed from the Flutter till app — this page is read-only.</p>
            </div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .min-w-0 { min-width: 0; }

    .ps-list { max-height: 70vh; overflow-y: auto; }
    .ps-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .ps-row:hover { background: #f8fafc; }
    .ps-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .ps-row:last-child { border-bottom: none; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.65rem 1rem;
    }
    .simple-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }

    .totals-row .totals-row__label {
      font-size: 0.72rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; margin-bottom: 0.15rem;
    }
    .totals-row .totals-row__value { font-size: 1.0rem; font-weight: 600; color: #111827; margin-bottom: 0; }
    .totals-row .totals-row__value--strong { font-size: 1.3rem; color: #0d2a5b; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--posted { background: #d1fae5; color: #047857; }
    .status-badge--posted .status-badge__dot { background: #10b981; }
    .status-badge--voided { background: #fee2e2; color: #b91c1c; }
    .status-badge--voided .status-badge__dot { background: #f43f5e; }

    .method-pill {
      display: inline-block; padding: 0.2rem 0.55rem; border-radius: 999px;
      font-size: 0.72rem; font-weight: 600; letter-spacing: 0.03em;
      background: #e0ecff; color: #1d4ed8;
    }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #ffedd5; color: #c2410c; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class PosSalesComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);
  private readonly base = environment.apiUrl;

  protected readonly sales = signal<PosSale[]>([]);
  protected readonly selected = signal<PosSale | null>(null);
  protected readonly busy = signal<boolean>(false);
  protected readonly info = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  ngOnInit(): void { this.refresh(); }

  refresh(): void {
    const branchId = this.branchId();
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    unwrap(this.http.get<ApiResponse<PosSale[]>>(`${this.base}/pos-sales`, { params })).subscribe({
      next: rows => this.sales.set(rows),
      error: err => this.showError(err)
    });
  }

  select(s: PosSale): void { this.selected.set(s); }

  voidSale(s: PosSale): void {
    const reason = globalThis.prompt(`Void sale ${s.number} — reason?`);
    if (!reason?.trim()) return;
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    unwrap(this.http.post<ApiResponse<PosSale>>(
      `${this.base}/pos-sales/${s.id}/void`, { reason: reason.trim() }
    )).subscribe({
      next: voided => {
        this.busy.set(false);
        this.info.set(`Sale ${voided.number} voided.`);
        this.selected.set(voided);
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
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
