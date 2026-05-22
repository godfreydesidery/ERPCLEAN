import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
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

  listInvoices(branchId: string | null): Observable<SalesInvoice[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<SalesInvoice[]>>(
      `${this.base}/sales-invoices`, { params }
    ));
  }

  getInvoice(id: string): Observable<SalesInvoice> {
    return unwrap(this.http.get<ApiResponse<SalesInvoice>>(`${this.base}/sales-invoices/${id}`));
  }

  createInvoice(request: CreateSalesInvoiceRequest): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(`${this.base}/sales-invoices`, request));
  }

  postInvoice(id: string): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(
      `${this.base}/sales-invoices/${id}/post`, {}
    ));
  }

  voidInvoice(id: string, request: VoidSalesInvoiceRequest): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(
      `${this.base}/sales-invoices/${id}/void`, request
    ));
  }

  cancelInvoice(id: string): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(
      `${this.base}/sales-invoices/${id}/cancel`, {}
    ));
  }

  // ---- sales receipts (F4.3) ----------------------------------------------

  listReceipts(branchId: string | null): Observable<SalesReceipt[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<SalesReceipt[]>>(
      `${this.base}/sales-receipts`, { params }
    ));
  }

  getReceipt(id: string): Observable<SalesReceipt> {
    return unwrap(this.http.get<ApiResponse<SalesReceipt>>(`${this.base}/sales-receipts/${id}`));
  }

  createReceipt(request: CreateSalesReceiptRequest): Observable<SalesReceipt> {
    return unwrap(this.http.post<ApiResponse<SalesReceipt>>(`${this.base}/sales-receipts`, request));
  }

  postReceipt(id: string): Observable<SalesReceipt> {
    return unwrap(this.http.post<ApiResponse<SalesReceipt>>(
      `${this.base}/sales-receipts/${id}/post`, {}
    ));
  }

  cancelReceipt(id: string): Observable<SalesReceipt> {
    return unwrap(this.http.post<ApiResponse<SalesReceipt>>(
      `${this.base}/sales-receipts/${id}/cancel`, {}
    ));
  }

  // ---- customer returns + credit notes (F4.4) -----------------------------

  listReturns(branchId: string | null): Observable<CustomerReturn[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<CustomerReturn[]>>(
      `${this.base}/customer-returns`, { params }
    ));
  }

  getReturn(id: string): Observable<CustomerReturn> {
    return unwrap(this.http.get<ApiResponse<CustomerReturn>>(
      `${this.base}/customer-returns/${id}`
    ));
  }

  createReturn(request: CreateCustomerReturnRequest): Observable<CustomerReturn> {
    return unwrap(this.http.post<ApiResponse<CustomerReturn>>(
      `${this.base}/customer-returns`, request
    ));
  }

  postReturn(id: string): Observable<CustomerReturn> {
    return unwrap(this.http.post<ApiResponse<CustomerReturn>>(
      `${this.base}/customer-returns/${id}/post`, {}
    ));
  }

  cancelReturn(id: string): Observable<CustomerReturn> {
    return unwrap(this.http.post<ApiResponse<CustomerReturn>>(
      `${this.base}/customer-returns/${id}/cancel`, {}
    ));
  }

  issueCreditNote(returnId: string, request: IssueCreditNoteRequest): Observable<CustomerCreditNote> {
    return unwrap(this.http.post<ApiResponse<CustomerCreditNote>>(
      `${this.base}/customer-returns/${returnId}/issue-credit-note`, request
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

  getPackingList(id: string): Observable<PackingList> {
    return unwrap(this.http.get<ApiResponse<PackingList>>(`${this.base}/packing-lists/${id}`));
  }

  createPackingList(request: CreatePackingListRequest): Observable<PackingList> {
    return unwrap(this.http.post<ApiResponse<PackingList>>(`${this.base}/packing-lists`, request));
  }

  dispatchPackingList(id: string): Observable<PackingList> {
    return unwrap(this.http.post<ApiResponse<PackingList>>(
      `${this.base}/packing-lists/${id}/dispatch`, {}
    ));
  }

  deliverPackingList(id: string): Observable<PackingList> {
    return unwrap(this.http.post<ApiResponse<PackingList>>(
      `${this.base}/packing-lists/${id}/deliver`, {}
    ));
  }

  cancelPackingList(id: string): Observable<PackingList> {
    return unwrap(this.http.post<ApiResponse<PackingList>>(
      `${this.base}/packing-lists/${id}/cancel`, {}
    ));
  }
}
