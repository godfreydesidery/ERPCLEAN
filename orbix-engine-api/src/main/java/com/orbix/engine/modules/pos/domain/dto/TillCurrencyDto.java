package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.pos.domain.entity.TillCurrency;

import java.time.Instant;

public record TillCurrencyDto(
    Long tillId,
    String currencyCode,
    Instant createdAt,
    Long createdBy
) {
    public static TillCurrencyDto from(TillCurrency tc) {
        return new TillCurrencyDto(
            tc.getId().getTillId(),
            tc.getId().getCurrencyCode(),
            tc.getCreatedAt(),
            tc.getCreatedBy()
        );
    }
}
