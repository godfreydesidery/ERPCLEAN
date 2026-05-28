/**
 * Wire-shape models for stock-side report endpoints (Slice J / US-RPT-004..006).
 * These mirror the actual backend DTOs — see StockMoveDto, ItemBranchBalanceDto,
 * ItemMovementRowDto. Long id fields serialise as strings globally (JacksonConfig).
 */

// ---------------------------------------------------------------------------
// Stock card — GET /api/v1/stock-card?itemId=&branchId=&page=&size=
// Mirrors StockMoveDto (record). Note: no uid, no runningBalance, no docKind
// computed field — the backend carries refType + refId instead.
// ---------------------------------------------------------------------------

export interface StockMove {
  /** Auto-generated PK, serialised as string by JacksonConfig. */
  id: string;
  /** ISO-8601 Instant — the timestamp of the move. */
  at: string;
  itemId: string;
  branchId: string;
  companyId: string;
  qty: number;
  costAmount: number | null;
  /** IN or OUT */
  direction: 'IN' | 'OUT';
  /** SALE, GRN, ADJUST, PROD_CONSUME, RETURN_OUT, TRANSFER_IN, TRANSFER_OUT, … */
  moveType: string;
  /** Source document kind string (e.g. "SALES_INVOICE", "GRN"). */
  refType: string | null;
  /** Source document PK, serialised as string. */
  refId: string | null;
  actorId: string | null;
  notes: string | null;
  batchId: string | null;
  sectionId: string | null;
  consumptionCategory: string | null;
  authorisedByUserId: string | null;
}

// ---------------------------------------------------------------------------
// Negative stock — GET /api/v1/reports/stock-negative?branchId=
// Mirrors ItemBranchBalanceDto. No itemCode/itemName/branchName on the wire —
// the DTO carries only numeric FKs. We render itemId / branchId in the table.
// ---------------------------------------------------------------------------

export interface ItemBranchBalance {
  itemId: string;
  branchId: string;
  qtyOnHand: number;
  qtyReserved: number | null;
  qtyInTransit: number | null;
  avgCost: number | null;
  lastCost: number | null;
  reorderMin: number | null;
  reorderMax: number | null;
  binLocation: string | null;
  lastMovedAt: string | null;
}

// ---------------------------------------------------------------------------
// Fast / slow movers — GET /api/v1/reports/stock-fast-movers|stock-slow-movers
// Mirrors ItemMovementRowDto. No rank/moveCount/lastMoveAt in the backend DTO —
// those are absent from the actual Java record. We derive rank client-side ($index+1).
// ---------------------------------------------------------------------------

export interface ItemMovementRow {
  itemId: string;
  itemCode: string;
  itemName: string;
  movedQty: number;
  qtyOnHand: number;
}
