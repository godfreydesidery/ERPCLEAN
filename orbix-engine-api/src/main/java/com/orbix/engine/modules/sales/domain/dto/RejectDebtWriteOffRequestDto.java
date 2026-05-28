package com.orbix.engine.modules.sales.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/debt/write-offs/uid/{uid}/reject}.
 */
public record RejectDebtWriteOffRequestDto(
    @NotBlank @Size(max = 2000) String reasonForReject
) {}
