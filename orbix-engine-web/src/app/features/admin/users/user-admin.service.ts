import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, unwrap } from '../../../core/api/api-response';
import {
  ChangePasswordRequest,
  CreateUserRequest,
  CreateUserResponse,
  ResetPasswordRequest,
  ResetPasswordResponse,
  UpdateUserRequest,
  UserDetail,
  UserSummary
} from './user-admin.models';

@Injectable({ providedIn: 'root' })
export class UserAdminService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listUsers(): Observable<UserSummary[]> {
    return unwrap(this.http.get<ApiResponse<UserSummary[]>>(`${this.base}/users`));
  }

  getUser(id: number): Observable<UserDetail> {
    return unwrap(this.http.get<ApiResponse<UserDetail>>(`${this.base}/users/${id}`));
  }

  createUser(request: CreateUserRequest): Observable<CreateUserResponse> {
    return unwrap(this.http.post<ApiResponse<CreateUserResponse>>(`${this.base}/users`, request));
  }

  updateUser(id: number, request: UpdateUserRequest): Observable<UserDetail> {
    return unwrap(this.http.patch<ApiResponse<UserDetail>>(`${this.base}/users/${id}`, request));
  }

  resetPassword(id: number, request: ResetPasswordRequest): Observable<ResetPasswordResponse> {
    return unwrap(this.http.post<ApiResponse<ResetPasswordResponse>>(
      `${this.base}/users/${id}/reset-password`, request
    ));
  }

  disableUser(id: number): Observable<UserDetail> {
    return unwrap(this.http.post<ApiResponse<UserDetail>>(`${this.base}/users/${id}/disable`, {}));
  }

  enableUser(id: number): Observable<UserDetail> {
    return unwrap(this.http.post<ApiResponse<UserDetail>>(`${this.base}/users/${id}/enable`, {}));
  }

  unlockUser(id: number): Observable<UserDetail> {
    return unwrap(this.http.post<ApiResponse<UserDetail>>(`${this.base}/users/${id}/unlock`, {}));
  }

  forceLogout(id: number): Observable<void> {
    return this.http.post(`${this.base}/users/${id}/force-logout`, {}).pipe(map(() => void 0));
  }

  changeMyPassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.post(`${this.base}/users/me/change-password`, request).pipe(map(() => void 0));
  }
}
