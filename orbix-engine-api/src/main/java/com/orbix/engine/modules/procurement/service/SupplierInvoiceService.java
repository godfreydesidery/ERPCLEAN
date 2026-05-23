package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.procurement.domain.dto.CreateSupplierInvoiceRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierInvoiceDto;
import org.springframework.data.domain.Pageable;

/**
 * Supplier invoice + 3-way match against GRNs (F3.3). Creating an invoice
 * captures header totals + allocations to one or more POSTED GRNs (same
 * supplier). On post the match is validated within
 * {@code orbix.procurement.invoice-match-tolerance-pct} and the supplier
 * payable opens. Settlement transitions land with F3.4.
 */
public interface SupplierInvoiceService {

    SupplierInvoiceDto createDraft(CreateSupplierInvoiceRequestDto request);

    /** DRAFT → POSTED — runs the match validation (already exercised on create, re-checked here). */
    SupplierInvoiceDto post(Long invoiceId);

    /** DRAFT or POSTED → CANCELLED. Allocations stay on the row for audit. */
    SupplierInvoiceDto cancel(Long invoiceId);

    PageDto<SupplierInvoiceDto> list(Long branchId, Pageable pageable);

    SupplierInvoiceDto get(Long invoiceId);
}
