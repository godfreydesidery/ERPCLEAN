package com.orbix.engine.modules.fiscal.domain.dto;

import com.orbix.engine.modules.fiscal.domain.enums.FiscalStatus;

import java.time.Instant;

/**
 * Result returned by FiscalProvider.fiscalize. On success, status=FISCALIZED
 * and the TRA artefacts are populated. On failure, status=FAILED and
 * errorMessage describes the fault. On NoOp, status=NONE.
 *
 * <p>Fields marked STUB are populated by the real EFDMS response. When the
 * TRA spec is confirmed, replace StubEfdmsClient with a real implementation —
 * these fields flow through unchanged.
 */
public record FiscalReceiptResultDto(

    FiscalStatus status,

    /** STUB: pending TRA EFDMS spec — RCTNUM (receipt counter, per device). */
    Long rctnum,

    /** STUB: pending TRA EFDMS spec — GC (grand cumulative counter). */
    Long gc,

    /** STUB: pending TRA EFDMS spec — DC (daily counter). */
    Long dc,

    /** STUB: pending TRA EFDMS spec — ZNUM (Z-report / day number). */
    Integer znum,

    /** STUB: pending TRA EFDMS spec — TRA verification code printed on receipt. */
    String verificationCode,

    /** STUB: pending TRA EFDMS spec — https://verify.tra.go.tz/... URL. */
    String verifyUrl,

    /** STUB: pending TRA EFDMS spec — QR code payload. */
    String qrPayload,

    /** STUB: pending TRA EFDMS spec — RSA device signature of the submitted receipt. */
    String signature,

    /** Raw EFDMS response body (stub: simulated JSON string). */
    String rawResponse,

    /** Human-readable error message when status=FAILED. */
    String errorMessage,

    /** When the EFDMS submission occurred (or simulated). */
    Instant submittedAt

) {
    /** Convenience factory for a successful result. */
    public static FiscalReceiptResultDto fiscalized(
        Long rctnum, Long gc, Long dc, Integer znum,
        String verificationCode, String verifyUrl, String qrPayload,
        String signature, String rawResponse) {
        return new FiscalReceiptResultDto(
            FiscalStatus.FISCALIZED,
            rctnum, gc, dc, znum,
            verificationCode, verifyUrl, qrPayload, signature,
            rawResponse, null, Instant.now());
    }

    /** Convenience factory for a NoOp (regime=NONE) result. */
    public static FiscalReceiptResultDto noOp() {
        return new FiscalReceiptResultDto(
            FiscalStatus.NONE,
            null, null, null, null,
            null, null, null, null,
            null, null, Instant.now());
    }

    /** Convenience factory for a failed result. */
    public static FiscalReceiptResultDto failed(String errorMessage) {
        return new FiscalReceiptResultDto(
            FiscalStatus.FAILED,
            null, null, null, null,
            null, null, null, null,
            null, errorMessage, Instant.now());
    }
}
