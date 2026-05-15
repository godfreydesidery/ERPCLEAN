package com.orbix.engine.modules.day.service;

import com.orbix.engine.modules.day.domain.dto.BusinessDayDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Business-day lifecycle (F2.1). State machine OPEN -> CLOSING -> CLOSED.
 * Invariants: at most one non-closed day per branch; business dates are
 * monotonic (no opening a day on or before the latest existing day).
 */
public interface BusinessDayService {

    /** The branch's current non-closed day (OPEN or CLOSING), if any. */
    Optional<BusinessDayDto> getCurrentDay(Long branchId);

    List<BusinessDayDto> listDays(Long branchId);

    BusinessDayDto openDay(Long branchId, LocalDate businessDate);

    /** OPEN -> CLOSING. EOD pre-flight checks run here (delegated, see impl). */
    BusinessDayDto startClosing(Long branchId, LocalDate businessDate);

    /** CLOSING -> CLOSED. */
    BusinessDayDto closeDay(Long branchId, LocalDate businessDate, String eodReportObjectKey);
}
