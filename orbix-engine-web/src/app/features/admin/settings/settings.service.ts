import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ApiResponse, unwrap } from '../../../core/api/api-response';
import { Setting, UpdateSettingItem } from './settings.models';

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  list(): Observable<Setting[]> {
    return unwrap(this.http.get<ApiResponse<Setting[]>>(`${this.base}/settings`));
  }

  update(items: UpdateSettingItem[]): Observable<Setting[]> {
    return unwrap(this.http.put<ApiResponse<Setting[]>>(`${this.base}/settings`, { items }));
  }
}
