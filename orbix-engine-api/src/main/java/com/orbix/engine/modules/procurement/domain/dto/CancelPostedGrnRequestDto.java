package com.orbix.engine.modules.procurement.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/grns/uid/{uid}/cancel-posted}. Reason is
 * mandatory — POSTED-cancel is a compensating action that must be auditable
 * with a human-readable justification.
 */
public record CancelPostedGrnRequestDto(
    @NotBlank @Size(max = 500) String reason
) {}
