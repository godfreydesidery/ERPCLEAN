import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { UserAdminService } from './user-admin.service';
import {
  CreateUserRequest,
  CreateUserResponse,
  ResetPasswordResponse,
  UserDetail,
  UserSummary
} from './user-admin.models';

interface CreateForm {
  username: string;
  displayName: string;
  email: string;
  phone: string;
  defaultBranchId: number | null;
  password: string;
  generatePassword: boolean;
}

@Component({
  selector: 'orbix-user-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe],
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
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Default branch ID <span class="text-muted">(opt)</span></label>
                  <input class="form-control" type="number" name="branchId" [(ngModel)]="newForm.defaultBranchId">
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

    <div class="row g-3 g-md-4">
      <!-- Users list -->
      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Users</h2>
            <span class="badge text-bg-light text-secondary">{{ users().length }}</span>
          </div>
          @if (users().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-people"></i></div>
              <p class="small text-secondary mb-0">No users yet.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 u-list">
              @for (u of users(); track u.id) {
                <li>
                  <button type="button" class="u-row"
                          [class.is-active]="selected()?.id === u.id"
                          (click)="selectUser(u.id)">
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
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      </div>

      <!-- Detail -->
      <div class="col-12 col-lg-7">
        @if (selected(); as user) {
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4">
              <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                <div class="d-flex align-items-center gap-3 min-w-0">
                  <span class="u-avatar u-avatar--lg">{{ initials(user.displayName) }}</span>
                  <div class="min-w-0">
                    <p class="small text-secondary mb-1 font-monospace">&#64;{{ user.username }}</p>
                    <h2 class="h4 fw-bold mb-1 text-dark text-truncate">{{ user.displayName }}</h2>
                    <span class="status-badge status-badge--{{ user.status.toLowerCase() }}">
                      <span class="status-badge__dot"></span>{{ user.status }}
                    </span>
                    @if (user.locked) {
                      <span class="badge text-bg-danger-subtle text-danger ms-1">
                        <i class="bi bi-lock-fill me-1"></i>Locked
                      </span>
                    }
                    @if (user.mustChangePassword) {
                      <span class="badge text-bg-warning-subtle text-warning ms-1">
                        <i class="bi bi-key-fill me-1"></i>Must change password
                      </span>
                    }
                  </div>
                </div>
                <div class="d-flex flex-wrap gap-2">
                  @if (user.status === 'ACTIVE') {
                    <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1"
                            (click)="onDisable(user)" [disabled]="busy()">
                      <i class="bi bi-pause-circle"></i> Disable
                    </button>
                  } @else {
                    <button class="btn btn-sm btn-outline-success d-inline-flex align-items-center gap-1"
                            (click)="onEnable(user)" [disabled]="busy()">
                      <i class="bi bi-play-circle"></i> Enable
                    </button>
                  }
                  @if (user.locked) {
                    <button class="btn btn-sm btn-outline-warning d-inline-flex align-items-center gap-1"
                            (click)="onUnlock(user)" [disabled]="busy()">
                      <i class="bi bi-unlock"></i> Unlock
                    </button>
                  }
                  <button class="btn btn-sm btn-outline-primary d-inline-flex align-items-center gap-1"
                          (click)="onResetPassword(user)" [disabled]="busy()">
                    <i class="bi bi-key"></i> Reset password
                  </button>
                  <button class="btn btn-sm btn-outline-secondary d-inline-flex align-items-center gap-1"
                          (click)="onForceLogout(user)" [disabled]="busy()">
                    <i class="bi bi-box-arrow-right"></i> Force logout
                  </button>
                </div>
              </div>

              <form (ngSubmit)="onSave(user)" #ef="ngForm" class="d-flex flex-column gap-3">
                <fieldset class="form-fieldset">
                  <legend class="form-fieldset__legend"><i class="bi bi-person text-secondary"></i> Profile</legend>
                  <div class="row g-2">
                    <div class="col-md-6">
                      <label class="form-label small fw-semibold text-secondary">Display name</label>
                      <input class="form-control" name="dn" [(ngModel)]="editForm.displayName" required>
                    </div>
                    <div class="col-md-6">
                      <label class="form-label small fw-semibold text-secondary">Email</label>
                      <input class="form-control" type="email" name="em" [(ngModel)]="editForm.email">
                    </div>
                    <div class="col-md-6">
                      <label class="form-label small fw-semibold text-secondary">Phone</label>
                      <input class="form-control" name="ph" [(ngModel)]="editForm.phone">
                    </div>
                    <div class="col-md-6">
                      <label class="form-label small fw-semibold text-secondary">Default branch ID</label>
                      <input class="form-control" type="number" name="br" [(ngModel)]="editForm.defaultBranchId">
                    </div>
                  </div>
                </fieldset>
                <div>
                  <button class="btn btn-outline-primary btn-sm d-inline-flex align-items-center gap-1"
                          [disabled]="busy() || ef.invalid">
                    <i class="bi bi-save"></i> Save profile
                  </button>
                </div>
              </form>

              <dl class="row small mt-3 mb-0">
                <dt class="col-4 text-secondary">Last login</dt>
                <dd class="col-8 mb-1">{{ user.lastLoginAt ? (user.lastLoginAt | date:'medium') : '—' }}</dd>
                <dt class="col-4 text-secondary">Created</dt>
                <dd class="col-8 mb-1">{{ user.createdAt | date:'medium' }}</dd>
                <dt class="col-4 text-secondary">Updated</dt>
                <dd class="col-8 mb-1">{{ user.updatedAt | date:'medium' }}</dd>
              </dl>
            </div>
          </div>

          <!-- Grants -->
          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Active role grants</h3>
              <span class="badge text-bg-light text-secondary">{{ user.grants.length }}</span>
            </div>
            @if (user.grants.length === 0) {
              <div class="p-4 text-center small text-secondary">
                No active role grants. Assign roles from
                <a routerLink="/admin/roles" class="text-decoration-none">Roles &amp; permissions</a>.
              </div>
            } @else {
              <div class="table-responsive">
                <table class="table table-hover align-middle mb-0 simple-table">
                  <thead>
                    <tr><th>Grant #</th><th>Branch</th><th>Granted at</th></tr>
                  </thead>
                  <tbody>
                    @for (g of user.grants; track g.id) {
                      <tr>
                        <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ g.id }}</span></td>
                        <td class="small">
                          @if (g.branchId !== null) { #{{ g.branchId }} }
                          @else { <em>company-wide</em> }
                        </td>
                        <td class="small text-secondary">{{ g.grantedAt | date:'short' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        } @else {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick a user</h2>
              <p class="small text-secondary mb-0">Or create a new one to onboard staff.</p>
            </div>
          </div>
        }
      </div>
    </div>
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

    .u-list { max-height: 70vh; overflow-y: auto; }
    .u-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .u-row:hover { background: #f8fafc; }
    .u-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .u-row:last-child { border-bottom: none; }

    .u-avatar {
      width: 36px; height: 36px; border-radius: 50%;
      background: #e8eef9; color: #1a4fb5;
      display: inline-flex; align-items: center; justify-content: center;
      font-weight: 600; font-size: 0.85rem; flex-shrink: 0;
    }
    .u-avatar--lg { width: 56px; height: 56px; font-size: 1.1rem; }

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

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.65rem 1rem;
    }
    .simple-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }

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

  protected readonly users = signal<UserSummary[]>([]);
  protected readonly selected = signal<UserDetail | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly showNewForm = signal(false);

  protected readonly tempPasswordBanner = signal<{ username: string; password: string } | null>(null);

  protected newForm: CreateForm = blankCreateForm();
  protected editForm = { displayName: '', email: '', phone: '', defaultBranchId: null as number | null };

  protected readonly statusBadgeFor = computed(() => this.selected()?.status?.toLowerCase());

  ngOnInit(): void {
    this.load();
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

  toggleNewForm(): void {
    this.showNewForm.update(v => !v);
    if (!this.showNewForm()) this.newForm = blankCreateForm();
  }

  selectUser(id: number): void {
    this.error.set(null);
    this.api.getUser(id).subscribe({
      next: detail => {
        this.selected.set(detail);
        this.editForm = {
          displayName: detail.displayName,
          email: detail.email ?? '',
          phone: detail.phone ?? '',
          defaultBranchId: detail.defaultBranchId
        };
      },
      error: err => this.showError(err)
    });
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
        this.info.set(`User ${resp.user.username} created.`);
        if (resp.temporaryPassword) {
          this.tempPasswordBanner.set({
            username: resp.user.username,
            password: resp.temporaryPassword
          });
        }
        this.newForm = blankCreateForm();
        this.showNewForm.set(false);
        this.load();
        this.selectUser(resp.user.id);
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  onSave(user: UserDetail): void {
    this.run(this.api.updateUser(user.id, {
      displayName: this.editForm.displayName.trim(),
      email: emptyToNull(this.editForm.email),
      phone: emptyToNull(this.editForm.phone),
      defaultBranchId: this.editForm.defaultBranchId
    }), updated => {
      this.info.set('Profile saved.');
      this.selected.set(updated);
      this.load();
    });
  }

  onDisable(user: UserDetail): void {
    if (!globalThis.confirm(`Disable ${user.username}? They will lose access immediately.`)) return;
    this.run(this.api.disableUser(user.id), updated => {
      this.info.set(`${user.username} disabled.`);
      this.selected.set(updated);
      this.load();
    });
  }

  onEnable(user: UserDetail): void {
    this.run(this.api.enableUser(user.id), updated => {
      this.info.set(`${user.username} re-enabled.`);
      this.selected.set(updated);
      this.load();
    });
  }

  onUnlock(user: UserDetail): void {
    this.run(this.api.unlockUser(user.id), updated => {
      this.info.set(`${user.username} unlocked.`);
      this.selected.set(updated);
      this.load();
    });
  }

  onResetPassword(user: UserDetail): void {
    if (!globalThis.confirm(`Reset password for ${user.username}? A temporary password will be generated.`)) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.resetPassword(user.id, {
      newPassword: null,
      mustChangePassword: true
    }).subscribe({
      next: (resp: ResetPasswordResponse) => {
        this.busy.set(false);
        this.info.set(`Password reset for ${user.username}.`);
        if (resp.temporaryPassword) {
          this.tempPasswordBanner.set({
            username: resp.user.username,
            password: resp.temporaryPassword
          });
        }
        this.selected.set(resp.user);
        this.load();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  onForceLogout(user: UserDetail): void {
    if (!globalThis.confirm(`Force ${user.username} out of every session?`)) return;
    this.run(this.api.forceLogout(user.id), () => {
      this.info.set(`${user.username} signed out everywhere.`);
    });
  }

  private load(): void {
    this.api.listUsers().subscribe({
      next: list => this.users.set(list),
      error: err => this.showError(err)
    });
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => { this.busy.set(false); onSuccess(value); },
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
