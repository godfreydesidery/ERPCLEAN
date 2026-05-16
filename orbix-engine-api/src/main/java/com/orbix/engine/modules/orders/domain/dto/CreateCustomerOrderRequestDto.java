package com.orbix.engine.modules.orders.domain.dto;

import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Create-order payload. {@code number} is optional — when omitted the service
 * generates one as {@code ORD-BR{branchId}-{seq}}. {@code depositRequiredAmount}
 * is optional — when omitted defaults to
 * {@code orbix.orders.deposit-required-pct} of the rolled-up total.
 * {@code reservedUntil} is optional — defaults to
 * {@code orbix.orders.default-layby-reserve-days} / {@code .default-pre-order-reserve-days}
 * from today.
 */
public record CreateCustomerOrderRequestDto(
    String number,
    @NotNull Long branchId,
    Long sectionId,
    @NotNull Long customerId,
    @NotNull CustomerOrderType type,
    @NotEmpty @Valid List<Line> lines,
    @PositiveOrZero BigDecimal depositRequiredAmount,
    Instant reservedUntil,
    String notes
) {
    public record Line(
        @NotNull Long itemId,
        Long uomId,
        @NotNull @Positive BigDecimal qty,
        @NotNull @PositiveOrZero BigDecimal unitPrice,
        @PositiveOrZero BigDecimal discountAmount,
        String notes
    ) {}
}
