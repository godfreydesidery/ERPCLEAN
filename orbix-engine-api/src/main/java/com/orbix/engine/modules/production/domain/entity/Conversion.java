package com.orbix.engine.modules.production.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.production.domain.enums.ConversionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One-shot non-BOM item transformation (F7.4 / US-PROD-007). DATA-MODEL §9.6.
 *
 * <p>Posting writes paired PROD_CONSUME (outbound from_item) + PROD_OUTPUT
 * (inbound to_item) stock_moves in one transaction. {@code unit_cost} is the
 * to_item's cost on output — derived from {@code from_item.avg_cost ×
 * from_qty / to_qty} when omitted, so a one-to-many split (one sack of bulk
 * flour → many small packs) preserves total value.
 */
@Entity
@Table(name = "conversion",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_conversion_uid", columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_conversion_branch_number",
            columnNames = {"branch_id", "number"})
    })
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class Conversion extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "conversion_seq")
    @SequenceGenerator(name = "conversion_seq", sequenceName = "conversion_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "conversion_date", nullable = false)
    private LocalDate conversionDate;

    @Column(name = "from_item_id", nullable = false)
    private Long fromItemId;

    @Column(name = "from_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal fromQty;

    @Column(name = "from_uom_id", nullable = false)
    private Long fromUomId;

    @Column(name = "to_item_id", nullable = false)
    private Long toItemId;

    @Column(name = "to_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal toQty;

    @Column(name = "to_uom_id", nullable = false)
    private Long toUomId;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(length = 120)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversionStatus status = ConversionStatus.DRAFT;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

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
    public Conversion(String number, Long companyId, Long branchId, LocalDate conversionDate,
                      Long fromItemId, BigDecimal fromQty, Long fromUomId,
                      Long toItemId, BigDecimal toQty, Long toUomId,
                      String reason, Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.conversionDate = conversionDate;
        this.fromItemId = fromItemId;
        this.fromQty = fromQty;
        this.fromUomId = fromUomId;
        this.toItemId = toItemId;
        this.toQty = toQty;
        this.toUomId = toUomId;
        this.reason = reason;
        this.status = ConversionStatus.DRAFT;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void markPosted(BigDecimal unitCost, Long actorId) {
        if (status != ConversionStatus.DRAFT) {
            throw new IllegalStateException(
                "Only DRAFT conversions can be posted (was " + status + ")");
        }
        this.unitCost = unitCost != null ? unitCost : BigDecimal.ZERO;
        this.status = ConversionStatus.POSTED;
        this.postedAt = Instant.now();
        this.postedBy = actorId;
        touch(actorId);
    }

    public void cancel(Long actorId) {
        if (status != ConversionStatus.DRAFT) {
            throw new IllegalStateException(
                "Only DRAFT conversions can be cancelled (was " + status + ")");
        }
        this.status = ConversionStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.cancelledBy = actorId;
        touch(actorId);
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
