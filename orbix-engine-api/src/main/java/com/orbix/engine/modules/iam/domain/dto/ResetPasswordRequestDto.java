package com.orbix.engine.modules.iam.domain.dto;

import jakarta.validation.constraints.Size;

/**
 * Admin-issued password reset. When {@code newPassword} is omitted the
 * server generates a 12-character temporary password and returns it once.
 * {@code mustChangePassword} defaults to {@code true} so the user is forced
 * to set their own password on next login.
 */
public record ResetPasswordRequestDto(
    @Size(min = 8, max = 80) String newPassword,
    Boolean mustChangePassword
) {}
