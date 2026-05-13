import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService, LoginResponse } from './auth.service';

const ANONYMOUS_PATHS = ['/auth/login', '/auth/refresh', '/setup/'];

function isAnonymous(req: HttpRequest<unknown>): boolean {
  return ANONYMOUS_PATHS.some(p => req.url.includes(p));
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const cloned = withAuthHeaders(req, auth.accessToken());

  return next(cloned).pipe(
    catchError((err: HttpErrorResponse) => {
      // Only attempt silent refresh for 401 on protected endpoints.
      if (err.status !== 401 || isAnonymous(req)) {
        return throwError(() => err);
      }
      return auth.refresh().pipe(
        switchMap((resp: LoginResponse) =>
          next(withAuthHeaders(req, resp.accessToken))
        ),
        catchError(refreshErr => {
          // Refresh failed — drop session and bounce to login.
          auth.clearSession();
          void router.navigate(['/login']);
          return throwError(() => refreshErr);
        })
      );
    })
  );
};

function withAuthHeaders(req: HttpRequest<unknown>, token: string | null) {
  const branchId = localStorage.getItem('orbix.activeBranchId');
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (branchId) headers['X-Branch-Id'] = branchId;
  headers['X-Client-Version'] = 'web/0.1.0';
  return req.clone({ setHeaders: headers });
}
