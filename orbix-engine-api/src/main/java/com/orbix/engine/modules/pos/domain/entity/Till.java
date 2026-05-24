package com.orbix.engine.modules.pos.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.pos.domain.enums.TillStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A physical till / cashier workstation. DATA-MODEL.md §7.1. */
@Entity
@Table(name = "till",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_till_uid", columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_till_branch_code", columnNames = {"branch_id", "code"})
    })
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class Till extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "till_seq")
    @SequenceGenerator(name = "till_seq", sequenceName = "till_seq", allocationSize = 50)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "install_id", length = 80)
    private String installId;

    @Column(name = "default_price_list_id", nullable = false)
    private Long defaultPriceListId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TillStatus status = TillStatus.ACTIVE;

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

    public Till(Long companyId, Long branchId, String code, String name, Long defaultPriceListId,
                Long actorId) {
        this.companyId = companyId;
        this.branchId = branchId;
        this.code = code;
        this.name = name;
        this.defaultPriceListId = defaultPriceListId;
        this.status = TillStatus.ACTIVE;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void update(String name, Long defaultPriceListId, String installId, Long actorId) {
        this.name = name;
        this.defaultPriceListId = defaultPriceListId;
        this.installId = installId;
        touch(actorId);
    }

    public void deactivate(Long actorId) {
        this.status = TillStatus.INACTIVE;
        touch(actorId);
    }

    public void activate(Long actorId) {
        this.status = TillStatus.ACTIVE;
        touch(actorId);
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
