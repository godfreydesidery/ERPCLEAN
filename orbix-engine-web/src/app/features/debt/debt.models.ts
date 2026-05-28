/**
 * Slice G — TypeScript models mirroring the backend DTOs.
 *
 * Long-id fields serialise as JSON strings globally (via the backend
 * {@code IdLongAsStringSerializerModifier}); BigDecimals stay numeric.
 * Customer drill-down identifiers are addressed by {@code customerUid}
 * (Crockford ULID) on URLs, with {@code customerId} kept for body-level
 * joins.
 */

/** Aging bucket dimension — matches backend {@code AgingBucket} enum. */
export type AgingBucket = 'CURRENT' | 'D_1_30' | 'D_31_60' | 'D_61_90' | 'D_90_PLUS';

/** Lifecycle of a {@code party_note}. */
export type PartyNoteStatus = 'ACTIVE' | 'ARCHIVED';

/** Workflow classifier on a {@code party_note}. */
export type PartyNoteKind = 'AR_CHASE' | 'AP_CHASE' | 'GENERAL';

/** Status of an open sales invoice line in the customer drill-down. */
export type SalesInvoiceStatus = 'DRAFT' | 'POSTED' | 'PARTIALLY_PAID' | 'PAID' | 'VOIDED';

/** GET /api/v1/debt/aging → DebtAgingDto. */
export interface DebtAging {
  asOf: string;
  branchId: string | null;
  currencyCode: string;
  totals: DebtAgingTotals;
  rows: DebtAgingCustomerRow[];
}

export interface DebtAgingTotals {
  current: number;
  d1_30: number;
  d31_60: number;
  d61_90: number;
  d90_plus: number;
  totalOutstanding: number;
  customerCount: number;
}

export interface DebtAgingCustomerRow {
  customerId: string;
  customerUid: string;
  customerName: string;
  current: number;
  d1_30: number;
  d31_60: number;
  d61_90: number;
  d90_plus: number;
  totalOutstanding: number;
  oldestDaysOverdue: number | null;
  creditLimit: number;
  creditUtilisation: number | null;
}

/** GET /api/v1/debt/dunning row. */
export interface DunningQueueRow {
  customerId: string;
  customerUid: string;
  customerName: string;
  creditLimit: number;
  totalOutstanding: number;
  oldestDaysOverdue: number | null;
  oldestDueDate: string | null;
  worstBucket: AgingBucket;
  overdueInvoiceCount: number;
}

/** GET /api/v1/debt/customer/uid/{uid} → CustomerStatementDto. */
export interface CustomerStatement {
  customerId: string;
  customerUid: string;
  customerName: string;
  currencyCode: string;
  creditLimit: number;
  totalOutstanding: number;
  creditUtilisation: number | null;
  openInvoiceCount: number;
  overdueInvoiceCount: number;
  asOf: string;
  openInvoices: OpenInvoiceRow[];
  recentReceipts: RecentReceiptRow[];
}

export interface OpenInvoiceRow {
  invoiceId: string;
  invoiceUid: string;
  number: string;
  invoiceDate: string;
  dueDate: string | null;
  totalAmount: number;
  paidAmount: number;
  outstanding: number;
  daysOverdue: number | null;
  status: SalesInvoiceStatus;
}

export interface RecentReceiptRow {
  receiptId: string;
  receiptUid: string;
  number: string;
  receiptDate: string;
  postedAt: string;
  totalAmount: number;
  currencyCode: string;
}

/** Request body for POST /api/v1/debt/customer/uid/{uid}/credit-limit. */
export interface AdjustCreditLimitRequest {
  newLimit: string | number;
  reason?: string | null;
}

/** Wire shape of a chase note (PartyNoteDto). */
export interface PartyNote {
  id: string;
  uid: string;
  partyId: string;
  kind: PartyNoteKind;
  body: string;
  status: PartyNoteStatus;
  createdAt: string;
  createdBy: string | null;
  archivedAt: string | null;
  archivedBy: string | null;
}

/** Request body for POST /api/v1/debt/notes. */
export interface CreatePartyNoteRequest {
  customerUid: string;
  kind: PartyNoteKind;
  body: string;
}
