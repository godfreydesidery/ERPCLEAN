package com.orbix.engine.modules.procurement.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One allocation row — links a vendor credit note to the supplier invoice it
 * partially or fully settles (Slice H.1).
 */
public record VendorCreditNoteAllocationDto(
    Long id,
    Long supplierInvoiceId,
    String supplierInvoiceNumber,
    BigDecimal amount,
    Instant allocatedAt,
    String allocatedBy
) {}
