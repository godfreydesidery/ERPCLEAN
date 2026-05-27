package com.orbix.engine.modules.day.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Audit record written whenever a supervisor back-dates a posting into a
 * closed business day. Rare and tightly scoped. DATA-MODEL.md §11.2.
 *
 * <p>Surrogate-Long PK; uid is the external URL handle (Slice D — overrides
 * are linkable from audit views).
 */
@Entity
@Table(
    name = "business_day_override",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_business_day_override_uid", columnNames = {"uid"})
    }
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class BusinessDayOverride extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "business_day_override_seq")
    @SequenceGenerator(name = "business_day_override_seq",
        sequenceName = "business_day_override_seq", allocationSize = 50)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "target_business_date", nullable = false)
    private LocalDate targetBusinessDate;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "authorised_by", nullable = false)
    private Long authorisedBy;

    @Column(nullable = false)
    private Instant at;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private Long archivedBy;

    public BusinessDayOverride(Long branchId, LocalDate targetBusinessDate, String entityType,
                               Long entityId, String reason, Long authorisedBy) {
        this.branchId = branchId;
        this.targetBusinessDate = targetBusinessDate;
        this.entityType = entityType;
        this.entityId = entityId;
        this.reason = reason;
        this.authorisedBy = authorisedBy;
        this.at = Instant.now();
    }

    /** Returns {@code true} once the override has been voided (archive lifecycle). */
    public boolean isArchived() {
        return archivedAt != null;
    }

    /**
     * Mark the override as voided. Called before the back-dated post lands;
     * after the post succeeds the override is immutable and this throws.
     */
    public void archive(Long actorId) {
        if (isArchived()) {
            throw new IllegalStateException("Business day override is already archived: " + getUid());
        }
        this.archivedAt = Instant.now();
        this.archivedBy = actorId;
    }
}
