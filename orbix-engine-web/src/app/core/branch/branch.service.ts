import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../api/api-response';

/** A branch the current user may switch into (mirrors backend AccessibleBranchDto). */
export interface AccessibleBranch {
  id: number;
  code: string;
  name: string;
  type: string;
}

/** localStorage key — read by AuthInterceptor to stamp the X-Branch-Id header. */
export const ACTIVE_BRANCH_KEY = 'orbix.activeBranchId';

@Injectable({ providedIn: 'root' })
export class BranchService {
  private readonly http = inject(HttpClient);

  private readonly _activeBranchId = signal<number | null>(readStoredBranchId());
  readonly activeBranchId = this._activeBranchId.asReadonly();

  listBranches(): Observable<AccessibleBranch[]> {
    return unwrap(this.http.get<ApiResponse<AccessibleBranch[]>>(
      `${environment.apiUrl}/session/branches`
    ));
  }

  setActiveBranch(branchId: number): Observable<void> {
    return this.http.put(`${environment.apiUrl}/session/active-branch`, { branchId }).pipe(
      map(() => void 0),
      tap(() => this.applyActiveBranch(branchId))
    );
  }

  applyActiveBranch(branchId: number): void {
    localStorage.setItem(ACTIVE_BRANCH_KEY, String(branchId));
    this._activeBranchId.set(branchId);
  }

  clear(): void {
    localStorage.removeItem(ACTIVE_BRANCH_KEY);
    this._activeBranchId.set(null);
  }
}

function readStoredBranchId(): number | null {
  const raw = localStorage.getItem(ACTIVE_BRANCH_KEY);
  if (!raw) return null;
  const id = Number(raw);
  return Number.isFinite(id) ? id : null;
}
