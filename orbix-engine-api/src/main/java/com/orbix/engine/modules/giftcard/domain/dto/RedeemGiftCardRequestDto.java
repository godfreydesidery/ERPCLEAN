package com.orbix.engine.modules.giftcard.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Redeem payload (F7.1 / US-GC-003). The {@code (refDocType, refDocId)}
 * triple drives idempotency — the POS sends {@code refDocType = PosSale}
 * and {@code refDocId = posSaleId}; a network retry hits the same
 * idempotency key and the prior {@code REDEEM} txn is returned without
 * double-debiting.
 */
public record RedeemGiftCardRequestDto(
    @NotNull @Positive BigDecimal amount,
    @NotNull String refDocType,
    @NotNull Long refDocId
) {}
