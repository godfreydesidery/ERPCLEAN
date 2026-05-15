package com.orbix.engine.modules.cash.domain.dto;

import com.orbix.engine.modules.cash.domain.entity.CashEntry;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CashEntryDto(
    Long id,
    Instant at,
    Long companyId,
    Long branchId,
    LocalDate businessDate,
    CashAccount account,
    CashDirection direction,
    BigDecimal amount,
    String currencyCode,
    String refType,
    Long refId,
    GlCategory glCategory,
    String notes,
    Long actorId
) {
    public static CashEntryDto from(CashEntry entry) {
        return new CashEntryDto(
            entry.getId(), entry.getAt(), entry.getCompanyId(), entry.getBranchId(),
            entry.getBusinessDate(), entry.getAccount(), entry.getDirection(),
            entry.getAmount(), entry.getCurrencyCode(), entry.getRefType(),
            entry.getRefId(), entry.getGlCategory(), entry.getNotes(), entry.getActorId());
    }
}
