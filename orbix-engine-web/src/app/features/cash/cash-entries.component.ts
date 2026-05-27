import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { CashService } from './services/cash.service';
import { CASH_ACCOUNTS, CashAccount, CashEntry } from './models/cash.models';

/**
 * Append-only cash-entries list. Gated by {@code CASH.ENTRY.READ}. No archive
 * lifecycle — the ledger is immutable; reversals appear as fresh
 * compensating entries (refType ending in {@code Reversal}).
 */
@Component({
  selector: 'orbix-cash-entries',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Cash</a> &rsaquo; Ledger
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Cash ledger</h1>
        <p class="text-secondary mb-0 small">Every cash movement, append-only.</p>
      </div>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
      </div>
    }

    <div class="card border-0 shadow-sm mb-3">
      <div class="card-body p-3 d-flex flex-wrap align-items-end gap-3">
        <div>
          <label class="form-label small fw-semibold text-secondary mb-1" for="ce-date">Business date</label>
          <input id="ce-date" class="form-control form-control-sm" type="date"
                 [(ngModel)]="businessDate" (ngModelChange)="onChange()">
        </div>
        <div>
          <label class="form-label small fw-semibold text-secondary mb-1" for="ce-account">Account</label>
          <select id="ce-account" class="form-select form-select-sm"
                  [(ngModel)]="accountFilter" (ngModelChange)="onChange()">
            <option [ngValue]="null">All accounts</option>
            @for (acc of accounts; track acc) {
              <option [ngValue]="acc">{{ acc }}</option>
            }
          </select>
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
        <h2 class="h6 fw-bold mb-0 text-dark">Entries</h2>
        <span class="badge text-bg-light text-secondary">{{ entries().length }}</span>
      </div>

      @if (loading()) {
        <div class="p-5 text-center">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading…</span>
          </div>
        </div>
      } @else if (entries().length === 0) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-list-ul"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No entries</h2>
          <p class="small text-secondary mb-0">No cash movements match the current filters.</p>
        </div>
      } @else {
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 simple-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Account</th>
                <th>Ref</th>
                <th>Direction</th>
                <th class="text-end">Amount</th>
                <th>Currency</th>
                <th class="small text-secondary">Notes</th>
              </tr>
            </thead>
            <tbody>
              @for (entry of entries(); track entry.uid) {
                <tr>
                  <td class="small text-secondary">{{ entry.at | date:'dd/MM/yyyy HH:mm:ss' }}</td>
                  <td>
                    <span class="account-pill account-pill--{{ entry.account.toLowerCase() }}">{{ entry.account }}</span>
                  </td>
                  <td>
                    <span class="badge text-bg-light border text-secondary font-monospace">{{ entry.refType }}</span>
                    @if (entry.refId) {
                      <span class="small text-secondary ms-1 font-monospace">#{{ entry.refId }}</span>
                    }
                  </td>
                  <td>
                    <span class="direction-badge direction-badge--{{ entry.direction.toLowerCase() }}">
                      <i class="bi" [class.bi-arrow-down-left]="entry.direction === 'IN'"
                                    [class.bi-arrow-up-right]="entry.direction === 'OUT'"></i>
                      {{ entry.direction }}
                    </span>
                  </td>
                  <td class="text-end fw-semibold"
                      [class.text-success]="entry.direction === 'IN'"
                      [class.text-danger]="entry.direction === 'OUT'">
                    {{ asNum(entry.amount) | number:'1.2-2' }}
                  </td>
                  <td class="font-monospace small text-secondary">{{ entry.currencyCode }}</td>
                  <td class="small text-secondary notes-cell" [title]="entry.notes ?? ''">{{ entry.notes ?? '—' }}</td>
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
    .simple-table tbody td { padding: 0.6rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }

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

    .notes-cell {
      max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }
  `]
})
export class CashEntriesComponent implements OnInit {
  private readonly cash = inject(CashService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly entries = signal<CashEntry[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly accounts = CASH_ACCOUNTS;
  protected accountFilter: CashAccount | null = null;
  protected businessDate = todayIso();

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  ngOnInit(): void {
    this.load();
  }

  onChange(): void {
    this.load();
  }

  protected asNum(value: string): number {
    return Number.parseFloat(value ?? '0') || 0;
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.cash.listEntries(this.branchId(), this.accountFilter, this.businessDate).subscribe({
      next: list => { this.entries.set(list); this.loading.set(false); },
      error: err => { this.loading.set(false); this.showError(err); }
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

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}
