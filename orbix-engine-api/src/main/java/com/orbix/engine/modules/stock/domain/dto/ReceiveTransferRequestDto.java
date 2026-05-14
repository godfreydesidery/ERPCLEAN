package com.orbix.engine.modules.stock.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/** Records received quantities against the lines of an ISSUED transfer. */
public record ReceiveTransferRequestDto(
    @NotEmpty List<ReceiveLine> lines
) {
    public record ReceiveLine(
        @NotNull Long lineId,
        @NotNull @PositiveOrZero BigDecimal receivedQty
    ) {}
}
