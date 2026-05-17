import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, unwrap } from '../../../core/api/api-response';
import { CreateRouteRequest, Route, UpdateRouteRequest } from './route-admin.models';

@Injectable({ providedIn: 'root' })
export class RouteAdminService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listRoutes(): Observable<Route[]> {
    return unwrap(this.http.get<ApiResponse<Route[]>>(`${this.base}/routes`));
  }

  createRoute(request: CreateRouteRequest): Observable<Route> {
    return unwrap(this.http.post<ApiResponse<Route>>(`${this.base}/routes`, request));
  }

  updateRoute(id: number, request: UpdateRouteRequest): Observable<Route> {
    return unwrap(this.http.patch<ApiResponse<Route>>(`${this.base}/routes/${id}`, request));
  }

  deactivateRoute(id: number): Observable<void> {
    return this.http.post(`${this.base}/routes/${id}/deactivate`, {}).pipe(map(() => void 0));
  }
}
