package com.orbix.engine.modules.day.service;

import com.orbix.engine.modules.common.domain.enums.ResponseCode;
import com.orbix.engine.modules.common.service.BusinessPreconditionException;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.domain.enums.BusinessDayStatus;
import com.orbix.engine.modules.day.repository.BusinessDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synchronous port consumed by every other module's posting service: a
 * transaction may only post against a branch that has an OPEN business day.
 * Back-dating into a closed day goes through {@code BusinessDayService}'s
 * override path instead.
 */
@Component
@RequiredArgsConstructor
public class DayGuard {

    private final BusinessDayRepository businessDays;

    /**
     * Returns the branch's OPEN business day, or throws
     * {@link BusinessPreconditionException} (HTTP 422 BUSINESS_DAY_CLOSED)
     * if the day is not open (none opened, or already CLOSING / CLOSED).
     */
    @Transactional(readOnly = true)
    public BusinessDay requireOpenDay(Long branchId) {
        return businessDays.findFirstByBranchIdAndStatus(branchId, BusinessDayStatus.OPEN)
            .orElseThrow(() -> new BusinessPreconditionException(
                ResponseCode.BUSINESS_DAY_CLOSED,
                "No open business day for branch " + branchId + " — open the day before posting"));
    }
}
