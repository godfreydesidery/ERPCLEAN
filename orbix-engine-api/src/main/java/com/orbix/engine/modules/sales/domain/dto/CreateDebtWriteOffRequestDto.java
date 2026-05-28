package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffTargetKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/debt/write-offs}.
 */
public record CreateDebtWriteOffRequestDto(
    @NotNull DebtWriteOffTargetKind targetKind,
    @NotBlank @ValidUlid String targetInvoiceUid,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank @Size(max = 2000) String reason
) {}
