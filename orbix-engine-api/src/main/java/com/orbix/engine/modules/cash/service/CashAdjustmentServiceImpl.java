package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.domain.dto.CashAdjustmentDto;
import com.orbix.engine.modules.cash.domain.dto.PostCashAdjustmentRequestDto;
import com.orbix.engine.modules.cash.domain.entity.CashAdjustment;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.repository.CashAdjustmentRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

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

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public CashAdjustmentDto post(PostCashAdjustmentRequestDto request) {
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

        events.publish("CashAdjustmentPosted.v1", AGG, String.valueOf(saved.getId()),
            Map.of("cashAdjustmentId", saved.getId(),
                "branchId", saved.getBranchId(),
                "account", saved.getAccount(),
                "direction", saved.getDirection(),
                "amount", saved.getAmount(),
                "businessDate", saved.getBusinessDate()));
        return CashAdjustmentDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashAdjustmentDto> list(Long branchId, LocalDate businessDate) {
        return adjustments.findByBranchIdAndBusinessDateOrderByAtAsc(branchId, businessDate).stream()
            .map(CashAdjustmentDto::from)
            .toList();
    }

    private String requireCompanyCurrency(Long companyId) {
        return companies.findById(companyId)
            .orElseThrow(() -> new NoSuchElementException("Company not found: " + companyId))
            .getCurrencyCode();
    }
}
