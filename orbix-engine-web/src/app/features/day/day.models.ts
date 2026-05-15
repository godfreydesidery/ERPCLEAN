/** Mirrors the backend BusinessDayController DTOs. */

export type BusinessDayStatus = 'OPEN' | 'CLOSING' | 'CLOSED';

export interface BusinessDay {
  branchId: number;
  businessDate: string;
  status: BusinessDayStatus;
  openedAt: string;
  openedBy: number;
  closedAt: string | null;
  closedBy: number | null;
  eodReportObjectKey: string | null;
}
