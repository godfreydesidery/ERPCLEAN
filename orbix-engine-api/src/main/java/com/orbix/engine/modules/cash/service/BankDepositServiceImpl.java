package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.domain.dto.BankDepositDto;
import com.orbix.engine.modules.cash.domain.dto.CashEntryDto;
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
public class BankDepositServiceImpl implements BankDepositService {

    private static final String AGG = "BankDeposit";

    private final BankDepositRepository deposits;
    private final CompanyRepository companies;
    private final CashLedgerService cashLedger;
    private final DayGuard dayGuard;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public BankDepositDto post(PostBankDepositRequestDto request) {
        branchScope.requireAccess(request.branchId());
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

        Map<String, Object> payload = new HashMap<>();
        payload.put("bankDepositUid", saved.getUid());
        payload.put("bankDepositId", saved.getId());
        payload.put("branchId", saved.getBranchId());
        payload.put("amount", saved.getAmount());
        payload.put("currencyCode", saved.getCurrencyCode());
        payload.put("reference", saved.getReference());
        payload.put("businessDate", saved.getBusinessDate());
        events.publish("BankDepositPosted.v1", AGG, String.valueOf(saved.getId()), payload);
        return BankDepositDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankDepositDto> list(Long branchId, LocalDate businessDate) {
        branchScope.requireAccess(branchId);
        return deposits.findByBranchIdAndBusinessDateOrderByAtAsc(branchId, businessDate).stream()
            .map(BankDepositDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BankDepositDto getBankDepositByUid(String uid) {
        return BankDepositDto.from(requireBankDepositByUid(uid));
    }

    @Override
    @Transactional
    @Auditable(action = "ARCHIVE", entityType = AGG)
    public BankDepositDto archiveBankDepositByUid(String uid) {
        BankDeposit original = requireBankDepositByUid(uid);
        if (original.isReversed()) {
            throw new IllegalArgumentException(
                "Bank deposit is already reversed: " + original.getUid());
        }
        Long actorId = context.userId();
        Instant at = Instant.now();

        // Compensating pair: CASH_BOX gets the money back (IN) and BANK loses
        // it (OUT). Same ref_id, NEW ref_type (BANK_DEPOSIT_REVERSAL) — a
        // direction-flip on the original ref_type would collide on the
        // (ref_type, ref_id, direction) UNIQUE because the original IN/OUT
        // pair already occupies both rows under BANK_DEPOSIT.
        CashEntryDto outEntry = cashLedger.post(at,
            original.getCompanyId(), original.getBranchId(), original.getBusinessDate(),
            CashAccount.CASH_BOX, CashDirection.IN, original.getAmount(),
            BigDecimal.ONE, original.getCurrencyCode(),
            CashRefType.BANK_DEPOSIT_REVERSAL, original.getId(), GlCategory.CASH,
            "REVERSAL: " + original.getReference(), actorId);
        CashEntryDto inEntry = cashLedger.post(at,
            original.getCompanyId(), original.getBranchId(), original.getBusinessDate(),
            CashAccount.BANK, CashDirection.OUT, original.getAmount(),
            BigDecimal.ONE, original.getCurrencyCode(),
            CashRefType.BANK_DEPOSIT_REVERSAL, original.getId(), GlCategory.BANK,
            "REVERSAL: " + original.getReference(), actorId);

        original.markReversed(actorId, outEntry.id(), inEntry.id());

        Map<String, Object> payload = new HashMap<>();
        payload.put("bankDepositUid", original.getUid());
        payload.put("bankDepositId", original.getId());
        payload.put("reversingCashEntryIds", List.of(outEntry.id(), inEntry.id()));
        payload.put("reversedBy", actorId);
        payload.put("branchId", original.getBranchId());
        payload.put("businessDate", original.getBusinessDate());
        events.publish("BankDepositReversed.v1", AGG, String.valueOf(original.getId()), payload);
        return BankDepositDto.from(original);
    }

    private BankDeposit requireBankDepositByUid(String uid) {
        BankDeposit deposit = deposits.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Bank deposit not found: " + uid));
        if (!Objects.equals(deposit.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Bank deposit not found: " + uid);
        }
        branchScope.requireAccess(deposit.getBranchId());
        return deposit;
    }

    private String requireCompanyCurrency(Long companyId) {
        return companies.findById(companyId)
            .orElseThrow(() -> new NoSuchElementException("Company not found: " + companyId))
            .getCurrencyCode();
    }
}
