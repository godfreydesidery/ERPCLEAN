package com.orbix.engine.modules.cash.domain.entity;

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
 * End-of-day banking deposit slip (F6.3). Standalone audit-doc; pairs with
 * two {@code cash_entry} rows (OUT-CASH_BOX + IN-BANK) sharing this row's
 * id as their {@code ref_id} and {@code ref_type = BankDeposit}.
 *
 * <p>Slice D — uid + reversal lifecycle: archiving a posted deposit stamps
 * {@code reversedAt} / {@code reversedBy} and the two compensating-entry
 * ids ({@code reversedByOutEntryId} = mirror of the original CASH_BOX OUT,
 * {@code reversedByInEntryId} = mirror of the original BANK IN), and posts
 * a paired compensating cash entry under a new {@code BANK_DEPOSIT_REVERSAL}
 * ref_type.
 */
@Entity
@Table(
    name = "bank_deposit",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_bank_deposit_uid", columnNames = {"uid"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class BankDeposit extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bank_deposit_seq")
    @SequenceGenerator(name = "bank_deposit_seq", sequenceName = "bank_deposit_seq",
        allocationSize = 50)
    @Setter
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 80)
    private String reference;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private Instant at;

    @Column(name = "posted_by", nullable = false)
    private Long postedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by")
    private Long reversedBy;

    /** Compensating CASH_BOX IN entry that mirrors the original CASH_BOX OUT. */
    @Column(name = "reversed_by_out_entry_id")
    private Long reversedByOutEntryId;

    /** Compensating BANK OUT entry that mirrors the original BANK IN. */
    @Column(name = "reversed_by_in_entry_id")
    private Long reversedByInEntryId;

    @SuppressWarnings("java:S107")
    public BankDeposit(Long companyId, Long branchId, LocalDate businessDate,
                       BigDecimal amount, String currencyCode, String reference, String notes,
                       Instant at, Long postedBy) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("bank_deposit amount must be > 0");
        }
        this.companyId = companyId;
        this.branchId = branchId;
        this.businessDate = businessDate;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.reference = reference;
        this.notes = notes;
        this.at = at;
        this.postedBy = postedBy;
        this.createdAt = Instant.now();
    }

    /** {@code true} once {@link #markReversed(Long, Long, Long)} has stamped the row. */
    public boolean isReversed() {
        return reversedAt != null;
    }

    /**
     * Stamp the reversal columns. Called once, in the same transaction as the
     * two compensating {@code cash_entry} inserts.
     *
     * @param actorId            the user reversing the deposit
     * @param outEntryId         id of the compensating CASH_BOX IN entry (mirror of original OUT)
     * @param inEntryId          id of the compensating BANK OUT entry (mirror of original IN)
     */
    public void markReversed(Long actorId, Long outEntryId, Long inEntryId) {
        if (isReversed()) {
            throw new IllegalStateException("Bank deposit is already reversed: " + getUid());
        }
        this.reversedAt = Instant.now();
        this.reversedBy = actorId;
        this.reversedByOutEntryId = outEntryId;
        this.reversedByInEntryId = inEntryId;
    }
}
