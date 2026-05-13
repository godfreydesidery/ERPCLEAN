import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

interface TokenPair {
  accessToken: string;
  refreshToken: string;
  user: { id: number; displayName: string; companyId: number; branchId: number };
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  login(username: string, password: string): Observable<TokenPair> {
    return this.http
      .post<TokenPair>(`${environment.apiUrl}/auth/login`, { username, password })
      .pipe(tap(t => this.storeTokens(t)));
  }

  logout(): void {
    localStorage.removeItem('orbix.access');
    localStorage.removeItem('orbix.refresh');
    localStorage.removeItem('orbix.user');
  }

  get accessToken(): string | null {
    return localStorage.getItem('orbix.access');
  }

  get isAuthenticated(): boolean {
    return !!this.accessToken;
  }

  private storeTokens(t: TokenPair): void {
    localStorage.setItem('orbix.access', t.accessToken);
    localStorage.setItem('orbix.refresh', t.refreshToken);
    localStorage.setItem('orbix.user', JSON.stringify(t.user));
  }
}
