package com.orbix.engine.modules.stock.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Bulk-records counted quantities against the lines of an IN_PROGRESS count. */
public record RecordCountsRequestDto(
    @NotEmpty List<CountEntry> counts
) {
    public record CountEntry(
        @NotNull Long lineId,
        @NotNull BigDecimal countedQty,
        @Size(max = 200) String note
    ) {}
}
