package com.orbix.engine.modules.auth.domain.dto;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    UserSummary user
) {
    public record UserSummary(
        Long id,
        String username,
        String displayName,
        Long defaultCompanyId,
        Long defaultBranchId
    ) {}
}
