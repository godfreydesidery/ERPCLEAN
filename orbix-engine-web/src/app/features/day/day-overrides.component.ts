import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { HasPermissionDirective } from '../../core/auth/has-permission.directive';
import { DayService } from './day.service';
import { BusinessDay, BusinessDayOverride } from './day.models';

/**
 * Read + void list of back-dated business-day overrides. List gated by
 * {@code DAY.OVERRIDE_LIST}; archive button gated by {@code DAY.OVERRIDE}.
 * Posting an override (POST .../uid/{dayUid}/overrides) is not surfaced here
 * — back-dated postings are produced by their owning feature (POS, sales,
 * procurement) and create the override row implicitly. This page is the
 * audit + void surface.
 */
@Component({
  selector: 'orbix-day-overrides',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, HasPermissionDirective],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Business day</a> &rsaquo; Back-dated overrides
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Back-dated overrides</h1>
        <p class="text-secondary mb-0 small">
          Supervisor grants that let a posting land in a closed day. Void before the back-dated post is committed.
        </p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm"
              *orbixHasPermission="'DAY.OVERRIDE'"
              (click)="toggleForm()"
              [title]="showForm() ? 'Close the form without saving' : 'Pre-grant a back-dated override for a target day'">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'Pre-grant override' }}
      </button>
    </header>

    @if (success()) {
      <div class="alert alert-success d-flex align-items-center gap-2 py-2">
        <i class="bi bi-check-circle-fill"></i>
        <span class="flex-grow-1">{{ success() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="success.set(null)"></button>
      </div>
    }

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
      </div>
    }

    @if (branchId() === null) {
      <div class="card border-0 shadow-sm">
        <div class="card-body p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-building"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No active branch</h2>
          <p class="small text-secondary mb-0">Pick a branch in the top bar to view its overrides.</p>
        </div>
      </div>
    } @else {
      @if (showForm()) {
        <div class="card border-0 shadow-sm mb-3">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Pre-grant override</h2>
            <button class="btn-close btn-sm" (click)="toggleForm()" aria-label="Close"
                    title="Close the form without saving"></button>
          </div>
          <div class="card-body p-3">
            <p class="small text-secondary mb-3">
              Authorise a back-dated posting in advance for a specific entity. The producing feature
              (POS, sales, procurement) consumes the grant when the post lands.
            </p>
            <form (ngSubmit)="submit()" #f="ngForm" class="d-flex flex-column gap-3">
              <div class="row g-2">
                <div class="col-md-6">
                  <label class="form-label small fw-semibold text-secondary" for="ov-day">Target day</label>
                  <select id="ov-day" class="form-select" name="dayUid" [(ngModel)]="form.dayUid" required>
                    <option [ngValue]="''" disabled>Pick a business day…</option>
                    @for (d of days(); track d.uid) {
                      <option [ngValue]="d.uid">
                        {{ d.businessDate }} — {{ d.status }}
                      </option>
                    }
                  </select>
                </div>
                <div class="col-md-3">
                  <label class="form-label small fw-semibold text-secondary" for="ov-etype">Entity type</label>
                  <input id="ov-etype" class="form-control font-monospace" name="entityType"
                         [(ngModel)]="form.entityType" required placeholder="e.g. SALES_INVOICE">
                </div>
                <div class="col-md-3">
                  <label class="form-label small fw-semibold text-secondary" for="ov-eid">Entity id</label>
                  <input id="ov-eid" class="form-control font-monospace" name="entityId"
                         [(ngModel)]="form.entityId" required placeholder="42">
                </div>
              </div>
              <div>
                <label class="form-label small fw-semibold text-secondary" for="ov-reason">Reason</label>
                <textarea id="ov-reason" class="form-control" name="reason" rows="2"
                          [(ngModel)]="form.reason" required minlength="4"
                          placeholder="Why this back-dated posting is permitted (min 4 chars)"></textarea>
              </div>
              <div class="d-flex gap-2 pt-2 border-top">
                <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                        [disabled]="busy() || f.invalid">
                  @if (busy()) {
                    <span class="spinner-border spinner-border-sm"></span>
                  } @else {
                    <i class="bi bi-shield-check"></i>
                  }
                  Grant override
                </button>
                <button type="button" class="btn btn-outline-secondary" (click)="toggleForm()"
                        title="Discard and close">Cancel</button>
              </div>
            </form>
          </div>
        </div>
      }

      <div class="card border-0 shadow-sm overflow-hidden">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">Overrides</h2>
          <span class="badge text-bg-light text-secondary">{{ overrides().length }}</span>
        </div>

        @if (loading()) {
          <div class="p-5 text-center">
            <div class="spinner-border text-primary" role="status">
              <span class="visually-hidden">Loading…</span>
            </div>
          </div>
        } @else if (overrides().length === 0) {
          <div class="p-5 text-center">
            <div class="empty-icon mx-auto mb-3"><i class="bi bi-clipboard-check"></i></div>
            <h2 class="h6 fw-bold mb-1 text-dark">No overrides recorded</h2>
            <p class="small text-secondary mb-0">Back-dating grants will appear here as they're issued.</p>
          </div>
        } @else {
          <div class="table-responsive">
            <table class="table table-hover align-middle mb-0 simple-table">
              <thead>
                <tr>
                  <th>Target date</th>
                  <th>Entity</th>
                  <th>Reason</th>
                  <th>Authorised</th>
                  <th>Status</th>
                  <th class="text-end actions-col"></th>
                </tr>
              </thead>
              <tbody>
                @for (o of overrides(); track o.uid) {
                  <tr [class.row-archived]="o.archivedAt">
                    <td class="fw-semibold text-dark">{{ o.targetBusinessDate | date:'dd/MM/yyyy' }}</td>
                    <td>
                      <span class="badge text-bg-light border text-secondary font-monospace">{{ o.entityType }}</span>
                      <span class="ms-1 small text-secondary font-monospace">#{{ o.entityId }}</span>
                    </td>
                    <td class="small text-secondary reason-cell" [title]="o.reason">{{ o.reason }}</td>
                    <td class="small text-secondary">
                      <div>By #{{ o.authorisedBy }}</div>
                      <div>{{ o.at | date:'dd/MM/yyyy HH:mm' }}</div>
                    </td>
                    <td>
                      @if (o.archivedAt) {
                        <span class="status-badge status-badge--archived">
                          <span class="status-badge__dot"></span>Voided
                        </span>
                      } @else {
                        <span class="status-badge status-badge--active">
                          <span class="status-badge__dot"></span>Active
                        </span>
                      }
                    </td>
                    <td class="text-end actions-col">
                      @if (!o.archivedAt) {
                        <button class="btn btn-sm btn-outline-danger"
                                *orbixHasPermission="'DAY.OVERRIDE'"
                                [disabled]="busy()"
                                (click)="archive(o)"
                                title="Void this override. Only possible before the back-dated post lands.">
                          <i class="bi bi-x-circle"></i> Void
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
    }
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

    .row-archived { opacity: 0.65; }
    .reason-cell {
      max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.72rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--active   { background: #d1fae5; color: #047857; }
    .status-badge--active .status-badge__dot   { background: #10b981; }
    .status-badge--archived { background: #f3f4f6; color: #4b5563; }
    .status-badge--archived .status-badge__dot { background: #9ca3af; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class DayOverridesComponent implements OnInit {
  private readonly dayService = inject(DayService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly overrides = signal<BusinessDayOverride[]>([]);
  protected readonly days = signal<BusinessDay[]>([]);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly showForm = signal(false);

  protected form = { dayUid: '', entityType: '', entityId: '', reason: '' };

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  ngOnInit(): void {
    this.load();
  }

  toggleForm(): void {
    this.showForm.update(v => !v);
    if (!this.showForm()) this.resetForm();
  }

  submit(): void {
    if (this.busy()) return;
    const { dayUid, entityType, entityId, reason } = this.form;
    if (!dayUid || !entityType.trim() || !entityId.trim() || reason.trim().length < 4) return;
    this.busy.set(true);
    this.error.set(null);
    this.success.set(null);
    this.dayService.postOverrideForDay(dayUid, {
      entityType: entityType.trim(),
      entityId: entityId.trim(),
      reason: reason.trim()
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.success.set('Override granted.');
        this.resetForm();
        this.showForm.set(false);
        this.load();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  archive(o: BusinessDayOverride): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.dayService.archiveOverride(o.uid).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private resetForm(): void {
    this.form = { dayUid: '', entityType: '', entityId: '', reason: '' };
  }

  private load(): void {
    const branchId = this.branchId();
    if (branchId === null) return;
    this.loading.set(true);
    this.error.set(null);
    this.dayService.listOverrides(branchId).subscribe({
      next: list => { this.overrides.set(list); this.loading.set(false); },
      error: err => { this.loading.set(false); this.showError(err); }
    });
    this.dayService.listDays(branchId).subscribe({
      next: list => this.days.set(list.filter(d => d.status === 'OPEN' || d.status === 'CLOSED')),
      error: () => this.days.set([])
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
