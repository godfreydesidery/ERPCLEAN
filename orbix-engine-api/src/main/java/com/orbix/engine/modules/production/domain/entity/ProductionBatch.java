package com.orbix.engine.modules.production.domain.entity;

import com.orbix.engine.modules.production.domain.enums.ProductionBatchStatus;
import com.orbix.engine.modules.production.domain.enums.ProductionLifecycleState;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single planned + executed production run. DATA-MODEL §9.3 + Phase 1.1
 * additions (section_id required, lifecycle_state).
 *
 * <p>For F7.3b the coarse {@code status} drives the plan/start/post-output
 * transitions; {@code lifecycleState} is a parallel finer-grained dimension
 * advanced by F7.3c's lifecycle endpoint.
 */
@Entity
@Table(name = "production_batch",
    uniqueConstraints = @UniqueConstraint(name = "uk_production_batch_branch_number",
        columnNames = {"branch_id", "number"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class ProductionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "production_batch_seq")
    @SequenceGenerator(name = "production_batch_seq", sequenceName = "production_batch_seq",
        allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    /** Null for custom (ad-hoc) production — added in F7.3c. */
    @Column(name = "bom_id")
    private Long bomId;

    @Column(name = "output_item_id", nullable = false)
    private Long outputItemId;

    @Column(name = "planned_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal plannedQty;

    @Column(name = "actual_qty", precision = 18, scale = 4)
    private BigDecimal actualQty;

    @Column(name = "reject_qty", precision = 18, scale = 4)
    private BigDecimal rejectQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductionBatchStatus status = ProductionBatchStatus.PLANNED;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private ProductionLifecycleState lifecycleState = ProductionLifecycleState.PLANNED;

    @Column(name = "planned_at", nullable = false)
    private Instant plannedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "started_by")
    private Long startedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(length = 2000)
    private String notes;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @SuppressWarnings("java:S107")
    public ProductionBatch(String number, Long companyId, Long branchId, Long sectionId,
                           Long bomId, Long outputItemId, BigDecimal plannedQty,
                           String notes, Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.sectionId = sectionId;
        this.bomId = bomId;
        this.outputItemId = outputItemId;
        this.plannedQty = plannedQty;
        this.status = ProductionBatchStatus.PLANNED;
        this.lifecycleState = ProductionLifecycleState.PLANNED;
        this.notes = notes;
        Instant now = Instant.now();
        this.plannedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void markStarted(Long actorId) {
        if (status != ProductionBatchStatus.PLANNED) {
            throw new IllegalStateException("Only PLANNED batches can be started (was " + status + ")");
        }
        this.status = ProductionBatchStatus.IN_PROGRESS;
        this.lifecycleState = ProductionLifecycleState.IN_PROGRESS;
        this.startedAt = Instant.now();
        this.startedBy = actorId;
        touch(actorId);
    }

    public void markCompleted(BigDecimal actualQty, Long actorId) {
        if (status != ProductionBatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only IN_PROGRESS batches can post output (was " + status + ")");
        }
        this.status = ProductionBatchStatus.COMPLETED;
        this.lifecycleState = ProductionLifecycleState.OUTPUT_HOT_DISPLAY;
        this.actualQty = actualQty;
        this.completedAt = Instant.now();
        this.completedBy = actorId;
        touch(actorId);
    }

    public void cancel(Long actorId) {
        if (status != ProductionBatchStatus.PLANNED) {
            throw new IllegalStateException(
                "Only PLANNED batches can be cancelled (was " + status
                    + ") — once IN_PROGRESS use the lifecycle write-off path");
        }
        this.status = ProductionBatchStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.cancelledBy = actorId;
        touch(actorId);
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
