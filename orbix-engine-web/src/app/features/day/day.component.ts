import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
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
  imports: [FormsModule, DatePipe, HasPermissionDirective],
  template: `
    <h2 class="h3 mb-4">Business day</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    @if (branchId() === null) {
      <div class="alert alert-warning py-2">No active branch selected.</div>
    } @else {
      <div class="card shadow-sm mb-4" style="max-width: 560px">
        <div class="card-header fw-semibold">Branch {{ branchId() }} — current day</div>
        <div class="card-body">
          @if (current(); as day) {
            <p class="mb-2">
              <span class="fw-semibold">{{ day.businessDate }}</span>
              @switch (day.status) {
                @case ('OPEN') { <span class="badge text-bg-success ms-2">OPEN</span> }
                @case ('CLOSING') { <span class="badge text-bg-warning ms-2">CLOSING</span> }
                @default { <span class="badge text-bg-secondary ms-2">{{ day.status }}</span> }
              }
            </p>
            @if (day.status === 'OPEN') {
              <button class="btn btn-outline-warning" (click)="startClosing(day)"
                      [disabled]="busy()" *orbixHasPermission="'DAY.CLOSE'">Start closing</button>
            } @else if (day.status === 'CLOSING') {
              <div class="input-group" style="max-width: 420px" *orbixHasPermission="'DAY.CLOSE'">
                <input class="form-control" placeholder="EOD report key (optional)"
                       [(ngModel)]="eodReportObjectKey" name="eod">
                <button class="btn btn-warning" (click)="closeDay(day)" [disabled]="busy()">
                  Close day
                </button>
              </div>
            }
          } @else {
            <p class="text-muted">No open day for this branch.</p>
            <form class="input-group" style="max-width: 420px" (ngSubmit)="openDay()"
                  *orbixHasPermission="'DAY.OPEN'">
              <input class="form-control" type="date" [(ngModel)]="openDate" name="openDate" required>
              <button class="btn btn-primary" [disabled]="busy() || !openDate">Open day</button>
            </form>
          }
        </div>
      </div>

      <h3 class="h6">Recent days</h3>
      <table class="table table-sm align-middle" style="max-width: 720px">
        <thead>
          <tr><th>Date</th><th>Status</th><th>Opened</th><th>Closed</th></tr>
        </thead>
        <tbody>
          @for (day of days(); track day.businessDate) {
            <tr>
              <td>{{ day.businessDate }}</td>
              <td>{{ day.status }}</td>
              <td>{{ day.openedAt | date:'short' }}</td>
              <td>{{ day.closedAt ? (day.closedAt | date:'short') : '—' }}</td>
            </tr>
          } @empty {
            <tr><td colspan="4" class="text-muted">No days recorded.</td></tr>
          }
        </tbody>
      </table>
    }
  `
})
export class DayComponent implements OnInit {
  private readonly dayService = inject(DayService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly current = signal<BusinessDay | null>(null);
  readonly days = signal<BusinessDay[]>([]);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  openDate = new Date().toISOString().slice(0, 10);
  eodReportObjectKey = '';

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
