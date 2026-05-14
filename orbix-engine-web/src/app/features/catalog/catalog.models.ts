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
