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
import { PagerComponent } from '../../core/ui/pager.component';
import { ProcurementService } from './procurement.service';
import { CreateLpoLine, LpoOrder } from './procurement.models';

@Component({
  selector: 'orbix-lpos',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, SearchSelectComponent, PagerComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Procurement</a> &rsaquo; LPOs
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Local purchase orders</h1>
        <p class="text-secondary mb-0 small">{{ total() }} LPO{{ total() === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New LPO' }}
      </button>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i><span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
      </div>
    }
    @if (info()) {
      <div class="alert alert-success d-flex align-items-center gap-2 py-2">
        <i class="bi bi-check-circle-fill"></i><span class="flex-grow-1">{{ info() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="info.set(null)"></button>
      </div>
    }

    @if (showForm()) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">Draft LPO</h2>
          <button class="btn-close btn-sm" aria-label="Close" (click)="toggleForm()"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="create()" #f="ngForm" class="d-flex flex-column gap-3">
            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend"><i class="bi bi-file-earmark-text text-secondary"></i> Header</legend>
              <div class="row g-2">
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Number</label>
                  <input class="form-control font-monospace" name="num" [(ngModel)]="newNumber" required placeholder="LPO0001">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Supplier ID</label>
                  <input class="form-control" type="number" name="sup" [(ngModel)]="newSupplierId" required>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Currency</label>
                  <orbix-search-select name="cur" [options]="currencyOptions()"
                                       [(ngModel)]="newCurrency" placeholder="Select a currency…" required>
                  </orbix-search-select>
                </div>
                <div class="col-md-6">
                  <label class="form-label small fw-semibold text-secondary">Order date</label>
                  <input class="form-control" type="date" name="od" [(ngModel)]="newOrderDate" required>
                </div>
                <div class="col-md-6">
                  <label class="form-label small fw-semibold text-secondary">Expected delivery <span class="text-muted">(optional)</span></label>
                  <input class="form-control" type="date" name="ed" [(ngModel)]="newExpectedDelivery">
                </div>
              </div>
            </fieldset>

            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend"><i class="bi bi-list-ul text-secondary"></i> First line</legend>
              <div class="row g-2">
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Item ID</label>
                  <input class="form-control" type="number" name="li" [(ngModel)]="newItemId" required>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Qty</label>
                  <input class="form-control text-end" type="number" step="0.0001" min="0.0001"
                         name="lq" [(ngModel)]="newQty" required>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Unit price</label>
                  <input class="form-control text-end" type="number" step="0.0001" min="0"
                         name="lp" [(ngModel)]="newUnitPrice" required>
                </div>
              </div>
              <p class="small text-secondary mb-0 mt-2">
                <i class="bi bi-info-circle me-1"></i>Add more lines via PATCH once the draft exists.
              </p>
            </fieldset>

            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy() || f.invalid">
                @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-file-earmark-text"></i> }
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
            <h2 class="h6 fw-bold mb-0 text-dark">LPOs</h2>
            <span class="badge text-bg-light text-secondary">{{ total() }}</span>
          </div>
          @if (lpos().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-file-earmark-text"></i></div>
              <p class="small text-secondary mb-0">No LPOs yet. Draft the first one.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 lpo-list">
              @for (l of lpos(); track l.id) {
                <li>
                  <button type="button" class="lpo-row"
                          [class.is-active]="selected()?.id === l.id"
                          [title]="l.cancellationReason ? ('Cancelled: ' + l.cancellationReason) : null"
                          (click)="select(l)">
                    <div class="flex-grow-1 min-w-0">
                      <div class="d-flex align-items-center gap-2 mb-1">
                        <span class="badge text-bg-light border text-secondary font-monospace">{{ l.number }}</span>
                        <span class="status-badge status-badge--{{ l.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ statusLabel(l.status) }}
                        </span>
                      </div>
                      <p class="small text-secondary mb-0">
                        Supplier #{{ l.supplierId }} · {{ l.orderDate | date:'mediumDate' }}
                      </p>
                    </div>
                    <div class="fw-bold text-dark">{{ l.totalAmount | number:'1.2-2' }}</div>
                  </button>
                </li>
              }
            </ul>
            @if (totalPages() >= 1) {
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
        @if (selected(); as lpo) {
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4">
              <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                <div>
                  <p class="small text-secondary mb-1">Supplier #{{ lpo.supplierId }} · {{ lpo.orderDate | date:'mediumDate' }}</p>
                  <h2 class="h4 fw-bold mb-1 text-dark">{{ lpo.number }}</h2>
                  <span class="status-badge status-badge--{{ lpo.status.toLowerCase() }}">
                    <span class="status-badge__dot"></span>{{ statusLabel(lpo.status) }}
                  </span>
                </div>
                <div class="d-flex gap-2 flex-wrap">
                  @if (lpo.status === 'DRAFT') {
                    <button class="btn btn-sm btn-primary d-inline-flex align-items-center gap-1" [disabled]="busy()" (click)="submit(lpo)">
                      <i class="bi bi-send"></i> Submit
                    </button>
                    <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1" [disabled]="busy()" (click)="openCancel(lpo)">
                      <i class="bi bi-x-circle"></i> Cancel
                    </button>
                  }
                  @if (lpo.status === 'PENDING_APPROVAL') {
                    <button class="btn btn-sm btn-success d-inline-flex align-items-center gap-1" [disabled]="busy()" (click)="approve(lpo)">
                      <i class="bi bi-check2-circle"></i> Approve
                    </button>
                    <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1" [disabled]="busy()" (click)="openCancel(lpo)">
                      <i class="bi bi-x-circle"></i> Cancel
                    </button>
                  }
                  @if (lpo.status === 'APPROVED' && canCancelLpo()) {
                    <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1"
                            [disabled]="busy()" (click)="openCancel(lpo)"
                            title="Cancel this approved LPO. Blocked if a GRN has already been posted against it.">
                      <i class="bi bi-x-circle"></i> Cancel
                    </button>
                  }
                </div>
              </div>

              @if (cancelTarget()?.id === lpo.id) {
                <form (ngSubmit)="confirmCancel()" #cf="ngForm" class="cancel-form mb-3">
                  <label class="form-label small fw-semibold text-secondary" [attr.for]="'lpo-cancel-reason-' + lpo.id">
                    Reason
                    @if (lpo.status === 'APPROVED') {
                      <span class="text-danger" aria-hidden="true">*</span>
                      <span class="visually-hidden">required</span>
                    } @else {
                      <span class="text-muted">(optional)</span>
                    }
                  </label>
                  <textarea [id]="'lpo-cancel-reason-' + lpo.id"
                            class="form-control" name="reason" rows="2" maxlength="500"
                            [(ngModel)]="cancelReason"
                            [required]="lpo.status === 'APPROVED'"
                            placeholder="Why is this LPO being cancelled?"></textarea>
                  <div class="d-flex gap-2 mt-2">
                    <button type="submit" class="btn btn-sm btn-danger d-inline-flex align-items-center gap-1"
                            [disabled]="busy() || cf.invalid">
                      @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                      @else { <i class="bi bi-x-circle"></i> }
                      Confirm cancel
                    </button>
                    <button type="button" class="btn btn-sm btn-outline-secondary"
                            [disabled]="busy()" (click)="closeCancel()">Keep LPO</button>
                  </div>
                </form>
              }

              <div class="row g-2 totals-row mb-3">
                <div class="col-6 col-md-4">
                  <p class="totals-row__label">Subtotal</p>
                  <p class="totals-row__value">{{ lpo.subtotalAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-4">
                  <p class="totals-row__label">Tax</p>
                  <p class="totals-row__value">{{ lpo.taxAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-4">
                  <p class="totals-row__label">Total</p>
                  <p class="totals-row__value totals-row__value--strong">{{ lpo.totalAmount | number:'1.2-2' }}</p>
                </div>
              </div>

              <dl class="row small mb-0">
                <dt class="col-4 text-secondary">Expected delivery</dt>
                <dd class="col-8 mb-1">{{ lpo.expectedDeliveryDate ? (lpo.expectedDeliveryDate | date:'mediumDate') : '—' }}</dd>
                <dt class="col-4 text-secondary">Currency</dt>
                <dd class="col-8 mb-1 font-monospace">{{ lpo.currencyCode }}</dd>
                @if (lpo.approvedBy) {
                  <dt class="col-4 text-secondary">Approved</dt>
                  <dd class="col-8 mb-1">by #{{ lpo.approvedBy }} at {{ lpo.approvedAt | date:'medium' }}</dd>
                }
                @if (lpo.notes) {
                  <dt class="col-4 text-secondary">Notes</dt>
                  <dd class="col-8 mb-1">{{ lpo.notes }}</dd>
                }
                @if (lpo.cancellationReason) {
                  <dt class="col-4 text-secondary">Cancellation reason</dt>
                  <dd class="col-8 mb-1">{{ lpo.cancellationReason }}</dd>
                }
              </dl>
            </div>
          </div>

          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Lines</h3>
              <span class="badge text-bg-light text-secondary">{{ lpo.lines.length }}</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>#</th><th>Item</th>
                    <th class="text-end">Ordered</th><th class="text-end">Received</th>
                    <th class="text-end">Unit</th><th class="text-end">Disc %</th>
                    <th class="text-end">Total</th>
                  </tr>
                </thead>
                <tbody>
                  @for (line of lpo.lines; track line.id) {
                    <tr>
                      <td class="small text-secondary">{{ line.lineNo }}</td>
                      <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ line.itemId }}</span></td>
                      <td class="text-end">{{ line.orderedQty }}</td>
                      <td class="text-end small"
                          [class.text-success]="line.receivedQty >= line.orderedQty"
                          [class.text-warning]="line.receivedQty > 0 && line.receivedQty < line.orderedQty"
                          [class.text-secondary]="line.receivedQty === 0">
                        {{ line.receivedQty }}
                      </td>
                      <td class="text-end">{{ line.unitPrice | number:'1.2-2' }}</td>
                      <td class="text-end small text-secondary">{{ line.discountPct | number:'1.0-2' }}</td>
                      <td class="text-end fw-semibold">{{ line.lineTotal | number:'1.2-2' }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="7" class="text-center text-secondary py-4">No lines.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick an LPO</h2>
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

    .lpo-list { max-height: 70vh; overflow-y: auto; }
    .lpo-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .lpo-row:hover { background: #f8fafc; }
    .lpo-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .lpo-row:last-child { border-bottom: none; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
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
    .status-badge--draft               { background: #f3f4f6; color: #4b5563; }
    .status-badge--draft .status-badge__dot               { background: #9ca3af; }
    .status-badge--pending_approval    { background: #fef3c7; color: #92400e; }
    .status-badge--pending_approval .status-badge__dot    { background: #f59e0b; }
    .status-badge--approved            { background: #e0ecff; color: #1d4ed8; }
    .status-badge--approved .status-badge__dot            { background: #3b82f6; }
    .status-badge--partially_received  { background: #fef3c7; color: #92400e; }
    .status-badge--partially_received .status-badge__dot  { background: #f59e0b; }
    .status-badge--received            { background: #d1fae5; color: #047857; }
    .status-badge--received .status-badge__dot            { background: #10b981; }
    .status-badge--cancelled           { background: #fee2e2; color: #b91c1c; }
    .status-badge--cancelled .status-badge__dot           { background: #f43f5e; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .cancel-form {
      background: #fff7f7; border: 1px solid #fecaca; border-radius: 10px;
      padding: 0.875rem 1rem;
    }
  `]
})
export class LposComponent implements OnInit {
  private readonly procurement = inject(ProcurementService);
  private readonly branchService = inject(BranchService);
  protected readonly auth = inject(AuthService);
  private readonly currencyService = inject(CurrencyService);

  protected readonly lpos = signal<LpoOrder[]>([]);
  protected readonly pageNo = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly pageSize = 20;
  protected readonly selected = signal<LpoOrder | null>(null);
  protected readonly busy = signal<boolean>(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly showForm = signal(false);
  protected readonly cancelTarget = signal<LpoOrder | null>(null);
  protected cancelReason = '';
  protected readonly canCancelLpo = computed(() =>
    this.auth.hasPermission('PROCUREMENT.CANCEL_LPO') || this.auth.hasPermission('PROCUREMENT.MANAGE_LPO')
  );
  protected readonly currencies = signal<Currency[]>([]);
  protected readonly currencyOptions = computed<SearchSelectOption[]>(() =>
    this.currencies()
      .filter(c => c.status === 'ACTIVE')
      .map(c => ({ id: c.code, label: `${c.code} · ${c.name}` }))
  );

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected newNumber = '';
  protected newSupplierId: string | null = null;
  protected newOrderDate = new Date().toISOString().slice(0, 10);
  protected newExpectedDelivery: string | null = null;
  protected newCurrency = 'TZS';
  protected newItemId: string | null = null;
  protected newQty: number | null = null;
  protected newUnitPrice: number | null = null;

  ngOnInit(): void {
    this.refresh();
    this.currencyService.listCurrencies().subscribe({
      next: list => this.currencies.set(list),
      error: () => this.currencies.set([])
    });
  }

  toggleForm(): void { this.showForm.update(v => !v); }

  refresh(): void {
    this.procurement.listLpos(this.branchId(), this.pageNo(), this.pageSize).subscribe({
      next: page => {
        this.lpos.set(page.content);
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

  select(lpo: LpoOrder): void {
    this.selected.set(lpo);
    if (this.cancelTarget()?.id !== lpo.id) this.closeCancel();
  }

  statusLabel(status: string): string {
    if (status === 'PENDING_APPROVAL') return 'PENDING';
    if (status === 'PARTIALLY_RECEIVED') return 'PART-RECV';
    return status;
  }

  create(): void {
    const branchId = this.branchId();
    if (branchId === null) {
      this.error.set('No active branch.');
      return;
    }
    if (this.newSupplierId === null || this.newItemId === null
        || this.newQty === null || this.newUnitPrice === null) return;
    const lines: CreateLpoLine[] = [{
      itemId: this.newItemId,
      uomId: null,
      orderedQty: this.newQty,
      unitPrice: this.newUnitPrice,
      vatGroupId: null,
      discountPct: null
    }];
    this.run(this.procurement.createLpo({
      number: this.newNumber.trim(),
      branchId,
      supplierId: this.newSupplierId,
      orderDate: this.newOrderDate,
      expectedDeliveryDate: this.newExpectedDelivery,
      currencyCode: this.newCurrency.trim().toUpperCase(),
      notes: null,
      lines
    }), `LPO ${this.newNumber} created.`);
    this.newNumber = '';
    this.newItemId = null;
    this.newQty = null;
    this.newUnitPrice = null;
    this.showForm.set(false);
  }

  submit(lpo: LpoOrder): void {
    this.run(this.procurement.submitLpo(lpo.uid), `LPO submitted.`);
  }

  approve(lpo: LpoOrder): void {
    this.run(this.procurement.approveLpo(lpo.uid), `LPO approved.`);
  }

  openCancel(lpo: LpoOrder): void {
    this.cancelTarget.set(lpo);
    this.cancelReason = '';
    this.error.set(null);
  }

  closeCancel(): void {
    this.cancelTarget.set(null);
    this.cancelReason = '';
  }

  confirmCancel(): void {
    const lpo = this.cancelTarget();
    if (!lpo) return;
    const reason = this.cancelReason.trim();
    if (lpo.status === 'APPROVED' && reason.length === 0) {
      this.error.set('A reason is required when cancelling an approved LPO.');
      return;
    }
    this.run(
      this.procurement.cancelLpo(lpo.uid, reason.length > 0 ? reason : null),
      `LPO ${lpo.number} cancelled.`
    );
    this.closeCancel();
  }

  private run(op: Observable<LpoOrder>, successMessage: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    op.subscribe({
      next: order => {
        this.busy.set(false);
        this.info.set(successMessage);
        this.selected.set(order);
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
