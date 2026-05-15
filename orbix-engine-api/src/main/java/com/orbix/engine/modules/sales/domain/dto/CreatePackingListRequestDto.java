package com.orbix.engine.modules.sales.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreatePackingListRequestDto(
    @NotBlank String number,
    @NotNull Long branchId,
    @NotNull Long salesInvoiceId,
    @NotNull LocalDate dispatchDate,
    String driverName,
    String vehicleNo,
    String notes,
    @NotEmpty @Valid List<Line> lines
) {
    public record Line(
        @NotNull Long salesInvoiceLineId,
        @NotNull @Positive BigDecimal qty
    ) {}
}
