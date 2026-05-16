package com.orbix.engine.modules.production.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;

/**
 * Post-output payload (F7.3b).
 *
 * <p>Each line records what came out of the batch — primary + optional
 * co-products. For batch-tracked output items {@code batchNo} +
 * {@code manufacturedAt} + {@code expiryAt} are required so the service
 * can create the {@code stock_batch} row that the PROD_OUTPUT stock_move
 * stamps. {@code rejectQty} captures rejected output not entering stock
 * (no stock_move written — wastage entry is F7.3c).
 */
public record PostProductionOutputRequestDto(
    @NotEmpty @Valid List<Line> outputs,
    BigDecimal rejectQty,
    String notes
) {
    public record Line(
        @NotNull Long outputItemId,
        @NotNull @Positive BigDecimal qty,
        Long uomId,
        Boolean primary,
        Boolean packByWeight,
        String batchNo,
        LocalDate manufacturedAt,
        LocalDate expiryAt,
        String notes
    ) {}
}
