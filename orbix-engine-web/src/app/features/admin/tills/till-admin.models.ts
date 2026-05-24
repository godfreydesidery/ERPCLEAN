/** Mirrors the backend POS DTOs (F5.1). */

export type TillStatus = 'ACTIVE' | 'INACTIVE';
export type TillSessionStatus = 'OPEN' | 'CLOSED' | 'RECONCILED';

export interface Till {
  id: string;
  uid: string;
  companyId: string;
  branchId: string;
  code: string;
  name: string;
  installId: string | null;
  defaultPriceListId: string;
  status: TillStatus;
}

export interface CreateTillRequest {
  branchId: string;
  code: string;
  name: string;
  defaultPriceListId: string;
  installId: string | null;
}

export interface UpdateTillRequest {
  name: string;
  defaultPriceListId: string;
  installId: string | null;
}

export interface TillSession {
  id: string;
  uid: string;
  tillId: string;
  branchId: string;
  companyId: string;
  businessDate: string;
  openedBy: string;
  openedAt: string;
  openingFloatAmount: number;
  closedBy: string | null;
  closedAt: string | null;
  expectedCashAmount: number | null;
  declaredCashAmount: number | null;
  varianceAmount: number | null;
  supervisorId: string | null;
  status: TillSessionStatus;
  notes: string | null;
}

export interface OpenTillSessionRequest {
  tillId: string;
  openingFloatAmount: number;
}

export interface CloseTillSessionRequest {
  declaredCashAmount: number;
  supervisorId: string | null;
  notes: string | null;
}
