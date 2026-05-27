package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.domain.dto.CashAdjustmentDto;
import com.orbix.engine.modules.cash.domain.dto.CashEntryDto;
import com.orbix.engine.modules.cash.domain.dto.PostCashAdjustmentRequestDto;
import com.orbix.engine.modules.cash.domain.entity.CashAdjustment;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.repository.CashAdjustmentRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.BranchScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CashAdjustmentServiceImpl implements CashAdjustmentService {

    private static final String AGG = "CashAdjustment";

    private final CashAdjustmentRepository adjustments;
    private final CompanyRepository companies;
    private final CashLedgerService cashLedger;
    private final DayGuard dayGuard;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public CashAdjustmentDto post(PostCashAdjustmentRequestDto request) {
        branchScope.requireAccess(request.branchId());
        Long companyId = context.companyId();
        Long actorId = context.userId();
        BusinessDay day = dayGuard.requireOpenDay(request.branchId());
        String currency = requireCompanyCurrency(companyId);

        Instant at = Instant.now();
        CashAdjustment saved = adjustments.save(new CashAdjustment(
            companyId, request.branchId(), day.getBusinessDate(),
            request.account(), request.direction(), request.amount(),
            currency, request.reason(), at, actorId
        ));

        cashLedger.post(at, companyId, request.branchId(), day.getBusinessDate(),
            request.account(), request.direction(), request.amount(),
            BigDecimal.ONE, currency,
            CashRefType.CASH_ADJUSTMENT, saved.getId(),
            GlCategory.ADJUSTMENT, request.reason(), actorId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("cashAdjustmentUid", saved.getUid());
        payload.put("cashAdjustmentId", saved.getId());
        payload.put("branchId", saved.getBranchId());
        payload.put("account", saved.getAccount());
        payload.put("direction", saved.getDirection());
        payload.put("amount", saved.getAmount());
        payload.put("currencyCode", saved.getCurrencyCode());
        payload.put("businessDate", saved.getBusinessDate());
        events.publish("CashAdjustmentPosted.v1", AGG, String.valueOf(saved.getId()), payload);
        return CashAdjustmentDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashAdjustmentDto> list(Long branchId, LocalDate businessDate) {
        branchScope.requireAccess(branchId);
        return adjustments.findByBranchIdAndBusinessDateOrderByAtAsc(branchId, businessDate).stream()
            .map(CashAdjustmentDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CashAdjustmentDto getCashAdjustmentByUid(String uid) {
        return CashAdjustmentDto.from(requireCashAdjustmentByUid(uid));
    }

    @Override
    @Transactional
    @Auditable(action = "ARCHIVE", entityType = AGG)
    public CashAdjustmentDto archiveCashAdjustmentByUid(String uid) {
        CashAdjustment original = requireCashAdjustmentByUid(uid);
        if (original.isReversed()) {
            throw new IllegalArgumentException(
                "Cash adjustment is already reversed: " + original.getUid());
        }
        Long actorId = context.userId();

        // Compensating entry: opposite direction, same account / amount / currency.
        // New ref_type avoids the (ref_type, ref_id, direction) UNIQUE on
        // cash_entry (the original posting already occupies that triple) and
        // keeps the reversal trivially queryable.
        CashDirection opposite = original.getDirection() == CashDirection.IN
            ? CashDirection.OUT : CashDirection.IN;
        CashEntryDto reversingEntry = cashLedger.post(
            Instant.now(),
            original.getCompanyId(),
            original.getBranchId(),
            original.getBusinessDate(),
            original.getAccount(),
            opposite,
            original.getAmount(),
            BigDecimal.ONE,
            original.getCurrencyCode(),
            CashRefType.CASH_ADJUSTMENT_REVERSAL,
            original.getId(),
            GlCategory.ADJUSTMENT,
            "REVERSAL: " + original.getReason(),
            actorId
        );

        original.markReversed(actorId, reversingEntry.id());

        Map<String, Object> payload = new HashMap<>();
        payload.put("cashAdjustmentUid", original.getUid());
        payload.put("cashAdjustmentId", original.getId());
        payload.put("reversingCashEntryId", reversingEntry.id());
        payload.put("reversedBy", actorId);
        payload.put("branchId", original.getBranchId());
        payload.put("businessDate", original.getBusinessDate());
        events.publish("CashAdjustmentReversed.v1", AGG, String.valueOf(original.getId()), payload);
        return CashAdjustmentDto.from(original);
    }

    private CashAdjustment requireCashAdjustmentByUid(String uid) {
        CashAdjustment adjustment = adjustments.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Cash adjustment not found: " + uid));
        if (!Objects.equals(adjustment.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Cash adjustment not found: " + uid);
        }
        branchScope.requireAccess(adjustment.getBranchId());
        return adjustment;
    }

    private String requireCompanyCurrency(Long companyId) {
        return companies.findById(companyId)
            .orElseThrow(() -> new NoSuchElementException("Company not found: " + companyId))
            .getCurrencyCode();
    }
}
