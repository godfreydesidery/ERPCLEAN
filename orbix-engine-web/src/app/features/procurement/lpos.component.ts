import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { ProcurementService } from './procurement.service';
import { CreateLpoLine, LpoOrder } from './procurement.models';

@Component({
  selector: 'orbix-lpos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2 class="h3 mb-4">Local purchase orders</h2>
    @if (error()) { <div class="alert alert-danger py-2">{{ error() }}</div> }
    @if (info()) { <div class="alert alert-success py-2">{{ info() }}</div> }

    <div class="row g-4">
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">LPOs</div>
          <div class="list-group list-group-flush">
            @for (l of lpos(); track l.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === l.id" (click)="select(l)">
                <span>{{ l.number }}
                  <small class="d-block text-muted">supplier #{{ l.supplierId }} · {{ l.totalAmount | number:'1.0-2' }}</small>
                </span>
                <span class="badge align-self-center"
                      [class.text-bg-secondary]="l.status === 'DRAFT'"
                      [class.text-bg-warning]="l.status === 'PENDING_APPROVAL'"
                      [class.text-bg-success]="l.status === 'APPROVED' || l.status === 'RECEIVED'"
                      [class.text-bg-info]="l.status === 'PARTIALLY_RECEIVED'"
                      [class.text-bg-danger]="l.status === 'CANCELLED'">{{ l.status }}</span>
              </button>
            } @empty { <div class="list-group-item text-muted">No LPOs yet.</div> }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New LPO</div>
          <div class="card-body">
            <form (ngSubmit)="create()" #f="ngForm">
              <div class="mb-2">
                <label class="form-label">Number</label>
                <input class="form-control" name="num" [(ngModel)]="newNumber" required>
              </div>
              <div class="mb-2">
                <label class="form-label">Supplier id</label>
                <input class="form-control" type="number" name="sup" [(ngModel)]="newSupplierId" required>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label">Order date</label>
                  <input class="form-control" type="date" name="od" [(ngModel)]="newOrderDate" required>
                </div>
                <div class="col">
                  <label class="form-label">Expected delivery</label>
                  <input class="form-control" type="date" name="ed" [(ngModel)]="newExpectedDelivery">
                </div>
              </div>
              <div class="mb-2">
                <label class="form-label">Currency</label>
                <input class="form-control" name="cur" [(ngModel)]="newCurrency" required>
              </div>
              <fieldset class="border rounded p-2 mb-2">
                <legend class="float-none w-auto small px-1">Line 1</legend>
                <div class="row g-2">
                  <div class="col">
                    <label class="form-label small mb-1">Item id</label>
                    <input class="form-control" type="number" name="li" [(ngModel)]="newItemId" required>
                  </div>
                  <div class="col">
                    <label class="form-label small mb-1">Qty</label>
                    <input class="form-control" type="number" step="0.0001" min="0.0001"
                           name="lq" [(ngModel)]="newQty" required>
                  </div>
                  <div class="col">
                    <label class="form-label small mb-1">Unit price</label>
                    <input class="form-control" type="number" step="0.0001" min="0"
                           name="lp" [(ngModel)]="newUnitPrice" required>
                  </div>
                </div>
                <div class="form-text">More lines via PATCH (edit) once draft is created.</div>
              </fieldset>
              <button class="btn btn-primary w-100" [disabled]="busy() || f.invalid">Create draft</button>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        @if (selected(); as lpo) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ lpo.number }} — {{ lpo.status }}</span>
              <div class="btn-group">
                @if (lpo.status === 'DRAFT') {
                  <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="submit(lpo)">Submit</button>
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busy()" (click)="cancel(lpo)">Cancel</button>
                }
                @if (lpo.status === 'PENDING_APPROVAL') {
                  <button class="btn btn-sm btn-success" [disabled]="busy()" (click)="approve(lpo)">Approve</button>
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busy()" (click)="cancel(lpo)">Cancel</button>
                }
              </div>
            </div>
            <div class="card-body">
              <dl class="row mb-3">
                <dt class="col-sm-3">Supplier</dt><dd class="col-sm-9">#{{ lpo.supplierId }}</dd>
                <dt class="col-sm-3">Order date</dt><dd class="col-sm-9">{{ lpo.orderDate }}</dd>
                <dt class="col-sm-3">Expected delivery</dt><dd class="col-sm-9">{{ lpo.expectedDeliveryDate ?? '—' }}</dd>
                <dt class="col-sm-3">Currency</dt><dd class="col-sm-9">{{ lpo.currencyCode }}</dd>
                <dt class="col-sm-3">Subtotal</dt><dd class="col-sm-9">{{ lpo.subtotalAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Tax</dt><dd class="col-sm-9">{{ lpo.taxAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Total</dt><dd class="col-sm-9 fw-semibold">{{ lpo.totalAmount | number:'1.2-2' }}</dd>
                @if (lpo.approvedBy) {
                  <dt class="col-sm-3">Approved</dt>
                  <dd class="col-sm-9">by #{{ lpo.approvedBy }} at {{ lpo.approvedAt }}</dd>
                }
                @if (lpo.notes) {
                  <dt class="col-sm-3">Notes</dt><dd class="col-sm-9">{{ lpo.notes }}</dd>
                }
              </dl>
              <table class="table table-sm align-middle">
                <thead><tr><th>#</th><th>Item</th><th class="text-end">Qty</th><th class="text-end">Unit</th>
                           <th class="text-end">Disc%</th><th class="text-end">Line total</th>
                           <th class="text-end">Received</th></tr></thead>
                <tbody>
                  @for (line of lpo.lines; track line.id) {
                    <tr>
                      <td>{{ line.lineNo }}</td>
                      <td>{{ line.itemId }}</td>
                      <td class="text-end">{{ line.orderedQty }}</td>
                      <td class="text-end">{{ line.unitPrice | number:'1.2-2' }}</td>
                      <td class="text-end">{{ line.discountPct | number:'1.0-2' }}</td>
                      <td class="text-end">{{ line.lineTotal | number:'1.2-2' }}</td>
                      <td class="text-end">{{ line.receivedQty }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="7" class="text-muted">No lines.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="alert alert-secondary">Select an LPO from the list, or create one.</div>
        }
      </div>
    </div>
  `
})
export class LposComponent implements OnInit {
  private readonly procurement = inject(ProcurementService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly lpos = signal<LpoOrder[]>([]);
  readonly selected = signal<LpoOrder | null>(null);
  readonly busy = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  // ---- new-LPO form state --------------------------------------------------
  newNumber = '';
  newSupplierId: number | null = null;
  newOrderDate = new Date().toISOString().slice(0, 10);
  newExpectedDelivery: string | null = null;
  newCurrency = 'TZS';
  newItemId: number | null = null;
  newQty: number | null = null;
  newUnitPrice: number | null = null;

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.procurement.listLpos(this.branchId()).subscribe({
      next: rows => this.lpos.set(rows),
      error: err => this.showError(err)
    });
  }

  select(lpo: LpoOrder): void {
    this.selected.set(lpo);
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
  }

  submit(lpo: LpoOrder): void {
    this.run(this.procurement.submitLpo(lpo.id), `LPO submitted.`);
  }

  approve(lpo: LpoOrder): void {
    this.run(this.procurement.approveLpo(lpo.id), `LPO approved.`);
  }

  cancel(lpo: LpoOrder): void {
    if (!window.confirm(`Cancel ${lpo.number}?`)) return;
    this.run(this.procurement.cancelLpo(lpo.id), `LPO cancelled.`);
  }

  private run(op: import('rxjs').Observable<LpoOrder>, successMessage: string): void {
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
