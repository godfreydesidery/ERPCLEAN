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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BusinessDayServiceImpl implements BusinessDayService {

    private static final List<BusinessDayStatus> NOT_CLOSED =
        List.of(BusinessDayStatus.OPEN, BusinessDayStatus.CLOSING);

    private final BusinessDayRepository businessDays;
    private final BranchRepository branches;
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
    @Auditable(action = "OPEN", entityType = "BusinessDay")
    public BusinessDayDto openDay(Long branchId, LocalDate businessDate) {
        requireBranch(branchId);
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

        BusinessDay day = businessDays.save(new BusinessDay(branchId, businessDate, context.userId()));
        events.publish("BusinessDayOpened.v1", "BusinessDay", branchId + ":" + businessDate,
            Map.of("branchId", branchId, "businessDate", businessDate.toString()));
        return BusinessDayDto.from(day);
    }

    @Override
    @Transactional
    @Auditable(action = "START_CLOSING", entityType = "BusinessDay")
    public BusinessDayDto startClosing(Long branchId, LocalDate businessDate) {
        BusinessDay day = requireDay(branchId, businessDate);
        if (day.getStatus() != BusinessDayStatus.OPEN) {
            throw new IllegalArgumentException("Business day is not OPEN: " + day.getStatus());
        }
        // TODO (F2.2 / F3.x / F5.x / F7.3): EOD pre-flight checks delegated into
        // stock / procurement / pos / production once those posting modules exist.
        day.startClosing();
        events.publish("BusinessDayClosingStarted.v1", "BusinessDay",
            branchId + ":" + businessDate,
            Map.of("branchId", branchId, "businessDate", businessDate.toString()));
        return BusinessDayDto.from(day);
    }

    @Override
    @Transactional
    @Auditable(action = "CLOSE", entityType = "BusinessDay")
    public BusinessDayDto closeDay(Long branchId, LocalDate businessDate, String eodReportObjectKey) {
        BusinessDay day = requireDay(branchId, businessDate);
        if (day.getStatus() != BusinessDayStatus.CLOSING) {
            throw new IllegalArgumentException(
                "Business day must be CLOSING to close: " + day.getStatus());
        }
        day.close(context.userId(), eodReportObjectKey);
        events.publish("BusinessDayClosed.v1", "BusinessDay", branchId + ":" + businessDate,
            Map.of("branchId", branchId, "businessDate", businessDate.toString()));
        return BusinessDayDto.from(day);
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
