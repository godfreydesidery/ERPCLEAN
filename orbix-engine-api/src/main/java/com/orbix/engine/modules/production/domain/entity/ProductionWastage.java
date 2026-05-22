package com.orbix.engine.modules.production.domain.entity;

import com.orbix.engine.modules.production.domain.enums.WastageCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Category-tagged loss against a {@link ProductionBatch}. DATA-MODEL §17.11.
 *
 * <p>Wastage qty does NOT enter stock — it's a side-channel record for
 * reporting / variance analysis. The mandatory {@code reason} ensures the
 * audit trail explains every loss.
 */
@Entity
@Table(name = "production_wastage")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class ProductionWastage {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "production_wastage_seq")
    @SequenceGenerator(name = "production_wastage_seq", sequenceName = "production_wastage_seq",
        allocationSize = 50)
    private Long id;

    @Column(name = "production_batch_id", nullable = false)
    private Long productionBatchId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal qty;

    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WastageCategory category;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @SuppressWarnings("java:S107")
    public ProductionWastage(Long productionBatchId, Long itemId, BigDecimal qty, Long uomId,
                             WastageCategory category, String reason, Long recordedBy) {
        this.productionBatchId = productionBatchId;
        this.itemId = itemId;
        this.qty = qty;
        this.uomId = uomId;
        this.category = category;
        this.reason = reason;
        this.recordedBy = recordedBy;
        this.recordedAt = Instant.now();
    }
}
