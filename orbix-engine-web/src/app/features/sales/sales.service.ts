import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import {
  CreateSalesInvoiceRequest,
  CreateSalesReceiptRequest,
  SalesInvoice,
  SalesReceipt,
  VoidSalesInvoiceRequest
} from './sales.models';

@Injectable({ providedIn: 'root' })
export class SalesService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listInvoices(branchId: number | null): Observable<SalesInvoice[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<SalesInvoice[]>>(
      `${this.base}/sales-invoices`, { params }
    ));
  }

  getInvoice(id: number): Observable<SalesInvoice> {
    return unwrap(this.http.get<ApiResponse<SalesInvoice>>(`${this.base}/sales-invoices/${id}`));
  }

  createInvoice(request: CreateSalesInvoiceRequest): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(`${this.base}/sales-invoices`, request));
  }

  postInvoice(id: number): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(
      `${this.base}/sales-invoices/${id}/post`, {}
    ));
  }

  voidInvoice(id: number, request: VoidSalesInvoiceRequest): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(
      `${this.base}/sales-invoices/${id}/void`, request
    ));
  }

  cancelInvoice(id: number): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(
      `${this.base}/sales-invoices/${id}/cancel`, {}
    ));
  }

  // ---- sales receipts (F4.3) ----------------------------------------------

  listReceipts(branchId: number | null): Observable<SalesReceipt[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<SalesReceipt[]>>(
      `${this.base}/sales-receipts`, { params }
    ));
  }

  getReceipt(id: number): Observable<SalesReceipt> {
    return unwrap(this.http.get<ApiResponse<SalesReceipt>>(`${this.base}/sales-receipts/${id}`));
  }

  createReceipt(request: CreateSalesReceiptRequest): Observable<SalesReceipt> {
    return unwrap(this.http.post<ApiResponse<SalesReceipt>>(`${this.base}/sales-receipts`, request));
  }

  postReceipt(id: number): Observable<SalesReceipt> {
    return unwrap(this.http.post<ApiResponse<SalesReceipt>>(
      `${this.base}/sales-receipts/${id}/post`, {}
    ));
  }

  cancelReceipt(id: number): Observable<SalesReceipt> {
    return unwrap(this.http.post<ApiResponse<SalesReceipt>>(
      `${this.base}/sales-receipts/${id}/cancel`, {}
    ));
  }
}
