package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.day.service.EodBlockerDto;
import com.orbix.engine.modules.day.service.EodGuard;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * POS EOD gate (F7.5 / TC-DAY-007). Any till session for the day still in
 * OPEN or CLOSED-but-not-RECONCILED state blocks close — the cashier must
 * either close + reconcile or the supervisor must reconcile the variance
 * before the day can move OPEN → CLOSING.
 */
@Component
@RequiredArgsConstructor
public class PosEodGuard implements EodGuard {

    private static final List<TillSessionStatus> BLOCKING =
        List.of(TillSessionStatus.OPEN, TillSessionStatus.CLOSED);

    private final TillSessionRepository tillSessions;

    @Override
    @Transactional(readOnly = true)
    public List<EodBlockerDto> check(Long branchId, LocalDate businessDate) {
        return tillSessions
            .findByBranchIdAndBusinessDateAndStatusIn(branchId, businessDate, BLOCKING)
            .stream()
            .map(this::toBlocker)
            .toList();
    }

    @Override
    public String moduleName() {
        return "pos";
    }

    private EodBlockerDto toBlocker(TillSession session) {
        String kind = session.getStatus() == TillSessionStatus.OPEN
            ? "OPEN_TILL_SESSION"
            : "UNRECONCILED_TILL_SESSION";
        String message = "Till session " + session.getId() + " on till " + session.getTillId()
            + " is " + session.getStatus() + " — close + reconcile before day-end";
        return new EodBlockerDto(moduleName(), kind, "TillSession", session.getId(), message);
    }
}
