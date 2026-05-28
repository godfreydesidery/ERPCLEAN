package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.procurement.domain.dto.CreateSupplierInvoiceRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierInvoiceDto;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

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
    SupplierInvoiceDto post(String uid);

    /** DRAFT or POSTED → CANCELLED. Allocations stay on the row for audit. */
    SupplierInvoiceDto cancel(String uid);

    PageDto<SupplierInvoiceDto> list(Long branchId, Pageable pageable);

    SupplierInvoiceDto get(String uid);

    /**
     * Slice G.2 — debt write-off. Advances {@code paidAmount} by {@code amount}
     * on the given invoice (must be POSTED or PARTIALLY_PAID) within the
     * caller's transaction. Throws {@link IllegalStateException} if the
     * invoice is in a non-payable status; {@link IllegalArgumentException}
     * if the amount would overpay.
     *
     * <p>ADR-0004 sync-TX exemption #20: called by
     * {@code DebtWriteOffServiceImpl} in the same DB tx as the write-off
     * record so the invariant "if write-off is POSTED, invoice paidAmount
     * reflects it" holds strictly.
     */
    void applyWriteOff(Long invoiceId, BigDecimal amount);
}
