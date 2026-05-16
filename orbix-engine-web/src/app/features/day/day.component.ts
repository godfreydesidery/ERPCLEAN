import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { HasPermissionDirective } from '../../core/auth/has-permission.directive';
import { DayService } from './day.service';
import { BusinessDay } from './day.models';

@Component({
  selector: 'orbix-day',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, HasPermissionDirective],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Operations</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Business day</h1>
      <p class="text-secondary mb-0 small">
        @if (branchId() !== null) { Branch #{{ branchId() }} — open, close and review trading days. }
        @else { Pick a branch to open or close its trading day. }
      </p>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i><span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="error.set(null)"></button>
      </div>
    }

    @if (branchId() === null) {
      <div class="card border-0 shadow-sm">
        <div class="card-body p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-building"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No active branch</h2>
          <p class="small text-secondary mb-0">Pick a branch in the top bar to manage its business day.</p>
        </div>
      </div>
    } @else {
      <!-- Current day banner -->
      <section class="card border-0 shadow-sm mb-4 overflow-hidden">
        <div class="card-body p-4 d-flex flex-wrap align-items-center justify-content-between gap-3"
             [class.bg-day-open]="status() === 'OPEN'"
             [class.bg-day-closing]="status() === 'CLOSING'"
             [class.bg-day-closed]="status() === 'NONE'">
          <div class="d-flex align-items-center gap-3">
            <div class="day-icon">
              <i class="bi" [class.bi-sun]="status() === 'OPEN'"
                           [class.bi-cloud-sun]="status() === 'CLOSING'"
                           [class.bi-moon-stars]="status() === 'NONE'"></i>
            </div>
            <div>
              <p class="text-uppercase small fw-semibold mb-1 day-eyebrow" style="letter-spacing:0.08em;">Current day</p>
              <h2 class="h5 fw-bold mb-0">
                @if (current(); as d) { {{ d.businessDate | date:'fullDate' }} }
                @else { No day open }
              </h2>
              <p class="small mb-0 day-meta">
                @if (status() === 'OPEN') { Sales, receipts and stock movements are live. }
                @else if (status() === 'CLOSING') { Finish the Z-report to close out the day. }
                @else { Open a day before recording any sales. }
              </p>
            </div>
          </div>

          <div class="d-flex gap-2 flex-wrap">
            @if (current(); as d) {
              @if (d.status === 'OPEN') {
                <button class="btn btn-warning text-dark d-inline-flex align-items-center gap-2"
                        (click)="startClosing(d)" [disabled]="busy()"
                        *orbixHasPermission="'DAY.CLOSE'">
                  <i class="bi bi-clipboard-check"></i> Start closing
                </button>
              } @else if (d.status === 'CLOSING') {
                <div class="input-group close-day-group" *orbixHasPermission="'DAY.CLOSE'">
                  <input class="form-control" placeholder="EOD report key (optional)"
                         [(ngModel)]="eodReportObjectKey" name="eod">
                  <button class="btn btn-success d-inline-flex align-items-center gap-1" (click)="closeDay(d)" [disabled]="busy()">
                    <i class="bi bi-lock"></i> Close day
                  </button>
                </div>
              }
            } @else {
              <form class="input-group open-day-group" (ngSubmit)="openDay()"
                    *orbixHasPermission="'DAY.OPEN'">
                <input class="form-control" type="date" [(ngModel)]="openDate" name="openDate" required>
                <button class="btn btn-primary d-inline-flex align-items-center gap-1"
                        [disabled]="busy() || !openDate">
                  <i class="bi bi-play-fill"></i> Open day
                </button>
              </form>
            }
          </div>
        </div>
      </section>

      <!-- Recent days table -->
      <div class="card border-0 shadow-sm overflow-hidden">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">Recent days</h2>
          <span class="badge text-bg-light text-secondary">{{ days().length }}</span>
        </div>
        @if (days().length === 0) {
          <div class="p-5 text-center">
            <div class="empty-icon mx-auto mb-3"><i class="bi bi-calendar2-week"></i></div>
            <p class="small text-secondary mb-0">No trading days recorded yet.</p>
          </div>
        } @else {
          <div class="table-responsive">
            <table class="table table-hover align-middle mb-0 simple-table">
              <thead>
                <tr>
                  <th>Date</th><th>Status</th><th>Opened</th><th>Closed</th>
                  <th class="small text-secondary">EOD key</th>
                </tr>
              </thead>
              <tbody>
                @for (day of days(); track day.businessDate) {
                  <tr>
                    <td class="fw-semibold text-dark">{{ day.businessDate | date:'mediumDate' }}</td>
                    <td>
                      <span class="status-badge status-badge--{{ day.status.toLowerCase() }}">
                        <span class="status-badge__dot"></span>{{ day.status }}
                      </span>
                    </td>
                    <td class="small text-secondary">{{ day.openedAt | date:'short' }}</td>
                    <td class="small text-secondary">{{ day.closedAt ? (day.closedAt | date:'short') : '—' }}</td>
                    <td class="small text-secondary font-monospace">{{ day.eodReportObjectKey ?? '—' }}</td>
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

    /* Day banner palettes */
    .bg-day-open    { background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%); color: #065f46; }
    .bg-day-closing { background: linear-gradient(135deg, #fffbeb 0%, #fde68a 100%); color: #78350f; }
    .bg-day-closed  { background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%); color: #1e3a8a; }

    .day-icon {
      width: 56px; height: 56px; border-radius: 16px;
      background: rgba(255, 255, 255, 0.6);
      display: flex; align-items: center; justify-content: center;
      font-size: 1.6rem; flex-shrink: 0;
    }
    .day-eyebrow { opacity: 0.75; }
    .day-meta { opacity: 0.8; }

    .open-day-group, .close-day-group { min-width: 280px; }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

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
    .status-badge--open    { background: #d1fae5; color: #047857; }
    .status-badge--open .status-badge__dot    { background: #10b981; }
    .status-badge--closing { background: #fef3c7; color: #92400e; }
    .status-badge--closing .status-badge__dot { background: #f59e0b; }
    .status-badge--closed  { background: #e0ecff; color: #1d4ed8; }
    .status-badge--closed .status-badge__dot  { background: #3b82f6; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class DayComponent implements OnInit {
  private readonly dayService = inject(DayService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly current = signal<BusinessDay | null>(null);
  protected readonly days = signal<BusinessDay[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected readonly status = computed<'OPEN' | 'CLOSING' | 'NONE'>(() => {
    const d = this.current();
    if (!d) return 'NONE';
    return d.status === 'OPEN' || d.status === 'CLOSING' ? d.status : 'NONE';
  });

  protected openDate = new Date().toISOString().slice(0, 10);
  protected eodReportObjectKey = '';

  ngOnInit(): void {
    this.reload();
  }

  openDay(): void {
    const branchId = this.branchId();
    if (branchId === null) return;
    this.run(this.dayService.openDay(branchId, this.openDate));
  }

  startClosing(day: BusinessDay): void {
    this.run(this.dayService.startClosing(day.branchId, day.businessDate));
  }

  closeDay(day: BusinessDay): void {
    this.run(this.dayService.closeDay(day.branchId, day.businessDate,
      this.eodReportObjectKey.trim() || null));
  }

  private run(source: Observable<BusinessDay>): void {
    this.busy.set(true);
    this.error.set(null);
    source.subscribe({
      next: () => { this.busy.set(false); this.eodReportObjectKey = ''; this.reload(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private reload(): void {
    const branchId = this.branchId();
    if (branchId === null) return;
    this.dayService.currentDay(branchId).subscribe({
      next: day => this.current.set(day),
      error: err => this.showError(err)
    });
    this.dayService.listDays(branchId).subscribe({
      next: list => this.days.set(list),
      error: err => this.showError(err)
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
