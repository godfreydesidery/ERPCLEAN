package com.orbix.engine.modules.iam.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin payload for creating a new {@code app_user}. When {@code password}
 * is omitted the server generates a 12-character temporary password and
 * returns it once in {@link CreateUserResponseDto}.
 */
public record CreateUserRequestDto(
    @NotBlank @Size(min = 2, max = 80) String username,
    @NotBlank @Size(min = 2, max = 120) String displayName,
    @Size(max = 120) String email,
    @Size(max = 40) String phone,
    Long defaultBranchId,
    @Size(min = 8, max = 80) String password,
    Boolean mustChangePassword
) {}
