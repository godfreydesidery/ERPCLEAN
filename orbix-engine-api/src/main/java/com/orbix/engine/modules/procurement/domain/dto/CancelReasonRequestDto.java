package com.orbix.engine.modules.procurement.domain.dto;

import jakarta.validation.constraints.Size;

/**
 * Shared body for LPO cancel + GRN cancel-posted endpoints. The cancel-posted
 * path requires {@code reason}; the simpler cancel paths treat it as optional.
 * The required-ness lives at the controller (per-endpoint) so the same record
 * can serve both. Length capped to {@code cancellation_reason} column width.
 */
public record CancelReasonRequestDto(
    @Size(max = 500) String reason
) {}
