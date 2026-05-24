import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { ConfirmService } from '../../../core/ui/confirm.service';
import { RouteAdminService } from './route-admin.service';
import { Route } from './route-admin.models';

@Component({
  selector: 'orbix-route-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Admin</a> &rsaquo; Routes
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Delivery routes</h1>
        <p class="text-secondary mb-0 small">{{ routes().length }} route{{ routes().length === 1 ? '' : 's' }} configured.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New route' }}
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
          <h2 class="h6 fw-bold mb-0 text-dark">
            {{ editing() ? 'Edit route' : 'New route' }}
          </h2>
          <button class="btn-close btn-sm" (click)="toggleForm()" aria-label="Close"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="submit()" #f="ngForm" class="d-flex flex-column gap-3">
            <div class="row g-2">
              <div class="col-md-4">
                <label class="form-label small fw-semibold text-secondary">Code</label>
                <input class="form-control font-monospace" name="code" [(ngModel)]="form.code"
                       [disabled]="!!editing()" required placeholder="e.g. CENTRAL">
              </div>
              <div class="col-md-8">
                <label class="form-label small fw-semibold text-secondary">Name</label>
                <input class="form-control" name="name" [(ngModel)]="form.name" required
                       placeholder="Central Dar es Salaam">
              </div>
              <div class="col-12">
                <label class="form-label small fw-semibold text-secondary">
                  Description <span class="text-muted">(optional)</span>
                </label>
                <textarea class="form-control" name="desc" rows="2"
                          [(ngModel)]="form.description"
                          placeholder="Areas covered, schedule notes, anything useful."></textarea>
              </div>
            </div>
            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="saving() || f.invalid">
                @if (saving()) {
                  <span class="spinner-border spinner-border-sm"></span>
                } @else {
                  <i class="bi" [class.bi-save]="editing()" [class.bi-plus-lg]="!editing()"></i>
                }
                {{ editing() ? 'Save route' : 'Create route' }}
              </button>
              <button type="button" class="btn btn-outline-secondary" (click)="toggleForm()">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    }

    <div class="card border-0 shadow-sm mb-3">
      <div class="card-body p-3 d-flex flex-wrap align-items-center gap-3">
        <div class="search-box flex-grow-1">
          <i class="bi bi-search"></i>
          <input type="search" class="form-control" placeholder="Search by code or name"
                 [(ngModel)]="searchTerm" (ngModelChange)="searchSignal.set(searchTerm)">
        </div>
        <div class="status-pills d-flex gap-1 flex-wrap">
          @for (opt of statusOptions; track opt.value) {
            <button type="button" class="status-pill"
                    [class.is-active]="statusFilter() === opt.value"
                    (click)="statusFilter.set(opt.value)">
              {{ opt.label }}
            </button>
          }
        </div>
      </div>
    </div>

    <div class="card border-0 shadow-sm overflow-hidden">
      @if (filtered().length === 0) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-signpost-split"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No routes match</h2>
          <p class="small text-secondary mb-0">
            @if (searchTerm) {
              Try a different code or name.
            } @else {
              Add your first delivery route to start tagging agents and van loads.
            }
          </p>
        </div>
      } @else {
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 simple-table">
            <thead>
              <tr>
                <th>Code</th><th>Name</th><th>Description</th><th>Status</th>
                <th class="text-end actions-col"></th>
              </tr>
            </thead>
            <tbody>
              @for (r of filtered(); track r.id) {
                <tr [class.table-active]="editing()?.id === r.id">
                  <td><span class="badge text-bg-light border text-secondary font-monospace">{{ r.code }}</span></td>
                  <td class="fw-semibold text-dark">{{ r.name }}</td>
                  <td class="small text-secondary text-truncate" style="max-width: 380px;">
                    {{ r.description ?? '—' }}
                  </td>
                  <td>
                    <span class="status-badge status-badge--{{ r.status.toLowerCase() }}">
                      <span class="status-badge__dot"></span>{{ r.status }}
                    </span>
                  </td>
                  <td class="text-end actions-col">
                    <div class="btn-group btn-group-sm">
                      <button class="btn btn-outline-secondary" (click)="startEdit(r)"
                              [disabled]="saving()" title="Edit">
                        <i class="bi bi-pencil"></i>
                      </button>
                      @if (r.status === 'ACTIVE') {
                        <button class="btn btn-outline-danger" (click)="deactivate(r)"
                                [disabled]="saving()" title="Deactivate">
                          <i class="bi bi-pause-circle"></i>
                        </button>
                      } @else {
                        <button class="btn btn-outline-primary" (click)="activate(r)"
                                [disabled]="saving()" title="Activate">
                          <i class="bi bi-play-circle"></i>
                        </button>
                      }
                    </div>
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

    .search-box { position: relative; min-width: 220px; }
    .search-box i { position: absolute; left: 0.875rem; top: 50%; transform: translateY(-50%); color: #9ca3af; pointer-events: none; }
    .search-box .form-control { padding-left: 2.4rem; border: 1px solid #e5e7eb; }
    .search-box .form-control:focus { border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12); }

    .status-pill {
      padding: 0.4rem 0.85rem; font-size: 0.85rem; font-weight: 500;
      border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; color: #6b7280;
      transition: all 0.15s ease;
    }
    .status-pill:hover { border-color: #cbd5e1; color: #1f2937; }
    .status-pill.is-active { background: #0d2a5b; border-color: #0d2a5b; color: #fff; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.875rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }
    .simple-table tbody tr.table-active { background: #eef4ff !important; }
    .simple-table .actions-col { width: 1%; white-space: nowrap; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.72rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--active   { background: #d1fae5; color: #047857; }
    .status-badge--active .status-badge__dot   { background: #10b981; }
    .status-badge--inactive { background: #fef3c7; color: #92400e; }
    .status-badge--inactive .status-badge__dot { background: #f59e0b; }
    .status-badge--archived { background: #f3f4f6; color: #4b5563; }
    .status-badge--archived .status-badge__dot { background: #9ca3af; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #ffedd5; color: #c2410c; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }
  `]
})
export class RouteAdminComponent implements OnInit, OnDestroy {
  private readonly api = inject(RouteAdminService);
  private readonly confirm = inject(ConfirmService);

  protected readonly routes = signal<Route[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly showForm = signal(false);
  protected readonly editing = signal<Route | null>(null);

  private infoTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly statusFilter = signal<'ACTIVE' | 'INACTIVE' | 'ARCHIVED' | null>(null);
  protected readonly searchSignal = signal('');
  protected searchTerm = '';

  protected readonly statusOptions = [
    { label: 'All',      value: null },
    { label: 'Active',   value: 'ACTIVE' as const },
    { label: 'Inactive', value: 'INACTIVE' as const },
    { label: 'Archived', value: 'ARCHIVED' as const },
  ];

  protected readonly filtered = computed(() => {
    const status = this.statusFilter();
    const q = this.searchSignal().trim().toLowerCase();
    return this.routes().filter(r => {
      if (status && r.status !== status) return false;
      if (!q) return true;
      return r.code.toLowerCase().includes(q)
          || r.name.toLowerCase().includes(q);
    });
  });

  protected form = blankRouteForm();

  ngOnInit(): void { this.load(); }

  ngOnDestroy(): void { clearTimeout(this.infoTimer); }

  toggleForm(): void {
    const next = !this.showForm();
    this.showForm.set(next);
    if (!next) this.resetForm();
  }

  startEdit(route: Route): void {
    this.editing.set(route);
    this.form = { code: route.code, name: route.name, description: route.description ?? '' };
    this.showForm.set(true);
  }

  submit(): void {
    const editing = this.editing();
    const payload = {
      name: this.form.name.trim(),
      description: emptyToNull(this.form.description)
    };
    const call = editing === null
      ? this.api.createRoute({ code: this.form.code.trim(), ...payload })
      : this.api.updateRoute(editing.uid, payload);
    this.run(call, () => {
      this.resetForm();
      this.showForm.set(false);
      this.load();
    }, editing === null ? 'Route created.' : 'Route updated.');
  }

  async deactivate(route: Route): Promise<void> {
    const { ok, reason } = await this.confirm.ask({
      title: 'Deactivate route',
      message: `Deactivate ${route.code} — ${route.name}? Agents can no longer be assigned to it until reactivated.`,
      confirmText: 'Deactivate',
      variant: 'warning',
      reason: { label: 'Reason', required: true }
    });
    if (!ok) return;
    this.run(this.api.deactivateRoute(route.uid, reason), () => this.load(), 'Route deactivated.');
  }

  async activate(route: Route): Promise<void> {
    const { ok, reason } = await this.confirm.ask({
      title: 'Activate route',
      message: `Reactivate ${route.code} — ${route.name}?`,
      confirmText: 'Activate',
      variant: 'primary',
      reason: { label: 'Reason', required: true }
    });
    if (!ok) return;
    this.run(this.api.activateRoute(route.uid, reason), () => this.load(), 'Route activated.');
  }

  private load(): void {
    this.api.listRoutes().subscribe({
      next: list => this.routes.set(list),
      error: err => this.showError(err)
    });
  }

  private resetForm(): void {
    this.editing.set(null);
    this.form = blankRouteForm();
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void, successMsg?: string): void {
    this.saving.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => {
        this.saving.set(false);
        onSuccess(value);
        if (successMsg) this.flashInfo(successMsg);
      },
      error: err => { this.saving.set(false); this.showError(err); }
    });
  }

  private flashInfo(message: string): void {
    this.info.set(message);
    clearTimeout(this.infoTimer);
    this.infoTimer = setTimeout(() => this.info.set(null), 4000);
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

function blankRouteForm() {
  return { code: '', name: '', description: '' };
}

function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}
