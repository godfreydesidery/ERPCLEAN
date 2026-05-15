package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VatGroupDto(
    Long id,
    Long companyId,
    String code,
    String name,
    BigDecimal rate,
    LocalDate validFrom,
    boolean isDefault,
    ItemStatus status
) {
    public static VatGroupDto from(VatGroup group) {
        return new VatGroupDto(
            group.getId(),
            group.getCompanyId(),
            group.getCode(),
            group.getName(),
            group.getRate(),
            group.getValidFrom(),
            group.isDefault(),
            group.getStatus()
        );
    }
}
