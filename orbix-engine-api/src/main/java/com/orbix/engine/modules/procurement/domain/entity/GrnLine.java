package com.orbix.engine.modules.procurement.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** GRN line. DATA-MODEL.md §5.6. */
@Entity
@Table(name = "grn_line")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class GrnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "grn_line_seq")
    @SequenceGenerator(name = "grn_line_seq", sequenceName = "grn_line_seq", allocationSize = 50)
    private Long id;

    @Column(name = "grn_id", nullable = false)
    private Long grnId;

    @Column(name = "lpo_order_line_id")
    private Long lpoOrderLineId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    @Column(name = "received_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal receivedQty;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "vat_group_id", nullable = false)
    private Long vatGroupId;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal lineTotal;

    @Column(name = "batch_no", length = 40)
    private String batchNo;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @SuppressWarnings("java:S107")  // line construction mirrors the schema
    public GrnLine(Long grnId, Long lpoOrderLineId, Long itemId, Long uomId, BigDecimal receivedQty,
                   BigDecimal unitCost, Long vatGroupId, BigDecimal lineTotal,
                   String batchNo, LocalDate expiryDate) {
        this.grnId = grnId;
        this.lpoOrderLineId = lpoOrderLineId;
        this.itemId = itemId;
        this.uomId = uomId;
        this.receivedQty = receivedQty;
        this.unitCost = unitCost;
        this.vatGroupId = vatGroupId;
        this.lineTotal = lineTotal;
        this.batchNo = batchNo;
        this.expiryDate = expiryDate;
    }
}
