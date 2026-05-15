package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.repository.SectionRepository;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.pos.domain.dto.PosSaleDto;
import com.orbix.engine.modules.pos.domain.dto.PostPosSaleRequestDto;
import com.orbix.engine.modules.pos.domain.entity.PosPayment;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.PosSaleLine;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.repository.PosPaymentRepository;
import com.orbix.engine.modules.pos.repository.PosSaleLineRepository;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import com.orbix.engine.modules.stock.domain.dto.BatchPickDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.service.StockBatchService;
import com.orbix.engine.modules.stock.service.StockMoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PosSaleServiceImpl implements PosSaleService {

    private static final int MONEY_SCALE = 4;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String AGG = "PosSale";
    private static final String F_ID = "posSaleId";
    private static final String F_NUMBER = "number";
    private static final String F_BRANCH_ID = "branchId";

    private final PosSaleRepository sales;
    private final PosSaleLineRepository lines;
    private final PosPaymentRepository payments;
    private final TillSessionRepository tillSessions;
    private final SectionRepository sections;
    private final ItemRepository items;
    private final VatGroupRepository vatGroups;
    private final CustomerRepository customers;
    private final ItemBranchBalanceRepository balances;
    private final StockMoveService stockMoveService;
    private final StockBatchService stockBatchService;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public PosSaleDto post(PostPosSaleRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();

        // Idempotency: same clientOpId returns the original sale + its rows.
        Optional<PosSale> existing = sales.findByCompanyIdAndClientOpId(companyId, request.clientOpId());
        if (existing.isPresent()) {
            PosSale prior = existing.get();
            return PosSaleDto.from(prior,
                lines.findByPosSaleIdOrderByLineNoAsc(prior.getId()),
                payments.findByPosSaleIdOrderByIdAsc(prior.getId()));
        }

        TillSession session = requireOpenSession(request.tillSessionId(), companyId);
        Section section = requireSection(request.sectionId(), session.getBranchId());
        requireCustomer(request.customerId());
        if (sales.existsByCompanyIdAndNumber(companyId, request.number())) {
            throw new IllegalArgumentException(
                "POS-sale number already exists: " + request.number());
        }

        // Compute line totals + tax. POS sale prices include line discount but
        // header-level discount is unused in F5.2 (matches the typical till UI).
        Totals totals = computeTotals(request, companyId);
        validateTender(request, totals.total);
        BigDecimal changeAmount = totals.tenderSum.subtract(totals.total);

        PosSale sale = sales.save(new PosSale(
            request.number().trim(),
            request.clientOpId().trim(),
            session.getId(),
            session.getTillId(),
            session.getBranchId(),
            companyId,
            section.getId(),
            request.customerId(),
            actorId,
            request.supervisorId(),
            PosSaleKind.SALE,
            request.saleAt(),
            session.getBusinessDate(),
            totals.subtotal, BigDecimal.ZERO, totals.tax, totals.total,
            totals.tenderSum, changeAmount,
            request.notes()
        ));

        List<PosSaleLine> savedLines = saveLines(sale, request.lines(), companyId, totals);
        List<PosPayment> savedPayments = savePayments(sale, request.payments());

        events.publish("PosSaleClosed.v1", AGG, String.valueOf(sale.getId()),
            Map.of(F_ID, sale.getId(),
                F_NUMBER, sale.getNumber(),
                "tillSessionId", session.getId(),
                F_BRANCH_ID, session.getBranchId(),
                "totalAmount", sale.getTotalAmount(),
                "tenderedAmount", sale.getTenderedAmount(),
                "changeAmount", sale.getChangeAmount()));
        return PosSaleDto.from(sale, savedLines, savedPayments);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PosSaleDto> list(Long branchId) {
        Long companyId = context.companyId();
        List<PosSale> rows = branchId == null
            ? sales.findByCompanyIdOrderByIdDesc(companyId)
            : sales.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, branchId);
        return rows.stream()
            .map(s -> PosSaleDto.from(s,
                lines.findByPosSaleIdOrderByLineNoAsc(s.getId()),
                payments.findByPosSaleIdOrderByIdAsc(s.getId())))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PosSaleDto get(Long saleId) {
        PosSale sale = requireSale(saleId);
        return PosSaleDto.from(sale,
            lines.findByPosSaleIdOrderByLineNoAsc(sale.getId()),
            payments.findByPosSaleIdOrderByIdAsc(sale.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PosSaleDto> listForSession(Long tillSessionId) {
        return sales.findByTillSessionIdOrderByIdAsc(tillSessionId).stream()
            .filter(s -> Objects.equals(s.getCompanyId(), context.companyId()))
            .map(s -> PosSaleDto.from(s,
                lines.findByPosSaleIdOrderByLineNoAsc(s.getId()),
                payments.findByPosSaleIdOrderByIdAsc(s.getId())))
            .toList();
    }

    /** Header totals + per-line subtotal/tax computed before any persistence. */
    private Totals computeTotals(PostPosSaleRequestDto request, Long companyId) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (PostPosSaleRequestDto.Line input : request.lines()) {
            Item item = requireItem(input.itemId(), companyId);
            Long vatGroupId = input.vatGroupId() != null ? input.vatGroupId() : item.getVatGroupId();
            VatGroup vat = requireVatGroup(vatGroupId, companyId);
            BigDecimal discountPct = input.discountPct() != null ? input.discountPct() : BigDecimal.ZERO;

            BigDecimal gross = input.qty().multiply(input.unitPrice())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal discountAmount = gross.multiply(discountPct)
                .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal netLine = gross.subtract(discountAmount);
            BigDecimal lineTax = netLine.multiply(vat.getRate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            subtotal = subtotal.add(netLine);
            tax = tax.add(lineTax);
        }
        BigDecimal total = subtotal.add(tax);
        BigDecimal tenderSum = request.payments().stream()
            .map(PostPosSaleRequestDto.Payment::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Totals(subtotal, tax, total, tenderSum);
    }

    private void validateTender(PostPosSaleRequestDto request, BigDecimal total) {
        BigDecimal sum = request.payments().stream()
            .map(PostPosSaleRequestDto.Payment::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total) < 0) {
            throw new IllegalArgumentException(
                "Tender sum " + sum + " is less than total " + total);
        }
    }

    private List<PosSaleLine> saveLines(PosSale sale,
                                        List<PostPosSaleRequestDto.Line> requestLines,
                                        Long companyId, Totals ignoredTotals) {
        List<PosSaleLine> savedLines = new ArrayList<>(requestLines.size());
        int lineNo = 1;
        for (PostPosSaleRequestDto.Line input : requestLines) {
            Item item = requireItem(input.itemId(), companyId);
            Long uomId = input.uomId() != null ? input.uomId() : item.getUomId();
            Long vatGroupId = input.vatGroupId() != null ? input.vatGroupId() : item.getVatGroupId();
            VatGroup vat = requireVatGroup(vatGroupId, companyId);
            BigDecimal discountPct = input.discountPct() != null ? input.discountPct() : BigDecimal.ZERO;

            BigDecimal gross = input.qty().multiply(input.unitPrice())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal discountAmount = gross.multiply(discountPct)
                .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal netLine = gross.subtract(discountAmount);
            BigDecimal lineTax = netLine.multiply(vat.getRate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTotal = netLine.add(lineTax);

            PosSaleLine line = lines.save(new PosSaleLine(
                sale.getId(), lineNo++, input.itemId(), uomId,
                input.qty(), input.unitPrice(), discountPct, discountAmount,
                vatGroupId, lineTax, lineTotal
            ));
            postLineStockMoves(sale, line, item);
            savedLines.add(line);
        }
        return savedLines;
    }

    private void postLineStockMoves(PosSale sale, PosSaleLine line, Item item) {
        if (item.isBatchTracked()) {
            List<BatchPickDto> picks = stockBatchService.drainFefo(
                line.getItemId(), sale.getBranchId(), line.getQty());
            BigDecimal totalCostValue = BigDecimal.ZERO;
            BigDecimal totalPickQty = BigDecimal.ZERO;
            for (BatchPickDto pick : picks) {
                stockMoveService.post(new PostStockMoveRequestDto(
                    line.getItemId(), sale.getBranchId(),
                    pick.qty().negate(), pick.cost(),
                    StockMoveType.SALE, AGG, sale.getId(),
                    null, false, pick.batchId()
                ));
                totalCostValue = totalCostValue.add(pick.qty().multiply(pick.cost()));
                totalPickQty = totalPickQty.add(pick.qty());
            }
            BigDecimal lineCost = totalPickQty.signum() > 0
                ? totalCostValue.divide(totalPickQty, MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            line.setCostAmount(lineCost);
        } else {
            BigDecimal avgCost = balances.findById(
                    new ItemBranchBalanceId(line.getItemId(), sale.getBranchId()))
                .map(ItemBranchBalance::getAvgCost)
                .orElse(BigDecimal.ZERO);
            stockMoveService.post(new PostStockMoveRequestDto(
                line.getItemId(), sale.getBranchId(),
                line.getQty().negate(), null,
                StockMoveType.SALE, AGG, sale.getId(),
                null, false, null
            ));
            line.setCostAmount(avgCost);
        }
    }

    private List<PosPayment> savePayments(PosSale sale,
                                          List<PostPosSaleRequestDto.Payment> requestPayments) {
        List<PosPayment> saved = new ArrayList<>(requestPayments.size());
        for (PostPosSaleRequestDto.Payment input : requestPayments) {
            saved.add(payments.save(new PosPayment(
                sale.getId(), input.method(), input.amount(),
                input.reference(), input.terminalId(), input.last4()
            )));
        }
        return saved;
    }

    private PosSale requireSale(Long id) {
        PosSale sale = sales.findById(id)
            .orElseThrow(() -> new NoSuchElementException("POS sale not found: " + id));
        if (!Objects.equals(sale.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("POS sale not found: " + id);
        }
        return sale;
    }

    private TillSession requireOpenSession(Long sessionId, Long companyId) {
        TillSession session = tillSessions.findById(sessionId)
            .orElseThrow(() -> new NoSuchElementException("Till session not found: " + sessionId));
        if (!Objects.equals(session.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Till session not found: " + sessionId);
        }
        if (session.getStatus() != TillSessionStatus.OPEN) {
            throw new IllegalArgumentException(
                "Till session " + sessionId + " is " + session.getStatus() + " — cannot post a sale");
        }
        return session;
    }

    private Section requireSection(Long sectionId, Long branchId) {
        Section section = sections.findById(sectionId)
            .orElseThrow(() -> new NoSuchElementException("Section not found: " + sectionId));
        if (!Objects.equals(section.getBranchId(), branchId)) {
            throw new IllegalArgumentException(
                "Section " + sectionId + " does not belong to branch " + branchId);
        }
        return section;
    }

    private void requireCustomer(Long customerId) {
        customers.findById(customerId)
            .orElseThrow(() -> new NoSuchElementException("Customer not found: " + customerId));
    }

    private Item requireItem(Long itemId, Long companyId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }

    private VatGroup requireVatGroup(Long vatGroupId, Long companyId) {
        VatGroup vat = vatGroups.findById(vatGroupId)
            .orElseThrow(() -> new NoSuchElementException("VAT group not found: " + vatGroupId));
        if (!Objects.equals(vat.getCompanyId(), companyId)) {
            throw new NoSuchElementException("VAT group not found: " + vatGroupId);
        }
        return vat;
    }

    private record Totals(BigDecimal subtotal, BigDecimal tax, BigDecimal total, BigDecimal tenderSum) {}
}
