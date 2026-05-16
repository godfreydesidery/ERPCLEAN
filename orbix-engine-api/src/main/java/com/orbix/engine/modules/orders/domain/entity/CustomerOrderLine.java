package com.orbix.engine.modules.orders.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Customer-order line. DATA-MODEL §17.9. */
@Entity
@Table(name = "customer_order_line",
    uniqueConstraints = @UniqueConstraint(name = "uk_customer_order_line_no",
        columnNames = {"customer_order_id", "line_no"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class CustomerOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_order_line_seq")
    @SequenceGenerator(name = "customer_order_line_seq", sequenceName = "customer_order_line_seq",
        allocationSize = 50)
    private Long id;

    @Column(name = "customer_order_id", nullable = false)
    private Long customerOrderId;

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

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(length = 200)
    private String notes;

    @SuppressWarnings("java:S107")
    public CustomerOrderLine(Long customerOrderId, Integer lineNo, Long itemId, Long uomId,
                             BigDecimal qty, BigDecimal unitPrice, BigDecimal discountAmount,
                             BigDecimal lineTotal, String notes) {
        this.customerOrderId = customerOrderId;
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.uomId = uomId;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.lineTotal = lineTotal != null ? lineTotal : BigDecimal.ZERO;
        this.notes = notes;
    }
}
