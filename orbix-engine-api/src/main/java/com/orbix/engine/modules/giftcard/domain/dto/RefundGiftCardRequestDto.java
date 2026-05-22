package com.orbix.engine.modules.giftcard.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Refund-credit payload (F7.1 / US-GC-004). Adds {@code amount} back to the
 * card balance and re-activates a FULLY_REDEEMED card. FROZEN and EXPIRED
 * cards are rejected.
 */
public record RefundGiftCardRequestDto(
    @NotNull @Positive BigDecimal amount,
    @NotNull String refDocType,
    @NotNull Long refDocId
) {}
