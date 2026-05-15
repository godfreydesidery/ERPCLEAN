/** Mirrors the backend POS DTOs (F5.2). Read-only on the web side; pushes
 *  come from the Flutter POS app. */

export type PosSaleStatus = 'POSTED' | 'VOIDED';
export type PosSaleKind = 'SALE' | 'REFUND' | 'NO_SALE';
export type PosPaymentMethod = 'CASH' | 'CARD' | 'MOBILE_MONEY' | 'VOUCHER' | 'STORE_CREDIT';

export interface PosSaleLine {
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

export interface PosPayment {
  id: number;
  method: PosPaymentMethod;
  amount: number;
  reference: string | null;
  terminalId: string | null;
  last4: string | null;
}

export interface PosSale {
  id: number;
  number: string;
  clientOpId: string;
  tillSessionId: number;
  tillId: number;
  branchId: number;
  companyId: number;
  sectionId: number;
  customerId: number;
  cashierId: number;
  supervisorId: number | null;
  kind: PosSaleKind;
  refundedFromSaleId: number | null;
  saleAt: string;
  serverAt: string;
  businessDate: string;
  subtotalAmount: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  tenderedAmount: number;
  changeAmount: number;
  status: PosSaleStatus;
  voidedAt: string | null;
  voidedBy: number | null;
  voidReason: string | null;
  notes: string | null;
  lines: PosSaleLine[];
  payments: PosPayment[];
}
