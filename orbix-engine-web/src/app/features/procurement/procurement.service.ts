import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import {
  CreateGrnRequest,
  CreateLpoOrderRequest,
  CreateSupplierInvoiceRequest,
  CreateSupplierPaymentRequest,
  Grn,
  LpoOrder,
  SupplierInvoice,
  SupplierPayment,
  UpdateLpoOrderRequest
} from './procurement.models';

@Injectable({ providedIn: 'root' })
export class ProcurementService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listLpos(branchId: string | null): Observable<LpoOrder[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<LpoOrder[]>>(`${this.base}/lpos`, { params }));
  }

  getLpo(id: string): Observable<LpoOrder> {
    return unwrap(this.http.get<ApiResponse<LpoOrder>>(`${this.base}/lpos/${id}`));
  }

  createLpo(request: CreateLpoOrderRequest): Observable<LpoOrder> {
    return unwrap(this.http.post<ApiResponse<LpoOrder>>(`${this.base}/lpos`, request));
  }

  updateLpo(id: string, request: UpdateLpoOrderRequest): Observable<LpoOrder> {
    return unwrap(this.http.patch<ApiResponse<LpoOrder>>(`${this.base}/lpos/${id}`, request));
  }

  submitLpo(id: string): Observable<LpoOrder> {
    return unwrap(this.http.post<ApiResponse<LpoOrder>>(`${this.base}/lpos/${id}/submit`, {}));
  }

  approveLpo(id: string): Observable<LpoOrder> {
    return unwrap(this.http.post<ApiResponse<LpoOrder>>(`${this.base}/lpos/${id}/approve`, {}));
  }

  cancelLpo(id: string): Observable<LpoOrder> {
    return unwrap(this.http.post<ApiResponse<LpoOrder>>(`${this.base}/lpos/${id}/cancel`, {}));
  }

  // ---- GRN (F3.2) ----------------------------------------------------------

  listGrns(branchId: string | null): Observable<Grn[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<Grn[]>>(`${this.base}/grns`, { params }));
  }

  getGrn(id: string): Observable<Grn> {
    return unwrap(this.http.get<ApiResponse<Grn>>(`${this.base}/grns/${id}`));
  }

  createGrn(request: CreateGrnRequest): Observable<Grn> {
    return unwrap(this.http.post<ApiResponse<Grn>>(`${this.base}/grns`, request));
  }

  postGrn(id: string): Observable<Grn> {
    return unwrap(this.http.post<ApiResponse<Grn>>(`${this.base}/grns/${id}/post`, {}));
  }

  cancelGrn(id: string): Observable<Grn> {
    return unwrap(this.http.post<ApiResponse<Grn>>(`${this.base}/grns/${id}/cancel`, {}));
  }

  // ---- supplier invoices (F3.3) -------------------------------------------

  listSupplierInvoices(branchId: string | null): Observable<SupplierInvoice[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<SupplierInvoice[]>>(
      `${this.base}/supplier-invoices`, { params }
    ));
  }

  getSupplierInvoice(id: string): Observable<SupplierInvoice> {
    return unwrap(this.http.get<ApiResponse<SupplierInvoice>>(
      `${this.base}/supplier-invoices/${id}`
    ));
  }

  createSupplierInvoice(request: CreateSupplierInvoiceRequest): Observable<SupplierInvoice> {
    return unwrap(this.http.post<ApiResponse<SupplierInvoice>>(
      `${this.base}/supplier-invoices`, request
    ));
  }

  postSupplierInvoice(id: string): Observable<SupplierInvoice> {
    return unwrap(this.http.post<ApiResponse<SupplierInvoice>>(
      `${this.base}/supplier-invoices/${id}/post`, {}
    ));
  }

  cancelSupplierInvoice(id: string): Observable<SupplierInvoice> {
    return unwrap(this.http.post<ApiResponse<SupplierInvoice>>(
      `${this.base}/supplier-invoices/${id}/cancel`, {}
    ));
  }

  // ---- supplier payments (F3.4) -------------------------------------------

  listSupplierPayments(branchId: string | null): Observable<SupplierPayment[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<SupplierPayment[]>>(
      `${this.base}/supplier-payments`, { params }
    ));
  }

  getSupplierPayment(id: string): Observable<SupplierPayment> {
    return unwrap(this.http.get<ApiResponse<SupplierPayment>>(
      `${this.base}/supplier-payments/${id}`
    ));
  }

  createSupplierPayment(request: CreateSupplierPaymentRequest): Observable<SupplierPayment> {
    return unwrap(this.http.post<ApiResponse<SupplierPayment>>(
      `${this.base}/supplier-payments`, request
    ));
  }

  postSupplierPayment(id: string): Observable<SupplierPayment> {
    return unwrap(this.http.post<ApiResponse<SupplierPayment>>(
      `${this.base}/supplier-payments/${id}/post`, {}
    ));
  }

  cancelSupplierPayment(id: string): Observable<SupplierPayment> {
    return unwrap(this.http.post<ApiResponse<SupplierPayment>>(
      `${this.base}/supplier-payments/${id}/cancel`, {}
    ));
  }
}
