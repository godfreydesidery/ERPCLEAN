import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { CashService } from './services/cash.service';
import { CashBook } from './models/cash.models';

/**
 * Read-only per-account closing-balance projection for the trading day.
 * Gated by {@code CASH.BOOK.READ}; the controller also accepts the coarse
 * legacy {@code CASH.READ} for back-compat.
 */
@Component({
  selector: 'orbix-cash-books',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Cash</a> &rsaquo; Cash book
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Cash book</h1>
        <p class="text-secondary mb-0 small">Per-account closing balances for the selected business day.</p>
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
      <div class="card-body p-3 d-flex flex-wrap align-items-center gap-3">
        <div>
          <label class="form-label small fw-semibold text-secondary mb-1" for="cb-date">Business date</label>
          <input id="cb-date" class="form-control form-control-sm" type="date"
                 [(ngModel)]="businessDate" (ngModelChange)="onChange()">
        </div>
        @if (branchId() !== null) {
          <div class="text-secondary small mt-3">
            Branch <span class="badge text-bg-light border text-secondary font-monospace">#{{ branchId() }}</span>
          </div>
        } @else {
          <div class="text-secondary small mt-3">
            <i class="bi bi-info-circle"></i> Pick a branch in the top bar to scope the cash book.
          </div>
        }
      </div>
    </div>

    <div class="card border-0 shadow-sm overflow-hidden">
      <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
        <h2 class="h6 fw-bold mb-0 text-dark">Closing balances</h2>
        <span class="badge text-bg-light text-secondary">{{ books().length }}</span>
      </div>

      @if (loading()) {
        <div class="p-5 text-center">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading…</span>
          </div>
        </div>
      } @else if (books().length === 0) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-journal-bookmark"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No cash-book rows</h2>
          <p class="small text-secondary mb-0">No cash movements have posted on {{ businessDate | date:'dd/MM/yyyy' }}.</p>
        </div>
      } @else {
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 simple-table">
            <thead>
              <tr>
                <th>Branch</th>
                <th>Account</th>
                <th>Currency</th>
                <th class="text-end">Opening</th>
                <th class="text-end">In</th>
                <th class="text-end">Out</th>
                <th class="text-end">Closing</th>
              </tr>
            </thead>
            <tbody>
              @for (book of books(); track book.uid) {
                <tr>
                  <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ book.branchId }}</span></td>
                  <td>
                    <span class="account-pill account-pill--{{ book.account.toLowerCase() }}">{{ book.account }}</span>
                  </td>
                  <td class="font-monospace small text-secondary">{{ book.currencyCode }}</td>
                  <td class="text-end fw-normal text-secondary">{{ asNum(book.openingAmount) | number:'1.2-2' }}</td>
                  <td class="text-end text-success">{{ asNum(book.inAmount) | number:'1.2-2' }}</td>
                  <td class="text-end text-danger">{{ asNum(book.outAmount) | number:'1.2-2' }}</td>
                  <td class="text-end fw-bold text-dark">{{ asNum(book.closingAmount) | number:'1.2-2' }}</td>
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

    .account-pill {
      display: inline-flex; align-items: center;
      padding: 0.2rem 0.6rem; border-radius: 999px;
      font-size: 0.72rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .account-pill--till         { background: #fef3c7; color: #92400e; }
    .account-pill--cash_box     { background: #e0ecff; color: #1d4ed8; }
    .account-pill--bank         { background: #d1fae5; color: #047857; }
    .account-pill--mobile_money { background: #ede9fe; color: #6d28d9; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .form-control:focus { border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12); }
  `]
})
export class CashBooksComponent implements OnInit {
  private readonly cash = inject(CashService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly books = signal<CashBook[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected businessDate = todayIso();

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
    this.cash.listCashBook(this.branchId(), this.businessDate).subscribe({
      next: list => { this.books.set(list); this.loading.set(false); },
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
