import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
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
  imports: [CommonModule, FormsModule],
  template: `
    <h2 class="h3 mb-4">Goods received notes</h2>
    @if (error()) { <div class="alert alert-danger py-2">{{ error() }}</div> }
    @if (info()) { <div class="alert alert-success py-2">{{ info() }}</div> }

    <div class="row g-4">
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">GRNs</div>
          <div class="list-group list-group-flush">
            @for (g of grns(); track g.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === g.id" (click)="select(g)">
                <span>{{ g.number }}
                  <small class="d-block text-muted">
                    supplier #{{ g.supplierId }} · {{ g.totalAmount | number:'1.0-2' }}
                    @if (g.lpoOrderId) { <span> · LPO #{{ g.lpoOrderId }}</span> }
                  </small>
                </span>
                <span class="badge align-self-center"
                      [class.text-bg-secondary]="g.status === 'DRAFT'"
                      [class.text-bg-success]="g.status === 'POSTED'"
                      [class.text-bg-danger]="g.status === 'CANCELLED'">{{ g.status }}</span>
              </button>
            } @empty { <div class="list-group-item text-muted">No GRNs yet.</div> }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">Receive against LPO</div>
          <div class="card-body">
            <form (ngSubmit)="loadLpo()" #lf="ngForm">
              <div class="mb-2">
                <label class="form-label small mb-1">LPO id</label>
                <div class="input-group">
                  <input class="form-control" type="number" name="lpoId" [(ngModel)]="lpoIdInput" required>
                  <button class="btn btn-outline-secondary" type="submit" [disabled]="busy() || lf.invalid">
                    Load
                  </button>
                </div>
              </div>
            </form>

            @if (loadedLpo(); as lpo) {
              <form (ngSubmit)="createAgainstLpo()" class="mt-2">
                <div class="small text-muted mb-2">
                  LPO {{ lpo.number }} · supplier #{{ lpo.supplierId }} · status {{ lpo.status }}
                </div>
                <div class="mb-2">
                  <label class="form-label small mb-1">GRN number</label>
                  <input class="form-control" name="grnNo" [(ngModel)]="newNumber" required>
                </div>
                <div class="mb-2">
                  <label class="form-label small mb-1">Received date</label>
                  <input class="form-control" type="date" name="rd" [(ngModel)]="receivedDate" required>
                </div>
                <div class="mb-2">
                  <label class="form-label small mb-1">Delivery note (optional)</label>
                  <input class="form-control" name="dn" [(ngModel)]="deliveryNote">
                </div>

                <fieldset class="border rounded p-2 mb-2">
                  <legend class="float-none w-auto small px-1">Lines to receive</legend>
                  @for (line of lpo.lines; track line.id) {
                    <div class="row g-2 align-items-end mb-2">
                      <div class="col-12">
                        <small class="text-muted">
                          line #{{ line.lineNo }} · item {{ line.itemId }} ·
                          ordered {{ line.orderedQty }} · received {{ line.receivedQty }} ·
                          outstanding <strong>{{ line.orderedQty - line.receivedQty }}</strong>
                        </small>
                      </div>
                      <div class="col-4">
                        <label class="form-label small mb-1">Qty</label>
                        <input class="form-control form-control-sm" type="number" step="0.0001" min="0"
                               [name]="'q' + line.id" [(ngModel)]="receiveQty[line.id]">
                      </div>
                      <div class="col-4">
                        <label class="form-label small mb-1">Unit cost</label>
                        <input class="form-control form-control-sm" type="number" step="0.0001" min="0"
                               [name]="'c' + line.id" [(ngModel)]="receiveCost[line.id]">
                      </div>
                      <div class="col-4">
                        <label class="form-label small mb-1">Batch no (if batched)</label>
                        <input class="form-control form-control-sm"
                               [name]="'b' + line.id" [(ngModel)]="receiveBatch[line.id]">
                      </div>
                    </div>
                  }
                </fieldset>
                <button class="btn btn-primary w-100" [disabled]="busy()">Create draft GRN</button>
              </form>
            }
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        @if (selected(); as grn) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ grn.number }} — {{ grn.status }}</span>
              <div class="btn-group">
                @if (grn.status === 'DRAFT') {
                  <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="post(grn)">Post</button>
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busy()" (click)="cancel(grn)">Cancel</button>
                }
              </div>
            </div>
            <div class="card-body">
              <dl class="row mb-3">
                <dt class="col-sm-3">Supplier</dt><dd class="col-sm-9">#{{ grn.supplierId }}</dd>
                <dt class="col-sm-3">LPO</dt>
                <dd class="col-sm-9">{{ grn.lpoOrderId ? ('#' + grn.lpoOrderId) : 'direct' }}</dd>
                <dt class="col-sm-3">Received</dt><dd class="col-sm-9">{{ grn.receivedDate }}</dd>
                <dt class="col-sm-3">Delivery note</dt><dd class="col-sm-9">{{ grn.supplierDeliveryNote ?? '—' }}</dd>
                <dt class="col-sm-3">Subtotal</dt><dd class="col-sm-9">{{ grn.subtotalAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Tax</dt><dd class="col-sm-9">{{ grn.taxAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Total</dt><dd class="col-sm-9 fw-semibold">{{ grn.totalAmount | number:'1.2-2' }}</dd>
                @if (grn.postedAt) {
                  <dt class="col-sm-3">Posted</dt>
                  <dd class="col-sm-9">by #{{ grn.postedBy }} at {{ grn.postedAt }}</dd>
                }
              </dl>
              <table class="table table-sm align-middle">
                <thead><tr><th>Item</th><th class="text-end">Qty</th><th class="text-end">Cost</th>
                           <th class="text-end">Line total</th><th>Batch</th><th>Expiry</th></tr></thead>
                <tbody>
                  @for (line of grn.lines; track line.id) {
                    <tr>
                      <td>{{ line.itemId }}</td>
                      <td class="text-end">{{ line.receivedQty }}</td>
                      <td class="text-end">{{ line.unitCost | number:'1.2-2' }}</td>
                      <td class="text-end">{{ line.lineTotal | number:'1.2-2' }}</td>
                      <td>{{ line.batchNo ?? '—' }}</td>
                      <td>{{ line.expiryDate ?? '—' }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="6" class="text-muted">No lines.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="alert alert-secondary">Select a GRN from the list, or receive against an LPO.</div>
        }
      </div>
    </div>
  `
})
export class GrnsComponent implements OnInit {
  private readonly procurement = inject(ProcurementService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly grns = signal<Grn[]>([]);
  readonly selected = signal<Grn | null>(null);
  readonly loadedLpo = signal<LpoOrder | null>(null);
  readonly busy = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  // form state
  lpoIdInput: number | null = null;
  newNumber = '';
  receivedDate = new Date().toISOString().slice(0, 10);
  deliveryNote = '';
  receiveQty: Record<number, number> = {};
  receiveCost: Record<number, number> = {};
  receiveBatch: Record<number, string> = {};

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.procurement.listGrns(this.branchId()).subscribe({
      next: rows => this.grns.set(rows),
      error: err => this.showError(err)
    });
  }

  select(grn: Grn): void {
    this.selected.set(grn);
  }

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
  }

  post(grn: Grn): void {
    this.run(this.procurement.postGrn(grn.id), `GRN posted.`);
  }

  cancel(grn: Grn): void {
    if (!window.confirm(`Cancel ${grn.number}?`)) return;
    this.run(this.procurement.cancelGrn(grn.id), `GRN cancelled.`);
  }

  private run(op: import('rxjs').Observable<Grn>, successMessage: string): void {
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
