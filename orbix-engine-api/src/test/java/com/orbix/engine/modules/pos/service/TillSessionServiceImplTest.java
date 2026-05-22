package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.service.CashLedgerService;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.pos.domain.dto.CloseTillSessionRequestDto;
import com.orbix.engine.modules.pos.domain.dto.OpenTillSessionRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillSessionDto;
import com.orbix.engine.modules.pos.domain.entity.Till;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.domain.enums.TillStatus;
import com.orbix.engine.modules.pos.repository.TillRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
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
class TillSessionServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long TILL_ID = 100L;
    private static final Long PRICE_LIST_ID = 5L;
    private static final Long ACTOR_ID = 4L;
    private static final Long SUPERVISOR_ID = 9L;

    @Mock private TillSessionRepository sessions;
    @Mock private TillRepository tills;
    @Mock private com.orbix.engine.modules.pos.repository.PosSaleRepository sales;
    @Mock private com.orbix.engine.modules.pos.repository.PosPaymentRepository payments;
    @Mock private com.orbix.engine.modules.pos.repository.CashPickupRepository pickups;
    @Mock private com.orbix.engine.modules.pos.repository.PettyCashRepository pettyCash;
    @Mock private CompanyRepository companies;
    @Mock private DayGuard dayGuard;
    @Mock private CashLedgerService cashLedger;
    @Mock private PermissionResolverService permissions;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private TillSessionServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(8000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        ReflectionTestUtils.setField(service, "varianceThreshold", new BigDecimal("1000"));

        Till till = new Till(COMPANY_ID, BRANCH_ID, "TILL-1", "Main till", PRICE_LIST_ID, ACTOR_ID);
        till.setId(TILL_ID);
        lenient().when(tills.findById(TILL_ID)).thenReturn(Optional.of(till));

        Company company = new Company(1L, "C", "Co.", "TZS", "TZ", "Africa/Dar_es_Salaam", ACTOR_ID);
        company.setId(COMPANY_ID);
        lenient().when(companies.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        lenient().when(sessions.save(any(TillSession.class))).thenAnswer(inv -> {
            TillSession s = inv.getArgument(0);
            if (s.getId() == null) s.setId(nextId.getAndIncrement());
            return s;
        });

        // No sales / pickups / petty cash by default — close uses opening_float only.
        lenient().when(sales.findByTillSessionIdOrderByIdAsc(any())).thenReturn(java.util.List.of());
        lenient().when(pickups.sumForSession(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(pettyCash.sumForSession(any())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void open_succeeds_andEmitsOpened() {
        when(sessions.findFirstByTillIdAndStatus(TILL_ID, TillSessionStatus.OPEN))
            .thenReturn(Optional.empty());
        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        TillSessionDto dto = service.open(new OpenTillSessionRequestDto(TILL_ID, new BigDecimal("50000")));

        assertThat(dto.status()).isEqualTo(TillSessionStatus.OPEN);
        assertThat(dto.openingFloatAmount()).isEqualByComparingTo("50000");
        assertThat(dto.businessDate()).isEqualTo(LocalDate.of(2026, 5, 13));
        verify(events).publish(eq("TillSessionOpened.v1"), any(), any(), any());
    }

    @Test
    void open_rejectsWhenTillAlreadyHasOpenSession() {
        when(sessions.findFirstByTillIdAndStatus(TILL_ID, TillSessionStatus.OPEN))
            .thenReturn(Optional.of(existingOpenSession()));

        OpenTillSessionRequestDto request = new OpenTillSessionRequestDto(TILL_ID, BigDecimal.ZERO);
        assertThatThrownBy(() -> service.open(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already has an OPEN session");
    }

    @Test
    void open_rejectsInactiveTill() {
        Till inactive = new Till(COMPANY_ID, BRANCH_ID, "TILL-2", "Dead till", PRICE_LIST_ID, ACTOR_ID);
        inactive.setId(101L);
        inactive.setStatus(TillStatus.INACTIVE);
        when(tills.findById(101L)).thenReturn(Optional.of(inactive));

        OpenTillSessionRequestDto request = new OpenTillSessionRequestDto(101L, BigDecimal.ZERO);
        assertThatThrownBy(() -> service.open(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INACTIVE");
    }

    @Test
    void open_requiresOpenBusinessDay() {
        when(sessions.findFirstByTillIdAndStatus(TILL_ID, TillSessionStatus.OPEN))
            .thenReturn(Optional.empty());
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenThrow(new IllegalStateException("No open business day"));

        OpenTillSessionRequestDto request = new OpenTillSessionRequestDto(TILL_ID, BigDecimal.ZERO);
        assertThatThrownBy(() -> service.open(request))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void close_belowVarianceThreshold_succeeds() {
        TillSession session = openSession(new BigDecimal("50000"));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        // expected_cash = opening_float 50000; declared 50500 → variance +500 (within threshold 1000)
        TillSessionDto dto = service.close(session.getId(),
            new CloseTillSessionRequestDto(new BigDecimal("50500"), null, "ok"));

        assertThat(dto.status()).isEqualTo(TillSessionStatus.CLOSED);
        assertThat(dto.expectedCashAmount()).isEqualByComparingTo("50000");
        assertThat(dto.declaredCashAmount()).isEqualByComparingTo("50500");
        assertThat(dto.varianceAmount()).isEqualByComparingTo("500");
        verify(events).publish(eq("TillSessionClosed.v1"), any(), any(), any());
    }

    @Test
    void close_aboveVariance_withoutSupervisor_rejected() {
        TillSession session = openSession(new BigDecimal("50000"));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        Long id = session.getId();
        CloseTillSessionRequestDto req = new CloseTillSessionRequestDto(
            new BigDecimal("48000"), null, "short"); // variance -2000 > threshold 1000
        assertThatThrownBy(() -> service.close(id, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("supervisor");
    }

    @Test
    void close_aboveVariance_supervisorMissingPermission_403() {
        TillSession session = openSession(new BigDecimal("50000"));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(permissions.resolve(SUPERVISOR_ID, COMPANY_ID, null)).thenReturn(Set.of());

        Long id = session.getId();
        CloseTillSessionRequestDto req = new CloseTillSessionRequestDto(
            new BigDecimal("52000"), SUPERVISOR_ID, "over");
        assertThatThrownBy(() -> service.close(id, req))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining(TillSessionServiceImpl.VARIANCE_APPROVE_PERMISSION);
    }

    @Test
    void close_aboveVariance_selfSupervisor_rejected() {
        TillSession session = openSession(new BigDecimal("50000"));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        Long id = session.getId();
        CloseTillSessionRequestDto req = new CloseTillSessionRequestDto(
            new BigDecimal("52000"), ACTOR_ID, "over");
        assertThatThrownBy(() -> service.close(id, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("your own");
    }

    @Test
    void close_aboveVariance_withApprovedSupervisor_succeeds() {
        TillSession session = openSession(new BigDecimal("50000"));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(permissions.resolve(SUPERVISOR_ID, COMPANY_ID, null))
            .thenReturn(Set.of(TillSessionServiceImpl.VARIANCE_APPROVE_PERMISSION));

        TillSessionDto dto = service.close(session.getId(),
            new CloseTillSessionRequestDto(new BigDecimal("52000"), SUPERVISOR_ID, "over"));

        assertThat(dto.status()).isEqualTo(TillSessionStatus.CLOSED);
        assertThat(dto.supervisorId()).isEqualTo(SUPERVISOR_ID);
        assertThat(dto.varianceAmount()).isEqualByComparingTo("2000");
    }

    @Test
    void reconcile_fromClosed_succeeds() {
        TillSession session = openSession(new BigDecimal("50000"));
        session.close(new BigDecimal("50000"), new BigDecimal("50000"), ACTOR_ID, null, null);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        TillSessionDto dto = service.reconcile(session.getId());

        assertThat(dto.status()).isEqualTo(TillSessionStatus.RECONCILED);
        verify(events).publish(eq("TillSessionReconciled.v1"), any(), any(), any());
    }

    @Test
    void reconcile_fromOpen_rejected() {
        TillSession session = openSession(new BigDecimal("50000"));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        Long id = session.getId();
        assertThatThrownBy(() -> service.reconcile(id))
            .isInstanceOf(IllegalStateException.class);
        verify(events, never()).publish(eq("TillSessionReconciled.v1"), any(), any(), any());
    }

    @Test
    void close_expectedCashFoldsInSalesRefundsPickupsAndPettyCash() {
        // opening 50,000
        // + cash sale 30,000
        // − cash refund 5,000
        // − pickup 10,000
        // − petty cash 2,000
        // = expected 63,000. Declared 63,200 → variance +200 (within threshold 1000).
        TillSession session = openSession(new BigDecimal("50000"));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        com.orbix.engine.modules.pos.domain.entity.PosSale sale = new com.orbix.engine.modules.pos.domain.entity.PosSale(
            "POS-1", "op-1", session.getId(), TILL_ID, BRANCH_ID, COMPANY_ID,
            33L, 540L, ACTOR_ID, null,
            com.orbix.engine.modules.pos.domain.enums.PosSaleKind.SALE,
            java.time.Instant.now(), LocalDate.of(2026, 5, 13),
            new BigDecimal("30000"), BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("30000"), new BigDecimal("30000"), BigDecimal.ZERO, null);
        sale.setId(900L);
        com.orbix.engine.modules.pos.domain.entity.PosSale refund = new com.orbix.engine.modules.pos.domain.entity.PosSale(
            "POS-2", "op-2", session.getId(), TILL_ID, BRANCH_ID, COMPANY_ID,
            33L, 540L, ACTOR_ID, null,
            com.orbix.engine.modules.pos.domain.enums.PosSaleKind.REFUND,
            java.time.Instant.now(), LocalDate.of(2026, 5, 13),
            new BigDecimal("5000"), BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("5000"), new BigDecimal("5000"), BigDecimal.ZERO, null);
        refund.setId(901L);

        when(sales.findByTillSessionIdOrderByIdAsc(session.getId()))
            .thenReturn(java.util.List.of(sale, refund));
        when(payments.findByPosSaleIdOrderByIdAsc(900L)).thenReturn(java.util.List.of(
            new com.orbix.engine.modules.pos.domain.entity.PosPayment(
                900L, com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod.CASH,
                new BigDecimal("30000"), "TZS", new BigDecimal("30000"), BigDecimal.ONE,
                null, null, null)));
        when(payments.findByPosSaleIdOrderByIdAsc(901L)).thenReturn(java.util.List.of(
            new com.orbix.engine.modules.pos.domain.entity.PosPayment(
                901L, com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod.CASH,
                new BigDecimal("5000"), "TZS", new BigDecimal("5000"), BigDecimal.ONE,
                null, null, null)));
        when(pickups.sumForSession(session.getId())).thenReturn(new BigDecimal("10000"));
        when(pettyCash.sumForSession(session.getId())).thenReturn(new BigDecimal("2000"));

        TillSessionDto dto = service.close(session.getId(),
            new CloseTillSessionRequestDto(new BigDecimal("63200"), null, "ok"));

        assertThat(dto.expectedCashAmount()).isEqualByComparingTo("63000");
        assertThat(dto.declaredCashAmount()).isEqualByComparingTo("63200");
        assertThat(dto.varianceAmount()).isEqualByComparingTo("200");
    }

    @Test
    void close_voidedSaleContributesZeroToExpectedCash() {
        TillSession session = openSession(new BigDecimal("50000"));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        com.orbix.engine.modules.pos.domain.entity.PosSale voided = new com.orbix.engine.modules.pos.domain.entity.PosSale(
            "POS-V", "op-v", session.getId(), TILL_ID, BRANCH_ID, COMPANY_ID,
            33L, 540L, ACTOR_ID, null,
            com.orbix.engine.modules.pos.domain.enums.PosSaleKind.SALE,
            java.time.Instant.now(), LocalDate.of(2026, 5, 13),
            new BigDecimal("20000"), BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("20000"), new BigDecimal("20000"), BigDecimal.ZERO, null);
        voided.setId(902L);
        voided.voidSale("test", ACTOR_ID);  // → status = VOIDED

        when(sales.findByTillSessionIdOrderByIdAsc(session.getId()))
            .thenReturn(java.util.List.of(voided));

        TillSessionDto dto = service.close(session.getId(),
            new CloseTillSessionRequestDto(new BigDecimal("50000"), null, "ok"));

        assertThat(dto.expectedCashAmount()).isEqualByComparingTo("50000");
        assertThat(dto.varianceAmount()).isEqualByComparingTo("0");
    }

    private TillSession openSession(BigDecimal openingFloat) {
        TillSession session = new TillSession(TILL_ID, BRANCH_ID, COMPANY_ID,
            LocalDate.of(2026, 5, 13), ACTOR_ID, openingFloat);
        session.setId(nextId.getAndIncrement());
        return session;
    }

    private TillSession existingOpenSession() {
        return openSession(new BigDecimal("50000"));
    }
}
