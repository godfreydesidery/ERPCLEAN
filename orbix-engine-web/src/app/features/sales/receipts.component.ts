import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { Currency, CurrencyService } from '../../core/currency/currency.service';
import { SearchSelectComponent, SearchSelectOption } from '../../core/ui/search-select.component';
import { SalesService } from './sales.service';
import {
  CreateReceiptAllocation,
  RECEIPT_METHODS,
  ReceiptMethod,
  SalesInvoice,
  SalesReceipt
} from './sales.models';

interface AllocRow { invoiceId: string | null; amount: number | null; outstanding: number; }

@Component({
  selector: 'orbix-sales-receipts',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, SearchSelectComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Sales</a> &rsaquo; Receipts
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Sales receipts</h1>
        <p class="text-secondary mb-0 small">{{ receipts().length }} receipt{{ receipts().length === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New receipt' }}
      </button>
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

    @if (showForm()) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">New receipt</h2>
          <button class="btn-close btn-sm" (click)="toggleForm()"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="create()" #f="ngForm" class="d-flex flex-column gap-3">
            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend"><i class="bi bi-cash-coin text-secondary"></i> Receipt header</legend>
              <div class="row g-2">
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Number</label>
                  <input class="form-control font-monospace" name="num" [(ngModel)]="newNumber" required placeholder="RCT0001">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Customer ID</label>
                  <input class="form-control" type="number" name="cid"
                         [(ngModel)]="newCustomerId" (ngModelChange)="loadOpenInvoices()" required>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Date</label>
                  <input class="form-control" type="date" name="rd" [(ngModel)]="newReceiptDate" required>
                </div>
                <div class="col-md-3">
                  <label class="form-label small fw-semibold text-secondary">Method</label>
                  <select class="form-select" name="m" [(ngModel)]="newMethod" required>
                    @for (m of methods; track m) { <option [ngValue]="m">{{ m }}</option> }
                  </select>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Reference</label>
                  <input class="form-control" name="ref" [(ngModel)]="newReference" placeholder="cheque, txn-id">
                </div>
                <div class="col-md-2">
                  <label class="form-label small fw-semibold text-secondary">Currency</label>
                  <orbix-search-select name="cur" [options]="currencyOptions()"
                                       [(ngModel)]="newCurrency" placeholder="Select…" required>
                  </orbix-search-select>
                </div>
                <div class="col-md-3">
                  <label class="form-label small fw-semibold text-secondary">Total</label>
                  <input class="form-control text-end" type="number" step="0.0001" min="0.0001"
                         name="tot" [(ngModel)]="newTotal" required>
                </div>
              </div>
            </fieldset>

            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend d-flex align-items-center justify-content-between">
                <span><i class="bi bi-link-45deg text-secondary"></i> Allocate to invoices</span>
                <button type="button" class="btn btn-sm btn-outline-primary" (click)="addAllocation()">
                  <i class="bi bi-plus-lg me-1"></i>Add allocation
                </button>
              </legend>
              <div class="table-responsive">
                <table class="table table-sm align-middle mb-0 line-table">
                  <thead>
                    <tr><th>Invoice</th><th class="text-end">Amount</th><th class="actions-col"></th></tr>
                  </thead>
                  <tbody>
                    @for (row of allocations; track $index) {
                      <tr>
                        <td>
                          <select class="form-select form-select-sm"
                                  [name]="'iid' + $index" [(ngModel)]="row.invoiceId"
                                  (ngModelChange)="onInvoicePicked(row, $event)">
                            <option [ngValue]="null">— pick —</option>
                            @for (inv of openInvoices(); track inv.id) {
                              <option [ngValue]="inv.id">
                                {{ inv.number }} ({{ inv.totalAmount - inv.paidAmount | number:'1.0-2' }} outstanding)
                              </option>
                            }
                          </select>
                        </td>
                        <td>
                          <input class="form-control form-control-sm text-end" type="number" step="0.0001" min="0"
                                 [name]="'iam' + $index" [(ngModel)]="row.amount">
                        </td>
                        <td class="actions-col">
                          <button type="button" class="btn btn-sm btn-outline-secondary"
                                  (click)="removeAllocation($index)">
                            <i class="bi bi-x-lg"></i>
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>

              <div class="alloc-summary">
                <div class="alloc-summary__cell">
                  <span class="alloc-summary__label">Total</span>
                  <span class="alloc-summary__value">{{ newTotal ?? 0 | number:'1.2-2' }}</span>
                </div>
                <div class="alloc-summary__cell">
                  <span class="alloc-summary__label">Allocated</span>
                  <span class="alloc-summary__value">{{ allocationsSum() | number:'1.2-2' }}</span>
                </div>
                <div class="alloc-summary__cell">
                  <span class="alloc-summary__label">Unallocated</span>
                  <span class="alloc-summary__value"
                        [class.text-warning]="((newTotal ?? 0) - allocationsSum()) > 0">
                    {{ (newTotal ?? 0) - allocationsSum() | number:'1.2-2' }}
                  </span>
                </div>
              </div>
            </fieldset>

            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy() || f.invalid">
                @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-cash-coin"></i> }
                Create draft
              </button>
              <button type="button" class="btn btn-outline-secondary" (click)="toggleForm()">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    }

    <div class="row g-3 g-md-4">
      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Receipts</h2>
            <span class="badge text-bg-light text-secondary">{{ receipts().length }}</span>
          </div>
          @if (receipts().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cash-coin"></i></div>
              <p class="small text-secondary mb-0">No receipts yet. Record the first one.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 rc-list">
              @for (r of receipts(); track r.id) {
                <li>
                  <button type="button" class="rc-row"
                          [class.is-active]="selected()?.id === r.id"
                          (click)="select(r)">
                    <div class="flex-grow-1 min-w-0">
                      <div class="d-flex align-items-center gap-2 mb-1">
                        <span class="badge text-bg-light border text-secondary font-monospace">{{ r.number }}</span>
                        <span class="status-badge status-badge--{{ r.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ r.status }}
                        </span>
                      </div>
                      <p class="small text-secondary mb-0">
                        Customer #{{ r.customerId }} · {{ r.method }} · {{ r.receiptDate | date:'mediumDate' }}
                      </p>
                    </div>
                    <div class="fw-bold text-dark">{{ r.totalAmount | number:'1.2-2' }}</div>
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      </div>

      <div class="col-12 col-lg-7">
        @if (selected(); as rcp) {
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4">
              <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                <div>
                  <p class="small text-secondary mb-1">Customer #{{ rcp.customerId }} · {{ rcp.receiptDate | date:'mediumDate' }}</p>
                  <h2 class="h4 fw-bold mb-1 text-dark">{{ rcp.number }}</h2>
                  <span class="status-badge status-badge--{{ rcp.status.toLowerCase() }}">
                    <span class="status-badge__dot"></span>{{ rcp.status }}
                  </span>
                </div>
                <div class="d-flex gap-2 flex-wrap">
                  @if (rcp.status === 'DRAFT') {
                    <button class="btn btn-sm btn-primary d-inline-flex align-items-center gap-1"
                            [disabled]="busy()" (click)="post(rcp)">
                      <i class="bi bi-send"></i> Post
                    </button>
                    <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1"
                            [disabled]="busy()" (click)="cancel(rcp)">
                      <i class="bi bi-x-circle"></i> Cancel
                    </button>
                  }
                </div>
              </div>

              <div class="row g-2 totals-row mb-3">
                <div class="col-6 col-md-4">
                  <p class="totals-row__label">Total</p>
                  <p class="totals-row__value totals-row__value--strong">{{ rcp.totalAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-4">
                  <p class="totals-row__label">Allocated</p>
                  <p class="totals-row__value text-success">{{ rcp.allocatedAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-4">
                  <p class="totals-row__label">Unallocated</p>
                  <p class="totals-row__value"
                     [class.text-warning]="rcp.unallocatedAmount > 0">
                    {{ rcp.unallocatedAmount | number:'1.2-2' }}
                  </p>
                </div>
              </div>

              <dl class="row small mb-0">
                <dt class="col-4 text-secondary">Method</dt><dd class="col-8 mb-1">{{ rcp.method }}</dd>
                <dt class="col-4 text-secondary">Reference</dt><dd class="col-8 mb-1">{{ rcp.reference ?? '—' }}</dd>
                <dt class="col-4 text-secondary">Currency</dt><dd class="col-8 mb-1 font-monospace">{{ rcp.currencyCode }}</dd>
              </dl>
            </div>
          </div>

          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Allocations</h3>
              <span class="badge text-bg-light text-secondary">{{ rcp.allocations.length }}</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr><th>Invoice</th><th class="text-end">Amount</th></tr>
                </thead>
                <tbody>
                  @for (a of rcp.allocations; track a.id) {
                    <tr>
                      <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ a.salesInvoiceId }}</span></td>
                      <td class="text-end fw-semibold">{{ a.amount | number:'1.2-2' }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="2" class="text-center text-secondary py-4">No allocations.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick a receipt</h2>
              <p class="small text-secondary mb-0">Or create one and allocate to open invoices.</p>
            </div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .min-w-0 { min-width: 0; }

    .form-fieldset {
      background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 10px; padding: 1rem 1.25rem 1.25rem;
    }
    .form-fieldset__legend {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #374151; padding: 0 0.5rem; width: 100%; margin-bottom: 0.5rem;
    }
    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    .line-table thead th {
      font-size: 0.72rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; border-bottom: 1px solid #e5e7eb; padding: 0.5rem 0.5rem;
    }
    .line-table tbody td { padding: 0.4rem 0.5rem; vertical-align: middle; }
    .line-table .actions-col { width: 1%; white-space: nowrap; }

    .alloc-summary {
      display: flex; gap: 1rem; padding: 0.75rem 1rem; margin-top: 0.75rem;
      background: #fff; border: 1px solid #e5e7eb; border-radius: 8px;
    }
    .alloc-summary__cell { display: flex; flex-direction: column; flex: 1; min-width: 0; }
    .alloc-summary__label {
      font-size: 0.7rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280;
    }
    .alloc-summary__value { font-size: 0.95rem; font-weight: 600; color: #111827; }

    .rc-list { max-height: 70vh; overflow-y: auto; }
    .rc-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .rc-row:hover { background: #f8fafc; }
    .rc-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .rc-row:last-child { border-bottom: none; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.75rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }

    .totals-row .totals-row__label {
      font-size: 0.72rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; margin-bottom: 0.15rem;
    }
    .totals-row .totals-row__value { font-size: 1.05rem; font-weight: 600; color: #111827; margin-bottom: 0; }
    .totals-row .totals-row__value--strong { font-size: 1.4rem; color: #0d2a5b; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--draft     { background: #f3f4f6; color: #4b5563; }
    .status-badge--draft .status-badge__dot     { background: #9ca3af; }
    .status-badge--posted    { background: #d1fae5; color: #047857; }
    .status-badge--posted .status-badge__dot    { background: #10b981; }
    .status-badge--cancelled { background: #fee2e2; color: #b91c1c; }
    .status-badge--cancelled .status-badge__dot { background: #f43f5e; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #d1fae5; color: #047857; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class ReceiptsComponent implements OnInit {
  private readonly sales = inject(SalesService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);
  private readonly currencyService = inject(CurrencyService);

  protected readonly currencies = signal<Currency[]>([]);
  protected readonly currencyOptions = computed<SearchSelectOption[]>(() =>
    this.currencies()
      .filter(c => c.status === 'ACTIVE')
      .map(c => ({ id: c.code, label: `${c.code} · ${c.name}` }))
  );

  protected readonly methods = RECEIPT_METHODS;
  protected readonly receipts = signal<SalesReceipt[]>([]);
  protected readonly selected = signal<SalesReceipt | null>(null);
  protected readonly openInvoices = signal<SalesInvoice[]>([]);
  protected readonly busy = signal<boolean>(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly showForm = signal(false);

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected newNumber = '';
  protected newCustomerId: string | null = null;
  protected newReceiptDate = new Date().toISOString().slice(0, 10);
  protected newMethod: ReceiptMethod = 'CASH';
  protected newReference = '';
  protected newCurrency = 'TZS';
  protected newTotal: number | null = null;
  protected allocations: AllocRow[] = [{ invoiceId: null, amount: null, outstanding: 0 }];

  allocationsSum(): number {
    return this.allocations.reduce((acc, r) => acc + (r.amount ?? 0), 0);
  }

  ngOnInit(): void {
    this.refresh();
    this.currencyService.listCurrencies().subscribe({
      next: list => this.currencies.set(list),
      error: () => this.currencies.set([])
    });
  }

  toggleForm(): void { this.showForm.update(v => !v); }

  refresh(): void {
    this.sales.listReceipts(this.branchId()).subscribe({
      next: rows => this.receipts.set(rows),
      error: err => this.showError(err)
    });
  }

  loadOpenInvoices(): void {
    if (this.newCustomerId === null) { this.openInvoices.set([]); return; }
    this.sales.listInvoices(this.branchId()).subscribe({
      next: rows => this.openInvoices.set(
        rows.filter(i => i.customerId === this.newCustomerId
          && (i.status === 'POSTED' || i.status === 'PARTIALLY_PAID')
          && i.totalAmount - i.paidAmount > 0)
      ),
      error: err => this.showError(err)
    });
  }

  onInvoicePicked(row: AllocRow, invoiceId: string | null): void {
    const inv = this.openInvoices().find(i => i.id === invoiceId);
    row.outstanding = inv ? inv.totalAmount - inv.paidAmount : 0;
    if (row.amount === null && inv) row.amount = row.outstanding;
  }

  addAllocation(): void { this.allocations.push({ invoiceId: null, amount: null, outstanding: 0 }); }
  removeAllocation(i: number): void {
    this.allocations.splice(i, 1);
    if (this.allocations.length === 0) this.addAllocation();
  }

  select(r: SalesReceipt): void { this.selected.set(r); }

  create(): void {
    const branchId = this.branchId();
    if (branchId === null || this.newCustomerId === null || this.newTotal === null) {
      this.error.set('Branch, customer, total required.');
      return;
    }
    const allocs: CreateReceiptAllocation[] = this.allocations
      .filter(r => r.invoiceId !== null && (r.amount ?? 0) > 0)
      .map(r => ({ salesInvoiceId: r.invoiceId as string, amount: r.amount as number }));
    this.run(this.sales.createReceipt({
      number: this.newNumber.trim(),
      branchId,
      customerId: this.newCustomerId,
      receiptDate: this.newReceiptDate,
      method: this.newMethod,
      reference: this.newReference.trim() || null,
      currencyCode: this.newCurrency.trim().toUpperCase(),
      totalAmount: this.newTotal,
      notes: null,
      allocations: allocs
    }), `Receipt ${this.newNumber} created.`);
    this.newNumber = '';
    this.newReference = '';
    this.newTotal = null;
    this.allocations = [{ invoiceId: null, amount: null, outstanding: 0 }];
    this.showForm.set(false);
  }

  post(r: SalesReceipt): void {
    this.run(this.sales.postReceipt(r.id), `Receipt posted.`);
  }

  cancel(r: SalesReceipt): void {
    if (!window.confirm(`Cancel ${r.number}?`)) return;
    this.run(this.sales.cancelReceipt(r.id), `Receipt cancelled.`);
  }

  private run(op: Observable<SalesReceipt>, msg: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    op.subscribe({
      next: r => {
        this.busy.set(false);
        this.info.set(msg);
        this.selected.set(r);
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
