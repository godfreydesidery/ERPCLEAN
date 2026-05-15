package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.admin.domain.entity.FxRate;
import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.admin.repository.FxRateRepository;
import com.orbix.engine.modules.admin.repository.SectionRepository;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.pos.domain.dto.PosSaleDto;
import com.orbix.engine.modules.pos.domain.dto.PostPosRefundRequestDto;
import com.orbix.engine.modules.pos.domain.dto.PostPosSaleRequestDto;
import com.orbix.engine.modules.pos.domain.dto.VoidPosSaleRequestDto;
import com.orbix.engine.modules.pos.domain.entity.PosPayment;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.PosSaleLine;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.repository.PosPaymentRepository;
import com.orbix.engine.modules.pos.repository.PosSaleLineRepository;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import com.orbix.engine.modules.pos.repository.TillCurrencyRepository;
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

    static final String DISCOUNT_APPROVE_PERMISSION = "POS.DISCOUNT_APPROVE";
    static final String REFUND_APPROVE_PERMISSION = "POS.REFUND_APPROVE";

    private final PosSaleRepository sales;
    private final PosSaleLineRepository lines;
    private final PosPaymentRepository payments;
    private final TillSessionRepository tillSessions;
    private final TillCurrencyRepository tillCurrencies;
    private final SectionRepository sections;
    private final ItemRepository items;
    private final VatGroupRepository vatGroups;
    private final CustomerRepository customers;
    private final CompanyRepository companies;
    private final FxRateRepository fxRates;
    private final ItemBranchBalanceRepository balances;
    private final StockMoveService stockMoveService;
    private final StockBatchService stockBatchService;
    private final DayGuard dayGuard;
    private final PermissionResolverService permissions;
    private final EventPublisher events;
    private final RequestContext context;

    @org.springframework.beans.factory.annotation.Value("${orbix.pos.discount-threshold-pct}")
    private BigDecimal discountThresholdPct;

    @org.springframework.beans.factory.annotation.Value("${orbix.pos.refund-threshold}")
    private BigDecimal refundThreshold;

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
        validateDiscountApprover(request, actorId, companyId);
        if (sales.existsByCompanyIdAndNumber(companyId, request.number())) {
            throw new IllegalArgumentException(
                "POS-sale number already exists: " + request.number());
        }

        // Compute line totals + tax, then apply optional header-level discount before tender check.
        Totals totals = computeTotals(request, companyId);
        String functional = requireCompanyCurrency(companyId);
        List<TenderResolution> tenders = resolveTenders(request.payments(),
            session.getTillId(), functional, request.saleAt());
        BigDecimal tenderSum = sumFunctional(tenders);
        if (tenderSum.compareTo(totals.total) < 0) {
            throw new IllegalArgumentException(
                "Tender sum " + tenderSum + " is less than total " + totals.total);
        }
        BigDecimal changeAmount = tenderSum.subtract(totals.total);

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
            totals.subtotal, totals.headerDiscount, totals.tax, totals.total,
            tenderSum, changeAmount,
            request.notes()
        ));

        List<PosSaleLine> savedLines = saveLines(sale, request.lines(), companyId, totals);
        List<PosPayment> savedPayments = savePayments(sale, tenders);

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
    @Transactional
    @Auditable(action = "VOID", entityType = AGG)
    public PosSaleDto voidSale(Long saleId, VoidPosSaleRequestDto request) {
        PosSale sale = requireSale(saleId);
        BusinessDay day = dayGuard.requireOpenDay(sale.getBranchId());
        if (!Objects.equals(day.getBusinessDate(), sale.getBusinessDate())) {
            throw new IllegalArgumentException(
                "POS sales can only be voided on the same business day they were posted ("
                    + sale.getBusinessDate() + ")");
        }
        List<PosSaleLine> saleLines = lines.findByPosSaleIdOrderByLineNoAsc(sale.getId());
        Long companyId = context.companyId();
        for (PosSaleLine line : saleLines) {
            Item item = requireItem(line.getItemId(), companyId);
            if (item.isBatchTracked()) {
                throw new IllegalArgumentException(
                    "Cannot same-day-void a POS sale containing batch-tracked items (item "
                        + item.getCode() + "); use the F5.5 refund flow once it lands");
            }
            // Compensating inbound at the snapped line cost — keeps avg cost unchanged.
            stockMoveService.post(new PostStockMoveRequestDto(
                line.getItemId(), sale.getBranchId(),
                line.getQty(), line.getCostAmount(),
                StockMoveType.RETURN_IN, "PosSaleVoid", sale.getId(),
                request.reason(), false, null
            ));
        }
        sale.voidSale(request.reason(), context.userId());
        events.publish("PosSaleVoided.v1", AGG, String.valueOf(sale.getId()),
            Map.of(F_ID, sale.getId(),
                F_NUMBER, sale.getNumber(),
                "tillSessionId", sale.getTillSessionId(),
                F_BRANCH_ID, sale.getBranchId(),
                "totalAmount", sale.getTotalAmount(),
                "reason", request.reason()));
        return PosSaleDto.from(sale, saleLines,
            payments.findByPosSaleIdOrderByIdAsc(sale.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "REFUND", entityType = AGG)
    public PosSaleDto refund(PostPosRefundRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();

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
        PosSale original = requireRefundableOriginal(request.originalSaleId(), session.getBranchId());

        BusinessDay day = dayGuard.requireOpenDay(session.getBranchId());
        if (!Objects.equals(day.getBusinessDate(), original.getBusinessDate())) {
            throw new IllegalArgumentException(
                "POS sales can only be refunded on the same business day they were posted ("
                    + original.getBusinessDate() + ")");
        }

        Map<Long, BigDecimal> snapCosts = snapOriginalCosts(original.getId());
        RefundTotals totals = computeRefundTotals(request, companyId, snapCosts);
        String functional = requireCompanyCurrency(companyId);
        List<TenderResolution> tenders = resolveRefundTenders(request.payments(),
            session.getTillId(), functional, request.saleAt());
        BigDecimal tenderSum = sumFunctional(tenders);
        if (tenderSum.compareTo(totals.total) != 0) {
            throw new IllegalArgumentException(
                "Refund tender sum " + tenderSum + " must equal refund total " + totals.total
                    + " (no change is paid on a refund)");
        }
        validateRefundApprover(totals.total, request.supervisorId(), actorId, companyId);

        PosSale refund = sales.save(new PosSale(
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
            PosSaleKind.REFUND,
            request.saleAt(),
            session.getBusinessDate(),
            totals.subtotal, BigDecimal.ZERO, totals.tax, totals.total,
            tenderSum, BigDecimal.ZERO,
            request.notes()
        ));
        refund.setRefundedFromSaleId(original.getId());

        List<PosSaleLine> savedLines = saveRefundLines(refund, request.lines(), companyId, snapCosts);
        List<PosPayment> savedPayments = saveRefundPayments(refund, tenders);

        events.publish("PosSaleRefunded.v1", AGG, String.valueOf(refund.getId()),
            Map.of(F_ID, refund.getId(),
                F_NUMBER, refund.getNumber(),
                "originalSaleId", original.getId(),
                "tillSessionId", session.getId(),
                F_BRANCH_ID, session.getBranchId(),
                "totalAmount", refund.getTotalAmount(),
                "tenderedAmount", refund.getTenderedAmount()));
        return PosSaleDto.from(refund, savedLines, savedPayments);
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
        BigDecimal headerDiscount = request.headerDiscountAmount() != null
            ? request.headerDiscountAmount()
            : BigDecimal.ZERO;
        if (headerDiscount.signum() < 0) {
            throw new IllegalArgumentException("headerDiscountAmount must be >= 0");
        }
        if (headerDiscount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException(
                "headerDiscountAmount " + headerDiscount + " exceeds subtotal " + subtotal);
        }
        BigDecimal total = subtotal.subtract(headerDiscount).add(tax);
        return new Totals(subtotal, headerDiscount, tax, total);
    }

    private void validateDiscountApprover(PostPosSaleRequestDto request, Long actorId, Long companyId) {
        BigDecimal threshold = discountThresholdPct;
        boolean needsApproval = request.lines().stream()
            .map(PostPosSaleRequestDto.Line::discountPct)
            .filter(java.util.Objects::nonNull)
            .anyMatch(pct -> pct.compareTo(threshold) > 0);
        if (!needsApproval) {
            return;
        }
        Long approverId = request.discountApproverId();
        if (approverId == null) {
            throw new IllegalArgumentException(
                "Discount above " + threshold + "% requires an authoriser holding "
                    + DISCOUNT_APPROVE_PERMISSION);
        }
        if (Objects.equals(approverId, actorId)) {
            throw new IllegalArgumentException("You cannot authorise your own line discount");
        }
        boolean ok = permissions.resolve(approverId, companyId, null)
            .contains(DISCOUNT_APPROVE_PERMISSION);
        if (!ok) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Authoriser " + approverId + " does not hold " + DISCOUNT_APPROVE_PERMISSION);
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

    private List<PosPayment> savePayments(PosSale sale, List<TenderResolution> tenders) {
        List<PosPayment> saved = new ArrayList<>(tenders.size());
        for (TenderResolution tr : tenders) {
            saved.add(payments.save(new PosPayment(
                sale.getId(), tr.method(), tr.functionalAmount(),
                tr.currency(), tr.tenderAmount(), tr.fxRate(),
                tr.reference(), tr.terminalId(), tr.last4()
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

    private record Totals(BigDecimal subtotal, BigDecimal headerDiscount, BigDecimal tax,
                          BigDecimal total) {}

    /** Refund totals — no header discount applies, tender must match total exactly. */
    private record RefundTotals(BigDecimal subtotal, BigDecimal tax, BigDecimal total) {}

    /**
     * Per-payment resolution: snapped currency + tender amount + FX rate, plus
     * the functional-currency-converted value used to validate against the total
     * and persisted as {@code pos_payment.amount}.
     */
    @SuppressWarnings("java:S107")  // tender row fields are inherently wide
    private record TenderResolution(
        PosPaymentMethod method,
        String currency,
        BigDecimal tenderAmount,
        BigDecimal fxRate,
        BigDecimal functionalAmount,
        String reference,
        String terminalId,
        String last4
    ) {}

    private String requireCompanyCurrency(Long companyId) {
        return companies.findById(companyId)
            .orElseThrow(() -> new NoSuchElementException("Company not found: " + companyId))
            .getCurrencyCode();
    }

    private TenderResolution resolveTender(Long tillId, String functional, Instant at,
                                           PosPaymentMethod method, BigDecimal tenderAmount,
                                           String requestedCurrency, String reference,
                                           String terminalId, String last4) {
        String code = requestedCurrency == null || requestedCurrency.isBlank()
            ? functional
            : requestedCurrency.trim().toUpperCase();
        BigDecimal rate;
        BigDecimal functionalAmount;
        if (code.equals(functional)) {
            rate = BigDecimal.ONE;
            functionalAmount = tenderAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        } else {
            if (!tillCurrencies.existsByIdTillIdAndIdCurrencyCode(tillId, code)) {
                throw new IllegalArgumentException(
                    "Till " + tillId + " does not accept tender currency " + code);
            }
            FxRate fx = fxRates.findMostRecent(code, functional, at)
                .orElseThrow(() -> new IllegalArgumentException(
                    "No FX rate quoted for " + code + " -> " + functional + " at or before " + at));
            rate = fx.getRate();
            functionalAmount = tenderAmount.multiply(rate)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return new TenderResolution(method, code, tenderAmount, rate, functionalAmount,
            reference, terminalId, last4);
    }

    private List<TenderResolution> resolveTenders(List<PostPosSaleRequestDto.Payment> payments,
                                                  Long tillId, String functional, Instant at) {
        List<TenderResolution> out = new ArrayList<>(payments.size());
        for (PostPosSaleRequestDto.Payment p : payments) {
            out.add(resolveTender(tillId, functional, at, p.method(), p.amount(),
                p.tenderCurrency(), p.reference(), p.terminalId(), p.last4()));
        }
        return out;
    }

    private List<TenderResolution> resolveRefundTenders(List<PostPosRefundRequestDto.Payment> payments,
                                                        Long tillId, String functional, Instant at) {
        List<TenderResolution> out = new ArrayList<>(payments.size());
        for (PostPosRefundRequestDto.Payment p : payments) {
            out.add(resolveTender(tillId, functional, at, p.method(), p.amount(),
                p.tenderCurrency(), p.reference(), p.terminalId(), p.last4()));
        }
        return out;
    }

    private BigDecimal sumFunctional(List<TenderResolution> tenders) {
        return tenders.stream()
            .map(TenderResolution::functionalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PosSale requireRefundableOriginal(Long originalSaleId, Long sessionBranchId) {
        PosSale original = requireSale(originalSaleId);
        if (original.getStatus() != PosSaleStatus.POSTED) {
            throw new IllegalArgumentException(
                "Cannot refund POS sale " + original.getNumber() + " — it is "
                    + original.getStatus());
        }
        if (original.getKind() != PosSaleKind.SALE) {
            throw new IllegalArgumentException(
                "Cannot refund POS sale " + original.getNumber() + " — its kind is "
                    + original.getKind() + " (only SALE may be refunded)");
        }
        if (!Objects.equals(original.getBranchId(), sessionBranchId)) {
            throw new IllegalArgumentException(
                "Refund must be issued from the same branch as the original sale (branch "
                    + original.getBranchId() + ")");
        }
        return original;
    }

    private Map<Long, BigDecimal> snapOriginalCosts(Long originalSaleId) {
        java.util.HashMap<Long, BigDecimal> map = new java.util.HashMap<>();
        for (PosSaleLine line : lines.findByPosSaleIdOrderByLineNoAsc(originalSaleId)) {
            map.putIfAbsent(line.getItemId(), line.getCostAmount());
        }
        return map;
    }

    private RefundTotals computeRefundTotals(PostPosRefundRequestDto request,
                                             Long companyId,
                                             Map<Long, BigDecimal> snapCosts) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (PostPosRefundRequestDto.Line input : request.lines()) {
            Item item = requireItem(input.itemId(), companyId);
            if (item.isBatchTracked()) {
                throw new IllegalArgumentException(
                    "Cannot refund a POS sale containing batch-tracked items (item "
                        + item.getCode() + "); restore-to-original-batch is not supported yet");
            }
            if (!snapCosts.containsKey(input.itemId())) {
                throw new IllegalArgumentException(
                    "Refund line item " + item.getCode()
                        + " was not on the original sale");
            }
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
        return new RefundTotals(subtotal, tax, total);
    }

    private void validateRefundApprover(BigDecimal total, Long supervisorId, Long actorId, Long companyId) {
        if (total.compareTo(refundThreshold) <= 0) {
            return;
        }
        if (supervisorId == null) {
            throw new IllegalArgumentException(
                "Refund total " + total + " exceeds threshold " + refundThreshold
                    + " — a supervisor holding " + REFUND_APPROVE_PERMISSION + " must authorise");
        }
        if (Objects.equals(supervisorId, actorId)) {
            throw new IllegalArgumentException("You cannot authorise your own refund");
        }
        boolean ok = permissions.resolve(supervisorId, companyId, null)
            .contains(REFUND_APPROVE_PERMISSION);
        if (!ok) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Supervisor " + supervisorId + " does not hold " + REFUND_APPROVE_PERMISSION);
        }
    }

    private List<PosSaleLine> saveRefundLines(PosSale refund,
                                              List<PostPosRefundRequestDto.Line> requestLines,
                                              Long companyId,
                                              Map<Long, BigDecimal> snapCosts) {
        List<PosSaleLine> savedLines = new ArrayList<>(requestLines.size());
        int lineNo = 1;
        for (PostPosRefundRequestDto.Line input : requestLines) {
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
            BigDecimal snappedCost = snapCosts.get(input.itemId());

            PosSaleLine line = lines.save(new PosSaleLine(
                refund.getId(), lineNo++, input.itemId(), uomId,
                input.qty(), input.unitPrice(), discountPct, discountAmount,
                vatGroupId, lineTax, lineTotal
            ));
            // Compensating inbound at the original snapped cost — keeps avg cost stable.
            stockMoveService.post(new PostStockMoveRequestDto(
                line.getItemId(), refund.getBranchId(),
                line.getQty(), snappedCost,
                StockMoveType.RETURN_IN, AGG, refund.getId(),
                null, false, null
            ));
            line.setCostAmount(snappedCost);
            savedLines.add(line);
        }
        return savedLines;
    }

    private List<PosPayment> saveRefundPayments(PosSale refund, List<TenderResolution> tenders) {
        List<PosPayment> saved = new ArrayList<>(tenders.size());
        for (TenderResolution tr : tenders) {
            saved.add(payments.save(new PosPayment(
                refund.getId(), tr.method(), tr.functionalAmount(),
                tr.currency(), tr.tenderAmount(), tr.fxRate(),
                tr.reference(), tr.terminalId(), tr.last4()
            )));
        }
        return saved;
    }
}
