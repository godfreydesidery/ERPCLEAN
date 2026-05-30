import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../api/api-response';
import { Page } from '../api/page';

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

/** Mirrors BranchResponseDto fields used by pickers. */
export interface BranchSummary {
  id: string;
  uid: string;
  code: string;
  name: string;
  type: string;
  isDefault: boolean;
  status: string;
}

/** Mirrors PriceListResponseDto fields used by pickers. */
export interface PriceListSummary {
  id: string;
  uid: string;
  code: string;
  name: string;
  currencyCode: string;
  isDefault: boolean;
  status: string;
}

/** Mirrors BranchSectionResponseDto. */
export interface SectionSummary {
  id: string;
  uid: string;
  branchId: string;
  code: string;
  name: string;
  type: string;
  status: string;
}

/** Lightweight row from GET /api/v1/stock-batches?itemId=. */
export interface StockBatchSummary {
  id: string;
  uid: string;
  itemId: string;
  batchNo: string;
  expiryAt: string | null;
  qtyOnHand: number;
  status: string;
}

/** Row from GET /api/v1/users/lookup?q=. */
export interface UserLookupRow {
  id: string;
  uid: string;
  displayName: string;
  username: string;
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

/**
 * Cross-cutting lookup service for small reference lists used by pickers.
 * All methods return unwrapped Observables (interceptor already strips the
 * ApiResponse envelope, but we do it here explicitly for clarity and
 * testability — same pattern as ProcurementService).
 */
@Injectable({ providedIn: 'root' })
export class CoreLookupService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** Returns all branches accessible to the current user (small list, no paging). */
  listBranches(): Observable<BranchSummary[]> {
    return unwrap(
      this.http.get<ApiResponse<BranchSummary[]>>(`${this.base}/branches`)
    );
  }

  /** Returns all price lists (small list, no paging). */
  listPriceLists(): Observable<PriceListSummary[]> {
    return unwrap(
      this.http.get<ApiResponse<PriceListSummary[]>>(`${this.base}/price-lists`)
    );
  }

  /** Returns all sections for the given branch uid. */
  listSections(branchUid: string): Observable<SectionSummary[]> {
    return unwrap(
      this.http.get<ApiResponse<SectionSummary[]>>(
        `${this.base}/branches/uid/${branchUid}/sections`
      )
    );
  }

  /**
   * Returns stock batches for the given item id (string-serialised Long).
   * Page size defaults to 50 — batches per item are bounded.
   */
  listStockBatches(itemId: string, size = 50): Observable<Page<StockBatchSummary>> {
    const params = new HttpParams()
      .set('itemId', itemId)
      .set('page', 0)
      .set('size', size);
    return unwrap(
      this.http.get<ApiResponse<Page<StockBatchSummary>>>(
        `${this.base}/stock-batches`, { params }
      )
    );
  }

  /**
   * Typeahead search over users. Hits GET /api/v1/users/lookup?q=&size=.
   * Returns a plain list (not paged) because the endpoint is a lookup, not a list.
   */
  lookupUsers(q: string, size = 20): Observable<UserLookupRow[]> {
    const params = new HttpParams()
      .set('q', q.trim())
      .set('size', size);
    return unwrap(
      this.http.get<ApiResponse<UserLookupRow[]>>(
        `${this.base}/users/lookup`, { params }
      )
    );
  }
}
