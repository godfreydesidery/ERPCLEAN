package com.orbix.engine.modules.admin.domain.entity;

import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.domain.enums.SectionType;
import com.orbix.engine.modules.common.domain.entity.UidEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Department within a branch (bakery, butchery, deli, retail floor, etc.). DATA-MODEL.md §17.1. */
@Entity
@Table(
    name = "section",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_section_uid",         columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_section_branch_code", columnNames = {"branch_id", "code"})
    }
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class Section extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "section_seq")
    @SequenceGenerator(name = "section_seq", sequenceName = "section_seq", allocationSize = 50)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SectionType type;

    @Column(name = "manager_user_id")
    private Long managerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdminStatus status = AdminStatus.ACTIVE;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "updated_by", nullable = false) private Long updatedBy;

    public Section(Long branchId, String code, String name, SectionType type, Long actorId) {
        this.branchId = branchId;
        this.code = code;
        this.name = name;
        this.type = type;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void updateDetails(String name, SectionType type, Long managerUserId, Long actorId) {
        this.name = name;
        this.type = type;
        this.managerUserId = managerUserId;
        touch(actorId);
    }

    public void deactivate(Long actorId) {
        this.status = AdminStatus.INACTIVE;
        touch(actorId);
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
