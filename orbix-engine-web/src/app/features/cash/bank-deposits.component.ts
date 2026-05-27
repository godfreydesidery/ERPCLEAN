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
import { BankDeposit } from './models/cash.models';

/**
 * EOD bank deposits. Posts paired OUT-CASH_BOX + IN-BANK entries. List + post
 * gated by {@code CASH.BANK_DEPOSIT.POST}; archive by
 * {@code CASH.BANK_DEPOSIT.ARCHIVE}. Reversal posts a compensating pair under
 * the {@code BankDepositReversal} ref_type.
 */
@Component({
  selector: 'orbix-bank-deposits',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, HasPermissionDirective],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Cash</a> &rsaquo; Bank deposits
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Bank deposits</h1>
        <p class="text-secondary mb-0 small">End-of-day cash transfers from the safe to the bank.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2"
              *orbixHasPermission="'CASH.BANK_DEPOSIT.POST'"
              (click)="toggleForm()"
              [title]="showForm() ? 'Close the form without saving' : 'Record a new deposit'">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New deposit' }}
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
      <div class="card border-0 shadow-sm mb-3" *orbixHasPermission="'CASH.BANK_DEPOSIT.POST'">
        <div class="card-header bg-white border-bottom p-3">
          <h2 class="h6 fw-bold mb-0 text-dark">New deposit</h2>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="submit()" #f="ngForm" class="row g-3">
            <div class="col-md-4">
              <label class="form-label small fw-semibold text-secondary" for="bd-amount">Amount (TZS)</label>
              <input id="bd-amount" class="form-control text-end" type="number" min="0.01" step="0.01"
                     name="amount" [(ngModel)]="formAmount" required>
            </div>
            <div class="col-md-8">
              <label class="form-label small fw-semibold text-secondary" for="bd-reference">Bank slip / reference</label>
              <input id="bd-reference" class="form-control" type="text" maxlength="80"
                     name="reference" [(ngModel)]="formReference" required
                     placeholder="Slip number or transaction reference">
            </div>
            <div class="col-12">
              <label class="form-label small fw-semibold text-secondary" for="bd-notes">Notes (optional)</label>
              <textarea id="bd-notes" class="form-control" name="notes" rows="2" maxlength="2000"
                        [(ngModel)]="formNotes"
                        placeholder="Anything that helps reconcile against the bank statement later."></textarea>
            </div>
            <div class="col-12 d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy() || f.invalid">
                @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-bank"></i> }
                Record deposit
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
          <label class="form-label small fw-semibold text-secondary mb-1" for="bd-date">Business date</label>
          <input id="bd-date" class="form-control form-control-sm" type="date"
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
        <h2 class="h6 fw-bold mb-0 text-dark">Deposits</h2>
        <span class="badge text-bg-light text-secondary">{{ deposits().length }}</span>
      </div>

      @if (loading()) {
        <div class="p-5 text-center">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading…</span>
          </div>
        </div>
      } @else if (deposits().length === 0) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-bank"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No deposits</h2>
          <p class="small text-secondary mb-0">No bank deposits recorded on {{ businessDate | date:'dd/MM/yyyy' }}.</p>
        </div>
      } @else {
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 simple-table">
            <thead>
              <tr>
                <th>Posted</th>
                <th>Reference</th>
                <th class="text-end">Amount</th>
                <th>Notes</th>
                <th>Status</th>
                <th class="text-end actions-col"></th>
              </tr>
            </thead>
            <tbody>
              @for (dep of deposits(); track dep.uid) {
                <tr [class.row-reversed]="dep.reversedAt">
                  <td class="small text-secondary">
                    {{ dep.at | date:'dd/MM/yyyy HH:mm' }}
                    <div class="small text-secondary">By #{{ dep.postedBy }}</div>
                  </td>
                  <td>
                    <span class="badge text-bg-light border text-secondary font-monospace">{{ dep.reference }}</span>
                  </td>
                  <td class="text-end fw-semibold"
                      [class.text-dark]="!dep.reversedAt"
                      [class.text-secondary]="dep.reversedAt">
                    {{ asNum(dep.amount) | number:'1.2-2' }}
                    <div class="small text-secondary fw-normal">{{ dep.currencyCode }}</div>
                  </td>
                  <td class="small text-secondary notes-cell" [title]="dep.notes ?? ''">{{ dep.notes ?? '—' }}</td>
                  <td>
                    @if (dep.reversedAt) {
                      <span class="status-badge status-badge--reversed" [title]="reversedTooltip(dep)">
                        <span class="status-badge__dot"></span>Reversed
                      </span>
                    } @else {
                      <span class="status-badge status-badge--active">
                        <span class="status-badge__dot"></span>Active
                      </span>
                    }
                  </td>
                  <td class="text-end actions-col">
                    @if (!dep.reversedAt) {
                      <button class="btn btn-sm btn-outline-danger"
                              *orbixHasPermission="'CASH.BANK_DEPOSIT.ARCHIVE'"
                              [disabled]="busy()"
                              (click)="archive(dep)"
                              title="Reverse this deposit. Posts a compensating IN/OUT pair to undo the transfer.">
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

    .notes-cell {
      max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }

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

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #d1fae5; color: #047857; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }
  `]
})
export class BankDepositsComponent implements OnInit {
  private readonly cash = inject(CashService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly deposits = signal<BankDeposit[]>([]);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showForm = signal(false);

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected businessDate = todayIso();
  protected formAmount: number | null = null;
  protected formReference = '';
  protected formNotes = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const branchId = this.branchId();
    if (branchId === null) { this.deposits.set([]); return; }
    this.loading.set(true);
    this.error.set(null);
    this.cash.listDeposits(branchId, this.businessDate).subscribe({
      next: list => { this.deposits.set(list); this.loading.set(false); },
      error: err => { this.loading.set(false); this.showError(err); }
    });
  }

  toggleForm(): void {
    this.showForm.update(v => !v);
    if (!this.showForm()) this.resetForm();
  }

  submit(): void {
    const branchId = this.branchId();
    if (branchId === null) { this.error.set('Pick a branch before recording a deposit.'); return; }
    if (this.formAmount === null || this.formAmount <= 0) return;
    this.busy.set(true);
    this.error.set(null);
    this.cash.postDeposit({
      branchId,
      amount: this.formAmount,
      reference: this.formReference,
      notes: this.formNotes.trim() ? this.formNotes : null
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

  archive(dep: BankDeposit): void {
    if (this.busy()) return;
    if (!confirm(`Reverse the deposit ${dep.reference} for ${this.asNum(dep.amount).toLocaleString()} ${dep.currencyCode}? A compensating cash-entry pair will be posted.`)) return;
    this.busy.set(true);
    this.error.set(null);
    this.cash.archiveDeposit(dep.uid).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  protected asNum(value: string): number {
    return Number.parseFloat(value ?? '0') || 0;
  }

  protected reversedTooltip(dep: BankDeposit): string {
    if (!dep.reversedAt) return '';
    return `Reversed ${dep.reversedAt} by #${dep.reversedBy}`;
  }

  private resetForm(): void {
    this.formAmount = null;
    this.formReference = '';
    this.formNotes = '';
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
