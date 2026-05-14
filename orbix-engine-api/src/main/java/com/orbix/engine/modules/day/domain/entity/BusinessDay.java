package com.orbix.engine.modules.day.domain.entity;

import com.orbix.engine.modules.day.domain.enums.BusinessDayStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A branch's logical business day, opened and closed explicitly. Drives posting
 * dates and blocks back-dated entries (except via a supervisor override).
 * At most one row per branch may be OPEN. DATA-MODEL.md §11.1.
 */
@Entity
@Table(name = "business_day")
@IdClass(BusinessDayId.class)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = {"branchId", "businessDate"})
public class BusinessDay {

    @Id
    @Column(name = "branch_id")
    private Long branchId;

    @Id
    @Column(name = "business_date")
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BusinessDayStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opened_by", nullable = false)
    private Long openedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "eod_report_object_key", length = 200)
    private String eodReportObjectKey;

    public BusinessDay(Long branchId, LocalDate businessDate, Long actorId) {
        this.branchId = branchId;
        this.businessDate = businessDate;
        this.status = BusinessDayStatus.OPEN;
        this.openedAt = Instant.now();
        this.openedBy = actorId;
    }

    /** OPEN -> CLOSING: EOD has started; new postings are blocked, close-out can run. */
    public void startClosing() {
        if (status != BusinessDayStatus.OPEN) {
            throw new IllegalStateException("Business day is not OPEN: " + status);
        }
        this.status = BusinessDayStatus.CLOSING;
    }

    /** CLOSING -> CLOSED: the day is finalised. */
    public void close(Long actorId, String eodReportObjectKey) {
        if (status != BusinessDayStatus.CLOSING) {
            throw new IllegalStateException("Business day is not CLOSING: " + status);
        }
        this.status = BusinessDayStatus.CLOSED;
        this.closedAt = Instant.now();
        this.closedBy = actorId;
        this.eodReportObjectKey = eodReportObjectKey;
    }
}
