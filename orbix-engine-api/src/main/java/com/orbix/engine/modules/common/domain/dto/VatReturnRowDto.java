package com.orbix.engine.modules.common.domain.dto;

import java.math.BigDecimal;

/**
 * One row of the VAT return — per-VAT-group rollup of output (sales) and
 * input (procurement) for the reporting period.
 *
 * <p>{@code netVatPayable = outputVat − inputVat}: positive means the
 * business owes the revenue authority, negative means a refund / carry-forward.
 *
 * <p>"Net" columns are pre-tax amounts. Sales lines already store
 * {@code line_total} as tax-inclusive so net = {@code line_total − tax_amount}.
 * GRN lines store {@code line_total} as the pre-tax cost so net = {@code line_total}
 * directly and input tax is derived as {@code net × rate} at report time.
 */
public record VatReturnRowDto(
    Long vatGroupId,
    String code,
    String name,
    BigDecimal rate,
    BigDecimal outputNet,
    BigDecimal outputVat,
    BigDecimal inputNet,
    BigDecimal inputVat,
    BigDecimal netVatPayable
) {}
