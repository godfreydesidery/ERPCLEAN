import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { Page } from '../../core/api/page';
import {
  ApplyCreditNoteRequest,
  ArSummary,
  CreateCustomerReturnRequest,
  CreatePackingListRequest,
  CreateSalesInvoiceRequest,
  CreateSalesReceiptRequest,
  CustomerCreditNote,
  CustomerReturn,
  IssueCreditNoteRequest,
  PackingList,
  ReprintReason,
  SalesInvoice,
  SalesReceipt,
  VoidSalesInvoiceRequest
} from './sales.models';
import { map } from 'rxjs/operators';
import { OpenInvoiceRow } from '../debt/debt.models';

@Injectable({ providedIn: 'root' })
export class SalesService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /**
   * Slice F — list sales invoices. {@code status} accepts the bucket aliases
   * {@code OPEN} / {@code OVERDUE} (matches the AR-summary tile semantics)
   * or any raw {@code SalesInvoiceStatus} value. {@code sort} is passed
   * through to the backend — the dashboard's AR-outstanding drill-through
   * sends {@code sort=outstanding,desc} (the backend honours it per Plan §6.2).
   */
  listInvoices(
    branchId: string | null,
    page: number,
    size: number,
    status?: string | null,
    sort?: string | null,
  ): Observable<Page<SalesInvoice>> {
    let params = branchPageParams(branchId, page, size);
    if (status) params = params.set('status', status);
    if (sort) params = params.set('sort', sort);
    return unwrap(this.http.get<ApiResponse<Page<SalesInvoice>>>(
      `${this.base}/sales-invoices`, { params }
    ));
  }

  getInvoice(uid: string): Observable<SalesInvoice> {
    return unwrap(this.http.get<ApiResponse<SalesInvoice>>(`${this.base}/sales-invoices/uid/${uid}`));
  }

  createInvoice(request: CreateSalesInvoiceRequest): Observable<SalesInvoice> {
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(`${this.base}/sales-invoices`, request));
  }

  /**
   * Post a DRAFT invoice. When {@code overrideReason} is supplied AND the caller
   * holds {@code SALES_INVOICE.OVERRIDE_CREDIT}, the backend bypasses the
   * credit-limit gate and persists the reason on the invoice for audit.
   */
  postInvoice(uid: string, overrideReason?: string): Observable<SalesInvoice> {
    const body = overrideReason && overrideReason.trim().length > 0
      ? { overrideReason: overrideReason.trim() }
      : {};
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(
      `${this.base}/sales-invoices/uid/${uid}/post`, body
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

  /**
   * Log a reprint of a POSTED invoice. Increments {@code reprintCount} on the
   * aggregate and emits {@code SalesInvoiceReprinted.v1} server-side. Gated by
   * {@code SALES_INVOICE.REPRINT} on the backend.
   */
  reprintInvoice(uid: string, reason: ReprintReason, notes?: string | null): Observable<SalesInvoice> {
    const body: { reason: ReprintReason; notes?: string } = { reason };
    if (notes && notes.trim().length > 0) body.notes = notes.trim();
    return unwrap(this.http.post<ApiResponse<SalesInvoice>>(
      `${this.base}/sales-invoices/uid/${uid}/reprint`, body
    ));
  }

  /**
   * Aggregate AR snapshot for the dashboard tiles. {@code branchId} null
   * returns a company-wide rollup. Gated by {@code SALES.REPORT.AR_SUMMARY}.
   */
  getArSummary(branchId: string | null): Observable<ArSummary> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<ArSummary>>(
      `${this.base}/sales/reports/ar-summary`, { params }
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

  /**
   * Slice H — apply a credit note (by uid) to an open invoice.
   * POST /api/v1/sales/customer-credit-notes/uid/{uid}/apply
   * Gated by SALES.MANAGE_RETURN on the backend.
   * Returns the updated CustomerCreditNote with refreshed allocatedAmount + status.
   */
  applyCreditNote(uid: string, request: ApplyCreditNoteRequest): Observable<CustomerCreditNote> {
    return unwrap(this.http.post<ApiResponse<CustomerCreditNote>>(
      `${this.base}/customer-credit-notes/uid/${uid}/apply`, request
    ));
  }

  /**
   * Slice H — fetch open invoices (POSTED / PARTIALLY_PAID) for a customer
   * to populate the apply-modal invoice picker.
   * Delegates to GET /api/v1/debt/statement/uid/{customerUid} and extracts
   * the openInvoices list, reusing the existing CustomerStatement endpoint
   * rather than adding a new one.
   */
  getOpenInvoicesForCustomer(customerUid: string): Observable<OpenInvoiceRow[]> {
    return unwrap(
      this.http.get<ApiResponse<{ openInvoices: OpenInvoiceRow[] }>>(
        `${this.base}/debt/statement/uid/${customerUid}`
      )
    ).pipe(map(stmt => stmt.openInvoices ?? []));
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
