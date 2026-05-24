import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, unwrap } from '../../../core/api/api-response';
import {
  CreateCurrencyRequest,
  Currency,
  FxRate,
  QuoteFxRateRequest,
  UpdateCurrencyRequest
} from './currency-admin.models';

@Injectable({ providedIn: 'root' })
export class CurrencyAdminService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listCurrencies(): Observable<Currency[]> {
    return unwrap(this.http.get<ApiResponse<Currency[]>>(`${this.base}/currencies`));
  }

  createCurrency(request: CreateCurrencyRequest): Observable<Currency> {
    return unwrap(this.http.post<ApiResponse<Currency>>(`${this.base}/currencies`, request));
  }

  updateCurrency(code: string, request: UpdateCurrencyRequest): Observable<Currency> {
    return unwrap(this.http.patch<ApiResponse<Currency>>(`${this.base}/currencies/${code}`, request));
  }

  enableCurrency(code: string): Observable<Currency> {
    return unwrap(this.http.post<ApiResponse<Currency>>(`${this.base}/currencies/${code}/enable`, {}));
  }

  disableCurrency(code: string): Observable<Currency> {
    return unwrap(this.http.post<ApiResponse<Currency>>(`${this.base}/currencies/${code}/disable`, {}));
  }

  listRates(): Observable<FxRate[]> {
    return unwrap(this.http.get<ApiResponse<FxRate[]>>(`${this.base}/fx-rates`));
  }

  quoteRate(request: QuoteFxRateRequest): Observable<FxRate> {
    return unwrap(this.http.post<ApiResponse<FxRate>>(`${this.base}/fx-rates`, request));
  }
}
