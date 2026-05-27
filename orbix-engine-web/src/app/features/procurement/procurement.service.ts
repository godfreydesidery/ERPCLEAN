import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { Page } from '../../core/api/page';
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

  listLpos(branchId: string | null, page: number, size: number): Observable<Page<LpoOrder>> {
    return unwrap(this.http.get<ApiResponse<Page<LpoOrder>>>(
      `${this.base}/lpos`, { params: branchPageParams(branchId, page, size) }
    ));
  }

  getLpo(uid: string): Observable<LpoOrder> {
    return unwrap(this.http.get<ApiResponse<LpoOrder>>(`${this.base}/lpos/uid/${uid}`));
  }

  createLpo(request: CreateLpoOrderRequest): Observable<LpoOrder> {
    return unwrap(this.http.post<ApiResponse<LpoOrder>>(`${this.base}/lpos`, request));
  }

  updateLpo(uid: string, request: UpdateLpoOrderRequest): Observable<LpoOrder> {
    return unwrap(this.http.patch<ApiResponse<LpoOrder>>(`${this.base}/lpos/uid/${uid}`, request));
  }

  submitLpo(uid: string): Observable<LpoOrder> {
    return unwrap(this.http.post<ApiResponse<LpoOrder>>(`${this.base}/lpos/uid/${uid}/submit`, {}));
  }

  approveLpo(uid: string): Observable<LpoOrder> {
    return unwrap(this.http.post<ApiResponse<LpoOrder>>(`${this.base}/lpos/uid/${uid}/approve`, {}));
  }

  cancelLpo(uid: string, reason?: string | null): Observable<LpoOrder> {
    const body = reason && reason.trim().length > 0 ? { reason: reason.trim() } : {};
    return unwrap(this.http.post<ApiResponse<LpoOrder>>(`${this.base}/lpos/uid/${uid}/cancel`, body));
  }

  // ---- GRN (F3.2) ----------------------------------------------------------

  listGrns(branchId: string | null, page: number, size: number): Observable<Page<Grn>> {
    return unwrap(this.http.get<ApiResponse<Page<Grn>>>(
      `${this.base}/grns`, { params: branchPageParams(branchId, page, size) }
    ));
  }

  getGrn(uid: string): Observable<Grn> {
    return unwrap(this.http.get<ApiResponse<Grn>>(`${this.base}/grns/uid/${uid}`));
  }

  createGrn(request: CreateGrnRequest): Observable<Grn> {
    return unwrap(this.http.post<ApiResponse<Grn>>(`${this.base}/grns`, request));
  }

  postGrn(uid: string): Observable<Grn> {
    return unwrap(this.http.post<ApiResponse<Grn>>(`${this.base}/grns/uid/${uid}/post`, {}));
  }

  cancelGrn(uid: string, reason?: string | null): Observable<Grn> {
    const body = reason && reason.trim().length > 0 ? { reason: reason.trim() } : {};
    return unwrap(this.http.post<ApiResponse<Grn>>(`${this.base}/grns/uid/${uid}/cancel`, body));
  }

  /** Cancel a POSTED GRN — backend writes a compensating stock_move and emits GrnCancelled.v1. */
  cancelPostedGrn(uid: string, reason: string): Observable<Grn> {
    return unwrap(this.http.post<ApiResponse<Grn>>(
      `${this.base}/grns/uid/${uid}/cancel-posted`,
      { reason: reason.trim() }
    ));
  }

  // ---- supplier invoices (F3.3) -------------------------------------------

  listSupplierInvoices(branchId: string | null, page: number, size: number): Observable<Page<SupplierInvoice>> {
    return unwrap(this.http.get<ApiResponse<Page<SupplierInvoice>>>(
      `${this.base}/supplier-invoices`, { params: branchPageParams(branchId, page, size) }
    ));
  }

  getSupplierInvoice(uid: string): Observable<SupplierInvoice> {
    return unwrap(this.http.get<ApiResponse<SupplierInvoice>>(
      `${this.base}/supplier-invoices/uid/${uid}`
    ));
  }

  createSupplierInvoice(request: CreateSupplierInvoiceRequest): Observable<SupplierInvoice> {
    return unwrap(this.http.post<ApiResponse<SupplierInvoice>>(
      `${this.base}/supplier-invoices`, request
    ));
  }

  postSupplierInvoice(uid: string): Observable<SupplierInvoice> {
    return unwrap(this.http.post<ApiResponse<SupplierInvoice>>(
      `${this.base}/supplier-invoices/uid/${uid}/post`, {}
    ));
  }

  cancelSupplierInvoice(uid: string): Observable<SupplierInvoice> {
    return unwrap(this.http.post<ApiResponse<SupplierInvoice>>(
      `${this.base}/supplier-invoices/uid/${uid}/cancel`, {}
    ));
  }

  // ---- supplier payments (F3.4) -------------------------------------------

  listSupplierPayments(branchId: string | null, page: number, size: number): Observable<Page<SupplierPayment>> {
    return unwrap(this.http.get<ApiResponse<Page<SupplierPayment>>>(
      `${this.base}/supplier-payments`, { params: branchPageParams(branchId, page, size) }
    ));
  }

  getSupplierPayment(uid: string): Observable<SupplierPayment> {
    return unwrap(this.http.get<ApiResponse<SupplierPayment>>(
      `${this.base}/supplier-payments/uid/${uid}`
    ));
  }

  createSupplierPayment(request: CreateSupplierPaymentRequest): Observable<SupplierPayment> {
    return unwrap(this.http.post<ApiResponse<SupplierPayment>>(
      `${this.base}/supplier-payments`, request
    ));
  }

  postSupplierPayment(uid: string): Observable<SupplierPayment> {
    return unwrap(this.http.post<ApiResponse<SupplierPayment>>(
      `${this.base}/supplier-payments/uid/${uid}/post`, {}
    ));
  }

  cancelSupplierPayment(uid: string): Observable<SupplierPayment> {
    return unwrap(this.http.post<ApiResponse<SupplierPayment>>(
      `${this.base}/supplier-payments/uid/${uid}/cancel`, {}
    ));
  }
}

/** Standard branch-scoped list params (branchId optional + page/size). */
function branchPageParams(branchId: string | null, page: number, size: number): HttpParams {
  let params = new HttpParams().set('page', page).set('size', size);
  if (branchId != null) params = params.set('branchId', branchId);
  return params;
}
