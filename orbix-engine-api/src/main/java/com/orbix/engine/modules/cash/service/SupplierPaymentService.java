package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.cash.domain.dto.CreateSupplierPaymentRequestDto;
import com.orbix.engine.modules.cash.domain.dto.SupplierPaymentDto;

import java.util.List;

/**
 * Supplier payments + per-invoice allocation (F3.4). Creating a payment captures
 * the cash outflow + the invoices it covers. On post, advances each invoice's
 * {@code paid_amount} and flips it to PARTIALLY_PAID / PAID. The cash-side
 * mirror (cash_entry OUT, cash_book delta) is owned by F6.1 and subscribes to
 * {@code SupplierPaymentPosted.v1}.
 */
public interface SupplierPaymentService {

    SupplierPaymentDto createDraft(CreateSupplierPaymentRequestDto request);

    /** DRAFT → POSTED: requires an open business day; advances invoice settlement. */
    SupplierPaymentDto post(Long paymentId);

    /** DRAFT → CANCELLED. */
    SupplierPaymentDto cancel(Long paymentId);

    List<SupplierPaymentDto> list(Long branchId);

    SupplierPaymentDto get(Long paymentId);
}
