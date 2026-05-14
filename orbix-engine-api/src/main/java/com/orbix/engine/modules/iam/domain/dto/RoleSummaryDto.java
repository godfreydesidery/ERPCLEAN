package com.orbix.engine.modules.iam.domain.dto;

import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.iam.domain.entity.Role;

/** Lightweight role row for the role list screen. */
public record RoleSummaryDto(
    Long id,
    String code,
    String name,
    String description,
    boolean isSystem,
    AdminStatus status,
    int permissionCount
) {
    public static RoleSummaryDto from(Role role) {
        return new RoleSummaryDto(
            role.getId(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.isSystem(),
            role.getStatus(),
            role.getPermissions().size()
        );
    }
}
