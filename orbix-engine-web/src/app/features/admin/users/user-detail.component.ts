import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { AccessibleBranch, BranchService } from '../../../core/branch/branch.service';
import { SearchSelectComponent, SearchSelectOption } from '../../../core/ui/search-select.component';
import { RoleAdminService } from '../roles/role-admin.service';
import { RoleSummary } from '../roles/role-admin.models';
import { UserAdminService } from './user-admin.service';
import {
  ResetPasswordResponse,
  RoleGrantSummary,
  UserDetail
} from './user-admin.models';

@Component({
  selector: 'orbix-user-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, SearchSelectComponent],
  template: `
    <header class="mb-3">
      <a routerLink="/admin/users" class="text-decoration-none small text-secondary d-inline-flex align-items-center gap-1 mb-2">
        <i class="bi bi-arrow-left"></i> Back to users
      </a>
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
        <a routerLink="/admin" class="text-decoration-none text-secondary">Admin</a> &rsaquo;
        <a routerLink="/admin/users" class="text-decoration-none text-secondary">Users</a> &rsaquo;
        @if (user()) { {{ user()!.username }} } @else { … }
      </p>
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
      <div class="alert alert-warning d-flex align-items-start gap-2 py-3 mb-3">
        <i class="bi bi-key-fill mt-1"></i>
        <div class="flex-grow-1">
          <p class="fw-semibold mb-1">Temporary password for {{ banner.username }}</p>
          <p class="small mb-2">Hand this to the user privately. It will not be shown again.</p>
          <code class="temp-pwd">{{ banner.password }}</code>
        </div>
        <button type="button" class="btn-close btn-sm" (click)="tempPasswordBanner.set(null)"></button>
      </div>
    }

    @if (user(); as user) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-body p-3 p-md-4">
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
            <div class="user-actions d-flex flex-wrap gap-2">
              @if (user.status === 'ACTIVE') {
                <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center justify-content-center gap-1"
                        (click)="onDisable(user)" [disabled]="busy()">
                  <i class="bi bi-pause-circle"></i> Disable
                </button>
              } @else {
                <button class="btn btn-sm btn-outline-success d-inline-flex align-items-center justify-content-center gap-1"
                        (click)="onEnable(user)" [disabled]="busy()">
                  <i class="bi bi-play-circle"></i> Enable
                </button>
              }
              @if (user.locked) {
                <button class="btn btn-sm btn-outline-warning d-inline-flex align-items-center justify-content-center gap-1"
                        (click)="onUnlock(user)" [disabled]="busy()">
                  <i class="bi bi-unlock"></i> Unlock
                </button>
              }
              <button class="btn btn-sm btn-outline-primary d-inline-flex align-items-center justify-content-center gap-1"
                      (click)="onResetPassword(user)" [disabled]="busy()">
                <i class="bi bi-key"></i><span class="d-none d-sm-inline">Reset password</span><span class="d-sm-none">Reset</span>
              </button>
              <button class="btn btn-sm btn-outline-secondary d-inline-flex align-items-center justify-content-center gap-1"
                      (click)="onForceLogout(user)" [disabled]="busy()">
                <i class="bi bi-box-arrow-right"></i><span class="d-none d-sm-inline">Force logout</span><span class="d-sm-none">Logout</span>
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
                  <label class="form-label small fw-semibold text-secondary">Default branch</label>
                  <orbix-search-select [options]="branchOptions()" [(ngModel)]="editForm.defaultBranchId"
                                       name="br"
                                       placeholder="— No default —"/>
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

          <div class="user-meta-strip mt-3" role="group" aria-label="Audit timestamps">
            <span [title]="'Last login: ' + (user.lastLoginAt ? (user.lastLoginAt | date:'medium') : 'never')">
              <i class="bi bi-box-arrow-in-right"></i>
              {{ user.lastLoginAt ? (user.lastLoginAt | date:'mediumDate') : 'Never logged in' }}
            </span>
            <span class="user-meta-strip__sep">·</span>
            <span [title]="'Created: ' + (user.createdAt | date:'medium')">
              <i class="bi bi-calendar-plus"></i>
              Created {{ user.createdAt | date:'mediumDate' }}
            </span>
            <span class="user-meta-strip__sep">·</span>
            <span [title]="'Updated: ' + (user.updatedAt | date:'medium')">
              <i class="bi bi-clock-history"></i>
              Updated {{ user.updatedAt | date:'mediumDate' }}
            </span>
          </div>
        </div>
      </div>

      <!-- Roles -->
      <div class="card border-0 shadow-sm overflow-hidden">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h3 class="h6 fw-bold mb-0 text-dark">Roles</h3>
          <span class="badge text-bg-light text-secondary">{{ user.grants.length }} grant{{ user.grants.length === 1 ? '' : 's' }}</span>
        </div>
        @if (roles().length === 0) {
          <div class="p-4 text-center small text-secondary">
            No roles defined yet. Create roles on the
            <a routerLink="/admin/roles" class="text-decoration-none">Roles &amp; permissions</a> page first.
          </div>
        } @else {
          <ul class="list-unstyled mb-0">
            @for (role of roles(); track role.id) {
              <li class="role-row">
                <div class="d-flex flex-wrap align-items-start justify-content-between gap-2 mb-2">
                  <div class="min-w-0 flex-grow-1">
                    <p class="fw-semibold text-dark mb-0">{{ role.name }}</p>
                    <p class="small text-secondary mb-0">
                      <span class="font-monospace">{{ role.code }}</span>
                      @if (role.isSystem) {
                        <span class="badge text-bg-primary-subtle text-primary ms-1">SYSTEM</span>
                      }
                      · {{ role.permissionCount }} permission{{ role.permissionCount === 1 ? '' : 's' }}
                    </p>
                  </div>
                </div>

                @if (grantsForRole(user, role.id); as roleGrants) {
                  @if (roleGrants.length > 0) {
                    <div class="grant-chips mb-2">
                      @for (g of roleGrants; track g.id) {
                        <span class="grant-chip">
                          <i class="bi bi-check2 me-1"></i>
                          @if (g.branchId === null) { Company-wide }
                          @else { Branch · {{ branchLabel(g.branchId) }} }
                          <button type="button" class="grant-chip__x"
                                  [disabled]="busy()"
                                  (click)="onRevoke(user, g)"
                                  title="Revoke">
                            <i class="bi bi-x"></i>
                          </button>
                        </span>
                      }
                    </div>
                  }

                  <div class="grant-add d-flex align-items-center gap-2 flex-wrap">
                    <select class="form-select form-select-sm grant-add__select"
                            [(ngModel)]="grantPick[role.id]" name="gp{{ role.id }}">
                      <option [ngValue]="null">— Pick scope —</option>
                      @if (!hasCompanyWide(roleGrants)) {
                        <option [ngValue]="''">Company-wide</option>
                      }
                      @for (b of branches(); track b.id) {
                        @if (!hasBranch(roleGrants, b.id)) {
                          <option [ngValue]="b.id">Branch · {{ b.name }}</option>
                        }
                      }
                    </select>
                    <button type="button" class="btn btn-sm btn-outline-primary d-inline-flex align-items-center gap-1"
                            [disabled]="busy() || grantPick[role.id] == null"
                            (click)="onGrant(user, role)">
                      <i class="bi bi-plus-lg"></i> Grant
                    </button>
                  </div>
                }
              </li>
            }
          </ul>
        }
      </div>
    } @else if (!loading()) {
      <div class="card border-0 shadow-sm">
        <div class="card-body p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-person-x"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">User not found</h2>
          <p class="small text-secondary mb-3">This user doesn't exist or belongs to another company.</p>
          <a routerLink="/admin/users" class="btn btn-sm btn-outline-primary">Back to users</a>
        </div>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }
    .min-w-0 { min-width: 0; }

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
    .text-bg-primary-subtle { background: #e0ecff; color: #1d4ed8; }

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

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #fee2e2; color: #b91c1c; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .temp-pwd {
      display: inline-block; padding: 0.4rem 0.75rem;
      background: #fff; border: 1px dashed #b45309; border-radius: 6px;
      font-size: 1.05rem; font-weight: 600; letter-spacing: 0.05em;
      color: #78350f; user-select: all;
    }

    .user-meta-strip {
      display: flex; flex-wrap: wrap; align-items: center;
      gap: 0.5rem 0.75rem;
      padding-top: 0.75rem;
      border-top: 1px solid #f3f4f6;
      font-size: 0.78rem;
      color: #6b7280;
    }
    .user-meta-strip span { display: inline-flex; align-items: center; gap: 0.3rem; }
    .user-meta-strip i { color: #9ca3af; font-size: 0.88rem; }
    .user-meta-strip__sep { color: #d1d5db; padding: 0 0.1rem; }
    @media (max-width: 575.98px) {
      .user-meta-strip__sep { display: none; }
      .user-meta-strip { gap: 0.4rem 0.6rem; }
    }

    .role-row {
      padding: 1rem 1.25rem;
      border-bottom: 1px solid #f3f4f6;
    }
    .role-row:last-child { border-bottom: none; }

    .grant-chips { display: flex; flex-wrap: wrap; gap: 0.5rem; }
    .grant-chip {
      display: inline-flex; align-items: center;
      padding: 0.3rem 0.5rem 0.3rem 0.7rem;
      background: #eef4ff; color: #1d4ed8;
      border-radius: 999px;
      font-size: 0.78rem; font-weight: 600;
    }
    .grant-chip__x {
      margin-left: 0.4rem;
      width: 20px; height: 20px;
      display: inline-flex; align-items: center; justify-content: center;
      background: transparent; border: none; border-radius: 50%;
      color: #1d4ed8;
      cursor: pointer;
    }
    .grant-chip__x:hover { background: #dbe8ff; }
    .grant-chip__x:disabled { opacity: 0.5; cursor: not-allowed; }

    .grant-add__select { max-width: 260px; }

    @media (max-width: 991.98px) {
      .user-actions {
        width: 100%;
        display: grid !important;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 0.5rem !important;
      }
      .user-actions .btn { min-height: 44px; font-size: 0.85rem; }

      .role-row { padding: 0.875rem 1rem; }
      .grant-add__select { max-width: 100% !important; flex: 1 1 0; }
    }
  `]
})
export class UserDetailComponent implements OnInit {
  private readonly api = inject(UserAdminService);
  private readonly branchService = inject(BranchService);
  private readonly roleApi = inject(RoleAdminService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly user = signal<UserDetail | null>(null);
  protected readonly roles = signal<RoleSummary[]>([]);
  protected readonly branches = signal<AccessibleBranch[]>([]);
  protected readonly branchOptions = computed<SearchSelectOption[]>(
    () => this.branches().map(b => ({ id: b.id, label: `${b.code} · ${b.name}` })));
  protected readonly busy = signal(false);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly tempPasswordBanner = signal<{ username: string; password: string } | null>(null);

  protected grantPick: Record<string, string | null> = {};
  protected editForm = { displayName: '', email: '', phone: '', defaultBranchId: null as string | null };

  ngOnInit(): void {
    const uid = this.route.snapshot.paramMap.get('uid');
    if (!uid) {
      this.error.set('Invalid user uid');
      this.loading.set(false);
      return;
    }
    // Pick up a temp-password handoff from the create flow on the list page.
    const stashKey = 'orbix.tempPwd.' + uid;
    const stash = globalThis.sessionStorage.getItem(stashKey);
    if (stash) {
      try {
        const parsed = JSON.parse(stash) as { username: string; password: string };
        this.tempPasswordBanner.set(parsed);
      } catch { /* ignore malformed */ }
      globalThis.sessionStorage.removeItem(stashKey);
    }

    this.loadUser(uid);
    this.branchService.listBranches().subscribe({
      next: list => this.branches.set(list),
      error: () => this.branches.set([])
    });
    this.roleApi.listRoles().subscribe({
      next: list => this.roles.set(list),
      error: () => this.roles.set([])
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

  // --- role-grant helpers -------------------------------------------------

  grantsForRole(user: UserDetail, roleId: string): RoleGrantSummary[] {
    return user.grants.filter(g => g.roleId === roleId);
  }

  hasCompanyWide(grants: RoleGrantSummary[]): boolean {
    return grants.some(g => g.branchId === null);
  }

  hasBranch(grants: RoleGrantSummary[], branchId: string): boolean {
    return grants.some(g => g.branchId === branchId);
  }

  /** Resolve a branch id to its display name; "—" when unset, {@code #id} if unknown. */
  branchLabel(branchId: string | null | undefined): string {
    if (!branchId) return '—';
    const b = this.branches().find(x => x.id === branchId);
    return b ? b.name : '#' + branchId;
  }

  onGrant(user: UserDetail, role: RoleSummary): void {
    const pick = this.grantPick[role.id];
    if (pick == null) return;
    // The "company-wide" sentinel is the empty string; otherwise pick is a branch uid string.
    const branchId = pick === '' ? null : pick;
    this.busy.set(true);
    this.error.set(null);
    this.roleApi.grantRole(role.uid, { username: user.username, branchId }).subscribe({
      next: () => {
        this.busy.set(false);
        this.grantPick[role.id] = null;
        this.info.set(`Granted ${role.code} to ${user.username}.`);
        this.loadUser(user.uid);
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  onRevoke(user: UserDetail, grant: RoleGrantSummary): void {
    if (!globalThis.confirm(`Revoke this role from ${user.username}?`)) return;
    this.busy.set(true);
    this.error.set(null);
    this.roleApi.revokeGrant(grant.uid).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Role revoked from ${user.username}.`);
        this.loadUser(user.uid);
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  // --- profile + lifecycle ------------------------------------------------

  onSave(user: UserDetail): void {
    this.run(this.api.updateUser(user.uid, {
      displayName: this.editForm.displayName.trim(),
      email: emptyToNull(this.editForm.email),
      phone: emptyToNull(this.editForm.phone),
      defaultBranchId: this.editForm.defaultBranchId
    }), updated => {
      this.info.set('Profile saved.');
      this.user.set(updated);
      this.applyEditForm(updated);
    });
  }

  onDisable(user: UserDetail): void {
    if (!globalThis.confirm(`Disable ${user.username}? They will lose access immediately.`)) return;
    this.run(this.api.disableUser(user.uid), updated => {
      this.info.set(`${user.username} disabled.`);
      this.user.set(updated);
    });
  }

  onEnable(user: UserDetail): void {
    this.run(this.api.enableUser(user.uid), updated => {
      this.info.set(`${user.username} re-enabled.`);
      this.user.set(updated);
    });
  }

  onUnlock(user: UserDetail): void {
    this.run(this.api.unlockUser(user.uid), updated => {
      this.info.set(`${user.username} unlocked.`);
      this.user.set(updated);
    });
  }

  onResetPassword(user: UserDetail): void {
    if (!globalThis.confirm(`Reset password for ${user.username}? A temporary password will be generated.`)) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.resetPassword(user.uid, {
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
        this.user.set(resp.user);
        this.applyEditForm(resp.user);
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  onForceLogout(user: UserDetail): void {
    if (!globalThis.confirm(`Force ${user.username} out of every session?`)) return;
    this.run(this.api.forceLogout(user.uid), () => {
      this.info.set(`${user.username} signed out everywhere.`);
    });
  }

  private loadUser(uid: string): void {
    this.loading.set(true);
    this.api.getUser(uid).subscribe({
      next: detail => {
        this.user.set(detail);
        this.applyEditForm(detail);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        if (err instanceof HttpErrorResponse && err.status === 404) {
          this.user.set(null);
        } else {
          this.showError(err);
        }
      }
    });
  }

  private applyEditForm(detail: UserDetail): void {
    this.editForm = {
      displayName: detail.displayName,
      email: detail.email ?? '',
      phone: detail.phone ?? '',
      defaultBranchId: detail.defaultBranchId
    };
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

function emptyToNull(value: string | null | undefined): string | null {
  if (value == null) return null;
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}
