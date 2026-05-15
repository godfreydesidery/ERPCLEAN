import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { SalesService } from './sales.service';
import {
  CreateReceiptAllocation,
  RECEIPT_METHODS,
  ReceiptMethod,
  SalesInvoice,
  SalesReceipt
} from './sales.models';

interface AllocRow { invoiceId: number | null; amount: number | null; outstanding: number; }

@Component({
  selector: 'orbix-sales-receipts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2 class="h3 mb-4">Sales receipts</h2>
    @if (error()) { <div class="alert alert-danger py-2">{{ error() }}</div> }
    @if (info()) { <div class="alert alert-success py-2">{{ info() }}</div> }

    <div class="row g-4">
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Receipts</div>
          <div class="list-group list-group-flush">
            @for (r of receipts(); track r.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === r.id" (click)="select(r)">
                <span>{{ r.number }}
                  <small class="d-block text-muted">
                    cust #{{ r.customerId }} · {{ r.totalAmount | number:'1.0-2' }} · {{ r.method }}
                  </small>
                </span>
                <span class="badge align-self-center"
                      [class.text-bg-secondary]="r.status === 'DRAFT'"
                      [class.text-bg-success]="r.status === 'POSTED'"
                      [class.text-bg-danger]="r.status === 'CANCELLED'">{{ r.status }}</span>
              </button>
            } @empty { <div class="list-group-item text-muted">No receipts yet.</div> }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New receipt</div>
          <div class="card-body">
            <form (ngSubmit)="create()" #f="ngForm">
              <div class="mb-2">
                <label class="form-label small mb-1">Number</label>
                <input class="form-control" name="num" [(ngModel)]="newNumber" required>
              </div>
              <div class="mb-2">
                <label class="form-label small mb-1">Customer id</label>
                <input class="form-control" type="number" name="cid"
                       [(ngModel)]="newCustomerId" (ngModelChange)="loadOpenInvoices()" required>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Date</label>
                  <input class="form-control" type="date" name="rd" [(ngModel)]="newReceiptDate" required>
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Method</label>
                  <select class="form-select" name="m" [(ngModel)]="newMethod" required>
                    @for (m of methods; track m) { <option [ngValue]="m">{{ m }}</option> }
                  </select>
                </div>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Reference</label>
                  <input class="form-control" name="ref" [(ngModel)]="newReference">
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Currency</label>
                  <input class="form-control" name="cur" [(ngModel)]="newCurrency" required>
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Total</label>
                  <input class="form-control" type="number" step="0.0001" min="0.0001"
                         name="tot" [(ngModel)]="newTotal" required>
                </div>
              </div>

              <fieldset class="border rounded p-2 mb-2">
                <legend class="float-none w-auto small px-1">Allocate to invoices</legend>
                @for (row of allocations; track $index) {
                  <div class="row g-2 mb-2 align-items-end">
                    <div class="col">
                      <label class="form-label small mb-1">Invoice</label>
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
                    </div>
                    <div class="col">
                      <label class="form-label small mb-1">Amount</label>
                      <input class="form-control form-control-sm" type="number" step="0.0001" min="0"
                             [name]="'iam' + $index" [(ngModel)]="row.amount">
                    </div>
                    <div class="col-auto">
                      <button type="button" class="btn btn-sm btn-outline-secondary"
                              (click)="removeAllocation($index)">Remove</button>
                    </div>
                  </div>
                }
                <button type="button" class="btn btn-sm btn-outline-primary"
                        (click)="addAllocation()">+ Add allocation</button>
              </fieldset>

              <div class="d-flex justify-content-between small text-muted mb-2">
                <span>Total: <strong>{{ newTotal ?? 0 | number:'1.2-2' }}</strong></span>
                <span>Allocated: <strong>{{ allocationsSum() | number:'1.2-2' }}</strong></span>
                <span>Unallocated: {{ (newTotal ?? 0) - allocationsSum() | number:'1.2-2' }}</span>
              </div>

              <button class="btn btn-primary w-100" [disabled]="busy() || f.invalid">
                Create draft
              </button>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        @if (selected(); as rcp) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ rcp.number }} — {{ rcp.status }}</span>
              <div class="btn-group">
                @if (rcp.status === 'DRAFT') {
                  <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="post(rcp)">Post</button>
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busy()" (click)="cancel(rcp)">Cancel</button>
                }
              </div>
            </div>
            <div class="card-body">
              <dl class="row mb-3">
                <dt class="col-sm-3">Customer</dt><dd class="col-sm-9">#{{ rcp.customerId }}</dd>
                <dt class="col-sm-3">Date</dt><dd class="col-sm-9">{{ rcp.receiptDate }}</dd>
                <dt class="col-sm-3">Method</dt><dd class="col-sm-9">{{ rcp.method }}</dd>
                <dt class="col-sm-3">Reference</dt><dd class="col-sm-9">{{ rcp.reference ?? '—' }}</dd>
                <dt class="col-sm-3">Currency</dt><dd class="col-sm-9">{{ rcp.currencyCode }}</dd>
                <dt class="col-sm-3">Total</dt><dd class="col-sm-9 fw-semibold">{{ rcp.totalAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Allocated</dt><dd class="col-sm-9">{{ rcp.allocatedAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Unallocated</dt><dd class="col-sm-9">{{ rcp.unallocatedAmount | number:'1.2-2' }}</dd>
              </dl>
              <h5 class="h6">Allocations</h5>
              <table class="table table-sm align-middle">
                <thead><tr><th>Invoice id</th><th class="text-end">Amount</th></tr></thead>
                <tbody>
                  @for (a of rcp.allocations; track a.id) {
                    <tr><td>#{{ a.salesInvoiceId }}</td><td class="text-end">{{ a.amount | number:'1.2-2' }}</td></tr>
                  } @empty {
                    <tr><td colspan="2" class="text-muted">No allocations.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="alert alert-secondary">Select a receipt, or create one and allocate to open invoices.</div>
        }
      </div>
    </div>
  `
})
export class ReceiptsComponent implements OnInit {
  private readonly sales = inject(SalesService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly methods = RECEIPT_METHODS;
  readonly receipts = signal<SalesReceipt[]>([]);
  readonly selected = signal<SalesReceipt | null>(null);
  readonly openInvoices = signal<SalesInvoice[]>([]);
  readonly busy = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  newNumber = '';
  newCustomerId: number | null = null;
  newReceiptDate = new Date().toISOString().slice(0, 10);
  newMethod: ReceiptMethod = 'CASH';
  newReference = '';
  newCurrency = 'TZS';
  newTotal: number | null = null;
  allocations: AllocRow[] = [{ invoiceId: null, amount: null, outstanding: 0 }];

  allocationsSum(): number {
    return this.allocations.reduce((acc, r) => acc + (r.amount ?? 0), 0);
  }

  ngOnInit(): void { this.refresh(); }
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

  onInvoicePicked(row: AllocRow, invoiceId: number | null): void {
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
      .map(r => ({ salesInvoiceId: r.invoiceId as number, amount: r.amount as number }));
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
  }

  post(r: SalesReceipt): void {
    this.run(this.sales.postReceipt(r.id), `Receipt posted.`);
  }

  cancel(r: SalesReceipt): void {
    if (!window.confirm(`Cancel ${r.number}?`)) return;
    this.run(this.sales.cancelReceipt(r.id), `Receipt cancelled.`);
  }

  private run(op: import('rxjs').Observable<SalesReceipt>, msg: string): void {
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
