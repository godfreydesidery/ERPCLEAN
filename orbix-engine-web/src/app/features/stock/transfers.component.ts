import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchPickerComponent, BranchSelectedEvent } from '../../core/ui/branch-picker.component';
import { ItemTypeaheadComponent, ItemSelectedEvent } from '../procurement/item-typeahead.component';
import { StockService } from './stock.service';
import { StockTransfer, StockTransferLine } from './stock.models';

@Component({
  selector: 'orbix-stock-transfers',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DecimalPipe, BranchPickerComponent, ItemTypeaheadComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Stock</a> &rsaquo; Transfers
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Stock transfers</h1>
        <p class="text-secondary mb-0 small">{{ transfers().length }} transfer{{ transfers().length === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()"
              [attr.aria-expanded]="showForm()">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()" aria-hidden="true"></i>
        {{ showForm() ? 'Close form' : 'New transfer' }}
      </button>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2" role="alert">
        <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss error" (click)="error.set(null)"></button>
      </div>
    }

    @if (showForm()) {
      <div class="card border-0 shadow-sm mb-3" role="region" aria-label="New stock transfer form">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">New stock transfer</h2>
          <button class="btn-close btn-sm" aria-label="Close new transfer form" (click)="toggleForm()"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="create()" #f="ngForm" class="d-flex flex-column gap-3" novalidate>
            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend">
                <i class="bi bi-arrow-left-right text-secondary" aria-hidden="true"></i> Header
              </legend>
              <div class="row g-2">
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary" for="tr-number">Number</label>
                  <input id="tr-number" class="form-control font-monospace" name="num"
                         [(ngModel)]="newNumber" required placeholder="ST0001">
                </div>
                <div class="col-md-4">
                  <orbix-branch-picker
                    instanceId="transfer-from"
                    label="From branch"
                    [required]="true"
                    (branchSelected)="onFromBranch($event)"
                    (branchCleared)="newFrom = null">
                  </orbix-branch-picker>
                </div>
                <div class="col-md-4">
                  <orbix-branch-picker
                    instanceId="transfer-to"
                    label="To branch"
                    [required]="true"
                    (branchSelected)="onToBranch($event)"
                    (branchCleared)="newTo = null">
                  </orbix-branch-picker>
                </div>
              </div>
            </fieldset>

            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend">
                <i class="bi bi-list-ul text-secondary" aria-hidden="true"></i> First line
              </legend>
              <div class="row g-2">
                <div class="col-md-6">
                  <orbix-item-typeahead
                    instanceId="transfer-item"
                    (itemSelected)="onItemSelected($event)"
                    (itemCleared)="newItemId = null">
                  </orbix-item-typeahead>
                </div>
                <div class="col-md-6">
                  <label class="form-label small fw-semibold text-secondary" for="tr-qty">Quantity</label>
                  <input id="tr-qty" class="form-control text-end" type="number" step="0.0001" min="0.0001"
                         name="qt" [(ngModel)]="newQty" required>
                </div>
              </div>
            </fieldset>

            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy() || !newFrom || !newTo || !newItemId || !newNumber">
                @if (busy()) {
                  <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                  <span class="visually-hidden">Creating…</span>
                } @else {
                  <i class="bi bi-arrow-left-right" aria-hidden="true"></i>
                }
                Create transfer
              </button>
              <button type="button" class="btn btn-outline-secondary" (click)="toggleForm()">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    }

    <div class="row g-3 g-md-4">
      <!-- Left: transfer list -->
      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Transfers</h2>
            <span class="badge text-bg-light text-secondary">{{ transfers().length }}</span>
          </div>
          @if (transfers().length === 0) {
            <div class="p-5 text-center" role="status">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-arrow-left-right" aria-hidden="true"></i></div>
              <p class="small text-secondary mb-0">No transfers yet.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 tr-list" role="list">
              @for (t of transfers(); track t.id) {
                <li role="listitem">
                  <button type="button" class="tr-row"
                          [class.is-active]="selected()?.id === t.id"
                          [attr.aria-pressed]="selected()?.id === t.id"
                          [attr.aria-label]="'Transfer ' + t.number + ', status ' + t.status"
                          (click)="select(t)">
                    <div class="flex-grow-1 min-w-0">
                      <div class="d-flex align-items-center gap-2 mb-1">
                        <span class="badge text-bg-light border text-secondary font-monospace">{{ t.number }}</span>
                        <span class="status-badge status-badge--{{ t.status.toLowerCase() }}"
                              [attr.aria-label]="'Status: ' + t.status">
                          <span class="status-badge__dot" aria-hidden="true"></span>{{ t.status }}
                        </span>
                      </div>
                      <p class="small text-secondary mb-0">
                        {{ t.fromBranchId }} <i class="bi bi-arrow-right" aria-hidden="true"></i> {{ t.toBranchId }}
                        &bull; {{ t.lines.length }} line{{ t.lines.length === 1 ? '' : 's' }}
                      </p>
                    </div>
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      </div>

      <!-- Right: transfer detail -->
      <div class="col-12 col-lg-7">
        @if (selected(); as transfer) {

          <!-- Header card -->
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4">
              <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                <div>
                  <p class="small text-secondary mb-1">
                    Branch {{ transfer.fromBranchId }}
                    <i class="bi bi-arrow-right" aria-hidden="true"></i>
                    {{ transfer.toBranchId }}
                  </p>
                  <h2 class="h4 fw-bold mb-1 text-dark">{{ transfer.number }}</h2>
                  <span class="status-badge status-badge--{{ transfer.status.toLowerCase() }}"
                        [attr.aria-label]="'Status: ' + transfer.status">
                    <span class="status-badge__dot" aria-hidden="true"></span>{{ transfer.status }}
                  </span>
                </div>
                <div class="d-flex gap-2 flex-wrap">
                  @if (transfer.status === 'DRAFT') {
                    <button class="btn btn-sm btn-primary d-inline-flex align-items-center gap-1"
                            (click)="issue(transfer)" [disabled]="busy()"
                            title="Issue the transfer and deduct stock from the source branch">
                      @if (busy()) {
                        <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                      } @else {
                        <i class="bi bi-box-arrow-up" aria-hidden="true"></i>
                      }
                      Issue
                    </button>
                  }
                  @if (transfer.status === 'ISSUED' || transfer.status === 'IN_TRANSIT') {
                    <button class="btn btn-sm btn-success d-inline-flex align-items-center gap-1"
                            (click)="receive(transfer)" [disabled]="busy() || !hasReceiveDraft()"
                            title="Confirm receipt of goods and credit stock at the destination branch">
                      @if (busy()) {
                        <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                      } @else {
                        <i class="bi bi-box-arrow-in-down" aria-hidden="true"></i>
                      }
                      Confirm receipt
                    </button>
                  }
                  @if (transfer.status === 'RECEIVED') {
                    <button class="btn btn-sm btn-warning text-dark d-inline-flex align-items-center gap-1"
                            (click)="close(transfer)" [disabled]="busy()"
                            title="Close and reconcile this transfer">
                      @if (busy()) {
                        <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                      } @else {
                        <i class="bi bi-lock" aria-hidden="true"></i>
                      }
                      Close
                    </button>
                  }
                </div>
              </div>

              @if (transfer.issuedAt) {
                <dl class="row small mb-0">
                  <dt class="col-5 col-md-4 text-secondary">Issued</dt>
                  <dd class="col-7 col-md-8 mb-1">{{ transfer.issuedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                  @if (transfer.receivedAt) {
                    <dt class="col-5 col-md-4 text-secondary">Received</dt>
                    <dd class="col-7 col-md-8 mb-1">{{ transfer.receivedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                  }
                </dl>
              }
            </div>
          </div>

          <!-- Receive-mode hint banner -->
          @if (transfer.status === 'ISSUED' || transfer.status === 'IN_TRANSIT') {
            <div class="alert alert-info d-flex align-items-start gap-2 py-2 mb-3 small" role="note">
              <i class="bi bi-info-circle-fill flex-shrink-0 mt-1" aria-hidden="true"></i>
              <span>
                Enter the quantity actually received for each line. Quantities default to the
                issued amount — adjust any line where the physical count differs. Click
                <strong>Confirm receipt</strong> to credit stock at the destination branch.
              </span>
            </div>
          }

          <!-- Lines card -->
          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Lines</h3>
              <span class="badge text-bg-light text-secondary">{{ transfer.lines.length }}</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table"
                     [attr.aria-label]="'Lines for transfer ' + transfer.number">
                <caption class="visually-hidden">
                  Transfer lines for {{ transfer.number }}:
                  issued quantities, received quantities{{ transfer.status === 'RECEIVED' || transfer.status === 'CLOSED' ? ', and variance' : '' }}, and costs.
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Item ID</th>
                    <th scope="col" class="text-end">Issued</th>
                    <th scope="col" class="text-end" [attr.style]="receiveColumnWidth(transfer)">
                      @if (transfer.status === 'ISSUED' || transfer.status === 'IN_TRANSIT') {
                        Received (edit)
                      } @else {
                        Received
                      }
                    </th>
                    @if (transfer.status === 'RECEIVED' || transfer.status === 'CLOSED') {
                      <th scope="col" class="text-end">Variance</th>
                    }
                    <th scope="col" class="text-end">Cost</th>
                  </tr>
                </thead>
                <tbody>
                  @for (l of transfer.lines; track l.id) {
                    <tr [class.table-warning]="isVarianceLine(l)">
                      <td>
                        <span class="badge text-bg-light border text-secondary font-monospace small">
                          {{ l.itemId }}
                        </span>
                      </td>
                      <td class="text-end">{{ l.issuedQty | number:'1.0-4' }}</td>
                      <td class="text-end" [attr.style]="receiveColumnWidth(transfer)">
                        @if (transfer.status === 'ISSUED' || transfer.status === 'IN_TRANSIT') {
                          <input class="form-control form-control-sm text-end"
                                 type="number"
                                 step="0.0001" min="0"
                                 [name]="'recv-' + l.id"
                                 [(ngModel)]="receiveDraft[l.id]"
                                 [attr.aria-label]="'Received quantity for item ' + l.itemId"
                                 [class.border-warning]="receiveDraft[l.id] !== null && +receiveDraft[l.id]! !== l.issuedQty">
                        } @else {
                          <span class="fw-semibold">{{ l.receivedQty !== null ? (l.receivedQty | number:'1.0-4') : '—' }}</span>
                        }
                      </td>
                      @if (transfer.status === 'RECEIVED' || transfer.status === 'CLOSED') {
                        <td class="text-end">
                          @if (l.receivedQty !== null) {
                            @if (variance(l) === 0) {
                              <span class="text-success fw-semibold" title="No variance">0</span>
                            } @else {
                              <span class="badge rounded-pill text-bg-warning"
                                    [title]="variance(l) > 0 ? 'Surplus: received more than issued' : 'Shortage: received less than issued'">
                                {{ variance(l) > 0 ? '+' : '' }}{{ variance(l) | number:'1.0-4' }}
                              </span>
                            }
                          } @else {
                            <span class="text-secondary">—</span>
                          }
                        </td>
                      }
                      <td class="text-end small text-secondary">{{ l.costAmount | number:'1.2-2' }}</td>
                    </tr>
                  }
                </tbody>
                @if ((transfer.status === 'RECEIVED' || transfer.status === 'CLOSED') && totalVariance(transfer) !== 0) {
                  <tfoot>
                    <tr class="table-light">
                      <td colspan="2" class="text-end small fw-semibold text-secondary">Net variance</td>
                      <td class="text-end"></td>
                      <td class="text-end">
                        <span class="badge rounded-pill"
                              [class.text-bg-warning]="totalVariance(transfer) !== 0"
                              [class.text-bg-success]="totalVariance(transfer) === 0">
                          {{ totalVariance(transfer) > 0 ? '+' : '' }}{{ totalVariance(transfer) | number:'1.0-4' }}
                        </span>
                      </td>
                      <td></td>
                    </tr>
                  </tfoot>
                }
              </table>
            </div>

            <!-- Receive-mode footer with totals -->
            @if (transfer.status === 'ISSUED' || transfer.status === 'IN_TRANSIT') {
              <div class="card-footer bg-white border-top px-3 py-2 d-flex align-items-center justify-content-between small">
                <span class="text-secondary">
                  Draft variance:
                  @if (draftVarianceTotal(transfer) === 0) {
                    <span class="text-success fw-semibold">none</span>
                  } @else {
                    <span class="fw-semibold text-warning-emphasis">
                      {{ draftVarianceTotal(transfer) > 0 ? '+' : '' }}{{ draftVarianceTotal(transfer) | number:'1.0-4' }}
                    </span>
                    <span class="text-muted ms-1">(lines with differences are highlighted)</span>
                  }
                </span>
                <button class="btn btn-sm btn-success d-inline-flex align-items-center gap-1"
                        (click)="receive(transfer)"
                        [disabled]="busy() || !hasReceiveDraft()"
                        aria-label="Submit received quantities and post receipt">
                  @if (busy()) {
                    <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                  } @else {
                    <i class="bi bi-check-circle" aria-hidden="true"></i>
                  }
                  Confirm receipt
                </button>
              </div>
            }
          </div>

        } @else {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor" aria-hidden="true"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick a transfer</h2>
              <p class="small text-secondary mb-0">Or draft a new one to move stock between branches.</p>
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
      color: #374151; padding: 0 0.5rem; width: auto; margin-bottom: 0.5rem;
    }
    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    .tr-list { max-height: 70vh; overflow-y: auto; }
    .tr-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .tr-row:hover { background: #f8fafc; }
    .tr-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .tr-row:last-child { border-bottom: none; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }
    .simple-table tfoot td { padding: 0.5rem 1rem; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--draft      { background: #f3f4f6; color: #4b5563; }
    .status-badge--draft .status-badge__dot      { background: #9ca3af; }
    .status-badge--issued     { background: #e0ecff; color: #1d4ed8; }
    .status-badge--issued .status-badge__dot     { background: #3b82f6; }
    .status-badge--in_transit { background: #fef3c7; color: #92400e; }
    .status-badge--in_transit .status-badge__dot { background: #f59e0b; }
    .status-badge--received   { background: #fef9c3; color: #854d0e; }
    .status-badge--received .status-badge__dot   { background: #eab308; }
    .status-badge--closed     { background: #d1fae5; color: #047857; }
    .status-badge--closed .status-badge__dot     { background: #10b981; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #ede9fe; color: #6d28d9; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class TransfersComponent implements OnInit {
  private readonly stock = inject(StockService);
  private readonly auth = inject(AuthService);

  protected readonly transfers = signal<StockTransfer[]>([]);
  protected readonly selected = signal<StockTransfer | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showForm = signal(false);

  protected newNumber = '';
  protected newFrom: string | null = null;
  protected newTo: string | null = null;
  protected newItemId: string | null = null;
  protected newQty: number | null = null;
  protected receiveDraft: Record<string, number | null> = {};

  ngOnInit(): void { this.load(); }

  toggleForm(): void { this.showForm.update(v => !v); }

  onFromBranch(evt: BranchSelectedEvent): void { this.newFrom = evt.id; }
  onToBranch(evt: BranchSelectedEvent): void { this.newTo = evt.id; }
  onItemSelected(evt: ItemSelectedEvent): void { this.newItemId = evt.id; }

  select(transfer: StockTransfer): void {
    this.selected.set(transfer);
    this.receiveDraft = {};
    for (const l of transfer.lines) {
      this.receiveDraft[l.id] = l.receivedQty ?? l.issuedQty;
    }
  }

  /** True when at least one line has a non-null draft qty. */
  hasReceiveDraft(): boolean {
    return Object.values(this.receiveDraft).some(v => v !== null && v !== undefined);
  }

  /** Line-level variance (received – issued). */
  variance(line: StockTransferLine): number {
    if (line.receivedQty === null) return 0;
    return line.receivedQty - line.issuedQty;
  }

  /** Whether the line has a non-zero variance (used to highlight table rows). */
  isVarianceLine(line: StockTransferLine): boolean {
    return line.receivedQty !== null && line.receivedQty !== line.issuedQty;
  }

  /** Net variance across all received lines. */
  totalVariance(transfer: StockTransfer): number {
    return transfer.lines.reduce((sum, l) => sum + this.variance(l), 0);
  }

  /**
   * Draft variance (before submit) — compares the editable receiveDraft entries
   * against the issued quantities so the user can see the net diff while editing.
   */
  draftVarianceTotal(transfer: StockTransfer): number {
    return transfer.lines.reduce((sum, l) => {
      const recv = this.receiveDraft[l.id];
      return sum + ((recv !== null && recv !== undefined ? +recv : l.issuedQty) - l.issuedQty);
    }, 0);
  }

  receiveColumnWidth(transfer: StockTransfer): string {
    return transfer.status === 'ISSUED' || transfer.status === 'IN_TRANSIT'
      ? 'width: 160px'
      : '';
  }

  create(): void {
    if (!this.newFrom || !this.newTo || !this.newItemId) return;
    this.run(this.stock.createTransfer({
      number: this.newNumber.trim(),
      fromBranchId: this.newFrom,
      toBranchId: this.newTo,
      lines: [{ itemId: this.newItemId, issuedQty: Number(this.newQty) }]
    }), created => {
      this.newNumber = '';
      this.newItemId = null;
      this.newQty = null;
      this.showForm.set(false);
      this.load();
      this.select(created);
    });
  }

  issue(transfer: StockTransfer): void {
    this.run(this.stock.issueTransfer(transfer.uid), updated => this.refresh(updated));
  }

  receive(transfer: StockTransfer): void {
    const lines = transfer.lines
      .filter(l => this.receiveDraft[l.id] !== null && this.receiveDraft[l.id] !== undefined)
      .map(l => ({ lineId: l.id, receivedQty: Number(this.receiveDraft[l.id]) }));
    if (lines.length === 0) return;
    this.run(this.stock.receiveTransfer(transfer.uid, { lines }), updated => this.refresh(updated));
  }

  close(transfer: StockTransfer): void {
    this.run(this.stock.closeTransfer(transfer.uid), updated => this.refresh(updated));
  }

  private refresh(updated: StockTransfer): void {
    this.load();
    this.select(updated);
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => { this.busy.set(false); onSuccess(value); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private load(): void {
    this.stock.listTransfers().subscribe({
      next: list => this.transfers.set(list),
      error: err => this.showError(err)
    });
  }

  private showError(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      const envelope = err.error as ApiResponse<unknown> | null;
      const msg = envelope?.message ?? `Request failed (${err.status})`;
      if (err.status === 400 && msg.includes('STOCK.OVERSELL')) {
        if (this.auth.hasPermission('STOCK.OVERSELL')) {
          this.error.set(
            'Stock would go negative on issue. Top up the source branch via a ' +
            'STOCK.OVERSELL adjustment first, then re-issue this transfer.'
          );
        } else {
          this.error.set(
            'Stock would go negative on issue. Ask a supervisor to authorise ' +
            'a top-up adjustment (STOCK.OVERSELL) before this transfer can be issued.'
          );
        }
        return;
      }
      this.error.set(msg);
    } else {
      this.error.set('Unexpected error');
    }
  }
}
