/** Mirrors the backend sales DTOs. */

export type PaymentTerms = 'CASH' | 'CREDIT';
export const PAYMENT_TERMS: PaymentTerms[] = ['CASH', 'CREDIT'];

export type SalesInvoiceStatus =
  'DRAFT' | 'POSTED' | 'PARTIALLY_PAID' | 'PAID' | 'VOIDED' | 'CANCELLED';

export interface SalesInvoiceLine {
  id: number;
  lineNo: number;
  itemId: number;
  uomId: number;
  qty: number;
  unitPrice: number;
  discountPct: number;
  discountAmount: number;
  vatGroupId: number;
  taxAmount: number;
  lineTotal: number;
  costAmount: number;
}

export interface SalesInvoice {
  id: number;
  number: string;
  companyId: number;
  branchId: number;
  customerId: number;
  salesAgentId: number | null;
  invoiceDate: string;
  dueDate: string | null;
  paymentTerms: PaymentTerms;
  currencyCode: string;
  priceListId: number;
  subtotalAmount: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  paidAmount: number;
  status: SalesInvoiceStatus;
  postedAt: string | null;
  postedBy: number | null;
  postedBusinessDate: string | null;
  voidedAt: string | null;
  voidedBy: number | null;
  voidReason: string | null;
  reference: string | null;
  notes: string | null;
  lines: SalesInvoiceLine[];
}

export interface CreateSalesInvoiceLine {
  itemId: number;
  uomId: number | null;
  qty: number;
  unitPrice: number;
  discountPct: number | null;
  vatGroupId: number | null;
}

export interface CreateSalesInvoiceRequest {
  number: string;
  branchId: number;
  customerId: number;
  salesAgentId: number | null;
  invoiceDate: string;
  dueDate: string | null;
  paymentTerms: PaymentTerms;
  currencyCode: string;
  priceListId: number;
  discountApproverId: number | null;
  reference: string | null;
  notes: string | null;
  lines: CreateSalesInvoiceLine[];
}

export interface VoidSalesInvoiceRequest {
  reason: string;
}

// ---- F4.3: sales receipt ---------------------------------------------------

export type ReceiptMethod =
  'CASH' | 'CARD' | 'BANK_TRANSFER' | 'MOBILE_MONEY' | 'CHEQUE' | 'STORE_CREDIT';
export const RECEIPT_METHODS: ReceiptMethod[] =
  ['CASH', 'CARD', 'BANK_TRANSFER', 'MOBILE_MONEY', 'CHEQUE', 'STORE_CREDIT'];

export type SalesReceiptStatus = 'DRAFT' | 'POSTED' | 'CANCELLED';

export interface ReceiptAllocation {
  id: number;
  salesInvoiceId: number;
  amount: number;
  allocatedAt: string;
  allocatedBy: number;
}

export interface SalesReceipt {
  id: number;
  number: string;
  companyId: number;
  branchId: number;
  customerId: number;
  receiptDate: string;
  method: ReceiptMethod;
  reference: string | null;
  currencyCode: string;
  totalAmount: number;
  allocatedAmount: number;
  unallocatedAmount: number;
  status: SalesReceiptStatus;
  postedAt: string | null;
  postedBy: number | null;
  notes: string | null;
  allocations: ReceiptAllocation[];
}

export interface CreateReceiptAllocation {
  salesInvoiceId: number;
  amount: number;
}

export interface CreateSalesReceiptRequest {
  number: string;
  branchId: number;
  customerId: number;
  receiptDate: string;
  method: ReceiptMethod;
  reference: string | null;
  currencyCode: string;
  totalAmount: number;
  notes: string | null;
  allocations: CreateReceiptAllocation[];
}

// ---- F4.4: customer return + credit note -----------------------------------

export type ReturnReason = 'DAMAGED' | 'EXPIRED' | 'WRONG_ITEM' | 'BUYER_REMORSE' | 'OTHER';
export const RETURN_REASONS: ReturnReason[] = ['DAMAGED', 'EXPIRED', 'WRONG_ITEM', 'BUYER_REMORSE', 'OTHER'];

export type CustomerReturnStatus = 'DRAFT' | 'POSTED' | 'CREDITED' | 'CANCELLED';
export type CreditNoteStatus = 'POSTED' | 'FULLY_ALLOCATED' | 'CANCELLED';

export interface CustomerReturnLine {
  id: number;
  lineNo: number;
  itemId: number;
  uomId: number;
  returnedQty: number;
  unitPrice: number;
  vatGroupId: number;
  taxAmount: number;
  lineTotal: number;
  originalLineId: number | null;
}

export interface CustomerReturn {
  id: number;
  number: string;
  companyId: number;
  branchId: number;
  customerId: number;
  originalInvoiceId: number | null;
  returnDate: string;
  reason: ReturnReason;
  totalAmount: number;
  status: CustomerReturnStatus;
  restock: boolean;
  postedAt: string | null;
  postedBy: number | null;
  notes: string | null;
  lines: CustomerReturnLine[];
}

export interface CreateCustomerReturnLine {
  itemId: number;
  uomId: number | null;
  returnedQty: number;
  unitPrice: number;
  vatGroupId: number | null;
  originalLineId: number | null;
}

export interface CreateCustomerReturnRequest {
  number: string;
  branchId: number;
  customerId: number;
  originalInvoiceId: number | null;
  returnDate: string;
  reason: ReturnReason;
  restock: boolean;
  notes: string | null;
  lines: CreateCustomerReturnLine[];
}

export interface IssueCreditNoteRequest {
  number: string;
  notes: string | null;
}

export interface CustomerCreditNote {
  id: number;
  number: string;
  companyId: number;
  branchId: number;
  customerId: number;
  customerReturnId: number | null;
  cnDate: string;
  currencyCode: string;
  totalAmount: number;
  allocatedAmount: number;
  status: CreditNoteStatus;
  notes: string | null;
}

// ---- F4.5: packing list ----------------------------------------------------

export type PackingListStatus = 'DRAFT' | 'DISPATCHED' | 'DELIVERED' | 'CANCELLED';

export interface PackingListLine {
  id: number;
  salesInvoiceLineId: number;
  qty: number;
}

export interface PackingList {
  id: number;
  number: string;
  companyId: number;
  branchId: number;
  salesInvoiceId: number;
  dispatchDate: string;
  driverName: string | null;
  vehicleNo: string | null;
  status: PackingListStatus;
  deliveredAt: string | null;
  deliveredBy: number | null;
  notes: string | null;
  lines: PackingListLine[];
}

export interface CreatePackingListLine {
  salesInvoiceLineId: number;
  qty: number;
}

export interface CreatePackingListRequest {
  number: string;
  branchId: number;
  salesInvoiceId: number;
  dispatchDate: string;
  driverName: string | null;
  vehicleNo: string | null;
  notes: string | null;
  lines: CreatePackingListLine[];
}
