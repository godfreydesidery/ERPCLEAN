package com.orbix.engine.modules.cash.domain.entity;

import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
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
 * Supervisor cash adjustment (F6.3). Standalone audit-doc with a mandatory
 * reason; pairs 1:1 with a {@code cash_entry} whose {@code ref_id} is this
 * row's id and {@code gl_category = ADJUSTMENT}.
 */
@Entity
@Table(name = "cash_adjustment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class CashAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cash_adjustment_seq")
    @SequenceGenerator(name = "cash_adjustment_seq", sequenceName = "cash_adjustment_seq",
        allocationSize = 50)
    @Setter
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CashAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashDirection direction;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(nullable = false)
    private Instant at;

    @Column(name = "posted_by", nullable = false)
    private Long postedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @SuppressWarnings("java:S107")
    public CashAdjustment(Long companyId, Long branchId, LocalDate businessDate,
                          CashAccount account, CashDirection direction, BigDecimal amount,
                          String currencyCode, String reason, Instant at, Long postedBy) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("cash_adjustment amount must be > 0");
        }
        this.companyId = companyId;
        this.branchId = branchId;
        this.businessDate = businessDate;
        this.account = account;
        this.direction = direction;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.reason = reason;
        this.at = at;
        this.postedBy = postedBy;
        this.createdAt = Instant.now();
    }
}
