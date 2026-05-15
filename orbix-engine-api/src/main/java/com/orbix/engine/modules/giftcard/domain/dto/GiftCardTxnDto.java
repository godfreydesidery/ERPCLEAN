package com.orbix.engine.modules.giftcard.domain.dto;

import com.orbix.engine.modules.giftcard.domain.entity.GiftCardTxn;
import com.orbix.engine.modules.giftcard.domain.enums.GiftCardTxnKind;

import java.math.BigDecimal;
import java.time.Instant;

public record GiftCardTxnDto(
    Long id,
    Long giftCardId,
    GiftCardTxnKind kind,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String refDocType,
    Long refDocId,
    Instant occurredAt,
    Long byUserId
) {
    public static GiftCardTxnDto from(GiftCardTxn t) {
        return new GiftCardTxnDto(
            t.getId(), t.getGiftCardId(), t.getKind(), t.getAmount(), t.getBalanceAfter(),
            t.getRefDocType(), t.getRefDocId(), t.getOccurredAt(), t.getByUserId());
    }
}
