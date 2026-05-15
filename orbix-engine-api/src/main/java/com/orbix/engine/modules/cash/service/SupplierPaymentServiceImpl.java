package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.cash.domain.dto.CreateSupplierPaymentRequestDto;
import com.orbix.engine.modules.cash.domain.dto.SupplierPaymentDto;
import com.orbix.engine.modules.cash.domain.entity.SupplierPayment;
import com.orbix.engine.modules.cash.domain.entity.SupplierPaymentAllocation;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.domain.enums.PaymentMethod;
import com.orbix.engine.modules.cash.repository.SupplierPaymentAllocationRepository;
import com.orbix.engine.modules.cash.repository.SupplierPaymentRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import com.orbix.engine.modules.procurement.repository.SupplierInvoiceRepository;
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

@Service
@RequiredArgsConstructor
public class SupplierPaymentServiceImpl implements SupplierPaymentService {

    private static final String AGG = "SupplierPayment";
    private static final String F_ID = "supplierPaymentId";
    private static final String F_NUMBER = "number";

    private final SupplierPaymentRepository payments;
    private final SupplierPaymentAllocationRepository allocations;
    private final SupplierInvoiceRepository invoices;
    private final DayGuard dayGuard;
    private final CashLedgerService cashLedger;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG)
    public SupplierPaymentDto createDraft(CreateSupplierPaymentRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        String number = request.number().trim().toUpperCase();

        if (payments.existsByBranchIdAndNumber(request.branchId(), number)) {
            throw new IllegalArgumentException(
                "Supplier-payment number already exists for this branch: " + number);
        }

        Map<Long, SupplierInvoice> invoiceById = loadAndValidateInvoices(request, companyId);
        BigDecimal allocatedSum = validateAllocationAmounts(request, invoiceById);
        if (allocatedSum.compareTo(request.totalAmount()) > 0) {
            throw new IllegalArgumentException(
                "Allocations sum " + allocatedSum + " exceeds payment total " + request.totalAmount());
        }

        SupplierPayment payment = payments.save(new SupplierPayment(
            number, companyId, request.branchId(), request.supplierId(),
            request.paymentDate(), request.method(), request.reference(),
            request.currencyCode(), request.totalAmount(), request.notes(), actorId
        ));
        payment.setAllocatedAmount(allocatedSum);

        List<SupplierPaymentAllocation> savedAllocs = new ArrayList<>(request.allocations().size());
        for (CreateSupplierPaymentRequestDto.Allocation alloc : request.allocations()) {
            savedAllocs.add(allocations.save(new SupplierPaymentAllocation(
                payment.getId(), alloc.supplierInvoiceId(), alloc.amount())));
        }

        events.publish("SupplierPaymentCreated.v1", AGG, String.valueOf(payment.getId()),
            Map.of(F_ID, payment.getId(), F_NUMBER, payment.getNumber(),
                "supplierId", payment.getSupplierId(),
                "totalAmount", payment.getTotalAmount(),
                "invoiceCount", savedAllocs.size()));
        return SupplierPaymentDto.from(payment, savedAllocs);
    }

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public SupplierPaymentDto post(Long paymentId) {
        SupplierPayment payment = requirePayment(paymentId);
        BusinessDay day = dayGuard.requireOpenDay(payment.getBranchId());

        List<SupplierPaymentAllocation> rows = allocations.findBySupplierPaymentId(payment.getId());
        Long actorId = context.userId();

        for (SupplierPaymentAllocation alloc : rows) {
            SupplierInvoice invoice = invoices.findById(alloc.getSupplierInvoiceId())
                .orElseThrow(() -> new NoSuchElementException(
                    "Supplier invoice not found: " + alloc.getSupplierInvoiceId()));
            invoice.applyPayment(alloc.getAmount(), actorId);
        }

        payment.post(actorId);

        // F6.1: paired cash entry on the OUT side. Method determines the account
        // (CASH→CASH_BOX, BANK_TRANSFER/CHEQUE→BANK, MOBILE_MONEY→MOBILE_MONEY).
        cashLedger.post(
            Instant.now(),
            payment.getCompanyId(),
            payment.getBranchId(),
            day.getBusinessDate(),
            accountFor(payment.getMethod()),
            CashDirection.OUT,
            payment.getTotalAmount(),
            payment.getCurrencyCode(),
            CashRefType.SUPPLIER_PAYMENT,
            payment.getId(),
            GlCategory.SUPPLIER_SETTLEMENT,
            payment.getNumber(),
            actorId
        );

        events.publish("SupplierPaymentPosted.v1", AGG, String.valueOf(payment.getId()),
            Map.of(F_ID, payment.getId(), F_NUMBER, payment.getNumber(),
                "supplierId", payment.getSupplierId(),
                "branchId", payment.getBranchId(),
                "totalAmount", payment.getTotalAmount(),
                "method", payment.getMethod().name(),
                "currencyCode", payment.getCurrencyCode()));
        return SupplierPaymentDto.from(payment, rows);
    }

    private static CashAccount accountFor(PaymentMethod method) {
        return switch (method) {
            case CASH -> CashAccount.CASH_BOX;
            case MOBILE_MONEY -> CashAccount.MOBILE_MONEY;
            case BANK_TRANSFER, CHEQUE -> CashAccount.BANK;
        };
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG)
    public SupplierPaymentDto cancel(Long paymentId) {
        SupplierPayment payment = requirePayment(paymentId);
        payment.cancel(context.userId());
        events.publish("SupplierPaymentCancelled.v1", AGG, String.valueOf(payment.getId()),
            Map.of(F_ID, payment.getId(), F_NUMBER, payment.getNumber()));
        return SupplierPaymentDto.from(payment,
            allocations.findBySupplierPaymentId(payment.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierPaymentDto> list(Long branchId) {
        Long companyId = context.companyId();
        List<SupplierPayment> rows = branchId == null
            ? payments.findByCompanyIdOrderByIdDesc(companyId)
            : payments.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, branchId);
        return rows.stream()
            .map(p -> SupplierPaymentDto.from(p, allocations.findBySupplierPaymentId(p.getId())))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierPaymentDto get(Long paymentId) {
        SupplierPayment payment = requirePayment(paymentId);
        return SupplierPaymentDto.from(payment, allocations.findBySupplierPaymentId(payment.getId()));
    }

    private Map<Long, SupplierInvoice> loadAndValidateInvoices(CreateSupplierPaymentRequestDto request,
                                                               Long companyId) {
        Map<Long, SupplierInvoice> byId = new HashMap<>();
        for (CreateSupplierPaymentRequestDto.Allocation alloc : request.allocations()) {
            if (byId.containsKey(alloc.supplierInvoiceId())) {
                throw new IllegalArgumentException(
                    "Duplicate allocation to invoice " + alloc.supplierInvoiceId());
            }
            SupplierInvoice invoice = invoices.findById(alloc.supplierInvoiceId())
                .orElseThrow(() -> new NoSuchElementException(
                    "Supplier invoice not found: " + alloc.supplierInvoiceId()));
            if (!Objects.equals(invoice.getCompanyId(), companyId)) {
                throw new NoSuchElementException(
                    "Supplier invoice not found: " + alloc.supplierInvoiceId());
            }
            if (!Objects.equals(invoice.getSupplierId(), request.supplierId())) {
                throw new IllegalArgumentException(
                    "Invoice " + invoice.getNumber() + " is for a different supplier");
            }
            byId.put(invoice.getId(), invoice);
        }
        return byId;
    }

    private BigDecimal validateAllocationAmounts(CreateSupplierPaymentRequestDto request,
                                                 Map<Long, SupplierInvoice> invoiceById) {
        BigDecimal sum = BigDecimal.ZERO;
        for (CreateSupplierPaymentRequestDto.Allocation alloc : request.allocations()) {
            SupplierInvoice invoice = invoiceById.get(alloc.supplierInvoiceId());
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

    private SupplierPayment requirePayment(Long id) {
        SupplierPayment payment = payments.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Supplier payment not found: " + id));
        if (!Objects.equals(payment.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Supplier payment not found: " + id);
        }
        return payment;
    }
}
