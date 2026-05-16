package com.orbix.engine.modules.day.service;

import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.dto.BusinessDayDto;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.domain.entity.BusinessDayId;
import com.orbix.engine.modules.day.domain.enums.BusinessDayStatus;
import com.orbix.engine.modules.day.repository.BusinessDayRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BusinessDayServiceImpl implements BusinessDayService {

    private static final Logger log = LoggerFactory.getLogger(BusinessDayServiceImpl.class);
    private static final long SYSTEM_ACTOR_ID = 0L;
    private static final String AGG = "BusinessDay";
    private static final String F_BRANCH = "branchId";
    private static final String F_DATE = "businessDate";
    private static final List<BusinessDayStatus> NOT_CLOSED =
        List.of(BusinessDayStatus.OPEN, BusinessDayStatus.CLOSING);

    private final BusinessDayRepository businessDays;
    private final BranchRepository branches;
    private final List<EodGuard> eodGuards;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessDayDto> getCurrentDay(Long branchId) {
        requireBranch(branchId);
        return businessDays.findFirstByBranchIdAndStatusIn(branchId, NOT_CLOSED)
            .map(BusinessDayDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusinessDayDto> listDays(Long branchId) {
        requireBranch(branchId);
        return businessDays.findByBranchIdOrderByBusinessDateDesc(branchId).stream()
            .map(BusinessDayDto::from)
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "OPEN", entityType = AGG)
    public BusinessDayDto openDay(Long branchId, LocalDate businessDate) {
        requireBranch(branchId);
        BusinessDay day = openInternal(branchId, businessDate, context.userId());
        events.publish("BusinessDayOpened.v1", AGG, branchId + ":" + businessDate,
            Map.of(F_BRANCH, branchId, F_DATE, businessDate.toString(),
                "openedBy", day.getOpenedBy()));
        return BusinessDayDto.from(day);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EodBlockerDto> previewBlockers(Long branchId, LocalDate businessDate) {
        requireBranch(branchId);
        return aggregateBlockers(branchId, businessDate);
    }

    @Override
    @Transactional
    @Auditable(action = "START_CLOSING", entityType = AGG)
    public BusinessDayDto startClosing(Long branchId, LocalDate businessDate) {
        BusinessDay day = requireDay(branchId, businessDate);
        if (day.getStatus() == BusinessDayStatus.CLOSING
                || day.getStatus() == BusinessDayStatus.CLOSED) {
            return BusinessDayDto.from(day);
        }
        if (day.getStatus() != BusinessDayStatus.OPEN) {
            throw new IllegalArgumentException("Business day is not OPEN: " + day.getStatus());
        }
        List<EodBlockerDto> blockers = aggregateBlockers(branchId, businessDate);
        if (!blockers.isEmpty()) {
            throw new EodBlockedException(blockers);
        }
        day.startClosing();
        events.publish("BusinessDayClosingStarted.v1", AGG, branchId + ":" + businessDate,
            Map.of(F_BRANCH, branchId, F_DATE, businessDate.toString()));
        return BusinessDayDto.from(day);
    }

    @Override
    @Transactional
    @Auditable(action = "CLOSE", entityType = AGG)
    public BusinessDayDto closeDay(Long branchId, LocalDate businessDate, String eodReportObjectKey) {
        BusinessDay day = requireDay(branchId, businessDate);
        if (day.getStatus() == BusinessDayStatus.CLOSED) {
            return BusinessDayDto.from(day);
        }
        if (day.getStatus() != BusinessDayStatus.CLOSING) {
            throw new IllegalArgumentException(
                "Business day must be CLOSING to close: " + day.getStatus());
        }
        day.close(context.userId(), eodReportObjectKey);
        events.publish("BusinessDayClosed.v1", AGG, branchId + ":" + businessDate,
            Map.of(F_BRANCH, branchId, F_DATE, businessDate.toString(),
                "closedBy", day.getClosedBy(),
                "eodReportObjectKey", eodReportObjectKey == null ? "" : eodReportObjectKey));
        autoRoll(branchId, businessDate);
        return BusinessDayDto.from(day);
    }

    @Override
    @Transactional
    @Auditable(action = "END", entityType = AGG)
    public BusinessDayDto endDay(Long branchId, LocalDate businessDate, String eodReportObjectKey) {
        BusinessDay day = requireDay(branchId, businessDate);
        // Already-closed days return as-is (TC-DAY-025 idempotency).
        if (day.getStatus() == BusinessDayStatus.CLOSED) {
            return BusinessDayDto.from(day);
        }
        if (day.getStatus() == BusinessDayStatus.OPEN) {
            List<EodBlockerDto> blockers = aggregateBlockers(branchId, businessDate);
            if (!blockers.isEmpty()) {
                throw new EodBlockedException(blockers);
            }
            day.startClosing();
            events.publish("BusinessDayClosingStarted.v1", AGG, branchId + ":" + businessDate,
                Map.of(F_BRANCH, branchId, F_DATE, businessDate.toString()));
        }
        day.close(context.userId(), eodReportObjectKey);
        events.publish("BusinessDayClosed.v1", AGG, branchId + ":" + businessDate,
            Map.of(F_BRANCH, branchId, F_DATE, businessDate.toString(),
                "closedBy", day.getClosedBy(),
                "eodReportObjectKey", eodReportObjectKey == null ? "" : eodReportObjectKey));
        autoRoll(branchId, businessDate);
        return BusinessDayDto.from(day);
    }

    /**
     * After a clean close, auto-create the next OPEN day for the branch
     * (TC-DAY-004) — opened_by = SYSTEM. Skipped if the next day already
     * exists (idempotent across retries / parallel close attempts).
     */
    private void autoRoll(Long branchId, LocalDate closedDate) {
        LocalDate nextDate = closedDate.plusDays(1);
        if (businessDays.findById(new BusinessDayId(branchId, nextDate)).isPresent()) {
            return;
        }
        BusinessDay next = openInternal(branchId, nextDate, SYSTEM_ACTOR_ID);
        events.publish("BusinessDayOpened.v1", AGG, branchId + ":" + nextDate,
            Map.of(F_BRANCH, branchId, F_DATE, nextDate.toString(),
                "openedBy", next.getOpenedBy(),
                "autoRolledFrom", closedDate.toString()));
    }

    private BusinessDay openInternal(Long branchId, LocalDate businessDate, Long actorId) {
        businessDays.findFirstByBranchIdAndStatusIn(branchId, NOT_CLOSED).ifPresent(open -> {
            throw new IllegalArgumentException(
                "Branch already has a non-closed business day: " + open.getBusinessDate());
        });
        businessDays.findByBranchIdOrderByBusinessDateDesc(branchId).stream().findFirst()
            .ifPresent(latest -> {
                if (!businessDate.isAfter(latest.getBusinessDate())) {
                    throw new IllegalArgumentException(
                        "Business date must be after the latest day: " + latest.getBusinessDate());
                }
            });
        return businessDays.save(new BusinessDay(branchId, businessDate, actorId));
    }

    private List<EodBlockerDto> aggregateBlockers(Long branchId, LocalDate businessDate) {
        List<EodBlockerDto> all = new ArrayList<>();
        for (EodGuard guard : eodGuards) {
            try {
                all.addAll(guard.check(branchId, businessDate));
            } catch (RuntimeException ex) {
                // A guard misbehaving must not falsely allow the close — fail
                // closed with a synthetic blocker so the operator sees the cause.
                log.error("EodGuard {} threw during close-check for branch {} / {}",
                    guard.moduleName(), branchId, businessDate, ex);
                all.add(new EodBlockerDto(guard.moduleName(), "GUARD_FAILURE",
                    "EodGuard", null,
                    "EOD guard '" + guard.moduleName() + "' failed: " + ex.getMessage()));
            }
        }
        return all;
    }

    private Branch requireBranch(Long branchId) {
        Branch branch = branches.findById(branchId)
            .orElseThrow(() -> new NoSuchElementException("Branch not found: " + branchId));
        if (!Objects.equals(branch.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Branch not found: " + branchId);
        }
        return branch;
    }

    private BusinessDay requireDay(Long branchId, LocalDate businessDate) {
        requireBranch(branchId);
        return businessDays.findById(new BusinessDayId(branchId, businessDate))
            .orElseThrow(() -> new NoSuchElementException(
                "Business day not found: " + branchId + ":" + businessDate));
    }
}
