package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.domain.dto.PageDto;
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
import com.orbix.engine.modules.sales.domain.dto.PostSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.ReprintInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesInvoiceDto;
import com.orbix.engine.modules.sales.domain.dto.VoidSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoiceLine;
import com.orbix.engine.modules.sales.domain.enums.PaymentTerms;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import com.orbix.engine.modules.sales.repository.SalesInvoiceLineRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.stock.domain.dto.BatchPickDto;
import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.service.StockBatchService;
import com.orbix.engine.modules.stock.service.StockMoveService;
import lombok.RequiredArgsConstructor;
import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.SettingsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
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
    static final String OVERRIDE_CREDIT_PERMISSION = "SALES_INVOICE.OVERRIDE_CREDIT";

    private static final String AGG = "SalesInvoice";
    private static final String F_ID = "salesInvoiceId";
    private static final String F_NUMBER = "number";
    private static final String F_CUSTOMER_ID = "customerId";
    private static final String F_TOTAL_AMOUNT = "totalAmount";
    private static final String F_BRANCH_ID = "branchId";
    private static final String F_REASON = "reason";

    private final SalesInvoiceRepository invoices;
    private final SalesInvoiceLineRepository lines;
    private final ItemRepository items;
    private final VatGroupRepository vatGroups;
    private final CustomerRepository customers;
    private final StockMoveService stockMoveService;
    private final StockBatchService stockBatchService;
    private final DayGuard dayGuard;
    private final PermissionResolverService permissions;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;
    private final SettingsService settings;

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
        // Customer must exist (and be in this company) before we accept a draft.
        // The full Customer record is not used here — the credit gate runs at
        // POST time (Slice C design intent).
        requireCustomer(request.customerId());
        validateDiscountApprover(request, actorId, companyId);
        validateLines(request, companyId);

        SalesInvoice invoice = invoices.save(new SalesInvoice(
            number, companyId, request.branchId(), request.customerId(),
            request.salesAgentId(), request.invoiceDate(), request.dueDate(),
            request.paymentTerms(), request.currencyCode(), request.priceListId(),
            request.reference(), request.notes(), actorId
        ));
        List<SalesInvoiceLine> savedLines = saveLinesAndRollUp(invoice, request.lines(), companyId);

        // Slice C design intent: drafts are always allowed even when they
        // exceed the customer's credit limit (sales-negotiation workflow).
        // The credit gate fires at POST time, with the
        // SALES_INVOICE.OVERRIDE_CREDIT off-ramp available there.

        events.publish("SalesInvoiceCreated.v1", AGG, String.valueOf(invoice.getId()),
            Map.of(F_ID, invoice.getId(), F_NUMBER, invoice.getNumber(),
                F_CUSTOMER_ID, invoice.getCustomerId(),
                F_TOTAL_AMOUNT, invoice.getTotalAmount(),
                "paymentTerms", invoice.getPaymentTerms().name()));
        return SalesInvoiceDto.from(invoice, savedLines);
    }

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public SalesInvoiceDto post(String uid, PostSalesInvoiceRequestDto request) {
        SalesInvoice invoice = requireInvoiceByUid(uid);
        Long actorId = context.userId();
        Long companyId = invoice.getCompanyId();
        BusinessDay day = dayGuard.requireOpenDay(invoice.getBranchId());

        // Re-check credit limit at post time (state may have changed since draft).
        // The override branch (Slice C GAP 3.A) only fires on the limit-exceeded
        // case — the zero-credit-limit branch stays unconditional.
        if (invoice.getPaymentTerms() == PaymentTerms.CREDIT) {
            Customer customer = requireCustomer(invoice.getCustomerId());
            String overrideReason = request != null ? request.overrideReason() : null;
            boolean overrideAllowed = false;
            if (overrideReason != null && !overrideReason.isBlank()) {
                overrideAllowed = permissions.resolve(actorId, companyId, invoice.getBranchId())
                    .contains(OVERRIDE_CREDIT_PERMISSION);
            }
            boolean breached = checkCreditLimit(customer, invoice.getTotalAmount(), overrideAllowed);
            if (breached) {
                invoice.markCreditOverride(actorId, overrideReason.trim());
            }
        }

        List<SalesInvoiceLine> invoiceLines = lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId());
        for (SalesInvoiceLine line : invoiceLines) {
            postLineStockMoves(invoice, line);
        }

        invoice.post(day.getBusinessDate(), actorId);

        Map<String, Object> postedPayload = new HashMap<>();
        postedPayload.put(F_ID, invoice.getId());
        postedPayload.put(F_NUMBER, invoice.getNumber());
        postedPayload.put(F_CUSTOMER_ID, invoice.getCustomerId());
        postedPayload.put(F_BRANCH_ID, invoice.getBranchId());
        postedPayload.put(F_TOTAL_AMOUNT, invoice.getTotalAmount());
        postedPayload.put("paymentTerms", invoice.getPaymentTerms().name());
        postedPayload.put("currencyCode", invoice.getCurrencyCode());
        // Slice C GAP 9.A — per-line breakdown so the deferred debt module
        // can build its sub-ledger without a `.v2` bump later.
        List<Map<String, Object>> linePayload = new ArrayList<>(invoiceLines.size());
        for (SalesInvoiceLine line : invoiceLines) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("salesInvoiceLineId", line.getId());
            entry.put("itemId", line.getItemId());
            entry.put("uomId", line.getUomId());
            entry.put("qty", line.getQty());
            entry.put("unitPrice", line.getUnitPrice());
            entry.put("vatGroupId", line.getVatGroupId());
            entry.put("lineTotal", line.getLineTotal());
            entry.put("batchId", null);
            linePayload.add(entry);
        }
        postedPayload.put("lines", linePayload);
        if (invoice.isCreditOverride()) {
            postedPayload.put("creditOverride", true);
            postedPayload.put("creditOverrideBy", invoice.getCreditOverrideBy());
            postedPayload.put("creditOverrideReason", invoice.getCreditOverrideReason());
        }
        events.publish("SalesInvoicePosted.v1", AGG, String.valueOf(invoice.getId()), postedPayload);
        return SalesInvoiceDto.from(invoice, invoiceLines);
    }

    @Override
    @Transactional
    @Auditable(action = "VOID", entityType = AGG)
    public SalesInvoiceDto voidInvoice(String uid, VoidSalesInvoiceRequestDto request) {
        SalesInvoice invoice = requireInvoiceByUid(uid);
        SalesInvoiceStatus priorStatus = invoice.getStatus();
        if (priorStatus != SalesInvoiceStatus.POSTED) {
            throw new IllegalStateException(
                "Only POSTED invoices can be voided (was " + priorStatus + ")");
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

        Long actorId = context.userId();
        invoice.voidInvoice(request.reason(), actorId);

        // Slice C GAP — widen the voided event so debt-side subscribers can
        // recognise the compensating action without re-reading the invoice.
        Map<String, Object> voidedPayload = new HashMap<>();
        voidedPayload.put(F_ID, invoice.getId());
        voidedPayload.put(F_NUMBER, invoice.getNumber());
        voidedPayload.put(F_CUSTOMER_ID, invoice.getCustomerId());
        voidedPayload.put(F_TOTAL_AMOUNT, invoice.getTotalAmount());
        voidedPayload.put(F_REASON, request.reason());
        voidedPayload.put("compensating", true);
        voidedPayload.put("priorStatus", priorStatus.name());
        voidedPayload.put("voidedBy", actorId);
        voidedPayload.put("voidedAt", invoice.getVoidedAt());
        events.publish("SalesInvoiceVoided.v1", AGG, String.valueOf(invoice.getId()), voidedPayload);
        return SalesInvoiceDto.from(invoice, invoiceLines);
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG)
    public SalesInvoiceDto cancel(String uid) {
        SalesInvoice invoice = requireInvoiceByUid(uid);
        invoice.cancel(context.userId());
        events.publish("SalesInvoiceCancelled.v1", AGG, String.valueOf(invoice.getId()),
            Map.of(F_ID, invoice.getId(), F_NUMBER, invoice.getNumber()));
        return SalesInvoiceDto.from(invoice, lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "REPRINT", entityType = AGG)
    public SalesInvoiceDto reprint(String uid, ReprintInvoiceRequestDto request) {
        SalesInvoice invoice = requireInvoiceByUid(uid);
        Long actorId = context.userId();
        int newCount = invoice.recordReprint(actorId);

        Map<String, Object> payload = new HashMap<>();
        payload.put(F_ID, invoice.getId());
        payload.put(F_NUMBER, invoice.getNumber());
        payload.put(F_REASON, request.reason().name());
        payload.put("notes", request.notes());
        payload.put("reprintedBy", actorId);
        payload.put("reprintedAt", invoice.getUpdatedAt());
        payload.put("reprintCount", newCount);
        events.publish("SalesInvoiceReprinted.v1", AGG, String.valueOf(invoice.getId()), payload);
        return SalesInvoiceDto.from(invoice, lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<SalesInvoiceDto> list(Long branchId, String status, Pageable pageable) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        Page<SalesInvoice> page = resolveListPage(companyId, scope, status, pageable);
        return PageDto.of(page, s -> SalesInvoiceDto.from(s, lines.findBySalesInvoiceIdOrderByLineNoAsc(s.getId())));
    }

    /**
     * Slice F — branch on the {@code status} token.
     * <ul>
     *   <li>{@code null} → today's behaviour.</li>
     *   <li>{@code "OPEN"} → POSTED+PARTIALLY_PAID with outstanding &gt; 0.</li>
     *   <li>{@code "OVERDUE"} → OPEN + dueDate &lt; today.</li>
     *   <li>any raw {@link SalesInvoiceStatus} value (case-insensitive) → exact match.</li>
     *   <li>anything else → {@link IllegalArgumentException}.</li>
     * </ul>
     */
    private Page<SalesInvoice> resolveListPage(Long companyId, Long scope, String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return scope == null
                ? invoices.findByCompanyIdOrderByIdDesc(companyId, pageable)
                : invoices.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, scope, pageable);
        }
        String token = status.trim().toUpperCase();
        if ("OPEN".equals(token)) {
            return invoices.findOpenForBranch(companyId, scope, pageable);
        }
        if ("OVERDUE".equals(token)) {
            return invoices.findOverdueForBranch(companyId, scope, java.time.LocalDate.now(), pageable);
        }
        SalesInvoiceStatus raw;
        try {
            raw = SalesInvoiceStatus.valueOf(token);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                "Unknown sales-invoice status filter: '" + status + "'. "
                    + "Use OPEN, OVERDUE or one of " + java.util.Arrays.toString(SalesInvoiceStatus.values()));
        }
        return scope == null
            ? invoices.findByCompanyIdAndStatusOrderByIdDesc(companyId, raw, pageable)
            : invoices.findByCompanyIdAndBranchIdAndStatusOrderByIdDesc(companyId, scope, raw, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesInvoiceDto get(String uid) {
        SalesInvoice invoice = requireInvoiceByUid(uid);
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
            // ADR-0004 §5 — reach into stock only via the *Service interface seam.
            BigDecimal avgCost = stockMoveService
                .findBalance(line.getItemId(), invoice.getBranchId())
                .map(ItemBranchBalanceDto::avgCost)
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
        BigDecimal threshold = settings.getDecimal(SettingKey.SALES_DISCOUNT_THRESHOLD_PCT);
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

    /**
     * Enforce the customer's credit-limit policy. Returns {@code true} when
     * the limit was breached AND the caller has been granted override (Slice C
     * GAP 3.A); the caller stamps the override columns on the invoice in that
     * case. Throws {@link IllegalArgumentException} on any unrecoverable
     * violation.
     *
     * <p>The repo's {@code sumOutstandingDebt} query already filters out
     * DRAFT invoices so re-checking on post never double-counts an
     * in-progress invoice.
     *
     * <p>The zero-limit branch ("customer has no credit configured") is NOT
     * overridable per the locked Slice C decision.
     */
    private boolean checkCreditLimit(Customer customer, BigDecimal invoiceTotal,
                                     boolean overrideAllowed) {
        BigDecimal currentDebt = invoices.sumOutstandingDebt(customer.getPartyId());
        BigDecimal projected = currentDebt.add(invoiceTotal);
        BigDecimal limit = customer.getCreditLimitAmount();
        if (limit.signum() == 0 && invoiceTotal.signum() > 0) {
            throw new IllegalArgumentException(
                "Customer has no credit limit configured — CREDIT sales are not allowed");
        }
        if (limit.signum() > 0 && projected.compareTo(limit) > 0) {
            if (overrideAllowed) {
                return true;
            }
            throw new IllegalArgumentException(
                "Customer credit limit exceeded: current debt " + currentDebt
                    + ", new invoice " + invoiceTotal + ", limit " + limit);
        }
        return false;
    }

    @Override
    @Transactional
    public void applyWriteOff(Long invoiceId, BigDecimal amount) {
        SalesInvoice invoice = invoices.findById(invoiceId)
            .orElseThrow(() -> new NoSuchElementException("Sales invoice not found: " + invoiceId));
        Long actorId = context.userId();
        invoice.applyReceipt(amount, actorId);
    }

    private SalesInvoice requireInvoiceByUid(String uid) {
        SalesInvoice invoice = invoices.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Sales invoice not found: " + uid));
        if (!Objects.equals(invoice.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Sales invoice not found: " + uid);
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
