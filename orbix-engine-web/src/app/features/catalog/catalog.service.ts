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

  getItem(uid: string): Observable<Item> {
    return unwrap(this.http.get<ApiResponse<Item>>(`${this.base}/items/uid/${uid}`));
  }

  createItem(request: CreateItemRequest): Observable<Item> {
    return unwrap(this.http.post<ApiResponse<Item>>(`${this.base}/items`, request));
  }

  updateItem(uid: string, request: UpdateItemRequest): Observable<Item> {
    return unwrap(this.http.patch<ApiResponse<Item>>(`${this.base}/items/uid/${uid}`, request));
  }

  archiveItem(uid: string): Observable<void> {
    return this.http.post(`${this.base}/items/uid/${uid}/archive`, {}).pipe(map(() => void 0));
  }

  activateItem(uid: string): Observable<void> {
    return this.http.post(`${this.base}/items/uid/${uid}/activate`, {}).pipe(map(() => void 0));
  }

  // ---- item groups ----------------------------------------------------------

  listGroups(): Observable<ItemGroup[]> {
    return unwrap(this.http.get<ApiResponse<ItemGroup[]>>(`${this.base}/item-groups`));
  }

  createGroup(request: CreateItemGroupRequest): Observable<ItemGroup> {
    return unwrap(this.http.post<ApiResponse<ItemGroup>>(`${this.base}/item-groups`, request));
  }

  renameGroup(uid: string, name: string): Observable<ItemGroup> {
    return unwrap(this.http.patch<ApiResponse<ItemGroup>>(`${this.base}/item-groups/uid/${uid}`, { name }));
  }

  moveGroup(uid: string, newParentId: string | null): Observable<ItemGroup> {
    return unwrap(this.http.post<ApiResponse<ItemGroup>>(
      `${this.base}/item-groups/uid/${uid}/move`, { newParentId }
    ));
  }

  archiveGroup(uid: string): Observable<void> {
    return this.http.post(`${this.base}/item-groups/uid/${uid}/archive`, {}).pipe(map(() => void 0));
  }

  // ---- units of measure -----------------------------------------------------

  listUoms(): Observable<Uom[]> {
    return unwrap(this.http.get<ApiResponse<Uom[]>>(`${this.base}/uoms`));
  }

  createUom(request: CreateUomRequest): Observable<Uom> {
    return unwrap(this.http.post<ApiResponse<Uom>>(`${this.base}/uoms`, request));
  }

  updateUom(uid: string, request: UpdateUomRequest): Observable<Uom> {
    return unwrap(this.http.patch<ApiResponse<Uom>>(`${this.base}/uoms/uid/${uid}`, request));
  }

  archiveUom(uid: string): Observable<void> {
    return this.http.post(`${this.base}/uoms/uid/${uid}/archive`, {}).pipe(map(() => void 0));
  }

  activateUom(uid: string): Observable<void> {
    return this.http.post(`${this.base}/uoms/uid/${uid}/activate`, {}).pipe(map(() => void 0));
  }

  // ---- VAT groups -----------------------------------------------------------

  listVatGroups(): Observable<VatGroup[]> {
    return unwrap(this.http.get<ApiResponse<VatGroup[]>>(`${this.base}/vat-groups`));
  }

  createVatGroup(request: CreateVatGroupRequest): Observable<VatGroup> {
    return unwrap(this.http.post<ApiResponse<VatGroup>>(`${this.base}/vat-groups`, request));
  }

  updateVatGroup(uid: string, request: UpdateVatGroupRequest): Observable<VatGroup> {
    return unwrap(this.http.patch<ApiResponse<VatGroup>>(`${this.base}/vat-groups/uid/${uid}`, request));
  }

  archiveVatGroup(uid: string): Observable<void> {
    return this.http.post(`${this.base}/vat-groups/uid/${uid}/archive`, {}).pipe(map(() => void 0));
  }

  // ---- item barcodes --------------------------------------------------------

  listBarcodes(itemUid: string): Observable<ItemBarcode[]> {
    return unwrap(this.http.get<ApiResponse<ItemBarcode[]>>(`${this.base}/items/uid/${itemUid}/barcodes`));
  }

  addBarcode(itemUid: string, request: CreateItemBarcodeRequest): Observable<ItemBarcode> {
    return unwrap(this.http.post<ApiResponse<ItemBarcode>>(
      `${this.base}/items/uid/${itemUid}/barcodes`, request
    ));
  }

  deleteBarcode(uid: string): Observable<void> {
    return this.http.delete(`${this.base}/barcodes/uid/${uid}`).pipe(map(() => void 0));
  }

  // ---- price lists ----------------------------------------------------------

  listPriceLists(): Observable<PriceList[]> {
    return unwrap(this.http.get<ApiResponse<PriceList[]>>(`${this.base}/price-lists`));
  }

  createPriceList(request: CreatePriceListRequest): Observable<PriceList> {
    return unwrap(this.http.post<ApiResponse<PriceList>>(`${this.base}/price-lists`, request));
  }

  updatePriceList(uid: string, request: UpdatePriceListRequest): Observable<PriceList> {
    return unwrap(this.http.patch<ApiResponse<PriceList>>(`${this.base}/price-lists/uid/${uid}`, request));
  }

  archivePriceList(uid: string): Observable<void> {
    return this.http.post(`${this.base}/price-lists/uid/${uid}/archive`, {}).pipe(map(() => void 0));
  }

  listPrices(priceListUid: string): Observable<PriceListItem[]> {
    return unwrap(this.http.get<ApiResponse<PriceListItem[]>>(
      `${this.base}/price-lists/uid/${priceListUid}/items`
    ));
  }

  setPrice(priceListUid: string, request: SetPriceRequest): Observable<PriceListItem> {
    return unwrap(this.http.put<ApiResponse<PriceListItem>>(
      `${this.base}/price-lists/uid/${priceListUid}/items`, request
    ));
  }

  priceHistory(itemUid: string): Observable<PriceChangeLog[]> {
    return unwrap(this.http.get<ApiResponse<PriceChangeLog[]>>(
      `${this.base}/items/uid/${itemUid}/price-changes`
    ));
  }
}
