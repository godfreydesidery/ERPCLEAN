package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TillSessionDto(
    Long id,
    Long tillId,
    Long branchId,
    Long companyId,
    LocalDate businessDate,
    Long openedBy,
    Instant openedAt,
    BigDecimal openingFloatAmount,
    Long closedBy,
    Instant closedAt,
    BigDecimal expectedCashAmount,
    BigDecimal declaredCashAmount,
    BigDecimal varianceAmount,
    Long supervisorId,
    TillSessionStatus status,
    String notes
) {
    public static TillSessionDto from(TillSession s) {
        return new TillSessionDto(s.getId(), s.getTillId(), s.getBranchId(), s.getCompanyId(),
            s.getBusinessDate(), s.getOpenedBy(), s.getOpenedAt(), s.getOpeningFloatAmount(),
            s.getClosedBy(), s.getClosedAt(), s.getExpectedCashAmount(),
            s.getDeclaredCashAmount(), s.getVarianceAmount(), s.getSupervisorId(),
            s.getStatus(), s.getNotes());
    }
}
