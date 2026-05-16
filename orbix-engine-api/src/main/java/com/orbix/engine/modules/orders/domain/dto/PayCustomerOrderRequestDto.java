package com.orbix.engine.modules.orders.domain.dto;

import com.orbix.engine.modules.orders.domain.enums.OrderPaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Deposit / instalment / final payment payload.
 *
 * <ul>
 *   <li>{@code reference} is required for {@link OrderPaymentMethod#GIFT_CARD}
 *       — carries the gift-card code consumed via {@code GiftCardService.redeem}.</li>
 *   <li>{@code idempotencyKey} is optional but recommended — UNIQUE per
 *       {@code (order, key)} blocks accidental double-post on a client retry.</li>
 * </ul>
 */
public record PayCustomerOrderRequestDto(
    @NotNull @Positive BigDecimal amount,
    @NotNull OrderPaymentMethod method,
    String reference,
    String notes,
    String idempotencyKey
) {}
