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

  listBalances(branchId: string): Observable<ItemBranchBalance[]> {
    const params = new HttpParams().set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<ItemBranchBalance[]>>(
      `${this.base}/balances`, { params }
    ));
  }

  stockCard(itemId: string, branchId: string, page: number, size: number): Observable<Page<StockMove>> {
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

  getCount(uid: string): Observable<StockCount> {
    return unwrap(this.http.get<ApiResponse<StockCount>>(`${this.base}/stock-counts/uid/${uid}`));
  }

  startCount(uid: string): Observable<StockCount> {
    return unwrap(this.http.post<ApiResponse<StockCount>>(`${this.base}/stock-counts/uid/${uid}/start`, {}));
  }

  recordCounts(uid: string, request: RecordCountsRequest): Observable<StockCount> {
    return unwrap(this.http.put<ApiResponse<StockCount>>(
      `${this.base}/stock-counts/uid/${uid}/counts`, request
    ));
  }

  closeCount(uid: string): Observable<StockCount> {
    return unwrap(this.http.post<ApiResponse<StockCount>>(`${this.base}/stock-counts/uid/${uid}/close`, {}));
  }

  postCount(uid: string): Observable<StockCount> {
    return unwrap(this.http.post<ApiResponse<StockCount>>(`${this.base}/stock-counts/uid/${uid}/post`, {}));
  }

  // ---- stock transfers ------------------------------------------------------

  listTransfers(): Observable<StockTransfer[]> {
    return unwrap(this.http.get<ApiResponse<StockTransfer[]>>(`${this.base}/stock-transfers`));
  }

  createTransfer(request: CreateStockTransferRequest): Observable<StockTransfer> {
    return unwrap(this.http.post<ApiResponse<StockTransfer>>(`${this.base}/stock-transfers`, request));
  }

  getTransfer(uid: string): Observable<StockTransfer> {
    return unwrap(this.http.get<ApiResponse<StockTransfer>>(`${this.base}/stock-transfers/uid/${uid}`));
  }

  issueTransfer(uid: string): Observable<StockTransfer> {
    return unwrap(this.http.post<ApiResponse<StockTransfer>>(
      `${this.base}/stock-transfers/uid/${uid}/issue`, {}
    ));
  }

  receiveTransfer(uid: string, request: ReceiveTransferRequest): Observable<StockTransfer> {
    return unwrap(this.http.put<ApiResponse<StockTransfer>>(
      `${this.base}/stock-transfers/uid/${uid}/receive`, request
    ));
  }

  closeTransfer(uid: string): Observable<StockTransfer> {
    return unwrap(this.http.post<ApiResponse<StockTransfer>>(
      `${this.base}/stock-transfers/uid/${uid}/close`, {}
    ));
  }

  // ---- stock batches (F2.4) -------------------------------------------------

  listBatches(
    filter: { branchId?: string; itemId?: string; status?: StockBatchStatus } = {},
    page = 0, size = 20
  ): Observable<Page<StockBatch>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filter.branchId != null) params = params.set('branchId', filter.branchId);
    if (filter.itemId != null) params = params.set('itemId', filter.itemId);
    if (filter.status) params = params.set('status', filter.status);
    return unwrap(this.http.get<ApiResponse<Page<StockBatch>>>(
      `${this.base}/stock-batches`, { params }
    ));
  }

  listExpiringSoon(branchId: string | null, daysAhead: number): Observable<StockBatch[]> {
    let params = new HttpParams().set('daysAhead', daysAhead);
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<StockBatch[]>>(
      `${this.base}/stock-batches/expiring-soon`, { params }
    ));
  }

  getBatch(uid: string): Observable<StockBatch> {
    return unwrap(this.http.get<ApiResponse<StockBatch>>(`${this.base}/stock-batches/uid/${uid}`));
  }

  recallBatch(uid: string, request: RecallStockBatchRequest): Observable<StockBatch> {
    return unwrap(this.http.post<ApiResponse<StockBatch>>(
      `${this.base}/stock-batches/uid/${uid}/recall`, request
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
