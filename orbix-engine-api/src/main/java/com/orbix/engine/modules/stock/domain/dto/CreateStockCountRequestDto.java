package com.orbix.engine.modules.stock.domain.dto;

import com.orbix.engine.modules.stock.domain.enums.StockCountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Opens a stock count. Lines are created for each item with the system qty frozen now. */
public record CreateStockCountRequestDto(
    @NotBlank @Size(max = 40) String number,
    @NotNull Long branchId,
    @NotNull LocalDate countDate,
    @NotNull StockCountType type,
    @NotEmpty List<Long> itemIds
) {}
