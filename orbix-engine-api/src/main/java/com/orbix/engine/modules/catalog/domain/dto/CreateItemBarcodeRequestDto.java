package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.enums.BarcodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Payload for adding a barcode to an item. {@code packQty} null defaults to 1. */
public record CreateItemBarcodeRequestDto(
    @NotBlank @Size(max = 40) String barcode,
    @NotNull BarcodeType barcodeType,
    Long packUomId,
    @Positive BigDecimal packQty
) {}
