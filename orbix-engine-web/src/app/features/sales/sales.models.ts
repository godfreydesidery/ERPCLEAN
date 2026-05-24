/** Mirrors the backend sales DTOs. */

export type PaymentTerms = 'CASH' | 'CREDIT';
export const PAYMENT_TERMS: PaymentTerms[] = ['CASH', 'CREDIT'];

export type SalesInvoiceStatus =
  'DRAFT' | 'POSTED' | 'PARTIALLY_PAID' | 'PAID' | 'VOIDED' | 'CANCELLED';

export interface SalesInvoiceLine {
  id: string;
  lineNo: number;
  itemId: string;
  uomId: string;
  qty: number;
  unitPrice: number;
  discountPct: number;
  discountAmount: number;
  vatGroupId: string;
  taxAmount: number;
  lineTotal: number;
  costAmount: number;
}

export interface SalesInvoice {
  id: string;
  uid: string;
  number: string;
  companyId: string;
  branchId: string;
  customerId: string;
  salesAgentId: string | null;
  invoiceDate: string;
  dueDate: string | null;
  paymentTerms: PaymentTerms;
  currencyCode: string;
  priceListId: string;
  subtotalAmount: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  paidAmount: number;
  status: SalesInvoiceStatus;
  postedAt: string | null;
  postedBy: string | null;
  postedBusinessDate: string | null;
  voidedAt: string | null;
  voidedBy: string | null;
  voidReason: string | null;
  reference: string | null;
  notes: string | null;
  lines: SalesInvoiceLine[];
}

export interface CreateSalesInvoiceLine {
  itemId: string;
  uomId: string | null;
  qty: number;
  unitPrice: number;
  discountPct: number | null;
  vatGroupId: string | null;
}

export interface CreateSalesInvoiceRequest {
  number: string;
  branchId: string;
  customerId: string;
  salesAgentId: string | null;
  invoiceDate: string;
  dueDate: string | null;
  paymentTerms: PaymentTerms;
  currencyCode: string;
  priceListId: string;
  discountApproverId: string | null;
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
  id: string;
  salesInvoiceId: string;
  amount: number;
  allocatedAt: string;
  allocatedBy: string;
}

export interface SalesReceipt {
  id: string;
  uid: string;
  number: string;
  companyId: string;
  branchId: string;
  customerId: string;
  receiptDate: string;
  method: ReceiptMethod;
  reference: string | null;
  currencyCode: string;
  totalAmount: number;
  allocatedAmount: number;
  unallocatedAmount: number;
  status: SalesReceiptStatus;
  postedAt: string | null;
  postedBy: string | null;
  notes: string | null;
  allocations: ReceiptAllocation[];
}

export interface CreateReceiptAllocation {
  salesInvoiceId: string;
  amount: number;
}

export interface CreateSalesReceiptRequest {
  number: string;
  branchId: string;
  customerId: string;
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
  id: string;
  lineNo: number;
  itemId: string;
  uomId: string;
  returnedQty: number;
  unitPrice: number;
  vatGroupId: string;
  taxAmount: number;
  lineTotal: number;
  originalLineId: string | null;
}

export interface CustomerReturn {
  id: string;
  uid: string;
  number: string;
  companyId: string;
  branchId: string;
  customerId: string;
  originalInvoiceId: string | null;
  returnDate: string;
  reason: ReturnReason;
  totalAmount: number;
  status: CustomerReturnStatus;
  restock: boolean;
  postedAt: string | null;
  postedBy: string | null;
  notes: string | null;
  lines: CustomerReturnLine[];
}

export interface CreateCustomerReturnLine {
  itemId: string;
  uomId: string | null;
  returnedQty: number;
  unitPrice: number;
  vatGroupId: string | null;
  originalLineId: string | null;
}

export interface CreateCustomerReturnRequest {
  number: string;
  branchId: string;
  customerId: string;
  originalInvoiceId: string | null;
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
  id: string;
  uid: string;
  number: string;
  companyId: string;
  branchId: string;
  customerId: string;
  customerReturnId: string | null;
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
  id: string;
  salesInvoiceLineId: string;
  qty: number;
}

export interface PackingList {
  id: string;
  uid: string;
  number: string;
  companyId: string;
  branchId: string;
  salesInvoiceId: string;
  dispatchDate: string;
  driverName: string | null;
  vehicleNo: string | null;
  status: PackingListStatus;
  deliveredAt: string | null;
  deliveredBy: string | null;
  notes: string | null;
  lines: PackingListLine[];
}

export interface CreatePackingListLine {
  salesInvoiceLineId: string;
  qty: number;
}

export interface CreatePackingListRequest {
  number: string;
  branchId: string;
  salesInvoiceId: string;
  dispatchDate: string;
  driverName: string | null;
  vehicleNo: string | null;
  notes: string | null;
  lines: CreatePackingListLine[];
}
