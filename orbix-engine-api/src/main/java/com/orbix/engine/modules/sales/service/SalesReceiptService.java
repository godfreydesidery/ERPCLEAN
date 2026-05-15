package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.sales.domain.dto.CreateSalesReceiptRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesReceiptDto;

import java.util.List;

/**
 * Customer receipts + per-invoice allocations (F4.3). Mirror of supplier-payment
 * for the inbound side. Posting advances {@code sales_invoice.paid_amount} and
 * flips PARTIALLY_PAID / PAID; any unallocated surplus stays on the receipt
 * for now (customer-credit routing comes with later work).
 */
public interface SalesReceiptService {

    SalesReceiptDto createDraft(CreateSalesReceiptRequestDto request);

    SalesReceiptDto post(Long receiptId);

    SalesReceiptDto cancel(Long receiptId);

    List<SalesReceiptDto> list(Long branchId);

    SalesReceiptDto get(Long receiptId);
}
