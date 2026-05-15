package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.pos.domain.entity.Till;
import com.orbix.engine.modules.pos.domain.enums.TillStatus;

public record TillDto(
    Long id,
    Long companyId,
    Long branchId,
    String code,
    String name,
    String installId,
    Long defaultPriceListId,
    TillStatus status
) {
    public static TillDto from(Till t) {
        return new TillDto(t.getId(), t.getCompanyId(), t.getBranchId(), t.getCode(),
            t.getName(), t.getInstallId(), t.getDefaultPriceListId(), t.getStatus());
    }
}
