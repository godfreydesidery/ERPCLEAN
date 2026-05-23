package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.domain.dto.BankDepositDto;
import com.orbix.engine.modules.cash.domain.dto.PostBankDepositRequestDto;
import com.orbix.engine.modules.cash.domain.entity.BankDeposit;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.repository.BankDepositRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.BranchScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankDepositServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long ACTOR_ID = 4L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 5, 15);

    @Mock private BankDepositRepository deposits;
    @Mock private CompanyRepository companies;
    @Mock private CashLedgerService cashLedger;
    @Mock private DayGuard dayGuard;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

    @InjectMocks private BankDepositServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(9000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenReturn(new BusinessDay(BRANCH_ID, BUSINESS_DATE, ACTOR_ID));

        Company company = new Company(1L, "C", "Co.", "TZS", "TZ", "Africa/Dar_es_Salaam", ACTOR_ID);
        company.setId(COMPANY_ID);
        lenient().when(companies.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        lenient().when(deposits.save(any(BankDeposit.class))).thenAnswer(inv -> {
            BankDeposit d = inv.getArgument(0);
            d.setId(nextId.getAndIncrement());
            return d;
        });
    }

    @Test
    void post_writesPairedLedgerEntriesAndEmitsEvent() {
        BankDepositDto dto = service.post(new PostBankDepositRequestDto(
            BRANCH_ID, new BigDecimal("500000"), "BANK-SLIP-2026-05-15-001", "End of day"));

        assertThat(dto.amount()).isEqualByComparingTo("500000");
        verify(cashLedger).post(any(), eq(COMPANY_ID), eq(BRANCH_ID), eq(BUSINESS_DATE),
            eq(CashAccount.CASH_BOX), eq(CashDirection.OUT), eq(new BigDecimal("500000")),
            eq(BigDecimal.ONE), eq("TZS"), eq(CashRefType.BANK_DEPOSIT),
            any(), eq(GlCategory.CASH), any(), eq(ACTOR_ID));
        verify(cashLedger).post(any(), eq(COMPANY_ID), eq(BRANCH_ID), eq(BUSINESS_DATE),
            eq(CashAccount.BANK), eq(CashDirection.IN), eq(new BigDecimal("500000")),
            eq(BigDecimal.ONE), eq("TZS"), eq(CashRefType.BANK_DEPOSIT),
            any(), eq(GlCategory.BANK), any(), eq(ACTOR_ID));
        verify(cashLedger, times(2)).post(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any());
        verify(events).publish(eq("BankDepositPosted.v1"), any(), any(), any());
    }

    @Test
    void post_rejectsClosedBusinessDay() {
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenThrow(new IllegalStateException("No open business day"));

        PostBankDepositRequestDto req = new PostBankDepositRequestDto(
            BRANCH_ID, new BigDecimal("100000"), "REF-1", null);
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalStateException.class);
        verify(deposits, never()).save(any());
        verify(cashLedger, never()).post(any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any());
    }
}
