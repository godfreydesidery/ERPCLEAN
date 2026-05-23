import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, unwrap } from '../../../core/api/api-response';
import { Company, UpdateCompanyRequest } from './company.models';

@Injectable({ providedIn: 'root' })
export class CompanyService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  get(): Observable<Company> {
    return unwrap(this.http.get<ApiResponse<Company>>(`${this.base}/company`));
  }

  update(request: UpdateCompanyRequest): Observable<Company> {
    return unwrap(this.http.patch<ApiResponse<Company>>(`${this.base}/company`, request));
  }
}
