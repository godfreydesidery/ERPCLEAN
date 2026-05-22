package com.orbix.engine.modules.pos.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Payload for {@code POST /api/v1/cash-pickups}. */
public record PostCashPickupRequestDto(
    @NotNull Long tillSessionId,
    @NotNull @Positive BigDecimal amount,
    /** Supervisor authorising the pickup. Must hold {@code POS.CASH_PICKUP} and differ from the caller. */
    @NotNull Long authorisedBy,
    @Size(max = 200) String note
) {}
