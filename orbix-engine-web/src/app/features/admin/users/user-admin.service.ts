import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
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
  UserPage
} from './user-admin.models';

@Injectable({ providedIn: 'root' })
export class UserAdminService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listUsers(q: string, status: string, page: number, size: number): Observable<UserPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (status && status !== 'all') params = params.set('status', status);
    return unwrap(this.http.get<ApiResponse<UserPage>>(`${this.base}/users`, { params }));
  }

  getUser(uid: string): Observable<UserDetail> {
    return unwrap(this.http.get<ApiResponse<UserDetail>>(`${this.base}/users/uid/${uid}`));
  }

  createUser(request: CreateUserRequest): Observable<CreateUserResponse> {
    return unwrap(this.http.post<ApiResponse<CreateUserResponse>>(`${this.base}/users`, request));
  }

  updateUser(uid: string, request: UpdateUserRequest): Observable<UserDetail> {
    return unwrap(this.http.patch<ApiResponse<UserDetail>>(`${this.base}/users/uid/${uid}`, request));
  }

  resetPassword(uid: string, request: ResetPasswordRequest): Observable<ResetPasswordResponse> {
    return unwrap(this.http.post<ApiResponse<ResetPasswordResponse>>(
      `${this.base}/users/uid/${uid}/reset-password`, request
    ));
  }

  disableUser(uid: string): Observable<UserDetail> {
    return unwrap(this.http.post<ApiResponse<UserDetail>>(`${this.base}/users/uid/${uid}/disable`, {}));
  }

  enableUser(uid: string): Observable<UserDetail> {
    return unwrap(this.http.post<ApiResponse<UserDetail>>(`${this.base}/users/uid/${uid}/enable`, {}));
  }

  unlockUser(uid: string): Observable<UserDetail> {
    return unwrap(this.http.post<ApiResponse<UserDetail>>(`${this.base}/users/uid/${uid}/unlock`, {}));
  }

  forceLogout(uid: string): Observable<void> {
    return this.http.post(`${this.base}/users/uid/${uid}/force-logout`, {}).pipe(map(() => void 0));
  }

  changeMyPassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.post(`${this.base}/users/me/change-password`, request).pipe(map(() => void 0));
  }
}
