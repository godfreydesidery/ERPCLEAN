package com.orbix.engine.modules.admin.domain.dto;

import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.domain.enums.SectionType;

/** Section as returned by the admin section-management endpoints. */
public record SectionResponseDto(
    Long id,
    Long branchId,
    String code,
    String name,
    SectionType type,
    Long managerUserId,
    AdminStatus status
) {
    public static SectionResponseDto from(Section section) {
        return new SectionResponseDto(
            section.getId(),
            section.getBranchId(),
            section.getCode(),
            section.getName(),
            section.getType(),
            section.getManagerUserId(),
            section.getStatus()
        );
    }
}
