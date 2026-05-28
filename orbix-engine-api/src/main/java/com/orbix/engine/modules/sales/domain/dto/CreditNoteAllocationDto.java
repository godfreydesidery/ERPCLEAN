package com.orbix.engine.modules.sales.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One allocation row — links a credit note to the invoice it partially or
 * fully settles (Slice H).
 */
public record CreditNoteAllocationDto(
    Long id,
    Long salesInvoiceId,
    String salesInvoiceNumber,
    BigDecimal amount,
    Instant allocatedAt,
    String allocatedBy
) {}
