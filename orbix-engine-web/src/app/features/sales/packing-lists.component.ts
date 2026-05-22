import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { SalesService } from './sales.service';
import {
  CreatePackingListLine,
  PackingList,
  SalesInvoice
} from './sales.models';

@Component({
  selector: 'orbix-packing-lists',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Sales</a> &rsaquo; Packing lists
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Packing lists</h1>
        <p class="text-secondary mb-0 small">{{ packingLists().length }} packing list{{ packingLists().length === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New packing list' }}
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
          <h2 class="h6 fw-bold mb-0 text-dark">New packing list</h2>
          <button class="btn-close btn-sm" (click)="toggleForm()"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="create()" #f="ngForm" class="d-flex flex-column gap-3">
            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend"><i class="bi bi-box-seam text-secondary"></i> Header</legend>
              <div class="row g-2">
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Number</label>
                  <input class="form-control font-monospace" name="num" [(ngModel)]="newNumber" required placeholder="PKL0001">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Invoice ID</label>
                  <input class="form-control" type="number" name="iid"
                         [(ngModel)]="newInvoiceId" (ngModelChange)="loadInvoice()" required>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Dispatch date</label>
                  <input class="form-control" type="date" name="dd" [(ngModel)]="newDispatchDate" required>
                </div>
                <div class="col-md-6">
                  <label class="form-label small fw-semibold text-secondary">Driver name</label>
                  <input class="form-control" name="drv" [(ngModel)]="newDriverName" placeholder="Optional">
                </div>
                <div class="col-md-6">
                  <label class="form-label small fw-semibold text-secondary">Vehicle no.</label>
                  <input class="form-control font-monospace" name="veh" [(ngModel)]="newVehicleNo" placeholder="Optional">
                </div>
              </div>
            </fieldset>

            @if (loadedInvoice(); as inv) {
              <fieldset class="form-fieldset">
                <legend class="form-fieldset__legend">
                  <i class="bi bi-list-ul text-secondary"></i>
                  Lines from invoice <span class="font-monospace text-primary">{{ inv.number }}</span>
                </legend>
                <div class="table-responsive">
                  <table class="table table-sm align-middle mb-0 line-table">
                    <thead>
                      <tr>
                        <th class="text-center">Pick</th><th>Line</th><th>Item</th>
                        <th class="text-end">Invoice qty</th><th class="text-end">Dispatch qty</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (line of inv.lines; track line.id; let i = $index) {
                        <tr>
                          <td class="text-center">
                            <input class="form-check-input" type="checkbox"
                                   [name]="'sel' + i" [(ngModel)]="lineSelected[line.id]">
                          </td>
                          <td class="small text-secondary">#{{ line.lineNo }}</td>
                          <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ line.itemId }}</span></td>
                          <td class="text-end small text-secondary">{{ line.qty }}</td>
                          <td>
                            <input class="form-control form-control-sm text-end" type="number" step="0.0001" min="0"
                                   [name]="'lq' + i" [(ngModel)]="lineQty[line.id]">
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              </fieldset>
            }

            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy() || f.invalid">
                @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-box-seam"></i> }
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
            <h2 class="h6 fw-bold mb-0 text-dark">Packing lists</h2>
            <span class="badge text-bg-light text-secondary">{{ packingLists().length }}</span>
          </div>
          @if (packingLists().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-box-seam"></i></div>
              <p class="small text-secondary mb-0">No packing lists yet.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 pl-list">
              @for (p of packingLists(); track p.id) {
                <li>
                  <button type="button" class="pl-row"
                          [class.is-active]="selected()?.id === p.id"
                          (click)="select(p)">
                    <div class="flex-grow-1 min-w-0">
                      <div class="d-flex align-items-center gap-2 mb-1">
                        <span class="badge text-bg-light border text-secondary font-monospace">{{ p.number }}</span>
                        <span class="status-badge status-badge--{{ p.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ p.status }}
                        </span>
                      </div>
                      <p class="small text-secondary mb-0">
                        Invoice #{{ p.salesInvoiceId }} · {{ p.dispatchDate | date:'mediumDate' }}
                        @if (p.vehicleNo) { · <span class="font-monospace">{{ p.vehicleNo }}</span> }
                      </p>
                    </div>
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      </div>

      <div class="col-12 col-lg-7">
        @if (selected(); as pl) {
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4">
              <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                <div>
                  <p class="small text-secondary mb-1">Invoice #{{ pl.salesInvoiceId }} · {{ pl.dispatchDate | date:'mediumDate' }}</p>
                  <h2 class="h4 fw-bold mb-1 text-dark">{{ pl.number }}</h2>
                  <span class="status-badge status-badge--{{ pl.status.toLowerCase() }}">
                    <span class="status-badge__dot"></span>{{ pl.status }}
                  </span>
                </div>
                <div class="d-flex gap-2 flex-wrap">
                  @if (pl.status === 'DRAFT') {
                    <button class="btn btn-sm btn-primary d-inline-flex align-items-center gap-1" [disabled]="busy()" (click)="dispatch(pl)">
                      <i class="bi bi-truck"></i> Dispatch
                    </button>
                    <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1" [disabled]="busy()" (click)="cancel(pl)">
                      <i class="bi bi-x-circle"></i> Cancel
                    </button>
                  }
                  @if (pl.status === 'DISPATCHED') {
                    <button class="btn btn-sm btn-success d-inline-flex align-items-center gap-1" [disabled]="busy()" (click)="deliver(pl)">
                      <i class="bi bi-check-circle"></i> Mark delivered
                    </button>
                  }
                </div>
              </div>

              <dl class="row small mb-0">
                <dt class="col-4 text-secondary">Driver</dt><dd class="col-8 mb-1">{{ pl.driverName ?? '—' }}</dd>
                <dt class="col-4 text-secondary">Vehicle</dt>
                <dd class="col-8 mb-1 font-monospace">{{ pl.vehicleNo ?? '—' }}</dd>
                @if (pl.deliveredAt) {
                  <dt class="col-4 text-secondary">Delivered</dt>
                  <dd class="col-8 mb-1">by #{{ pl.deliveredBy }} at {{ pl.deliveredAt | date:'medium' }}</dd>
                }
              </dl>
            </div>
          </div>

          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Lines</h3>
              <span class="badge text-bg-light text-secondary">{{ pl.lines.length }}</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr><th>Invoice line</th><th class="text-end">Qty dispatched</th></tr>
                </thead>
                <tbody>
                  @for (line of pl.lines; track line.id) {
                    <tr>
                      <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ line.salesInvoiceLineId }}</span></td>
                      <td class="text-end fw-semibold">{{ line.qty }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="2" class="text-center text-secondary py-4">No lines.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick a packing list</h2>
              <p class="small text-secondary mb-0">Or create one against an invoice.</p>
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
    .line-table tbody td { padding: 0.5rem 0.5rem; vertical-align: middle; }

    .pl-list { max-height: 70vh; overflow-y: auto; }
    .pl-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .pl-row:hover { background: #f8fafc; }
    .pl-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .pl-row:last-child { border-bottom: none; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.75rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--draft      { background: #f3f4f6; color: #4b5563; }
    .status-badge--draft .status-badge__dot      { background: #9ca3af; }
    .status-badge--dispatched { background: #e0ecff; color: #1d4ed8; }
    .status-badge--dispatched .status-badge__dot { background: #3b82f6; }
    .status-badge--delivered  { background: #d1fae5; color: #047857; }
    .status-badge--delivered .status-badge__dot  { background: #10b981; }
    .status-badge--cancelled  { background: #fee2e2; color: #b91c1c; }
    .status-badge--cancelled .status-badge__dot  { background: #f43f5e; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #fef3c7; color: #b45309; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class PackingListsComponent implements OnInit {
  private readonly sales = inject(SalesService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly packingLists = signal<PackingList[]>([]);
  protected readonly selected = signal<PackingList | null>(null);
  protected readonly loadedInvoice = signal<SalesInvoice | null>(null);
  protected readonly busy = signal<boolean>(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly showForm = signal(false);

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected newNumber = '';
  protected newInvoiceId: string | null = null;
  protected newDispatchDate = new Date().toISOString().slice(0, 10);
  protected newDriverName = '';
  protected newVehicleNo = '';
  protected lineSelected: Record<string, boolean> = {};
  protected lineQty: Record<string, number> = {};

  ngOnInit(): void { this.refresh(); }

  toggleForm(): void { this.showForm.update(v => !v); }

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
    this.showForm.set(false);
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

  private run(op: Observable<PackingList>, msg: string): void {
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
