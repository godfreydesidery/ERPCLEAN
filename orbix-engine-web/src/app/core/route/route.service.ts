import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../api/api-response';

/** A delivery route (sales territory) within the caller's company. */
export interface Route {
  id: string;
  uid: string;
  companyId: string;
  code: string;
  name: string;
  description: string | null;
  status: 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
}

@Injectable({ providedIn: 'root' })
export class RouteService {
  private readonly http = inject(HttpClient);

  listRoutes(): Observable<Route[]> {
    return unwrap(this.http.get<ApiResponse<Route[]>>(
      `${environment.apiUrl}/routes`
    ));
  }
}
