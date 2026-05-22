import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../api/api-response';

/** A currency registered in the company's currency master. */
export interface Currency {
  code: string;
  name: string;
  symbol: string | null;
  minorUnitDigits: number;
  status: 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
}

@Injectable({ providedIn: 'root' })
export class CurrencyService {
  private readonly http = inject(HttpClient);

  /**
   * Lists every currency registered in the deployment. Callers typically
   * filter to {@code status === 'ACTIVE'} for picker dropdowns.
   */
  listCurrencies(): Observable<Currency[]> {
    return unwrap(this.http.get<ApiResponse<Currency[]>>(
      `${environment.apiUrl}/currencies`
    ));
  }
}
