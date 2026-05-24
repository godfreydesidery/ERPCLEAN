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
export type WeighingUnit = 'KG' | 'G' | 'L' | 'ML';

export const ITEM_TYPES: ItemType[] = ['SELLABLE', 'CONSUMABLE', 'BOTH', 'SERVICE'];
export const WEIGHING_UNITS: WeighingUnit[] = ['KG', 'G', 'L', 'ML'];

export interface Item {
  /** Numeric id as a JSON string (JSON:API discipline). Body-level join
   *  handle for DTOs that still reference items by id. Never used in URLs. */
  id: string;
  /** ULID (26 chars). The canonical external identifier — used in every URL. */
  uid: string;
  companyId: string;
  code: string;
  name: string;
  shortName: string | null;
  type: ItemType;
  itemGroupId: string;
  uomId: string;
  vatGroupId: string;
  tracked: boolean;
  weighed: boolean;
  weighingUnit: WeighingUnit | null;
  batchTracked: boolean;
  avgCost: number;
  lastCost: number;
  minSellPrice: number | null;
  status: ItemStatus;
}

export interface CreateItemRequest {
  code: string;
  name: string;
  shortName: string | null;
  type: ItemType;
  itemGroupId: string;
  uomId: string;
  vatGroupId: string;
}

export interface UpdateItemRequest {
  name: string;
  shortName: string | null;
  type: ItemType;
  itemGroupId: string;
  uomId: string;
  vatGroupId: string;
  tracked: boolean;
  minSellPrice: number | null;
  weighed: boolean;
  weighingUnit: WeighingUnit | null;
  batchTracked: boolean;
}

export interface ItemGroup {
  id: string;
  uid: string;
  parentId: string | null;
  level: number;
  code: string;
  name: string;
  status: ItemStatus;
}

export interface CreateItemGroupRequest {
  parentId: string | null;
  code: string;
  name: string;
}

// ---- F1.4: UoM, VAT groups, barcodes ----------------------------------------

export type UomDimension = 'COUNT' | 'WEIGHT' | 'VOLUME' | 'LENGTH';
export const UOM_DIMENSIONS: UomDimension[] = ['COUNT', 'WEIGHT', 'VOLUME', 'LENGTH'];

export interface Uom {
  id: string;
  uid: string;
  code: string;
  name: string;
  dimension: UomDimension;
  base: boolean;
  status: ItemStatus;
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
  id: string;
  uid: string;
  companyId: string;
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

export type BarcodeType = 'UPC' | 'EAN13' | 'EAN8' | 'PLU' | 'EMBEDDED_WEIGHT' | 'EMBEDDED_PRICE';
export const BARCODE_TYPES: BarcodeType[] =
  ['UPC', 'EAN13', 'EAN8', 'PLU', 'EMBEDDED_WEIGHT', 'EMBEDDED_PRICE'];

export interface ItemBarcode {
  id: string;
  uid: string;
  itemId: string;
  barcode: string;
  barcodeType: BarcodeType;
  packUomId: string | null;
  packQty: number;
}

export interface CreateItemBarcodeRequest {
  barcode: string;
  barcodeType: BarcodeType;
  packUomId: string | null;
  packQty: number | null;
}

// ---- F1.5: price lists + price-change audit ---------------------------------

export interface PriceList {
  id: string;
  uid: string;
  companyId: string;
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
  id: string;
  priceListId: string;
  itemId: string;
  uomId: string;
  price: number;
  validFrom: string;
  validTo: string | null;
}

export interface SetPriceRequest {
  itemId: string;
  uomId: string;
  price: number;
  effectiveFrom: string;
  reason: string | null;
}

export interface PriceChangeLog {
  id: string;
  priceListItemId: string;
  oldPrice: number | null;
  newPrice: number;
  effectiveFrom: string;
  changedAt: string;
  changedBy: string;
  reason: string | null;
}
