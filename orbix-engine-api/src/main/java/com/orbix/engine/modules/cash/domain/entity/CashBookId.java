package com.orbix.engine.modules.cash.domain.entity;

import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Composite primary key for {@link CashBook}. Per F6.2 (Phase 1.1 §202)
 * the key extends from {@code (branch_id, account, business_date)} to
 * include {@code currency_code} so the projection splits per tender
 * currency (US-DAY-006).
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashBookId implements Serializable {

    private Long branchId;

    @Enumerated(EnumType.STRING)
    private CashAccount account;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    private LocalDate businessDate;
}
