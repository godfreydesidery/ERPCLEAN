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
  id: string;
  at: string;
  itemId: string;
  branchId: string;
  companyId: string;
  qty: number;
  costAmount: number;
  direction: StockMoveDirection;
  moveType: string;
  refType: string;
  refId: string;
  actorId: string;
  notes: string | null;
  batchId: string | null;
  sectionId: string | null;
  consumptionCategory: ConsumptionCategory | null;
  authorisedByUserId: string | null;
}

export interface ItemBranchBalance {
  itemId: string;
  branchId: string;
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
  id: string;
  itemId: string;
  systemQty: number;
  countedQty: number | null;
  varianceQty: number | null;
  note: string | null;
}

export interface StockCount {
  id: string;
  uid: string;
  number: string;
  branchId: string;
  companyId: string;
  countDate: string;
  type: StockCountType;
  status: StockCountStatus;
  startedBy: string;
  closedBy: string | null;
  postedAt: string | null;
  lines: StockCountLine[];
}

export interface CreateStockCountRequest {
  number: string;
  branchId: string;
  countDate: string;
  type: StockCountType;
  itemIds: number[];
}

export interface RecordCountsRequest {
  counts: { lineId: string; countedQty: number; note: string | null }[];
}

export type StockTransferStatus = 'DRAFT' | 'ISSUED' | 'IN_TRANSIT' | 'RECEIVED' | 'CLOSED';

export interface StockTransferLine {
  id: string;
  itemId: string;
  issuedQty: number;
  receivedQty: number | null;
  costAmount: number;
}

export interface StockTransfer {
  id: string;
  uid: string;
  number: string;
  companyId: string;
  fromBranchId: string;
  toBranchId: string;
  issuedAt: string | null;
  receivedAt: string | null;
  status: StockTransferStatus;
  lines: StockTransferLine[];
}

export interface CreateStockTransferRequest {
  number: string;
  fromBranchId: string;
  toBranchId: string;
  lines: { itemId: string; issuedQty: number }[];
}

export interface ReceiveTransferRequest {
  lines: { lineId: string; receivedQty: number }[];
}

// ---- F2.4: stock batches + FEFO --------------------------------------------

export type StockBatchStatus = 'ACTIVE' | 'EXHAUSTED' | 'EXPIRED' | 'RECALLED';
export const STOCK_BATCH_STATUSES: StockBatchStatus[] = ['ACTIVE', 'EXHAUSTED', 'EXPIRED', 'RECALLED'];

export interface StockBatch {
  id: string;
  uid: string;
  itemId: string;
  branchId: string;
  companyId: string;
  batchNo: string;
  manufacturedAt: string | null;
  expiryAt: string | null;
  qtyReceived: number;
  qtyOnHand: number;
  cost: number;
  sourceDocType: string;
  sourceDocId: string;
  status: StockBatchStatus;
}

export interface RecallStockBatchRequest {
  reason: string;
}

// ---- F2.5: adjustments + internal consumption ------------------------------

export type ConsumptionCategory =
  'CANTEEN' | 'DISPLAY' | 'SAMPLES' | 'DONATION' | 'MAINTENANCE' | 'OTHER';
export const CONSUMPTION_CATEGORIES: ConsumptionCategory[] =
  ['CANTEEN', 'DISPLAY', 'SAMPLES', 'DONATION', 'MAINTENANCE', 'OTHER'];

export interface PostAdjustmentRequest {
  itemId: string;
  branchId: string;
  qty: number;
  unitCost: number | null;
  reason: string;
  sectionId: string | null;
  batchId: string | null;
  authorisedByUserId: string | null;
  allowOversell: boolean;
}

export interface PostInternalConsumptionRequest {
  itemId: string;
  branchId: string;
  qty: number;
  consumptionCategory: ConsumptionCategory;
  sectionId: string;
  authorisedByUserId: string;
  reason: string;
  batchId: string | null;
}
