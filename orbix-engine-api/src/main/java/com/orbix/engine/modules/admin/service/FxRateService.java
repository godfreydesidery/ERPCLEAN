package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.FxRateDto;
import com.orbix.engine.modules.admin.domain.dto.QuoteFxRateRequestDto;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * FX rate administration (F1.2). The {@code fx_rate} table is append-only —
 * every quote is a new row; lookups pick the most recent rate effective at or
 * before a given time.
 */
public interface FxRateService {

    /** Full quote history, newest first. */
    List<FxRateDto> listRates();

    FxRateDto quoteRate(QuoteFxRateRequestDto request);

    /** The rate to apply for {@code from -> to} at the given instant, if one exists. */
    Optional<FxRateDto> effectiveRate(String fromCurrency, String toCurrency, Instant at);
}
