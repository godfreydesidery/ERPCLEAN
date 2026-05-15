package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.entity.PriceChangeLog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PriceChangeLogDto(
    Long id,
    Long priceListItemId,
    BigDecimal oldPrice,
    BigDecimal newPrice,
    LocalDate effectiveFrom,
    Instant changedAt,
    Long changedBy,
    String reason
) {
    public static PriceChangeLogDto from(PriceChangeLog log) {
        return new PriceChangeLogDto(
            log.getId(),
            log.getPriceListItemId(),
            log.getOldPrice(),
            log.getNewPrice(),
            log.getEffectiveFrom(),
            log.getChangedAt(),
            log.getChangedBy(),
            log.getReason()
        );
    }
}
