import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { Page } from '../../core/api/page';
import {
  CreateCustomerReturnRequest,
  CreatePackingListRequest,
  CreateSalesInvoiceRequest,
  CreateSalesReceiptRequest,
  CustomerCreditNote,
  CustomerReturn,
  IssueCreditNoteRequest,
  PackingList,
  SalesInvoice,
  SalesReceipt,
  VoidSalesInvoiceRequest
} from './sales.models';

@Injectable({ providedIn: 'root' })
export class SalesService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listInvoices(branchId: string | null, page: number, size: number): Observable<Page<SalesInvoice>> {
    return unwrap(this.http.get<ApiResponse<Page<SalesInvoice>>>(
      `${this.base}/sales-invoices`, { params: branchPageParams(branchId, page, size) }
    ));
  }

  getInvoice(uid: string): Observable<SalesInvoice> {
    return unwrap(this.http.get<ApiResponse<SalesInvoice>>(`${this.base}/sales-invoices/uid/${uid}`));
  }

  createInvoice(request: CreateSalesInvoiceRequest): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(`${this.base}/sales-invoices`, request));
  }

  postInvoice(uid: string): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(
      `${this.base}/sales-invoices/uid/${uid}/post`, {}
    ));
  }

  voidInvoice(uid: string, request: VoidSalesInvoiceRequest): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(
      `${this.base}/sales-invoices/uid/${uid}/void`, request
    ));
  }

  cancelInvoice(uid: string): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(
      `${this.base}/sales-invoices/uid/${uid}/cancel`, {}
    ));
  }

  // ---- sales receipts (F4.3) ----------------------------------------------

  listReceipts(branchId: string | null, page: number, size: number): Observable<Page<SalesReceipt>> {
    return unwrap(this.http.get<ApiResponse<Page<SalesReceipt>>>(
      `${this.base}/sales-receipts`, { params: branchPageParams(branchId, page, size) }
    ));
  }

  getReceipt(uid: string): Observable<SalesReceipt> {
    return unwrap(this.http.get<ApiResponse<SalesReceipt>>(`${this.base}/sales-receipts/uid/${uid}`));
  }

  createReceipt(request: CreateSalesReceiptRequest): Observable<SalesReceipt> {
    return unwrap(this.http.post<ApiResponse<SalesReceipt>>(`${this.base}/sales-receipts`, request));
  }

  postReceipt(uid: string): Observable<SalesReceipt> {
    return unwrap(this.http.post<ApiResponse<SalesReceipt>>(
      `${this.base}/sales-receipts/uid/${uid}/post`, {}
    ));
  }

  cancelReceipt(uid: string): Observable<SalesReceipt> {
    return unwrap(this.http.post<ApiResponse<SalesReceipt>>(
      `${this.base}/sales-receipts/uid/${uid}/cancel`, {}
    ));
  }

  // ---- customer returns + credit notes (F4.4) -----------------------------

  listReturns(branchId: string | null, page: number, size: number): Observable<Page<CustomerReturn>> {
    return unwrap(this.http.get<ApiResponse<Page<CustomerReturn>>>(
      `${this.base}/customer-returns`, { params: branchPageParams(branchId, page, size) }
    ));
  }

  getReturn(uid: string): Observable<CustomerReturn> {
    return unwrap(this.http.get<ApiResponse<CustomerReturn>>(
      `${this.base}/customer-returns/uid/${uid}`
    ));
  }

  createReturn(request: CreateCustomerReturnRequest): Observable<CustomerReturn> {
    return unwrap(this.http.post<ApiResponse<CustomerReturn>>(
      `${this.base}/customer-returns`, request
    ));
  }

  postReturn(uid: string): Observable<CustomerReturn> {
    return unwrap(this.http.post<ApiResponse<CustomerReturn>>(
      `${this.base}/customer-returns/uid/${uid}/post`, {}
    ));
  }

  cancelReturn(uid: string): Observable<CustomerReturn> {
    return unwrap(this.http.post<ApiResponse<CustomerReturn>>(
      `${this.base}/customer-returns/uid/${uid}/cancel`, {}
    ));
  }

  issueCreditNote(uid: string, request: IssueCreditNoteRequest): Observable<CustomerCreditNote> {
    return unwrap(this.http.post<ApiResponse<CustomerCreditNote>>(
      `${this.base}/customer-returns/uid/${uid}/issue-credit-note`, request
    ));
  }

  listCreditNotes(branchId: string | null): Observable<CustomerCreditNote[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<CustomerCreditNote[]>>(
      `${this.base}/customer-credit-notes`, { params }
    ));
  }

  // ---- packing lists (F4.5) -----------------------------------------------

  listPackingLists(branchId: string | null): Observable<PackingList[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<PackingList[]>>(
      `${this.base}/packing-lists`, { params }
    ));
  }

  getPackingList(uid: string): Observable<PackingList> {
    return unwrap(this.http.get<ApiResponse<PackingList>>(`${this.base}/packing-lists/uid/${uid}`));
  }

  createPackingList(request: CreatePackingListRequest): Observable<PackingList> {
    return unwrap(this.http.post<ApiResponse<PackingList>>(`${this.base}/packing-lists`, request));
  }

  dispatchPackingList(uid: string): Observable<PackingList> {
    return unwrap(this.http.post<ApiResponse<PackingList>>(
      `${this.base}/packing-lists/uid/${uid}/dispatch`, {}
    ));
  }

  deliverPackingList(uid: string): Observable<PackingList> {
    return unwrap(this.http.post<ApiResponse<PackingList>>(
      `${this.base}/packing-lists/uid/${uid}/deliver`, {}
    ));
  }

  cancelPackingList(uid: string): Observable<PackingList> {
    return unwrap(this.http.post<ApiResponse<PackingList>>(
      `${this.base}/packing-lists/uid/${uid}/cancel`, {}
    ));
  }
}

/** Standard branch-scoped list params (branchId optional + page/size). */
function branchPageParams(branchId: string | null, page: number, size: number): HttpParams {
  let params = new HttpParams().set('page', page).set('size', size);
  if (branchId != null) params = params.set('branchId', branchId);
  return params;
}
