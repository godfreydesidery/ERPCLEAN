package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Slice G.1 — single-supplier AP position used by the supplier drill-down.
 * Open invoices first (sorted by dueDate asc), then recent payments, plus
 * aging summary row.
 */
public record SupplierStatementDto(
    Long supplierId,
    String supplierUid,
    String supplierName,
    String currencyCode,
    BigDecimal totalOutstanding,
    long openInvoiceCount,
    long overdueInvoiceCount,
    LocalDate asOf,
    SupplierAgingDto.SupplierRow agingRow,
    List<OpenInvoiceRow> openInvoices,
    List<RecentPaymentRow> recentPayments
) {

    public record OpenInvoiceRow(
        Long invoiceId,
        String invoiceUid,
        String number,
        String supplierInvoiceNo,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstanding,
        Integer daysOverdue,
        SupplierInvoiceStatus status
    ) {}

    public record RecentPaymentRow(
        Long paymentId,
        String paymentUid,
        String number,
        LocalDate paymentDate,
        Instant postedAt,
        BigDecimal totalAmount,
        String currencyCode
    ) {}
}
