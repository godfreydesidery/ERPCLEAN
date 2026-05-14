package com.orbix.engine.modules.admin.domain.dto;

import com.orbix.engine.modules.admin.domain.entity.Currency;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;

/** Currency as returned by the admin currency endpoints. */
public record CurrencyDto(
    String code,
    String name,
    String symbol,
    int minorUnitDigits,
    AdminStatus status
) {
    public static CurrencyDto from(Currency currency) {
        return new CurrencyDto(
            currency.getCode(),
            currency.getName(),
            currency.getSymbol(),
            currency.getMinorUnitDigits(),
            currency.getStatus()
        );
    }
}
