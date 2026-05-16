package com.orbix.engine.modules.production.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One material consumed by a {@link ProductionBatch}. DATA-MODEL §9.4.
 * Planned qty is set on plan (BOM-exploded total with wastage); actual qty
 * + unit cost + posted_at are filled when the PROD_CONSUME stock move is
 * posted at start (F7.3b — actual = planned for now; F7.3c will allow
 * operator override at post-output).
 */
@Entity
@Table(name = "production_consumption",
    uniqueConstraints = @UniqueConstraint(name = "uk_production_consumption_line_no",
        columnNames = {"production_batch_id", "line_no"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class ProductionConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "production_consumption_seq")
    @SequenceGenerator(name = "production_consumption_seq",
        sequenceName = "production_consumption_seq", allocationSize = 50)
    private Long id;

    @Column(name = "production_batch_id", nullable = false)
    private Long productionBatchId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "input_item_id", nullable = false)
    private Long inputItemId;

    @Column(name = "planned_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal plannedQty;

    @Column(name = "actual_qty", precision = 18, scale = 4)
    private BigDecimal actualQty;

    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    @Column(name = "unit_cost", precision = 18, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(length = 200)
    private String notes;

    @SuppressWarnings("java:S107")
    public ProductionConsumption(Long productionBatchId, Integer lineNo, Long inputItemId,
                                 BigDecimal plannedQty, Long uomId, String notes) {
        this.productionBatchId = productionBatchId;
        this.lineNo = lineNo;
        this.inputItemId = inputItemId;
        this.plannedQty = plannedQty;
        this.uomId = uomId;
        this.notes = notes;
    }

    public void recordActual(BigDecimal actualQty, BigDecimal unitCost) {
        this.actualQty = actualQty;
        this.unitCost = unitCost;
        this.postedAt = Instant.now();
    }
}
