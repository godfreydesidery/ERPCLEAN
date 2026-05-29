import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { Page } from '../../core/api/page';
import { ItemBranchBalance, ItemMovementRow, LaybyAgeingReport, PartyStatement, StockMove } from './reports.models';

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
  /**
   * US-DEBT-005 / US-RPT-007 — AR statement for one customer.
   * GET /api/v1/reports/customer-statement?customerId=&from=&to=
   * Not permission-gated — matches the existing controller convention.
   * @param customerId Numeric party PK (string — Jackson Long-as-string).
   * @param from ISO-8601 date (YYYY-MM-DD). Null → backend defaults to last 30 days.
   * @param to ISO-8601 date. Null → backend defaults to today.
   */
  customerStatement(
    customerId: string,
    from: string | null,
    to: string | null,
  ): Observable<PartyStatement> {
    let params = new HttpParams().set('customerId', customerId);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return unwrap(
      this.http.get<ApiResponse<PartyStatement>>(
        `${this.base}/reports/customer-statement`,
        { params },
      ),
    );
  }

  /**
   * US-DEBT-006 / US-RPT-007 — AP statement for one supplier.
   * GET /api/v1/reports/supplier-statement?supplierId=&from=&to=
   * Not permission-gated.
   * @param supplierId Numeric party PK (string — Jackson Long-as-string).
   */
  supplierStatement(
    supplierId: string,
    from: string | null,
    to: string | null,
  ): Observable<PartyStatement> {
    let params = new HttpParams().set('supplierId', supplierId);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return unwrap(
      this.http.get<ApiResponse<PartyStatement>>(
        `${this.base}/reports/supplier-statement`,
        { params },
      ),
    );
  }

  /**
   * US-RPT-014 — Layby / pre-order ageing report.
   * GET /api/v1/reports/layby-ageing?branchId=&type=&asOf=
   * Gated by ORDER.READ or ORDER.MANAGE on the backend.
   * @param branchId Null → all accessible branches.
   * @param type 'LAYBY' | 'PRE_ORDER' | null → all types.
   * @param asOf ISO-8601 instant string. Null → server defaults to now.
   */
  laybyAgeing(
    branchId: string | null,
    type: 'LAYBY' | 'PRE_ORDER' | null,
    asOf: string | null,
  ): Observable<LaybyAgeingReport> {
    let params = new HttpParams();
    if (branchId) params = params.set('branchId', branchId);
    if (type) params = params.set('type', type);
    if (asOf) params = params.set('asOf', asOf);
    return unwrap(
      this.http.get<ApiResponse<LaybyAgeingReport>>(
        `${this.base}/reports/layby-ageing`,
        { params },
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
