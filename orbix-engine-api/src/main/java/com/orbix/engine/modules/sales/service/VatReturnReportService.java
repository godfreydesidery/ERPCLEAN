package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.common.domain.dto.VatReturnDto;

import java.time.LocalDate;

/**
 * VAT return export (F8.8 / US-NFR-COMP-001). Per-period output VAT
 * (sales) minus input VAT (procurement) rollup per VAT group, plus a
 * grand-total line for the filing.
 */
public interface VatReturnReportService {

    /**
     * Produces the VAT return for {@code [from, to]}. When either bound
     * is omitted, defaults to the previous calendar month. {@code branchId}
     * may be null to span the whole company.
     */
    VatReturnDto vatReturn(Long branchId, LocalDate from, LocalDate to);
}
