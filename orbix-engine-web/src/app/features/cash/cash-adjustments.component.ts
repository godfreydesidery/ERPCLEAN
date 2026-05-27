import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { HasPermissionDirective } from '../../core/auth/has-permission.directive';
import { CashService } from './services/cash.service';
import { CASH_ACCOUNTS, CashAccount, CashAdjustment, CashDirection } from './models/cash.models';

/**
 * Supervisor cash adjustments. List + post form gated by
 * {@code CASH.ADJUSTMENT.POST}; archive button by {@code CASH.ADJUSTMENT.ARCHIVE}.
 * Reversal posts a compensating cash entry — UI marks the original row as
 * "Reversed" once the audit-doc carries {@code reversedAt}.
 */
@Component({
  selector: 'orbix-cash-adjustments',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, HasPermissionDirective],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Cash</a> &rsaquo; Adjustments
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Cash adjustments</h1>
        <p class="text-secondary mb-0 small">Supervisor corrections — every adjustment requires a reason and is auditable.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2"
              *orbixHasPermission="'CASH.ADJUSTMENT.POST'"
              (click)="toggleForm()"
              [title]="showForm() ? 'Close the form without saving' : 'Post a new adjustment'">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New adjustment' }}
      </button>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
      </div>
    }

    @if (showForm()) {
      <div class="card border-0 shadow-sm mb-3" *orbixHasPermission="'CASH.ADJUSTMENT.POST'">
        <div class="card-header bg-white border-bottom p-3">
          <h2 class="h6 fw-bold mb-0 text-dark">New adjustment</h2>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="submit()" #f="ngForm" class="row g-3">
            <div class="col-md-3">
              <label class="form-label small fw-semibold text-secondary" for="adj-account">Account</label>
              <select id="adj-account" class="form-select" name="account" [(ngModel)]="formAccount" required>
                @for (acc of accounts; track acc) {
                  <option [ngValue]="acc">{{ acc }}</option>
                }
              </select>
            </div>
            <div class="col-md-2">
              <label class="form-label small fw-semibold text-secondary" for="adj-direction">Direction</label>
              <select id="adj-direction" class="form-select" name="direction" [(ngModel)]="formDirection" required>
                <option ngValue="IN">IN</option>
                <option ngValue="OUT">OUT</option>
              </select>
            </div>
            <div class="col-md-3">
              <label class="form-label small fw-semibold text-secondary" for="adj-amount">Amount (TZS)</label>
              <input id="adj-amount" class="form-control text-end" type="number" min="0.01" step="0.01"
                     name="amount" [(ngModel)]="formAmount" required>
            </div>
            <div class="col-12">
              <label class="form-label small fw-semibold text-secondary" for="adj-reason">Reason</label>
              <textarea id="adj-reason" class="form-control" name="reason" rows="2"
                        [(ngModel)]="formReason" required maxlength="2000"
                        placeholder="Explain the correction — visible in the audit trail."></textarea>
            </div>
            <div class="col-12 d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy() || f.invalid">
                @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-save"></i> }
                Post adjustment
              </button>
              <button type="button" class="btn btn-outline-secondary" (click)="toggleForm()">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    }

    <div class="card border-0 shadow-sm mb-3">
      <div class="card-body p-3 d-flex flex-wrap align-items-end gap-3">
        <div>
          <label class="form-label small fw-semibold text-secondary mb-1" for="ca-date">Business date</label>
          <input id="ca-date" class="form-control form-control-sm" type="date"
                 [(ngModel)]="businessDate" (ngModelChange)="load()">
        </div>
        <div class="ms-auto small text-secondary">
          @if (branchId() !== null) {
            Branch <span class="badge text-bg-light border text-secondary font-monospace">#{{ branchId() }}</span>
          } @else {
            <i class="bi bi-info-circle"></i> Pick a branch in the top bar
          }
        </div>
      </div>
    </div>

    <div class="card border-0 shadow-sm overflow-hidden">
      <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
        <h2 class="h6 fw-bold mb-0 text-dark">Adjustments</h2>
        <span class="badge text-bg-light text-secondary">{{ adjustments().length }}</span>
      </div>

      @if (loading()) {
        <div class="p-5 text-center">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading…</span>
          </div>
        </div>
      } @else if (adjustments().length === 0) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-pencil-square"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No adjustments</h2>
          <p class="small text-secondary mb-0">No adjustments posted on {{ businessDate | date:'dd/MM/yyyy' }}.</p>
        </div>
      } @else {
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 simple-table">
            <thead>
              <tr>
                <th>Posted</th>
                <th>Account</th>
                <th>Direction</th>
                <th class="text-end">Amount</th>
                <th>Reason</th>
                <th>Status</th>
                <th class="text-end actions-col"></th>
              </tr>
            </thead>
            <tbody>
              @for (adj of adjustments(); track adj.uid) {
                <tr [class.row-reversed]="adj.reversedAt">
                  <td class="small text-secondary">
                    {{ adj.at | date:'dd/MM/yyyy HH:mm' }}
                    <div class="small text-secondary">By #{{ adj.postedBy }}</div>
                  </td>
                  <td>
                    <span class="account-pill account-pill--{{ adj.account.toLowerCase() }}">{{ adj.account }}</span>
                  </td>
                  <td>
                    <span class="direction-badge direction-badge--{{ adj.direction.toLowerCase() }}">
                      <i class="bi" [class.bi-arrow-down-left]="adj.direction === 'IN'"
                                    [class.bi-arrow-up-right]="adj.direction === 'OUT'"></i>
                      {{ adj.direction }}
                    </span>
                  </td>
                  <td class="text-end fw-semibold"
                      [class.text-success]="adj.direction === 'IN' && !adj.reversedAt"
                      [class.text-danger]="adj.direction === 'OUT' && !adj.reversedAt"
                      [class.text-secondary]="adj.reversedAt">
                    {{ asNum(adj.amount) | number:'1.2-2' }}
                    <div class="small text-secondary fw-normal">{{ adj.currencyCode }}</div>
                  </td>
                  <td class="small text-secondary reason-cell" [title]="adj.reason">{{ adj.reason }}</td>
                  <td>
                    @if (adj.reversedAt) {
                      <span class="status-badge status-badge--reversed" [title]="reversedTooltip(adj)">
                        <span class="status-badge__dot"></span>Reversed
                      </span>
                    } @else {
                      <span class="status-badge status-badge--active">
                        <span class="status-badge__dot"></span>Active
                      </span>
                    }
                  </td>
                  <td class="text-end actions-col">
                    @if (!adj.reversedAt) {
                      <button class="btn btn-sm btn-outline-danger"
                              *orbixHasPermission="'CASH.ADJUSTMENT.ARCHIVE'"
                              [disabled]="busy()"
                              (click)="archive(adj)"
                              title="Reverse this adjustment. Posts a compensating cash entry; the original stays in the audit trail.">
                        <i class="bi bi-arrow-counterclockwise"></i> Reverse
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.75rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }
    .simple-table .actions-col { width: 1%; white-space: nowrap; }

    .row-reversed { opacity: 0.65; }

    .account-pill {
      display: inline-flex; align-items: center;
      padding: 0.2rem 0.55rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .account-pill--till         { background: #fef3c7; color: #92400e; }
    .account-pill--cash_box     { background: #e0ecff; color: #1d4ed8; }
    .account-pill--bank         { background: #d1fae5; color: #047857; }
    .account-pill--mobile_money { background: #ede9fe; color: #6d28d9; }

    .direction-badge {
      display: inline-flex; align-items: center; gap: 0.25rem;
      padding: 0.15rem 0.45rem; border-radius: 6px;
      font-size: 0.72rem; font-weight: 600;
    }
    .direction-badge--in  { background: #ecfdf5; color: #047857; }
    .direction-badge--out { background: #fef2f2; color: #b91c1c; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.72rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--active   { background: #d1fae5; color: #047857; }
    .status-badge--active .status-badge__dot   { background: #10b981; }
    .status-badge--reversed { background: #f3f4f6; color: #4b5563; }
    .status-badge--reversed .status-badge__dot { background: #9ca3af; }

    .reason-cell {
      max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #fef3c7; color: #92400e; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }
  `]
})
export class CashAdjustmentsComponent implements OnInit {
  private readonly cash = inject(CashService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly adjustments = signal<CashAdjustment[]>([]);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showForm = signal(false);

  protected readonly accounts = CASH_ACCOUNTS;
  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected businessDate = todayIso();
  protected formAccount: CashAccount = 'CASH_BOX';
  protected formDirection: CashDirection = 'IN';
  protected formAmount: number | null = null;
  protected formReason = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const branchId = this.branchId();
    if (branchId === null) { this.adjustments.set([]); return; }
    this.loading.set(true);
    this.error.set(null);
    this.cash.listAdjustments(branchId, this.businessDate).subscribe({
      next: list => { this.adjustments.set(list); this.loading.set(false); },
      error: err => { this.loading.set(false); this.showError(err); }
    });
  }

  toggleForm(): void {
    this.showForm.update(v => !v);
    if (!this.showForm()) this.resetForm();
  }

  submit(): void {
    const branchId = this.branchId();
    if (branchId === null) { this.error.set('Pick a branch before posting an adjustment.'); return; }
    if (this.formAmount === null || this.formAmount <= 0) { return; }
    this.busy.set(true);
    this.error.set(null);
    this.cash.postAdjustment({
      branchId,
      account: this.formAccount,
      direction: this.formDirection,
      amount: this.formAmount,
      reason: this.formReason
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.resetForm();
        this.showForm.set(false);
        this.load();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  archive(adj: CashAdjustment): void {
    if (this.busy()) return;
    if (!confirm(`Reverse this ${adj.direction} adjustment for ${this.asNum(adj.amount).toLocaleString()} ${adj.currencyCode}? A compensating cash entry will be posted.`)) return;
    this.busy.set(true);
    this.error.set(null);
    this.cash.archiveAdjustment(adj.uid).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  protected asNum(value: string): number {
    return Number.parseFloat(value ?? '0') || 0;
  }

  protected reversedTooltip(adj: CashAdjustment): string {
    if (!adj.reversedAt) return '';
    return `Reversed ${adj.reversedAt} by #${adj.reversedBy} (entry #${adj.reversedByEntryId})`;
  }

  private resetForm(): void {
    this.formAccount = 'CASH_BOX';
    this.formDirection = 'IN';
    this.formAmount = null;
    this.formReason = '';
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

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}
