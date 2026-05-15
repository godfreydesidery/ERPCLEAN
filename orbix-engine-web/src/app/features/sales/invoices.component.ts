import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { SalesService } from './sales.service';
import {
  CreateSalesInvoiceLine,
  PAYMENT_TERMS,
  PaymentTerms,
  SalesInvoice
} from './sales.models';

interface LineRow {
  itemId: number | null;
  qty: number | null;
  unitPrice: number | null;
  discountPct: number | null;
}

@Component({
  selector: 'orbix-sales-invoices',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2 class="h3 mb-4">Sales invoices</h2>
    @if (error()) { <div class="alert alert-danger py-2">{{ error() }}</div> }
    @if (info()) { <div class="alert alert-success py-2">{{ info() }}</div> }

    <div class="row g-4">
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Invoices</div>
          <div class="list-group list-group-flush">
            @for (s of invoices(); track s.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === s.id" (click)="select(s)">
                <span>{{ s.number }}
                  <small class="d-block text-muted">
                    cust #{{ s.customerId }} · {{ s.totalAmount | number:'1.0-2' }} ·
                    {{ s.paymentTerms }} · {{ s.invoiceDate }}
                  </small>
                </span>
                <span class="badge align-self-center"
                      [class.text-bg-secondary]="s.status === 'DRAFT'"
                      [class.text-bg-success]="s.status === 'POSTED' || s.status === 'PAID'"
                      [class.text-bg-warning]="s.status === 'PARTIALLY_PAID'"
                      [class.text-bg-danger]="s.status === 'VOIDED' || s.status === 'CANCELLED'">{{ s.status }}</span>
              </button>
            } @empty { <div class="list-group-item text-muted">No invoices yet.</div> }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New invoice</div>
          <div class="card-body">
            <form (ngSubmit)="create()" #f="ngForm">
              <div class="mb-2">
                <label class="form-label small mb-1">Number</label>
                <input class="form-control" name="num" [(ngModel)]="newNumber" required>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Customer id</label>
                  <input class="form-control" type="number" name="cid" [(ngModel)]="newCustomerId" required>
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Sales agent id (optional)</label>
                  <input class="form-control" type="number" name="aid" [(ngModel)]="newSalesAgentId">
                </div>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Invoice date</label>
                  <input class="form-control" type="date" name="id" [(ngModel)]="newInvoiceDate" required>
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Due date</label>
                  <input class="form-control" type="date" name="dd" [(ngModel)]="newDueDate">
                </div>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Terms</label>
                  <select class="form-select" name="pt" [(ngModel)]="newPaymentTerms" required>
                    @for (t of paymentTerms; track t) { <option [ngValue]="t">{{ t }}</option> }
                  </select>
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Currency</label>
                  <input class="form-control" name="cur" [(ngModel)]="newCurrency" required>
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Price list id</label>
                  <input class="form-control" type="number" name="pl" [(ngModel)]="newPriceListId" required>
                </div>
              </div>
              <div class="mb-2">
                <label class="form-label small mb-1">Discount approver id (if &gt; 10%)</label>
                <input class="form-control" type="number" name="da" [(ngModel)]="newDiscountApproverId">
              </div>

              <fieldset class="border rounded p-2 mb-2">
                <legend class="float-none w-auto small px-1">Lines</legend>
                @for (row of newLines; track $index) {
                  <div class="row g-2 mb-2 align-items-end">
                    <div class="col">
                      <label class="form-label small mb-1">Item id</label>
                      <input class="form-control form-control-sm" type="number"
                             [name]="'lid' + $index" [(ngModel)]="row.itemId">
                    </div>
                    <div class="col">
                      <label class="form-label small mb-1">Qty</label>
                      <input class="form-control form-control-sm" type="number" step="0.0001" min="0"
                             [name]="'lq' + $index" [(ngModel)]="row.qty">
                    </div>
                    <div class="col">
                      <label class="form-label small mb-1">Price</label>
                      <input class="form-control form-control-sm" type="number" step="0.0001" min="0"
                             [name]="'lp' + $index" [(ngModel)]="row.unitPrice">
                    </div>
                    <div class="col">
                      <label class="form-label small mb-1">Disc %</label>
                      <input class="form-control form-control-sm" type="number" step="0.01" min="0" max="100"
                             [name]="'ld' + $index" [(ngModel)]="row.discountPct">
                    </div>
                    <div class="col-auto">
                      <button type="button" class="btn btn-sm btn-outline-secondary"
                              (click)="removeLine($index)">Remove</button>
                    </div>
                  </div>
                }
                <button type="button" class="btn btn-sm btn-outline-primary" (click)="addLine()">
                  + Add line
                </button>
              </fieldset>

              <button class="btn btn-primary w-100" [disabled]="busy() || f.invalid">
                Create draft
              </button>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        @if (selected(); as inv) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ inv.number }} — {{ inv.status }}</span>
              <div class="btn-group">
                @if (inv.status === 'DRAFT') {
                  <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="post(inv)">Post</button>
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busy()" (click)="cancel(inv)">Cancel</button>
                }
                @if (inv.status === 'POSTED') {
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busy()" (click)="voidIt(inv)">Void</button>
                }
              </div>
            </div>
            <div class="card-body">
              <dl class="row mb-3">
                <dt class="col-sm-3">Customer</dt><dd class="col-sm-9">#{{ inv.customerId }}</dd>
                <dt class="col-sm-3">Date</dt><dd class="col-sm-9">{{ inv.invoiceDate }}</dd>
                <dt class="col-sm-3">Terms</dt><dd class="col-sm-9">{{ inv.paymentTerms }}</dd>
                <dt class="col-sm-3">Due</dt><dd class="col-sm-9">{{ inv.dueDate ?? '—' }}</dd>
                <dt class="col-sm-3">Currency</dt><dd class="col-sm-9">{{ inv.currencyCode }}</dd>
                <dt class="col-sm-3">Subtotal</dt><dd class="col-sm-9">{{ inv.subtotalAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Tax</dt><dd class="col-sm-9">{{ inv.taxAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Total</dt><dd class="col-sm-9 fw-semibold">{{ inv.totalAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Paid</dt><dd class="col-sm-9">{{ inv.paidAmount | number:'1.2-2' }}</dd>
                @if (inv.postedAt) {
                  <dt class="col-sm-3">Posted</dt>
                  <dd class="col-sm-9">by #{{ inv.postedBy }} at {{ inv.postedAt }} (business day {{ inv.postedBusinessDate }})</dd>
                }
                @if (inv.voidedAt) {
                  <dt class="col-sm-3">Voided</dt>
                  <dd class="col-sm-9">by #{{ inv.voidedBy }} — {{ inv.voidReason }}</dd>
                }
              </dl>
              <table class="table table-sm align-middle">
                <thead><tr><th>#</th><th>Item</th><th class="text-end">Qty</th><th class="text-end">Price</th>
                           <th class="text-end">Disc%</th><th class="text-end">Tax</th>
                           <th class="text-end">Line total</th><th class="text-end">Cost</th></tr></thead>
                <tbody>
                  @for (line of inv.lines; track line.id) {
                    <tr>
                      <td>{{ line.lineNo }}</td>
                      <td>{{ line.itemId }}</td>
                      <td class="text-end">{{ line.qty }}</td>
                      <td class="text-end">{{ line.unitPrice | number:'1.2-2' }}</td>
                      <td class="text-end">{{ line.discountPct | number:'1.0-2' }}</td>
                      <td class="text-end">{{ line.taxAmount | number:'1.2-2' }}</td>
                      <td class="text-end">{{ line.lineTotal | number:'1.2-2' }}</td>
                      <td class="text-end">{{ line.costAmount | number:'1.2-2' }}</td>
                    </tr>
                  } @empty { <tr><td colspan="8" class="text-muted">No lines.</td></tr> }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="alert alert-secondary">Select an invoice, or draft a new one.</div>
        }
      </div>
    </div>
  `
})
export class InvoicesComponent implements OnInit {
  private readonly sales = inject(SalesService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly paymentTerms = PAYMENT_TERMS;
  readonly invoices = signal<SalesInvoice[]>([]);
  readonly selected = signal<SalesInvoice | null>(null);
  readonly busy = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  newNumber = '';
  newCustomerId: number | null = null;
  newSalesAgentId: number | null = null;
  newInvoiceDate = new Date().toISOString().slice(0, 10);
  newDueDate: string | null = null;
  newPaymentTerms: PaymentTerms = 'CASH';
  newCurrency = 'TZS';
  newPriceListId: number | null = null;
  newDiscountApproverId: number | null = null;
  newLines: LineRow[] = [{ itemId: null, qty: null, unitPrice: null, discountPct: null }];

  ngOnInit(): void { this.refresh(); }

  refresh(): void {
    this.sales.listInvoices(this.branchId()).subscribe({
      next: rows => this.invoices.set(rows),
      error: err => this.showError(err)
    });
  }

  select(invoice: SalesInvoice): void { this.selected.set(invoice); }
  addLine(): void { this.newLines.push({ itemId: null, qty: null, unitPrice: null, discountPct: null }); }
  removeLine(i: number): void {
    this.newLines.splice(i, 1);
    if (this.newLines.length === 0) this.addLine();
  }

  create(): void {
    const branchId = this.branchId();
    if (branchId === null || this.newCustomerId === null || this.newPriceListId === null) {
      this.error.set('Branch, customer, and price list are required.');
      return;
    }
    const lines: CreateSalesInvoiceLine[] = this.newLines
      .filter(r => r.itemId !== null && (r.qty ?? 0) > 0 && (r.unitPrice ?? 0) >= 0)
      .map(r => ({
        itemId: r.itemId as number,
        uomId: null,
        qty: r.qty as number,
        unitPrice: r.unitPrice as number,
        discountPct: r.discountPct,
        vatGroupId: null
      }));
    if (lines.length === 0) {
      this.error.set('Add at least one line.');
      return;
    }
    this.run(this.sales.createInvoice({
      number: this.newNumber.trim(),
      branchId,
      customerId: this.newCustomerId,
      salesAgentId: this.newSalesAgentId,
      invoiceDate: this.newInvoiceDate,
      dueDate: this.newDueDate,
      paymentTerms: this.newPaymentTerms,
      currencyCode: this.newCurrency.trim().toUpperCase(),
      priceListId: this.newPriceListId,
      discountApproverId: this.newDiscountApproverId,
      reference: null,
      notes: null,
      lines
    }), `Invoice ${this.newNumber} created.`);
    this.newNumber = '';
    this.newLines = [{ itemId: null, qty: null, unitPrice: null, discountPct: null }];
  }

  post(inv: SalesInvoice): void {
    this.run(this.sales.postInvoice(inv.id), `Invoice posted.`);
  }

  voidIt(inv: SalesInvoice): void {
    const reason = window.prompt(`Void ${inv.number} — reason?`);
    if (!reason || !reason.trim()) return;
    this.run(this.sales.voidInvoice(inv.id, { reason: reason.trim() }), `Invoice voided.`);
  }

  cancel(inv: SalesInvoice): void {
    if (!window.confirm(`Cancel ${inv.number}?`)) return;
    this.run(this.sales.cancelInvoice(inv.id), `Invoice cancelled.`);
  }

  private run(op: import('rxjs').Observable<SalesInvoice>, msg: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    op.subscribe({
      next: inv => {
        this.busy.set(false);
        this.info.set(msg);
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
