import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { PagerComponent } from '../../core/ui/pager.component';
import { SalesService } from './sales.service';
import {
  CreateCustomerReturnLine,
  CustomerReturn,
  RETURN_REASONS,
  ReturnReason
} from './sales.models';

interface LineRow {
  itemId: string | null;
  qty: number | null;
  unitPrice: number | null;
}

@Component({
  selector: 'orbix-customer-returns',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, PagerComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Sales</a> &rsaquo; Returns
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Customer returns</h1>
        <p class="text-secondary mb-0 small">{{ total() }} return{{ total() === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New return' }}
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
          <h2 class="h6 fw-bold mb-0 text-dark">New return</h2>
          <button class="btn-close btn-sm" (click)="toggleForm()"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="create()" #f="ngForm" class="d-flex flex-column gap-3">
            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend"><i class="bi bi-arrow-counterclockwise text-secondary"></i> Return header</legend>
              <div class="row g-2">
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Number</label>
                  <input class="form-control font-monospace" name="num" [(ngModel)]="newNumber" required placeholder="RET0001">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Customer ID</label>
                  <input class="form-control" type="number" name="cid" [(ngModel)]="newCustomerId" required>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Original invoice <span class="text-muted">(optional)</span></label>
                  <input class="form-control" type="number" name="oi" [(ngModel)]="newOriginalInvoiceId">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Return date</label>
                  <input class="form-control" type="date" name="rd" [(ngModel)]="newReturnDate" required>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Reason</label>
                  <select class="form-select" name="rr" [(ngModel)]="newReason" required>
                    @for (r of reasons; track r) { <option [ngValue]="r">{{ r }}</option> }
                  </select>
                </div>
                <div class="col-md-4 d-flex align-items-end">
                  <div class="form-check pb-2">
                    <input class="form-check-input" type="checkbox" id="restock" [(ngModel)]="newRestock" name="restock">
                    <label class="form-check-label small" for="restock">
                      Restock <span class="text-muted">(otherwise damage write-off)</span>
                    </label>
                  </div>
                </div>
              </div>
            </fieldset>

            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend d-flex align-items-center justify-content-between">
                <span><i class="bi bi-list-ul text-secondary"></i> Lines</span>
                <button type="button" class="btn btn-sm btn-outline-primary" (click)="addLine()">
                  <i class="bi bi-plus-lg me-1"></i>Add line
                </button>
              </legend>
              <div class="table-responsive">
                <table class="table table-sm align-middle mb-0 line-table">
                  <thead>
                    <tr>
                      <th>Item ID</th><th class="text-end">Qty</th>
                      <th class="text-end">Unit price</th><th class="actions-col"></th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of newLines; track $index) {
                      <tr>
                        <td><input class="form-control form-control-sm" type="number" [name]="'li' + $index" [(ngModel)]="row.itemId"></td>
                        <td><input class="form-control form-control-sm text-end" type="number" step="0.0001" min="0" [name]="'lq' + $index" [(ngModel)]="row.qty"></td>
                        <td><input class="form-control form-control-sm text-end" type="number" step="0.0001" min="0" [name]="'lp' + $index" [(ngModel)]="row.unitPrice"></td>
                        <td class="actions-col">
                          <button type="button" class="btn btn-sm btn-outline-secondary" (click)="removeLine($index)">
                            <i class="bi bi-x-lg"></i>
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </fieldset>

            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy() || f.invalid">
                @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-arrow-counterclockwise"></i> }
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
            <h2 class="h6 fw-bold mb-0 text-dark">Returns</h2>
            <span class="badge text-bg-light text-secondary">{{ total() }}</span>
          </div>
          @if (returns().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-arrow-counterclockwise"></i></div>
              <p class="small text-secondary mb-0">No returns yet.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 rt-list">
              @for (r of returns(); track r.id) {
                <li>
                  <button type="button" class="rt-row"
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
                        Customer #{{ r.customerId }} · {{ r.reason }} · {{ r.restock ? 'restock' : 'damage' }}
                      </p>
                    </div>
                    <div class="fw-bold text-dark">{{ r.totalAmount | number:'1.2-2' }}</div>
                  </button>
                </li>
              }
            </ul>
            @if (totalPages() > 1) {
              <div class="card-footer bg-white border-top">
                <orbix-pager [page]="pageNo()" [totalPages]="totalPages()"
                             [totalElements]="total()" [pageSize]="pageSize"
                             (pageChange)="goTo($event)"/>
              </div>
            }
          }
        </div>
      </div>

      <div class="col-12 col-lg-7">
        @if (selected(); as ret) {
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4">
              <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                <div>
                  <p class="small text-secondary mb-1">Customer #{{ ret.customerId }} · {{ ret.returnDate | date:'mediumDate' }}</p>
                  <h2 class="h4 fw-bold mb-1 text-dark">{{ ret.number }}</h2>
                  <span class="status-badge status-badge--{{ ret.status.toLowerCase() }}">
                    <span class="status-badge__dot"></span>{{ ret.status }}
                  </span>
                </div>
                <div class="d-flex gap-2 flex-wrap">
                  @if (ret.status === 'DRAFT') {
                    <button class="btn btn-sm btn-primary d-inline-flex align-items-center gap-1" [disabled]="busy()" (click)="post(ret)">
                      <i class="bi bi-send"></i> Post
                    </button>
                    <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1" [disabled]="busy()" (click)="cancel(ret)">
                      <i class="bi bi-x-circle"></i> Cancel
                    </button>
                  }
                  @if (ret.status === 'POSTED') {
                    <button class="btn btn-sm btn-success d-inline-flex align-items-center gap-1" [disabled]="busy()" (click)="issueCreditNote(ret)">
                      <i class="bi bi-receipt"></i> Issue credit note
                    </button>
                  }
                </div>
              </div>

              <dl class="row small mb-0">
                <dt class="col-4 text-secondary">Original invoice</dt>
                <dd class="col-8 mb-1">{{ ret.originalInvoiceId ? '#' + ret.originalInvoiceId : '—' }}</dd>
                <dt class="col-4 text-secondary">Reason</dt><dd class="col-8 mb-1">{{ ret.reason }}</dd>
                <dt class="col-4 text-secondary">Disposition</dt>
                <dd class="col-8 mb-1">{{ ret.restock ? 'Restock' : 'Damage write-off' }}</dd>
                <dt class="col-4 text-secondary">Total</dt>
                <dd class="col-8 mb-1 fw-bold text-dark">{{ ret.totalAmount | number:'1.2-2' }}</dd>
              </dl>
            </div>
          </div>

          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Lines</h3>
              <span class="badge text-bg-light text-secondary">{{ ret.lines.length }}</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>#</th><th>Item</th>
                    <th class="text-end">Qty</th><th class="text-end">Price</th>
                    <th class="text-end">Tax</th><th class="text-end">Total</th>
                  </tr>
                </thead>
                <tbody>
                  @for (line of ret.lines; track line.id) {
                    <tr>
                      <td class="small text-secondary">{{ line.lineNo }}</td>
                      <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ line.itemId }}</span></td>
                      <td class="text-end">{{ line.returnedQty }}</td>
                      <td class="text-end">{{ line.unitPrice | number:'1.2-2' }}</td>
                      <td class="text-end small text-secondary">{{ line.taxAmount | number:'1.2-2' }}</td>
                      <td class="text-end fw-semibold">{{ line.lineTotal | number:'1.2-2' }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="6" class="text-center text-secondary py-4">No lines.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick a return</h2>
              <p class="small text-secondary mb-0">Or draft a new one.</p>
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

    .rt-list { max-height: 70vh; overflow-y: auto; }
    .rt-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .rt-row:hover { background: #f8fafc; }
    .rt-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .rt-row:last-child { border-bottom: none; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--draft     { background: #f3f4f6; color: #4b5563; }
    .status-badge--draft .status-badge__dot     { background: #9ca3af; }
    .status-badge--posted    { background: #e0ecff; color: #1d4ed8; }
    .status-badge--posted .status-badge__dot    { background: #3b82f6; }
    .status-badge--credited  { background: #d1fae5; color: #047857; }
    .status-badge--credited .status-badge__dot  { background: #10b981; }
    .status-badge--cancelled { background: #fee2e2; color: #b91c1c; }
    .status-badge--cancelled .status-badge__dot { background: #f43f5e; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #ffe4e6; color: #be123c; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class ReturnsComponent implements OnInit {
  private readonly sales = inject(SalesService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly reasons = RETURN_REASONS;
  protected readonly returns = signal<CustomerReturn[]>([]);
  protected readonly pageNo = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly pageSize = 20;
  protected readonly selected = signal<CustomerReturn | null>(null);
  protected readonly busy = signal<boolean>(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly showForm = signal(false);

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected newNumber = '';
  protected newCustomerId: string | null = null;
  protected newOriginalInvoiceId: string | null = null;
  protected newReturnDate = new Date().toISOString().slice(0, 10);
  protected newReason: ReturnReason = 'DAMAGED';
  protected newRestock = true;
  protected newLines: LineRow[] = [{ itemId: null, qty: null, unitPrice: null }];

  ngOnInit(): void { this.refresh(); }

  toggleForm(): void { this.showForm.update(v => !v); }

  refresh(): void {
    this.sales.listReturns(this.branchId(), this.pageNo(), this.pageSize).subscribe({
      next: page => {
        this.returns.set(page.content);
        this.total.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.pageNo.set(page.page);
      },
      error: err => this.showError(err)
    });
  }
  goTo(p: number): void {
    if (p < 0 || p >= this.totalPages()) return;
    this.pageNo.set(p);
    this.refresh();
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
        itemId: r.itemId as string,
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
    this.showForm.set(false);
  }

  post(r: CustomerReturn): void {
    this.run(this.sales.postReturn(r.uid), `Return posted.`);
  }

  cancel(r: CustomerReturn): void {
    if (!window.confirm(`Cancel ${r.number}?`)) return;
    this.run(this.sales.cancelReturn(r.uid), `Return cancelled.`);
  }

  issueCreditNote(r: CustomerReturn): void {
    const cnNumber = window.prompt(`Credit note number for ${r.number}?`);
    if (!cnNumber || !cnNumber.trim()) return;
    this.busy.set(true);
    this.error.set(null);
    this.sales.issueCreditNote(r.uid, { number: cnNumber.trim(), notes: null }).subscribe({
      next: cn => {
        this.busy.set(false);
        this.info.set(`Credit note ${cn.number} issued for ${cn.totalAmount}.`);
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private run(op: Observable<CustomerReturn>, msg: string): void {
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
