package com.orbix.engine.modules.day.service;

import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.dto.BusinessDayDto;
import com.orbix.engine.modules.day.domain.dto.BusinessDayOverrideDto;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.domain.entity.BusinessDayId;
import com.orbix.engine.modules.day.domain.entity.BusinessDayOverride;
import com.orbix.engine.modules.day.domain.enums.BusinessDayStatus;
import com.orbix.engine.modules.day.repository.BusinessDayOverrideRepository;
import com.orbix.engine.modules.day.repository.BusinessDayRepository;
import com.orbix.engine.modules.iam.service.BranchScope;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final String AGG_OVERRIDE = "BusinessDayOverride";
    private static final String F_BRANCH = "branchId";
    private static final String F_DATE = "businessDate";
    private static final List<BusinessDayStatus> NOT_CLOSED =
        List.of(BusinessDayStatus.OPEN, BusinessDayStatus.CLOSING);

    private final BusinessDayRepository businessDays;
    private final BusinessDayOverrideRepository businessDayOverrides;
    private final BranchRepository branches;
    private final List<EodGuard> eodGuards;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

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
            Map.of("uid", day.getUid(),
                F_BRANCH, branchId, F_DATE, businessDate.toString(),
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
        return startClosingInternal(day);
    }

    @Override
    @Transactional
    @Auditable(action = "CLOSE", entityType = AGG)
    public BusinessDayDto closeDay(Long branchId, LocalDate businessDate, String eodReportObjectKey) {
        BusinessDay day = requireDay(branchId, businessDate);
        return closeInternal(day, eodReportObjectKey);
    }

    @Override
    @Transactional
    @Auditable(action = "END", entityType = AGG)
    public BusinessDayDto endDay(Long branchId, LocalDate businessDate, String eodReportObjectKey) {
        BusinessDay day = requireDay(branchId, businessDate);
        return endInternal(day, eodReportObjectKey);
    }

    // ---- uid entry points --------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public BusinessDayDto getBusinessDayByUid(String uid) {
        return BusinessDayDto.from(requireBusinessDayByUid(uid));
    }

    @Override
    @Transactional
    @Auditable(action = "START_CLOSING", entityType = AGG)
    public BusinessDayDto startClosingByUid(String uid) {
        return startClosingInternal(requireBusinessDayByUid(uid));
    }

    @Override
    @Transactional
    @Auditable(action = "CLOSE", entityType = AGG)
    public BusinessDayDto closeDayByUid(String uid, String eodReportObjectKey) {
        return closeInternal(requireBusinessDayByUid(uid), eodReportObjectKey);
    }

    @Override
    @Transactional
    @Auditable(action = "END", entityType = AGG)
    public BusinessDayDto endDayByUid(String uid, String eodReportObjectKey) {
        return endInternal(requireBusinessDayByUid(uid), eodReportObjectKey);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EodBlockerDto> previewBlockersByUid(String uid) {
        BusinessDay day = requireBusinessDayByUid(uid);
        return aggregateBlockers(day.getBranchId(), day.getBusinessDate());
    }

    // ---- business-day overrides --------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "POST_OVERRIDE", entityType = AGG_OVERRIDE)
    public BusinessDayOverrideDto postOverrideByDayUid(String dayUid,
                                                       String entityType,
                                                       Long entityId,
                                                       String reason) {
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType is required");
        }
        if (entityId == null) {
            throw new IllegalArgumentException("entityId is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        BusinessDay day = requireBusinessDayByUid(dayUid);
        BusinessDayOverride override = businessDayOverrides.save(new BusinessDayOverride(
            day.getBranchId(),
            day.getBusinessDate(),
            entityType.trim(),
            entityId,
            reason.trim(),
            context.userId()
        ));
        Map<String, Object> payload = new HashMap<>();
        payload.put("uid", override.getUid());
        payload.put("dayUid", day.getUid());
        payload.put(F_BRANCH, override.getBranchId());
        payload.put(F_DATE, override.getTargetBusinessDate().toString());
        payload.put("entityType", override.getEntityType());
        payload.put("entityId", override.getEntityId());
        payload.put("reason", override.getReason());
        payload.put("overriddenBy", override.getAuthorisedBy());
        payload.put("overriddenAt", override.getAt().toString());
        events.publish("BusinessDayOverridden.v1", AGG_OVERRIDE,
            String.valueOf(override.getId()), payload);
        return BusinessDayOverrideDto.from(override);
    }

    @Override
    @Transactional
    @Auditable(action = "ARCHIVE_OVERRIDE", entityType = AGG_OVERRIDE)
    public BusinessDayOverrideDto archiveBusinessDayOverrideByUid(String uid) {
        BusinessDayOverride override = requireOverrideByUid(uid);
        if (override.isArchived()) {
            throw new IllegalArgumentException(
                "Business day override is already archived: " + override.getUid());
        }
        override.archive(context.userId());
        return BusinessDayOverrideDto.from(override);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusinessDayOverrideDto> listOverrides(Long branchId) {
        requireBranch(branchId);
        // Repository already sorts by `at DESC`; map straight to DTOs.
        return businessDayOverrides.findByBranchIdOrderByAtDesc(branchId).stream()
            .map(BusinessDayOverrideDto::from)
            .toList();
    }

    // ---- shared internals --------------------------------------------------

    private BusinessDayDto startClosingInternal(BusinessDay day) {
        if (day.getStatus() == BusinessDayStatus.CLOSING
                || day.getStatus() == BusinessDayStatus.CLOSED) {
            return BusinessDayDto.from(day);
        }
        if (day.getStatus() != BusinessDayStatus.OPEN) {
            throw new IllegalArgumentException("Business day is not OPEN: " + day.getStatus());
        }
        List<EodBlockerDto> blockers = aggregateBlockers(day.getBranchId(), day.getBusinessDate());
        if (!blockers.isEmpty()) {
            throw new EodBlockedException(blockers);
        }
        day.startClosing();
        events.publish("BusinessDayClosingStarted.v1", AGG,
            day.getBranchId() + ":" + day.getBusinessDate(),
            Map.of("uid", day.getUid(),
                F_BRANCH, day.getBranchId(),
                F_DATE, day.getBusinessDate().toString()));
        return BusinessDayDto.from(day);
    }

    private BusinessDayDto closeInternal(BusinessDay day, String eodReportObjectKey) {
        if (day.getStatus() == BusinessDayStatus.CLOSED) {
            return BusinessDayDto.from(day);
        }
        if (day.getStatus() != BusinessDayStatus.CLOSING) {
            throw new IllegalArgumentException(
                "Business day must be CLOSING to close: " + day.getStatus());
        }
        day.close(context.userId(), eodReportObjectKey);
        events.publish("BusinessDayClosed.v1", AGG,
            day.getBranchId() + ":" + day.getBusinessDate(),
            Map.of("uid", day.getUid(),
                F_BRANCH, day.getBranchId(),
                F_DATE, day.getBusinessDate().toString(),
                "closedBy", day.getClosedBy(),
                "closedAt", day.getClosedAt() == null ? "" : day.getClosedAt().toString(),
                "eodReportObjectKey", eodReportObjectKey == null ? "" : eodReportObjectKey));
        autoRoll(day.getBranchId(), day.getBusinessDate());
        return BusinessDayDto.from(day);
    }

    private BusinessDayDto endInternal(BusinessDay day, String eodReportObjectKey) {
        if (day.getStatus() == BusinessDayStatus.CLOSED) {
            return BusinessDayDto.from(day);
        }
        if (day.getStatus() == BusinessDayStatus.OPEN) {
            List<EodBlockerDto> blockers =
                aggregateBlockers(day.getBranchId(), day.getBusinessDate());
            if (!blockers.isEmpty()) {
                throw new EodBlockedException(blockers);
            }
            day.startClosing();
            events.publish("BusinessDayClosingStarted.v1", AGG,
                day.getBranchId() + ":" + day.getBusinessDate(),
                Map.of("uid", day.getUid(),
                    F_BRANCH, day.getBranchId(),
                    F_DATE, day.getBusinessDate().toString()));
        }
        day.close(context.userId(), eodReportObjectKey);
        events.publish("BusinessDayClosed.v1", AGG,
            day.getBranchId() + ":" + day.getBusinessDate(),
            Map.of("uid", day.getUid(),
                F_BRANCH, day.getBranchId(),
                F_DATE, day.getBusinessDate().toString(),
                "closedBy", day.getClosedBy(),
                "closedAt", day.getClosedAt() == null ? "" : day.getClosedAt().toString(),
                "eodReportObjectKey", eodReportObjectKey == null ? "" : eodReportObjectKey));
        autoRoll(day.getBranchId(), day.getBusinessDate());
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
            Map.of("uid", next.getUid(),
                F_BRANCH, branchId, F_DATE, nextDate.toString(),
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
        branchScope.requireAccess(branchId);
        return branch;
    }

    private BusinessDay requireDay(Long branchId, LocalDate businessDate) {
        requireBranch(branchId);
        return businessDays.findById(new BusinessDayId(branchId, businessDate))
            .orElseThrow(() -> new NoSuchElementException(
                "Business day not found: " + branchId + ":" + businessDate));
    }

    private BusinessDay requireBusinessDayByUid(String uid) {
        BusinessDay day = businessDays.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Business day not found: " + uid));
        // Tenant predicate: the parent branch must belong to the caller's
        // company. requireBranch also checks BranchScope (per-user branch
        // access) so cross-branch reads inside a single company are blocked
        // for non-superusers.
        requireBranch(day.getBranchId());
        return day;
    }

    private BusinessDayOverride requireOverrideByUid(String uid) {
        BusinessDayOverride override = businessDayOverrides.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException(
                "Business day override not found: " + uid));
        requireBranch(override.getBranchId());
        return override;
    }
}
