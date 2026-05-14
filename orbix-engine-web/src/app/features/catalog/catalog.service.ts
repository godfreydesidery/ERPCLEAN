import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import {
  CreateItemGroupRequest,
  CreateItemRequest,
  Item,
  ItemGroup,
  ItemStatus,
  Page,
  UpdateItemRequest
} from './catalog.models';

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  // ---- items ----------------------------------------------------------------

  listItems(status: ItemStatus | null, page: number, size: number): Observable<Page<Item>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return unwrap(this.http.get<ApiResponse<Page<Item>>>(`${this.base}/items`, { params }));
  }

  getItem(id: number): Observable<Item> {
    return unwrap(this.http.get<ApiResponse<Item>>(`${this.base}/items/${id}`));
  }

  createItem(request: CreateItemRequest): Observable<Item> {
    return unwrap(this.http.post<ApiResponse<Item>>(`${this.base}/items`, request));
  }

  updateItem(id: number, request: UpdateItemRequest): Observable<Item> {
    return unwrap(this.http.patch<ApiResponse<Item>>(`${this.base}/items/${id}`, request));
  }

  archiveItem(id: number): Observable<void> {
    return this.http.post(`${this.base}/items/${id}/archive`, {}).pipe(map(() => void 0));
  }

  activateItem(id: number): Observable<void> {
    return this.http.post(`${this.base}/items/${id}/activate`, {}).pipe(map(() => void 0));
  }

  // ---- item groups ----------------------------------------------------------

  listGroups(): Observable<ItemGroup[]> {
    return unwrap(this.http.get<ApiResponse<ItemGroup[]>>(`${this.base}/item-groups`));
  }

  createGroup(request: CreateItemGroupRequest): Observable<ItemGroup> {
    return unwrap(this.http.post<ApiResponse<ItemGroup>>(`${this.base}/item-groups`, request));
  }

  renameGroup(id: number, name: string): Observable<ItemGroup> {
    return unwrap(this.http.patch<ApiResponse<ItemGroup>>(`${this.base}/item-groups/${id}`, { name }));
  }

  moveGroup(id: number, newParentId: number | null): Observable<ItemGroup> {
    return unwrap(this.http.post<ApiResponse<ItemGroup>>(
      `${this.base}/item-groups/${id}/move`, { newParentId }
    ));
  }

  archiveGroup(id: number): Observable<void> {
    return this.http.post(`${this.base}/item-groups/${id}/archive`, {}).pipe(map(() => void 0));
  }
}
