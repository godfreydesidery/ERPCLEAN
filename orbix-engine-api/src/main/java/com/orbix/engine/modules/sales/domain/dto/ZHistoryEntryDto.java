package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.pos.domain.dto.TillReportDto;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One row of the Z-history report (F8.2 / US-RPT-003). Wraps a single
 * {@code TillReportDto} with the session-level metadata the auditor needs
 * to scan the list (till id, opened / closed timestamps, status). Skipped
 * sessions (OPEN — Z-report not yet computable) are excluded by the
 * service rather than carrying a null report payload here.
 */
public record ZHistoryEntryDto(
    Long tillSessionId,
    Long tillId,
    Long branchId,
    LocalDate businessDate,
    TillSessionStatus sessionStatus,
    Instant openedAt,
    Instant closedAt,
    TillReportDto report
) {}
