import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';

export interface OrganisationInfo {
  name: string;
  legalName: string;
  currencyCode: string;
  countryCode: string;
}

export interface CompanyInfo {
  code: string;
  name: string;
  timeZone: string;
}

export interface BranchInfo {
  code: string;
  name: string;
  timeZone: string;
}

export interface AdminUserInfo {
  username: string;
  password: string;
  displayName: string;
}

export interface FirstRunRequest {
  organisation: OrganisationInfo;
  company: CompanyInfo;
  branch: BranchInfo;
  admin: AdminUserInfo;
}

export interface FirstRunResponse {
  organisationId: number;
  companyId: number;
  companyCode: string;
  branchId: number;
  branchCode: string;
  defaultSectionId: number;
  adminUserId: number;
  adminUsername: string;
}

export interface SetupStatus {
  bootstrapped: boolean;
}

@Injectable({ providedIn: 'root' })
export class SetupService {
  private readonly http = inject(HttpClient);

  status(): Observable<SetupStatus> {
    return unwrap(this.http.get<ApiResponse<SetupStatus>>(`${environment.apiUrl}/setup/status`));
  }

  firstRun(payload: FirstRunRequest): Observable<FirstRunResponse> {
    return unwrap(this.http.post<ApiResponse<FirstRunResponse>>(
      `${environment.apiUrl}/setup/first-run`,
      payload
    ));
  }
}
