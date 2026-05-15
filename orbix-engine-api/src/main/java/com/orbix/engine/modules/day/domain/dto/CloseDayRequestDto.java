package com.orbix.engine.modules.day.domain.dto;

import jakarta.validation.constraints.Size;

/** Finalises a CLOSING business day. The EOD report key is optional (set when the PDF lands). */
public record CloseDayRequestDto(
    @Size(max = 200) String eodReportObjectKey
) {}
