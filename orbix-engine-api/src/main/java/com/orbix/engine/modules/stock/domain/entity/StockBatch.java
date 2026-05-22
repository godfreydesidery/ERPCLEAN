package com.orbix.engine.modules.stock.domain.entity;

import com.orbix.engine.modules.stock.domain.enums.StockBatchStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Per-(branch, item, batch_no) inventory row carrying manufacture / expiry date.
 * Created on GRN (when item is batch-tracked) or on production output; drains via
 * FEFO consumption. DATA-MODEL.md §17.5.
 */
@Entity
@Table(name = "stock_batch",
    uniqueConstraints = @UniqueConstraint(name = "uk_stock_batch_branch_item_no",
        columnNames = {"branch_id", "item_id", "batch_no"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class StockBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_batch_seq")
    @SequenceGenerator(name = "stock_batch_seq", sequenceName = "stock_batch_seq", allocationSize = 50)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "batch_no", nullable = false, length = 40)
    private String batchNo;

    @Column(name = "manufactured_at")
    private LocalDate manufacturedAt;

    @Column(name = "expiry_at")
    private LocalDate expiryAt;

    @Column(name = "qty_received", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyReceived;

    @Column(name = "qty_on_hand", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyOnHand;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal cost;

    @Column(name = "source_doc_type", nullable = false, length = 40)
    private String sourceDocType;

    @Column(name = "source_doc_id", nullable = false)
    private Long sourceDocId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StockBatchStatus status = StockBatchStatus.ACTIVE;

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

    @SuppressWarnings("java:S107")  // batch rows are inherently wide (id + provenance + metrics + audit)
    public StockBatch(Long itemId, Long branchId, Long companyId, String batchNo,
                      LocalDate manufacturedAt, LocalDate expiryAt, BigDecimal qty,
                      BigDecimal cost, String sourceDocType, Long sourceDocId, Long actorId) {
        this.itemId = itemId;
        this.branchId = branchId;
        this.companyId = companyId;
        this.batchNo = batchNo;
        this.manufacturedAt = manufacturedAt;
        this.expiryAt = expiryAt;
        this.qtyReceived = qty;
        this.qtyOnHand = qty;
        this.cost = cost;
        this.sourceDocType = sourceDocType;
        this.sourceDocId = sourceDocId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    /**
     * Drains {@code qty} (positive) from this batch. Flips to {@code EXHAUSTED}
     * when on-hand reaches zero. Throws if the batch is not ACTIVE or if {@code qty}
     * exceeds the on-hand amount.
     */
    public void drain(BigDecimal qty, Long actorId) {
        if (status != StockBatchStatus.ACTIVE) {
            throw new IllegalStateException(
                "Cannot drain a " + status + " batch: id=" + id + " no=" + batchNo);
        }
        if (qty.signum() <= 0) {
            throw new IllegalArgumentException("Drain qty must be positive: " + qty);
        }
        if (qty.compareTo(qtyOnHand) > 0) {
            throw new IllegalArgumentException(
                "Cannot drain " + qty + " from batch " + batchNo + " (on-hand " + qtyOnHand + ")");
        }
        this.qtyOnHand = qtyOnHand.subtract(qty);
        if (qtyOnHand.signum() == 0) {
            this.status = StockBatchStatus.EXHAUSTED;
        }
        touch(actorId);
    }

    /** Flip from ACTIVE to EXPIRED. No-op when already in a terminal state. */
    public void markExpired(Long actorId) {
        if (status != StockBatchStatus.ACTIVE) {
            return;
        }
        this.status = StockBatchStatus.EXPIRED;
        touch(actorId);
    }

    /** Drains all remaining on-hand to zero and flips to the given terminal status — used by recall + expiry write-offs. */
    public void writeOffRemaining(StockBatchStatus terminalStatus, Long actorId) {
        if (status != StockBatchStatus.ACTIVE) {
            throw new IllegalStateException(
                "Cannot write off a " + status + " batch: id=" + id + " no=" + batchNo);
        }
        if (terminalStatus == StockBatchStatus.ACTIVE) {
            throw new IllegalArgumentException("Terminal status must not be ACTIVE");
        }
        this.qtyOnHand = BigDecimal.ZERO;
        this.status = terminalStatus;
        touch(actorId);
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
