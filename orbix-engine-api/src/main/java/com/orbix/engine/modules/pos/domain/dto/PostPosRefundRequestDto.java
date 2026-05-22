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
 * Refund a previously-POSTED POS sale (F5.5). Creates a new {@code PosSale}
 * with {@code kind = REFUND} that references the original via
 * {@code refunded_from_sale_id} and writes compensating {@code RETURN_IN}
 * stock moves at the snapped line cost. Same-business-day-only.
 *
 * <p>Above {@code orbix.pos.refund-threshold} the request requires a
 * {@code supervisorId} holding {@code POS.REFUND_APPROVE}, different from the
 * cashier. Batch-tracked items are rejected for now (restock-to-original-batch
 * is a separate concern, same constraint as F5.3 void and F4.4 customer return).
 */
public record PostPosRefundRequestDto(
    @NotBlank String number,
    @NotBlank String clientOpId,
    @NotNull Long tillSessionId,
    @NotNull Long originalSaleId,
    @NotNull Long sectionId,
    @NotNull Long customerId,
    Long supervisorId,
    @NotNull Instant saleAt,
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

    /** Tender amount {@code amount} is in {@code tenderCurrency}; FX rate snapped at refund time. */
    public record Payment(
        @NotNull PosPaymentMethod method,
        @NotNull @Positive BigDecimal amount,
        String tenderCurrency,
        String reference,
        String terminalId,
        String last4
    ) {}
}
