package com.orbix.engine.modules.common.domain.dto;

import java.util.List;

/** A page of audit rows for the viewer (US-IAM-013). */
public record AuditPageDto(
    List<AuditLogDto> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
