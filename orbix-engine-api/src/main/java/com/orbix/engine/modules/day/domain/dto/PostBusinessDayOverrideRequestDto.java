package com.orbix.engine.modules.day.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Supervisor request to back-date a posting into a closed business day. The
 * back-dating producer (POS / sales / procurement) is identified by
 * {@code entityType} + {@code entityId}; the parent business day is addressed
 * by uid on the URL.
 */
public record PostBusinessDayOverrideRequestDto(
    @NotBlank @Size(max = 40) String entityType,
    @NotNull Long entityId,
    @NotBlank @Size(max = 4000) String reason
) {}
