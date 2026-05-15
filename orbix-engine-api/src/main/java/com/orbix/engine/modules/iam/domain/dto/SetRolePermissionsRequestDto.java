package com.orbix.engine.modules.iam.domain.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Replaces a role's full permission set with the given permission ids. */
public record SetRolePermissionsRequestDto(
    @NotNull List<Long> permissionIds
) {}
