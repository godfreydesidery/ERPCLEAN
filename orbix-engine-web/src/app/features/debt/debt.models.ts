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

// ---------------------------------------------------------------------------
// Slice G.1 — Supplier-AP models (mirrors the AR side with supplier* names)
// All Long-id fields typed string (global IdLongAsStringSerializerModifier).
// ---------------------------------------------------------------------------

/** GET /api/v1/debt/supplier-aging → SupplierAgingDto. */
export interface SupplierAging {
  asOf: string;
  branchId: string | null;
  currencyCode: string;
  totals: SupplierAgingTotals;
  rows: SupplierAgingRow[];
}

export interface SupplierAgingTotals {
  current: number;
  d1_30: number;
  d31_60: number;
  d61_90: number;
  d90_plus: number;
  totalOutstanding: number;
  supplierCount: number;
}

export interface SupplierAgingRow {
  supplierId: string;
  supplierUid: string;
  supplierName: string;
  current: number;
  d1_30: number;
  d31_60: number;
  d61_90: number;
  d90_plus: number;
  totalOutstanding: number;
  oldestDaysOverdue: number | null;
  paymentTermsDays: number | null;
}

/** GET /api/v1/debt/supplier-dunning row → SupplierDunningQueueRowDto. */
export interface SupplierDunningQueueRow {
  supplierId: string;
  supplierUid: string;
  supplierName: string;
  paymentTermsDays: number | null;
  totalOutstanding: number;
  oldestDaysOverdue: number | null;
  oldestDueDate: string | null;
  worstBucket: AgingBucket;
  overdueInvoiceCount: number;
}

/** GET /api/v1/debt/supplier/uid/{uid} → SupplierStatementDto. */
export interface SupplierStatement {
  supplierId: string;
  supplierUid: string;
  supplierName: string;
  currencyCode: string;
  paymentTermsDays: number | null;
  totalOutstanding: number;
  openInvoiceCount: number;
  overdueInvoiceCount: number;
  asOf: string;
  openInvoices: OpenSupplierInvoiceRow[];
  recentPayments: RecentSupplierPaymentRow[];
}

export interface OpenSupplierInvoiceRow {
  invoiceId: string;
  invoiceUid: string;
  number: string;
  supplierInvoiceNo: string | null;
  invoiceDate: string;
  dueDate: string | null;
  totalAmount: number;
  paidAmount: number;
  outstanding: number;
  daysOverdue: number | null;
  status: string;
}

export interface RecentSupplierPaymentRow {
  paymentId: string;
  paymentUid: string;
  number: string;
  paymentDate: string;
  totalAmount: number;
  currencyCode: string;
}

// ---------------------------------------------------------------------------
// Slice G.2 — Debt write-off models
// All Long-id fields typed string (global IdLongAsStringSerializerModifier).
// ---------------------------------------------------------------------------

/** Lifecycle of a debt write-off request. */
export type DebtWriteOffStatus = 'PENDING_APPROVAL' | 'POSTED' | 'REJECTED';

/** Discriminator — which side of the ledger the write-off targets. */
export type DebtWriteOffTargetKind = 'CUSTOMER_INVOICE' | 'SUPPLIER_INVOICE';

/**
 * Wire shape of a {@code DebtWriteOffDto} returned by the backend.
 * Long-id fields ({@code id}, {@code targetInvoiceId}, {@code requestedByUserId},
 * {@code approvedByUserId}) serialise as JSON strings via the global
 * {@code IdLongAsStringSerializerModifier}.
 */
export interface DebtWriteOff {
  id: string;
  uid: string;
  targetKind: DebtWriteOffTargetKind;
  targetInvoiceId: string;
  targetInvoiceUid: string;
  targetInvoiceNumber: string | null;
  partyName: string | null;
  amount: number;
  currencyCode: string;
  reason: string;
  status: DebtWriteOffStatus;
  requestedByUserId: string;
  requestedByUsername: string | null;
  requestedAt: string;    // ISO-8601
  approvedByUserId: string | null;
  approvedByUsername: string | null;
  approvedAt: string | null;
  postedAt: string | null;
  rejectedAt: string | null;
  reasonForReject: string | null;
}

/** Request body for POST /api/v1/debt/write-offs. */
export interface CreateDebtWriteOffRequest {
  targetKind: DebtWriteOffTargetKind;
  targetInvoiceUid: string;
  amount: number;
  reason: string;
}

/** Request body for POST /api/v1/debt/write-offs/uid/{uid}/reject. */
export interface RejectDebtWriteOffRequest {
  reasonForReject: string;
}
