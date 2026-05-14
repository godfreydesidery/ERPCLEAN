package com.orbix.engine.modules.day.domain.dto;

import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.domain.enums.BusinessDayStatus;

import java.time.Instant;
import java.time.LocalDate;

public record BusinessDayDto(
    Long branchId,
    LocalDate businessDate,
    BusinessDayStatus status,
    Instant openedAt,
    Long openedBy,
    Instant closedAt,
    Long closedBy,
    String eodReportObjectKey
) {
    public static BusinessDayDto from(BusinessDay day) {
        return new BusinessDayDto(
            day.getBranchId(),
            day.getBusinessDate(),
            day.getStatus(),
            day.getOpenedAt(),
            day.getOpenedBy(),
            day.getClosedAt(),
            day.getClosedBy(),
            day.getEodReportObjectKey()
        );
    }
}
