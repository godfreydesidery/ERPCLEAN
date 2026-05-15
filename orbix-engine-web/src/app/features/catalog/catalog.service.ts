import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import {
  CreateItemBarcodeRequest,
  CreateItemGroupRequest,
  CreateItemRequest,
  CreatePriceListRequest,
  CreateUomRequest,
  CreateVatGroupRequest,
  Item,
  ItemBarcode,
  ItemGroup,
  ItemStatus,
  Page,
  PriceChangeLog,
  PriceList,
  PriceListItem,
  SetPriceRequest,
  UpdateItemRequest,
  UpdatePriceListRequest,
  UpdateUomRequest,
  UpdateVatGroupRequest,
  Uom,
  VatGroup
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

  // ---- units of measure -----------------------------------------------------

  listUoms(): Observable<Uom[]> {
    return unwrap(this.http.get<ApiResponse<Uom[]>>(`${this.base}/uoms`));
  }

  createUom(request: CreateUomRequest): Observable<Uom> {
    return unwrap(this.http.post<ApiResponse<Uom>>(`${this.base}/uoms`, request));
  }

  updateUom(id: number, request: UpdateUomRequest): Observable<Uom> {
    return unwrap(this.http.patch<ApiResponse<Uom>>(`${this.base}/uoms/${id}`, request));
  }

  // ---- VAT groups -----------------------------------------------------------

  listVatGroups(): Observable<VatGroup[]> {
    return unwrap(this.http.get<ApiResponse<VatGroup[]>>(`${this.base}/vat-groups`));
  }

  createVatGroup(request: CreateVatGroupRequest): Observable<VatGroup> {
    return unwrap(this.http.post<ApiResponse<VatGroup>>(`${this.base}/vat-groups`, request));
  }

  updateVatGroup(id: number, request: UpdateVatGroupRequest): Observable<VatGroup> {
    return unwrap(this.http.patch<ApiResponse<VatGroup>>(`${this.base}/vat-groups/${id}`, request));
  }

  archiveVatGroup(id: number): Observable<void> {
    return this.http.post(`${this.base}/vat-groups/${id}/archive`, {}).pipe(map(() => void 0));
  }

  // ---- item barcodes --------------------------------------------------------

  listBarcodes(itemId: number): Observable<ItemBarcode[]> {
    return unwrap(this.http.get<ApiResponse<ItemBarcode[]>>(`${this.base}/items/${itemId}/barcodes`));
  }

  addBarcode(itemId: number, request: CreateItemBarcodeRequest): Observable<ItemBarcode> {
    return unwrap(this.http.post<ApiResponse<ItemBarcode>>(
      `${this.base}/items/${itemId}/barcodes`, request
    ));
  }

  deleteBarcode(id: number): Observable<void> {
    return this.http.delete(`${this.base}/barcodes/${id}`).pipe(map(() => void 0));
  }

  // ---- price lists ----------------------------------------------------------

  listPriceLists(): Observable<PriceList[]> {
    return unwrap(this.http.get<ApiResponse<PriceList[]>>(`${this.base}/price-lists`));
  }

  createPriceList(request: CreatePriceListRequest): Observable<PriceList> {
    return unwrap(this.http.post<ApiResponse<PriceList>>(`${this.base}/price-lists`, request));
  }

  updatePriceList(id: number, request: UpdatePriceListRequest): Observable<PriceList> {
    return unwrap(this.http.patch<ApiResponse<PriceList>>(`${this.base}/price-lists/${id}`, request));
  }

  archivePriceList(id: number): Observable<void> {
    return this.http.post(`${this.base}/price-lists/${id}/archive`, {}).pipe(map(() => void 0));
  }

  listPrices(priceListId: number): Observable<PriceListItem[]> {
    return unwrap(this.http.get<ApiResponse<PriceListItem[]>>(
      `${this.base}/price-lists/${priceListId}/items`
    ));
  }

  setPrice(priceListId: number, request: SetPriceRequest): Observable<PriceListItem> {
    return unwrap(this.http.put<ApiResponse<PriceListItem>>(
      `${this.base}/price-lists/${priceListId}/items`, request
    ));
  }

  priceHistory(itemId: number): Observable<PriceChangeLog[]> {
    return unwrap(this.http.get<ApiResponse<PriceChangeLog[]>>(
      `${this.base}/items/${itemId}/price-changes`
    ));
  }
}
