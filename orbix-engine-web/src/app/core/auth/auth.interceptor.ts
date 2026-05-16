import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService, LoginResponse } from './auth.service';
import { BranchService } from '../branch/branch.service';

const ANONYMOUS_PATHS = ['/auth/login', '/auth/refresh', '/setup/'];
const STALE_BRANCH_RETRY_HEADER = 'X-Orbix-Stale-Branch-Retry';

function isAnonymous(req: HttpRequest<unknown>): boolean {
  return ANONYMOUS_PATHS.some(p => req.url.includes(p));
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const branch = inject(BranchService);
  const router = inject(Router);

  const cloned = withAuthHeaders(req, auth.accessToken());

  return next(cloned).pipe(
    catchError((err: HttpErrorResponse) => {
      // 401 — try silent refresh and retry the original request.
      if (err.status === 401 && !isAnonymous(req)) {
        return auth.refresh().pipe(
          switchMap((resp: LoginResponse) =>
            next(withAuthHeaders(req, resp.accessToken))
          ),
          catchError(refreshErr => {
            auth.clearSession();
            void router.navigate(['/login']);
            return throwError(() => refreshErr);
          })
        );
      }

      // 403 with a stale X-Branch-Id override — clear the override and
      // retry once. Recovers from "I switched branches in a past session,
      // lost access, came back, every call denies" without forcing a
      // re-login. We only attempt this once per request to avoid loops
      // when the 403 is a real permission denial.
      if (err.status === 403
          && !isAnonymous(req)
          && !req.headers.has(STALE_BRANCH_RETRY_HEADER)
          && hasStaleBranchOverride(auth.accessToken())) {
        branch.clear();
        const retried = withAuthHeaders(req, auth.accessToken()).clone({
          setHeaders: { [STALE_BRANCH_RETRY_HEADER]: '1' }
        });
        return next(retried);
      }

      return throwError(() => err);
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

/**
 * True when {@code localStorage.activeBranchId} points at a branch
 * different from the JWT's baked-in {@code bid} claim — i.e. the
 * outgoing request will stamp an X-Branch-Id override that the
 * backend's BranchAccessGuard must validate. Stale overrides surviving
 * across sessions cause persistent 403s; this detector lets the
 * interceptor recover automatically.
 */
function hasStaleBranchOverride(token: string | null): boolean {
  if (!token) return false;
  const stored = localStorage.getItem('orbix.activeBranchId');
  if (!stored) return false;
  try {
    const payload = JSON.parse(
      atob(token.split('.')[1].replaceAll('-', '+').replaceAll('_', '/'))
    ) as { bid?: number };
    const bid = payload.bid;
    return bid !== Number(stored);
  } catch {
    return false;
  }
}
