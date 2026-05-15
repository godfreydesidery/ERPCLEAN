package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.pos.domain.dto.TillCurrencyDto;

import java.util.List;

/**
 * Manages the foreign currencies a till is allowed to accept as FX tender (F5.6).
 * The company's functional currency is implicit and not stored.
 * Gated by {@code POS.TILL_CURRENCY_MANAGE} at the controller.
 */
public interface TillCurrencyService {

    List<TillCurrencyDto> list(Long tillId);

    TillCurrencyDto add(Long tillId, String currencyCode);

    void remove(Long tillId, String currencyCode);
}
