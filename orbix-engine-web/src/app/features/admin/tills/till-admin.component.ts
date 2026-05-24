import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../../core/api/api-response';
import { AuthService } from '../../../core/auth/auth.service';
import { BranchService } from '../../../core/branch/branch.service';
import { TillAdminService } from './till-admin.service';
import { Till, TillSession } from './till-admin.models';

@Component({
  selector: 'orbix-till-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Admin</a> &rsaquo; Tills
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">POS tills &amp; sessions</h1>
        <p class="text-secondary mb-0 small">{{ tills().length }} till{{ tills().length === 1 ? '' : 's' }} · {{ sessions().length }} session{{ sessions().length === 1 ? '' : 's' }}.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleNewTill()">
        <i class="bi" [class.bi-plus-lg]="!showNewTill()" [class.bi-x-lg]="showNewTill()"></i>
        {{ showNewTill() ? 'Close form' : 'New till' }}
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

    @if (showNewTill()) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">New till</h2>
          <button class="btn-close btn-sm" (click)="toggleNewTill()"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="createTill()" #f="ngForm" class="d-flex flex-column gap-3">
            <div class="row g-2">
              <div class="col-md-3">
                <label class="form-label small fw-semibold text-secondary">Code</label>
                <input class="form-control font-monospace text-uppercase" name="cd" [(ngModel)]="newCode" required placeholder="TILL01">
              </div>
              <div class="col-md-5">
                <label class="form-label small fw-semibold text-secondary">Name</label>
                <input class="form-control" name="nm" [(ngModel)]="newName" required placeholder="Main counter">
              </div>
              <div class="col-md-4">
                <label class="form-label small fw-semibold text-secondary">Default price list ID</label>
                <input class="form-control" type="number" name="pl" [(ngModel)]="newPriceListId" required>
              </div>
            </div>
            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy() || f.invalid">
                @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-plus-lg"></i> }
                Create till
              </button>
              <button type="button" class="btn btn-outline-secondary" (click)="toggleNewTill()">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    }

    <div class="row g-3 g-md-4">
      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Tills</h2>
            <span class="badge text-bg-light text-secondary">{{ tills().length }}</span>
          </div>
          @if (tills().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cash-stack"></i></div>
              <p class="small text-secondary mb-0">No tills registered.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 tl-list">
              @for (t of tills(); track t.id) {
                <li>
                  <button type="button" class="tl-row"
                          [class.is-active]="selectedTill()?.id === t.id"
                          (click)="selectTill(t)">
                    <div class="flex-grow-1 min-w-0">
                      <div class="d-flex align-items-center gap-2 mb-1">
                        <span class="badge text-bg-light border text-secondary font-monospace">{{ t.code }}</span>
                        <span class="status-badge status-badge--{{ t.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ t.status }}
                        </span>
                      </div>
                      <p class="fw-semibold text-dark mb-0 text-truncate">{{ t.name }}</p>
                      <p class="small text-secondary mb-0">Price list #{{ t.defaultPriceListId }}</p>
                    </div>
                  </button>
                </li>
              }
            </ul>
          }
        </div>

        @if (selectedTill(); as t) {
          <div class="card border-0 shadow-sm mt-3">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h2 class="h6 fw-bold mb-0 text-dark">{{ t.code }} actions</h2>
              <span class="status-badge status-badge--{{ t.status.toLowerCase() }}">
                <span class="status-badge__dot"></span>{{ t.status }}
              </span>
            </div>
            <div class="card-body p-3 d-flex flex-column gap-3">
              <form (ngSubmit)="openSession(t)" #of="ngForm" class="d-flex flex-column gap-2">
                <label class="form-label small fw-semibold text-secondary mb-0">Open new session</label>
                <div class="input-group">
                  <span class="input-group-text">Float</span>
                  <input class="form-control text-end" type="number" step="0.0001" min="0"
                         name="of" [(ngModel)]="newFloat" required>
                  <button class="btn btn-primary d-inline-flex align-items-center gap-1" [disabled]="busy() || of.invalid">
                    <i class="bi bi-play-fill"></i> Open
                  </button>
                </div>
              </form>
              <div class="d-flex gap-2 pt-2 border-top">
                @if (t.status === 'ACTIVE') {
                  <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1"
                          [disabled]="busy()" (click)="deactivate(t)">
                    <i class="bi bi-pause-circle"></i> Deactivate till
                  </button>
                } @else {
                  <button class="btn btn-sm btn-outline-success d-inline-flex align-items-center gap-1"
                          [disabled]="busy()" (click)="activate(t)">
                    <i class="bi bi-play-circle"></i> Activate till
                  </button>
                }
              </div>
            </div>
          </div>
        }
      </div>

      <div class="col-12 col-lg-7">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Sessions</h2>
            <span class="badge text-bg-light text-secondary">{{ sessions().length }}</span>
          </div>
          @if (sessions().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-clock-history"></i></div>
              <p class="small text-secondary mb-0">No sessions yet.</p>
            </div>
          } @else {
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>#</th><th>Till</th><th>Day</th><th>Status</th>
                    <th class="text-end">Float</th>
                    <th class="text-end">Expected</th>
                    <th class="text-end">Declared</th>
                    <th class="text-end">Variance</th>
                    <th class="actions-col"></th>
                  </tr>
                </thead>
                <tbody>
                  @for (s of sessions(); track s.id) {
                    <tr [class.row-warn]="s.varianceAmount !== null && abs(s.varianceAmount) > 0">
                      <td class="small text-secondary">#{{ s.id }}</td>
                      <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ s.tillId }}</span></td>
                      <td class="small text-secondary">{{ s.businessDate | date:'mediumDate' }}</td>
                      <td>
                        <span class="status-badge status-badge--{{ s.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ s.status }}
                        </span>
                      </td>
                      <td class="text-end">{{ s.openingFloatAmount | number:'1.2-2' }}</td>
                      <td class="text-end small text-secondary">{{ s.expectedCashAmount ?? '—' }}</td>
                      <td class="text-end small text-secondary">{{ s.declaredCashAmount ?? '—' }}</td>
                      <td class="text-end fw-semibold"
                          [class.text-danger]="(s.varianceAmount ?? 0) < 0"
                          [class.text-success]="(s.varianceAmount ?? 0) > 0"
                          [class.text-secondary]="(s.varianceAmount ?? 0) === 0">
                        {{ s.varianceAmount ?? '—' }}
                      </td>
                      <td class="actions-col">
                        @if (s.status === 'OPEN') {
                          <button class="btn btn-sm btn-outline-primary d-inline-flex align-items-center gap-1"
                                  [disabled]="busy()" (click)="closeSession(s)">
                            <i class="bi bi-lock"></i> Close
                          </button>
                        }
                        @if (s.status === 'CLOSED') {
                          <button class="btn btn-sm btn-outline-success d-inline-flex align-items-center gap-1"
                                  [disabled]="busy()" (click)="reconcileSession(s)">
                            <i class="bi bi-check2-circle"></i> Reconcile
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
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .min-w-0 { min-width: 0; }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    .tl-list { max-height: 50vh; overflow-y: auto; }
    .tl-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .tl-row:hover { background: #f8fafc; }
    .tl-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .tl-row:last-child { border-bottom: none; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.65rem 0.75rem;
    }
    .simple-table tbody td { padding: 0.65rem 0.75rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }
    .simple-table tbody tr.row-warn { background: #fffbeb; }
    .simple-table tbody tr.row-warn:hover { background: #fef3c7; }
    .simple-table .actions-col { width: 1%; white-space: nowrap; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.2rem 0.55rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 5px; height: 5px; border-radius: 50%; }
    .status-badge--active     { background: #d1fae5; color: #047857; }
    .status-badge--active .status-badge__dot     { background: #10b981; }
    .status-badge--inactive   { background: #f3f4f6; color: #4b5563; }
    .status-badge--inactive .status-badge__dot   { background: #9ca3af; }
    .status-badge--open       { background: #e0ecff; color: #1d4ed8; }
    .status-badge--open .status-badge__dot       { background: #3b82f6; }
    .status-badge--closed     { background: #fef3c7; color: #92400e; }
    .status-badge--closed .status-badge__dot     { background: #f59e0b; }
    .status-badge--reconciled { background: #d1fae5; color: #047857; }
    .status-badge--reconciled .status-badge__dot { background: #10b981; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #d1fae5; color: #047857; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class TillAdminComponent implements OnInit {
  private readonly api = inject(TillAdminService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly tills = signal<Till[]>([]);
  protected readonly sessions = signal<TillSession[]>([]);
  protected readonly selectedTill = signal<Till | null>(null);
  protected readonly busy = signal<boolean>(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly showNewTill = signal(false);

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected newCode = '';
  protected newName = '';
  protected newPriceListId: string | null = null;
  protected newFloat: number | null = null;

  ngOnInit(): void { this.refresh(); }

  toggleNewTill(): void { this.showNewTill.update(v => !v); }

  refresh(): void {
    this.api.listTills(this.branchId()).subscribe({
      next: rows => this.tills.set(rows),
      error: err => this.showError(err)
    });
    this.api.listSessions(this.branchId()).subscribe({
      next: rows => this.sessions.set(rows),
      error: err => this.showError(err)
    });
  }

  selectTill(t: Till): void { this.selectedTill.set(t); }
  abs(n: number): number { return Math.abs(n); }

  createTill(): void {
    const branchId = this.branchId();
    if (branchId === null || this.newPriceListId === null) {
      this.error.set('Branch + price list id required.');
      return;
    }
    this.busy.set(true);
    this.api.createTill({
      branchId,
      code: this.newCode.trim().toUpperCase(),
      name: this.newName.trim(),
      defaultPriceListId: this.newPriceListId,
      installId: null
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Till ${this.newCode} created.`);
        this.newCode = ''; this.newName = ''; this.newPriceListId = null;
        this.showNewTill.set(false);
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  openSession(till: Till): void {
    if (this.newFloat === null) { this.error.set('Opening float required.'); return; }
    this.busy.set(true);
    this.api.openSession({ tillId: till.id, openingFloatAmount: this.newFloat }).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Session opened on ${till.code}.`);
        this.newFloat = null;
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  closeSession(s: TillSession): void {
    const declared = globalThis.prompt(`Declared cash for session #${s.id}?`);
    if (!declared) return;
    const amount = Number.parseFloat(declared);
    if (Number.isNaN(amount) || amount < 0) {
      this.error.set('Declared cash must be a non-negative number.');
      return;
    }
    let supervisorIdStr: string | null = null;
    if (Math.abs(amount - s.openingFloatAmount) > 1000) {
      supervisorIdStr = globalThis.prompt('Variance above threshold — supervisor user id?');
      if (!supervisorIdStr) return;
    }
    const supervisorId = supervisorIdStr || null;
    this.busy.set(true);
    this.api.closeSession(s.uid, {
      declaredCashAmount: amount,
      supervisorId,
      notes: null
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Session #${s.id} closed.`);
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  reconcileSession(s: TillSession): void {
    this.busy.set(true);
    this.api.reconcileSession(s.uid).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Session #${s.id} reconciled.`);
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  deactivate(t: Till): void {
    this.busy.set(true);
    this.api.deactivateTill(t.uid).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Till ${t.code} deactivated.`);
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  activate(t: Till): void {
    this.busy.set(true);
    this.api.activateTill(t.uid).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Till ${t.code} activated.`);
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
