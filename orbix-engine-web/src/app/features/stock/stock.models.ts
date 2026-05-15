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
  batchId: number | null;
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

// ---- F2.3: stock counts + transfers ----------------------------------------

export type StockCountType = 'FULL' | 'CYCLE' | 'SPOT';
export type StockCountStatus = 'DRAFT' | 'IN_PROGRESS' | 'CLOSED' | 'POSTED';
export const STOCK_COUNT_TYPES: StockCountType[] = ['FULL', 'CYCLE', 'SPOT'];

export interface StockCountLine {
  id: number;
  itemId: number;
  systemQty: number;
  countedQty: number | null;
  varianceQty: number | null;
  note: string | null;
}

export interface StockCount {
  id: number;
  number: string;
  branchId: number;
  companyId: number;
  countDate: string;
  type: StockCountType;
  status: StockCountStatus;
  startedBy: number;
  closedBy: number | null;
  postedAt: string | null;
  lines: StockCountLine[];
}

export interface CreateStockCountRequest {
  number: string;
  branchId: number;
  countDate: string;
  type: StockCountType;
  itemIds: number[];
}

export interface RecordCountsRequest {
  counts: { lineId: number; countedQty: number; note: string | null }[];
}

export type StockTransferStatus = 'DRAFT' | 'ISSUED' | 'IN_TRANSIT' | 'RECEIVED' | 'CLOSED';

export interface StockTransferLine {
  id: number;
  itemId: number;
  issuedQty: number;
  receivedQty: number | null;
  costAmount: number;
}

export interface StockTransfer {
  id: number;
  number: string;
  companyId: number;
  fromBranchId: number;
  toBranchId: number;
  issuedAt: string | null;
  receivedAt: string | null;
  status: StockTransferStatus;
  lines: StockTransferLine[];
}

export interface CreateStockTransferRequest {
  number: string;
  fromBranchId: number;
  toBranchId: number;
  lines: { itemId: number; issuedQty: number }[];
}

export interface ReceiveTransferRequest {
  lines: { lineId: number; receivedQty: number }[];
}

// ---- F2.4: stock batches + FEFO --------------------------------------------

export type StockBatchStatus = 'ACTIVE' | 'EXHAUSTED' | 'EXPIRED' | 'RECALLED';
export const STOCK_BATCH_STATUSES: StockBatchStatus[] = ['ACTIVE', 'EXHAUSTED', 'EXPIRED', 'RECALLED'];

export interface StockBatch {
  id: number;
  itemId: number;
  branchId: number;
  companyId: number;
  batchNo: string;
  manufacturedAt: string | null;
  expiryAt: string | null;
  qtyReceived: number;
  qtyOnHand: number;
  cost: number;
  sourceDocType: string;
  sourceDocId: number;
  status: StockBatchStatus;
}

export interface RecallStockBatchRequest {
  reason: string;
}
