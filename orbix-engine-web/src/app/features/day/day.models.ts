/** Mirrors the backend BusinessDayController DTOs. */

export type BusinessDayStatus = 'OPEN' | 'CLOSING' | 'CLOSED';

export interface BusinessDay {
  branchId: string;
  businessDate: string;
  status: BusinessDayStatus;
  openedAt: string;
  openedBy: string;
  closedAt: string | null;
  closedBy: string | null;
  eodReportObjectKey: string | null;
}
