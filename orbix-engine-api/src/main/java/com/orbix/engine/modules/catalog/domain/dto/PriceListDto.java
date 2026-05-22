package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.entity.PriceList;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;

import java.time.LocalDate;

public record PriceListDto(
    Long id,
    String uid,
    Long companyId,
    String code,
    String name,
    String currencyCode,
    LocalDate validFrom,
    LocalDate validTo,
    boolean isDefault,
    boolean taxInclusive,
    ItemStatus status
) {
    public static PriceListDto from(PriceList list) {
        return new PriceListDto(
            list.getId(),
            list.getUid(),
            list.getCompanyId(),
            list.getCode(),
            list.getName(),
            list.getCurrencyCode(),
            list.getValidFrom(),
            list.getValidTo(),
            list.isDefault(),
            list.isTaxInclusive(),
            list.getStatus()
        );
    }
}
