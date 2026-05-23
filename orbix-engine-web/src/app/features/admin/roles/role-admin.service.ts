import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, unwrap } from '../../../core/api/api-response';
import {
  CreateRoleRequest,
  GrantRoleRequest,
  Permission,
  RoleDetail,
  RoleGrant,
  RoleSummary,
  UpdateRoleRequest
} from './role-admin.models';

@Injectable({ providedIn: 'root' })
export class RoleAdminService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listPermissions(): Observable<Permission[]> {
    return unwrap(this.http.get<ApiResponse<Permission[]>>(`${this.base}/permissions`));
  }

  listRoles(): Observable<RoleSummary[]> {
    return unwrap(this.http.get<ApiResponse<RoleSummary[]>>(`${this.base}/roles`));
  }

  getRole(uid: string): Observable<RoleDetail> {
    return unwrap(this.http.get<ApiResponse<RoleDetail>>(`${this.base}/roles/uid/${uid}`));
  }

  createRole(request: CreateRoleRequest): Observable<RoleDetail> {
    return unwrap(this.http.post<ApiResponse<RoleDetail>>(`${this.base}/roles`, request));
  }

  updateRole(uid: string, request: UpdateRoleRequest): Observable<RoleDetail> {
    return unwrap(this.http.patch<ApiResponse<RoleDetail>>(`${this.base}/roles/uid/${uid}`, request));
  }

  setPermissions(uid: string, permissionIds: string[]): Observable<RoleDetail> {
    return unwrap(this.http.put<ApiResponse<RoleDetail>>(
      `${this.base}/roles/uid/${uid}/permissions`, { permissionIds }
    ));
  }

  deleteRole(uid: string): Observable<void> {
    return this.http.delete(`${this.base}/roles/uid/${uid}`).pipe(map(() => void 0));
  }

  listGrants(roleUid: string): Observable<RoleGrant[]> {
    return unwrap(this.http.get<ApiResponse<RoleGrant[]>>(`${this.base}/roles/uid/${roleUid}/grants`));
  }

  grantRole(roleUid: string, request: GrantRoleRequest): Observable<RoleGrant> {
    return unwrap(this.http.post<ApiResponse<RoleGrant>>(
      `${this.base}/roles/uid/${roleUid}/grants`, request
    ));
  }

  revokeGrant(grantUid: string): Observable<void> {
    return this.http.delete(`${this.base}/grants/uid/${grantUid}`).pipe(map(() => void 0));
  }
}
