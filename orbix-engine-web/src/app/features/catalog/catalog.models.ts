/** Mirrors the backend catalog DTOs (ItemController, ItemGroupController). */

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type ItemType = 'SELLABLE' | 'CONSUMABLE' | 'BOTH' | 'SERVICE';
export type ItemStatus = 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';

export const ITEM_TYPES: ItemType[] = ['SELLABLE', 'CONSUMABLE', 'BOTH', 'SERVICE'];

export interface Item {
  id: number;
  companyId: number;
  code: string;
  name: string;
  shortName: string | null;
  type: ItemType;
  itemGroupId: number;
  uomId: number;
  vatGroupId: number;
  tracked: boolean;
  avgCost: number;
  lastCost: number;
  minSellPrice: number | null;
  status: ItemStatus;
}

export interface CreateItemRequest {
  code: string;
  name: string;
  type: ItemType;
  itemGroupId: number;
  uomId: number;
  vatGroupId: number;
}

export interface UpdateItemRequest {
  name: string;
  shortName: string | null;
  type: ItemType;
  itemGroupId: number;
  uomId: number;
  vatGroupId: number;
  tracked: boolean;
  minSellPrice: number | null;
}

export interface ItemGroup {
  id: number;
  parentId: number | null;
  level: number;
  code: string;
  name: string;
  status: ItemStatus;
}

export interface CreateItemGroupRequest {
  parentId: number | null;
  code: string;
  name: string;
}

// ---- F1.4: UoM, VAT groups, barcodes ----------------------------------------

export type UomDimension = 'COUNT' | 'WEIGHT' | 'VOLUME' | 'LENGTH';
export const UOM_DIMENSIONS: UomDimension[] = ['COUNT', 'WEIGHT', 'VOLUME', 'LENGTH'];

export interface Uom {
  id: number;
  code: string;
  name: string;
  dimension: UomDimension;
  base: boolean;
}

export interface CreateUomRequest {
  code: string;
  name: string;
  dimension: UomDimension;
  base: boolean;
}

export interface UpdateUomRequest {
  name: string;
  dimension: UomDimension;
  base: boolean;
}

export interface VatGroup {
  id: number;
  companyId: number;
  code: string;
  name: string;
  rate: number;
  validFrom: string;
  isDefault: boolean;
  status: ItemStatus;
}

export interface CreateVatGroupRequest {
  code: string;
  name: string;
  rate: number;
  validFrom: string;
  isDefault: boolean;
}

export interface UpdateVatGroupRequest {
  name: string;
  rate: number;
  validFrom: string;
  isDefault: boolean;
}

export interface ItemBarcode {
  id: number;
  itemId: number;
  barcode: string;
  packUomId: number | null;
  packQty: number;
}

export interface CreateItemBarcodeRequest {
  barcode: string;
  packUomId: number | null;
  packQty: number | null;
}

// ---- F1.5: price lists + price-change audit ---------------------------------

export interface PriceList {
  id: number;
  companyId: number;
  code: string;
  name: string;
  currencyCode: string;
  validFrom: string;
  validTo: string | null;
  isDefault: boolean;
  taxInclusive: boolean;
  status: ItemStatus;
}

export interface CreatePriceListRequest {
  code: string;
  name: string;
  currencyCode: string;
  validFrom: string;
  validTo: string | null;
  isDefault: boolean;
  taxInclusive: boolean;
}

export interface UpdatePriceListRequest {
  name: string;
  currencyCode: string;
  validFrom: string;
  validTo: string | null;
  isDefault: boolean;
  taxInclusive: boolean;
}

export interface PriceListItem {
  id: number;
  priceListId: number;
  itemId: number;
  uomId: number;
  price: number;
  validFrom: string;
  validTo: string | null;
}

export interface SetPriceRequest {
  itemId: number;
  uomId: number;
  price: number;
  effectiveFrom: string;
  reason: string | null;
}

export interface PriceChangeLog {
  id: number;
  priceListItemId: number;
  oldPrice: number | null;
  newPrice: number;
  effectiveFrom: string;
  changedAt: string;
  changedBy: number;
  reason: string | null;
}
