import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../../core/api/api-response';
import { AccessibleBranch, BranchService } from '../../../core/branch/branch.service';
import { SearchSelectComponent, SearchSelectOption } from '../../../core/ui/search-select.component';
import { UserAdminService } from './user-admin.service';
import {
  CreateUserRequest,
  CreateUserResponse,
  UserSummary
} from './user-admin.models';

interface CreateForm {
  username: string;
  displayName: string;
  email: string;
  phone: string;
  defaultBranchId: string | null;
  password: string;
  generatePassword: boolean;
}

type UserListFilter = 'all' | 'active' | 'disabled' | 'locked' | 'reset';

@Component({
  selector: 'orbix-user-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, SearchSelectComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Admin</a> &rsaquo; Users
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Users</h1>
        <p class="text-secondary mb-0 small">{{ users().length }} user{{ users().length === 1 ? '' : 's' }} in this company.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleNewForm()">
        <i class="bi" [class.bi-plus-lg]="!showNewForm()" [class.bi-x-lg]="showNewForm()"></i>
        {{ showNewForm() ? 'Close form' : 'New user' }}
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
    @if (tempPasswordBanner(); as banner) {
      <div class="alert alert-warning d-flex align-items-start gap-2 py-3">
        <i class="bi bi-key-fill mt-1"></i>
        <div class="flex-grow-1">
          <p class="fw-semibold mb-1">Temporary password for {{ banner.username }}</p>
          <p class="small mb-2">Hand this to the user privately. It will not be shown again.</p>
          <code class="temp-pwd">{{ banner.password }}</code>
        </div>
        <button type="button" class="btn-close btn-sm" (click)="tempPasswordBanner.set(null)"></button>
      </div>
    }

    @if (showNewForm()) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">New user</h2>
          <button class="btn-close btn-sm" (click)="toggleNewForm()"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="createUser()" #cf="ngForm" class="d-flex flex-column gap-3">
            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend"><i class="bi bi-person-vcard text-secondary"></i> Account</legend>
              <div class="row g-2">
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Username</label>
                  <input class="form-control font-monospace" name="username" [(ngModel)]="newForm.username" required
                         minlength="2" maxlength="80" placeholder="e.g. j.smith">
                </div>
                <div class="col-md-8">
                  <label class="form-label small fw-semibold text-secondary">Display name</label>
                  <input class="form-control" name="displayName" [(ngModel)]="newForm.displayName" required>
                </div>
                <div class="col-md-6">
                  <label class="form-label small fw-semibold text-secondary">Email <span class="text-muted">(opt)</span></label>
                  <input class="form-control" type="email" name="email" [(ngModel)]="newForm.email">
                </div>
                <div class="col-md-6">
                  <label class="form-label small fw-semibold text-secondary">Phone <span class="text-muted">(opt)</span></label>
                  <input class="form-control" name="phone" [(ngModel)]="newForm.phone">
                </div>
                <div class="col-md-12">
                  <label class="form-label small fw-semibold text-secondary">
                    Default branch <span class="text-muted">(opt)</span>
                  </label>
                  <orbix-search-select [options]="branchOptions()" [(ngModel)]="newForm.defaultBranchId"
                                       name="branchId"
                                       placeholder="— No default —"/>
                  <small class="form-text text-secondary">
                    Where the user lands on login. This does NOT grant them access — open the user after
                    creating to assign their roles.
                  </small>
                </div>
              </div>
            </fieldset>

            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend"><i class="bi bi-key text-secondary"></i> Initial password</legend>
              <div class="form-check mb-2">
                <input class="form-check-input" type="checkbox" id="generatePassword" name="generatePassword"
                       [(ngModel)]="newForm.generatePassword">
                <label class="form-check-label small" for="generatePassword">
                  Generate a temporary password (recommended) — shown once on creation, user must change on first login.
                </label>
              </div>
              @if (!newForm.generatePassword) {
                <label class="form-label small fw-semibold text-secondary">Password</label>
                <input class="form-control" type="text" name="password" [(ngModel)]="newForm.password"
                       minlength="8" maxlength="80" required>
                <small class="form-text text-secondary">Minimum 8 characters. User will be forced to change it on first login.</small>
              }
            </fieldset>

            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy() || cf.invalid">
                @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-person-plus"></i> }
                Create user
              </button>
              <button type="button" class="btn btn-outline-secondary" (click)="toggleNewForm()">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    }

    @if (!showNewForm()) {
      <!-- Toolbar -->
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-body p-3 d-flex flex-wrap align-items-center gap-3">
          <div class="search-box flex-grow-1">
            <i class="bi bi-search"></i>
            <input type="search" class="form-control" placeholder="Search by username, name or email"
                   [(ngModel)]="searchTerm" (ngModelChange)="searchSignal.set(searchTerm)">
          </div>
          <div class="status-pills d-flex gap-1 flex-wrap">
            @for (opt of SearchSelectOptions; track opt.value) {
              <button type="button" class="status-pill"
                      [class.is-active]="filter() === opt.value"
                      (click)="filter.set(opt.value)">
                {{ opt.label }}
              </button>
            }
          </div>
        </div>
      </div>

      <div class="card border-0 shadow-sm overflow-hidden">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">Users</h2>
          <span class="badge text-bg-light text-secondary">
            {{ filtered().length }}@if (filtered().length !== users().length) { / {{ users().length }} }
          </span>
        </div>
        @if (filtered().length === 0) {
          <div class="p-5 text-center">
            <div class="empty-icon mx-auto mb-3"><i class="bi bi-people"></i></div>
            <p class="small text-secondary mb-0">
              @if (users().length === 0) { No users yet. }
              @else { No users match these filters. }
            </p>
          </div>
        } @else {
          <!-- Desktop: tabular -->
          <div class="table-responsive d-none d-md-block">
            <table class="table table-hover align-middle mb-0 simple-table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Email</th>
                  <th>Status</th>
                  <th>Default branch</th>
                  <th>Last login</th>
                  <th class="text-end actions-col"></th>
                </tr>
              </thead>
              <tbody>
                @for (u of filtered(); track u.id) {
                  <tr class="u-table-row"
                      [routerLink]="['/admin/users', u.uid]"
                      tabindex="0">
                    <td>
                      <div class="d-flex align-items-center gap-2">
                        <span class="u-avatar">{{ initials(u.displayName) }}</span>
                        <div class="min-w-0">
                          <p class="fw-semibold text-dark mb-0 text-truncate">{{ u.displayName }}</p>
                          <p class="small text-secondary mb-0 font-monospace">&#64;{{ u.username }}</p>
                        </div>
                      </div>
                    </td>
                    <td class="small text-secondary text-truncate">{{ u.email ?? '—' }}</td>
                    <td>
                      <div class="d-flex flex-wrap gap-1">
                        <span class="status-badge status-badge--{{ u.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ u.status }}
                        </span>
                        @if (u.locked) {
                          <span class="badge text-bg-danger-subtle text-danger small">
                            <i class="bi bi-lock-fill"></i> Locked
                          </span>
                        }
                        @if (u.mustChangePassword) {
                          <span class="badge text-bg-warning-subtle text-warning small">
                            <i class="bi bi-key-fill"></i> Reset
                          </span>
                        }
                      </div>
                    </td>
                    <td class="small text-secondary">{{ branchLabel(u.defaultBranchId) }}</td>
                    <td class="small text-secondary">{{ u.lastLoginAt ? (u.lastLoginAt | date:'short') : '—' }}</td>
                    <td class="text-end actions-col">
                      <i class="bi bi-chevron-right text-secondary"></i>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <!-- Mobile: card list (tables are painful on small screens) -->
          <ul class="list-unstyled mb-0 u-list d-md-none">
            @for (u of filtered(); track u.id) {
              <li>
                <a class="u-row text-decoration-none"
                   [routerLink]="['/admin/users', u.uid]">
                  <span class="u-avatar">{{ initials(u.displayName) }}</span>
                  <div class="flex-grow-1 min-w-0">
                    <p class="fw-semibold text-dark mb-0 text-truncate">{{ u.displayName }}</p>
                    <p class="small text-secondary mb-0 font-monospace">&#64;{{ u.username }}</p>
                  </div>
                  <div class="d-flex flex-column align-items-end gap-1">
                    <span class="status-badge status-badge--{{ u.status.toLowerCase() }}">
                      <span class="status-badge__dot"></span>{{ u.status }}
                    </span>
                    @if (u.locked) {
                      <span class="badge text-bg-danger-subtle text-danger small">
                        <i class="bi bi-lock-fill"></i> Locked
                      </span>
                    }
                    @if (u.mustChangePassword) {
                      <span class="badge text-bg-warning-subtle text-warning small">
                        <i class="bi bi-key-fill"></i> Reset
                      </span>
                    }
                  </div>
                  <i class="bi bi-chevron-right text-secondary u-row__chev"></i>
                </a>
              </li>
            }
          </ul>
        }
      </div>
    }
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

    /* ---- Toolbar ---- */
    .search-box { position: relative; min-width: 220px; }
    .search-box i {
      position: absolute; left: 0.875rem; top: 50%; transform: translateY(-50%);
      color: #9ca3af; pointer-events: none;
    }
    .search-box .form-control { padding-left: 2.4rem; border: 1px solid #e5e7eb; }

    .status-pill {
      padding: 0.4rem 0.85rem; font-size: 0.85rem; font-weight: 500;
      border: 1px solid #e5e7eb; border-radius: 999px;
      background: #fff; color: #6b7280;
      transition: all 0.15s ease;
    }
    .status-pill:hover { border-color: #cbd5e1; color: #1f2937; }
    .status-pill.is-active { background: #0d2a5b; border-color: #0d2a5b; color: #fff; }

    @media (max-width: 575.98px) {
      .search-box { min-width: 100%; }
      .status-pills { width: 100%; overflow-x: auto; flex-wrap: nowrap; }
      .status-pill { flex-shrink: 0; }
    }

    /* ---- Desktop table ---- */
    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb;
      padding: 0.75rem 1rem;
    }
    .simple-table tbody td {
      padding: 0.75rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle;
    }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table .actions-col { width: 1%; white-space: nowrap; }

    .u-table-row { cursor: pointer; transition: background 0.1s ease; }
    .u-table-row:hover { background: #f8fafc !important; }
    .u-table-row:focus-visible { outline: 2px solid #1d4ed8; outline-offset: -2px; }
    .u-table-row:hover .bi-chevron-right { color: #1d4ed8 !important; }

    /* ---- Mobile card list ---- */
    .u-list { }
    .u-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff;
      border-bottom: 1px solid #f3f4f6;
      color: inherit;
      transition: background 0.1s ease;
      min-height: 64px;
    }
    .u-row:hover { background: #f8fafc; }
    .u-row:last-child { border-bottom: none; }
    .u-row__chev { font-size: 1rem; color: #cbd5e1; transition: transform 0.15s ease, color 0.15s ease; }
    .u-row:hover .u-row__chev { color: #1d4ed8; transform: translateX(2px); }

    .u-avatar {
      width: 36px; height: 36px; border-radius: 50%;
      background: #e8eef9; color: #1a4fb5;
      display: inline-flex; align-items: center; justify-content: center;
      font-weight: 600; font-size: 0.85rem; flex-shrink: 0;
    }

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
    .status-badge--locked     { background: #fef3c7; color: #92400e; }
    .status-badge--locked .status-badge__dot     { background: #f59e0b; }
    .status-badge--suspended  { background: #fee2e2; color: #b91c1c; }
    .status-badge--suspended .status-badge__dot  { background: #f43f5e; }

    .text-bg-danger-subtle  { background: #fee2e2; color: #b91c1c; }
    .text-bg-warning-subtle { background: #fef3c7; color: #92400e; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .temp-pwd {
      display: inline-block; padding: 0.4rem 0.75rem;
      background: #fff; border: 1px dashed #b45309; border-radius: 6px;
      font-size: 1.05rem; font-weight: 600; letter-spacing: 0.05em;
      color: #78350f; user-select: all;
    }
  `]
})
export class UserAdminComponent implements OnInit {
  private readonly api = inject(UserAdminService);
  private readonly branchService = inject(BranchService);
  private readonly router = inject(Router);

  protected readonly branches = signal<AccessibleBranch[]>([]);
  protected readonly branchOptions = computed<SearchSelectOption[]>(
    () => this.branches().map(b => ({ id: b.id, label: `${b.code} · ${b.name}` })));
  protected readonly users = signal<UserSummary[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly showNewForm = signal(false);
  protected readonly tempPasswordBanner = signal<{ username: string; password: string } | null>(null);

  protected newForm: CreateForm = blankCreateForm();

  // --- toolbar state ------------------------------------------------------
  protected readonly searchSignal = signal('');
  protected searchTerm = '';
  protected readonly filter = signal<UserListFilter>('all');
  protected readonly SearchSelectOptions: { label: string; value: UserListFilter }[] = [
    { label: 'All',      value: 'all' },
    { label: 'Active',   value: 'active' },
    { label: 'Disabled', value: 'disabled' },
    { label: 'Locked',   value: 'locked' },
    { label: 'Reset due', value: 'reset' },
  ];

  protected readonly filtered = computed(() => {
    const q = this.searchSignal().trim().toLowerCase();
    const f = this.filter();
    return this.users().filter(u => {
      switch (f) {
        case 'active':   if (u.status !== 'ACTIVE') return false; break;
        case 'disabled': if (u.status === 'ACTIVE') return false; break;
        case 'locked':   if (!u.locked) return false; break;
        case 'reset':    if (!u.mustChangePassword) return false; break;
        default: break;
      }
      if (!q) return true;
      return u.username.toLowerCase().includes(q)
          || u.displayName.toLowerCase().includes(q)
          || (u.email?.toLowerCase().includes(q) ?? false);
    });
  });

  ngOnInit(): void {
    this.load();
    this.branchService.listBranches().subscribe({
      next: list => this.branches.set(list),
      error: () => this.branches.set([])
    });
  }

  initials(displayName: string | null): string {
    if (!displayName) return '?';
    const parts = displayName.trim().split(/\s+/).filter(p => p.length > 0);
    if (parts.length === 0) return '?';
    const first = parts[0].charAt(0);
    if (parts.length === 1) return first.toUpperCase();
    const last = parts.at(-1) ?? '';
    return (first + last.charAt(0)).toUpperCase();
  }

  /** Resolve a default-branch id to its name; falls back to "—" when null. */
  branchLabel(branchId: string | null): string {
    if (branchId === null) return '—';
    const b = this.branches().find(x => x.id === branchId);
    return b ? b.name : '#' + branchId;
  }

  toggleNewForm(): void {
    this.showNewForm.update(v => !v);
    if (!this.showNewForm()) this.newForm = blankCreateForm();
  }

  createUser(): void {
    const f = this.newForm;
    const request: CreateUserRequest = {
      username: f.username.trim(),
      displayName: f.displayName.trim(),
      email: emptyToNull(f.email),
      phone: emptyToNull(f.phone),
      defaultBranchId: f.defaultBranchId,
      password: f.generatePassword ? null : f.password,
      mustChangePassword: true
    };
    this.busy.set(true);
    this.error.set(null);
    this.api.createUser(request).subscribe({
      next: (resp: CreateUserResponse) => {
        this.busy.set(false);
        this.newForm = blankCreateForm();
        this.showNewForm.set(false);
        if (resp.temporaryPassword) {
          // Stash the temp password so the detail page can show the banner
          // after navigation.
          globalThis.sessionStorage.setItem(
            'orbix.tempPwd.' + resp.user.uid,
            JSON.stringify({ username: resp.user.username, password: resp.temporaryPassword })
          );
        }
        // Hand off to the detail page so the admin can assign roles.
        void this.router.navigate(['/admin/users', resp.user.uid]);
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private load(): void {
    this.api.listUsers().subscribe({
      next: list => this.users.set(list),
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

function blankCreateForm(): CreateForm {
  return {
    username: '', displayName: '', email: '', phone: '',
    defaultBranchId: null, password: '', generatePassword: true
  };
}

function emptyToNull(value: string | null | undefined): string | null {
  if (value == null) return null;
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}
