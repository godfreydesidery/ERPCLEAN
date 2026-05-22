package com.orbix.engine.modules.admin.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/** Payload for quoting an FX rate. fx_rate is append-only — each quote is a new row. */
public record QuoteFxRateRequestDto(
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String fromCurrency,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String toCurrency,
    @NotNull @Positive BigDecimal rate,
    @NotNull Instant effectiveAt
) {}
