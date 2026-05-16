package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.enums.PaymentTerms;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the daily sales report (F8.2 / US-RPT-001). Blends back-office
 * sales_invoice and POS sales — {@code source} disambiguates so the manager
 * can filter (e.g. credit invoices vs cash POS).
 *
 * <p>{@code paymentTerms} is non-null for SalesInvoice rows (CASH / CREDIT)
 * and null for PosSale rows (always cash-equivalent at till).
 * {@code status} on PosSale rows composes the kind + lifecycle status so a
 * refund and a voided sale read distinctly (e.g. {@code REFUND_POSTED},
 * {@code SALE_VOIDED}).
 */
public record DailySalesRowDto(
    Source source,
    Long id,
    String number,
    Long customerId,
    Long branchId,
    BigDecimal totalAmount,
    BigDecimal taxAmount,
    BigDecimal discountAmount,
    String status,
    PaymentTerms paymentTerms,
    Instant occurredAt
) {
    public enum Source { SALES_INVOICE, POS_SALE }

    public static DailySalesRowDto from(SalesInvoice inv) {
        return new DailySalesRowDto(
            Source.SALES_INVOICE,
            inv.getId(),
            inv.getNumber(),
            inv.getCustomerId(),
            inv.getBranchId(),
            inv.getTotalAmount(),
            inv.getTaxAmount(),
            inv.getDiscountAmount(),
            inv.getStatus().name(),
            inv.getPaymentTerms(),
            inv.getPostedAt()
        );
    }

    public static DailySalesRowDto from(PosSale sale) {
        String composedStatus = (sale.getKind() == PosSaleKind.REFUND ? "REFUND_" : "SALE_")
            + sale.getStatus().name();
        return new DailySalesRowDto(
            Source.POS_SALE,
            sale.getId(),
            sale.getNumber(),
            sale.getCustomerId(),
            sale.getBranchId(),
            sale.getTotalAmount(),
            sale.getTaxAmount(),
            sale.getDiscountAmount(),
            composedStatus,
            null,
            sale.getSaleAt()
        );
    }
}
