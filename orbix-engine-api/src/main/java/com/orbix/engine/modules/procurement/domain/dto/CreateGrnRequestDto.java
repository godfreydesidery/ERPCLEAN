package com.orbix.engine.modules.procurement.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateGrnRequestDto(
    @NotBlank String number,
    @NotNull Long branchId,
    @NotNull Long supplierId,
    /** Null = direct GRN — requires {@code GRN.DIRECT}. */
    Long lpoOrderId,
    @NotNull LocalDate receivedDate,
    String supplierDeliveryNote,
    String notes,
    @NotEmpty @Valid List<Line> lines
) {
    public record Line(
        /** Required when the GRN is bound to an LPO; null for direct GRN. */
        Long lpoOrderLineId,
        @NotNull Long itemId,
        Long uomId,
        @NotNull @Positive BigDecimal receivedQty,
        @NotNull @PositiveOrZero BigDecimal unitCost,
        Long vatGroupId,
        /** Required when the item is batch-tracked. */
        String batchNo,
        LocalDate expiryDate
    ) {}
}
