package com.orbix.engine.modules.catalog.domain.entity;

import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import jakarta.persistence.*;

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

    protected Item() {}

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

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public ItemType getType() { return type; }
    public ItemStatus getStatus() { return status; }
    public BigDecimal getAvgCost() { return avgCost; }
    public BigDecimal getLastCost() { return lastCost; }

    public void rename(String newName, Long actorId) {
        this.name = newName;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    public void archive(Long actorId) {
        this.status = ItemStatus.ARCHIVED;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
