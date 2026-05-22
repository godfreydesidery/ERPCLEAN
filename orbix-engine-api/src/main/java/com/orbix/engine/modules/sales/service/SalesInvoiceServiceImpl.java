package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.party.domain.entity.Customer;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.sales.domain.dto.CreateSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesInvoiceDto;
import com.orbix.engine.modules.sales.domain.dto.VoidSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoiceLine;
import com.orbix.engine.modules.sales.domain.enums.PaymentTerms;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import com.orbix.engine.modules.sales.repository.SalesInvoiceLineRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.stock.domain.dto.BatchPickDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.service.StockBatchService;
import com.orbix.engine.modules.stock.service.StockMoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SalesInvoiceServiceImpl implements SalesInvoiceService {

    private static final int MONEY_SCALE = 4;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    static final String DISCOUNT_APPROVE_PERMISSION = "SALES.DISCOUNT_APPROVE";

    private static final String AGG = "SalesInvoice";
    private static final String F_ID = "salesInvoiceId";
    private static final String F_NUMBER = "number";

    private final SalesInvoiceRepository invoices;
    private final SalesInvoiceLineRepository lines;
    private final ItemRepository items;
    private final VatGroupRepository vatGroups;
    private final CustomerRepository customers;
    private final ItemBranchBalanceRepository balances;
    private final StockMoveService stockMoveService;
    private final StockBatchService stockBatchService;
    private final DayGuard dayGuard;
    private final PermissionResolverService permissions;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Value("${orbix.sales.discount-threshold-pct}")
    private BigDecimal discountThresholdPct;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG)
    public SalesInvoiceDto createDraft(CreateSalesInvoiceRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        branchScope.requireAccess(request.branchId());
        String number = request.number().trim().toUpperCase();
        if (invoices.existsByBranchIdAndNumber(request.branchId(), number)) {
            throw new IllegalArgumentException(
                "Sales invoice number already exists for this branch: " + number);
        }
        Customer customer = requireCustomer(request.customerId());
        validateDiscountApprover(request, actorId, companyId);
        validateLines(request, companyId);

        SalesInvoice invoice = invoices.save(new SalesInvoice(
            number, companyId, request.branchId(), request.customerId(),
            request.salesAgentId(), request.invoiceDate(), request.dueDate(),
            request.paymentTerms(), request.currencyCode(), request.priceListId(),
            request.reference(), request.notes(), actorId
        ));
        List<SalesInvoiceLine> savedLines = saveLinesAndRollUp(invoice, request.lines(), companyId);

        if (request.paymentTerms() == PaymentTerms.CREDIT) {
            checkCreditLimit(customer, invoice.getTotalAmount(), null);
        }

        events.publish("SalesInvoiceCreated.v1", AGG, String.valueOf(invoice.getId()),
            Map.of(F_ID, invoice.getId(), F_NUMBER, invoice.getNumber(),
                "customerId", invoice.getCustomerId(),
                "totalAmount", invoice.getTotalAmount(),
                "paymentTerms", invoice.getPaymentTerms().name()));
        return SalesInvoiceDto.from(invoice, savedLines);
    }

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public SalesInvoiceDto post(Long invoiceId) {
        SalesInvoice invoice = requireInvoice(invoiceId);
        Long actorId = context.userId();
        BusinessDay day = dayGuard.requireOpenDay(invoice.getBranchId());

        // Re-check credit limit at post time (state may have changed since draft).
        if (invoice.getPaymentTerms() == PaymentTerms.CREDIT) {
            Customer customer = requireCustomer(invoice.getCustomerId());
            checkCreditLimit(customer, invoice.getTotalAmount(), invoice.getId());
        }

        List<SalesInvoiceLine> invoiceLines = lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId());
        for (SalesInvoiceLine line : invoiceLines) {
            postLineStockMoves(invoice, line);
        }

        invoice.post(day.getBusinessDate(), actorId);
        events.publish("SalesInvoicePosted.v1", AGG, String.valueOf(invoice.getId()),
            Map.of(F_ID, invoice.getId(), F_NUMBER, invoice.getNumber(),
                "customerId", invoice.getCustomerId(),
                "branchId", invoice.getBranchId(),
                "totalAmount", invoice.getTotalAmount(),
                "paymentTerms", invoice.getPaymentTerms().name(),
                "currencyCode", invoice.getCurrencyCode()));
        return SalesInvoiceDto.from(invoice, invoiceLines);
    }

    @Override
    @Transactional
    @Auditable(action = "VOID", entityType = AGG)
    public SalesInvoiceDto voidInvoice(Long invoiceId, VoidSalesInvoiceRequestDto request) {
        SalesInvoice invoice = requireInvoice(invoiceId);
        if (invoice.getStatus() != SalesInvoiceStatus.POSTED) {
            throw new IllegalStateException(
                "Only POSTED invoices can be voided (was " + invoice.getStatus() + ")");
        }
        BusinessDay day = dayGuard.requireOpenDay(invoice.getBranchId());
        if (!Objects.equals(day.getBusinessDate(), invoice.getPostedBusinessDate())) {
            throw new IllegalArgumentException(
                "Sales invoices can only be voided on the same business day they were posted ("
                    + invoice.getPostedBusinessDate() + ")");
        }
        if (invoice.getPaidAmount().signum() > 0) {
            throw new IllegalArgumentException(
                "Cannot void an invoice that has already received payments; reverse the receipts first");
        }

        List<SalesInvoiceLine> invoiceLines = lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId());
        for (SalesInvoiceLine line : invoiceLines) {
            Item item = requireItem(line.getItemId(), invoice.getCompanyId());
            if (item.isBatchTracked()) {
                throw new IllegalArgumentException(
                    "Cannot same-day-void an invoice containing batch-tracked items;"
                        + " use a customer return (F4.4) instead");
            }
            // Compensating inbound at the snapped line cost — keeps avg cost unchanged.
            stockMoveService.post(new PostStockMoveRequestDto(
                line.getItemId(), invoice.getBranchId(),
                line.getQty(), line.getCostAmount(),
                StockMoveType.RETURN_IN, "SalesInvoiceVoid", invoice.getId(),
                request.reason(), false, null
            ));
        }

        invoice.voidInvoice(request.reason(), context.userId());
        events.publish("SalesInvoiceVoided.v1", AGG, String.valueOf(invoice.getId()),
            Map.of(F_ID, invoice.getId(), F_NUMBER, invoice.getNumber(),
                "customerId", invoice.getCustomerId(),
                "totalAmount", invoice.getTotalAmount(),
                "reason", request.reason()));
        return SalesInvoiceDto.from(invoice, invoiceLines);
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG)
    public SalesInvoiceDto cancel(Long invoiceId) {
        SalesInvoice invoice = requireInvoice(invoiceId);
        invoice.cancel(context.userId());
        events.publish("SalesInvoiceCancelled.v1", AGG, String.valueOf(invoice.getId()),
            Map.of(F_ID, invoice.getId(), F_NUMBER, invoice.getNumber()));
        return SalesInvoiceDto.from(invoice, lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceDto> list(Long branchId) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        List<SalesInvoice> rows = scope == null
            ? invoices.findByCompanyIdOrderByIdDesc(companyId)
            : invoices.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, scope);
        return rows.stream()
            .map(s -> SalesInvoiceDto.from(s, lines.findBySalesInvoiceIdOrderByLineNoAsc(s.getId())))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SalesInvoiceDto get(Long invoiceId) {
        SalesInvoice invoice = requireInvoice(invoiceId);
        return SalesInvoiceDto.from(invoice, lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId()));
    }

    private void postLineStockMoves(SalesInvoice invoice, SalesInvoiceLine line) {
        Long companyId = invoice.getCompanyId();
        Item item = requireItem(line.getItemId(), companyId);
        if (item.isBatchTracked()) {
            List<BatchPickDto> picks = stockBatchService.drainFefo(
                line.getItemId(), invoice.getBranchId(), line.getQty());
            BigDecimal totalCostValue = BigDecimal.ZERO;
            BigDecimal totalPickQty = BigDecimal.ZERO;
            for (BatchPickDto pick : picks) {
                stockMoveService.post(new PostStockMoveRequestDto(
                    line.getItemId(), invoice.getBranchId(),
                    pick.qty().negate(), pick.cost(),
                    StockMoveType.SALE, AGG, invoice.getId(),
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
            BigDecimal avgCost = balances.findById(new ItemBranchBalanceId(line.getItemId(),
                    invoice.getBranchId()))
                .map(ItemBranchBalance::getAvgCost)
                .orElse(BigDecimal.ZERO);
            stockMoveService.post(new PostStockMoveRequestDto(
                line.getItemId(), invoice.getBranchId(),
                line.getQty().negate(), null,
                StockMoveType.SALE, AGG, invoice.getId(),
                null, false, null
            ));
            line.setCostAmount(avgCost);
        }
    }

    private void validateDiscountApprover(CreateSalesInvoiceRequestDto request, Long actorId,
                                          Long companyId) {
        BigDecimal threshold = discountThresholdPct;
        boolean needsApproval = request.lines().stream()
            .map(CreateSalesInvoiceRequestDto.Line::discountPct)
            .filter(Objects::nonNull)
            .anyMatch(pct -> pct.compareTo(threshold) > 0);
        if (!needsApproval) {
            return;
        }
        if (request.discountApproverId() == null) {
            throw new IllegalArgumentException(
                "Discount above " + threshold + "% requires an authoriser holding "
                    + DISCOUNT_APPROVE_PERMISSION);
        }
        if (Objects.equals(request.discountApproverId(), actorId)) {
            throw new IllegalArgumentException("You cannot authorise your own line discount");
        }
        boolean ok = permissions.resolve(request.discountApproverId(), companyId, null)
            .contains(DISCOUNT_APPROVE_PERMISSION);
        if (!ok) {
            throw new AccessDeniedException(
                "Authoriser " + request.discountApproverId() + " does not hold "
                    + DISCOUNT_APPROVE_PERMISSION);
        }
    }

    private void validateLines(CreateSalesInvoiceRequestDto request, Long companyId) {
        for (CreateSalesInvoiceRequestDto.Line input : request.lines()) {
            Item item = requireItem(input.itemId(), companyId);
            BigDecimal discountPct = input.discountPct() != null ? input.discountPct() : BigDecimal.ZERO;
            BigDecimal discountFactor = BigDecimal.ONE.subtract(
                discountPct.divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP));
            BigDecimal netUnit = input.unitPrice().multiply(discountFactor)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (item.getMinSellPrice() != null
                    && netUnit.compareTo(item.getMinSellPrice()) < 0) {
                throw new IllegalArgumentException(
                    "Line for item " + item.getCode() + " is below min sell price "
                        + item.getMinSellPrice() + " (net unit " + netUnit + ")");
            }
        }
    }

    private List<SalesInvoiceLine> saveLinesAndRollUp(SalesInvoice invoice,
                                                      List<CreateSalesInvoiceRequestDto.Line> requestLines,
                                                      Long companyId) {
        List<SalesInvoiceLine> savedLines = new ArrayList<>(requestLines.size());
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        int lineNo = 1;
        for (CreateSalesInvoiceRequestDto.Line input : requestLines) {
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

            SalesInvoiceLine line = lines.save(new SalesInvoiceLine(
                invoice.getId(), lineNo++, input.itemId(), uomId,
                input.qty(), input.unitPrice(), discountPct, discountAmount,
                vatGroupId, lineTax, lineTotal
            ));
            savedLines.add(line);
            subtotal = subtotal.add(netLine);
            tax = tax.add(lineTax);
        }
        invoice.rollUpTotals(subtotal, BigDecimal.ZERO, tax);
        return savedLines;
    }

    private void checkCreditLimit(Customer customer, BigDecimal invoiceTotal, Long excludeInvoiceId) {
        // The repo's sumOutstandingDebt query already filters out DRAFT invoices,
        // so re-checking on post never double-counts an in-progress invoice.
        // excludeInvoiceId is unused for now; kept on the signature for future
        // edits-on-POSTED flows where we'd need to subtract the prior amount.
        Objects.requireNonNullElse(excludeInvoiceId, 0L);  // suppress unused warning
        BigDecimal currentDebt = invoices.sumOutstandingDebt(customer.getPartyId());
        BigDecimal projected = currentDebt.add(invoiceTotal);
        BigDecimal limit = customer.getCreditLimitAmount();
        if (limit.signum() > 0 && projected.compareTo(limit) > 0) {
            throw new IllegalArgumentException(
                "Customer credit limit exceeded: current debt " + currentDebt
                    + ", new invoice " + invoiceTotal + ", limit " + limit);
        }
        if (limit.signum() == 0 && invoiceTotal.signum() > 0) {
            throw new IllegalArgumentException(
                "Customer has no credit limit configured — CREDIT sales are not allowed");
        }
    }

    private SalesInvoice requireInvoice(Long id) {
        SalesInvoice invoice = invoices.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Sales invoice not found: " + id));
        if (!Objects.equals(invoice.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Sales invoice not found: " + id);
        }
        branchScope.requireAccess(invoice.getBranchId());
        return invoice;
    }

    private Customer requireCustomer(Long customerId) {
        return customers.findById(customerId)
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
}
