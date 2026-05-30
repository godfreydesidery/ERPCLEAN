package com.orbix.engine.modules.pos.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * One queued device operation inside a {@link SyncPushRequestDto} batch.
 * {@code opType} is the discriminator; {@code payload} is op-type-specific
 * (e.g. {@link PostPosSaleRequestDto} shape for {@code POS_SALE}).
 * {@code dependsOn} points to the {@code clientOpId} of the prerequisite op;
 * absent or null means the op has no dependency.
 * Design: docs/design/slice-sync-spine.md §2.2.
 */
public record SyncOpDto(
    /** Crockford ULID (26 chars), client-generated, globally unique per op. */
    @NotBlank String clientOpId,
    /** Operation discriminator. */
    @NotNull String opType,
    /** Monotonic per-device sequence counter (advisory — ordering is dependsOn-driven). */
    @NotNull Long seq,
    /** Client wall-clock; server stamps its own server_at independently. */
    @NotNull Instant occurredAt,
    /** clientOpId that must be ACCEPTED/DUPLICATE first; null for no dependency. */
    String dependsOn,
    /** Op-type-specific payload (deserialized as a generic map for dispatch). */
    Map<String, Object> payload
) {}
