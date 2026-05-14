package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CreateCurrencyRequestDto;
import com.orbix.engine.modules.admin.domain.dto.CurrencyDto;
import com.orbix.engine.modules.admin.domain.entity.Currency;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.repository.CurrencyRepository;
import com.orbix.engine.modules.common.service.Auditable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class CurrencyServiceImpl implements CurrencyService {

    private final CurrencyRepository currencies;

    @Override
    @Transactional(readOnly = true)
    public List<CurrencyDto> listCurrencies() {
        return currencies.findAll().stream()
            .sorted(Comparator.comparing(Currency::getCode))
            .map(CurrencyDto::from)
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Currency")
    public CurrencyDto createCurrency(CreateCurrencyRequestDto request) {
        String code = request.code().trim().toUpperCase();
        if (currencies.existsById(code)) {
            throw new IllegalArgumentException("Currency already exists: " + code);
        }
        Currency currency = new Currency(code, request.name(), request.symbol(),
            request.minorUnitDigits());
        return CurrencyDto.from(currencies.save(currency));
    }

    @Override
    @Transactional
    @Auditable(action = "ENABLE", entityType = "Currency")
    public CurrencyDto enableCurrency(String code) {
        return setStatus(code, AdminStatus.ACTIVE);
    }

    @Override
    @Transactional
    @Auditable(action = "DISABLE", entityType = "Currency")
    public CurrencyDto disableCurrency(String code) {
        return setStatus(code, AdminStatus.INACTIVE);
    }

    private CurrencyDto setStatus(String code, AdminStatus status) {
        Currency currency = currencies.findById(code.trim().toUpperCase())
            .orElseThrow(() -> new NoSuchElementException("Currency not found: " + code));
        currency.setStatus(status);
        return CurrencyDto.from(currencies.save(currency));
    }
}
