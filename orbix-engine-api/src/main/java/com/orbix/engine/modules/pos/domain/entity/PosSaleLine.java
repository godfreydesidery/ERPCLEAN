package com.orbix.engine.modules.pos.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** POS-sale line — mirrors {@code sales_invoice_line} shape. DATA-MODEL.md §7.4. */
@Entity
@Table(name = "pos_sale_line",
    uniqueConstraints = @UniqueConstraint(name = "uk_pos_sale_line_no",
        columnNames = {"pos_sale_id", "line_no"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class PosSaleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pos_sale_line_seq")
    @SequenceGenerator(name = "pos_sale_line_seq", sequenceName = "pos_sale_line_seq",
        allocationSize = 50)
    private Long id;

    @Column(name = "pos_sale_id", nullable = false)
    private Long posSaleId;

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
    private BigDecimal lineTotal;

    @Column(name = "cost_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal costAmount = BigDecimal.ZERO;

    @Column(name = "promotion_id")
    private Long promotionId;

    @SuppressWarnings("java:S107")
    public PosSaleLine(Long posSaleId, Integer lineNo, Long itemId, Long uomId, BigDecimal qty,
                       BigDecimal unitPrice, BigDecimal discountPct, BigDecimal discountAmount,
                       Long vatGroupId, BigDecimal taxAmount, BigDecimal lineTotal) {
        this.posSaleId = posSaleId;
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
