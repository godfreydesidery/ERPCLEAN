package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.pos.domain.entity.CashPickup;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CashPickupDto(
    Long id,
    Long tillSessionId,
    Long branchId,
    LocalDate businessDate,
    BigDecimal amount,
    Instant at,
    Long pickedUpBy,
    Long authorisedBy,
    String note
) {
    public static CashPickupDto from(CashPickup pickup) {
        return new CashPickupDto(
            pickup.getId(), pickup.getTillSessionId(), pickup.getBranchId(),
            pickup.getBusinessDate(), pickup.getAmount(), pickup.getAt(),
            pickup.getPickedUpBy(), pickup.getAuthorisedBy(), pickup.getNote());
    }
}
