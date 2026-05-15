package com.orbix.engine.modules.pos.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Batched POS-sale push (F5.4). The till's offline outbox sends N pending
 * sales in one call when the network returns. Per-item processing is isolated:
 * one failing sale does NOT roll back the others — the response carries a
 * per-item accepted / rejected verdict.
 */
public record SyncPushRequestDto(
    @NotEmpty @Valid List<PostPosSaleRequestDto> sales
) {}
