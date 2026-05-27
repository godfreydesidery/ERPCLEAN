package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.pos.domain.entity.CashPickup;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Cash-pickup response. Slice D — surrogate-Long PK aggregate: carries both
 * {@code id} (stringified on the wire) and {@code uid} (external URL handle).
 * Append-only; no archive lifecycle.
 */
public record CashPickupDto(
    String uid,
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
            pickup.getUid(),
            pickup.getId(), pickup.getTillSessionId(), pickup.getBranchId(),
            pickup.getBusinessDate(), pickup.getAmount(), pickup.getAt(),
            pickup.getPickedUpBy(), pickup.getAuthorisedBy(), pickup.getNote());
    }
}
