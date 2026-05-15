import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import {
  CreateStockCountRequest,
  CreateStockTransferRequest,
  ItemBranchBalance,
  Page,
  PostAdjustmentRequest,
  PostInternalConsumptionRequest,
  ReceiveTransferRequest,
  RecallStockBatchRequest,
  RecordCountsRequest,
  StockBatch,
  StockBatchStatus,
  StockCount,
  StockMove,
  StockTransfer
} from './stock.models';

@Injectable({ providedIn: 'root' })
export class StockService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listBalances(branchId: number): Observable<ItemBranchBalance[]> {
    const params = new HttpParams().set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<ItemBranchBalance[]>>(
      `${this.base}/balances`, { params }
    ));
  }

  stockCard(itemId: number, branchId: number, page: number, size: number): Observable<Page<StockMove>> {
    const params = new HttpParams()
      .set('itemId', itemId).set('branchId', branchId).set('page', page).set('size', size);
    return unwrap(this.http.get<ApiResponse<Page<StockMove>>>(
      `${this.base}/stock-card`, { params }
    ));
  }

  // ---- stock counts ---------------------------------------------------------

  listCounts(): Observable<StockCount[]> {
    return unwrap(this.http.get<ApiResponse<StockCount[]>>(`${this.base}/stock-counts`));
  }

  createCount(request: CreateStockCountRequest): Observable<StockCount> {
    return unwrap(this.http.post<ApiResponse<StockCount>>(`${this.base}/stock-counts`, request));
  }

  startCount(id: number): Observable<StockCount> {
    return unwrap(this.http.post<ApiResponse<StockCount>>(`${this.base}/stock-counts/${id}/start`, {}));
  }

  recordCounts(id: number, request: RecordCountsRequest): Observable<StockCount> {
    return unwrap(this.http.put<ApiResponse<StockCount>>(
      `${this.base}/stock-counts/${id}/counts`, request
    ));
  }

  closeCount(id: number): Observable<StockCount> {
    return unwrap(this.http.post<ApiResponse<StockCount>>(`${this.base}/stock-counts/${id}/close`, {}));
  }

  postCount(id: number): Observable<StockCount> {
    return unwrap(this.http.post<ApiResponse<StockCount>>(`${this.base}/stock-counts/${id}/post`, {}));
  }

  // ---- stock transfers ------------------------------------------------------

  listTransfers(): Observable<StockTransfer[]> {
    return unwrap(this.http.get<ApiResponse<StockTransfer[]>>(`${this.base}/stock-transfers`));
  }

  createTransfer(request: CreateStockTransferRequest): Observable<StockTransfer> {
    return unwrap(this.http.post<ApiResponse<StockTransfer>>(`${this.base}/stock-transfers`, request));
  }

  issueTransfer(id: number): Observable<StockTransfer> {
    return unwrap(this.http.post<ApiResponse<StockTransfer>>(
      `${this.base}/stock-transfers/${id}/issue`, {}
    ));
  }

  receiveTransfer(id: number, request: ReceiveTransferRequest): Observable<StockTransfer> {
    return unwrap(this.http.put<ApiResponse<StockTransfer>>(
      `${this.base}/stock-transfers/${id}/receive`, request
    ));
  }

  closeTransfer(id: number): Observable<StockTransfer> {
    return unwrap(this.http.post<ApiResponse<StockTransfer>>(
      `${this.base}/stock-transfers/${id}/close`, {}
    ));
  }

  // ---- stock batches (F2.4) -------------------------------------------------

  listBatches(filter: { branchId?: number; itemId?: number; status?: StockBatchStatus } = {}):
      Observable<StockBatch[]> {
    let params = new HttpParams();
    if (filter.branchId != null) params = params.set('branchId', filter.branchId);
    if (filter.itemId != null) params = params.set('itemId', filter.itemId);
    if (filter.status) params = params.set('status', filter.status);
    return unwrap(this.http.get<ApiResponse<StockBatch[]>>(
      `${this.base}/stock-batches`, { params }
    ));
  }

  listExpiringSoon(branchId: number | null, daysAhead: number): Observable<StockBatch[]> {
    let params = new HttpParams().set('daysAhead', daysAhead);
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<StockBatch[]>>(
      `${this.base}/stock-batches/expiring-soon`, { params }
    ));
  }

  recallBatch(id: number, request: RecallStockBatchRequest): Observable<StockBatch> {
    return unwrap(this.http.post<ApiResponse<StockBatch>>(
      `${this.base}/stock-batches/${id}/recall`, request
    ));
  }

  // ---- adjustments + internal consumption (F2.5) ----------------------------

  postAdjustment(request: PostAdjustmentRequest): Observable<StockMove> {
    return unwrap(this.http.post<ApiResponse<StockMove>>(`${this.base}/adjustments`, request));
  }

  postInternalConsumption(request: PostInternalConsumptionRequest): Observable<StockMove> {
    return unwrap(this.http.post<ApiResponse<StockMove>>(
      `${this.base}/internal-consumption`, request
    ));
  }
}
