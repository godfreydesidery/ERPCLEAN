package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffStatus;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffTargetKind;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for a debt write-off request (AR or AP).
 * Long {@code id} / {@code *Id} fields are serialised as JSON strings by
 * {@code IdLongAsStringSerializerModifier} (registered globally in JacksonConfig).
 * {@code amount} is a genuine numeric and stays numeric on the wire.
 */
public record DebtWriteOffDto(
    Long id,
    String uid,
    DebtWriteOffTargetKind targetKind,
    Long targetInvoiceId,
    String targetInvoiceUid,
    String targetInvoiceNumber,      // hydrated for UI — invoice number string
    String partyName,                // customer or supplier name, hydrated
    BigDecimal amount,
    String currencyCode,
    String reason,
    DebtWriteOffStatus status,
    Long requestedByUserId,
    String requestedByUsername,
    Instant requestedAt,
    Long approvedByUserId,
    String approvedByUsername,
    Instant approvedAt,
    Instant postedAt,
    Instant rejectedAt,
    String reasonForReject
) {}
