package com.orbix.engine.modules.pos.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.pos.domain.enums.PettyCashCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Small payout from the till drawer (deliveries, courier, office sundries).
 * DATA-MODEL.md §7.7. {@code till_session_id} may be NULL when the payout
 * came from the main cash book rather than a till, but at MVP we only
 * support till-side petty cash. Slice D adds {@code uid} (inherited from
 * {@link UidEntity}) as the external URL handle. No archive lifecycle —
 * petty-cash payouts are append-only.
 */
@Entity
@Table(
    name = "petty_cash",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_petty_cash_uid",       columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_petty_cash_client_op", columnNames = {"company_id", "client_op_id"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class PettyCash extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "petty_cash_seq")
    @SequenceGenerator(name = "petty_cash_seq", sequenceName = "petty_cash_seq", allocationSize = 50)
    @Setter
    private Long id;

    @Column(name = "till_session_id")
    private Long tillSessionId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Idempotency key for device-outbox pushes. NULL for payouts posted online.
     *  Unique per (company_id, client_op_id) — see uk_petty_cash_client_op. */
    @Setter
    @Column(name = "client_op_id", length = 40)
    private String clientOpId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant at;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PettyCashCategory category;

    @Column(name = "paid_to", length = 120)
    private String paidTo;

    @Column(name = "paid_by", nullable = false)
    private Long paidBy;

    @Column(name = "authorised_by", nullable = false)
    private Long authorisedBy;

    @Column(length = 2000)
    private String description;

    @Column(name = "receipt_attachment_key", length = 200)
    private String receiptAttachmentKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @SuppressWarnings("java:S107")  // posting row is inherently wide
    public PettyCash(Long tillSessionId, Long companyId, Long branchId, LocalDate businessDate,
                     BigDecimal amount, Instant at, PettyCashCategory category, String paidTo,
                     Long paidBy, Long authorisedBy, String description, String receiptAttachmentKey) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("petty_cash amount must be > 0");
        }
        this.tillSessionId = tillSessionId;
        this.companyId = companyId;
        this.branchId = branchId;
        this.businessDate = businessDate;
        this.amount = amount;
        this.at = at;
        this.category = category;
        this.paidTo = paidTo;
        this.paidBy = paidBy;
        this.authorisedBy = authorisedBy;
        this.description = description;
        this.receiptAttachmentKey = receiptAttachmentKey;
        this.createdAt = Instant.now();
    }
}
