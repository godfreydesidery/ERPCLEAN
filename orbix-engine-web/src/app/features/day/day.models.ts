/** Mirrors the backend BusinessDayController DTOs (Slice D — uid-keyed). */

export type BusinessDayStatus = 'OPEN' | 'CLOSING' | 'CLOSED';

/**
 * Composite-PK aggregate per ADR 0002. The composite (branchId, businessDate)
 * stays on the wire; uid is the external URL handle. No surrogate `id` field.
 */
export interface BusinessDay {
  uid: string;
  branchId: string;
  businessDate: string;
  status: BusinessDayStatus;
  openedAt: string;
  openedBy: string;
  closedAt: string | null;
  closedBy: string | null;
  eodReportObjectKey: string | null;
}

/**
 * Surrogate-Long PK aggregate, so the DTO carries both id (numeric handle,
 * stringified on the wire) and uid (external URL handle). Archive lifecycle:
 * the override can be voided before its back-dated post lands; once stamped,
 * archivedAt and archivedBy describe who/when reverted it.
 */
export interface BusinessDayOverride {
  uid: string;
  id: string;
  branchId: string;
  targetBusinessDate: string;
  entityType: string;
  entityId: string;
  reason: string;
  authorisedBy: string;
  at: string;
  archivedAt: string | null;
  archivedBy: string | null;
}

export interface PostBusinessDayOverrideRequest {
  entityType: string;
  entityId: string;
  reason: string;
}
