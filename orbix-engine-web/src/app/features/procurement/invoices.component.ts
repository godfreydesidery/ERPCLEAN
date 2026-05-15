import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { ProcurementService } from './procurement.service';
import {
  Grn,
  SupplierInvoice,
  SupplierInvoiceAllocation
} from './procurement.models';

interface AllocRow {
  grnId: number | null;
  amount: number | null;
}

@Component({
  selector: 'orbix-supplier-invoices',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2 class="h3 mb-4">Supplier invoices</h2>
    @if (error()) { <div class="alert alert-danger py-2">{{ error() }}</div> }
    @if (info()) { <div class="alert alert-success py-2">{{ info() }}</div> }

    <div class="row g-4">
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Invoices</div>
          <div class="list-group list-group-flush">
            @for (i of invoices(); track i.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === i.id" (click)="select(i)">
                <span>{{ i.number }}
                  <small class="d-block text-muted">
                    supplier #{{ i.supplierId }} · their no {{ i.supplierInvoiceNo }} ·
                    {{ i.totalAmount | number:'1.0-2' }} · due {{ i.dueDate }}
                  </small>
                </span>
                <span class="badge align-self-center"
                      [class.text-bg-secondary]="i.status === 'DRAFT'"
                      [class.text-bg-success]="i.status === 'POSTED' || i.status === 'PAID'"
                      [class.text-bg-warning]="i.status === 'PARTIALLY_PAID'"
                      [class.text-bg-danger]="i.status === 'CANCELLED'">{{ i.status }}</span>
              </button>
            } @empty { <div class="list-group-item text-muted">No invoices yet.</div> }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New invoice</div>
          <div class="card-body">
            <form (ngSubmit)="create()" #f="ngForm">
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Our number</label>
                  <input class="form-control" name="num" [(ngModel)]="newNumber" required>
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Supplier inv no</label>
                  <input class="form-control" name="sino" [(ngModel)]="newSupplierInvoiceNo" required>
                </div>
              </div>
              <div class="mb-2">
                <label class="form-label small mb-1">Supplier id</label>
                <input class="form-control" type="number" name="sid"
                       [(ngModel)]="newSupplierId" (ngModelChange)="loadGrns()" required>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Invoice date</label>
                  <input class="form-control" type="date" name="id" [(ngModel)]="newInvoiceDate" required>
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Due date (optional)</label>
                  <input class="form-control" type="date" name="dd" [(ngModel)]="newDueDate">
                </div>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Currency</label>
                  <input class="form-control" name="cur" [(ngModel)]="newCurrency" required>
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Subtotal</label>
                  <input class="form-control" type="number" step="0.0001" min="0"
                         name="sub" [(ngModel)]="newSubtotal" required>
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Tax</label>
                  <input class="form-control" type="number" step="0.0001" min="0"
                         name="tax" [(ngModel)]="newTax">
                </div>
              </div>

              <fieldset class="border rounded p-2 mb-2">
                <legend class="float-none w-auto small px-1">Allocations</legend>
                @for (row of allocations; track $index) {
                  <div class="row g-2 mb-2 align-items-end">
                    <div class="col">
                      <label class="form-label small mb-1">GRN</label>
                      <select class="form-select form-select-sm"
                              [name]="'gid' + $index" [(ngModel)]="row.grnId">
                        <option [ngValue]="null">— pick a GRN —</option>
                        @for (g of grnOptions(); track g.id) {
                          <option [ngValue]="g.id">{{ g.number }} ({{ g.totalAmount | number:'1.0-2' }})</option>
                        }
                      </select>
                    </div>
                    <div class="col">
                      <label class="form-label small mb-1">Amount</label>
                      <input class="form-control form-control-sm" type="number" step="0.0001" min="0"
                             [name]="'gam' + $index" [(ngModel)]="row.amount">
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
                <span>Total: <strong>{{ headerTotal() | number:'1.2-2' }}</strong></span>
                <span>Allocated: <strong>{{ allocationsSum() | number:'1.2-2' }}</strong></span>
                <span [class.text-danger]="!withinTolerance()">
                  Diff: {{ headerTotal() - allocationsSum() | number:'1.2-2' }}
                </span>
              </div>

              <button class="btn btn-primary w-100" [disabled]="busy() || f.invalid">
                Create draft invoice
              </button>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        @if (selected(); as inv) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ inv.number }} ({{ inv.supplierInvoiceNo }}) — {{ inv.status }}</span>
              <div class="btn-group">
                @if (inv.status === 'DRAFT') {
                  <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="post(inv)">Post</button>
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busy()" (click)="cancel(inv)">Cancel</button>
                }
                @if (inv.status === 'POSTED') {
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busy()" (click)="cancel(inv)">Cancel</button>
                }
              </div>
            </div>
            <div class="card-body">
              <dl class="row mb-3">
                <dt class="col-sm-3">Supplier</dt><dd class="col-sm-9">#{{ inv.supplierId }}</dd>
                <dt class="col-sm-3">Invoice date</dt><dd class="col-sm-9">{{ inv.invoiceDate }}</dd>
                <dt class="col-sm-3">Due date</dt><dd class="col-sm-9">{{ inv.dueDate }}</dd>
                <dt class="col-sm-3">Currency</dt><dd class="col-sm-9">{{ inv.currencyCode }}</dd>
                <dt class="col-sm-3">Subtotal</dt><dd class="col-sm-9">{{ inv.subtotalAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Tax</dt><dd class="col-sm-9">{{ inv.taxAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Total</dt>
                <dd class="col-sm-9 fw-semibold">{{ inv.totalAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Paid</dt><dd class="col-sm-9">{{ inv.paidAmount | number:'1.2-2' }}</dd>
                @if (inv.postedAt) {
                  <dt class="col-sm-3">Posted</dt>
                  <dd class="col-sm-9">by #{{ inv.postedBy }} at {{ inv.postedAt }}</dd>
                }
              </dl>
              <h5 class="h6">Allocations</h5>
              <table class="table table-sm align-middle">
                <thead><tr><th>GRN id</th><th class="text-end">Amount</th></tr></thead>
                <tbody>
                  @for (a of inv.allocations; track a.grnId) {
                    <tr>
                      <td>#{{ a.grnId }}</td>
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
          <div class="alert alert-secondary">Select an invoice, or create one and match it to its GRNs.</div>
        }
      </div>
    </div>
  `
})
export class InvoicesComponent implements OnInit {
  private readonly procurement = inject(ProcurementService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly invoices = signal<SupplierInvoice[]>([]);
  readonly selected = signal<SupplierInvoice | null>(null);
  readonly grnOptions = signal<Grn[]>([]);
  readonly busy = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  // form state
  newNumber = '';
  newSupplierInvoiceNo = '';
  newSupplierId: number | null = null;
  newInvoiceDate = new Date().toISOString().slice(0, 10);
  newDueDate: string | null = null;
  newCurrency = 'TZS';
  newSubtotal: number | null = null;
  newTax: number | null = 0;
  allocations: AllocRow[] = [{ grnId: null, amount: null }];

  readonly tolerance = 0.005;

  headerTotal(): number {
    return (this.newSubtotal ?? 0) + (this.newTax ?? 0);
  }

  allocationsSum(): number {
    return this.allocations.reduce((acc, row) => acc + (row.amount ?? 0), 0);
  }

  withinTolerance(): boolean {
    const total = this.headerTotal();
    if (total === 0) return this.allocationsSum() === 0;
    return Math.abs(total - this.allocationsSum()) <= Math.abs(total) * this.tolerance;
  }

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.procurement.listSupplierInvoices(this.branchId()).subscribe({
      next: rows => this.invoices.set(rows),
      error: err => this.showError(err)
    });
  }

  loadGrns(): void {
    if (this.newSupplierId === null) {
      this.grnOptions.set([]);
      return;
    }
    this.procurement.listGrns(this.branchId()).subscribe({
      next: rows => this.grnOptions.set(
        rows.filter(g => g.supplierId === this.newSupplierId && g.status === 'POSTED')
      ),
      error: err => this.showError(err)
    });
  }

  addAllocation(): void {
    this.allocations.push({ grnId: null, amount: null });
  }

  removeAllocation(index: number): void {
    this.allocations.splice(index, 1);
    if (this.allocations.length === 0) {
      this.allocations.push({ grnId: null, amount: null });
    }
  }

  select(invoice: SupplierInvoice): void {
    this.selected.set(invoice);
  }

  create(): void {
    const branchId = this.branchId();
    if (branchId === null || this.newSupplierId === null
        || this.newSubtotal === null) {
      this.error.set('Branch, supplier, and subtotal are required.');
      return;
    }
    const allocs: SupplierInvoiceAllocation[] = this.allocations
      .filter(r => r.grnId !== null && (r.amount ?? 0) > 0)
      .map(r => ({ grnId: r.grnId as number, amount: r.amount as number }));
    if (allocs.length === 0) {
      this.error.set('Add at least one allocation with grn + amount.');
      return;
    }
    this.run(this.procurement.createSupplierInvoice({
      number: this.newNumber.trim(),
      supplierInvoiceNo: this.newSupplierInvoiceNo.trim(),
      branchId,
      supplierId: this.newSupplierId,
      invoiceDate: this.newInvoiceDate,
      dueDate: this.newDueDate,
      currencyCode: this.newCurrency.trim().toUpperCase(),
      subtotalAmount: this.newSubtotal,
      taxAmount: this.newTax ?? 0,
      notes: null,
      allocations: allocs
    }), `Invoice ${this.newNumber} created.`);
    this.newNumber = '';
    this.newSupplierInvoiceNo = '';
    this.newSubtotal = null;
    this.newTax = 0;
    this.allocations = [{ grnId: null, amount: null }];
  }

  post(inv: SupplierInvoice): void {
    this.run(this.procurement.postSupplierInvoice(inv.id), `Invoice posted.`);
  }

  cancel(inv: SupplierInvoice): void {
    if (!window.confirm(`Cancel ${inv.number}?`)) return;
    this.run(this.procurement.cancelSupplierInvoice(inv.id), `Invoice cancelled.`);
  }

  private run(op: import('rxjs').Observable<SupplierInvoice>, successMessage: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    op.subscribe({
      next: inv => {
        this.busy.set(false);
        this.info.set(successMessage);
        this.selected.set(inv);
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
