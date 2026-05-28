package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Request body for POST /vendor-credit-notes/uid/{uid}/apply (Slice H.1). */
public record ApplyVendorCreditNoteRequestDto(
    @NotBlank @ValidUlid String supplierInvoiceUid,
    @NotNull @DecimalMin("0.01") BigDecimal amount
) {}
