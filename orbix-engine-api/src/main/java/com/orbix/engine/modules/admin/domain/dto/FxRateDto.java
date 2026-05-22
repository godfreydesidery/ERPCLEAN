package com.orbix.engine.modules.admin.domain.dto;

import com.orbix.engine.modules.admin.domain.entity.FxRate;

import java.math.BigDecimal;
import java.time.Instant;

/** A quoted FX rate row, as shown in the rate-history table. */
public record FxRateDto(
    Long id,
    String fromCurrency,
    String toCurrency,
    BigDecimal rate,
    Instant effectiveAt
) {
    public static FxRateDto from(FxRate rate) {
        return new FxRateDto(
            rate.getId(),
            rate.getFromCurrency(),
            rate.getToCurrency(),
            rate.getRate(),
            rate.getEffectiveAt()
        );
    }
}
