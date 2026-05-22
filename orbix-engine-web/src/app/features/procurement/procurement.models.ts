/** Mirrors the backend procurement DTOs. */

export type LpoOrderStatus =
  'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED';

export interface LpoOrderLine {
  id: string;
  lineNo: number;
  itemId: string;
  uomId: string;
  orderedQty: number;
  receivedQty: number;
  unitPrice: number;
  vatGroupId: string;
  discountPct: number;
  lineTotal: number;
}

export interface LpoOrder {
  id: string;
  number: string;
  companyId: string;
  branchId: string;
  supplierId: string;
  orderDate: string;
  expectedDeliveryDate: string | null;
  currencyCode: string;
  subtotalAmount: number;
  taxAmount: number;
  totalAmount: number;
  status: LpoOrderStatus;
  approvedBy: string | null;
  approvedAt: string | null;
  notes: string | null;
  lines: LpoOrderLine[];
}

export interface CreateLpoLine {
  itemId: string;
  uomId: string | null;
  orderedQty: number;
  unitPrice: number;
  vatGroupId: string | null;
  discountPct: number | null;
}

export interface CreateLpoOrderRequest {
  number: string;
  branchId: string;
  supplierId: string;
  orderDate: string;
  expectedDeliveryDate: string | null;
  currencyCode: string;
  notes: string | null;
  lines: CreateLpoLine[];
}

export interface UpdateLpoOrderRequest {
  supplierId: string;
  orderDate: string;
  expectedDeliveryDate: string | null;
  currencyCode: string;
  notes: string | null;
  lines: CreateLpoLine[];
}

// ---- F3.2: GRN -----------------------------------------------------------

export type GrnStatus = 'DRAFT' | 'POSTED' | 'CANCELLED';

export interface GrnLine {
  id: string;
  lpoOrderLineId: string | null;
  itemId: string;
  uomId: string;
  receivedQty: number;
  unitCost: number;
  vatGroupId: string;
  lineTotal: number;
  batchNo: string | null;
  expiryDate: string | null;
}

export interface Grn {
  id: string;
  number: string;
  companyId: string;
  branchId: string;
  supplierId: string;
  lpoOrderId: string | null;
  receivedDate: string;
  supplierDeliveryNote: string | null;
  subtotalAmount: number;
  taxAmount: number;
  totalAmount: number;
  status: GrnStatus;
  postedAt: string | null;
  postedBy: string | null;
  notes: string | null;
  lines: GrnLine[];
}

export interface CreateGrnLine {
  lpoOrderLineId: string | null;
  itemId: string;
  uomId: string | null;
  receivedQty: number;
  unitCost: number;
  vatGroupId: string | null;
  batchNo: string | null;
  expiryDate: string | null;
}

export interface CreateGrnRequest {
  number: string;
  branchId: string;
  supplierId: string;
  lpoOrderId: string | null;
  receivedDate: string;
  supplierDeliveryNote: string | null;
  notes: string | null;
  lines: CreateGrnLine[];
}

// ---- F3.3: supplier invoice + 3-way match ----------------------------------

export type SupplierInvoiceStatus =
  'DRAFT' | 'POSTED' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';

export interface SupplierInvoiceAllocation {
  grnId: string;
  amount: number;
}

export interface SupplierInvoice {
  id: string;
  number: string;
  supplierInvoiceNo: string;
  companyId: string;
  branchId: string;
  supplierId: string;
  invoiceDate: string;
  dueDate: string;
  currencyCode: string;
  subtotalAmount: number;
  taxAmount: number;
  totalAmount: number;
  paidAmount: number;
  status: SupplierInvoiceStatus;
  postedAt: string | null;
  postedBy: string | null;
  notes: string | null;
  allocations: SupplierInvoiceAllocation[];
}

export interface CreateSupplierInvoiceRequest {
  number: string;
  supplierInvoiceNo: string;
  branchId: string;
  supplierId: string;
  invoiceDate: string;
  dueDate: string | null;
  currencyCode: string;
  subtotalAmount: number;
  taxAmount: number;
  notes: string | null;
  allocations: SupplierInvoiceAllocation[];
}

// ---- F3.4: supplier payment + settlement -----------------------------------

export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'CHEQUE' | 'MOBILE_MONEY';
export const PAYMENT_METHODS: PaymentMethod[] =
  ['CASH', 'BANK_TRANSFER', 'CHEQUE', 'MOBILE_MONEY'];

export type SupplierPaymentStatus = 'DRAFT' | 'POSTED' | 'CANCELLED';

export interface SupplierPaymentAllocation {
  id: string;
  supplierInvoiceId: string;
  amount: number;
}

export interface SupplierPayment {
  id: string;
  number: string;
  companyId: string;
  branchId: string;
  supplierId: string;
  paymentDate: string;
  method: PaymentMethod;
  reference: string | null;
  currencyCode: string;
  totalAmount: number;
  allocatedAmount: number;
  status: SupplierPaymentStatus;
  postedAt: string | null;
  postedBy: string | null;
  notes: string | null;
  allocations: SupplierPaymentAllocation[];
}

export interface CreateSupplierPaymentAllocation {
  supplierInvoiceId: string;
  amount: number;
}

export interface CreateSupplierPaymentRequest {
  number: string;
  branchId: string;
  supplierId: string;
  paymentDate: string;
  method: PaymentMethod;
  reference: string | null;
  currencyCode: string;
  totalAmount: number;
  notes: string | null;
  allocations: CreateSupplierPaymentAllocation[];
}
