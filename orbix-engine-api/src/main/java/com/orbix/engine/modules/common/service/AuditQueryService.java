package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.dto.AuditIntegrityResultDto;
import com.orbix.engine.modules.common.domain.dto.AuditPageDto;

import java.time.Instant;

/** Read-side of the audit log: filtered viewing (US-IAM-013) and chain integrity (US-IAM-014). */
public interface AuditQueryService {

    AuditPageDto search(Long actorId, String action, String entityType, String entityId,
                        Long branchId, Instant from, Instant to, int page, int size);

    AuditIntegrityResultDto verify(Instant from, Instant to);
}
