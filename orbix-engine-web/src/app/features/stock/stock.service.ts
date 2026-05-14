import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { ItemBranchBalance, Page, StockMove } from './stock.models';

@Injectable({ providedIn: 'root' })
export class StockService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listBalances(branchId: number): Observable<ItemBranchBalance[]> {
    const params = new HttpParams().set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<ItemBranchBalance[]>>(
      `${this.base}/balances`, { params }
    ));
  }

  stockCard(itemId: number, branchId: number, page: number, size: number): Observable<Page<StockMove>> {
    const params = new HttpParams()
      .set('itemId', itemId).set('branchId', branchId).set('page', page).set('size', size);
    return unwrap(this.http.get<ApiResponse<Page<StockMove>>>(
      `${this.base}/stock-card`, { params }
    ));
  }
}
