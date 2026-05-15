package com.orbix.engine.modules.giftcard.domain.dto;

import com.orbix.engine.modules.cash.domain.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Issue-card payload (F7.1 / US-GC-001). {@code code} is optional — when
 * null the service generates a random 12-digit numeric code; supplying a
 * pre-printed code lets the cashier issue a physical card from inventory.
 * {@code tenderMethod} drives the cash-side entry (no cash entry for CARD —
 * card settles via the card rail; gift-card-funded gift cards rejected at
 * MVP).
 */
public record IssueGiftCardRequestDto(
    @NotNull Long branchId,
    @NotNull @Positive BigDecimal initialValue,
    @NotNull PaymentMethod tenderMethod,
    @Size(max = 40) String code,
    Instant expiresAt
) {}
