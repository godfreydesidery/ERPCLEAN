package com.orbix.engine.modules.fiscal.domain.dto;

import com.orbix.engine.modules.fiscal.domain.entity.FiscalReceipt;
import com.orbix.engine.modules.fiscal.domain.enums.FiscalStatus;

import java.time.Instant;

/**
 * External-facing response DTO for the FiscalReceipt aggregate.
 * Exposes both id (Long, serialized as string by the global modifier) and uid.
 * The verification artefacts here are what the POS/Web need to render the
 * fiscal receipt QR and printed verification code on reprint.
 */
public record FiscalReceiptDto(
    Long id,
    String uid,
    Long posSaleId,
    Long companyId,
    Long branchId,
    FiscalStatus status,
    String provider,

    /** STUB: pending TRA EFDMS spec confirmation. */
    Long rctnum,

    /** STUB: pending TRA EFDMS spec confirmation. */
    Long gc,

    /** STUB: pending TRA EFDMS spec confirmation. */
    Long dc,

    /** STUB: pending TRA EFDMS spec confirmation. */
    Integer znum,

    /** STUB: pending TRA EFDMS spec confirmation. */
    String verificationCode,

    /** STUB: pending TRA EFDMS spec confirmation. */
    String verifyUrl,

    /** STUB: pending TRA EFDMS spec confirmation. */
    String qrPayload,

    int attemptCount,
    String lastError,
    Instant submittedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static FiscalReceiptDto from(FiscalReceipt r) {
        return new FiscalReceiptDto(
            r.getId(), r.getUid(),
            r.getPosSaleId(), r.getCompanyId(), r.getBranchId(),
            r.getStatus(), r.getProvider(),
            r.getRctnum(), r.getGc(), r.getDc(), r.getZnum(),
            r.getVerificationCode(), r.getVerifyUrl(), r.getQrPayload(),
            r.getAttemptCount(), r.getLastError(),
            r.getSubmittedAt(), r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
