import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { EMPTY, Observable, of, tap } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../api/api-response';

export interface UserSummary {
  id: number;
  username: string;
  displayName: string;
  defaultCompanyId: number | null;
  defaultBranchId: number | null;
  mustChangePassword: boolean;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserSummary;
}

const TOKEN_KEY = 'orbix.access';
const REFRESH_KEY = 'orbix.refresh';
const USER_KEY = 'orbix.user';
const ACTIVE_BRANCH_KEY = 'orbix.activeBranchId';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly _token = signal<string | null>(sessionStorage.getItem(TOKEN_KEY));
  private readonly _user = signal<UserSummary | null>(this.readUser());

  readonly isAuthenticated = computed(() => this._token() !== null);
  readonly currentUser = this._user.asReadonly();
  readonly accessToken = this._token.asReadonly();

  /** Permission codes carried in the access token's `perms` claim. */
  readonly permissions = computed(() => decodePermissions(this._token()));

  hasPermission(code: string): boolean {
    return this.permissions().includes(code);
  }

  /** Refresh-in-flight latch so concurrent 401s share a single refresh call. */
  private inFlightRefresh: Observable<LoginResponse> | null = null;

  login(username: string, password: string): Observable<LoginResponse> {
    return unwrap(this.http.post<ApiResponse<LoginResponse>>(
      `${environment.apiUrl}/auth/login`,
      { username, password }
    )).pipe(tap(resp => {
      // Fresh login — drop any active-branch left over from a prior session.
      localStorage.removeItem(ACTIVE_BRANCH_KEY);
      this.storeSession(resp);
    }));
  }

  refresh(): Observable<LoginResponse> {
    if (this.inFlightRefresh) return this.inFlightRefresh;
    const refreshToken = sessionStorage.getItem(REFRESH_KEY);
    if (!refreshToken) return EMPTY;
    this.inFlightRefresh = unwrap(this.http.post<ApiResponse<LoginResponse>>(
      `${environment.apiUrl}/auth/refresh`,
      { refreshToken }
    )).pipe(
      tap(resp => this.storeSession(resp)),
      tap({ complete: () => (this.inFlightRefresh = null), error: () => (this.inFlightRefresh = null) })
    );
    return this.inFlightRefresh;
  }

  logout(): Observable<void> {
    const refreshToken = sessionStorage.getItem(REFRESH_KEY);
    this.clearSession();
    if (!refreshToken) return of(void 0);
    return this.http
      .post<void>(`${environment.apiUrl}/auth/logout`, { refreshToken })
      .pipe(catchError(() => of(void 0)));  // ignore — session is already gone locally
  }

  logoutEverywhere(): Observable<void> {
    this.clearSession();
    return this.http
      .post<void>(`${environment.apiUrl}/auth/logout-everywhere`, {})
      .pipe(catchError(() => of(void 0)));
  }

  clearSession(): void {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_KEY);
    sessionStorage.removeItem(USER_KEY);
    localStorage.removeItem(ACTIVE_BRANCH_KEY);
    this._token.set(null);
    this._user.set(null);
  }

  /**
   * Persist a token pair returned by any endpoint that mints a fresh session
   * (login, refresh, branch switch). Replaces the active access + refresh
   * tokens and the cached user-summary signal.
   */
  applySession(resp: LoginResponse): void {
    this.storeSession(resp);
  }

  private storeSession(resp: LoginResponse): void {
    sessionStorage.setItem(TOKEN_KEY, resp.accessToken);
    sessionStorage.setItem(REFRESH_KEY, resp.refreshToken);
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

/** Reads the `perms` claim out of a JWT access token without verifying the signature. */
function decodePermissions(token: string | null): string[] {
  if (!token) return [];
  const payload = token.split('.')[1];
  if (!payload) return [];
  try {
    const json = atob(payload.replaceAll('-', '+').replaceAll('_', '/'));
    const claims = JSON.parse(json) as { perms?: unknown };
    return Array.isArray(claims.perms) ? (claims.perms as string[]) : [];
  } catch {
    return [];
  }
}
