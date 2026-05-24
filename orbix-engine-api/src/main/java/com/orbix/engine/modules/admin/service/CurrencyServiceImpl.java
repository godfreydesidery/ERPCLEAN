package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CreateCurrencyRequestDto;
import com.orbix.engine.modules.admin.domain.dto.CurrencyDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateCurrencyRequestDto;
import com.orbix.engine.modules.admin.domain.entity.Currency;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
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
    private final CompanyRepository companies;

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
    @Auditable(action = "UPDATE", entityType = "Currency")
    public CurrencyDto updateCurrency(String code, UpdateCurrencyRequestDto request) {
        Currency currency = require(code);
        currency.updateDetails(request.name().trim(),
            request.symbol() == null ? null : request.symbol().trim(),
            request.minorUnitDigits());
        return CurrencyDto.from(currencies.save(currency));
    }

    @Override
    @Transactional
    @Auditable(action = "ENABLE", entityType = "Currency")
    public CurrencyDto enableCurrency(String code) {
        Currency currency = require(code);
        currency.setStatus(AdminStatus.ACTIVE);
        return CurrencyDto.from(currencies.save(currency));
    }

    @Override
    @Transactional
    @Auditable(action = "DISABLE", entityType = "Currency")
    public CurrencyDto disableCurrency(String code) {
        Currency currency = require(code);
        // A functional currency (any company transacts in it) must stay enabled —
        // disabling it would strip it from the pickers that depend on it.
        if (companies.existsByCurrencyCode(currency.getCode())) {
            throw new IllegalArgumentException(
                "Cannot disable the functional currency: " + currency.getCode());
        }
        currency.setStatus(AdminStatus.INACTIVE);
        return CurrencyDto.from(currencies.save(currency));
    }

    private Currency require(String code) {
        return currencies.findById(code.trim().toUpperCase())
            .orElseThrow(() -> new NoSuchElementException("Currency not found: " + code));
    }
}
