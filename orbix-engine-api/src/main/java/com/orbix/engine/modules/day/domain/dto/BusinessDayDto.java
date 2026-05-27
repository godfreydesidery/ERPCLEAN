package com.orbix.engine.modules.day.domain.dto;

import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.domain.enums.BusinessDayStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Response shape for a business day. The composite {@code (branchId,
 * businessDate)} is the database identity (ADR 0002 — Path A); {@code uid} is
 * the external URL handle and is exposed alongside the composite components.
 * No surrogate {@code id} field — the composite *is* the identity.
 */
public record BusinessDayDto(
    String uid,
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
            day.getUid(),
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
