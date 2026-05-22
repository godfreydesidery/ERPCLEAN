package com.orbix.engine.modules.iam.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Grants a role to a user, scoped to the caller's current company.
 * {@code branchId} is optional — null means the grant applies company-wide.
 */
public record GrantRoleRequestDto(
    @NotBlank String username,
    Long branchId
) {}
