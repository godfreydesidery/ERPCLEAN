package com.orbix.engine.modules.iam.domain.dto;

/**
 * Response of {@code POST /users/{id}/reset-password}. Echoes the user
 * plus the plaintext temporary password ONCE (when generated server-side).
 */
public record ResetPasswordResponseDto(
    UserDetailDto user,
    String temporaryPassword
) {}
