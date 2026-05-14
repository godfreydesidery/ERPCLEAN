import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { BusinessDay } from './day.models';

@Injectable({ providedIn: 'root' })
export class DayService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listDays(branchId: number): Observable<BusinessDay[]> {
    const params = new HttpParams().set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<BusinessDay[]>>(`${this.base}/business-days`, { params }));
  }

  /** Current non-closed day, or null if none is open. */
  currentDay(branchId: number): Observable<BusinessDay | null> {
    const params = new HttpParams().set('branchId', branchId);
    return this.http.get<ApiResponse<BusinessDay | null>>(
      `${this.base}/business-days/current`, { params }
    ).pipe(/* envelope may carry data:null */ unwrapNullable());
  }

  openDay(branchId: number, businessDate: string): Observable<BusinessDay> {
    const params = new HttpParams().set('branchId', branchId);
    return unwrap(this.http.post<ApiResponse<BusinessDay>>(
      `${this.base}/business-days`, { businessDate }, { params }
    ));
  }

  startClosing(branchId: number, businessDate: string): Observable<BusinessDay> {
    const params = new HttpParams().set('branchId', branchId);
    return unwrap(this.http.post<ApiResponse<BusinessDay>>(
      `${this.base}/business-days/${businessDate}/start-closing`, {}, { params }
    ));
  }

  closeDay(branchId: number, businessDate: string,
           eodReportObjectKey: string | null): Observable<BusinessDay> {
    const params = new HttpParams().set('branchId', branchId);
    return unwrap(this.http.post<ApiResponse<BusinessDay>>(
      `${this.base}/business-days/${businessDate}/close`, { eodReportObjectKey }, { params }
    ));
  }
}

/** Like unwrap() but tolerates a successful envelope whose data is null. */
function unwrapNullable() {
  return (source: Observable<ApiResponse<BusinessDay | null>>): Observable<BusinessDay | null> =>
    new Observable(subscriber => source.subscribe({
      next: env => subscriber.next(env.data ?? null),
      error: err => subscriber.error(err),
      complete: () => subscriber.complete()
    }));
}
