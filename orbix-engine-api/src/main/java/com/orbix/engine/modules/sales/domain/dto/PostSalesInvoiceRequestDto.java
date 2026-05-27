package com.orbix.engine.modules.sales.domain.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/sales-invoices/uid/{uid}/post}.
 *
 * <p>{@code overrideReason} is OPTIONAL — null in the normal case. When the
 * caller holds {@code SALES_INVOICE.OVERRIDE_CREDIT} and the credit-limit
 * gate would otherwise block the post, the service requires
 * {@code overrideReason} to be a non-blank string and persists it on the
 * invoice (Slice C GAP 3.A + 5.B). The zero-credit-limit branch is NOT
 * overridable — that error always fires unconditionally.
 *
 * <p>The post controller accepts an empty/missing body (null DTO) so the
 * happy path stays {@code POST .../post} with no body required.
 */
public record PostSalesInvoiceRequestDto(
    @Size(max = 500) String overrideReason
) {
    public static PostSalesInvoiceRequestDto empty() {
        return new PostSalesInvoiceRequestDto(null);
    }
}
