package com.orbix.engine.modules.common.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * VAT return for a period (F8.8 / US-NFR-COMP-001). Per-VAT-group input
 * + output rollup with a grand-total summary line for the tax filing.
 *
 * <p>Output composition: POSTED sales invoices (excluding VOIDED + DRAFT
 * + CANCELLED) blended with POSTED POS sales — REFUND POS rows contribute
 * negatively. Source date columns: {@code posted_business_date} for sales
 * invoices and {@code business_date} for POS sales.
 *
 * <p>Input composition: POSTED GRN lines on {@code received_date} in window.
 */
public record VatReturnDto(
    LocalDate from,
    LocalDate to,
    Long branchId,
    List<VatReturnRowDto> rows,
    BigDecimal totalOutputNet,
    BigDecimal totalOutputVat,
    BigDecimal totalInputNet,
    BigDecimal totalInputVat,
    BigDecimal netPayable
) {}
