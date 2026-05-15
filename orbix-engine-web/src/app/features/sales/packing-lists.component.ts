import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { SalesService } from './sales.service';
import {
  CreatePackingListLine,
  PackingList,
  SalesInvoice
} from './sales.models';

interface LineRow { invoiceLineId: number | null; qty: number | null; }

@Component({
  selector: 'orbix-packing-lists',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2 class="h3 mb-4">Packing lists</h2>
    @if (error()) { <div class="alert alert-danger py-2">{{ error() }}</div> }
    @if (info()) { <div class="alert alert-success py-2">{{ info() }}</div> }

    <div class="row g-4">
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Packing lists</div>
          <div class="list-group list-group-flush">
            @for (p of packingLists(); track p.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === p.id" (click)="select(p)">
                <span>{{ p.number }}
                  <small class="d-block text-muted">
                    inv #{{ p.salesInvoiceId }} · {{ p.dispatchDate }}
                    @if (p.vehicleNo) { · {{ p.vehicleNo }} }
                  </small>
                </span>
                <span class="badge align-self-center"
                      [class.text-bg-secondary]="p.status === 'DRAFT'"
                      [class.text-bg-info]="p.status === 'DISPATCHED'"
                      [class.text-bg-success]="p.status === 'DELIVERED'"
                      [class.text-bg-danger]="p.status === 'CANCELLED'">{{ p.status }}</span>
              </button>
            } @empty { <div class="list-group-item text-muted">No packing lists yet.</div> }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New packing list</div>
          <div class="card-body">
            <form (ngSubmit)="create()" #f="ngForm">
              <div class="mb-2">
                <label class="form-label small mb-1">Number</label>
                <input class="form-control" name="num" [(ngModel)]="newNumber" required>
              </div>
              <div class="mb-2">
                <label class="form-label small mb-1">Invoice id</label>
                <input class="form-control" type="number" name="iid"
                       [(ngModel)]="newInvoiceId" (ngModelChange)="loadInvoice()" required>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Dispatch date</label>
                  <input class="form-control" type="date" name="dd" [(ngModel)]="newDispatchDate" required>
                </div>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Driver name</label>
                  <input class="form-control" name="drv" [(ngModel)]="newDriverName">
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Vehicle no</label>
                  <input class="form-control" name="veh" [(ngModel)]="newVehicleNo">
                </div>
              </div>

              @if (loadedInvoice(); as inv) {
                <fieldset class="border rounded p-2 mb-2">
                  <legend class="float-none w-auto small px-1">Lines from invoice {{ inv.number }}</legend>
                  @for (line of inv.lines; track line.id; let i = $index) {
                    <div class="row g-2 mb-2 align-items-end">
                      <div class="col-12">
                        <small class="text-muted">
                          line #{{ line.lineNo }} · item {{ line.itemId }} · qty {{ line.qty }}
                        </small>
                      </div>
                      <div class="col-4">
                        <input class="form-check-input me-2" type="checkbox"
                               [name]="'sel' + i" [(ngModel)]="lineSelected[line.id]">
                        <label class="form-check-label small">Include</label>
                      </div>
                      <div class="col-8">
                        <input class="form-control form-control-sm" type="number" step="0.0001" min="0"
                               placeholder="qty to dispatch"
                               [name]="'lq' + i" [(ngModel)]="lineQty[line.id]">
                      </div>
                    </div>
                  }
                </fieldset>
              }

              <button class="btn btn-primary w-100" [disabled]="busy() || f.invalid">
                Create draft
              </button>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        @if (selected(); as pl) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ pl.number }} — {{ pl.status }}</span>
              <div class="btn-group">
                @if (pl.status === 'DRAFT') {
                  <button class="btn btn-sm btn-primary" [disabled]="busy()" (click)="dispatch(pl)">
                    Dispatch
                  </button>
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busy()" (click)="cancel(pl)">
                    Cancel
                  </button>
                }
                @if (pl.status === 'DISPATCHED') {
                  <button class="btn btn-sm btn-success" [disabled]="busy()" (click)="deliver(pl)">
                    Mark delivered
                  </button>
                }
              </div>
            </div>
            <div class="card-body">
              <dl class="row mb-3">
                <dt class="col-sm-3">Invoice</dt><dd class="col-sm-9">#{{ pl.salesInvoiceId }}</dd>
                <dt class="col-sm-3">Dispatch date</dt><dd class="col-sm-9">{{ pl.dispatchDate }}</dd>
                <dt class="col-sm-3">Driver</dt><dd class="col-sm-9">{{ pl.driverName ?? '—' }}</dd>
                <dt class="col-sm-3">Vehicle</dt><dd class="col-sm-9">{{ pl.vehicleNo ?? '—' }}</dd>
                @if (pl.deliveredAt) {
                  <dt class="col-sm-3">Delivered</dt>
                  <dd class="col-sm-9">by #{{ pl.deliveredBy }} at {{ pl.deliveredAt }}</dd>
                }
              </dl>
              <table class="table table-sm align-middle">
                <thead><tr><th>Invoice line id</th><th class="text-end">Qty</th></tr></thead>
                <tbody>
                  @for (line of pl.lines; track line.id) {
                    <tr>
                      <td>#{{ line.salesInvoiceLineId }}</td>
                      <td class="text-end">{{ line.qty }}</td>
                    </tr>
                  } @empty { <tr><td colspan="2" class="text-muted">No lines.</td></tr> }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="alert alert-secondary">Select a packing list, or create one against an invoice.</div>
        }
      </div>
    </div>
  `
})
export class PackingListsComponent implements OnInit {
  private readonly sales = inject(SalesService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly packingLists = signal<PackingList[]>([]);
  readonly selected = signal<PackingList | null>(null);
  readonly loadedInvoice = signal<SalesInvoice | null>(null);
  readonly busy = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  newNumber = '';
  newInvoiceId: number | null = null;
  newDispatchDate = new Date().toISOString().slice(0, 10);
  newDriverName = '';
  newVehicleNo = '';
  lineSelected: Record<number, boolean> = {};
  lineQty: Record<number, number> = {};

  ngOnInit(): void { this.refresh(); }
  refresh(): void {
    this.sales.listPackingLists(this.branchId()).subscribe({
      next: rows => this.packingLists.set(rows),
      error: err => this.showError(err)
    });
  }
  select(p: PackingList): void { this.selected.set(p); }

  loadInvoice(): void {
    if (this.newInvoiceId === null) { this.loadedInvoice.set(null); return; }
    this.sales.getInvoice(this.newInvoiceId).subscribe({
      next: inv => {
        this.loadedInvoice.set(inv);
        this.lineSelected = {};
        this.lineQty = {};
        for (const line of inv.lines) {
          this.lineQty[line.id] = line.qty;
        }
      },
      error: err => this.showError(err)
    });
  }

  create(): void {
    const branchId = this.branchId();
    const inv = this.loadedInvoice();
    if (branchId === null || inv === null) {
      this.error.set('Branch + invoice required.');
      return;
    }
    const lines: CreatePackingListLine[] = inv.lines
      .filter(l => this.lineSelected[l.id] && (this.lineQty[l.id] ?? 0) > 0)
      .map(l => ({ salesInvoiceLineId: l.id, qty: this.lineQty[l.id] }));
    if (lines.length === 0) { this.error.set('Tick at least one line.'); return; }
    this.run(this.sales.createPackingList({
      number: this.newNumber.trim(),
      branchId,
      salesInvoiceId: inv.id,
      dispatchDate: this.newDispatchDate,
      driverName: this.newDriverName.trim() || null,
      vehicleNo: this.newVehicleNo.trim() || null,
      notes: null,
      lines
    }), `Packing list ${this.newNumber} created.`);
    this.newNumber = '';
  }

  dispatch(p: PackingList): void {
    this.run(this.sales.dispatchPackingList(p.id), `Dispatched.`);
  }

  deliver(p: PackingList): void {
    this.run(this.sales.deliverPackingList(p.id), `Marked delivered.`);
  }

  cancel(p: PackingList): void {
    if (!window.confirm(`Cancel ${p.number}?`)) return;
    this.run(this.sales.cancelPackingList(p.id), `Cancelled.`);
  }

  private run(op: import('rxjs').Observable<PackingList>, msg: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    op.subscribe({
      next: p => {
        this.busy.set(false);
        this.info.set(msg);
        this.selected.set(p);
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
