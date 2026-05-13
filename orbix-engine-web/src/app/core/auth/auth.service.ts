import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../api/api-response';

export interface UserSummary {
  id: number;
  username: string;
  displayName: string;
  defaultCompanyId: number | null;
  defaultBranchId: number | null;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserSummary;
}

const TOKEN_KEY = 'orbix.access';
const USER_KEY = 'orbix.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly _token = signal<string | null>(sessionStorage.getItem(TOKEN_KEY));
  private readonly _user = signal<UserSummary | null>(this.readUser());

  readonly isAuthenticated = computed(() => this._token() !== null);
  readonly currentUser = this._user.asReadonly();
  readonly accessToken = this._token.asReadonly();

  login(username: string, password: string): Observable<LoginResponse> {
    return unwrap(this.http.post<ApiResponse<LoginResponse>>(
      `${environment.apiUrl}/auth/login`,
      { username, password }
    )).pipe(tap(resp => this.storeSession(resp)));
  }

  logout(): void {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    this._token.set(null);
    this._user.set(null);
  }

  private storeSession(resp: LoginResponse): void {
    sessionStorage.setItem(TOKEN_KEY, resp.accessToken);
    sessionStorage.setItem(USER_KEY, JSON.stringify(resp.user));
    this._token.set(resp.accessToken);
    this._user.set(resp.user);
  }

  private readUser(): UserSummary | null {
    const raw = sessionStorage.getItem(USER_KEY);
    if (!raw) return null;
    try { return JSON.parse(raw) as UserSummary; } catch { return null; }
  }
}
