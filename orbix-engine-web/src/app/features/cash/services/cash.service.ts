import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, unwrap } from '../../../core/api/api-response';
import {
  BankDeposit,
  CashAccount,
  CashAdjustment,
  CashBook,
  CashEntry,
  PostBankDepositRequest,
  PostCashAdjustmentRequest
} from '../models/cash.models';

/**
 * Cash spine API client. Slice D — every state-changing route is uid-keyed;
 * read endpoints stay query-keyed because they're enumeration views.
 *
 * Backends:
 *   - {@code CashLedgerController} — cash-book + cash-entries (read-only)
 *   - {@code CashAdjustmentController} — supervisor adjustments + reversal
 *   - {@code BankDepositController} — EOD bank deposits + reversal
 */
@Injectable({ providedIn: 'root' })
export class CashService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  // ---- cash-book ---------------------------------------------------------

  listCashBook(branchId: string | null, businessDate: string): Observable<CashBook[]> {
    let params = new HttpParams().set('businessDate', businessDate);
    if (branchId !== null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<CashBook[]>>(
      `${this.base}/cash-book`, { params }
    ));
  }

  getCashBookByUid(uid: string): Observable<CashBook> {
    return unwrap(this.http.get<ApiResponse<CashBook>>(
      `${this.base}/cash-book/uid/${uid}`
    ));
  }

  // ---- cash-entries ------------------------------------------------------

  listEntries(
    branchId: string | null,
    account: CashAccount | null,
    businessDate: string
  ): Observable<CashEntry[]> {
    let params = new HttpParams().set('businessDate', businessDate);
    if (branchId !== null) params = params.set('branchId', branchId);
    if (account !== null) params = params.set('account', account);
    return unwrap(this.http.get<ApiResponse<CashEntry[]>>(
      `${this.base}/cash-entries`, { params }
    ));
  }

  getEntryByUid(uid: string): Observable<CashEntry> {
    return unwrap(this.http.get<ApiResponse<CashEntry>>(
      `${this.base}/cash-entries/uid/${uid}`
    ));
  }

  // ---- cash-adjustments --------------------------------------------------

  listAdjustments(branchId: string, businessDate: string): Observable<CashAdjustment[]> {
    const params = new HttpParams()
      .set('branchId', branchId)
      .set('businessDate', businessDate);
    return unwrap(this.http.get<ApiResponse<CashAdjustment[]>>(
      `${this.base}/cash-adjustments`, { params }
    ));
  }

  getAdjustmentByUid(uid: string): Observable<CashAdjustment> {
    return unwrap(this.http.get<ApiResponse<CashAdjustment>>(
      `${this.base}/cash-adjustments/uid/${uid}`
    ));
  }

  postAdjustment(request: PostCashAdjustmentRequest): Observable<CashAdjustment> {
    return unwrap(this.http.post<ApiResponse<CashAdjustment>>(
      `${this.base}/cash-adjustments`, request
    ));
  }

  archiveAdjustment(uid: string): Observable<CashAdjustment> {
    return unwrap(this.http.post<ApiResponse<CashAdjustment>>(
      `${this.base}/cash-adjustments/uid/${uid}/archive`, {}
    ));
  }

  // ---- bank-deposits -----------------------------------------------------

  listDeposits(branchId: string, businessDate: string): Observable<BankDeposit[]> {
    const params = new HttpParams()
      .set('branchId', branchId)
      .set('businessDate', businessDate);
    return unwrap(this.http.get<ApiResponse<BankDeposit[]>>(
      `${this.base}/bank-deposits`, { params }
    ));
  }

  getDepositByUid(uid: string): Observable<BankDeposit> {
    return unwrap(this.http.get<ApiResponse<BankDeposit>>(
      `${this.base}/bank-deposits/uid/${uid}`
    ));
  }

  postDeposit(request: PostBankDepositRequest): Observable<BankDeposit> {
    return unwrap(this.http.post<ApiResponse<BankDeposit>>(
      `${this.base}/bank-deposits`, request
    ));
  }

  archiveDeposit(uid: string): Observable<BankDeposit> {
    return unwrap(this.http.post<ApiResponse<BankDeposit>>(
      `${this.base}/bank-deposits/uid/${uid}/archive`, {}
    ));
  }
}
