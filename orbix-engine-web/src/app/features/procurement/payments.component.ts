import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { ProcurementService } from './procurement.service';
import {
  CreateSupplierPaymentAllocation,
  PAYMENT_METHODS,
  PaymentMethod,
  SupplierInvoice,
  SupplierPayment
} from './procurement.models';

interface AllocRow {
  invoiceId: number | null;
  amount: number | null;
  outstanding: number;
}

@Component({
  selector: 'orbix-supplier-payments',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2 class="h3 mb-4">Supplier payments</h2>
    @if (error()) { <div class="alert alert-danger py-2">{{ error() }}</div> }
    @if (info()) { <div class="alert alert-success py-2">{{ info() }}</div> }

    <div class="row g-4">
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Payments</div>
          <div class="list-group list-group-flush">
            @for (p of payments(); track p.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === p.id" (click)="select(p)">
                <span>{{ p.number }}
                  <small class="d-block text-muted">
                    supplier #{{ p.supplierId }} · {{ p.totalAmount | number:'1.0-2' }} ·
                    {{ p.method }} · {{ p.paymentDate }}
                  </small>
                </span>
                <span class="badge align-self-center"
                      [class.text-bg-secondary]="p.status === 'DRAFT'"
                      [class.text-bg-success]="p.status === 'POSTED'"
                      [class.text-bg-danger]="p.status === 'CANCELLED'">{{ p.status }}</span>
              </button>
            } @empty { <div class="list-group-item text-muted">No payments yet.</div> }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New payment</div>
          <div class="card-body">
            <form (ngSubmit)="create()" #f="ngForm">
              <div class="mb-2">
                <label class="form-label small mb-1">Our number</label>
                <input class="form-control" name="num" [(ngModel)]="newNumber" required>
              </div>
              <div class="mb-2">
                <label class="form-label small mb-1">Supplier id</label>
                <input class="form-control" type="number" name="sid"
                       [(ngModel)]="newSupplierId" (ngModelChange)="loadOpenInvoices()" required>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Payment date</label>
                  <input class="form-control" type="date" name="pd" [(ngModel)]="newPaymentDate" required>
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
                  <label class="form-label small mb-1">Reference (optional)</label>
                  <input class="form-control" name="ref" [(ngModel)]="newReference">
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Currency</label>
                  <input class="form-control" name="cur" [(ngModel)]="newCurrency" required>
                </div>
              </div>
              <div class="mb-2">
                <label class="form-label small mb-1">Total amount</label>
                <input class="form-control" type="number" step="0.0001" min="0.0001"
                       name="tot" [(ngModel)]="newTotal" required>
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
                        <option [ngValue]="null">— pick an invoice —</option>
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
                      @if (row.outstanding > 0 && (row.amount ?? 0) > row.outstanding) {
                        <small class="text-danger">Exceeds outstanding {{ row.outstanding }}</small>
                      }
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
                <span [class.text-danger]="(newTotal ?? 0) < allocationsSum()">
                  Remaining: {{ (newTotal ?? 0) - allocationsSum() | number:'1.2-2' }}
                </span>
              </div>

              <button class="btn btn-primary w-100" [disabled]="busy() || f.invalid">
                Create draft payment
              </button>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        @if (selected(); as pay) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ pay.number }} — {{ pay.status }}</span>
              <div class="btn-group">
                @if (pay.status === 'DRAFT') {
                  <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="post(pay)">Post</button>
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busy()" (click)="cancel(pay)">Cancel</button>
                }
              </div>
            </div>
            <div class="card-body">
              <dl class="row mb-3">
                <dt class="col-sm-3">Supplier</dt><dd class="col-sm-9">#{{ pay.supplierId }}</dd>
                <dt class="col-sm-3">Date</dt><dd class="col-sm-9">{{ pay.paymentDate }}</dd>
                <dt class="col-sm-3">Method</dt><dd class="col-sm-9">{{ pay.method }}</dd>
                <dt class="col-sm-3">Reference</dt><dd class="col-sm-9">{{ pay.reference ?? '—' }}</dd>
                <dt class="col-sm-3">Currency</dt><dd class="col-sm-9">{{ pay.currencyCode }}</dd>
                <dt class="col-sm-3">Total</dt>
                <dd class="col-sm-9 fw-semibold">{{ pay.totalAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Allocated</dt>
                <dd class="col-sm-9">{{ pay.allocatedAmount | number:'1.2-2' }}</dd>
                @if (pay.postedAt) {
                  <dt class="col-sm-3">Posted</dt>
                  <dd class="col-sm-9">by #{{ pay.postedBy }} at {{ pay.postedAt }}</dd>
                }
              </dl>
              <h5 class="h6">Allocations</h5>
              <table class="table table-sm align-middle">
                <thead><tr><th>Invoice id</th><th class="text-end">Amount</th></tr></thead>
                <tbody>
                  @for (a of pay.allocations; track a.id) {
                    <tr>
                      <td>#{{ a.supplierInvoiceId }}</td>
                      <td class="text-end">{{ a.amount | number:'1.2-2' }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="2" class="text-muted">No allocations.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="alert alert-secondary">Select a payment, or create one and allocate it to open invoices.</div>
        }
      </div>
    </div>
  `
})
export class PaymentsComponent implements OnInit {
  private readonly procurement = inject(ProcurementService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly methods = PAYMENT_METHODS;

  readonly payments = signal<SupplierPayment[]>([]);
  readonly selected = signal<SupplierPayment | null>(null);
  readonly openInvoices = signal<SupplierInvoice[]>([]);
  readonly busy = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  // form state
  newNumber = '';
  newSupplierId: number | null = null;
  newPaymentDate = new Date().toISOString().slice(0, 10);
  newMethod: PaymentMethod = 'BANK_TRANSFER';
  newReference = '';
  newCurrency = 'TZS';
  newTotal: number | null = null;
  allocations: AllocRow[] = [{ invoiceId: null, amount: null, outstanding: 0 }];

  allocationsSum(): number {
    return this.allocations.reduce((acc, row) => acc + (row.amount ?? 0), 0);
  }

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.procurement.listSupplierPayments(this.branchId()).subscribe({
      next: rows => this.payments.set(rows),
      error: err => this.showError(err)
    });
  }

  loadOpenInvoices(): void {
    if (this.newSupplierId === null) {
      this.openInvoices.set([]);
      return;
    }
    this.procurement.listSupplierInvoices(this.branchId()).subscribe({
      next: rows => this.openInvoices.set(
        rows.filter(i =>
          i.supplierId === this.newSupplierId
          && (i.status === 'POSTED' || i.status === 'PARTIALLY_PAID')
          && i.totalAmount - i.paidAmount > 0
        )
      ),
      error: err => this.showError(err)
    });
  }

  onInvoicePicked(row: AllocRow, invoiceId: number | null): void {
    const inv = this.openInvoices().find(i => i.id === invoiceId);
    row.outstanding = inv ? inv.totalAmount - inv.paidAmount : 0;
    if (row.amount === null && inv) {
      row.amount = row.outstanding;
    }
  }

  addAllocation(): void {
    this.allocations.push({ invoiceId: null, amount: null, outstanding: 0 });
  }

  removeAllocation(index: number): void {
    this.allocations.splice(index, 1);
    if (this.allocations.length === 0) {
      this.allocations.push({ invoiceId: null, amount: null, outstanding: 0 });
    }
  }

  select(p: SupplierPayment): void {
    this.selected.set(p);
  }

  create(): void {
    const branchId = this.branchId();
    if (branchId === null || this.newSupplierId === null || this.newTotal === null) {
      this.error.set('Branch, supplier, and total amount are required.');
      return;
    }
    const allocs: CreateSupplierPaymentAllocation[] = this.allocations
      .filter(r => r.invoiceId !== null && (r.amount ?? 0) > 0)
      .map(r => ({ supplierInvoiceId: r.invoiceId as number, amount: r.amount as number }));
    if (allocs.length === 0) {
      this.error.set('Add at least one allocation with invoice + amount.');
      return;
    }
    this.run(this.procurement.createSupplierPayment({
      number: this.newNumber.trim(),
      branchId,
      supplierId: this.newSupplierId,
      paymentDate: this.newPaymentDate,
      method: this.newMethod,
      reference: this.newReference.trim() || null,
      currencyCode: this.newCurrency.trim().toUpperCase(),
      totalAmount: this.newTotal,
      notes: null,
      allocations: allocs
    }), `Payment ${this.newNumber} created.`);
    this.newNumber = '';
    this.newReference = '';
    this.newTotal = null;
    this.allocations = [{ invoiceId: null, amount: null, outstanding: 0 }];
  }

  post(p: SupplierPayment): void {
    this.run(this.procurement.postSupplierPayment(p.id), `Payment posted.`);
  }

  cancel(p: SupplierPayment): void {
    if (!window.confirm(`Cancel ${p.number}?`)) return;
    this.run(this.procurement.cancelSupplierPayment(p.id), `Payment cancelled.`);
  }

  private run(op: import('rxjs').Observable<SupplierPayment>, successMessage: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    op.subscribe({
      next: p => {
        this.busy.set(false);
        this.info.set(successMessage);
        this.selected.set(p);
        this.refresh();
      },
      error: err => {
        this.busy.set(false);
        this.showError(err);
      }
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
