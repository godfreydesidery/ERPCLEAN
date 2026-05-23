package com.orbix.engine.modules.iam.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-service password change. Both current + new are mandatory. */
public record ChangePasswordRequestDto(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 10, max = 80) String newPassword
) {}
