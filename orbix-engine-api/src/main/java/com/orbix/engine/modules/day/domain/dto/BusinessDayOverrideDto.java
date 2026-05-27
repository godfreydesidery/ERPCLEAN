package com.orbix.engine.modules.day.domain.dto;

import com.orbix.engine.modules.day.domain.entity.BusinessDayOverride;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Response shape for a {@link BusinessDayOverride}. Surrogate-Long PK
 * aggregate, so the DTO carries both {@code id} (numeric handle, stringified
 * on the wire by the global modifier) and {@code uid} (external URL handle).
 * Slice D — overrides have an archive lifecycle (void before the back-dated
 * post lands); {@code archivedAt} / {@code archivedBy} are stamped when the
 * supervisor cancels the grant.
 */
public record BusinessDayOverrideDto(
    String uid,
    Long id,
    Long branchId,
    LocalDate targetBusinessDate,
    String entityType,
    Long entityId,
    String reason,
    Long authorisedBy,
    Instant at,
    Instant archivedAt,
    Long archivedBy
) {
    public static BusinessDayOverrideDto from(BusinessDayOverride override) {
        return new BusinessDayOverrideDto(
            override.getUid(),
            override.getId(),
            override.getBranchId(),
            override.getTargetBusinessDate(),
            override.getEntityType(),
            override.getEntityId(),
            override.getReason(),
            override.getAuthorisedBy(),
            override.getAt(),
            override.getArchivedAt(),
            override.getArchivedBy()
        );
    }
}
