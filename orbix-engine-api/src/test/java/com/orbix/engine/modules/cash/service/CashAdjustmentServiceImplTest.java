package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.domain.dto.CashAdjustmentDto;
import com.orbix.engine.modules.cash.domain.dto.PostCashAdjustmentRequestDto;
import com.orbix.engine.modules.cash.domain.entity.CashAdjustment;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.repository.CashAdjustmentRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashAdjustmentServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long ACTOR_ID = 4L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 5, 15);

    @Mock private CashAdjustmentRepository adjustments;
    @Mock private CompanyRepository companies;
    @Mock private CashLedgerService cashLedger;
    @Mock private DayGuard dayGuard;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private CashAdjustmentServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(8000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenReturn(new BusinessDay(BRANCH_ID, BUSINESS_DATE, ACTOR_ID));

        Company company = new Company(1L, "C", "Co.", "TZS", "TZ", "Africa/Dar_es_Salaam", ACTOR_ID);
        company.setId(COMPANY_ID);
        lenient().when(companies.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        lenient().when(adjustments.save(any(CashAdjustment.class))).thenAnswer(inv -> {
            CashAdjustment a = inv.getArgument(0);
            a.setId(nextId.getAndIncrement());
            return a;
        });
    }

    @Test
    void post_writesSingleEntryWithAdjustmentCategory() {
        CashAdjustmentDto dto = service.post(new PostCashAdjustmentRequestDto(
            BRANCH_ID, CashAccount.TILL, CashDirection.OUT,
            new BigDecimal("250"), "Drawer short — confirmed missing"));

        assertThat(dto.amount()).isEqualByComparingTo("250");
        assertThat(dto.reason()).contains("Drawer short");
        verify(cashLedger).post(any(), eq(COMPANY_ID), eq(BRANCH_ID), eq(BUSINESS_DATE),
            eq(CashAccount.TILL), eq(CashDirection.OUT), eq(new BigDecimal("250")),
            eq(BigDecimal.ONE), eq("TZS"), eq(CashRefType.CASH_ADJUSTMENT),
            any(), eq(GlCategory.ADJUSTMENT), any(), eq(ACTOR_ID));
        verify(events).publish(eq("CashAdjustmentPosted.v1"), any(), any(), any());
    }

    @Test
    void post_rejectsClosedBusinessDay() {
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenThrow(new IllegalStateException("No open business day"));

        PostCashAdjustmentRequestDto req = new PostCashAdjustmentRequestDto(
            BRANCH_ID, CashAccount.TILL, CashDirection.IN, new BigDecimal("100"), "reason");
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalStateException.class);
        verify(adjustments, never()).save(any());
        verify(cashLedger, never()).post(any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any());
    }
}
