package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.service.CashLedgerService;
import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.SettingsService;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.pos.domain.dto.TillSessionBalanceDto;
import com.orbix.engine.modules.pos.domain.entity.PosPayment;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.repository.CashPickupRepository;
import com.orbix.engine.modules.pos.repository.PettyCashRepository;
import com.orbix.engine.modules.pos.repository.PosPaymentRepository;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import com.orbix.engine.modules.pos.repository.TillRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TillSessionServiceImpl#getBalance(String)} (ISSUE-CASH-001).
 * Verifies the breakdown components and the expectedCash formula consistency
 * with the close-time computation.
 */
@ExtendWith(MockitoExtension.class)
class TillSessionBalanceServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long TILL_ID = 100L;
    private static final Long PRICE_LIST_ID = 5L;
    private static final Long ACTOR_ID = 4L;

    @Mock private TillSessionRepository sessions;
    @Mock private TillRepository tills;
    @Mock private PosSaleRepository sales;
    @Mock private PosPaymentRepository payments;
    @Mock private CashPickupRepository pickups;
    @Mock private PettyCashRepository pettyCash;
    @Mock private CompanyRepository companies;
    @Mock private DayGuard dayGuard;
    @Mock private CashLedgerService cashLedger;
    @Mock private PermissionResolverService permissions;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;
    @Mock private SettingsService settings;

    @InjectMocks private TillSessionServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(9000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(settings.getDecimal(SettingKey.POS_SESSION_VARIANCE_THRESHOLD))
            .thenReturn(new BigDecimal("1000"));
        lenient().when(sales.findByTillSessionIdOrderByIdAsc(any())).thenReturn(List.of());
        lenient().when(pickups.sumForSession(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(pettyCash.sumForSession(any())).thenReturn(BigDecimal.ZERO);

        Company company = new Company(1L, "C", "Co.", "TZS", "TZ", "Africa/Dar_es_Salaam", ACTOR_ID);
        company.setId(COMPANY_ID);
        lenient().when(companies.findById(COMPANY_ID)).thenReturn(Optional.of(company));
    }

    @Test
    void getBalance_noActivity_returnsOpeningFloatAsExpectedCash() {
        TillSession session = openSession(new BigDecimal("50000"));
        when(sessions.findByUid(session.getUid())).thenReturn(Optional.of(session));

        TillSessionBalanceDto dto = service.getBalance(session.getUid());

        assertThat(dto.sessionId()).isEqualTo(session.getId());
        assertThat(dto.sessionUid()).isEqualTo(session.getUid());
        assertThat(dto.openingFloat()).isEqualByComparingTo("50000");
        assertThat(dto.cashSales()).isEqualByComparingTo("0");
        assertThat(dto.cashPickups()).isEqualByComparingTo("0");
        assertThat(dto.pettyCash()).isEqualByComparingTo("0");
        assertThat(dto.expectedCash()).isEqualByComparingTo("50000");
    }

    @Test
    void getBalance_withSalesPickupsAndPettyCash_computesCorrectBreakdown() {
        // opening 50,000
        // + cash sale 30,000
        // − cash refund 5,000  → cashSales net = 25,000
        // − pickup 10,000
        // − petty cash 2,000
        // = expected 63,000
        TillSession session = openSession(new BigDecimal("50000"));
        when(sessions.findByUid(session.getUid())).thenReturn(Optional.of(session));

        PosSale sale = posSale("POS-1", "op-1", session.getId(), PosSaleKind.SALE,
            new BigDecimal("30000"));
        sale.setId(900L);
        PosSale refund = posSale("POS-2", "op-2", session.getId(), PosSaleKind.REFUND,
            new BigDecimal("5000"));
        refund.setId(901L);

        when(sales.findByTillSessionIdOrderByIdAsc(session.getId()))
            .thenReturn(List.of(sale, refund));
        when(payments.findByPosSaleIdOrderByIdAsc(900L)).thenReturn(List.of(
            cashPayment(900L, new BigDecimal("30000"))));
        when(payments.findByPosSaleIdOrderByIdAsc(901L)).thenReturn(List.of(
            cashPayment(901L, new BigDecimal("5000"))));
        when(pickups.sumForSession(session.getId())).thenReturn(new BigDecimal("10000"));
        when(pettyCash.sumForSession(session.getId())).thenReturn(new BigDecimal("2000"));

        TillSessionBalanceDto dto = service.getBalance(session.getUid());

        assertThat(dto.openingFloat()).isEqualByComparingTo("50000");
        assertThat(dto.cashSales()).isEqualByComparingTo("25000");  // 30000 - 5000
        assertThat(dto.cashPickups()).isEqualByComparingTo("10000");
        assertThat(dto.pettyCash()).isEqualByComparingTo("2000");
        assertThat(dto.expectedCash()).isEqualByComparingTo("63000");
    }

    @Test
    void getBalance_voidedSaleContributesZeroToCashSales() {
        TillSession session = openSession(new BigDecimal("50000"));
        when(sessions.findByUid(session.getUid())).thenReturn(Optional.of(session));

        PosSale voided = posSale("POS-V", "op-v", session.getId(), PosSaleKind.SALE,
            new BigDecimal("20000"));
        voided.setId(902L);
        voided.voidSale("test", ACTOR_ID);

        when(sales.findByTillSessionIdOrderByIdAsc(session.getId()))
            .thenReturn(List.of(voided));

        TillSessionBalanceDto dto = service.getBalance(session.getUid());

        assertThat(dto.cashSales()).isEqualByComparingTo("0");
        assertThat(dto.expectedCash()).isEqualByComparingTo("50000");
    }

    @Test
    void getBalance_onClosedSession_returnsConsistentExpectedCash() {
        // Closing a session stores expectedCashAmount — the balance query on a
        // closed session should return the same value so the UI is consistent.
        TillSession session = openSession(new BigDecimal("50000"));
        session.close(new BigDecimal("50000"), new BigDecimal("50200"), ACTOR_ID, null, null);
        when(sessions.findByUid(session.getUid())).thenReturn(Optional.of(session));

        TillSessionBalanceDto dto = service.getBalance(session.getUid());

        // No sales/pickups/pettyCash → expectedCash = opening float.
        assertThat(dto.expectedCash()).isEqualByComparingTo("50000");
    }

    // --- helpers ---

    private TillSession openSession(BigDecimal openingFloat) {
        TillSession session = new TillSession(TILL_ID, BRANCH_ID, COMPANY_ID,
            LocalDate.of(2026, 5, 13), ACTOR_ID, openingFloat);
        session.setId(nextId.getAndIncrement());
        ReflectionTestUtils.setField(session, "uid", UidGenerator.next());
        return session;
    }

    private PosSale posSale(String number, String clientOpId, Long sessionId,
                            PosSaleKind kind, BigDecimal total) {
        return new PosSale(
            number, clientOpId, sessionId, TILL_ID, BRANCH_ID, COMPANY_ID,
            33L, 540L, ACTOR_ID, null,
            kind,
            Instant.now(), LocalDate.of(2026, 5, 13),
            total, BigDecimal.ZERO, BigDecimal.ZERO,
            total, total, BigDecimal.ZERO, null
        );
    }

    private PosPayment cashPayment(Long saleId, BigDecimal amount) {
        return new PosPayment(
            saleId, PosPaymentMethod.CASH, amount, "TZS", amount, BigDecimal.ONE,
            null, null, null
        );
    }
}
