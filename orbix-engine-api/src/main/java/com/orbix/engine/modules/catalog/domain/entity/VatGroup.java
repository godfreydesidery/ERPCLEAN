package com.orbix.engine.modules.catalog.domain.entity;

import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.common.domain.entity.UidEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Tax classification per company. {@code rate} is a fraction (0.18 = 18%). */
@Entity
@Table(
    name = "vat_group",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_vat_group_uid",          columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_vat_group_company_code", columnNames = {"company_id", "code"})
    }
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class VatGroup extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vat_group_seq")
    @SequenceGenerator(name = "vat_group_seq", sequenceName = "vat_group_seq", allocationSize = 50)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal rate;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ItemStatus status = ItemStatus.ACTIVE;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "updated_by", nullable = false) private Long updatedBy;

    public VatGroup(Long companyId, String code, String name, BigDecimal rate,
                    LocalDate validFrom, boolean isDefault, Long actorId) {
        this.companyId = companyId;
        this.code = code;
        this.name = name;
        this.rate = rate;
        this.validFrom = validFrom;
        this.isDefault = isDefault;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void update(String name, BigDecimal rate, LocalDate validFrom, Long actorId) {
        this.name = name;
        this.rate = rate;
        this.validFrom = validFrom;
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
