import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { ProcurementService } from './procurement.service';
import {
  CreateGrnLine,
  Grn,
  LpoOrder,
  LpoOrderLine
} from './procurement.models';

@Component({
  selector: 'orbix-grns',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Procurement</a> &rsaquo; GRNs
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Goods received notes</h1>
        <p class="text-secondary mb-0 small">{{ grns().length }} GRN{{ grns().length === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'Receive against LPO' }}
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
          <h2 class="h6 fw-bold mb-0 text-dark">Receive against LPO</h2>
          <button class="btn-close btn-sm" (click)="toggleForm()"></button>
        </div>
        <div class="card-body p-3 d-flex flex-column gap-3">
          <form (ngSubmit)="loadLpo()" #lf="ngForm">
            <label class="form-label small fw-semibold text-secondary">LPO ID</label>
            <div class="input-group">
              <input class="form-control" type="number" name="lpoId" [(ngModel)]="lpoIdInput" required>
              <button class="btn btn-outline-primary d-inline-flex align-items-center gap-1" type="submit" [disabled]="busy() || lf.invalid">
                <i class="bi bi-arrow-down-circle"></i> Load LPO
              </button>
            </div>
          </form>

          @if (loadedLpo(); as lpo) {
            <form (ngSubmit)="createAgainstLpo()" class="d-flex flex-column gap-3">
              <div class="alert alert-info py-2 mb-0 d-flex align-items-center gap-2 small">
                <i class="bi bi-info-circle-fill"></i>
                <span>LPO <strong class="font-monospace">{{ lpo.number }}</strong> · supplier #{{ lpo.supplierId }} · status {{ lpo.status }}</span>
              </div>

              <fieldset class="form-fieldset">
                <legend class="form-fieldset__legend"><i class="bi bi-box-arrow-in-down text-secondary"></i> Header</legend>
                <div class="row g-2">
                  <div class="col-md-4">
                    <label class="form-label small fw-semibold text-secondary">GRN number</label>
                    <input class="form-control font-monospace" name="grnNo" [(ngModel)]="newNumber" required placeholder="GRN0001">
                  </div>
                  <div class="col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Received date</label>
                    <input class="form-control" type="date" name="rd" [(ngModel)]="receivedDate" required>
                  </div>
                  <div class="col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Delivery note <span class="text-muted">(optional)</span></label>
                    <input class="form-control" name="dn" [(ngModel)]="deliveryNote">
                  </div>
                </div>
              </fieldset>

              <fieldset class="form-fieldset">
                <legend class="form-fieldset__legend"><i class="bi bi-list-ul text-secondary"></i> Lines to receive</legend>
                <div class="table-responsive">
                  <table class="table table-sm align-middle mb-0 line-table">
                    <thead>
                      <tr>
                        <th>Line</th><th>Item</th>
                        <th class="text-end">Ordered</th><th class="text-end">Recv'd</th><th class="text-end">Outstanding</th>
                        <th class="text-end">Qty in</th><th class="text-end">Unit cost</th><th>Batch</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (line of lpo.lines; track line.id) {
                        <tr>
                          <td class="small text-secondary">#{{ line.lineNo }}</td>
                          <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ line.itemId }}</span></td>
                          <td class="text-end small text-secondary">{{ line.orderedQty }}</td>
                          <td class="text-end small text-secondary">{{ line.receivedQty }}</td>
                          <td class="text-end fw-semibold">{{ line.orderedQty - line.receivedQty }}</td>
                          <td>
                            <input class="form-control form-control-sm text-end" type="number" step="0.0001" min="0"
                                   [name]="'q' + line.id" [(ngModel)]="receiveQty[line.id]">
                          </td>
                          <td>
                            <input class="form-control form-control-sm text-end" type="number" step="0.0001" min="0"
                                   [name]="'c' + line.id" [(ngModel)]="receiveCost[line.id]">
                          </td>
                          <td>
                            <input class="form-control form-control-sm font-monospace"
                                   [name]="'b' + line.id" [(ngModel)]="receiveBatch[line.id]" placeholder="optional">
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              </fieldset>

              <div class="d-flex gap-2 pt-2 border-top">
                <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                        [disabled]="busy()">
                  @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                  @else { <i class="bi bi-box-arrow-in-down"></i> }
                  Create draft GRN
                </button>
                <button type="button" class="btn btn-outline-secondary" (click)="toggleForm()">Cancel</button>
              </div>
            </form>
          }
        </div>
      </div>
    }

    <div class="row g-3 g-md-4">
      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">GRNs</h2>
            <span class="badge text-bg-light text-secondary">{{ grns().length }}</span>
          </div>
          @if (grns().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-box-arrow-in-down"></i></div>
              <p class="small text-secondary mb-0">No GRNs yet.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 grn-list">
              @for (g of grns(); track g.id) {
                <li>
                  <button type="button" class="grn-row"
                          [class.is-active]="selected()?.id === g.id"
                          (click)="select(g)">
                    <div class="flex-grow-1 min-w-0">
                      <div class="d-flex align-items-center gap-2 mb-1">
                        <span class="badge text-bg-light border text-secondary font-monospace">{{ g.number }}</span>
                        <span class="status-badge status-badge--{{ g.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ g.status }}
                        </span>
                      </div>
                      <p class="small text-secondary mb-0">
                        Supplier #{{ g.supplierId }} · {{ g.receivedDate | date:'mediumDate' }}
                        @if (g.lpoOrderId) { · LPO #{{ g.lpoOrderId }} }
                      </p>
                    </div>
                    <div class="fw-bold text-dark">{{ g.totalAmount | number:'1.2-2' }}</div>
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      </div>

      <div class="col-12 col-lg-7">
        @if (selected(); as grn) {
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4">
              <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                <div>
                  <p class="small text-secondary mb-1">
                    Supplier #{{ grn.supplierId }}
                    @if (grn.lpoOrderId) { · LPO #{{ grn.lpoOrderId }} }
                    @else { · direct }
                    · {{ grn.receivedDate | date:'mediumDate' }}
                  </p>
                  <h2 class="h4 fw-bold mb-1 text-dark">{{ grn.number }}</h2>
                  <span class="status-badge status-badge--{{ grn.status.toLowerCase() }}">
                    <span class="status-badge__dot"></span>{{ grn.status }}
                  </span>
                </div>
                <div class="d-flex gap-2 flex-wrap">
                  @if (grn.status === 'DRAFT') {
                    <button class="btn btn-sm btn-primary d-inline-flex align-items-center gap-1" [disabled]="busy()" (click)="post(grn)">
                      <i class="bi bi-send"></i> Post
                    </button>
                    <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1" [disabled]="busy()" (click)="cancel(grn)">
                      <i class="bi bi-x-circle"></i> Cancel
                    </button>
                  }
                </div>
              </div>

              <div class="row g-2 totals-row mb-3">
                <div class="col-6 col-md-4">
                  <p class="totals-row__label">Subtotal</p>
                  <p class="totals-row__value">{{ grn.subtotalAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-4">
                  <p class="totals-row__label">Tax</p>
                  <p class="totals-row__value">{{ grn.taxAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-4">
                  <p class="totals-row__label">Total</p>
                  <p class="totals-row__value totals-row__value--strong">{{ grn.totalAmount | number:'1.2-2' }}</p>
                </div>
              </div>

              <dl class="row small mb-0">
                <dt class="col-4 text-secondary">Delivery note</dt>
                <dd class="col-8 mb-1">{{ grn.supplierDeliveryNote ?? '—' }}</dd>
                @if (grn.postedAt) {
                  <dt class="col-4 text-secondary">Posted</dt>
                  <dd class="col-8 mb-1">by #{{ grn.postedBy }} at {{ grn.postedAt | date:'medium' }}</dd>
                }
              </dl>
            </div>
          </div>

          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Lines</h3>
              <span class="badge text-bg-light text-secondary">{{ grn.lines.length }}</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>Item</th><th class="text-end">Qty</th><th class="text-end">Cost</th>
                    <th class="text-end">Total</th><th>Batch</th><th>Expiry</th>
                  </tr>
                </thead>
                <tbody>
                  @for (line of grn.lines; track line.id) {
                    <tr>
                      <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ line.itemId }}</span></td>
                      <td class="text-end">{{ line.receivedQty }}</td>
                      <td class="text-end">{{ line.unitCost | number:'1.2-2' }}</td>
                      <td class="text-end fw-semibold">{{ line.lineTotal | number:'1.2-2' }}</td>
                      <td class="small text-secondary font-monospace">{{ line.batchNo ?? '—' }}</td>
                      <td class="small text-secondary">{{ line.expiryDate ? (line.expiryDate | date:'mediumDate') : '—' }}</td>
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
              <h2 class="h6 fw-bold mb-1 text-dark">Pick a GRN</h2>
              <p class="small text-secondary mb-0">Or receive new stock against an LPO.</p>
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

    .grn-list { max-height: 70vh; overflow-y: auto; }
    .grn-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .grn-row:hover { background: #f8fafc; }
    .grn-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .grn-row:last-child { border-bottom: none; }

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
    .status-badge--draft     { background: #f3f4f6; color: #4b5563; }
    .status-badge--draft .status-badge__dot     { background: #9ca3af; }
    .status-badge--posted    { background: #d1fae5; color: #047857; }
    .status-badge--posted .status-badge__dot    { background: #10b981; }
    .status-badge--cancelled { background: #fee2e2; color: #b91c1c; }
    .status-badge--cancelled .status-badge__dot { background: #f43f5e; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #fef3c7; color: #b45309; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class GrnsComponent implements OnInit {
  private readonly procurement = inject(ProcurementService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly grns = signal<Grn[]>([]);
  protected readonly selected = signal<Grn | null>(null);
  protected readonly loadedLpo = signal<LpoOrder | null>(null);
  protected readonly busy = signal<boolean>(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly showForm = signal(false);

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected lpoIdInput: string | null = null;
  protected newNumber = '';
  protected receivedDate = new Date().toISOString().slice(0, 10);
  protected deliveryNote = '';
  protected receiveQty: Record<string, number> = {};
  protected receiveCost: Record<string, number> = {};
  protected receiveBatch: Record<string, string> = {};

  ngOnInit(): void { this.refresh(); }

  toggleForm(): void { this.showForm.update(v => !v); }

  refresh(): void {
    this.procurement.listGrns(this.branchId()).subscribe({
      next: rows => this.grns.set(rows),
      error: err => this.showError(err)
    });
  }

  select(grn: Grn): void { this.selected.set(grn); }

  loadLpo(): void {
    if (this.lpoIdInput === null) return;
    this.error.set(null);
    this.procurement.getLpo(this.lpoIdInput).subscribe({
      next: lpo => {
        this.loadedLpo.set(lpo);
        this.receiveQty = {};
        this.receiveCost = {};
        this.receiveBatch = {};
        for (const line of lpo.lines) {
          this.receiveCost[line.id] = line.unitPrice;
        }
      },
      error: err => this.showError(err)
    });
  }

  createAgainstLpo(): void {
    const lpo = this.loadedLpo();
    const branchId = this.branchId();
    if (!lpo || branchId === null) return;

    const lines: CreateGrnLine[] = lpo.lines
      .filter((l: LpoOrderLine) => (this.receiveQty[l.id] ?? 0) > 0)
      .map((l: LpoOrderLine) => ({
        lpoOrderLineId: l.id,
        itemId: l.itemId,
        uomId: l.uomId,
        receivedQty: this.receiveQty[l.id],
        unitCost: this.receiveCost[l.id] ?? l.unitPrice,
        vatGroupId: l.vatGroupId,
        batchNo: this.receiveBatch[l.id] ? this.receiveBatch[l.id].trim() : null,
        expiryDate: null
      }));

    if (lines.length === 0) {
      this.error.set('Enter a qty > 0 on at least one line.');
      return;
    }

    this.run(this.procurement.createGrn({
      number: this.newNumber.trim(),
      branchId,
      supplierId: lpo.supplierId,
      lpoOrderId: lpo.id,
      receivedDate: this.receivedDate,
      supplierDeliveryNote: this.deliveryNote.trim() || null,
      notes: null,
      lines
    }), `GRN ${this.newNumber} created.`);
    this.newNumber = '';
    this.showForm.set(false);
  }

  post(grn: Grn): void {
    this.run(this.procurement.postGrn(grn.id), `GRN posted.`);
  }

  cancel(grn: Grn): void {
    if (!globalThis.confirm(`Cancel ${grn.number}?`)) return;
    this.run(this.procurement.cancelGrn(grn.id), `GRN cancelled.`);
  }

  private run(op: Observable<Grn>, successMessage: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    op.subscribe({
      next: grn => {
        this.busy.set(false);
        this.info.set(successMessage);
        this.selected.set(grn);
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
