package com.orbix.engine.modules.party.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Supplier role attached to a {@link Party} via a shared primary key.
 * DATA-MODEL.md §2.5.
 */
@Entity
@Table(name = "supplier")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "partyId")
public class Supplier {

    @Id
    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays = 0;

    @Column(name = "credit_limit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal creditLimitAmount = BigDecimal.ZERO;

    @Column(name = "default_currency_code", length = 3)
    private String defaultCurrencyCode;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "bank_account_no", length = 40)
    private String bankAccountNo;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    public Supplier(Long partyId) {
        this.partyId = partyId;
    }

    public void update(int paymentTermsDays, BigDecimal creditLimitAmount, String defaultCurrencyCode,
                       String bankName, String bankAccountNo, Integer leadTimeDays) {
        this.paymentTermsDays = paymentTermsDays;
        this.creditLimitAmount = creditLimitAmount != null ? creditLimitAmount : BigDecimal.ZERO;
        this.defaultCurrencyCode = defaultCurrencyCode;
        this.bankName = bankName;
        this.bankAccountNo = bankAccountNo;
        this.leadTimeDays = leadTimeDays;
    }
}
