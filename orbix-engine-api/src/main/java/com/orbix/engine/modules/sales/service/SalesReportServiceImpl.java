package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.cash.domain.entity.CashBook;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.repository.CashBookRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.pos.domain.dto.TillReportDto;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import com.orbix.engine.modules.pos.service.TillReportService;
import com.orbix.engine.modules.procurement.domain.entity.Grn;
import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;
import com.orbix.engine.modules.procurement.repository.GrnRepository;
import com.orbix.engine.modules.sales.domain.dto.DailySalesRowDto;
import com.orbix.engine.modules.sales.domain.dto.DailySummaryDto;
import com.orbix.engine.modules.sales.domain.dto.ZHistoryEntryDto;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SalesReportServiceImpl implements SalesReportService {

    private static final Logger log = LoggerFactory.getLogger(SalesReportServiceImpl.class);

    private final SalesInvoiceRepository invoices;
    private final PosSaleRepository posSales;
    private final GrnRepository grns;
    private final CashBookRepository cashBooks;
    private final TillSessionRepository tillSessions;
    private final TillReportService tillReports;
    private final RequestContext context;

    // ---------------------------------------------------------------------
    // Daily sales (US-RPT-001)
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<DailySalesRowDto> dailySales(Long branchId, LocalDate businessDate) {
        Long companyId = context.companyId();
        List<DailySalesRowDto> out = new ArrayList<>();
        for (SalesInvoice inv : invoices.findPostedOnDate(companyId, branchId, businessDate)) {
            out.add(DailySalesRowDto.from(inv));
        }
        List<PosSale> sales = branchId != null
            ? posSales.findByCompanyIdAndBranchIdAndBusinessDateOrderByIdAsc(
                companyId, branchId, businessDate)
            : posSales.findByCompanyIdAndBusinessDateOrderByIdAsc(companyId, businessDate);
        for (PosSale s : sales) {
            out.add(DailySalesRowDto.from(s));
        }
        out.sort(Comparator.comparing(DailySalesRowDto::occurredAt,
            Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    // ---------------------------------------------------------------------
    // Daily summary (US-RPT-002)
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public DailySummaryDto dailySummary(Long branchId, LocalDate businessDate) {
        Long companyId = context.companyId();

        BigDecimal invTotal = BigDecimal.ZERO;
        BigDecimal invTax = BigDecimal.ZERO;
        BigDecimal invDiscount = BigDecimal.ZERO;
        int invCount = 0;
        for (SalesInvoice inv : invoices.findPostedOnDate(companyId, branchId, businessDate)) {
            if (inv.getStatus() == SalesInvoiceStatus.VOIDED) continue;
            invTotal = invTotal.add(inv.getTotalAmount());
            invTax = invTax.add(inv.getTaxAmount());
            invDiscount = invDiscount.add(inv.getDiscountAmount());
            invCount++;
        }

        BigDecimal posNet = BigDecimal.ZERO;
        BigDecimal posTax = BigDecimal.ZERO;
        BigDecimal posDiscount = BigDecimal.ZERO;
        int posSaleCount = 0;
        int posRefundCount = 0;
        List<PosSale> sales = branchId != null
            ? posSales.findByCompanyIdAndBranchIdAndBusinessDateOrderByIdAsc(
                companyId, branchId, businessDate)
            : posSales.findByCompanyIdAndBusinessDateOrderByIdAsc(companyId, businessDate);
        for (PosSale s : sales) {
            if (s.getStatus() == PosSaleStatus.VOIDED) continue;
            BigDecimal signed = s.getKind() == PosSaleKind.REFUND
                ? s.getTotalAmount().negate() : s.getTotalAmount();
            posNet = posNet.add(signed);
            posTax = posTax.add(s.getKind() == PosSaleKind.REFUND
                ? s.getTaxAmount().negate() : s.getTaxAmount());
            posDiscount = posDiscount.add(s.getDiscountAmount());
            if (s.getKind() == PosSaleKind.REFUND) posRefundCount++;
            else posSaleCount++;
        }
        BigDecimal grandTotal = invTotal.add(posNet);
        DailySummaryDto.SalesBlock salesBlock = new DailySummaryDto.SalesBlock(
            invTotal, invTax, invDiscount, invCount,
            posNet, posTax, posDiscount, posSaleCount, posRefundCount,
            grandTotal);

        BigDecimal grnTotal = BigDecimal.ZERO;
        BigDecimal grnTax = BigDecimal.ZERO;
        int grnCount = 0;
        List<Grn> postedGrns = branchId != null
            ? grns.findByCompanyIdAndBranchIdAndReceivedDateAndStatus(
                companyId, branchId, businessDate, GrnStatus.POSTED)
            : grns.findByCompanyIdAndReceivedDateAndStatus(companyId, businessDate, GrnStatus.POSTED);
        for (Grn g : postedGrns) {
            grnTotal = grnTotal.add(g.getTotalAmount());
            grnTax = grnTax.add(g.getTaxAmount());
            grnCount++;
        }
        DailySummaryDto.PurchasesBlock purchasesBlock =
            new DailySummaryDto.PurchasesBlock(grnTotal, grnTax, grnCount);

        DailySummaryDto.CashBlock cashBlock = buildCashBlock(companyId, branchId, businessDate);

        return new DailySummaryDto(businessDate, branchId, salesBlock, purchasesBlock, cashBlock);
    }

    private DailySummaryDto.CashBlock buildCashBlock(Long companyId, Long branchId,
                                                     LocalDate businessDate) {
        List<CashBook> books = branchId != null
            ? cashBooks.findByIdBranchIdAndIdBusinessDate(branchId, businessDate)
            : cashBooks.findByCompanyIdAndIdBusinessDate(companyId, businessDate);
        // Branch-scoped query above bypasses company filter — re-apply it.
        if (branchId != null) {
            books = books.stream()
                .filter(b -> Objects.equals(b.getCompanyId(), companyId))
                .toList();
        }
        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        BigDecimal closing = BigDecimal.ZERO;
        Map<CashAccount, BigDecimal> closingByAccount = new EnumMap<>(CashAccount.class);
        for (CashBook b : books) {
            opening = opening.add(b.getOpeningAmount());
            in = in.add(b.getInAmount());
            out = out.add(b.getOutAmount());
            closing = closing.add(b.getClosingAmount());
            closingByAccount.merge(b.getAccount(), b.getClosingAmount(), BigDecimal::add);
        }
        return new DailySummaryDto.CashBlock(opening, in, out, closing, closingByAccount);
    }

    // ---------------------------------------------------------------------
    // Z-history (US-RPT-003)
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ZHistoryEntryDto> zHistory(Long branchId, LocalDate from, LocalDate to) {
        Long companyId = context.companyId();
        LocalDate start = from != null ? from : LocalDate.now().minusDays(7);
        LocalDate end = to != null ? to : LocalDate.now();
        List<TillSession> sessions = branchId != null
            ? tillSessions.findByCompanyIdAndBranchIdAndBusinessDateBetweenOrderByIdDesc(
                companyId, branchId, start, end)
            : tillSessions.findByCompanyIdAndBusinessDateBetweenOrderByIdDesc(companyId, start, end);

        List<ZHistoryEntryDto> out = new ArrayList<>();
        for (TillSession session : sessions) {
            if (session.getStatus() == TillSessionStatus.OPEN) continue;
            TillReportDto report;
            try {
                report = tillReports.zReport(session.getId());
            } catch (RuntimeException ex) {
                // Defensive — a single bad session shouldn't break the whole
                // window; surface as an entry without a report payload.
                log.warn("Z-report failed for tillSession {}: {}",
                    session.getId(), ex.getMessage());
                report = null;
            }
            out.add(new ZHistoryEntryDto(
                session.getId(),
                session.getTillId(),
                session.getBranchId(),
                session.getBusinessDate(),
                session.getStatus(),
                session.getOpenedAt(),
                session.getClosedAt(),
                report));
        }
        return out;
    }
}
