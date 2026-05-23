package com.orbix.engine.modules.common.domain.dto;

import com.orbix.engine.modules.common.domain.entity.AuditLog;

import java.time.Instant;

/**
 * Audit row as exposed by the audit viewer (US-IAM-013). The hash fields are
 * included so the UI can show chain provenance; Long ids serialise as strings
 * via the global JSON:API modifier.
 */
public record AuditLogDto(
    Long id,
    Instant at,
    Long actorId,
    String action,
    String entityType,
    String entityId,
    Long companyId,
    Long branchId,
    String beforeJson,
    String afterJson,
    String metaJson,
    String prevHash,
    String rowHash
) {
    public static AuditLogDto from(AuditLog a) {
        return new AuditLogDto(
            a.getId(), a.getAt(), a.getActorId(), a.getAction(), a.getEntityType(),
            a.getEntityId(), a.getCompanyId(), a.getBranchId(),
            a.getBeforeJson(), a.getAfterJson(), a.getMetaJson(),
            a.getPrevHash(), a.getRowHash());
    }
}
