package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Push a POS sale committed locally on the till to the server. POS sales never
 * start as DRAFT — they arrive already committed by the cashier. Idempotent on
 * {@code clientOpId} per company.
 */
public record PostPosSaleRequestDto(
    /** Client-namespaced number such as {@code TILL-3-20260513-00027}. */
    @NotBlank String number,
    /** UUID v7 generated client-side — same value on retry returns the original sale. */
    @NotBlank String clientOpId,
    @NotNull Long tillSessionId,
    @NotNull Long sectionId,
    @NotNull Long customerId,
    /** Supervisor user id stamped on the sale (e.g. for general supervisor PIN flows). */
    Long supervisorId,
    /** Required when any line's discountPct exceeds {@code orbix.pos.discount-threshold-pct}; must hold {@code POS.DISCOUNT_APPROVE}. */
    Long discountApproverId,
    @NotNull Instant saleAt,
    /** Optional header-level discount (after line discounts). Applied before tax in F5.3. */
    BigDecimal headerDiscountAmount,
    @NotEmpty @Valid List<Line> lines,
    @NotEmpty @Valid List<Payment> payments,
    String notes
) {
    public record Line(
        @NotNull Long itemId,
        Long uomId,
        @NotNull @Positive BigDecimal qty,
        @NotNull @PositiveOrZero BigDecimal unitPrice,
        BigDecimal discountPct,
        Long vatGroupId
    ) {}

    public record Payment(
        @NotNull PosPaymentMethod method,
        @NotNull @Positive BigDecimal amount,
        String reference,
        String terminalId,
        String last4
    ) {}
}
