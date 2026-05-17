import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import {
  CreateCustomerRequest,
  CreateEmployeeRequest,
  CreateSalesAgentRequest,
  CreateSupplierRequest,
  Customer,
  Employee,
  PartyResponse,
  SalesAgent,
  Supplier
} from './party.models';

@Injectable({ providedIn: 'root' })
export class PartyService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** Every party in the caller's company — backs the "pick existing party" picker. */
  listParties(): Observable<PartyResponse[]> {
    return unwrap(this.http.get<ApiResponse<PartyResponse[]>>(`${this.base}/parties`));
  }

  /** Shared-party hint: an existing party in the company with this TIN, or null. */
  findByTin(tin: string): Observable<PartyResponse | null> {
    const params = new HttpParams().set('tin', tin);
    return unwrap(this.http.get<ApiResponse<PartyResponse>>(`${this.base}/parties/by-tin`, { params }))
      .pipe(catchError(() => of(null)));
  }

  // ---- customers ------------------------------------------------------------

  listCustomers(): Observable<Customer[]> {
    return unwrap(this.http.get<ApiResponse<Customer[]>>(`${this.base}/customers`));
  }

  createCustomer(request: CreateCustomerRequest): Observable<Customer> {
    return unwrap(this.http.post<ApiResponse<Customer>>(`${this.base}/customers`, request));
  }

  deactivateCustomer(partyId: number): Observable<void> {
    return this.http.post(`${this.base}/customers/${partyId}/deactivate`, {}).pipe(map(() => void 0));
  }

  // ---- suppliers ------------------------------------------------------------

  listSuppliers(): Observable<Supplier[]> {
    return unwrap(this.http.get<ApiResponse<Supplier[]>>(`${this.base}/suppliers`));
  }

  createSupplier(request: CreateSupplierRequest): Observable<Supplier> {
    return unwrap(this.http.post<ApiResponse<Supplier>>(`${this.base}/suppliers`, request));
  }

  deactivateSupplier(partyId: number): Observable<void> {
    return this.http.post(`${this.base}/suppliers/${partyId}/deactivate`, {}).pipe(map(() => void 0));
  }

  // ---- employees ------------------------------------------------------------

  listEmployees(): Observable<Employee[]> {
    return unwrap(this.http.get<ApiResponse<Employee[]>>(`${this.base}/employees`));
  }

  createEmployee(request: CreateEmployeeRequest): Observable<Employee> {
    return unwrap(this.http.post<ApiResponse<Employee>>(`${this.base}/employees`, request));
  }

  deactivateEmployee(partyId: number): Observable<void> {
    return this.http.post(`${this.base}/employees/${partyId}/deactivate`, {}).pipe(map(() => void 0));
  }

  // ---- sales agents ---------------------------------------------------------

  listSalesAgents(): Observable<SalesAgent[]> {
    return unwrap(this.http.get<ApiResponse<SalesAgent[]>>(`${this.base}/sales-agents`));
  }

  createSalesAgent(request: CreateSalesAgentRequest): Observable<SalesAgent> {
    return unwrap(this.http.post<ApiResponse<SalesAgent>>(`${this.base}/sales-agents`, request));
  }

  deactivateSalesAgent(partyId: number): Observable<void> {
    return this.http.post(`${this.base}/sales-agents/${partyId}/deactivate`, {}).pipe(map(() => void 0));
  }
}
