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
