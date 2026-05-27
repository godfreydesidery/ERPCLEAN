import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { Page } from '../../core/api/page';
import {
  CreateCustomerRequest,
  CreateEmployeeRequest,
  CreateSalesAgentRequest,
  CreateSupplierRequest,
  Customer,
  Employee,
  PartyResponse,
  SalesAgent,
  Supplier,
  UpdateCustomerRequest,
  UpdateEmployeeRequest,
  UpdateSalesAgentRequest,
  UpdateSupplierRequest
} from './party.models';

@Injectable({ providedIn: 'root' })
export class PartyService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** Every party in the caller's company — backs the "pick existing party" picker. */
  listParties(): Observable<PartyResponse[]> {
    return unwrap(this.http.get<ApiResponse<PartyResponse[]>>(`${this.base}/parties`));
  }

  /**
   * Reserves the next free party code for {@code prefix} (e.g. `AGT`). Each
   * call increments the company-scoped counter, so callers should only fetch
   * when they actually intend to use the code (form open in create-new mode).
   */
  reservePartyCode(prefix: string): Observable<string> {
    const params = new HttpParams().set('prefix', prefix);
    return unwrap(this.http.post<ApiResponse<{ code: string }>>(
      `${this.base}/parties/codes/reserve`, null, { params }
    )).pipe(map(resp => resp.code));
  }

  /** Shared-party hint: an existing party in the company with this TIN, or null. */
  findByTin(tin: string): Observable<PartyResponse | null> {
    const params = new HttpParams().set('tin', tin);
    return unwrap(this.http.get<ApiResponse<PartyResponse>>(`${this.base}/parties/by-tin`, { params }))
      .pipe(catchError(() => of(null)));
  }

  // ---- customers ------------------------------------------------------------

  listCustomers(q: string, status: string | null, page: number, size: number): Observable<Page<Customer>> {
    return unwrap(this.http.get<ApiResponse<Page<Customer>>>(
      `${this.base}/customers`, { params: pageParams(q, status, page, size) }
    ));
  }

  createCustomer(request: CreateCustomerRequest): Observable<Customer> {
    return unwrap(this.http.post<ApiResponse<Customer>>(`${this.base}/customers`, request));
  }

  updateCustomer(partyUid: string, request: UpdateCustomerRequest): Observable<Customer> {
    return unwrap(this.http.patch<ApiResponse<Customer>>(
      `${this.base}/customers/uid/${partyUid}`, request
    ));
  }

  archiveCustomer(partyUid: string): Observable<void> {
    return this.http.post(`${this.base}/customers/uid/${partyUid}/archive`, {}).pipe(map(() => void 0));
  }

  activateCustomer(partyUid: string): Observable<void> {
    return this.http.post(`${this.base}/customers/uid/${partyUid}/activate`, {}).pipe(map(() => void 0));
  }

  // ---- suppliers ------------------------------------------------------------

  listSuppliers(q: string, status: string | null, page: number, size: number): Observable<Page<Supplier>> {
    return unwrap(this.http.get<ApiResponse<Page<Supplier>>>(
      `${this.base}/suppliers`, { params: pageParams(q, status, page, size) }
    ));
  }

  createSupplier(request: CreateSupplierRequest): Observable<Supplier> {
    return unwrap(this.http.post<ApiResponse<Supplier>>(`${this.base}/suppliers`, request));
  }

  updateSupplier(partyUid: string, request: UpdateSupplierRequest): Observable<Supplier> {
    return unwrap(this.http.patch<ApiResponse<Supplier>>(
      `${this.base}/suppliers/uid/${partyUid}`, request
    ));
  }

  archiveSupplier(partyUid: string): Observable<void> {
    return this.http.post(`${this.base}/suppliers/uid/${partyUid}/archive`, {}).pipe(map(() => void 0));
  }

  activateSupplier(partyUid: string): Observable<void> {
    return this.http.post(`${this.base}/suppliers/uid/${partyUid}/activate`, {}).pipe(map(() => void 0));
  }

  // ---- employees ------------------------------------------------------------

  listEmployees(q: string, status: string | null, page: number, size: number): Observable<Page<Employee>> {
    return unwrap(this.http.get<ApiResponse<Page<Employee>>>(
      `${this.base}/employees`, { params: pageParams(q, status, page, size) }
    ));
  }

  createEmployee(request: CreateEmployeeRequest): Observable<Employee> {
    return unwrap(this.http.post<ApiResponse<Employee>>(`${this.base}/employees`, request));
  }

  updateEmployee(partyUid: string, request: UpdateEmployeeRequest): Observable<Employee> {
    return unwrap(this.http.patch<ApiResponse<Employee>>(
      `${this.base}/employees/uid/${partyUid}`, request
    ));
  }

  archiveEmployee(partyUid: string): Observable<void> {
    return this.http.post(`${this.base}/employees/uid/${partyUid}/archive`, {}).pipe(map(() => void 0));
  }

  activateEmployee(partyUid: string): Observable<void> {
    return this.http.post(`${this.base}/employees/uid/${partyUid}/activate`, {}).pipe(map(() => void 0));
  }

  // ---- sales agents ---------------------------------------------------------

  listSalesAgents(): Observable<SalesAgent[]> {
    return unwrap(this.http.get<ApiResponse<SalesAgent[]>>(`${this.base}/sales-agents`));
  }

  createSalesAgent(request: CreateSalesAgentRequest): Observable<SalesAgent> {
    return unwrap(this.http.post<ApiResponse<SalesAgent>>(`${this.base}/sales-agents`, request));
  }

  updateSalesAgent(partyUid: string, request: UpdateSalesAgentRequest): Observable<SalesAgent> {
    return unwrap(this.http.patch<ApiResponse<SalesAgent>>(
      `${this.base}/sales-agents/uid/${partyUid}`, request
    ));
  }

  archiveSalesAgent(partyUid: string): Observable<void> {
    return this.http.post(`${this.base}/sales-agents/uid/${partyUid}/archive`, {}).pipe(map(() => void 0));
  }

  activateSalesAgent(partyUid: string): Observable<void> {
    return this.http.post(`${this.base}/sales-agents/uid/${partyUid}/activate`, {}).pipe(map(() => void 0));
  }
}

/** Builds the standard list query params (q / status / page / size), omitting blanks. */
function pageParams(q: string, status: string | null, page: number, size: number): HttpParams {
  let params = new HttpParams().set('page', page).set('size', size);
  if (q?.trim()) params = params.set('q', q.trim());
  if (status) params = params.set('status', status);
  return params;
}
