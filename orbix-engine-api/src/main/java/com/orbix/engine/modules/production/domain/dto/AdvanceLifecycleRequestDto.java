package com.orbix.engine.modules.production.domain.dto;

import com.orbix.engine.modules.production.domain.enums.ProductionLifecycleState;
import jakarta.validation.constraints.NotNull;

/**
 * Advance-lifecycle payload (F7.3c / US-PROD-010). Forward-only except
 * {@code OUTPUT_DONATED} / {@code OUTPUT_WRITE_OFF} which are reachable
 * from any {@code OUTPUT_*} state. Optional {@code reason} surfaces on the
 * emitted event + audit trail.
 */
public record AdvanceLifecycleRequestDto(
    @NotNull ProductionLifecycleState targetState,
    String reason
) {}
