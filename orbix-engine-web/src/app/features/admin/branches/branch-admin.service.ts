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

  updateBranch(uid: string, request: UpdateBranchRequest): Observable<Branch> {
    return unwrap(this.http.patch<ApiResponse<Branch>>(`${this.base}/branches/uid/${uid}`, request));
  }

  deactivateBranch(uid: string): Observable<void> {
    return this.http.post(`${this.base}/branches/uid/${uid}/deactivate`, {}).pipe(map(() => void 0));
  }

  listSections(branchUid: string): Observable<Section[]> {
    return unwrap(this.http.get<ApiResponse<Section[]>>(
      `${this.base}/branches/uid/${branchUid}/sections`
    ));
  }

  createSection(branchUid: string, request: CreateSectionRequest): Observable<Section> {
    return unwrap(this.http.post<ApiResponse<Section>>(
      `${this.base}/branches/uid/${branchUid}/sections`, request
    ));
  }

  updateSection(uid: string, request: UpdateSectionRequest): Observable<Section> {
    return unwrap(this.http.patch<ApiResponse<Section>>(`${this.base}/sections/uid/${uid}`, request));
  }

  deactivateSection(uid: string): Observable<void> {
    return this.http.post(`${this.base}/sections/uid/${uid}/deactivate`, {}).pipe(map(() => void 0));
  }
}
