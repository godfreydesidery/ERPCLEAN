package com.orbix.engine.modules.catalog.domain.entity;

import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.common.domain.entity.UidEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** Named price book — RETAIL / WHOLESALE / AGENT / STAFF. DATA-MODEL.md §3.8. */
@Entity
@Table(
    name = "price_list",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_price_list_uid",          columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_price_list_company_code", columnNames = {"company_id", "code"})
    }
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class PriceList extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "price_list_seq")
    @SequenceGenerator(name = "price_list_seq", sequenceName = "price_list_seq", allocationSize = 50)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "tax_inclusive", nullable = false)
    private boolean taxInclusive;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ItemStatus status = ItemStatus.ACTIVE;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "updated_by", nullable = false) private Long updatedBy;

    @SuppressWarnings("java:S107")  // mirrors the spec column set; a VO would cost more than it saves
    public PriceList(Long companyId, String code, String name, String currencyCode,
                     LocalDate validFrom, LocalDate validTo, boolean isDefault,
                     boolean taxInclusive, Long actorId) {
        this.companyId = companyId;
        this.code = code;
        this.name = name;
        this.currencyCode = currencyCode;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.isDefault = isDefault;
        this.taxInclusive = taxInclusive;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    @SuppressWarnings("java:S107")
    public void update(String name, String currencyCode, LocalDate validFrom, LocalDate validTo,
                       boolean taxInclusive, Long actorId) {
        this.name = name;
        this.currencyCode = currencyCode;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.taxInclusive = taxInclusive;
        touch(actorId);
    }

    public void setAsDefault(boolean value, Long actorId) {
        this.isDefault = value;
        touch(actorId);
    }

    public void archive(Long actorId) {
        this.status = ItemStatus.ARCHIVED;
        touch(actorId);
    }

    public void activate(Long actorId) {
        this.status = ItemStatus.ACTIVE;
        touch(actorId);
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
