import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, unwrap } from '../../../core/api/api-response';
import { AuditFilters, AuditIntegrityResult, AuditPage } from './audit.models';

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  list(filters: AuditFilters, page: number, size: number): Observable<AuditPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filters)) {
      if (value != null && value !== '') {
        params = params.set(key, value);
      }
    }
    return unwrap(this.http.get<ApiResponse<AuditPage>>(`${this.base}/audit`, { params }));
  }

  verify(from?: string, to?: string): Observable<AuditIntegrityResult> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return unwrap(this.http.get<ApiResponse<AuditIntegrityResult>>(`${this.base}/audit/integrity`, { params }));
  }
}
