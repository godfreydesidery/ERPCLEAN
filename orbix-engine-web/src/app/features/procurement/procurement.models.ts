/** Mirrors the backend procurement DTOs. */

export type LpoOrderStatus =
  'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED';

export interface LpoOrderLine {
  id: number;
  lineNo: number;
  itemId: number;
  uomId: number;
  orderedQty: number;
  receivedQty: number;
  unitPrice: number;
  vatGroupId: number;
  discountPct: number;
  lineTotal: number;
}

export interface LpoOrder {
  id: number;
  number: string;
  companyId: number;
  branchId: number;
  supplierId: number;
  orderDate: string;
  expectedDeliveryDate: string | null;
  currencyCode: string;
  subtotalAmount: number;
  taxAmount: number;
  totalAmount: number;
  status: LpoOrderStatus;
  approvedBy: number | null;
  approvedAt: string | null;
  notes: string | null;
  lines: LpoOrderLine[];
}

export interface CreateLpoLine {
  itemId: number;
  uomId: number | null;
  orderedQty: number;
  unitPrice: number;
  vatGroupId: number | null;
  discountPct: number | null;
}

export interface CreateLpoOrderRequest {
  number: string;
  branchId: number;
  supplierId: number;
  orderDate: string;
  expectedDeliveryDate: string | null;
  currencyCode: string;
  notes: string | null;
  lines: CreateLpoLine[];
}

export interface UpdateLpoOrderRequest {
  supplierId: number;
  orderDate: string;
  expectedDeliveryDate: string | null;
  currencyCode: string;
  notes: string | null;
  lines: CreateLpoLine[];
}

// ---- F3.2: GRN -----------------------------------------------------------

export type GrnStatus = 'DRAFT' | 'POSTED' | 'CANCELLED';

export interface GrnLine {
  id: number;
  lpoOrderLineId: number | null;
  itemId: number;
  uomId: number;
  receivedQty: number;
  unitCost: number;
  vatGroupId: number;
  lineTotal: number;
  batchNo: string | null;
  expiryDate: string | null;
}

export interface Grn {
  id: number;
  number: string;
  companyId: number;
  branchId: number;
  supplierId: number;
  lpoOrderId: number | null;
  receivedDate: string;
  supplierDeliveryNote: string | null;
  subtotalAmount: number;
  taxAmount: number;
  totalAmount: number;
  status: GrnStatus;
  postedAt: string | null;
  postedBy: number | null;
  notes: string | null;
  lines: GrnLine[];
}

export interface CreateGrnLine {
  lpoOrderLineId: number | null;
  itemId: number;
  uomId: number | null;
  receivedQty: number;
  unitCost: number;
  vatGroupId: number | null;
  batchNo: string | null;
  expiryDate: string | null;
}

export interface CreateGrnRequest {
  number: string;
  branchId: number;
  supplierId: number;
  lpoOrderId: number | null;
  receivedDate: string;
  supplierDeliveryNote: string | null;
  notes: string | null;
  lines: CreateGrnLine[];
}

// ---- F3.3: supplier invoice + 3-way match ----------------------------------

export type SupplierInvoiceStatus =
  'DRAFT' | 'POSTED' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';

export interface SupplierInvoiceAllocation {
  grnId: number;
  amount: number;
}

export interface SupplierInvoice {
  id: number;
  number: string;
  supplierInvoiceNo: string;
  companyId: number;
  branchId: number;
  supplierId: number;
  invoiceDate: string;
  dueDate: string;
  currencyCode: string;
  subtotalAmount: number;
  taxAmount: number;
  totalAmount: number;
  paidAmount: number;
  status: SupplierInvoiceStatus;
  postedAt: string | null;
  postedBy: number | null;
  notes: string | null;
  allocations: SupplierInvoiceAllocation[];
}

export interface CreateSupplierInvoiceRequest {
  number: string;
  supplierInvoiceNo: string;
  branchId: number;
  supplierId: number;
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
  id: number;
  supplierInvoiceId: number;
  amount: number;
}

export interface SupplierPayment {
  id: number;
  number: string;
  companyId: number;
  branchId: number;
  supplierId: number;
  paymentDate: string;
  method: PaymentMethod;
  reference: string | null;
  currencyCode: string;
  totalAmount: number;
  allocatedAmount: number;
  status: SupplierPaymentStatus;
  postedAt: string | null;
  postedBy: number | null;
  notes: string | null;
  allocations: SupplierPaymentAllocation[];
}

export interface CreateSupplierPaymentAllocation {
  supplierInvoiceId: number;
  amount: number;
}

export interface CreateSupplierPaymentRequest {
  number: string;
  branchId: number;
  supplierId: number;
  paymentDate: string;
  method: PaymentMethod;
  reference: string | null;
  currencyCode: string;
  totalAmount: number;
  notes: string | null;
  allocations: CreateSupplierPaymentAllocation[];
}
