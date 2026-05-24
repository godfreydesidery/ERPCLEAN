package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.FxRateDto;
import com.orbix.engine.modules.admin.domain.dto.QuoteFxRateRequestDto;
import com.orbix.engine.modules.admin.domain.entity.Currency;
import com.orbix.engine.modules.admin.domain.entity.FxRate;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.repository.CurrencyRepository;
import com.orbix.engine.modules.admin.repository.FxRateRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FxRateServiceImpl implements FxRateService {

    private final FxRateRepository rates;
    private final CurrencyRepository currencies;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<FxRateDto> listRates() {
        return rates.findAllByOrderByEffectiveAtDescIdDesc().stream()
            .map(FxRateDto::from)
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "QUOTE", entityType = "FxRate")
    public FxRateDto quoteRate(QuoteFxRateRequestDto request) {
        String from = request.fromCurrency().trim().toUpperCase();
        String to = request.toCurrency().trim().toUpperCase();

        if (from.equals(to)) {
            throw new IllegalArgumentException("FX rate must be between two different currencies");
        }
        if (request.rate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("FX rate must be greater than zero");
        }
        // Rates are quoted between any two registered, active currencies — a
        // general currency-to-currency pair, not anchored to one functional
        // currency. POS picks the foreign -> functional pair it needs at tender.
        requireActive(from);
        requireActive(to);

        FxRate rate = rates.save(new FxRate(from, to, request.rate(),
            request.effectiveAt(), context.userId()));
        return FxRateDto.from(rate);
    }

    /** A rate may only be quoted between currencies that are registered and enabled. */
    private void requireActive(String code) {
        Currency currency = currencies.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("Unknown currency: " + code));
        if (currency.getStatus() != AdminStatus.ACTIVE) {
            throw new IllegalArgumentException("Currency is not active: " + code);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FxRateDto> effectiveRate(String fromCurrency, String toCurrency, Instant at) {
        return rates.findMostRecent(
                fromCurrency.trim().toUpperCase(),
                toCurrency.trim().toUpperCase(),
                at)
            .map(FxRateDto::from);
    }
}
