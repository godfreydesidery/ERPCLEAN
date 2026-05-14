import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, unwrap } from '../../../core/api/api-response';
import {
  Branch,
  CreateBranchRequest,
  CreateSectionRequest,
  Section,
  UpdateBranchRequest,
  UpdateSectionRequest
} from './branch-admin.models';

@Injectable({ providedIn: 'root' })
export class BranchAdminService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listBranches(): Observable<Branch[]> {
    return unwrap(this.http.get<ApiResponse<Branch[]>>(`${this.base}/branches`));
  }

  createBranch(request: CreateBranchRequest): Observable<Branch> {
    return unwrap(this.http.post<ApiResponse<Branch>>(`${this.base}/branches`, request));
  }

  updateBranch(id: number, request: UpdateBranchRequest): Observable<Branch> {
    return unwrap(this.http.patch<ApiResponse<Branch>>(`${this.base}/branches/${id}`, request));
  }

  deactivateBranch(id: number): Observable<void> {
    return this.http.post(`${this.base}/branches/${id}/deactivate`, {}).pipe(map(() => void 0));
  }

  listSections(branchId: number): Observable<Section[]> {
    return unwrap(this.http.get<ApiResponse<Section[]>>(
      `${this.base}/branches/${branchId}/sections`
    ));
  }

  createSection(branchId: number, request: CreateSectionRequest): Observable<Section> {
    return unwrap(this.http.post<ApiResponse<Section>>(
      `${this.base}/branches/${branchId}/sections`, request
    ));
  }

  updateSection(id: number, request: UpdateSectionRequest): Observable<Section> {
    return unwrap(this.http.patch<ApiResponse<Section>>(`${this.base}/sections/${id}`, request));
  }

  deactivateSection(id: number): Observable<void> {
    return this.http.post(`${this.base}/sections/${id}/deactivate`, {}).pipe(map(() => void 0));
  }
}
