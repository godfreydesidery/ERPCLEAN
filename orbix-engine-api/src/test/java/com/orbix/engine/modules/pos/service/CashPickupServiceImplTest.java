package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.service.CashLedgerService;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.pos.domain.dto.PostCashPickupRequestDto;
import com.orbix.engine.modules.pos.domain.entity.CashPickup;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.repository.CashPickupRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashPickupServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long TILL_ID = 100L;
    private static final Long SESSION_ID = 200L;
    private static final Long ACTOR_ID = 4L;
    private static final Long SUPERVISOR_ID = 9L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 5, 15);

    @Mock private CashPickupRepository pickups;
    @Mock private TillSessionRepository sessions;
    @Mock private CompanyRepository companies;
    @Mock private CashLedgerService cashLedger;
    @Mock private DayGuard dayGuard;
    @Mock private PermissionResolverService permissions;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private CashPickupServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(5000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenReturn(new BusinessDay(BRANCH_ID, BUSINESS_DATE, ACTOR_ID));

        Company company = new Company(1L, "C", "Co.", "TZS", "TZ", "Africa/Dar_es_Salaam", ACTOR_ID);
        company.setId(COMPANY_ID);
        lenient().when(companies.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        lenient().when(pickups.save(any(CashPickup.class))).thenAnswer(inv -> {
            CashPickup p = inv.getArgument(0);
            p.setId(nextId.getAndIncrement());
            return p;
        });
    }

    private TillSession openSession() {
        TillSession s = new TillSession(TILL_ID, BRANCH_ID, COMPANY_ID, BUSINESS_DATE, ACTOR_ID,
            new BigDecimal("50000"));
        s.setId(SESSION_ID);
        return s;
    }

    @Test
    void post_writesPairedLedgerEntriesAndEmitsEvent() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(openSession()));
        when(permissions.resolve(SUPERVISOR_ID, COMPANY_ID, null))
            .thenReturn(Set.of("POS.CASH_PICKUP"));

        service.post(new PostCashPickupRequestDto(SESSION_ID, new BigDecimal("10000"), SUPERVISOR_ID, "Safe drop"));

        verify(cashLedger).post(any(), eq(COMPANY_ID), eq(BRANCH_ID), eq(BUSINESS_DATE),
            eq(CashAccount.TILL), eq(CashDirection.OUT), eq(new BigDecimal("10000")),
            eq(BigDecimal.ONE), eq("TZS"), eq(CashRefType.CASH_PICKUP),
            any(), any(), any(), eq(ACTOR_ID));
        verify(cashLedger).post(any(), eq(COMPANY_ID), eq(BRANCH_ID), eq(BUSINESS_DATE),
            eq(CashAccount.CASH_BOX), eq(CashDirection.IN), eq(new BigDecimal("10000")),
            eq(BigDecimal.ONE), eq("TZS"), eq(CashRefType.CASH_PICKUP),
            any(), any(), any(), eq(ACTOR_ID));
        verify(cashLedger, times(2)).post(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any());
        verify(events).publish(eq("CashPickupRecorded.v1"), any(), any(), any());
    }

    @Test
    void post_rejectsClosedSession() {
        TillSession session = openSession();
        session.close(new BigDecimal("50000"), new BigDecimal("50000"), ACTOR_ID, null, null);
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));

        PostCashPickupRequestDto req = new PostCashPickupRequestDto(
            SESSION_ID, new BigDecimal("10000"), SUPERVISOR_ID, null);
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(TillSessionStatus.CLOSED.name());
        verify(pickups, never()).save(any());
        verify(cashLedger, never()).post(any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void post_rejectsSelfAuthorisation() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(openSession()));

        PostCashPickupRequestDto req = new PostCashPickupRequestDto(
            SESSION_ID, new BigDecimal("10000"), ACTOR_ID, null);
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("authorise your own");
        verify(pickups, never()).save(any());
    }

    @Test
    void post_rejectsAuthoriserWithoutPermission() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(openSession()));
        when(permissions.resolve(SUPERVISOR_ID, COMPANY_ID, null)).thenReturn(Set.of());

        PostCashPickupRequestDto req = new PostCashPickupRequestDto(
            SESSION_ID, new BigDecimal("10000"), SUPERVISOR_ID, null);
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("POS.CASH_PICKUP");
        verify(pickups, never()).save(any());
    }

    @Test
    void post_rejectsClosedBusinessDay() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(openSession()));
        when(permissions.resolve(SUPERVISOR_ID, COMPANY_ID, null))
            .thenReturn(Set.of("POS.CASH_PICKUP"));
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenThrow(new IllegalStateException("No open business day"));

        PostCashPickupRequestDto req = new PostCashPickupRequestDto(
            SESSION_ID, new BigDecimal("10000"), SUPERVISOR_ID, null);
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalStateException.class);
        verify(pickups, never()).save(any());
    }
}
