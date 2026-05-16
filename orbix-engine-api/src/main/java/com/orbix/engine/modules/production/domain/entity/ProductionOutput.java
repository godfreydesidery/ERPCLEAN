package com.orbix.engine.modules.production.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One output line of a completed {@link ProductionBatch}. DATA-MODEL §9.5
 * + Phase 1.1 additions ({@code is_pack_by_weight}, {@code batch_id} → stock_batch).
 *
 * <p>{@code unitCost} is computed at post time as sum-of-consumption-cost
 * divided by total-output-qty (proportional split across lines). For
 * batch-tracked output items, {@code batchId} points at the freshly-created
 * {@code stock_batch} row that the PROD_OUTPUT {@code stock_move} carries.
 */
@Entity
@Table(name = "production_output",
    uniqueConstraints = @UniqueConstraint(name = "uk_production_output_line_no",
        columnNames = {"production_batch_id", "line_no"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class ProductionOutput {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "production_output_seq")
    @SequenceGenerator(name = "production_output_seq", sequenceName = "production_output_seq",
        allocationSize = 50)
    private Long id;

    @Column(name = "production_batch_id", nullable = false)
    private Long productionBatchId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "output_item_id", nullable = false)
    private Long outputItemId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal qty;

    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    @Column(name = "is_pack_by_weight", nullable = false)
    private boolean packByWeight = false;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "batch_no", length = 40)
    private String batchNo;

    @Column(name = "manufactured_at")
    private LocalDate manufacturedAt;

    @Column(name = "expiry_at")
    private LocalDate expiryAt;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(length = 200)
    private String notes;

    @SuppressWarnings("java:S107")
    public ProductionOutput(Long productionBatchId, Integer lineNo, Long outputItemId,
                            BigDecimal qty, Long uomId, BigDecimal unitCost,
                            boolean primary, boolean packByWeight, Long batchId, String batchNo,
                            LocalDate manufacturedAt, LocalDate expiryAt, String notes) {
        this.productionBatchId = productionBatchId;
        this.lineNo = lineNo;
        this.outputItemId = outputItemId;
        this.qty = qty;
        this.uomId = uomId;
        this.unitCost = unitCost != null ? unitCost : BigDecimal.ZERO;
        this.primary = primary;
        this.packByWeight = packByWeight;
        this.batchId = batchId;
        this.batchNo = batchNo;
        this.manufacturedAt = manufacturedAt;
        this.expiryAt = expiryAt;
        this.notes = notes;
        this.postedAt = Instant.now();
    }
}
