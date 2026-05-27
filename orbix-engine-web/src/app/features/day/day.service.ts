import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import {
  BusinessDay,
  BusinessDayOverride,
  PostBusinessDayOverrideRequest
} from './day.models';

/**
 * Business-day API client. Slice D — calls the new {@code /uid/{uid}} routes
 * for every state transition (start-closing / close / end). The listing /
 * current endpoints remain query-keyed because they're enumeration views, not
 * resource addresses (ADR 0002).
 */
@Injectable({ providedIn: 'root' })
export class DayService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  // ---- list / current ----------------------------------------------------

  listDays(branchId: string): Observable<BusinessDay[]> {
    const params = new HttpParams().set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<BusinessDay[]>>(
      `${this.base}/business-days`, { params }
    ));
  }

  /** Current non-closed day, or null if none is open. */
  currentDay(branchId: string): Observable<BusinessDay | null> {
    const params = new HttpParams().set('branchId', branchId);
    return this.http.get<ApiResponse<BusinessDay | null>>(
      `${this.base}/business-days/current`, { params }
    ).pipe(unwrapNullable<BusinessDay>());
  }

  getByUid(uid: string): Observable<BusinessDay> {
    return unwrap(this.http.get<ApiResponse<BusinessDay>>(
      `${this.base}/business-days/uid/${uid}`
    ));
  }

  // ---- lifecycle (uid-keyed) --------------------------------------------

  openDay(branchId: string, businessDate: string): Observable<BusinessDay> {
    const params = new HttpParams().set('branchId', branchId);
    return unwrap(this.http.post<ApiResponse<BusinessDay>>(
      `${this.base}/business-days`, { businessDate }, { params }
    ));
  }

  startClosing(uid: string): Observable<BusinessDay> {
    return unwrap(this.http.post<ApiResponse<BusinessDay>>(
      `${this.base}/business-days/uid/${uid}/start-closing`, {}
    ));
  }

  closeDay(uid: string, eodReportObjectKey: string | null): Observable<BusinessDay> {
    return unwrap(this.http.post<ApiResponse<BusinessDay>>(
      `${this.base}/business-days/uid/${uid}/close`, { eodReportObjectKey }
    ));
  }

  endDay(uid: string, eodReportObjectKey: string | null): Observable<BusinessDay> {
    return unwrap(this.http.post<ApiResponse<BusinessDay>>(
      `${this.base}/business-days/uid/${uid}/end`, { eodReportObjectKey }
    ));
  }

  // ---- back-dated overrides ---------------------------------------------

  listOverrides(branchId: string): Observable<BusinessDayOverride[]> {
    const params = new HttpParams().set('branchId', branchId);
    return unwrap(this.http.get<ApiResponse<BusinessDayOverride[]>>(
      `${this.base}/business-days/overrides`, { params }
    ));
  }

  postOverrideForDay(dayUid: string,
                     request: PostBusinessDayOverrideRequest): Observable<BusinessDayOverride> {
    return unwrap(this.http.post<ApiResponse<BusinessDayOverride>>(
      `${this.base}/business-days/uid/${dayUid}/overrides`, request
    ));
  }

  archiveOverride(overrideUid: string): Observable<BusinessDayOverride> {
    return unwrap(this.http.post<ApiResponse<BusinessDayOverride>>(
      `${this.base}/business-days/overrides/uid/${overrideUid}/archive`, {}
    ));
  }
}

/** Like unwrap() but tolerates a successful envelope whose data is null. */
function unwrapNullable<T>() {
  return (source: Observable<ApiResponse<T | null>>): Observable<T | null> =>
    new Observable(subscriber => source.subscribe({
      next: env => subscriber.next(env.data ?? null),
      error: err => subscriber.error(err),
      complete: () => subscriber.complete()
    }));
}
