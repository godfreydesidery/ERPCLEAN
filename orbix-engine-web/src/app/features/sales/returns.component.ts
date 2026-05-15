import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { SalesService } from './sales.service';
import {
  CreateCustomerReturnLine,
  CustomerReturn,
  RETURN_REASONS,
  ReturnReason
} from './sales.models';

interface LineRow {
  itemId: number | null;
  qty: number | null;
  unitPrice: number | null;
}

@Component({
  selector: 'orbix-customer-returns',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2 class="h3 mb-4">Customer returns</h2>
    @if (error()) { <div class="alert alert-danger py-2">{{ error() }}</div> }
    @if (info()) { <div class="alert alert-success py-2">{{ info() }}</div> }

    <div class="row g-4">
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Returns</div>
          <div class="list-group list-group-flush">
            @for (r of returns(); track r.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === r.id" (click)="select(r)">
                <span>{{ r.number }}
                  <small class="d-block text-muted">
                    cust #{{ r.customerId }} · {{ r.totalAmount | number:'1.0-2' }} ·
                    {{ r.reason }} · {{ r.restock ? 'restock' : 'damage' }}
                  </small>
                </span>
                <span class="badge align-self-center"
                      [class.text-bg-secondary]="r.status === 'DRAFT'"
                      [class.text-bg-info]="r.status === 'POSTED'"
                      [class.text-bg-success]="r.status === 'CREDITED'"
                      [class.text-bg-danger]="r.status === 'CANCELLED'">{{ r.status }}</span>
              </button>
            } @empty { <div class="list-group-item text-muted">No returns yet.</div> }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New return</div>
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
                  <label class="form-label small mb-1">Original invoice (optional)</label>
                  <input class="form-control" type="number" name="oi" [(ngModel)]="newOriginalInvoiceId">
                </div>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Return date</label>
                  <input class="form-control" type="date" name="rd" [(ngModel)]="newReturnDate" required>
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Reason</label>
                  <select class="form-select" name="rr" [(ngModel)]="newReason" required>
                    @for (r of reasons; track r) { <option [ngValue]="r">{{ r }}</option> }
                  </select>
                </div>
              </div>
              <div class="form-check mb-2">
                <input class="form-check-input" type="checkbox" id="restock" [(ngModel)]="newRestock"
                       name="restock">
                <label class="form-check-label small" for="restock">
                  Restock (otherwise posts as DAMAGE)
                </label>
              </div>

              <fieldset class="border rounded p-2 mb-2">
                <legend class="float-none w-auto small px-1">Lines</legend>
                @for (row of newLines; track $index) {
                  <div class="row g-2 mb-2 align-items-end">
                    <div class="col">
                      <label class="form-label small mb-1">Item id</label>
                      <input class="form-control form-control-sm" type="number"
                             [name]="'li' + $index" [(ngModel)]="row.itemId">
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

              <button class="btn btn-primary w-100" [disabled]="busy() || f.invalid">Create draft</button>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        @if (selected(); as ret) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ ret.number }} — {{ ret.status }}</span>
              <div class="btn-group">
                @if (ret.status === 'DRAFT') {
                  <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="post(ret)">Post</button>
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busy()" (click)="cancel(ret)">Cancel</button>
                }
                @if (ret.status === 'POSTED') {
                  <button class="btn btn-sm btn-success" [disabled]="busy()" (click)="issueCreditNote(ret)">
                    Issue credit note
                  </button>
                }
              </div>
            </div>
            <div class="card-body">
              <dl class="row mb-3">
                <dt class="col-sm-3">Customer</dt><dd class="col-sm-9">#{{ ret.customerId }}</dd>
                <dt class="col-sm-3">Original invoice</dt>
                <dd class="col-sm-9">{{ ret.originalInvoiceId ? '#' + ret.originalInvoiceId : '—' }}</dd>
                <dt class="col-sm-3">Date</dt><dd class="col-sm-9">{{ ret.returnDate }}</dd>
                <dt class="col-sm-3">Reason</dt><dd class="col-sm-9">{{ ret.reason }}</dd>
                <dt class="col-sm-3">Restock</dt><dd class="col-sm-9">{{ ret.restock ? 'Yes' : 'No (damage write-off)' }}</dd>
                <dt class="col-sm-3">Total</dt><dd class="col-sm-9 fw-semibold">{{ ret.totalAmount | number:'1.2-2' }}</dd>
              </dl>
              <table class="table table-sm align-middle">
                <thead><tr><th>#</th><th>Item</th><th class="text-end">Qty</th>
                           <th class="text-end">Price</th><th class="text-end">Tax</th>
                           <th class="text-end">Line total</th></tr></thead>
                <tbody>
                  @for (line of ret.lines; track line.id) {
                    <tr>
                      <td>{{ line.lineNo }}</td>
                      <td>{{ line.itemId }}</td>
                      <td class="text-end">{{ line.returnedQty }}</td>
                      <td class="text-end">{{ line.unitPrice | number:'1.2-2' }}</td>
                      <td class="text-end">{{ line.taxAmount | number:'1.2-2' }}</td>
                      <td class="text-end">{{ line.lineTotal | number:'1.2-2' }}</td>
                    </tr>
                  } @empty { <tr><td colspan="6" class="text-muted">No lines.</td></tr> }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="alert alert-secondary">Select a return, or draft a new one.</div>
        }
      </div>
    </div>
  `
})
export class ReturnsComponent implements OnInit {
  private readonly sales = inject(SalesService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly reasons = RETURN_REASONS;
  readonly returns = signal<CustomerReturn[]>([]);
  readonly selected = signal<CustomerReturn | null>(null);
  readonly busy = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  newNumber = '';
  newCustomerId: number | null = null;
  newOriginalInvoiceId: number | null = null;
  newReturnDate = new Date().toISOString().slice(0, 10);
  newReason: ReturnReason = 'DAMAGED';
  newRestock = true;
  newLines: LineRow[] = [{ itemId: null, qty: null, unitPrice: null }];

  ngOnInit(): void { this.refresh(); }
  refresh(): void {
    this.sales.listReturns(this.branchId()).subscribe({
      next: rows => this.returns.set(rows),
      error: err => this.showError(err)
    });
  }
  select(r: CustomerReturn): void { this.selected.set(r); }
  addLine(): void { this.newLines.push({ itemId: null, qty: null, unitPrice: null }); }
  removeLine(i: number): void {
    this.newLines.splice(i, 1);
    if (this.newLines.length === 0) this.addLine();
  }

  create(): void {
    const branchId = this.branchId();
    if (branchId === null || this.newCustomerId === null) {
      this.error.set('Branch and customer required.');
      return;
    }
    const lines: CreateCustomerReturnLine[] = this.newLines
      .filter(r => r.itemId !== null && (r.qty ?? 0) > 0 && (r.unitPrice ?? 0) >= 0)
      .map(r => ({
        itemId: r.itemId as number,
        uomId: null,
        returnedQty: r.qty as number,
        unitPrice: r.unitPrice as number,
        vatGroupId: null,
        originalLineId: null
      }));
    if (lines.length === 0) { this.error.set('Add at least one line.'); return; }
    this.run(this.sales.createReturn({
      number: this.newNumber.trim(),
      branchId,
      customerId: this.newCustomerId,
      originalInvoiceId: this.newOriginalInvoiceId,
      returnDate: this.newReturnDate,
      reason: this.newReason,
      restock: this.newRestock,
      notes: null,
      lines
    }), `Return ${this.newNumber} created.`);
    this.newNumber = '';
    this.newLines = [{ itemId: null, qty: null, unitPrice: null }];
  }

  post(r: CustomerReturn): void {
    this.run(this.sales.postReturn(r.id), `Return posted.`);
  }

  cancel(r: CustomerReturn): void {
    if (!window.confirm(`Cancel ${r.number}?`)) return;
    this.run(this.sales.cancelReturn(r.id), `Return cancelled.`);
  }

  issueCreditNote(r: CustomerReturn): void {
    const cnNumber = window.prompt(`Credit note number for ${r.number}?`);
    if (!cnNumber || !cnNumber.trim()) return;
    this.busy.set(true);
    this.error.set(null);
    this.sales.issueCreditNote(r.id, { number: cnNumber.trim(), notes: null }).subscribe({
      next: cn => {
        this.busy.set(false);
        this.info.set(`Credit note ${cn.number} issued for ${cn.totalAmount}.`);
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private run(op: import('rxjs').Observable<CustomerReturn>, msg: string): void {
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
