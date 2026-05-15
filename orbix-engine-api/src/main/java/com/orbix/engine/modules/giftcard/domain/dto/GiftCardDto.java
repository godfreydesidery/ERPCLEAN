package com.orbix.engine.modules.giftcard.domain.dto;

import com.orbix.engine.modules.giftcard.domain.entity.GiftCard;
import com.orbix.engine.modules.giftcard.domain.enums.GiftCardStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record GiftCardDto(
    Long id,
    String code,
    Long companyId,
    Long issuedByBranchId,
    Long issuedByUserId,
    BigDecimal initialValue,
    BigDecimal currentBalance,
    String currencyCode,
    GiftCardStatus status,
    Instant issuedAt,
    Instant expiresAt
) {
    public static GiftCardDto from(GiftCard card) {
        return new GiftCardDto(
            card.getId(), card.getCode(), card.getCompanyId(),
            card.getIssuedByBranchId(), card.getIssuedByUserId(),
            card.getInitialValue(), card.getCurrentBalance(), card.getCurrencyCode(),
            card.getStatus(), card.getIssuedAt(), card.getExpiresAt());
    }
}
