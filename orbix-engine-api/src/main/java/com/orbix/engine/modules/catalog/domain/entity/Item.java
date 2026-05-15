package com.orbix.engine.modules.catalog.domain.entity;

import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.domain.enums.WeighingUnit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single unified item — replaces the legacy {@code Product} / {@code Material} split.
 * See DATA-MODEL.md §3.2.
 */
@Entity
@Table(
    name = "item",
    uniqueConstraints = @UniqueConstraint(name = "uk_item_company_code", columnNames = {"company_id", "code"})
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "item_seq")
    @SequenceGenerator(name = "item_seq", sequenceName = "item_seq", allocationSize = 50)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "short_name", length = 80)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemType type;

    @Column(name = "item_group_id", nullable = false)
    private Long itemGroupId;

    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    @Column(name = "vat_group_id", nullable = false)
    private Long vatGroupId;

    @Column(name = "is_tracked", nullable = false)
    private boolean tracked = true;

    @Column(name = "is_weighed", nullable = false)
    private boolean weighed = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "weighing_unit", length = 10)
    private WeighingUnit weighingUnit;

    @Column(name = "is_batch_tracked", nullable = false)
    private boolean batchTracked = false;

    @Column(name = "avg_cost", precision = 18, scale = 4, nullable = false)
    private BigDecimal avgCost = BigDecimal.ZERO;

    @Column(name = "last_cost", precision = 18, scale = 4, nullable = false)
    private BigDecimal lastCost = BigDecimal.ZERO;

    @Column(name = "min_sell_price", precision = 18, scale = 4)
    private BigDecimal minSellPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ItemStatus status = ItemStatus.ACTIVE;

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

    @SuppressWarnings("java:S107")  // 7 required init fields + actorId for audit; grouping into a VO costs more than it saves
    public Item(Long companyId, String code, String name, ItemType type,
                Long itemGroupId, Long uomId, Long vatGroupId, Long actorId) {
        this.companyId = companyId;
        this.code = code;
        this.name = name;
        this.type = type;
        this.itemGroupId = itemGroupId;
        this.uomId = uomId;
        this.vatGroupId = vatGroupId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    /** Audited rename — prefer this over {@code setName} so updatedAt / updatedBy stay in sync. */
    public void rename(String newName, Long actorId) {
        this.name = newName;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /** Audited attribute edit. Code is immutable and not updatable here. */
    @SuppressWarnings("java:S107")  // mirrors the constructor's field set; a VO would cost more than it saves
    public void update(String name, String shortName, ItemType type, Long itemGroupId,
                       Long uomId, Long vatGroupId, boolean tracked,
                       java.math.BigDecimal minSellPrice, Long actorId) {
        this.name = name;
        this.shortName = shortName;
        this.type = type;
        this.itemGroupId = itemGroupId;
        this.uomId = uomId;
        this.vatGroupId = vatGroupId;
        this.tracked = tracked;
        this.minSellPrice = minSellPrice;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /** Audited archive — prefer this over {@code setStatus(ARCHIVED)}. */
    public void archive(Long actorId) {
        this.status = ItemStatus.ARCHIVED;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /** Audited un-archive back to ACTIVE. */
    public void activate(Long actorId) {
        this.status = ItemStatus.ACTIVE;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /**
     * Sets the weighed flag and its unit together. {@code unit} must be non-null
     * iff {@code weighed} — the caller validates that invariant.
     */
    public void applyWeighing(boolean weighed, WeighingUnit unit, Long actorId) {
        this.weighed = weighed;
        this.weighingUnit = weighed ? unit : null;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    public void applyBatchTracking(boolean batchTracked, Long actorId) {
        this.batchTracked = batchTracked;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
