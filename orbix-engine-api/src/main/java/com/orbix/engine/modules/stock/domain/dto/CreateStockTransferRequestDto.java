package com.orbix.engine.modules.stock.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Drafts an inter-branch transfer with its item lines. */
public record CreateStockTransferRequestDto(
    @NotBlank @Size(max = 40) String number,
    @NotNull Long fromBranchId,
    @NotNull Long toBranchId,
    @NotEmpty List<TransferLine> lines
) {
    public record TransferLine(
        @NotNull Long itemId,
        @NotNull @Positive BigDecimal issuedQty
    ) {}
}
