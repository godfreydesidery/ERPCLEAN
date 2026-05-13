import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      // TODO: surface via toast service; redirect to /login on 401.
      console.error(`[${err.status}] ${req.method} ${req.url}`, err.error);
      return throwError(() => err);
    })
  );
