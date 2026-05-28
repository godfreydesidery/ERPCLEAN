package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Slice G — single-customer debt position used by the customer drill-down.
 * Open invoices first (sorted by dueDate asc), then recent receipts, plus
 * credit-limit headroom.
 */
public record CustomerStatementDto(
    Long customerId,
    String customerUid,
    String customerName,
    String currencyCode,
    BigDecimal creditLimit,
    BigDecimal totalOutstanding,
    BigDecimal creditUtilisation,
    long openInvoiceCount,
    long overdueInvoiceCount,
    LocalDate asOf,
    List<OpenInvoiceRow> openInvoices,
    List<RecentReceiptRow> recentReceipts
) {

    public record OpenInvoiceRow(
        Long invoiceId,
        String invoiceUid,
        String number,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstanding,
        Integer daysOverdue,
        SalesInvoiceStatus status
    ) {}

    public record RecentReceiptRow(
        Long receiptId,
        String receiptUid,
        String number,
        LocalDate receiptDate,
        Instant postedAt,
        BigDecimal totalAmount,
        String currencyCode
    ) {}
}
