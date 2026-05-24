package com.orbix.engine.modules.pos.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A cashier shift on a till. DATA-MODEL.md §7.2. */
@Entity
@Table(name = "till_session",
    uniqueConstraints = @UniqueConstraint(name = "uk_till_session_uid", columnNames = {"uid"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class TillSession extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "till_session_seq")
    @SequenceGenerator(name = "till_session_seq", sequenceName = "till_session_seq",
        allocationSize = 50)
    private Long id;

    @Column(name = "till_id", nullable = false)
    private Long tillId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "opened_by", nullable = false)
    private Long openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opening_float_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal openingFloatAmount;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "expected_cash_amount", precision = 18, scale = 4)
    private BigDecimal expectedCashAmount;

    @Column(name = "declared_cash_amount", precision = 18, scale = 4)
    private BigDecimal declaredCashAmount;

    @Column(name = "variance_amount", precision = 18, scale = 4)
    private BigDecimal varianceAmount;

    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TillSessionStatus status = TillSessionStatus.OPEN;

    @Column(name = "z_report_object_key", length = 200)
    private String zReportObjectKey;

    @Column(length = 2000)
    private String notes;

    @Version
    private Integer version;

    public TillSession(Long tillId, Long branchId, Long companyId, LocalDate businessDate,
                      Long openedBy, BigDecimal openingFloat) {
        this.tillId = tillId;
        this.branchId = branchId;
        this.companyId = companyId;
        this.businessDate = businessDate;
        this.openedBy = openedBy;
        this.openedAt = Instant.now();
        this.openingFloatAmount = openingFloat;
        this.status = TillSessionStatus.OPEN;
    }

    public void close(BigDecimal expectedCash, BigDecimal declaredCash, Long actorId,
                      Long supervisorId, String notes) {
        if (status != TillSessionStatus.OPEN) {
            throw new IllegalStateException("Only OPEN sessions can be closed (was " + status + ")");
        }
        this.expectedCashAmount = expectedCash;
        this.declaredCashAmount = declaredCash;
        this.varianceAmount = declaredCash.subtract(expectedCash);
        this.closedBy = actorId;
        this.closedAt = Instant.now();
        this.supervisorId = supervisorId;
        this.notes = notes;
        this.status = TillSessionStatus.CLOSED;
    }

    public void reconcile(Long actorId) {
        if (status != TillSessionStatus.CLOSED) {
            throw new IllegalStateException("Only CLOSED sessions can be reconciled (was " + status + ")");
        }
        this.status = TillSessionStatus.RECONCILED;
        this.supervisorId = actorId;
    }
}
