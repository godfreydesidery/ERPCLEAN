package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.FxRateDto;
import com.orbix.engine.modules.admin.domain.dto.QuoteFxRateRequestDto;
import com.orbix.engine.modules.admin.domain.entity.FxRate;
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
        return rates.findAllByOrderByEffectiveAtDesc().stream()
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
        if (!currencies.existsById(from)) {
            throw new IllegalArgumentException("Unknown currency: " + from);
        }
        if (!currencies.existsById(to)) {
            throw new IllegalArgumentException("Unknown currency: " + to);
        }

        FxRate rate = rates.save(new FxRate(from, to, request.rate(),
            request.effectiveAt(), context.userId()));
        return FxRateDto.from(rate);
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
