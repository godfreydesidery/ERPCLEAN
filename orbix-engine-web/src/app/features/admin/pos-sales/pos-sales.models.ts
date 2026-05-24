/** Mirrors the backend POS DTOs (F5.2). Read-only on the web side; pushes
 *  come from the Flutter POS app. */

export type PosSaleStatus = 'POSTED' | 'VOIDED';
export type PosSaleKind = 'SALE' | 'REFUND' | 'NO_SALE';
export type PosPaymentMethod = 'CASH' | 'CARD' | 'MOBILE_MONEY' | 'VOUCHER' | 'STORE_CREDIT';

export interface PosSaleLine {
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

export interface PosPayment {
  id: string;
  method: PosPaymentMethod;
  amount: number;
  reference: string | null;
  terminalId: string | null;
  last4: string | null;
}

export interface PosSale {
  id: string;
  uid: string;
  number: string;
  clientOpId: string;
  tillSessionId: string;
  tillId: string;
  branchId: string;
  companyId: string;
  sectionId: string;
  customerId: string;
  cashierId: string;
  supervisorId: string | null;
  kind: PosSaleKind;
  refundedFromSaleId: string | null;
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
  voidedBy: string | null;
  voidReason: string | null;
  notes: string | null;
  lines: PosSaleLine[];
  payments: PosPayment[];
}
