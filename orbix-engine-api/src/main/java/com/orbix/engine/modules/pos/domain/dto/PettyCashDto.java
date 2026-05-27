package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.pos.domain.entity.PettyCash;
import com.orbix.engine.modules.pos.domain.enums.PettyCashCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Petty-cash response. Slice D — surrogate-Long PK aggregate: carries both
 * {@code id} (stringified on the wire) and {@code uid} (external URL handle).
 * Append-only; no archive lifecycle.
 */
public record PettyCashDto(
    String uid,
    Long id,
    Long tillSessionId,
    Long branchId,
    LocalDate businessDate,
    BigDecimal amount,
    Instant at,
    PettyCashCategory category,
    String paidTo,
    Long paidBy,
    Long authorisedBy,
    String description,
    String receiptAttachmentKey
) {
    public static PettyCashDto from(PettyCash row) {
        return new PettyCashDto(
            row.getUid(),
            row.getId(), row.getTillSessionId(), row.getBranchId(),
            row.getBusinessDate(), row.getAmount(), row.getAt(),
            row.getCategory(), row.getPaidTo(), row.getPaidBy(),
            row.getAuthorisedBy(), row.getDescription(), row.getReceiptAttachmentKey());
    }
}
