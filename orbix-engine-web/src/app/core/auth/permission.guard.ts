import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * Route guard factory: allows activation only when the current user holds the
 * given permission code. Pairs with the shell {@link authGuard} (which handles
 * authentication) — by the time this runs the user is logged in, so a missing
 * permission sends them back to the admin landing rather than to /login.
 *
 *   { path: 'fx-rates', canActivate: [permissionGuard('ADMIN.MANAGE_FX')], ... }
 *
 * The backend still enforces the same permission; this is defence-in-depth and
 * avoids rendering a shell that can only error.
 */
export function permissionGuard(code: string): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (auth.hasPermission(code)) return true;
    router.navigate(['/admin']);
    return false;
  };
}
