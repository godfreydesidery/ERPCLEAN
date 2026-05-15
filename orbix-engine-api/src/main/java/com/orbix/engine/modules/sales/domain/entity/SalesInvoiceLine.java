package com.orbix.engine.modules.sales.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Sales-invoice line. DATA-MODEL.md §6.4. */
@Entity
@Table(name = "sales_invoice_line",
    uniqueConstraints = @UniqueConstraint(name = "uk_sales_invoice_line_no",
        columnNames = {"sales_invoice_id", "line_no"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class SalesInvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_invoice_line_seq")
    @SequenceGenerator(name = "sales_invoice_line_seq", sequenceName = "sales_invoice_line_seq",
        allocationSize = 50)
    private Long id;

    @Column(name = "sales_invoice_id", nullable = false)
    private Long salesInvoiceId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal qty;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "discount_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "vat_group_id", nullable = false)
    private Long vatGroupId;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "cost_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal costAmount = BigDecimal.ZERO;

    @Column(name = "promotion_id")
    private Long promotionId;

    @SuppressWarnings("java:S107")
    public SalesInvoiceLine(Long salesInvoiceId, Integer lineNo, Long itemId, Long uomId,
                            BigDecimal qty, BigDecimal unitPrice, BigDecimal discountPct,
                            BigDecimal discountAmount, Long vatGroupId, BigDecimal taxAmount,
                            BigDecimal lineTotal) {
        this.salesInvoiceId = salesInvoiceId;
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.uomId = uomId;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.discountPct = discountPct != null ? discountPct : BigDecimal.ZERO;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.vatGroupId = vatGroupId;
        this.taxAmount = taxAmount;
        this.lineTotal = lineTotal;
    }
}
