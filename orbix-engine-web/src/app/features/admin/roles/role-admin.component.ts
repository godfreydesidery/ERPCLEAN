import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { RoleAdminService } from './role-admin.service';
import { Permission, RoleDetail, RoleGrant, RoleSummary } from './role-admin.models';

@Component({
  selector: 'orbix-role-admin',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <h2 class="h3 mb-4">Roles &amp; permissions</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <div class="row g-4">
      <!-- Roles list + create -->
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Roles</div>
          <div class="list-group list-group-flush">
            @for (role of roles(); track role.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
                      [class.active]="selected()?.id === role.id"
                      (click)="selectRole(role.id)">
                <span>
                  <span class="fw-semibold">{{ role.name }}</span>
                  <small class="d-block text-muted">{{ role.code }}</small>
                </span>
                <span>
                  @if (role.isSystem) {
                    <span class="badge text-bg-secondary me-1">system</span>
                  }
                  <span class="badge text-bg-light">{{ role.permissionCount }}</span>
                </span>
              </button>
            } @empty {
              <div class="list-group-item text-muted">No roles yet.</div>
            }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New role</div>
          <div class="card-body">
            <form (ngSubmit)="createRole()" #cf="ngForm">
              <div class="mb-2">
                <label class="form-label">Code</label>
                <input class="form-control" name="code" [(ngModel)]="newCode" required
                       placeholder="e.g. SALES_MANAGER">
              </div>
              <div class="mb-2">
                <label class="form-label">Name</label>
                <input class="form-control" name="name" [(ngModel)]="newName" required>
              </div>
              <div class="mb-2">
                <label class="form-label">Description</label>
                <textarea class="form-control" name="desc" rows="2" [(ngModel)]="newDescription"></textarea>
              </div>
              <button class="btn btn-primary w-100" [disabled]="saving() || cf.invalid">Create role</button>
            </form>
          </div>
        </div>
      </div>

      <!-- Selected role detail -->
      <div class="col-12 col-lg-8">
        @if (selected(); as role) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ role.code }}</span>
              @if (!role.isSystem) {
                <button class="btn btn-sm btn-outline-danger" (click)="deleteRole(role)"
                        [disabled]="saving()">Delete role</button>
              }
            </div>
            <div class="card-body">
              @if (role.isSystem) {
                <div class="alert alert-secondary py-2">
                  System role — name, description and permissions are locked.
                </div>
              }

              <!-- Details -->
              <form (ngSubmit)="saveDetails(role)" #df="ngForm" class="mb-4">
                <div class="mb-2">
                  <label class="form-label">Name</label>
                  <input class="form-control" name="ename" [(ngModel)]="editName" required
                         [disabled]="role.isSystem">
                </div>
                <div class="mb-2">
                  <label class="form-label">Description</label>
                  <textarea class="form-control" name="edesc" rows="2" [(ngModel)]="editDescription"
                            [disabled]="role.isSystem"></textarea>
                </div>
                @if (!role.isSystem) {
                  <button class="btn btn-outline-primary btn-sm" [disabled]="saving() || df.invalid">
                    Save details
                  </button>
                }
              </form>

              <!-- Permissions -->
              <h3 class="h6">Permissions</h3>
              @for (group of permissionGroups(); track group.module) {
                <div class="mb-3">
                  <div class="text-uppercase text-muted small fw-semibold mb-1">{{ group.module }}</div>
                  <div class="row">
                    @for (perm of group.perms; track perm.id) {
                      <div class="col-12 col-md-6">
                        <div class="form-check">
                          <input class="form-check-input" type="checkbox" [id]="'perm-' + perm.id"
                                 [checked]="isChecked(perm.id)" [disabled]="role.isSystem"
                                 (change)="togglePermission(perm.id)">
                          <label class="form-check-label" [for]="'perm-' + perm.id">
                            <span class="fw-semibold">{{ perm.code }}</span>
                            <small class="d-block text-muted">{{ perm.description }}</small>
                          </label>
                        </div>
                      </div>
                    }
                  </div>
                </div>
              }
              @if (!role.isSystem) {
                <button class="btn btn-outline-primary btn-sm mb-4" (click)="savePermissions(role)"
                        [disabled]="saving()">Save permissions</button>
              }

              <!-- Grants -->
              <h3 class="h6">Granted to</h3>
              <table class="table table-sm align-middle">
                <thead>
                  <tr><th>User</th><th>Branch</th><th>Granted</th><th></th></tr>
                </thead>
                <tbody>
                  @for (grant of grants(); track grant.id) {
                    <tr>
                      <td>
                        <span class="fw-semibold">{{ grant.displayName }}</span>
                        <small class="text-muted">&#64;{{ grant.username }}</small>
                      </td>
                      <td>{{ grant.branchId ?? 'company-wide' }}</td>
                      <td>{{ grant.grantedAt | date:'short' }}</td>
                      <td class="text-end">
                        <button class="btn btn-sm btn-outline-danger" (click)="revoke(grant)"
                                [disabled]="saving()">Revoke</button>
                      </td>
                    </tr>
                  } @empty {
                    <tr><td colspan="4" class="text-muted">Not granted to anyone yet.</td></tr>
                  }
                </tbody>
              </table>

              <form (ngSubmit)="grant(role)" #gf="ngForm" class="row g-2 align-items-end">
                <div class="col-12 col-sm-6">
                  <label class="form-label">Username</label>
                  <input class="form-control" name="guser" [(ngModel)]="grantUsername" required>
                </div>
                <div class="col-8 col-sm-3">
                  <label class="form-label">Branch id <small class="text-muted">(optional)</small></label>
                  <input class="form-control" name="gbranch" type="number" [(ngModel)]="grantBranchId">
                </div>
                <div class="col-4 col-sm-3">
                  <button class="btn btn-primary w-100" [disabled]="saving() || gf.invalid">Grant</button>
                </div>
              </form>
            </div>
          </div>
        } @else {
          <div class="text-muted">Select a role to view its permissions and grants.</div>
        }
      </div>
    </div>
  `
})
export class RoleAdminComponent implements OnInit {
  private readonly api = inject(RoleAdminService);

  readonly roles = signal<RoleSummary[]>([]);
  readonly permissions = signal<Permission[]>([]);
  readonly selected = signal<RoleDetail | null>(null);
  readonly grants = signal<RoleGrant[]>([]);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  private readonly selectedPermissionIds = signal<Set<number>>(new Set());

  newCode = '';
  newName = '';
  newDescription = '';

  editName = '';
  editDescription = '';

  grantUsername = '';
  grantBranchId: number | null = null;

  readonly permissionGroups = computed(() => {
    const groups = new Map<string, Permission[]>();
    for (const perm of this.permissions()) {
      const list = groups.get(perm.module) ?? [];
      list.push(perm);
      groups.set(perm.module, list);
    }
    return [...groups.entries()].map(([module, perms]) => ({ module, perms }));
  });

  ngOnInit(): void {
    this.loadRoles();
    this.api.listPermissions().subscribe({
      next: perms => this.permissions.set(perms),
      error: err => this.showError(err)
    });
  }

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

  grant(role: RoleDetail): void {
    this.run(this.api.grantRole(role.id, {
      username: this.grantUsername.trim(),
      branchId: this.grantBranchId
    }), () => {
      this.grantUsername = '';
      this.grantBranchId = null;
      this.selectRole(role.id);
      this.loadRoles();
    });
  }

  revoke(grant: RoleGrant): void {
    const role = this.selected();
    this.run(this.api.revokeGrant(grant.id), () => {
      if (role) {
        this.selectRole(role.id);
        this.loadRoles();
      }
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
      next: value => {
        this.saving.set(false);
        onSuccess(value);
      },
      error: err => {
        this.saving.set(false);
        this.showError(err);
      }
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
