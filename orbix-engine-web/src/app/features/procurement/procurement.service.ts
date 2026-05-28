import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { Page } from '../../core/api/page';
import {
  ApplyVendorCreditNoteRequest,
  CreateGrnRequest,
  CreateLpoOrderRequest,
  CreateSupplierInvoiceRequest,
  CreateSupplierPaymentRequest,
  CreateVendorReturnRequest,
  Grn,
  IssueVendorCreditNoteRequest,
  LpoOrder,
  SupplierInvoice,
  SupplierPayment,
  SupplierSummary,
  UpdateLpoOrderRequest,
  VendorCreditNote,
  VendorReturn,
} from './procurement.models';
import { SupplierStatement } from '../debt/debt.models';

@Injectable({ providedIn: 'root' })
export class ProcurementService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /**
   * Slice F — list LPOs. {@code status} accepts any
   * {@link import('./procurement.models').LpoOrderStatus} value (e.g.
   * {@code PENDING_APPROVAL} for the dashboard drill-through).
   */
  listLpos(
    branchId: string | null,
    page: number,
    size: number,
    status?: string | null,
  ): Observable<Page<LpoOrder>> {
    let params = branchPageParams(branchId, page, size);
    if (status) params = params.set('status', status);
    return unwrap(this.http.get<ApiResponse<Page<LpoOrder>>>(
      `${this.base}/lpos`, { params }
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

  // ---- Supplier search (typeahead) ------------------------------------------

  /**
   * Typeahead search over suppliers. Returns a page of {@link SupplierSummary}
   * rows. Each row exposes {@code partyUid} — the uid to submit in write
   * payloads — plus {@code code} and {@code name} for display.
   */
  searchSuppliers(q: string, page = 0, size = 20): Observable<Page<SupplierSummary>> {
    const params = new HttpParams()
      .set('q', q)
      .set('page', page)
      .set('size', size);
    return unwrap(this.http.get<ApiResponse<Page<SupplierSummary>>>(
      `${this.base}/suppliers`, { params }
    ));
  }

  // ---- GRN (F3.2) ----------------------------------------------------------

  /**
   * List GRNs. Supports optional {@code supplierId} (Long as string) and
   * {@code status} query params added by the BE in the parallel Slice H.1 task.
   * Falls back to branch-scoped listing when those params are absent so
   * existing call sites are unaffected.
   */
  listGrns(
    branchId: string | null,
    page: number,
    size: number,
    supplierId?: string | null,
    status?: 'POSTED' | 'DRAFT' | 'CANCELLED' | null,
  ): Observable<Page<Grn>> {
    let params = branchPageParams(branchId, page, size);
    if (supplierId != null) params = params.set('supplierId', supplierId);
    if (status != null) params = params.set('status', status);
    return unwrap(this.http.get<ApiResponse<Page<Grn>>>(
      `${this.base}/grns`, { params }
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

  // ---- Slice H.1: vendor returns (US-PROC-008) + vendor credit notes (US-PROC-009) ----

  createVendorReturn(request: CreateVendorReturnRequest): Observable<VendorReturn> {
    return unwrap(this.http.post<ApiResponse<VendorReturn>>(
      `${this.base}/vendor-returns`, request
    ));
  }

  listVendorReturns(
    branchId?: string | null,
    page = 0,
    size = 20,
  ): Observable<Page<VendorReturn>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<Page<VendorReturn>>>(
      `${this.base}/vendor-returns`, { params }
    ));
  }

  getVendorReturn(uid: string): Observable<VendorReturn> {
    return unwrap(this.http.get<ApiResponse<VendorReturn>>(
      `${this.base}/vendor-returns/uid/${uid}`
    ));
  }

  postVendorReturn(uid: string): Observable<VendorReturn> {
    return unwrap(this.http.post<ApiResponse<VendorReturn>>(
      `${this.base}/vendor-returns/uid/${uid}/post`, {}
    ));
  }

  cancelVendorReturn(uid: string): Observable<VendorReturn> {
    return unwrap(this.http.post<ApiResponse<VendorReturn>>(
      `${this.base}/vendor-returns/uid/${uid}/cancel`, {}
    ));
  }

  issueVendorCreditNote(uid: string, req: IssueVendorCreditNoteRequest): Observable<VendorCreditNote> {
    return unwrap(this.http.post<ApiResponse<VendorCreditNote>>(
      `${this.base}/vendor-returns/uid/${uid}/issue-credit-note`, req
    ));
  }

  listVendorCreditNotes(branchId?: string | null): Observable<VendorCreditNote[]> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<VendorCreditNote[]>>(
      `${this.base}/vendor-credit-notes`, { params }
    ));
  }

  applyVendorCreditNote(uid: string, req: ApplyVendorCreditNoteRequest): Observable<VendorCreditNote> {
    return unwrap(this.http.post<ApiResponse<VendorCreditNote>>(
      `${this.base}/vendor-credit-notes/uid/${uid}/apply`, req
    ));
  }

  /**
   * Fetches open supplier invoices for a given supplier via the debt/AP statement endpoint
   * (GET /api/v1/debt/supplier/uid/{uid}) and returns only the openInvoices array.
   * Reuses SupplierStatement from debt.models — same shape as the AR-side customer statement.
   */
  getSupplierOpenInvoices(supplierUid: string): Observable<SupplierStatement['openInvoices']> {
    return unwrap(
      this.http.get<ApiResponse<SupplierStatement>>(`${this.base}/debt/supplier/uid/${supplierUid}`)
    ).pipe(map(stmt => stmt.openInvoices));
  }
}

/** Standard branch-scoped list params (branchId optional + page/size). */
function branchPageParams(branchId: string | null, page: number, size: number): HttpParams {
  let params = new HttpParams().set('page', page).set('size', size);
  if (branchId != null) params = params.set('branchId', branchId);
  return params;
}
