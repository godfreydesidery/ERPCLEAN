package com.orbix.engine.modules.catalog.domain.entity;

import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.UomDimension;
import com.orbix.engine.modules.common.domain.entity.UidEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Unit of measure. Global (not company-scoped) — the one shared catalog table.
 * See the catalog README §3.
 */
@Entity
@Table(
    name = "uom",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_uom_uid",  columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_uom_code", columnNames = {"code"})
    }
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class Uom extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "uom_seq")
    @SequenceGenerator(name = "uom_seq", sequenceName = "uom_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UomDimension dimension;

    /** At most one base unit per dimension — enforced in the service layer. */
    @Column(name = "is_base", nullable = false)
    private boolean base;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ItemStatus status = ItemStatus.ACTIVE;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "updated_by", nullable = false) private Long updatedBy;

    public Uom(String code, String name, UomDimension dimension, boolean base, Long actorId) {
        this.code = code;
        this.name = name;
        this.dimension = dimension;
        this.base = base;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void update(String name, UomDimension dimension, boolean base, Long actorId) {
        this.name = name;
        this.dimension = dimension;
        this.base = base;
        touch(actorId);
    }

    /** Demote this unit from being the base of its dimension. */
    public void clearBase(Long actorId) {
        this.base = false;
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
