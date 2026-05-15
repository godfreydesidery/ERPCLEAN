import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import {
  CreateGrnRequest,
  CreateLpoOrderRequest,
  CreateSupplierInvoiceRequest,
  Grn,
  LpoOrder,
  SupplierInvoice,
  UpdateLpoOrderRequest
} from './procurement.models';

@Injectable({ providedIn: 'root' })
export class ProcurementService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listLpos(branchId: number | null): Observable<LpoOrder[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<LpoOrder[]>>(`${this.base}/lpos`, { params }));
  }

  getLpo(id: number): Observable<LpoOrder> {
    return unwrap(this.http.get<ApiResponse<LpoOrder>>(`${this.base}/lpos/${id}`));
  }

  createLpo(request: CreateLpoOrderRequest): Observable<LpoOrder> {
    return unwrap(this.http.post<ApiResponse<LpoOrder>>(`${this.base}/lpos`, request));
  }

  updateLpo(id: number, request: UpdateLpoOrderRequest): Observable<LpoOrder> {
    return unwrap(this.http.patch<ApiResponse<LpoOrder>>(`${this.base}/lpos/${id}`, request));
  }

  submitLpo(id: number): Observable<LpoOrder> {
    return unwrap(this.http.post<ApiResponse<LpoOrder>>(`${this.base}/lpos/${id}/submit`, {}));
  }

  approveLpo(id: number): Observable<LpoOrder> {
    return unwrap(this.http.post<ApiResponse<LpoOrder>>(`${this.base}/lpos/${id}/approve`, {}));
  }

  cancelLpo(id: number): Observable<LpoOrder> {
    return unwrap(this.http.post<ApiResponse<LpoOrder>>(`${this.base}/lpos/${id}/cancel`, {}));
  }

  // ---- GRN (F3.2) ----------------------------------------------------------

  listGrns(branchId: number | null): Observable<Grn[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<Grn[]>>(`${this.base}/grns`, { params }));
  }

  getGrn(id: number): Observable<Grn> {
    return unwrap(this.http.get<ApiResponse<Grn>>(`${this.base}/grns/${id}`));
  }

  createGrn(request: CreateGrnRequest): Observable<Grn> {
    return unwrap(this.http.post<ApiResponse<Grn>>(`${this.base}/grns`, request));
  }

  postGrn(id: number): Observable<Grn> {
    return unwrap(this.http.post<ApiResponse<Grn>>(`${this.base}/grns/${id}/post`, {}));
  }

  cancelGrn(id: number): Observable<Grn> {
    return unwrap(this.http.post<ApiResponse<Grn>>(`${this.base}/grns/${id}/cancel`, {}));
  }

  // ---- supplier invoices (F3.3) -------------------------------------------

  listSupplierInvoices(branchId: number | null): Observable<SupplierInvoice[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<SupplierInvoice[]>>(
      `${this.base}/supplier-invoices`, { params }
    ));
  }

  getSupplierInvoice(id: number): Observable<SupplierInvoice> {
    return unwrap(this.http.get<ApiResponse<SupplierInvoice>>(
      `${this.base}/supplier-invoices/${id}`
    ));
  }

  createSupplierInvoice(request: CreateSupplierInvoiceRequest): Observable<SupplierInvoice> {
    return unwrap(this.http.post<ApiResponse<SupplierInvoice>>(
      `${this.base}/supplier-invoices`, request
    ));
  }

  postSupplierInvoice(id: number): Observable<SupplierInvoice> {
    return unwrap(this.http.post<ApiResponse<SupplierInvoice>>(
      `${this.base}/supplier-invoices/${id}/post`, {}
    ));
  }

  cancelSupplierInvoice(id: number): Observable<SupplierInvoice> {
    return unwrap(this.http.post<ApiResponse<SupplierInvoice>>(
      `${this.base}/supplier-invoices/${id}/cancel`, {}
    ));
  }
}
