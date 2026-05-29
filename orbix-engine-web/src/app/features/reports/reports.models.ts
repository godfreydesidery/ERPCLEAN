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

// ---------------------------------------------------------------------------
// Customer / Supplier statement — GET /api/v1/reports/customer-statement
//                                  GET /api/v1/reports/supplier-statement
// Mirrors PartyStatementDto + StatementEntryDto (common module DTOs, F8.7).
// All Long id fields serialise as strings (JacksonConfig global modifier).
// ---------------------------------------------------------------------------

/**
 * One chronological entry in an AR/AP statement.
 *
 * AR kind values: INVOICE | RECEIPT | CREDIT_NOTE
 * AP kind values: INVOICE | PAYMENT
 * A voided entry contributes zero to debit/credit/balance but is included
 * for traceability.
 */
export interface StatementEntry {
  /** ISO-8601 date string — YYYY-MM-DD. */
  date: string;
  /** INVOICE | RECEIPT | CREDIT_NOTE | PAYMENT */
  kind: string;
  /** Source document numeric PK, serialised as string. */
  refId: string;
  /** Printable document number (e.g. SI-00001). */
  number: string;
  /** Optional free-text reference. */
  reference: string | null;
  debit: number;
  credit: number;
  /** Running balance after this entry. */
  balance: number;
  voided: boolean;
}

/** Envelope returned by customer-statement and supplier-statement endpoints. */
export interface PartyStatement {
  /** Numeric party PK, serialised as string. */
  partyId: string;
  /** CUSTOMER | SUPPLIER */
  partyType: string;
  /** ISO-8601 date string — window start. */
  from: string;
  /** ISO-8601 date string — window end. */
  to: string;
  openingBalance: number;
  periodDebits: number;
  periodCredits: number;
  closingBalance: number;
  entries: StatementEntry[];
}

// ---------------------------------------------------------------------------
// Layby ageing — GET /api/v1/reports/layby-ageing
// Mirrors LaybyAgeingReportDto + LaybyAgeingBucketDto + LaybyAgeingOrderDto
// (orders module DTOs, F8.6 / US-RPT-014).
// ---------------------------------------------------------------------------

/** Rollup for one (type, age-bucket) combination. */
export interface LaybyAgeingBucket {
  /** LAYBY | PRE_ORDER */
  type: string;
  /** Human-readable label e.g. "0-7 days". */
  bucketLabel: string;
  minDays: number;
  /** Integer.MAX_VALUE for the topmost bucket. */
  maxDays: number;
  orderCount: number;
  totalAmount: number;
  paidAmount: number;
  balanceDue: number;
}

/** One order in the flat drill-down list (oldest-first). */
export interface LaybyAgeingOrder {
  /** Order numeric PK, serialised as string. */
  id: string;
  number: string;
  /** Branch numeric PK, serialised as string. */
  branchId: string;
  /** Customer numeric PK, serialised as string. */
  customerId: string;
  /** LAYBY | PRE_ORDER */
  type: string;
  /** Order status enum string. */
  status: string;
  /** ISO-8601 Instant string. */
  createdAt: string;
  /** ISO-8601 Instant string — when the reservation expires. */
  reservedUntil: string | null;
  ageDays: number;
  /** Null if no expiry set. Negative means already expired. */
  daysUntilExpiry: number | null;
  totalAmount: number;
  paidAmount: number;
  balanceDue: number;
}

/** Top-level envelope from GET /api/v1/reports/layby-ageing. */
export interface LaybyAgeingReport {
  /** ISO-8601 Instant string — the asOf timestamp used for age computation. */
  asOf: string;
  /** Outstanding balance per type (keys: LAYBY, PRE_ORDER). */
  balanceByType: Record<string, number>;
  /** Open order count per type. */
  countByType: Record<string, number>;
  buckets: LaybyAgeingBucket[];
  orders: LaybyAgeingOrder[];
}
