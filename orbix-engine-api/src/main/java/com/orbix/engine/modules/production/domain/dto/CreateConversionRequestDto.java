package com.orbix.engine.modules.production.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create-conversion payload (F7.4 / US-PROD-007). {@code number} is optional —
 * service generates {@code CONV-BR{branchId}-{epoch-suffix}} when omitted.
 * {@code conversionDate} defaults to today. UoMs default to each item's
 * declared UoM.
 */
public record CreateConversionRequestDto(
    String number,
    @NotNull Long branchId,
    LocalDate conversionDate,
    @NotNull Long fromItemId,
    @NotNull @Positive BigDecimal fromQty,
    Long fromUomId,
    @NotNull Long toItemId,
    @NotNull @Positive BigDecimal toQty,
    Long toUomId,
    String reason
) {}
