package com.orbix.engine.modules.cash.domain.dto;

import com.orbix.engine.modules.cash.domain.entity.CashAdjustment;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CashAdjustmentDto(
    Long id,
    Long companyId,
    Long branchId,
    LocalDate businessDate,
    CashAccount account,
    CashDirection direction,
    BigDecimal amount,
    String currencyCode,
    String reason,
    Instant at,
    Long postedBy
) {
    public static CashAdjustmentDto from(CashAdjustment row) {
        return new CashAdjustmentDto(
            row.getId(), row.getCompanyId(), row.getBranchId(), row.getBusinessDate(),
            row.getAccount(), row.getDirection(), row.getAmount(), row.getCurrencyCode(),
            row.getReason(), row.getAt(), row.getPostedBy());
    }
}
