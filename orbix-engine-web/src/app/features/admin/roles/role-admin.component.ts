import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { AccessibleBranch, BranchService } from '../../../core/branch/branch.service';
import { RoleAdminService } from './role-admin.service';
import { Permission, RoleDetail, RoleGrant, RoleSummary } from './role-admin.models';

/** A user with one or more active grants of the selected role, rolled up. */
interface GrantGroup {
  userId: number;
  username: string;
  displayName: string;
  scopes: { id: number; label: string }[];
}

@Component({
  selector: 'orbix-role-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Admin</a> &rsaquo; Roles &amp; permissions
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Roles &amp; permissions</h1>
        <p class="text-secondary mb-0 small">{{ roles().length }} role{{ roles().length === 1 ? '' : 's' }} configured.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleNewRole()">
        <i class="bi" [class.bi-plus-lg]="!showNewRole()" [class.bi-x-lg]="showNewRole()"></i>
        {{ showNewRole() ? 'Close form' : 'New role' }}
      </button>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i><span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="error.set(null)"></button>
      </div>
    }

    @if (showNewRole()) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">New role</h2>
          <button class="btn-close btn-sm" (click)="toggleNewRole()"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="createRole()" #cf="ngForm" class="d-flex flex-column gap-3">
            <div class="row g-2">
              <div class="col-md-4">
                <label class="form-label small fw-semibold text-secondary">Code</label>
                <input class="form-control font-monospace text-uppercase" name="code"
                       [(ngModel)]="newCode" required placeholder="SALES_MANAGER">
              </div>
              <div class="col-md-8">
                <label class="form-label small fw-semibold text-secondary">Name</label>
                <input class="form-control" name="name" [(ngModel)]="newName" required>
              </div>
              <div class="col-12">
                <label class="form-label small fw-semibold text-secondary">Description</label>
                <textarea class="form-control" name="desc" rows="2" [(ngModel)]="newDescription"></textarea>
              </div>
            </div>
            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="saving() || cf.invalid">
                @if (saving()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-plus-lg"></i> }
                Create role
              </button>
              <button type="button" class="btn btn-outline-secondary" (click)="toggleNewRole()">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    }

    <div class="row g-3 g-md-4">
      <!-- Roles list -->
      <div class="col-12 col-lg-4">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Roles</h2>
            <span class="badge text-bg-light text-secondary">{{ roles().length }}</span>
          </div>
          @if (roles().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-shield-lock"></i></div>
              <p class="small text-secondary mb-0">No roles yet.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 rl-list">
              @for (role of roles(); track role.id) {
                <li>
                  <button type="button" class="rl-row"
                          [class.is-active]="selected()?.id === role.id"
                          (click)="selectRole(role.id)">
                    <div class="flex-grow-1 min-w-0">
                      <p class="fw-semibold text-dark mb-0 text-truncate">{{ role.name }}</p>
                      <p class="small text-secondary mb-0 font-monospace">{{ role.code }}</p>
                    </div>
                    <div class="d-flex flex-column align-items-end gap-1">
                      @if (role.isSystem) {
                        <span class="badge text-bg-primary-subtle text-primary">SYSTEM</span>
                      }
                      <span class="badge text-bg-light text-secondary">{{ role.permissionCount }} perms</span>
                    </div>
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      </div>

      <!-- Selected role detail -->
      <div class="col-12 col-lg-8">
        @if (selected(); as role) {
          <!-- Role header -->
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4">
              <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                <div>
                  <p class="small text-secondary mb-1 font-monospace">{{ role.code }}</p>
                  <h2 class="h4 fw-bold mb-1 text-dark">{{ role.name }}</h2>
                  @if (role.isSystem) {
                    <span class="badge text-bg-primary-subtle text-primary">SYSTEM ROLE</span>
                  }
                </div>
                @if (!role.isSystem) {
                  <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1"
                          (click)="deleteRole(role)" [disabled]="saving()">
                    <i class="bi bi-trash"></i> Delete role
                  </button>
                }
              </div>

              @if (role.isSystem) {
                <div class="alert alert-info py-2 mb-3 d-flex align-items-start gap-2 small">
                  <i class="bi bi-info-circle-fill mt-1"></i>
                  <div>System role — name, description and permissions are locked.</div>
                </div>
              }

              <form (ngSubmit)="saveDetails(role)" #df="ngForm" class="d-flex flex-column gap-2">
                <div>
                  <label class="form-label small fw-semibold text-secondary">Name</label>
                  <input class="form-control" name="ename" [(ngModel)]="editName" required
                         [disabled]="role.isSystem">
                </div>
                <div>
                  <label class="form-label small fw-semibold text-secondary">Description</label>
                  <textarea class="form-control" name="edesc" rows="2" [(ngModel)]="editDescription"
                            [disabled]="role.isSystem"></textarea>
                </div>
                @if (!role.isSystem) {
                  <div class="d-flex">
                    <button class="btn btn-outline-primary btn-sm d-inline-flex align-items-center gap-1"
                            [disabled]="saving() || df.invalid">
                      <i class="bi bi-save"></i> Save details
                    </button>
                  </div>
                }
              </form>
            </div>
          </div>

          <!-- Permissions -->
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Permissions</h3>
              <span class="badge text-bg-light text-secondary">{{ selectedPermissionsCount() }} selected</span>
            </div>
            <div class="card-body p-3">
              @for (group of permissionGroups(); track group.module) {
                <div class="mb-3 perm-group">
                  <div class="perm-group-header">
                    <span class="perm-group-label">{{ group.module }}</span>
                  </div>
                  <div class="row g-2">
                    @for (perm of group.perms; track perm.id) {
                      <div class="col-12 col-md-6">
                        <label class="perm-check" [for]="'perm-' + perm.id"
                               [class.is-checked]="isChecked(perm.id)"
                               [class.is-disabled]="role.isSystem">
                          <input class="form-check-input" type="checkbox" [id]="'perm-' + perm.id"
                                 [checked]="isChecked(perm.id)" [disabled]="role.isSystem"
                                 (change)="togglePermission(perm.id)">
                          <span class="flex-grow-1 min-w-0">
                            <span class="perm-code">{{ perm.code }}</span>
                            <span class="perm-desc">{{ perm.description }}</span>
                          </span>
                        </label>
                      </div>
                    }
                  </div>
                </div>
              }
              @if (!role.isSystem) {
                <button class="btn btn-outline-primary btn-sm d-inline-flex align-items-center gap-1"
                        (click)="savePermissions(role)" [disabled]="saving()">
                  <i class="bi bi-save"></i> Save permissions
                </button>
              }
            </div>
          </div>

          <!-- Granted to (read-only — manage from /admin/users) -->
          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex flex-wrap align-items-center justify-content-between gap-2">
              <div class="d-flex align-items-center gap-2 min-w-0">
                <h3 class="h6 fw-bold mb-0 text-dark">Granted to</h3>
                <span class="badge text-bg-light text-secondary">
                  {{ filteredGrantGroups().length }} user{{ filteredGrantGroups().length === 1 ? '' : 's' }}
                  @if (filteredGrantGroups().length !== grantGroups().length) { / {{ grantGroups().length }} }
                </span>
                <span class="badge text-bg-primary-subtle text-primary">
                  {{ grants().length }} grant{{ grants().length === 1 ? '' : 's' }}
                </span>
              </div>
              @if (grantGroups().length >= 5) {
                <div class="grants-search">
                  <i class="bi bi-search"></i>
                  <input type="search" class="form-control form-control-sm"
                         placeholder="Filter user / branch"
                         [(ngModel)]="grantSearchTerm"
                         (ngModelChange)="grantSearchSignal.set(grantSearchTerm)">
                </div>
              }
            </div>

            <div class="alert alert-info d-flex align-items-start gap-2 m-3 mb-0 py-2">
              <i class="bi bi-info-circle-fill mt-1"></i>
              <div class="flex-grow-1 small">
                Manage who holds this role on the
                <a routerLink="/admin/users" class="text-decoration-none fw-semibold">Users page</a>
                — open a user, then toggle roles in the Roles section.
              </div>
            </div>

            @if (grants().length === 0) {
              <div class="p-4 text-center small text-secondary">Not granted to anyone yet.</div>
            } @else if (filteredGrantGroups().length === 0) {
              <div class="p-4 text-center small text-secondary">No users match "{{ grantSearchTerm }}".</div>
            } @else {
              <ul class="list-unstyled mb-0 gt-list">
                @for (g of pagedGrantGroups(); track g.userId) {
                  <li class="gt-row">
                    <a class="d-flex align-items-center gap-3 text-decoration-none flex-grow-1 min-w-0"
                       [routerLink]="['/admin/users', g.userId]">
                      <span class="u-avatar">{{ initials(g.displayName) }}</span>
                      <div class="flex-grow-1 min-w-0">
                        <p class="fw-semibold text-dark mb-0 text-truncate">{{ g.displayName }}</p>
                        <p class="small text-secondary mb-0 font-monospace">&#64;{{ g.username }}</p>
                      </div>
                      <div class="d-flex flex-wrap gap-1 align-items-center">
                        @for (scope of g.scopes; track scope.id) {
                          <span class="gt-chip">{{ scope.label }}</span>
                        }
                      </div>
                      <i class="bi bi-chevron-right text-secondary"></i>
                    </a>
                  </li>
                }
              </ul>

              @if (filteredGrantGroups().length > grantsPageSize) {
                <div class="card-footer bg-white border-top p-3 d-flex flex-wrap justify-content-between align-items-center gap-2">
                  <small class="text-secondary">
                    Page {{ grantsPage() + 1 }} of {{ totalGrantPages() }} ·
                    {{ filteredGrantGroups().length }} user{{ filteredGrantGroups().length === 1 ? '' : 's' }}
                  </small>
                  <div class="btn-group">
                    <button class="btn btn-sm btn-outline-secondary d-inline-flex align-items-center gap-1"
                            [disabled]="grantsPage() === 0"
                            (click)="grantsPage.set(grantsPage() - 1)">
                      <i class="bi bi-chevron-left"></i> Prev
                    </button>
                    <button class="btn btn-sm btn-outline-secondary d-inline-flex align-items-center gap-1"
                            [disabled]="grantsPage() + 1 >= totalGrantPages()"
                            (click)="grantsPage.set(grantsPage() + 1)">
                      Next <i class="bi bi-chevron-right"></i>
                    </button>
                  </div>
                </div>
              }
            }
          </div>
        } @else {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick a role</h2>
              <p class="small text-secondary mb-0">Or create a new one to assign permissions.</p>
            </div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .min-w-0 { min-width: 0; }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    .rl-list { max-height: 70vh; overflow-y: auto; }
    .rl-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .rl-row:hover { background: #f8fafc; }
    .rl-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .rl-row:last-child { border-bottom: none; }

    .perm-group { background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 10px; padding: 0.75rem 1rem; }
    .perm-group-header {
      display: flex; align-items: center; gap: 0.5rem;
      margin: -0.5rem -0.5rem 0.5rem; padding: 0.25rem 0.5rem;
    }
    .perm-group-label {
      font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em;
      color: #1d4ed8;
    }

    .perm-check {
      display: flex; align-items: flex-start; gap: 0.5rem;
      padding: 0.5rem 0.75rem; border-radius: 8px;
      background: #fff; border: 1px solid #e5e7eb;
      cursor: pointer;
      transition: border-color 0.1s ease, background 0.1s ease;
    }
    .perm-check:hover { border-color: #cbd5e1; }
    .perm-check.is-checked { border-color: #1d4ed8; background: #eef4ff; }
    .perm-check.is-disabled { cursor: not-allowed; opacity: 0.7; }
    .perm-check input { margin-top: 0.2rem; flex-shrink: 0; }

    .perm-code {
      display: block; font-size: 0.78rem; font-weight: 600;
      color: #111827; font-family: ui-monospace, SFMono-Regular, monospace;
    }
    .perm-desc { display: block; font-size: 0.72rem; color: #6b7280; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.75rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }
    .simple-table .actions-col { width: 1%; white-space: nowrap; }

    .text-bg-primary-subtle { background: #e0ecff; color: #1d4ed8; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #ffe4e6; color: #be123c; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    /* ---- Granted-to list ---- */
    .grants-search { position: relative; min-width: 220px; }
    .grants-search i {
      position: absolute; left: 0.75rem; top: 50%; transform: translateY(-50%);
      color: #9ca3af; pointer-events: none; font-size: 0.85rem;
    }
    .grants-search .form-control { padding-left: 2.1rem; }

    .gt-list { max-height: 60vh; overflow-y: auto; }
    .gt-row {
      padding: 0.75rem 1rem; border-bottom: 1px solid #f3f4f6;
      transition: background 0.1s ease;
    }
    .gt-row:hover { background: #f8fafc; }
    .gt-row:last-child { border-bottom: none; }

    .u-avatar {
      width: 36px; height: 36px; border-radius: 50%;
      background: #e8eef9; color: #1a4fb5;
      display: inline-flex; align-items: center; justify-content: center;
      font-weight: 600; font-size: 0.85rem; flex-shrink: 0;
    }

    .gt-chip {
      display: inline-block; padding: 0.2rem 0.55rem;
      background: #eef4ff; color: #1d4ed8;
      border-radius: 999px;
      font-size: 0.72rem; font-weight: 600;
    }
  `]
})
export class RoleAdminComponent implements OnInit {
  private readonly api = inject(RoleAdminService);
  private readonly branchService = inject(BranchService);

  protected readonly roles = signal<RoleSummary[]>([]);
  protected readonly permissions = signal<Permission[]>([]);
  protected readonly selected = signal<RoleDetail | null>(null);
  protected readonly grants = signal<RoleGrant[]>([]);
  protected readonly branches = signal<AccessibleBranch[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showNewRole = signal(false);

  // ---- Granted-to filtering + pagination state ---------------------------
  protected readonly grantSearchSignal = signal('');
  protected grantSearchTerm = '';
  protected readonly grantsPage = signal(0);
  protected readonly grantsPageSize = 25;

  private readonly selectedPermissionIds = signal<Set<number>>(new Set());

  protected newCode = '';
  protected newName = '';
  protected newDescription = '';

  protected editName = '';
  protected editDescription = '';


  protected readonly permissionGroups = computed(() => {
    const groups = new Map<string, Permission[]>();
    for (const perm of this.permissions()) {
      const list = groups.get(perm.module) ?? [];
      list.push(perm);
      groups.set(perm.module, list);
    }
    return [...groups.entries()].map(([module, perms]) => ({ module, perms }));
  });

  protected readonly selectedPermissionsCount = computed(() => this.selectedPermissionIds().size);

  /** All grants rolled up by user, with branch names resolved. */
  protected readonly grantGroups = computed<GrantGroup[]>(() => {
    const branchById = new Map(this.branches().map(b => [b.id, b]));
    const byUser = new Map<number, GrantGroup>();
    for (const g of this.grants()) {
      const existing = byUser.get(g.userId);
      const label = g.branchId === null
        ? 'Company-wide'
        : (branchById.get(g.branchId)?.name ?? '#' + g.branchId);
      const scope = { id: g.id, label };
      if (existing) {
        existing.scopes.push(scope);
      } else {
        byUser.set(g.userId, {
          userId: g.userId,
          username: g.username,
          displayName: g.displayName,
          scopes: [scope]
        });
      }
    }
    return [...byUser.values()].sort((a, b) => a.displayName.localeCompare(b.displayName));
  });

  protected readonly filteredGrantGroups = computed<GrantGroup[]>(() => {
    const q = this.grantSearchSignal().trim().toLowerCase();
    if (!q) return this.grantGroups();
    return this.grantGroups().filter(g =>
      g.username.toLowerCase().includes(q)
      || g.displayName.toLowerCase().includes(q)
      || g.scopes.some(s => s.label.toLowerCase().includes(q)));
  });

  protected readonly totalGrantPages = computed(() =>
    Math.max(1, Math.ceil(this.filteredGrantGroups().length / this.grantsPageSize)));

  protected readonly pagedGrantGroups = computed(() => {
    const page = Math.min(this.grantsPage(), this.totalGrantPages() - 1);
    const start = page * this.grantsPageSize;
    return this.filteredGrantGroups().slice(start, start + this.grantsPageSize);
  });

  ngOnInit(): void {
    this.loadRoles();
    this.api.listPermissions().subscribe({
      next: perms => this.permissions.set(perms),
      error: err => this.showError(err)
    });
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

  toggleNewRole(): void { this.showNewRole.update(v => !v); }

  isChecked(permissionId: number): boolean {
    return this.selectedPermissionIds().has(permissionId);
  }

  togglePermission(permissionId: number): void {
    const next = new Set(this.selectedPermissionIds());
    if (next.has(permissionId)) {
      next.delete(permissionId);
    } else {
      next.add(permissionId);
    }
    this.selectedPermissionIds.set(next);
  }

  selectRole(id: number): void {
    this.error.set(null);
    // Reset the Granted-to filter/pagination when switching roles.
    this.grantSearchTerm = '';
    this.grantSearchSignal.set('');
    this.grantsPage.set(0);
    this.api.getRole(id).subscribe({
      next: role => {
        this.selected.set(role);
        this.editName = role.name;
        this.editDescription = role.description ?? '';
        this.selectedPermissionIds.set(new Set(role.permissions.map(p => p.id)));
      },
      error: err => this.showError(err)
    });
    this.api.listGrants(id).subscribe({
      next: grants => this.grants.set(grants),
      error: err => this.showError(err)
    });
  }

  createRole(): void {
    this.run(this.api.createRole({
      code: this.newCode.trim(),
      name: this.newName.trim(),
      description: this.newDescription.trim()
    }), role => {
      this.newCode = '';
      this.newName = '';
      this.newDescription = '';
      this.showNewRole.set(false);
      this.loadRoles();
      this.applySelected(role);
    });
  }

  saveDetails(role: RoleDetail): void {
    this.run(this.api.updateRole(role.id, {
      name: this.editName.trim(),
      description: this.editDescription.trim()
    }), updated => {
      this.applySelected(updated);
      this.loadRoles();
    });
  }

  savePermissions(role: RoleDetail): void {
    this.run(
      this.api.setPermissions(role.id, [...this.selectedPermissionIds()]),
      updated => {
        this.applySelected(updated);
        this.loadRoles();
      }
    );
  }

  deleteRole(role: RoleDetail): void {
    this.run(this.api.deleteRole(role.id), () => {
      this.selected.set(null);
      this.grants.set([]);
      this.loadRoles();
    });
  }


  private applySelected(role: RoleDetail): void {
    this.selected.set(role);
    this.editName = role.name;
    this.editDescription = role.description ?? '';
    this.selectedPermissionIds.set(new Set(role.permissions.map(p => p.id)));
  }

  private loadRoles(): void {
    this.api.listRoles().subscribe({
      next: roles => this.roles.set(roles),
      error: err => this.showError(err)
    });
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.saving.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => { this.saving.set(false); onSuccess(value); },
      error: err => { this.saving.set(false); this.showError(err); }
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
