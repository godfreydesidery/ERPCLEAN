import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { Page } from '../../core/api/page';
import { ItemBranchBalance, ItemMovementRow, StockMove } from './reports.models';

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /**
   * US-RPT-004 — paginated stock card for one item+branch.
   * GET /api/v1/stock-card?itemId=&branchId=&page=&size=
   * Both itemId and branchId are required by the backend.
   */
  stockCard(
    itemId: string,
    branchId: string,
    page = 0,
    size = 100,
  ): Observable<Page<StockMove>> {
    const params = new HttpParams()
      .set('itemId', itemId)
      .set('branchId', branchId)
      .set('page', page)
      .set('size', size);
    return unwrap(
      this.http.get<ApiResponse<Page<StockMove>>>(`${this.base}/stock-card`, { params }),
    );
  }

  /**
   * US-RPT-006 — items with negative on-hand for a branch.
   * GET /api/v1/reports/stock-negative?branchId=
   * branchId is optional on the backend; null omits the param (all branches).
   */
  negativeStock(branchId: string | null): Observable<ItemBranchBalance[]> {
    let params = new HttpParams();
    if (branchId !== null) params = params.set('branchId', branchId);
    return unwrap(
      this.http.get<ApiResponse<ItemBranchBalance[]>>(
        `${this.base}/reports/stock-negative`,
        { params },
      ),
    );
  }

  /**
   * US-RPT-005 — top-N fast movers by total moved qty.
   * GET /api/v1/reports/stock-fast-movers?branchId=&from=&to=&moveTypes=&limit=
   * Spring binds repeated ?moveTypes=X&moveTypes=Y to List<String>.
   */
  fastMovers(
    branchId: string | null,
    from: string | null,
    to: string | null,
    moveTypes: string[] | null,
    limit = 20,
  ): Observable<ItemMovementRow[]> {
    return unwrap(
      this.http.get<ApiResponse<ItemMovementRow[]>>(
        `${this.base}/reports/stock-fast-movers`,
        { params: buildMoversParams(branchId, from, to, moveTypes, limit) },
      ),
    );
  }

  /**
   * US-RPT-005 — bottom-N slow movers (zero-movers included).
   * GET /api/v1/reports/stock-slow-movers?…
   */
  slowMovers(
    branchId: string | null,
    from: string | null,
    to: string | null,
    moveTypes: string[] | null,
    limit = 20,
  ): Observable<ItemMovementRow[]> {
    return unwrap(
      this.http.get<ApiResponse<ItemMovementRow[]>>(
        `${this.base}/reports/stock-slow-movers`,
        { params: buildMoversParams(branchId, from, to, moveTypes, limit) },
      ),
    );
  }
}

/** Build the shared query-param set for fast/slow movers endpoints. */
function buildMoversParams(
  branchId: string | null,
  from: string | null,
  to: string | null,
  moveTypes: string[] | null,
  limit: number,
): HttpParams {
  let params = new HttpParams().set('limit', limit);
  if (branchId !== null) params = params.set('branchId', branchId);
  if (from !== null) params = params.set('from', from);
  if (to !== null) params = params.set('to', to);
  // Spring @RequestParam List<String> binds repeated ?moveTypes= values.
  if (moveTypes && moveTypes.length > 0) {
    for (const mt of moveTypes) {
      params = params.append('moveTypes', mt);
    }
  }
  return params;
}
