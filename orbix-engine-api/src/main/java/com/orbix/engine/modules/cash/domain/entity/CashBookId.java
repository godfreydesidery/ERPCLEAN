package com.orbix.engine.modules.cash.domain.entity;

import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashBookId implements Serializable {

    private Long branchId;

    @Enumerated(EnumType.STRING)
    private CashAccount account;

    private LocalDate businessDate;
}
