package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoiceLine;
import com.orbix.engine.modules.sales.domain.enums.PaymentTerms;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SalesInvoiceDto(
    Long id,
    String uid,
    String number,
    Long companyId,
    Long branchId,
    Long customerId,
    Long salesAgentId,
    LocalDate invoiceDate,
    LocalDate dueDate,
    PaymentTerms paymentTerms,
    String currencyCode,
    Long priceListId,
    BigDecimal subtotalAmount,
    BigDecimal discountAmount,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    SalesInvoiceStatus status,
    Instant postedAt,
    Long postedBy,
    LocalDate postedBusinessDate,
    Instant voidedAt,
    Long voidedBy,
    String voidReason,
    String cancellationReason,
    boolean creditOverride,
    Long creditOverrideBy,
    String creditOverrideReason,
    int reprintCount,
    String reference,
    String notes,
    List<SalesInvoiceLineDto> lines
) {
    public static SalesInvoiceDto from(SalesInvoice s, List<SalesInvoiceLine> lines) {
        return new SalesInvoiceDto(
            s.getId(), s.getUid(), s.getNumber(), s.getCompanyId(), s.getBranchId(),
            s.getCustomerId(), s.getSalesAgentId(), s.getInvoiceDate(), s.getDueDate(),
            s.getPaymentTerms(), s.getCurrencyCode(), s.getPriceListId(),
            s.getSubtotalAmount(), s.getDiscountAmount(), s.getTaxAmount(),
            s.getTotalAmount(), s.getPaidAmount(), s.getStatus(),
            s.getPostedAt(), s.getPostedBy(), s.getPostedBusinessDate(),
            s.getVoidedAt(), s.getVoidedBy(), s.getVoidReason(),
            s.getCancellationReason(),
            s.isCreditOverride(), s.getCreditOverrideBy(), s.getCreditOverrideReason(),
            s.getReprintCount(),
            s.getReference(), s.getNotes(),
            lines.stream().map(SalesInvoiceLineDto::from).toList()
        );
    }
}
