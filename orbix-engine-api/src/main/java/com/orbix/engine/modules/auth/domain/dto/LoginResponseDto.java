package com.orbix.engine.modules.auth.domain.dto;

public record LoginResponseDto(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresInSeconds,
    UserSummaryDto user
) {
    public record UserSummaryDto(
        Long id,
        String username,
        String displayName,
        Long defaultCompanyId,
        Long defaultBranchId
    ) {}
}
