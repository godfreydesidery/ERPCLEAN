import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, unwrap } from '../../../core/api/api-response';
import {
  CloseTillSessionRequest,
  CreateTillRequest,
  OpenTillSessionRequest,
  Till,
  TillSession,
  UpdateTillRequest
} from './till-admin.models';

@Injectable({ providedIn: 'root' })
export class TillAdminService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listTills(branchId: string | null): Observable<Till[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<Till[]>>(`${this.base}/tills`, { params }));
  }

  createTill(request: CreateTillRequest): Observable<Till> {
    return unwrap(this.http.post<ApiResponse<Till>>(`${this.base}/tills`, request));
  }

  updateTill(id: string, request: UpdateTillRequest): Observable<Till> {
    return unwrap(this.http.patch<ApiResponse<Till>>(`${this.base}/tills/${id}`, request));
  }

  deactivateTill(id: string): Observable<Till> {
    return unwrap(this.http.post<ApiResponse<Till>>(`${this.base}/tills/${id}/deactivate`, {}));
  }

  activateTill(id: string): Observable<Till> {
    return unwrap(this.http.post<ApiResponse<Till>>(`${this.base}/tills/${id}/activate`, {}));
  }

  listSessions(branchId: string | null, tillId: string | null = null): Observable<TillSession[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    if (tillId != null) params = params.set('tillId', tillId);
    return unwrap(this.http.get<ApiResponse<TillSession[]>>(`${this.base}/till-sessions`, { params }));
  }

  openSession(request: OpenTillSessionRequest): Observable<TillSession> {
    return unwrap(this.http.post<ApiResponse<TillSession>>(
      `${this.base}/till-sessions/open`, request
    ));
  }

  closeSession(id: string, request: CloseTillSessionRequest): Observable<TillSession> {
    return unwrap(this.http.post<ApiResponse<TillSession>>(
      `${this.base}/till-sessions/${id}/close`, request
    ));
  }

  reconcileSession(id: string): Observable<TillSession> {
    return unwrap(this.http.post<ApiResponse<TillSession>>(
      `${this.base}/till-sessions/${id}/reconcile`, {}
    ));
  }
}
