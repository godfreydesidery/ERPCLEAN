import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { Page } from '../../core/api/page';
import {
  AdjustCreditLimitRequest,
  AgingBucket,
  CreatePartyNoteRequest,
  CustomerStatement,
  DebtAging,
  DunningQueueRow,
  PartyNote,
  PartyNoteKind,
  SupplierAging,
  SupplierDunningQueueRow,
  SupplierStatement
} from './debt.models';

/**
 * Slice G — debt-module HTTP service.
 *
 * Mirrors {@code DebtController} ({@code /api/v1/debt/*}). Every endpoint is
 * gated by {@code DEBT.READ} class-level on the backend; the per-action
 * write perms ({@code DEBT.CREDIT_LIMIT.UPDATE}, {@code DEBT.NOTE.CREATE},
 * {@code DEBT.NOTE.ARCHIVE}) decorate the relevant methods. Callers should
 * treat 403 as a UX state, not a fatal error.
 *
 * Wrapping is via the project's {@link ApiResponse} envelope; we
 * {@code unwrap()} per-call so feature code sees the raw {@code T}
 * (mirrors {@code DashboardService}).
 */
@Injectable({ providedIn: 'root' })
export class DebtService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /**
   * 5-bucket aging snapshot. {@code branchId} null = company-wide.
   * Backed by {@code GET /api/v1/debt/aging}; permission {@code DEBT.READ}.
   */
  aging(branchId: string | null, asOf?: string): Observable<DebtAging> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    if (asOf) params = params.set('asOf', asOf);
    return unwrap(this.http.get<ApiResponse<DebtAging>>(
      `${this.base}/debt/aging`, { params }
    ));
  }

  /**
   * Paged dunning queue. {@code bucket} narrows to one aging band; default
   * (null) returns all customers with overdue exposure.
   * Backed by {@code GET /api/v1/debt/dunning}; permission {@code DEBT.READ}.
   */
  dunning(
    branchId: string | null,
    bucket: AgingBucket | null,
    page = 0,
    size = 25
  ): Observable<Page<DunningQueueRow>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (branchId != null) params = params.set('branchId', branchId);
    if (bucket) params = params.set('bucket', bucket);
    return unwrap(this.http.get<ApiResponse<Page<DunningQueueRow>>>(
      `${this.base}/debt/dunning`, { params }
    ));
  }

  /**
   * Per-customer debt drill-down. Open invoices + recent receipts +
   * credit-limit headroom.
   * Backed by {@code GET /api/v1/debt/statement/uid/{uid}}; permission
   * {@code DEBT.READ}.
   */
  customerStatement(customerUid: string): Observable<CustomerStatement> {
    return unwrap(this.http.get<ApiResponse<CustomerStatement>>(
      `${this.base}/debt/statement/uid/${customerUid}`
    ));
  }

  /**
   * Adjust a customer's credit limit from the debt surface. Returns the
   * refreshed {@link CustomerStatement} (the backend mirrors the same DTO
   * shape on read + write).
   * Backed by {@code POST /api/v1/debt/customer/uid/{uid}/credit-limit};
   * permission {@code DEBT.CREDIT_LIMIT.UPDATE}.
   */
  adjustCreditLimit(
    customerUid: string,
    request: AdjustCreditLimitRequest
  ): Observable<CustomerStatement> {
    return unwrap(this.http.post<ApiResponse<CustomerStatement>>(
      `${this.base}/debt/customer/uid/${customerUid}/credit-limit`,
      request
    ));
  }

  /**
   * Append a chase note against a party. The backend resolves
   * {@code customerUid → party.id} internally.
   * Backed by {@code POST /api/v1/debt/notes}; permission
   * {@code DEBT.NOTE.CREATE}.
   */
  createNote(request: CreatePartyNoteRequest): Observable<PartyNote> {
    return unwrap(this.http.post<ApiResponse<PartyNote>>(
      `${this.base}/debt/notes`, request
    ));
  }

  /**
   * List chase notes for a party (customer or supplier), newest-first.
   * Pass {@code kind} to filter by note type; backend defaults to {@code AR_CHASE}
   * when omitted (backwards-compat). {@code includeArchived} default false;
   * {@code limit} default 50.
   * Backed by {@code GET /api/v1/debt/notes}; inherits class-level
   * {@code DEBT.READ}.
   */
  listNotes(
    partyUid: string,
    opts?: { includeArchived?: boolean; limit?: number; kind?: PartyNoteKind }
  ): Observable<PartyNote[]> {
    let params = new HttpParams().set('customerUid', partyUid);
    if (opts?.kind) params = params.set('kind', opts.kind);
    if (opts?.includeArchived) params = params.set('includeArchived', 'true');
    if (opts?.limit != null) params = params.set('limit', opts.limit);
    return unwrap(this.http.get<ApiResponse<PartyNote[]>>(
      `${this.base}/debt/notes`, { params }
    ));
  }

  /**
   * Get a single chase note by uid.
   * Backed by {@code GET /api/v1/debt/notes/uid/{uid}}.
   */
  getNote(noteUid: string): Observable<PartyNote> {
    return unwrap(this.http.get<ApiResponse<PartyNote>>(
      `${this.base}/debt/notes/uid/${noteUid}`
    ));
  }

  /**
   * Soft-delete a chase note (notes are append-only).
   * Backed by {@code POST /api/v1/debt/notes/uid/{uid}/archive};
   * permission {@code DEBT.NOTE.ARCHIVE}.
   */
  archiveNote(noteUid: string): Observable<PartyNote> {
    return unwrap(this.http.post<ApiResponse<PartyNote>>(
      `${this.base}/debt/notes/uid/${noteUid}/archive`, {}
    ));
  }

  // ---------------------------------------------------------------------------
  // Slice G.1 — Supplier-AP methods
  // ---------------------------------------------------------------------------

  /**
   * 5-bucket AP aging snapshot. {@code branchId} null = company-wide.
   * Backed by {@code GET /api/v1/debt/supplier-aging}; permission {@code DEBT.READ}.
   */
  supplierAging(branchId?: string | null, asOf?: string | null): Observable<SupplierAging> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    if (asOf) params = params.set('asOf', asOf);
    return unwrap(this.http.get<ApiResponse<SupplierAging>>(
      `${this.base}/debt/supplier-aging`, { params }
    ));
  }

  /**
   * Paged supplier obligations queue.
   * Backed by {@code GET /api/v1/debt/supplier-dunning}; permission {@code DEBT.READ}.
   */
  supplierDunning(
    branchId?: string | null,
    bucketFilter?: AgingBucket | null,
    page = 0,
    size = 25
  ): Observable<Page<SupplierDunningQueueRow>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (branchId != null) params = params.set('branchId', branchId);
    if (bucketFilter) params = params.set('bucket', bucketFilter);
    return unwrap(this.http.get<ApiResponse<Page<SupplierDunningQueueRow>>>(
      `${this.base}/debt/supplier-dunning`, { params }
    ));
  }

  /**
   * Per-supplier debt drill-down. Open AP invoices + recent payments.
   * Backed by {@code GET /api/v1/debt/supplier/uid/{uid}}; permission {@code DEBT.READ}.
   */
  supplierStatement(uid: string): Observable<SupplierStatement> {
    return unwrap(this.http.get<ApiResponse<SupplierStatement>>(
      `${this.base}/debt/supplier/uid/${uid}`
    ));
  }
}
