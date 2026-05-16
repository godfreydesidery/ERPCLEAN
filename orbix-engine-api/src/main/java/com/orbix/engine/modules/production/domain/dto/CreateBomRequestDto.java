package com.orbix.engine.modules.production.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * BOM create payload. Service assigns version 1 (or next sequential per
 * output item via the {@code /version} endpoint). {@code validFrom} defaults
 * to today if omitted; {@code standardYieldPct} defaults to 100 (no loss).
 *
 * <p>Each line is either a raw-material reference ({@code inputItemId} set)
 * or a sub-recipe reference ({@code subBomId} set) — never both, never
 * neither.
 */
public record CreateBomRequestDto(
    @NotNull Long sectionId,
    Long parentBomId,
    @NotNull Long outputItemId,
    @NotNull @Positive BigDecimal outputQty,
    Long outputUomId,
    @PositiveOrZero BigDecimal standardYieldPct,
    LocalDate validFrom,
    String notes,
    @NotEmpty @Valid List<Line> lines
) {
    public record Line(
        Long inputItemId,
        Long subBomId,
        @NotNull @Positive BigDecimal qty,
        Long uomId,
        @PositiveOrZero BigDecimal wastagePct,
        String notes
    ) {
        @AssertTrue(message = "Each BOM line must reference exactly one of inputItemId or subBomId")
        public boolean isExactlyOneRef() {
            return (inputItemId == null) != (subBomId == null);
        }
    }
}
