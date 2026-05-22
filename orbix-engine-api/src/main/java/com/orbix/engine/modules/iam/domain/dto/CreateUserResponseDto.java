package com.orbix.engine.modules.iam.domain.dto;

/**
 * Response of {@code POST /users}. Echoes the new user plus, if the server
 * generated a temporary password, the plaintext value so the admin can
 * hand it to the user (shown ONCE — it's not retrievable after).
 */
public record CreateUserResponseDto(
    UserDetailDto user,
    String temporaryPassword
) {}
