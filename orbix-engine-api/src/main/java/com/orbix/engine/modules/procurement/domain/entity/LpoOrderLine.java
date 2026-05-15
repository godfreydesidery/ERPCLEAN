package com.orbix.engine.modules.procurement.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Line on an {@link LpoOrder}. DATA-MODEL.md §5.4. */
@Entity
@Table(name = "lpo_order_line",
    uniqueConstraints = @UniqueConstraint(name = "uk_lpo_order_line_no",
        columnNames = {"lpo_order_id", "line_no"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class LpoOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lpo_order_line_seq")
    @SequenceGenerator(name = "lpo_order_line_seq", sequenceName = "lpo_order_line_seq", allocationSize = 50)
    private Long id;

    @Column(name = "lpo_order_id", nullable = false)
    private Long lpoOrderId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    @Column(name = "ordered_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal orderedQty;

    @Column(name = "received_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal receivedQty = BigDecimal.ZERO;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "vat_group_id", nullable = false)
    private Long vatGroupId;

    @Column(name = "discount_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @SuppressWarnings("java:S107")  // line construction mirrors the schema; a VO would only shuffle args
    public LpoOrderLine(Long lpoOrderId, Integer lineNo, Long itemId, Long uomId, BigDecimal orderedQty,
                        BigDecimal unitPrice, Long vatGroupId, BigDecimal discountPct,
                        BigDecimal lineTotal) {
        this.lpoOrderId = lpoOrderId;
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.uomId = uomId;
        this.orderedQty = orderedQty;
        this.unitPrice = unitPrice;
        this.vatGroupId = vatGroupId;
        this.discountPct = discountPct != null ? discountPct : BigDecimal.ZERO;
        this.lineTotal = lineTotal;
    }
}
