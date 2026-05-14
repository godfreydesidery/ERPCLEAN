/** Mirrors the backend StockController DTOs. */

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type StockMoveDirection = 'IN' | 'OUT';

export interface StockMove {
  id: number;
  at: string;
  itemId: number;
  branchId: number;
  companyId: number;
  qty: number;
  costAmount: number;
  direction: StockMoveDirection;
  moveType: string;
  refType: string;
  refId: number;
  actorId: number;
  notes: string | null;
}

export interface ItemBranchBalance {
  itemId: number;
  branchId: number;
  qtyOnHand: number;
  qtyReserved: number;
  qtyInTransit: number;
  avgCost: number;
  lastCost: number;
  reorderMin: number | null;
  reorderMax: number | null;
  binLocation: string | null;
  lastMovedAt: string | null;
}
