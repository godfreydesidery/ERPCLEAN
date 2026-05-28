package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.procurement.domain.enums.VendorReturnReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateVendorReturnRequestDto(
    @NotBlank String number,
    @NotNull Long branchId,
    @NotNull Long supplierId,
    Long originalGrnId,
    Long originalSupplierInvoiceId,
    @NotNull LocalDate returnDate,
    @NotNull VendorReturnReason reason,
    boolean restock,
    String notes,
    @NotEmpty @Valid List<LineDto> lines
) {
    public record LineDto(
        @NotNull Long itemId,
        Long uomId,
        @NotNull @Positive BigDecimal returnedQty,
        @NotNull @PositiveOrZero BigDecimal unitPrice,
        Long vatGroupId,
        Long originalLineId
    ) {}
}
