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
