package com.orbix.engine.modules.production.domain.dto;

import com.orbix.engine.modules.production.domain.entity.Conversion;
import com.orbix.engine.modules.production.domain.enums.ConversionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ConversionDto(
    Long id,
    String uid,
    String number,
    Long companyId,
    Long branchId,
    LocalDate conversionDate,
    Long fromItemId,
    BigDecimal fromQty,
    Long fromUomId,
    Long toItemId,
    BigDecimal toQty,
    Long toUomId,
    BigDecimal unitCost,
    String reason,
    ConversionStatus status,
    Instant postedAt,
    Instant cancelledAt
) {
    public static ConversionDto from(Conversion c) {
        return new ConversionDto(
            c.getId(),
            c.getUid(),
            c.getNumber(),
            c.getCompanyId(),
            c.getBranchId(),
            c.getConversionDate(),
            c.getFromItemId(),
            c.getFromQty(),
            c.getFromUomId(),
            c.getToItemId(),
            c.getToQty(),
            c.getToUomId(),
            c.getUnitCost(),
            c.getReason(),
            c.getStatus(),
            c.getPostedAt(),
            c.getCancelledAt()
        );
    }
}
