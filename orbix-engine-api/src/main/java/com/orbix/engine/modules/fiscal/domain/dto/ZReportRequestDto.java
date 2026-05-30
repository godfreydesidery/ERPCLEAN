package com.orbix.engine.modules.fiscal.domain.dto;

import java.time.LocalDate;

/**
 * Request to close the business day on the fiscal device (Z-report).
 * Submitted to EFDMS at end of business day to advance ZNUM and reset DC.
 *
 * STUB: exact fields required by TRA EFDMS Z-report endpoint are unknown
 * until the spec is confirmed. This is a placeholder for the API contract.
 */
public record ZReportRequestDto(

    Long companyId,
    Long branchId,

    /** Business date being closed. STUB: TRA may require a specific date format. */
    LocalDate businessDate,

    /** Seller TIN. STUB: pending TRA EFDMS spec. */
    String sellerTin

) {}
