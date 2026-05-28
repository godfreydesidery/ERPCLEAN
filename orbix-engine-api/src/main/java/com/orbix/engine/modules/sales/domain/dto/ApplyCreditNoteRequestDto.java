package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Request body for POST /customer-credit-notes/uid/{uid}/apply (Slice H). */
public record ApplyCreditNoteRequestDto(
    @NotBlank @ValidUlid String salesInvoiceUid,
    @NotNull @DecimalMin("0.01") BigDecimal amount
) {}
