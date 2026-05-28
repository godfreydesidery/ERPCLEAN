package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.domain.dto.CashAdjustmentDto;
import com.orbix.engine.modules.cash.domain.dto.CashEntryDto;
import com.orbix.engine.modules.cash.domain.dto.PostCashAdjustmentRequestDto;
import com.orbix.engine.modules.cash.domain.entity.CashAdjustment;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.repository.CashAdjustmentRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.BranchScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
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
    @Mock private BranchScope branchScope;

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
            ReflectionTestUtils.setField(a, "uid", UidGenerator.next());
            return a;
        });
    }

    private static CashAdjustment posted(Long companyId, Long branchId, CashDirection direction) {
        CashAdjustment row = new CashAdjustment(companyId, branchId, BUSINESS_DATE,
            CashAccount.TILL, direction, new BigDecimal("250"), "TZS",
            "Drawer short — confirmed missing", Instant.now(), ACTOR_ID);
        row.setId(8123L);
        ReflectionTestUtils.setField(row, "uid", UidGenerator.next());
        return row;
    }

    private static CashEntryDto reversingEntry(Long id, CashDirection direction) {
        return new CashEntryDto(
            UidGenerator.next(), id, Instant.now(), COMPANY_ID, BRANCH_ID, BUSINESS_DATE,
            CashAccount.TILL, direction, new BigDecimal("250"), new BigDecimal("250"),
            BigDecimal.ONE, "TZS", CashRefType.CASH_ADJUSTMENT_REVERSAL, 8123L,
            GlCategory.ADJUSTMENT, "REVERSAL", ACTOR_ID);
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

    // ---- uid entry points --------------------------------------------------

    @Test
    void getByUid_returnsAdjustmentWhenTenantMatches() {
        CashAdjustment row = posted(COMPANY_ID, BRANCH_ID, CashDirection.OUT);
        when(adjustments.findByUid(row.getUid())).thenReturn(Optional.of(row));

        CashAdjustmentDto result = service.getCashAdjustmentByUid(row.getUid());

        assertThat(result.uid()).isEqualTo(row.getUid());
        assertThat(result.amount()).isEqualByComparingTo("250");
    }

    @Test
    void getByUid_throwsWhenNotFound() {
        when(adjustments.findByUid("01HZ8X7M3K9PJK2D7Q5BCN8W4F")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCashAdjustmentByUid("01HZ8X7M3K9PJK2D7Q5BCN8W4F"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Cash adjustment not found");
    }

    @Test
    void getByUid_throwsWhenCrossTenant() {
        CashAdjustment row = posted(999L, BRANCH_ID, CashDirection.OUT);
        when(adjustments.findByUid(row.getUid())).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.getCashAdjustmentByUid(row.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void archiveByUid_postsCompensatingEntryAndStampsReversalColumns() {
        CashAdjustment original = posted(COMPANY_ID, BRANCH_ID, CashDirection.OUT);
        when(adjustments.findByUid(original.getUid())).thenReturn(Optional.of(original));
        when(cashLedger.post(any(), eq(COMPANY_ID), eq(BRANCH_ID), eq(BUSINESS_DATE),
                eq(CashAccount.TILL), eq(CashDirection.IN),       // opposite of original OUT
                eq(new BigDecimal("250")), eq(BigDecimal.ONE), eq("TZS"),
                eq(CashRefType.CASH_ADJUSTMENT_REVERSAL), eq(original.getId()),
                eq(GlCategory.ADJUSTMENT), any(), eq(ACTOR_ID)))
            .thenReturn(reversingEntry(77L, CashDirection.IN));

        CashAdjustmentDto result = service.archiveCashAdjustmentByUid(original.getUid());

        assertThat(result.reversedAt()).isNotNull();
        assertThat(result.reversedBy()).isEqualTo(ACTOR_ID);
        assertThat(result.reversedByEntryId()).isEqualTo(77L);
        verify(events).publish(eq("CashAdjustmentReversed.v1"), any(), any(), any());
    }

    @Test
    void archiveByUid_rejectsDoubleReversal() {
        CashAdjustment original = posted(COMPANY_ID, BRANCH_ID, CashDirection.OUT);
        original.markReversed(ACTOR_ID, 77L);
        when(adjustments.findByUid(original.getUid())).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.archiveCashAdjustmentByUid(original.getUid()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already reversed");
        verify(cashLedger, never()).post(any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any());
        verify(events, never()).publish(eq("CashAdjustmentReversed.v1"), any(), any(), any());
    }

    @Test
    void archiveByUid_throwsOnCrossTenant() {
        CashAdjustment foreign = posted(999L, BRANCH_ID, CashDirection.OUT);
        when(adjustments.findByUid(foreign.getUid())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.archiveCashAdjustmentByUid(foreign.getUid()))
            .isInstanceOf(NoSuchElementException.class);
        verify(cashLedger, never()).post(any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any());
    }
}
