package com.orbix.engine.modules.auth.domain.dto;

import java.time.Instant;

/**
 * A live login session, surfaced for the "log out everywhere" screen
 * (US-IAM-003). Backed by an active (non-revoked, unexpired) refresh token.
 * The raw token is never exposed — only its metadata.
 */
public record SessionDto(
    Long id,
    String clientInstallId,
    Instant issuedAt,
    Instant expiresAt
) {}
