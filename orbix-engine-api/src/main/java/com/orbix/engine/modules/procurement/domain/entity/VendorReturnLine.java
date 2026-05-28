package com.orbix.engine.modules.procurement.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Vendor return line — one item + quantity being returned to supplier. DATA-MODEL.md §5.x (Slice H.1). */
@Entity
@Table(name = "vendor_return_line",
    uniqueConstraints = @UniqueConstraint(name = "uk_vendor_return_line_no",
        columnNames = {"vendor_return_id", "line_no"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class VendorReturnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vendor_return_line_seq")
    @SequenceGenerator(name = "vendor_return_line_seq",
        sequenceName = "vendor_return_line_seq", allocationSize = 50)
    private Long id;

    @Column(name = "vendor_return_id", nullable = false)
    private Long vendorReturnId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    @Column(name = "returned_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal returnedQty;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "vat_group_id", nullable = false)
    private Long vatGroupId;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "original_line_id")
    private Long originalLineId;

    @SuppressWarnings("java:S107")
    public VendorReturnLine(Long vendorReturnId, Integer lineNo, Long itemId, Long uomId,
                            BigDecimal returnedQty, BigDecimal unitPrice, Long vatGroupId,
                            BigDecimal taxAmount, BigDecimal lineTotal, Long originalLineId) {
        this.vendorReturnId = vendorReturnId;
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.uomId = uomId;
        this.returnedQty = returnedQty;
        this.unitPrice = unitPrice;
        this.vatGroupId = vatGroupId;
        this.taxAmount = taxAmount;
        this.lineTotal = lineTotal;
        this.originalLineId = originalLineId;
    }
}
