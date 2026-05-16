package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.pos.domain.dto.TillReportDto;
import com.orbix.engine.modules.pos.domain.entity.CashPickup;
import com.orbix.engine.modules.pos.domain.entity.PettyCash;
import com.orbix.engine.modules.pos.domain.entity.PosPayment;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.PettyCashCategory;
import com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.repository.CashPickupRepository;
import com.orbix.engine.modules.pos.repository.PettyCashRepository;
import com.orbix.engine.modules.pos.repository.PosPaymentRepository;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TillReportServiceImpl implements TillReportService {

    private final TillSessionRepository sessions;
    private final PosSaleRepository sales;
    private final PosPaymentRepository payments;
    private final CashPickupRepository pickups;
    private final PettyCashRepository pettyCash;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional(readOnly = true)
    public TillReportDto xReport(Long tillSessionId) {
        TillSession session = requireSession(tillSessionId);
        if (session.getStatus() != TillSessionStatus.OPEN) {
            throw new IllegalStateException(
                "X-report requires an OPEN till session (was " + session.getStatus() + ")");
        }
        return build(session, TillReportDto.ReportType.X);
    }

    @Override
    @Transactional(readOnly = true)
    public TillReportDto zReport(Long tillSessionId) {
        TillSession session = requireSession(tillSessionId);
        if (session.getStatus() == TillSessionStatus.OPEN) {
            throw new IllegalStateException(
                "Z-report requires a CLOSED or RECONCILED till session (was OPEN)");
        }
        return build(session, TillReportDto.ReportType.Z);
    }

    private TillReportDto build(TillSession session, TillReportDto.ReportType reportType) {
        List<PosSale> sessionSales = sales.findByTillSessionIdOrderByIdAsc(session.getId());
        Map<Long, List<PosPayment>> paymentsBySale = loadPaymentsBySale(sessionSales);

        Aggregate sale = aggregate(sessionSales, paymentsBySale, PosSaleKind.SALE);
        Aggregate refund = aggregate(sessionSales, paymentsBySale, PosSaleKind.REFUND);
        int voidsCount = (int) sessionSales.stream()
            .filter(s -> s.getStatus() == PosSaleStatus.VOIDED)
            .count();

        BigDecimal pickupTotal = pickups.sumForSession(session.getId());
        BigDecimal pettyTotal = pettyCash.sumForSession(session.getId());
        Map<PettyCashCategory, BigDecimal> pettyByCategory =
            pettyCash.findByTillSessionIdOrderByAtAsc(session.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                    PettyCash::getCategory,
                    PettyCash::getAmount,
                    BigDecimal::add,
                    () -> new EnumMap<>(PettyCashCategory.class)));

        BigDecimal cashIn = sale.tender.getOrDefault(PosPaymentMethod.CASH, BigDecimal.ZERO);
        BigDecimal cashOut = refund.tender.getOrDefault(PosPaymentMethod.CASH, BigDecimal.ZERO);
        BigDecimal expectedCash = session.getOpeningFloatAmount()
            .add(cashIn).subtract(cashOut)
            .subtract(pickupTotal).subtract(pettyTotal);

        BigDecimal netSales = sale.gross.subtract(refund.gross);

        return new TillReportDto(
            reportType,
            session.getId(), session.getTillId(), session.getBranchId(),
            session.getBusinessDate(), session.getStatus(),
            session.getOpenedBy(), session.getSupervisorId(),
            session.getOpenedAt(), session.getClosedAt(),
            Instant.now(),
            sale.count, refund.count, voidsCount,
            sale.gross, refund.gross, netSales,
            sale.discount, sale.tax,
            sale.tender, refund.tender,
            pickupTotal, pettyTotal, pettyByCategory,
            session.getOpeningFloatAmount(), expectedCash,
            session.getDeclaredCashAmount(), session.getVarianceAmount(),
            sale.refs, refund.refs, voidedRefs(sessionSales)
        );
    }

    private Aggregate aggregate(List<PosSale> rows,
                                Map<Long, List<PosPayment>> paymentsBySale,
                                PosSaleKind kind) {
        Aggregate a = new Aggregate();
        for (PosSale s : rows) {
            if (s.getStatus() != PosSaleStatus.POSTED || s.getKind() != kind) {
                continue;
            }
            a.count++;
            a.gross = a.gross.add(s.getTotalAmount());
            a.discount = a.discount.add(s.getDiscountAmount() != null ? s.getDiscountAmount() : BigDecimal.ZERO);
            a.tax = a.tax.add(s.getTaxAmount() != null ? s.getTaxAmount() : BigDecimal.ZERO);
            a.refs.add(new TillReportDto.LineRef(s.getId(), s.getNumber(), s.getTotalAmount(), s.getSaleAt()));
            for (PosPayment p : paymentsBySale.getOrDefault(s.getId(), List.of())) {
                a.tender.merge(p.getMethod(), p.getAmount(), BigDecimal::add);
            }
        }
        return a;
    }

    private List<TillReportDto.LineRef> voidedRefs(List<PosSale> rows) {
        List<TillReportDto.LineRef> out = new ArrayList<>();
        for (PosSale s : rows) {
            if (s.getStatus() == PosSaleStatus.VOIDED) {
                out.add(new TillReportDto.LineRef(s.getId(), s.getNumber(), s.getTotalAmount(), s.getSaleAt()));
            }
        }
        return out;
    }

    private Map<Long, List<PosPayment>> loadPaymentsBySale(List<PosSale> rows) {
        Map<Long, List<PosPayment>> map = new HashMap<>();
        for (PosSale s : rows) {
            map.put(s.getId(), payments.findByPosSaleIdOrderByIdAsc(s.getId()));
        }
        return map;
    }

    private TillSession requireSession(Long id) {
        TillSession session = sessions.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Till session not found: " + id));
        if (!Objects.equals(session.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Till session not found: " + id);
        }
        branchScope.requireAccess(session.getBranchId());
        return session;
    }

    private static final class Aggregate {
        int count;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        Map<PosPaymentMethod, BigDecimal> tender = new EnumMap<>(PosPaymentMethod.class);
        List<TillReportDto.LineRef> refs = new ArrayList<>();
    }
}
