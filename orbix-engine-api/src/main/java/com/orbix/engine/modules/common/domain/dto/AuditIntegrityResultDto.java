package com.orbix.engine.modules.common.domain.dto;

/**
 * Result of an audit-chain integrity check (US-IAM-014). {@code ok} is false
 * when a row's recomputed hash or its link to the previous row doesn't match;
 * {@code firstBrokenId} then points at the earliest offending row.
 */
public record AuditIntegrityResultDto(
    boolean ok,
    long verifiedCount,
    Long firstBrokenId,
    String message
) {}
