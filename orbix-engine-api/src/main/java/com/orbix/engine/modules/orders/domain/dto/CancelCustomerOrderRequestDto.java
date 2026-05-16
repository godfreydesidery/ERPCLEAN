package com.orbix.engine.modules.orders.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cancel payload — reason required for audit. Refund vs forfeit follows the
 * configured {@code orbix.orders.cancel-refund-window-days} policy:
 * cancellations inside the window refund the full {@code paid_amount} on the
 * original tender(s); cancellations outside the window forfeit.
 */
public record CancelCustomerOrderRequestDto(
    @NotBlank String reason
) {}
