package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.pos.domain.enums.PettyCashCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Payload for {@code POST /api/v1/petty-cash}. */
public record PostPettyCashRequestDto(
    @NotNull Long tillSessionId,
    @NotNull @Positive BigDecimal amount,
    @NotNull PettyCashCategory category,
    @Size(max = 120) String paidTo,
    /** Supervisor authorising the payout. Must hold {@code POS.PETTY_CASH} and differ from the caller. */
    @NotNull Long authorisedBy,
    @Size(max = 2000) String description,
    @Size(max = 200) String receiptAttachmentKey
) {}
