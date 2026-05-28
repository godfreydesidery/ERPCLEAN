package com.orbix.engine.modules.sales.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffStatus;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffTargetKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Debt write-off request — covers both AR (CUSTOMER_INVOICE) and AP
 * (SUPPLIER_INVOICE) via the {@code targetKind} discriminator.
 * State machine: PENDING_APPROVAL → POSTED or REJECTED.
 */
@Entity
@Table(name = "debt_write_off",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_debt_write_off_uid", columnNames = {"uid"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtWriteOff extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "debt_write_off_seq")
    @SequenceGenerator(name = "debt_write_off_seq", sequenceName = "debt_write_off_seq", allocationSize = 50)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_kind", nullable = false, length = 32)
    private DebtWriteOffTargetKind targetKind;

    @Column(name = "target_invoice_id", nullable = false)
    private Long targetInvoiceId;

    @Column(name = "target_invoice_uid", nullable = false, length = 26)
    private String targetInvoiceUid;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "reason", nullable = false, length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DebtWriteOffStatus status;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "reason_for_reject", length = 2000)
    private String reasonForReject;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void approve(Long approverUserId) {
        if (status != DebtWriteOffStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                "Write-off is " + status + ", expected PENDING_APPROVAL");
        }
        Instant now = Instant.now();
        this.status = DebtWriteOffStatus.POSTED;
        this.approvedByUserId = approverUserId;
        this.approvedAt = now;
        this.postedAt = now;
        this.updatedAt = now;
    }

    public void reject(Long rejectorUserId, String reasonForReject) {
        if (status != DebtWriteOffStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                "Write-off is " + status + ", expected PENDING_APPROVAL");
        }
        Instant now = Instant.now();
        this.status = DebtWriteOffStatus.REJECTED;
        this.approvedByUserId = rejectorUserId;
        this.rejectedAt = now;
        this.reasonForReject = reasonForReject;
        this.updatedAt = now;
    }
}
