package com.orbix.engine.modules.catalog.domain.entity;

import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.common.domain.entity.UidEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Self-referencing item-group tree. {@code parentId} is null at the root;
 * {@code level} is a denormalised depth hint (1 = root) recomputed on move.
 * See DATA-MODEL.md §3 and the catalog README.
 */
@Entity
@Table(
    name = "item_group",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_item_group_uid",          columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_item_group_company_code", columnNames = {"company_id", "code"})
    }
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class ItemGroup extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "item_group_seq")
    @SequenceGenerator(name = "item_group_seq", sequenceName = "item_group_seq", allocationSize = 50)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ItemStatus status = ItemStatus.ACTIVE;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "updated_by", nullable = false) private Long updatedBy;

    public ItemGroup(Long companyId, Long parentId, int level, String code, String name, Long actorId) {
        this.companyId = companyId;
        this.parentId = parentId;
        this.level = level;
        this.code = code;
        this.name = name;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void rename(String newName, Long actorId) {
        this.name = newName;
        touch(actorId);
    }

    public void moveTo(Long newParentId, int newLevel, Long actorId) {
        this.parentId = newParentId;
        this.level = newLevel;
        touch(actorId);
    }

    /** Shifts a node's level when an ancestor moved. Parent link is unchanged. */
    public void shiftLevel(int delta, Long actorId) {
        this.level += delta;
        touch(actorId);
    }

    public void archive(Long actorId) {
        this.status = ItemStatus.ARCHIVED;
        touch(actorId);
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
