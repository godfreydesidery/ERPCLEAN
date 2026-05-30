package com.orbix.engine.modules.pos.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Batched device-outbox push (US-POS-018). Replaces the old sales-only shape.
 * Each op is independently transacted; one REJECTED op never rolls back siblings.
 * HTTP 200 even when every op is REJECTED — read the per-op verdict array.
 * Design: docs/design/slice-sync-spine.md §2.2.
 *
 * <p>Batch size is capped at {@code orbix.sync.push-batch-max} (default 500 ops).
 */
public record SyncPushRequestDto(
    /** Logical device identifier, audit-only. Tenancy is derived from the JWT, never this field. */
    @NotBlank String deviceId,
    /** Contract major the client was built against. Also sent as {@code X-Orbix-Contract-Version} header. */
    @NotNull @Positive Integer clientContractVersion,
    /** Ordered batch of queued operations. Server applies each in its own REQUIRED tx. */
    @NotEmpty @Valid List<SyncOpDto> ops
) {}
