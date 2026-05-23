package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.sales.domain.dto.CreateSalesInvoiceRequestDto;
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

    /** DRAFT → POSTED. Requires open business day; writes stock moves + opens debt. */
    SalesInvoiceDto post(Long invoiceId);

    /** POSTED → VOIDED (only on the same business day). Writes compensating stock moves. */
    SalesInvoiceDto voidInvoice(Long invoiceId, VoidSalesInvoiceRequestDto request);

    /** DRAFT → CANCELLED. */
    SalesInvoiceDto cancel(Long invoiceId);

    PageDto<SalesInvoiceDto> list(Long branchId, Pageable pageable);

    SalesInvoiceDto get(Long invoiceId);
}
