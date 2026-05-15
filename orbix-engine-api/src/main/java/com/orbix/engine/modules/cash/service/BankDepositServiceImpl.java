package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.domain.dto.BankDepositDto;
import com.orbix.engine.modules.cash.domain.dto.PostBankDepositRequestDto;
import com.orbix.engine.modules.cash.domain.entity.BankDeposit;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.repository.BankDepositRepository;
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
public class BankDepositServiceImpl implements BankDepositService {

    private static final String AGG = "BankDeposit";

    private final BankDepositRepository deposits;
    private final CompanyRepository companies;
    private final CashLedgerService cashLedger;
    private final DayGuard dayGuard;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public BankDepositDto post(PostBankDepositRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        BusinessDay day = dayGuard.requireOpenDay(request.branchId());
        String currency = requireCompanyCurrency(companyId);

        Instant at = Instant.now();
        BankDeposit saved = deposits.save(new BankDeposit(
            companyId, request.branchId(), day.getBusinessDate(),
            request.amount(), currency, request.reference(), request.notes(),
            at, actorId
        ));

        // Paired entries: cash leaves the safe (CASH_BOX) and lands at the bank.
        // Same ref_id (the deposit), different direction → both pass the F6.1
        // (ref_type, ref_id, direction) idempotency UNIQUE.
        cashLedger.post(at, companyId, request.branchId(), day.getBusinessDate(),
            CashAccount.CASH_BOX, CashDirection.OUT, request.amount(),
            BigDecimal.ONE, currency,
            CashRefType.BANK_DEPOSIT, saved.getId(), GlCategory.CASH,
            request.reference(), actorId);
        cashLedger.post(at, companyId, request.branchId(), day.getBusinessDate(),
            CashAccount.BANK, CashDirection.IN, request.amount(),
            BigDecimal.ONE, currency,
            CashRefType.BANK_DEPOSIT, saved.getId(), GlCategory.BANK,
            request.reference(), actorId);

        events.publish("BankDepositPosted.v1", AGG, String.valueOf(saved.getId()),
            Map.of("bankDepositId", saved.getId(),
                "branchId", saved.getBranchId(),
                "amount", saved.getAmount(),
                "reference", saved.getReference(),
                "businessDate", saved.getBusinessDate()));
        return BankDepositDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankDepositDto> list(Long branchId, LocalDate businessDate) {
        return deposits.findByBranchIdAndBusinessDateOrderByAtAsc(branchId, businessDate).stream()
            .map(BankDepositDto::from)
            .toList();
    }

    private String requireCompanyCurrency(Long companyId) {
        return companies.findById(companyId)
            .orElseThrow(() -> new NoSuchElementException("Company not found: " + companyId))
            .getCurrencyCode();
    }
}
