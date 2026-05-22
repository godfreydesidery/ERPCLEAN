package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CreateCurrencyRequestDto;
import com.orbix.engine.modules.admin.domain.dto.CurrencyDto;

import java.util.List;

/**
 * Currency reference-data administration (F1.2). Currencies are deployment-wide
 * (not company-scoped) and enabled / disabled rather than deleted.
 */
public interface CurrencyService {

    List<CurrencyDto> listCurrencies();

    CurrencyDto createCurrency(CreateCurrencyRequestDto request);

    CurrencyDto enableCurrency(String code);

    CurrencyDto disableCurrency(String code);
}
