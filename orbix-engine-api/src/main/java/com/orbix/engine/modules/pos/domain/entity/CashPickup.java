package com.orbix.engine.modules.pos.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
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
 * Mid-shift cash withdrawal from the till drawer for safety (large notes
 * moved to the back-office safe). DATA-MODEL.md §7.6. Immutable record;
 * Slice D adds {@code uid} (inherited from {@link UidEntity}) as the
 * external URL handle. No archive lifecycle — pickups are append-only.
 */
@Entity
@Table(
    name = "cash_pickup",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_cash_pickup_uid", columnNames = {"uid"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class CashPickup extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cash_pickup_seq")
    @SequenceGenerator(name = "cash_pickup_seq", sequenceName = "cash_pickup_seq", allocationSize = 50)
    @Setter
    private Long id;

    @Column(name = "till_session_id", nullable = false)
    private Long tillSessionId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant at;

    @Column(name = "picked_up_by", nullable = false)
    private Long pickedUpBy;

    @Column(name = "authorised_by", nullable = false)
    private Long authorisedBy;

    @Column(length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @SuppressWarnings("java:S107")  // posting row is inherently wide
    public CashPickup(Long tillSessionId, Long companyId, Long branchId, LocalDate businessDate,
                      BigDecimal amount, Instant at, Long pickedUpBy, Long authorisedBy, String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("cash_pickup amount must be > 0");
        }
        this.tillSessionId = tillSessionId;
        this.companyId = companyId;
        this.branchId = branchId;
        this.businessDate = businessDate;
        this.amount = amount;
        this.at = at;
        this.pickedUpBy = pickedUpBy;
        this.authorisedBy = authorisedBy;
        this.note = note;
        this.createdAt = Instant.now();
    }
}
