package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.service.CashLedgerService;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.sales.domain.dto.CreateSalesReceiptRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesReceiptDto;
import com.orbix.engine.modules.sales.domain.entity.ReceiptAllocation;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.entity.SalesReceipt;
import com.orbix.engine.modules.sales.domain.enums.ReceiptMethod;
import com.orbix.engine.modules.sales.repository.ReceiptAllocationRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.sales.repository.SalesReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SalesReceiptServiceImpl implements SalesReceiptService {

    private static final String AGG = "SalesReceipt";
    private static final String F_ID = "salesReceiptId";
    private static final String F_NUMBER = "number";

    private final SalesReceiptRepository receipts;
    private final ReceiptAllocationRepository allocations;
    private final SalesInvoiceRepository invoices;
    private final DayGuard dayGuard;
    private final CashLedgerService cashLedger;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG)
    public SalesReceiptDto createDraft(CreateSalesReceiptRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        String number = request.number().trim().toUpperCase();
        if (receipts.existsByBranchIdAndNumber(request.branchId(), number)) {
            throw new IllegalArgumentException(
                "Sales receipt number already exists for this branch: " + number);
        }

        List<CreateSalesReceiptRequestDto.Allocation> allocs = request.allocations() != null
            ? request.allocations() : List.of();
        Map<Long, SalesInvoice> invoiceById = loadAndValidate(allocs, request.customerId(), companyId);
        BigDecimal allocatedSum = validateAmounts(allocs, invoiceById);
        if (allocatedSum.compareTo(request.totalAmount()) > 0) {
            throw new IllegalArgumentException(
                "Allocations sum " + allocatedSum + " exceeds receipt total " + request.totalAmount());
        }

        SalesReceipt receipt = receipts.save(new SalesReceipt(
            number, companyId, request.branchId(), request.customerId(),
            request.receiptDate(), request.method(), request.reference(),
            request.currencyCode(), request.totalAmount(), request.notes(), actorId
        ));
        receipt.setAllocated(allocatedSum);

        List<ReceiptAllocation> savedAllocs = new ArrayList<>(allocs.size());
        for (CreateSalesReceiptRequestDto.Allocation alloc : allocs) {
            savedAllocs.add(allocations.save(new ReceiptAllocation(
                receipt.getId(), alloc.salesInvoiceId(), alloc.amount(), actorId)));
        }

        events.publish("SalesReceiptCreated.v1", AGG, String.valueOf(receipt.getId()),
            Map.of(F_ID, receipt.getId(), F_NUMBER, receipt.getNumber(),
                "customerId", receipt.getCustomerId(),
                "totalAmount", receipt.getTotalAmount(),
                "allocations", savedAllocs.size()));
        return SalesReceiptDto.from(receipt, savedAllocs);
    }

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public SalesReceiptDto post(Long receiptId) {
        SalesReceipt receipt = requireReceipt(receiptId);
        BusinessDay day = dayGuard.requireOpenDay(receipt.getBranchId());
        Long actorId = context.userId();
        List<ReceiptAllocation> rows = allocations.findBySalesReceiptId(receipt.getId());

        for (ReceiptAllocation alloc : rows) {
            SalesInvoice invoice = invoices.findById(alloc.getSalesInvoiceId())
                .orElseThrow(() -> new NoSuchElementException(
                    "Sales invoice not found: " + alloc.getSalesInvoiceId()));
            invoice.applyReceipt(alloc.getAmount(), actorId);
        }

        receipt.post(actorId);

        // F6.1: cash side. CARD / STORE_CREDIT don't move physical cash; the
        // other methods land an IN entry on their matching account.
        Optional<CashAccount> account = accountFor(receipt.getMethod());
        if (account.isPresent()) {
            // Sales receipts are single-currency per receipt; F6.2 multi-currency
            // book stores the tender amount as-is with fx_rate_snapshot = 1.
            cashLedger.post(
                Instant.now(),
                receipt.getCompanyId(),
                receipt.getBranchId(),
                day.getBusinessDate(),
                account.get(),
                CashDirection.IN,
                receipt.getTotalAmount(),
                java.math.BigDecimal.ONE,
                receipt.getCurrencyCode(),
                CashRefType.SALES_RECEIPT,
                receipt.getId(),
                GlCategory.RECEIPT,
                receipt.getNumber(),
                actorId
            );
        }

        events.publish("SalesReceiptPosted.v1", AGG, String.valueOf(receipt.getId()),
            Map.of(F_ID, receipt.getId(), F_NUMBER, receipt.getNumber(),
                "customerId", receipt.getCustomerId(),
                "branchId", receipt.getBranchId(),
                "totalAmount", receipt.getTotalAmount(),
                "method", receipt.getMethod().name(),
                "currencyCode", receipt.getCurrencyCode()));
        return SalesReceiptDto.from(receipt, rows);
    }

    /**
     * Maps the receipt method to the cash account it lands in.
     * {@code CARD} settles via the card rail and {@code STORE_CREDIT} draws on
     * the customer-credit ledger — neither moves physical cash, so they have
     * no cash-entry side.
     */
    private static Optional<CashAccount> accountFor(ReceiptMethod method) {
        return switch (method) {
            case CASH -> Optional.of(CashAccount.CASH_BOX);
            case MOBILE_MONEY -> Optional.of(CashAccount.MOBILE_MONEY);
            case BANK_TRANSFER, CHEQUE -> Optional.of(CashAccount.BANK);
            case CARD, STORE_CREDIT -> Optional.empty();
        };
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG)
    public SalesReceiptDto cancel(Long receiptId) {
        SalesReceipt receipt = requireReceipt(receiptId);
        receipt.cancel(context.userId());
        events.publish("SalesReceiptCancelled.v1", AGG, String.valueOf(receipt.getId()),
            Map.of(F_ID, receipt.getId(), F_NUMBER, receipt.getNumber()));
        return SalesReceiptDto.from(receipt, allocations.findBySalesReceiptId(receipt.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesReceiptDto> list(Long branchId) {
        Long companyId = context.companyId();
        List<SalesReceipt> rows = branchId == null
            ? receipts.findByCompanyIdOrderByIdDesc(companyId)
            : receipts.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, branchId);
        return rows.stream()
            .map(r -> SalesReceiptDto.from(r, allocations.findBySalesReceiptId(r.getId())))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SalesReceiptDto get(Long receiptId) {
        SalesReceipt receipt = requireReceipt(receiptId);
        return SalesReceiptDto.from(receipt, allocations.findBySalesReceiptId(receipt.getId()));
    }

    private Map<Long, SalesInvoice> loadAndValidate(List<CreateSalesReceiptRequestDto.Allocation> allocs,
                                                    Long customerId, Long companyId) {
        Map<Long, SalesInvoice> byId = new HashMap<>();
        for (CreateSalesReceiptRequestDto.Allocation alloc : allocs) {
            if (byId.containsKey(alloc.salesInvoiceId())) {
                throw new IllegalArgumentException(
                    "Duplicate allocation to invoice " + alloc.salesInvoiceId());
            }
            SalesInvoice invoice = invoices.findById(alloc.salesInvoiceId())
                .orElseThrow(() -> new NoSuchElementException(
                    "Sales invoice not found: " + alloc.salesInvoiceId()));
            if (!Objects.equals(invoice.getCompanyId(), companyId)) {
                throw new NoSuchElementException("Sales invoice not found: " + alloc.salesInvoiceId());
            }
            if (!Objects.equals(invoice.getCustomerId(), customerId)) {
                throw new IllegalArgumentException(
                    "Invoice " + invoice.getNumber() + " is for a different customer");
            }
            byId.put(invoice.getId(), invoice);
        }
        return byId;
    }

    private BigDecimal validateAmounts(List<CreateSalesReceiptRequestDto.Allocation> allocs,
                                       Map<Long, SalesInvoice> invoiceById) {
        BigDecimal sum = BigDecimal.ZERO;
        for (CreateSalesReceiptRequestDto.Allocation alloc : allocs) {
            SalesInvoice invoice = invoiceById.get(alloc.salesInvoiceId());
            BigDecimal outstanding = invoice.outstandingAmount();
            if (alloc.amount().compareTo(outstanding) > 0) {
                throw new IllegalArgumentException(
                    "Allocation " + alloc.amount() + " exceeds outstanding " + outstanding
                        + " on invoice " + invoice.getNumber());
            }
            sum = sum.add(alloc.amount());
        }
        return sum;
    }

    private SalesReceipt requireReceipt(Long id) {
        SalesReceipt receipt = receipts.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Sales receipt not found: " + id));
        if (!Objects.equals(receipt.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Sales receipt not found: " + id);
        }
        return receipt;
    }
}
