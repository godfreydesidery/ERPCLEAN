// Mirrors the backend audit DTOs. Long ids arrive as strings (global JSON:API).
export interface AuditLogRow {
  id: string;
  at: string;
  actorId: string;
  action: string;
  entityType: string;
  entityId: string;
  companyId: string | null;
  branchId: string | null;
  beforeJson: string | null;
  afterJson: string | null;
  metaJson: string | null;
  prevHash: string;
  rowHash: string;
}

export interface AuditPage {
  content: AuditLogRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditIntegrityResult {
  ok: boolean;
  verifiedCount: number;
  firstBrokenId: string | null;
  message: string;
}

export interface AuditFilters {
  action?: string;
  entityType?: string;
  entityId?: string;
  actorId?: string;
  from?: string;
  to?: string;
}
