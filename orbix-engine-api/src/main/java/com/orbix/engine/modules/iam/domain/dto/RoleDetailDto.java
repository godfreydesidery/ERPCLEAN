package com.orbix.engine.modules.iam.domain.dto;

import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.iam.domain.entity.Role;

import java.util.List;

/** Full role view including its granted permissions. */
public record RoleDetailDto(
    Long id,
    String uid,
    String code,
    String name,
    String description,
    boolean isSystem,
    AdminStatus status,
    List<PermissionDto> permissions
) {
    public static RoleDetailDto from(Role role) {
        List<PermissionDto> perms = role.getPermissions().stream()
            .map(PermissionDto::from)
            .sorted((a, b) -> a.code().compareTo(b.code()))
            .toList();
        return new RoleDetailDto(
            role.getId(),
            role.getUid(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.isSystem(),
            role.getStatus(),
            perms
        );
    }
}
