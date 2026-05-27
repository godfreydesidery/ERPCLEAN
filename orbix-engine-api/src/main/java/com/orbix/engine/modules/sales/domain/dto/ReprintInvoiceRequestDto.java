package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.enums.ReprintReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/sales-invoices/uid/{uid}/reprint}. Reason is
 * a closed enum ({@link ReprintReason}); optional free-text notes capture
 * the operator's context (≤500 chars, persisted on the outbox event
 * payload).
 *
 * <p>Slice C — locked decision: enum + optional notes (PM plan §1).
 */
public record ReprintInvoiceRequestDto(
    @NotNull ReprintReason reason,
    @Size(max = 500) String notes
) {}
