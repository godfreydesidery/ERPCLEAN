package com.orbix.engine.modules.stock.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Service-layer input for creating a {@code stock_batch} row on inbound. Used by
 * GRN postings (F3.2), production output (F7.3), and opening-stock loaders.
 */
public record CreateStockBatchRequestDto(
    @NotNull Long itemId,
    @NotNull Long branchId,
    @NotBlank String batchNo,
    LocalDate manufacturedAt,
    LocalDate expiryAt,
    @NotNull @Positive BigDecimal qty,
    @NotNull BigDecimal cost,
    @NotBlank String sourceDocType,
    @NotNull Long sourceDocId
) {}
