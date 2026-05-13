import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.accessToken;
  const branchId = localStorage.getItem('orbix.activeBranchId');

  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (branchId) headers['X-Branch-Id'] = branchId;
  headers['X-Client-Version'] = 'web/0.1.0';

  return next(req.clone({ setHeaders: headers }));
};
