package com.orbix.engine.modules.fiscal.domain.dto;

import java.time.Instant;

/**
 * Result of a Z-report (end-of-day) submission to EFDMS.
 * All fields are STUB until the TRA EFDMS spec is confirmed.
 */
public record ZReportResultDto(

    boolean success,

    /** STUB: pending TRA EFDMS spec — new ZNUM after the Z-report advances the counter. */
    Integer newZnum,

    /** STUB: pending TRA EFDMS spec — EFDMS acknowledgement reference. */
    String acknowledgementRef,

    /** Raw EFDMS response. STUB. */
    String rawResponse,

    /** Error message when success=false. */
    String errorMessage,

    Instant submittedAt

) {}
