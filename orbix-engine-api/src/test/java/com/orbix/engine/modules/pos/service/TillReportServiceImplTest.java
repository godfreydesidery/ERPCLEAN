package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.pos.domain.dto.TillReportDto;
import com.orbix.engine.modules.pos.domain.entity.PettyCash;
import com.orbix.engine.modules.pos.domain.entity.PosPayment;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.PettyCashCategory;
import com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.repository.CashPickupRepository;
import com.orbix.engine.modules.pos.repository.PettyCashRepository;
import com.orbix.engine.modules.pos.repository.PosPaymentRepository;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TillReportServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long TILL_ID = 100L;
    private static final Long SESSION_ID = 200L;
    private static final Long ACTOR_ID = 4L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 5, 15);

    @Mock private TillSessionRepository sessions;
    @Mock private PosSaleRepository sales;
    @Mock private PosPaymentRepository payments;
    @Mock private CashPickupRepository pickups;
    @Mock private PettyCashRepository pettyCash;
    @Mock private RequestContext context;

    @InjectMocks private TillReportServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(pickups.sumForSession(SESSION_ID)).thenReturn(BigDecimal.ZERO);
        lenient().when(pettyCash.sumForSession(SESSION_ID)).thenReturn(BigDecimal.ZERO);
        lenient().when(pettyCash.findByTillSessionIdOrderByAtAsc(SESSION_ID)).thenReturn(List.of());
        lenient().when(sales.findByTillSessionIdOrderByIdAsc(SESSION_ID)).thenReturn(List.of());
    }

    private TillSession openSession() {
        TillSession s = new TillSession(TILL_ID, BRANCH_ID, COMPANY_ID, BUSINESS_DATE, ACTOR_ID,
            new BigDecimal("50000"));
        s.setId(SESSION_ID);
        return s;
    }

    private PosSale sale(Long id, PosSaleKind kind, BigDecimal total) {
        PosSale s = new PosSale(
            "POS-" + id, "op-" + id, SESSION_ID, TILL_ID, BRANCH_ID, COMPANY_ID,
            33L, 540L, ACTOR_ID, null,
            kind, Instant.now(), BUSINESS_DATE,
            total, BigDecimal.ZERO, BigDecimal.ZERO,
            total, total, BigDecimal.ZERO, null);
        s.setId(id);
        return s;
    }

    private PosPayment cashPayment(Long saleId, BigDecimal amount) {
        return new PosPayment(saleId, PosPaymentMethod.CASH, amount, "TZS", amount,
            BigDecimal.ONE, null, null, null);
    }

    private PosPayment cardPayment(Long saleId, BigDecimal amount) {
        return new PosPayment(saleId, PosPaymentMethod.CARD, amount, "TZS", amount,
            BigDecimal.ONE, null, null, null);
    }

    @Test
    void xReport_emptyOpenSession_isJustOpeningFloat() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(openSession()));

        TillReportDto r = service.xReport(SESSION_ID);

        assertThat(r.reportType()).isEqualTo(TillReportDto.ReportType.X);
        assertThat(r.status()).isEqualTo(TillSessionStatus.OPEN);
        assertThat(r.salesCount()).isZero();
        assertThat(r.openingFloat()).isEqualByComparingTo("50000");
        assertThat(r.expectedCash()).isEqualByComparingTo("50000");
        assertThat(r.declaredCash()).isNull();
        assertThat(r.variance()).isNull();
    }

    @Test
    void xReport_rejectsClosedSession() {
        TillSession closed = openSession();
        closed.close(new BigDecimal("50000"), new BigDecimal("50000"), ACTOR_ID, null, null);
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.xReport(SESSION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("X-report requires an OPEN");
    }

    @Test
    void zReport_rejectsOpenSession() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(openSession()));

        assertThatThrownBy(() -> service.zReport(SESSION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Z-report requires");
    }

    @Test
    void xReport_foldsMixedTendersRefundsVoidsPickupsAndPetty() {
        PosSale s1 = sale(901L, PosSaleKind.SALE, new BigDecimal("30000"));    // cash
        PosSale s2 = sale(902L, PosSaleKind.SALE, new BigDecimal("20000"));    // card + cash mixed
        PosSale s3 = sale(903L, PosSaleKind.SALE, new BigDecimal("10000"));    // voided
        s3.voidSale("oops", ACTOR_ID);
        PosSale r1 = sale(904L, PosSaleKind.REFUND, new BigDecimal("5000"));    // cash refund

        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(openSession()));
        when(sales.findByTillSessionIdOrderByIdAsc(SESSION_ID)).thenReturn(List.of(s1, s2, s3, r1));
        when(payments.findByPosSaleIdOrderByIdAsc(901L)).thenReturn(List.of(
            cashPayment(901L, new BigDecimal("30000"))));
        when(payments.findByPosSaleIdOrderByIdAsc(902L)).thenReturn(List.of(
            cashPayment(902L, new BigDecimal("8000")),
            cardPayment(902L, new BigDecimal("12000"))));
        when(payments.findByPosSaleIdOrderByIdAsc(903L)).thenReturn(List.of(
            cashPayment(903L, new BigDecimal("10000"))));  // voided — should not affect tender totals
        when(payments.findByPosSaleIdOrderByIdAsc(904L)).thenReturn(List.of(
            cashPayment(904L, new BigDecimal("5000"))));
        when(pickups.sumForSession(SESSION_ID)).thenReturn(new BigDecimal("10000"));
        when(pettyCash.sumForSession(SESSION_ID)).thenReturn(new BigDecimal("2000"));
        when(pettyCash.findByTillSessionIdOrderByAtAsc(SESSION_ID)).thenReturn(List.of(
            new PettyCash(SESSION_ID, COMPANY_ID, BRANCH_ID, BUSINESS_DATE,
                new BigDecimal("2000"), Instant.now(), PettyCashCategory.TRANSPORT,
                null, ACTOR_ID, 9L, null, null)));

        TillReportDto r = service.xReport(SESSION_ID);

        assertThat(r.salesCount()).isEqualTo(2);    // s1 + s2 only (s3 voided)
        assertThat(r.refundsCount()).isEqualTo(1);  // r1
        assertThat(r.voidsCount()).isEqualTo(1);    // s3
        assertThat(r.grossSales()).isEqualByComparingTo("50000");
        assertThat(r.grossRefunds()).isEqualByComparingTo("5000");
        assertThat(r.netSales()).isEqualByComparingTo("45000");
        assertThat(r.tenderByMethod().get(PosPaymentMethod.CASH)).isEqualByComparingTo("38000");
        assertThat(r.tenderByMethod().get(PosPaymentMethod.CARD)).isEqualByComparingTo("12000");
        assertThat(r.refundByMethod().get(PosPaymentMethod.CASH)).isEqualByComparingTo("5000");
        assertThat(r.cashPickupTotal()).isEqualByComparingTo("10000");
        assertThat(r.pettyCashTotal()).isEqualByComparingTo("2000");
        assertThat(r.pettyCashByCategory().get(PettyCashCategory.TRANSPORT)).isEqualByComparingTo("2000");
        // 50000 + 38000 (cash in) − 5000 (cash refund) − 10000 (pickup) − 2000 (petty) = 71000
        assertThat(r.expectedCash()).isEqualByComparingTo("71000");
    }

    @Test
    void zReport_returnsVarianceCapturedAtClose() {
        TillSession closed = openSession();
        closed.close(new BigDecimal("50000"), new BigDecimal("50500"), ACTOR_ID, null, "ok");
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(closed));

        TillReportDto r = service.zReport(SESSION_ID);

        assertThat(r.reportType()).isEqualTo(TillReportDto.ReportType.Z);
        assertThat(r.status()).isEqualTo(TillSessionStatus.CLOSED);
        assertThat(r.declaredCash()).isEqualByComparingTo("50500");
        assertThat(r.variance()).isEqualByComparingTo("500");
    }

    @Test
    void session_notFound_throws() {
        when(sessions.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.xReport(404L))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void session_crossCompany_throws() {
        TillSession other = new TillSession(TILL_ID, BRANCH_ID, 999L, BUSINESS_DATE, ACTOR_ID,
            new BigDecimal("50000"));
        other.setId(SESSION_ID);
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.xReport(SESSION_ID))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
