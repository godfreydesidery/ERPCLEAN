package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.sales.domain.dto.CreateSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.PostSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.ReprintInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesInvoiceDto;
import com.orbix.engine.modules.sales.domain.dto.VoidSalesInvoiceRequestDto;
import org.springframework.data.domain.Pageable;

/**
 * Back-office sales invoices (F4.2). DRAFT → POSTED writes the outbound
 * stock_move rows, snaps line cost from the balance avg, applies the
 * credit-limit + discount-threshold + min-sell-price rules. VOIDED is a
 * same-business-day reversal that writes compensating stock_move rows.
 * PARTIALLY_PAID / PAID transitions come from F4.3 sales receipts.
 */
public interface SalesInvoiceService {

    SalesInvoiceDto createDraft(CreateSalesInvoiceRequestDto request);

    /**
     * DRAFT → POSTED. Requires open business day; writes stock moves + opens
     * debt. The optional {@code request.overrideReason} is consumed when the
     * caller holds {@code SALES_INVOICE.OVERRIDE_CREDIT} and the customer's
     * credit limit would otherwise block the post (Slice C GAP 3.A / 5.B).
     */
    SalesInvoiceDto post(String uid, PostSalesInvoiceRequestDto request);

    /** POSTED → VOIDED (only on the same business day). Writes compensating stock moves. */
    SalesInvoiceDto voidInvoice(String uid, VoidSalesInvoiceRequestDto request);

    /** DRAFT → CANCELLED. */
    SalesInvoiceDto cancel(String uid);

    /**
     * Slice C — record a reprint of a posted invoice. Increments
     * {@code reprint_count} and emits {@code SalesInvoiceReprinted.v1}.
     * No state mutation; pure audit. Permission: {@code SALES_INVOICE.REPRINT}.
     */
    SalesInvoiceDto reprint(String uid, ReprintInvoiceRequestDto request);

    PageDto<SalesInvoiceDto> list(Long branchId, Pageable pageable);

    SalesInvoiceDto get(String uid);
}
