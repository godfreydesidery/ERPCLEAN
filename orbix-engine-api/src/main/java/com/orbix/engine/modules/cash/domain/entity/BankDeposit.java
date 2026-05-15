package com.orbix.engine.modules.cash.domain.entity;

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
 */
@Entity
@Table(name = "bank_deposit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class BankDeposit {

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
}
