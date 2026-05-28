/** Mirrors the backend procurement DTOs. */

// ---- Item lookup (typeahead) -----------------------------------------------

/**
 * Lightweight item projection returned by GET /api/v1/items?q=...
 * Mirrors the fields the item typeahead and line autopopulation need.
 */
export interface ItemSummary {
  id: string;
  uid: string;
  code: string;
  name: string;
  /** uid of the item's default UoM — used to pre-select the UoM dropdown. */
  defaultUomUid: string | null;
  defaultUomCode: string | null;
  /** uid of the item's default VAT group — used to pre-select the VAT dropdown. */
  defaultVatGroupUid: string | null;
}

// ---- Supplier lookup (typeahead) ------------------------------------------

export interface SupplierSummary {
  /** Numeric PK serialised as string (Jackson global Long-as-string). */
  id: string;
  /** The supplier's party uid — submit this in write payloads. */
  partyUid: string;
  code: string;
  name: string;
}

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
  uid: string;
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
  cancellationReason: string | null;
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
  itemUid: string | null;
  itemName: string | null;
  itemCode: string | null;
  uomId: string;
  uomUid: string | null;
  uomCode: string | null;
  receivedQty: number;
  unitCost: number;
  vatGroupId: string;
  vatGroupUid: string | null;
  vatGroupName: string | null;
  lineTotal: number;
  batchNo: string | null;
  expiryDate: string | null;
}

export interface Grn {
  id: string;
  uid: string;
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
  cancellationReason: string | null;
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
  uid: string;
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
  uid: string;
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

// ---- Slice H.1: vendor returns + vendor credit notes -----------------------

export type VendorReturnStatus = 'DRAFT' | 'POSTED' | 'CREDITED';
export type VendorCreditNoteStatus = 'POSTED' | 'PARTIALLY_ALLOCATED' | 'FULLY_ALLOCATED';
export type ReturnReason = 'DAMAGED' | 'WRONG_ITEM' | 'EXPIRED' | 'OTHER';

export const VENDOR_RETURN_REASONS: ReturnReason[] = ['DAMAGED', 'WRONG_ITEM', 'EXPIRED', 'OTHER'];

export interface VendorReturn {
  id: string; uid: string; number: string;
  supplierId: string; supplierUid: string | null;
  originalGrnId: string | null; originalGrnNumber: string | null;
  originalSupplierInvoiceId: string | null;
  returnDate: string;
  reason: ReturnReason; restock: boolean;
  totalAmount: number; status: VendorReturnStatus;
  postedAt: string | null;
  notes: string | null;
  lines: VendorReturnLine[];
}

export interface VendorReturnLine {
  id: string;
  lineNo: number;
  itemId: string; itemName: string | null;
  uomId: string; uomCode: string | null;
  returnedQty: number; unitPrice: number;
  vatGroupId: string;
  taxAmount: number; lineTotal: number;
  originalLineId: string | null;
}

export interface VendorCreditNote {
  id: string; uid: string; number: string;
  supplierId: string; supplierUid: string | null;
  vendorReturnId: string | null;
  cnDate: string; currencyCode: string;
  totalAmount: number; allocatedAmount: number; availableAmount: number;
  status: VendorCreditNoteStatus;
  notes: string | null;
  allocations: VendorCreditNoteAllocation[] | null;
}

export interface VendorCreditNoteAllocation {
  id: string;
  supplierInvoiceId: string; supplierInvoiceNumber: string | null;
  amount: number;
  allocatedAt: string;
  allocatedBy: string | null;
}

export interface CreateVendorReturnRequest {
  supplierUid: string;
  originalGrnUid?: string;
  originalSupplierInvoiceUid?: string;
  returnDate: string;
  reason: ReturnReason;
  restock: boolean;
  notes?: string;
  lines: Array<{
    itemUid: string;
    uomUid: string;
    returnedQty: number;
    unitPrice: number;
    vatGroupUid: string;
    originalLineId?: string;
  }>;
}

export interface IssueVendorCreditNoteRequest {
  cnDate: string;
  notes?: string;
}

export interface ApplyVendorCreditNoteRequest {
  supplierInvoiceUid: string;
  amount: number;
}
