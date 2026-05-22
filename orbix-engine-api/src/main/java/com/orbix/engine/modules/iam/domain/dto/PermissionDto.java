package com.orbix.engine.modules.iam.domain.dto;

import com.orbix.engine.modules.iam.domain.entity.Permission;

/** A single grantable permission, as shown in the role-admin permission picker. */
public record PermissionDto(
    Long id,
    String code,
    String description,
    String module
) {
    public static PermissionDto from(Permission p) {
        return new PermissionDto(p.getId(), p.getCode(), p.getDescription(), p.getModule());
    }
}
