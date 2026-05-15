/** Mirrors the backend POS DTOs (F5.1). */

export type TillStatus = 'ACTIVE' | 'INACTIVE';
export type TillSessionStatus = 'OPEN' | 'CLOSED' | 'RECONCILED';

export interface Till {
  id: number;
  companyId: number;
  branchId: number;
  code: string;
  name: string;
  installId: string | null;
  defaultPriceListId: number;
  status: TillStatus;
}

export interface CreateTillRequest {
  branchId: number;
  code: string;
  name: string;
  defaultPriceListId: number;
  installId: string | null;
}

export interface UpdateTillRequest {
  name: string;
  defaultPriceListId: number;
  installId: string | null;
}

export interface TillSession {
  id: number;
  tillId: number;
  branchId: number;
  companyId: number;
  businessDate: string;
  openedBy: number;
  openedAt: string;
  openingFloatAmount: number;
  closedBy: number | null;
  closedAt: string | null;
  expectedCashAmount: number | null;
  declaredCashAmount: number | null;
  varianceAmount: number | null;
  supervisorId: number | null;
  status: TillSessionStatus;
  notes: string | null;
}

export interface OpenTillSessionRequest {
  tillId: number;
  openingFloatAmount: number;
}

export interface CloseTillSessionRequest {
  declaredCashAmount: number;
  supervisorId: number | null;
  notes: string | null;
}
