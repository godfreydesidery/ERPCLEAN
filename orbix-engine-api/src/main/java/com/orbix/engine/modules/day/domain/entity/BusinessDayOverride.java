package com.orbix.engine.modules.day.domain.entity;

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
 */
@Entity
@Table(name = "business_day_override")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class BusinessDayOverride {

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
}
